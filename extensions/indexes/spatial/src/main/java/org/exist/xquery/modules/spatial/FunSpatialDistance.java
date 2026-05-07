/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.modules.spatial;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex;
import org.exist.indexing.spatial.AbstractGMLJDBCIndexWorker;
import org.exist.indexing.spatial.SpatialIndexException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.operation.distance.DistanceOp;
import org.w3c.dom.Element;

/**
 * Returns the distance between two GML geometries.
 *
 * <p>Two arities:
 * <ul>
 *   <li>{@code spatial:distance($g1, $g2)} returns the cartesian distance in
 *       the units of the geometries' source spatial reference system. For
 *       EPSG:4326 (WGS84 lat/lon) the unit is degrees, which is rarely what
 *       a developer wants -- use the 3-arity form for meters/kilometers/miles.
 *   <li>{@code spatial:distance($g1, $g2, $unit)} returns the great-circle
 *       distance computed via haversine on the closest-point pair, after
 *       transforming both geometries to EPSG:4326. Supported units:
 *       {@code "degree"} (cartesian, no transform; equivalent to the 2-arity
 *       form), {@code "meter"}, {@code "kilometer"}, {@code "mile"},
 *       {@code "nautical-mile"}.
 * </ul>
 *
 * <p>Haversine has roughly 0.5% error against full WGS84 ellipsoidal distance
 * but is faster and avoids pulling more of GeoTools' referencing stack into
 * the hot path. Users who need geodetic precision should transform to a
 * projected CRS via {@code spatial:transform} before calling.
 */
public class FunSpatialDistance extends BasicFunction implements IndexUseReporter {

    private static final Logger LOG = LogManager.getLogger(FunSpatialDistance.class);

    private static final FunctionParameterSequenceType GEOMETRY_1 =
            new FunctionParameterSequenceType("geometry1", Type.NODE, Cardinality.ZERO_OR_ONE,
                    "The first geometry");
    private static final FunctionParameterSequenceType GEOMETRY_2 =
            new FunctionParameterSequenceType("geometry2", Type.NODE, Cardinality.ZERO_OR_ONE,
                    "The second geometry");
    private static final FunctionParameterSequenceType UNIT =
            new FunctionParameterSequenceType("unit", Type.STRING, Cardinality.EXACTLY_ONE,
                    """
                    Distance unit: "degree" (cartesian, source SRS), "meter", \
                    "kilometer", "mile", or "nautical-mile"\
                    """);

