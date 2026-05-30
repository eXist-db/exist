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
package org.exist.indexing.spatial;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * JUnit tests for {@link org.exist.xquery.modules.spatial.FunSpatialDistance}.
 *
 * <p>Uses in-memory GML geometries so the tests do not depend on the OS MasterMap
 * fixture downloaded by the build's wget plugin. The spatial index is still
 * configured (the embedded server picks up the test {@code conf.xml}); the
 * geometries here flow through {@code streamNodeToGeometry} rather than
 * {@code getGeometryForNode}, exercising the in-memory branch of
 * {@link org.exist.xquery.modules.spatial.FunSpatialDistance#resolveGeometry}.
 */
public class FunSpatialDistanceTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String SPATIAL_PROLOG = """
            import module namespace spatial='http://exist-db.org/xquery/spatial'
                at 'java:org.exist.xquery.modules.spatial.SpatialModule';
            declare namespace gml='http://www.opengis.net/gml';
            """;

    /** Times Square, NYC. */
    private static final String POINT_NYC = """
            <gml:Point srsName='EPSG:4326'>
              <gml:coordinates>-73.9857,40.7484</gml:coordinates>
            </gml:Point>""";

    /** LAX airport, Los Angeles. */
    private static final String POINT_LA = """
            <gml:Point srsName='EPSG:4326'>
              <gml:coordinates>-118.4081,33.9416</gml:coordinates>
            </gml:Point>""";

    /** Eiffel Tower, Paris. */
    private static final String POINT_PARIS = """
            <gml:Point srsName='EPSG:4326'>
              <gml:coordinates>2.2945,48.8584</gml:coordinates>
            </gml:Point>""";

    @Test
    public void distanceCartesianReturnsEuclideanDegrees()
            throws EXistException, PermissionDeniedException, XPathException {
        final double dx = -118.4081 - -73.9857;
        final double dy = 33.9416 - 40.7484;
        final double expected = Math.sqrt(dx * dx + dy * dy);

        final double result = runDouble("spatial:distance(%s, %s)".formatted(POINT_NYC, POINT_LA));
        assertEquals(expected, result, 1e-9);
    }

    @Test
    public void distanceMetersNycToLa() throws EXistException, PermissionDeniedException, XPathException {
        final double meters = runDouble(
                "spatial:distance(%s, %s, 'meter')".formatted(POINT_NYC, POINT_LA));

        // Reference value: NYC -> LA great-circle distance is ~3935.7 km. Allow
        // 1% slack: haversine has ~0.5% error vs ellipsoidal, plus the test
        // doesn't pin the exact reference algorithm.
        final double expectedMeters = 3_935_700d;
        assertEquals(expectedMeters, meters, expectedMeters * 0.01);
    }

    @Test
    public void distanceKilometersMatchesMetersScaledDown()
            throws EXistException, PermissionDeniedException, XPathException {
        final double meters = runDouble(
                "spatial:distance(%s, %s, 'meter')".formatted(POINT_NYC, POINT_LA));
        final double km = runDouble(
                "spatial:distance(%s, %s, 'kilometer')".formatted(POINT_NYC, POINT_LA));
        assertEquals(meters / 1_000d, km, 1e-3);
    }

    @Test
    public void distanceMilesNycToLa() throws EXistException, PermissionDeniedException, XPathException {
        final double miles = runDouble(
                "spatial:distance(%s, %s, 'mile')".formatted(POINT_NYC, POINT_LA));
        // ~2446 mi great-circle. Allow 1% slack.
        final double expectedMiles = 2_445.6d;
        assertEquals(expectedMiles, miles, expectedMiles * 0.01);
    }

    @Test
    public void distanceNauticalMilesNycToLa() throws EXistException, PermissionDeniedException, XPathException {
        final double nm = runDouble(
                "spatial:distance(%s, %s, 'nautical-mile')".formatted(POINT_NYC, POINT_LA));
        // ~2124.6 nm great-circle. Allow 1% slack.
        final double expectedNm = 2_124.6d;
        assertEquals(expectedNm, nm, expectedNm * 0.01);
    }

    @Test
    public void distanceMetersNycToParis() throws EXistException, PermissionDeniedException, XPathException {
        final double meters = runDouble(
                "spatial:distance(%s, %s, 'meter')".formatted(POINT_NYC, POINT_PARIS));
        // ~5837 km great-circle. Allow 1% slack.
        final double expectedMeters = 5_837_000d;
        assertEquals(expectedMeters, meters, expectedMeters * 0.01);
    }

    @Test
    public void distanceWithEmptyFirstOperandReturnsEmpty()
            throws EXistException, PermissionDeniedException, XPathException {
        final Sequence seq = runQuery("spatial:distance((), %s)".formatted(POINT_NYC));
        assertEquals(0, seq.getItemCount());
    }

    @Test
    public void distanceWithEmptySecondOperandReturnsEmpty()
            throws EXistException, PermissionDeniedException, XPathException {
        final Sequence seq = runQuery("spatial:distance(%s, ())".formatted(POINT_NYC));
        assertEquals(0, seq.getItemCount());
    }

    @Test
    public void distanceWithUnsupportedUnitRaisesError() throws EXistException, PermissionDeniedException {
        try {
            runQuery("spatial:distance(%s, %s, 'furlong')".formatted(POINT_NYC, POINT_LA));
            fail("Expected XPathException for unsupported unit");
        } catch (final XPathException e) {
            assertTrue("Expected mention of the unsupported unit in the error message",
                    e.getMessage().contains("furlong"));
        }
    }

    @Test
    public void distanceWithDegreeUnitMatchesCartesianDefault()
            throws EXistException, PermissionDeniedException, XPathException {
        final double cartesian = runDouble("spatial:distance(%s, %s)".formatted(POINT_NYC, POINT_LA));
        final double explicit = runDouble(
                "spatial:distance(%s, %s, 'degree')".formatted(POINT_NYC, POINT_LA));
        assertEquals(cartesian, explicit, 1e-12);
    }

    private double runDouble(final String body) throws EXistException, PermissionDeniedException, XPathException {
        final Sequence seq = runQuery(body);
        assertNotNull(seq);
        assertEquals(1, seq.getItemCount());
        return ((DoubleValue) seq.itemAt(0)).getDouble();
    }

    private Sequence runQuery(final String body) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final XQuery xquery = pool.getXQueryService();
            return xquery.execute(broker, SPATIAL_PROLOG + body, null);
        }
    }
}
