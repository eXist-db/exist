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
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex;
import org.exist.indexing.spatial.AbstractGMLJDBCIndexWorker;
import org.exist.indexing.spatial.SpatialIndexException;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.IndexUseReporter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Element;

import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKBWriter;
import org.locationtech.jts.io.WKTWriter;

/**
  * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
  * @author ljo
  */
public class FunGeometricProperties extends BasicFunction implements IndexUseReporter {
    protected static final Logger logger = LogManager.getLogger(FunGeometricProperties.class);
    boolean hasUsedIndex = false;

    protected WKTWriter wktWriter = new WKTWriter();
    protected WKBWriter wkbWriter = new WKBWriter();

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("getWKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKT representation of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the WKT representation of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getWKB", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKB representation of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the WKB representation of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getMinX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the minimal X of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the minimal X of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getMaxX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the maximal X of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the maxmal X of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getMinY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the minimal Y of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the minimal Y of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getMaxY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the maximal Y of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the maximal Y of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getCentroidX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the X of centroid of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the X of centroid of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getCentroidY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the Y of centroid of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the Y of centroid of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getArea", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the area of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the area of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getEPSG4326WKT", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKT representation of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the WKT representation of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326WKB", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the WKB representation of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the WKB representation of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326MinX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the minimal X of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the minimal X of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326MaxX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the maximal X of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the maximal X of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326MinY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the minimal Y of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the minimal Y of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326MaxY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the maximal Y of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the maximal Y of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326CentroidX", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the X of centroid of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the X of centroid of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326CentroidY", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the Y of centroid of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the Y of centroid of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getEPSG4326Area", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the area of geometry $geometry in the EPSG:4326 SRS",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.DOUBLE, Cardinality.ZERO_OR_ONE, "the area of geometry $geometry in the EPSG:4326 SRS")
        ),
        new FunctionSignature(
            new QName("getSRS", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the spatial reference system of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the spatial reference system of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("getGeometryType", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns the type of geometry $geometry",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the type of geometry $geometry")
        ),
        new FunctionSignature(
            new QName("isClosed", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns true() if geometry $geometry is closed, otherwise false()",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true() if geometry $geometry is closed, otherwise false()")
        ),
        new FunctionSignature(
            new QName("isSimple", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns true() if geometry $geometry is simple, otherwise false()",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true() if geometry $geometry is simple, otherwise false()")
        ),
        new FunctionSignature(
            new QName("isValid", SpatialModule.NAMESPACE_URI, SpatialModule.PREFIX),
            "Returns true() if geometry $geometry is valid, otherwise false()",
            new SequenceType[]{
                FunSpatialSearch.GEOMETRY_PARAMETER,
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_ONE, "true() if geometry $geometry is valid, otherwise false()")
        )
   	};