    /** Mean Earth radius (WGS84 authalic radius), in meters. Used for haversine. */
    private static final double EARTH_RADIUS_METERS = 6_371_007.2d;

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("distance", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                    """
                    Returns the cartesian distance between $geometry1 and $geometry2 in \
                    the units of their source spatial reference system. For lat/lon \
                    geometries (EPSG:4326) this returns degrees; use the 3-arity form \
                    with $unit = "meter" for great-circle distance.\
                    """,
                    new SequenceType[]{GEOMETRY_1, GEOMETRY_2},
                    new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                            "the cartesian distance, or empty sequence if either argument is empty")
            ),
            new FunctionSignature(
                    new QName("distance", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
                    """
                    Returns the distance between $geometry1 and $geometry2 in the \
                    requested $unit. Supported units: "degree" (cartesian, source SRS), \
                    "meter", "kilometer", "mile", "nautical-mile". Non-degree units \
                    transform both geometries to EPSG:4326 and use haversine on the \
                    closest-point pair (~0.5% error vs ellipsoidal).\
                    """,
                    new SequenceType[]{GEOMETRY_1, GEOMETRY_2, UNIT},
                    new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE,
                            """
                            the distance in the requested unit, or empty sequence if \
                            either geometry argument is empty\
                            """)
            )
    };

    private boolean hasUsedIndex = false;

    public FunSpatialDistance(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty() || args[1].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String unit = args.length == 3
                ? args[2].itemAt(0).getStringValue().trim().toLowerCase()
                : "degree";
        if (!isSupportedUnit(unit)) {
            throw new XPathException(this, ErrorCodes.FOER0000, """
                    Unsupported distance unit '%s'. Expected one of: \
                    degree, meter, kilometer, mile, nautical-mile.\
                    """.formatted(unit));
        }

        final AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker)
                context.getBroker().getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
        if (indexWorker == null) {
            throw new XPathException(this, ErrorCodes.FOER0000,
                    "Unable to find a spatial index worker.");
        }

        final boolean wantEpsg4326 = !"degree".equals(unit);
        try {
            final Geometry g1 = resolveGeometry(indexWorker, (NodeValue) args[0].itemAt(0), wantEpsg4326);
            final Geometry g2 = resolveGeometry(indexWorker, (NodeValue) args[1].itemAt(0), wantEpsg4326);
            if (g1 == null || g2 == null) {
                throw new XPathException(this, ErrorCodes.FOER0000,
                        "Unable to resolve a geometry from the input nodes.");
            }
            final double result = "degree".equals(unit)
                    ? g1.distance(g2)
                    : haversineDistance(g1, g2, unit);
            return new DoubleValue(this, result);
        } catch (final SpatialIndexException e) {
            LOG.error(e.getMessage(), e);
            throw new XPathException(this, ErrorCodes.FOER0000, e);
        }
    }

    private Geometry resolveGeometry(final AbstractGMLJDBCIndexWorker indexWorker,
                                     final NodeValue node, final boolean wantEpsg4326)
            throws SpatialIndexException {
        if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
            final Geometry indexed = indexWorker.getGeometryForNode(
                    context.getBroker(), (NodeProxy) node, wantEpsg4326);
            if (indexed != null) {
                hasUsedIndex = true;
                return indexed;
            }
        }
        // Fallback for in-memory geometries or unindexed persistent nodes.
        final Geometry streamed = indexWorker.streamNodeToGeometry(context, node);
        if (streamed == null) {
            return null;
        }
        if (!wantEpsg4326) {
            return streamed;
        }
        final String sourceCrs = ((Element) node.getNode()).getAttribute("srsName").trim();
        if (sourceCrs.isEmpty() || "EPSG:4326".equalsIgnoreCase(sourceCrs)) {
            return streamed;
        }
        return indexWorker.transformGeometry(streamed, sourceCrs, "EPSG:4326");
    }

    /**
     * Closest-point haversine. Uses JTS DistanceOp to find the nearest pair of
     * coordinates on the two (already EPSG:4326-transformed) geometries, then
     * computes great-circle distance via haversine and converts to the
     * requested unit.
     */
    private static double haversineDistance(final Geometry g1, final Geometry g2, final String unit) {
        final Coordinate[] nearest = DistanceOp.nearestPoints(g1, g2);
        final double meters = haversineMeters(nearest[0].y, nearest[0].x, nearest[1].y, nearest[1].x);
        return switch (unit) {
            case "meter" -> meters;
            case "kilometer" -> meters / 1_000d;
            case "mile" -> meters / 1_609.344d;
            case "nautical-mile" -> meters / 1_852d;
            // "degree" is handled by the caller via Geometry.distance(); not reached here.
            default -> throw new IllegalArgumentException("Unsupported unit: " + unit);
        };
    }

    private static double haversineMeters(final double lat1Deg, final double lon1Deg,
                                          final double lat2Deg, final double lon2Deg) {
        final double lat1 = Math.toRadians(lat1Deg);
        final double lat2 = Math.toRadians(lat2Deg);
        final double dLat = lat2 - lat1;
        final double dLon = Math.toRadians(lon2Deg - lon1Deg);
        final double sinDLat = Math.sin(dLat / 2d);
        final double sinDLon = Math.sin(dLon / 2d);
        final double a = sinDLat * sinDLat
                + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;
        final double c = 2d * Math.atan2(Math.sqrt(a), Math.sqrt(1d - a));
        return EARTH_RADIUS_METERS * c;
    }

    private static boolean isSupportedUnit(final String unit) {
        return switch (unit) {
            case "degree", "meter", "kilometer", "mile", "nautical-mile" -> true;
            default -> false;
        };
    }

    @Override
    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }
}