    public FunGeometricProperties(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Sequence result = null;
        Sequence nodes = args[0];

        if (nodes.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            try {
                Geometry geometry = null;
                String sourceCRS = null;
                AbstractGMLJDBCIndexWorker indexWorker = 
                    (AbstractGMLJDBCIndexWorker)context.getBroker().getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
                if (indexWorker == null) {
                    logger.error("Unable to find a spatial index worker");
                    throw new XPathException(this, "Unable to find a spatial index worker");
                }
                String propertyName = null;
                if (isCalledAs("getWKT")) {
                    propertyName = "WKT";
                } else if (isCalledAs("getWKB")) {
                    propertyName = "WKB";
                } else if (isCalledAs("getMinX")) {
                    propertyName = "MINX";
                } else if (isCalledAs("getMaxX")) {
                    propertyName = "MAXX";
                } else if (isCalledAs("getMinY")) {
                    propertyName = "MINY";
                } else if (isCalledAs("getMaxY")) {
                    propertyName = "MAXY";
                } else if (isCalledAs("getCentroidX")) {
                    propertyName = "CENTROID_X";
                } else if (isCalledAs("getCentroidY")) {
                    propertyName = "CENTROID_Y";
                } else if (isCalledAs("getArea")) {
                    propertyName = "AREA";
                } else if (isCalledAs("getEPSG4326WKT")) {
                    propertyName = "EPSG4326_WKT";
                } else if (isCalledAs("getEPSG4326WKB")) {
                    propertyName = "EPSG4326_WKB";
                } else if (isCalledAs("getEPSG4326MinX")) {
                    propertyName = "EPSG4326_MINX";
                } else if (isCalledAs("getEPSG4326MaxX")) {
                    propertyName = "EPSG4326_MAXX";
                } else if (isCalledAs("getEPSG4326MinY")) {
                    propertyName = "EPSG4326_MINY";
                } else if (isCalledAs("getEPSG4326MaxY")) {
                    propertyName = "EPSG4326_MAXY";
                } else if (isCalledAs("getEPSG4326CentroidX")) {
                    propertyName = "EPSG4326_CENTROID_X";
                } else if (isCalledAs("getEPSG4326CentroidY")) {
                    propertyName = "EPSG4326_CENTROID_Y";
                } else if (isCalledAs("getEPSG4326Area")) {
                    propertyName = "EPSG4326_AREA";
                } else if (isCalledAs("getSRS")) {
                    propertyName = "SRS_NAME";
                } else if (isCalledAs("getGeometryType")) {
                    propertyName = "GEOMETRY_TYPE";
                } else if (isCalledAs("isClosed")) {
                    propertyName = "IS_CLOSED";
                } else if (isCalledAs("isSimple")) {
                    propertyName = "IS_SIMPLE";
                } else if (isCalledAs("isValid")) {
                    propertyName = "IS_VALID";
                } else {
                    logger.error("Unknown spatial property: {}", getName().getLocalPart());
                    throw new XPathException(this, "Unknown spatial property: " + getName().getLocalPart());
                }
                NodeValue geometryNode = (NodeValue)nodes.itemAt(0);
                if (geometryNode.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                    //The node should be indexed : get its property
                    result = indexWorker.getGeometricPropertyForNode(context, (NodeProxy)geometryNode, propertyName);
                    hasUsedIndex = true;
                } else {
                    //builds the geometry
                    sourceCRS = ((Element)geometryNode.getNode()).getAttribute("srsName").trim();
                    geometry = indexWorker.streamNodeToGeometry(context, geometryNode);
                    if (geometry == null) {
                        logger.error("Unable to get a geometry from the node");
                        throw new XPathException(this, "Unable to get a geometry from the node");
                    }
                    //Transform the geometry to EPSG:4326 if relevant
                    if (propertyName.contains("EPSG4326")) {
                        geometry = indexWorker.transformGeometry(geometry, sourceCRS, "EPSG:4326");
                        if (isCalledAs("getEPSG4326WKT")) {
                            result = new StringValue(this, wktWriter.write(geometry));
                        } else if (isCalledAs("getEPSG4326WKB")) {
                            byte data[] = wkbWriter.write(geometry);
                            return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new UnsynchronizedByteArrayInputStream(data), this);
                        } else if (isCalledAs("getEPSG4326MinX")) {
                            result = new DoubleValue(this, geometry.getEnvelopeInternal().getMinX());
                        } else if (isCalledAs("getEPSG4326MaxX")) {
                            result = new DoubleValue(this, geometry.getEnvelopeInternal().getMaxX());
                        } else if (isCalledAs("getEPSG4326MinY")) {
                            result = new DoubleValue(this, geometry.getEnvelopeInternal().getMinY());
                        } else if (isCalledAs("getEPSG4326MaxY")) {
                            result = new DoubleValue(this, geometry.getEnvelopeInternal().getMaxY());
                        } else if (isCalledAs("getEPSG4326CentroidX")) {
                            result = new DoubleValue(this, geometry.getCentroid().getX());
                        } else if (isCalledAs("getEPSG4326CentroidY")) {
                            result = new DoubleValue(this, geometry.getCentroid().getY());
                        } else if (isCalledAs("getEPSG4326Area")) {
                            result = new DoubleValue(this, geometry.getArea());
                        }
                    } else if (isCalledAs("getWKT")) {
                        result = new StringValue(this, wktWriter.write(geometry));
                    } else if (isCalledAs("getWKB")) {
                        byte data[] = wkbWriter.write(geometry);
                        return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new UnsynchronizedByteArrayInputStream(data), this);
                    } else if (isCalledAs("getMinX")) {
                        result = new DoubleValue(this, geometry.getEnvelopeInternal().getMinX());
                    } else if (isCalledAs("getMaxX")) {
                        result = new DoubleValue(this, geometry.getEnvelopeInternal().getMaxX());
                    } else if (isCalledAs("getMinY")) {
                        result = new DoubleValue(this, geometry.getEnvelopeInternal().getMinY());
                    } else if (isCalledAs("getMaxY")) {
                        result = new DoubleValue(this, geometry.getEnvelopeInternal().getMaxY());
                    } else if (isCalledAs("getCentroidX")) {
                        result = new DoubleValue(this, geometry.getCentroid().getX());
                    } else if (isCalledAs("getCentroidY")) {
                        result = new DoubleValue(this, geometry.getCentroid().getY());
                    } else if (isCalledAs("getArea")) {
                        result = new DoubleValue(this, geometry.getArea());
                    } else if (isCalledAs("getSRS")) {
                        result = new StringValue(this, ((Element)geometryNode).getAttribute("srsName"));
                    } else if (isCalledAs("getGeometryType")) {
                        result = new StringValue(this, geometry.getGeometryType());
                    } else if (isCalledAs("isClosed")) {
                        result = new BooleanValue(this, !geometry.isEmpty());
                    } else if (isCalledAs("isSimple")) {
                        result = new BooleanValue(this, geometry.isSimple());
                    } else if (isCalledAs("isValid")) {
                        result = new BooleanValue(this, geometry.isValid());
                    } else {
                        logger.error("Unknown spatial property: {}", getName().getLocalPart());
                        throw new XPathException(this, "Unknown spatial property: " + getName().getLocalPart());
                    }
                }
            } catch (SpatialIndexException e) {
                logger.error(e.getMessage());
                throw new XPathException(this, e);
            }
        }
        return result;
    }

    public boolean hasUsedIndex() {
        return hasUsedIndex;
    }

}
