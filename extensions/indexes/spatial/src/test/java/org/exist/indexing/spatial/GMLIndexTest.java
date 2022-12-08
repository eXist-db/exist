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

import java.io.IOException;
import java.io.StringReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.spatial.AbstractGMLJDBCIndex.SpatialOperator;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ExistSAXParserFactory;
import org.exist.util.FileInputSource;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.junit.*;
import org.xml.sax.*;
import org.xml.sax.helpers.XMLFilterImpl;

import org.locationtech.jts.geom.Geometry;

import static org.junit.Assert.*;

/**
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 */
public class GMLIndexTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String FILES[] = { "15385-SS7886-5i1.gml" };

    private static final XmldbURI TEST_COLLECTION_URI = XmldbURI.create("/db/test-spatial-index");

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "        <gml/>" +
        "   </index>" +
        "   <validation mode=\"no\"/> " +
    	"</collection>";

    String IN_MEMORY_GML = "<gml:Polygon xmlns:gml = 'http://www.opengis.net/gml' srsName='osgb:BNG'>" +
    "  <gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>" +
    "278515.400,187060.450 278515.150,187057.950 278516.350,187057.150 " +
    "278546.700,187054.000 278580.550,187050.900 278609.500,187048.100 " +
    "278609.750,187051.250 278574.750,187054.650 278544.950,187057.450 " +
    "278515.400,187060.450 " +
    "   </gml:coordinates></gml:LinearRing></gml:outerBoundaryIs>" +
    "</gml:Polygon>";
    
    String WKT_POLYGON = "POLYGON ((-3.7530493069563913 51.5695210244188, " +
    "-3.7526220716233705 51.569500427086325, -3.752191300029012 51.569481679670055, " +
    "-3.7516853221460167 51.5694586575048, -3.751687839470607 51.569430291017945, " +
    "-3.752106350923544 51.56944922336166, -3.752595638781826 51.5694697950237, " +
    "-3.753034464037513 51.56949156828257, -3.753052048201362 51.56949850020053, " +
    "-3.7530493069563913 51.5695210244188))";

    private Geometry currentGeometry;

    @BeforeClass
    public static void setup() throws EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, URISyntaxException, LockException {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI)) {

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, testCollection, COLLECTION_CONFIG);

            for (int i = 0; i < FILES.length; i++) {
                final URL url = GMLIndexTest.class.getResource("/" + FILES[i]);
                broker.storeDocument(transaction, XmldbURI.create(FILES[i]), new FileInputSource(Paths.get(url.toURI())), MimeType.XML_TYPE, testCollection);
            }

            transaction.commit();
        }
    }

    @AfterClass
    public static void tearDown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = server.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection testCollection = broker.openCollection(TEST_COLLECTION_URI, Lock.LockMode.WRITE_LOCK)) {

            if (testCollection != null) {
                broker.removeCollection(transaction, testCollection);
            }

            transaction.commit();
        }
    }

    @Test
    public void indexDocument() throws EXistException, CollectionConfigurationException, PermissionDeniedException, IOException, SAXException, LockException, URISyntaxException, SQLException {
        final BrokerPool pool = server.getBrokerPool();

        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction();
             final Collection testCollection = broker.openCollection(TEST_COLLECTION_URI, Lock.LockMode.READ_LOCK)) {

//            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
//            mgr.addConfiguration(transaction, broker, testCollection, COLLECTION_CONFIG);
//
//            for (int i = 0; i < FILES.length; i++) {
//                final URL url = getClass().getResource("/" + FILES[i]);
//                final IndexInfo indexInfo;
//                try (final InputStream is = Files.newInputStream(Paths.get(url.toURI()))) {
//                    final InputSource source = new InputSource();
//                    source.setByteStream(is);
//                    indexInfo = testCollection.validateXMLResource(transaction, broker, XmldbURI.create(FILES[i]), source);
//                }
//                try (final InputStream is = Files.newInputStream(Paths.get(url.toURI()))) {
//                    final InputSource source = new InputSource();
//                    source.setByteStream(is);
//                    testCollection.store(transaction, broker, indexInfo, source);
//                }
//            }

            GMLHSQLIndexWorker indexWorker = (GMLHSQLIndexWorker) broker.getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
            //Unplugged
            if (indexWorker != null) {
                Connection conn = null;
                try {
                    conn = indexWorker.acquireConnection();
                    for (int i = 0; i < FILES.length; i++) {
                        try (final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(FILES[i]), Lock.LockMode.READ_LOCK)) {
                            final DocumentImpl doc = lockedDoc.getDocument();

                            PreparedStatement ps = conn.prepareStatement(
                                    "SELECT * FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"
                            );
                            ps.setString(1, testCollection.getURI().append(doc.getURI()).getRawCollectionPath());
                            ResultSet rs = ps.executeQuery();
                            while (rs.next()) {
                                //Let be sure we have the right count
                            }
                            int count = rs.getRow();
                            ps.close();
                            assertEquals(0, count);
                        }
                    }
                } finally {
                    indexWorker.releaseConnection(conn);
                }
            }

            transaction.commit();
        }
    }

    @Test
    public void checkIndex() throws EXistException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            AbstractGMLJDBCIndex index = (AbstractGMLJDBCIndex) pool.getIndexManager().getIndexById(AbstractGMLJDBCIndex.ID);
            //Unplugged
            if (index != null) {
                assertTrue(index.checkIndex(broker));
            }
        }
    }

    @Test
    public void scanIndex() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            Sequence seq = xquery.execute(
                    broker,
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                            "declare function local:key-callback($term as xs:string, $data as xs:int+) as element() { " +
                            "   <entry>" +
                            "     <term>{$term}</term>" +
                            "     <frequency>{$data[1]}</frequency>" +
                            "     <documents>{$data[2]}</documents>" +
                            "     <position>{$data[3]}</position>" +
                            "   </entry> " +
                            "}; " +
                            //"util:index-keys(//gml:*, '', local:key-callback#2, 1000, 'spatial-index')[entry/frequency > 1] ",
                            "util:index-keys(//gml:*, '', local:key-callback#2, 1000, 'spatial-index')",
                    null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 1);
        }
    }

    @Test
    public void lowLevelSearch() throws EXistException, SAXException, ParserConfigurationException, SpatialIndexException, IOException {
    	GMLHandlerJTS geometryHandler = new GeometryHandler();
        GMLFilterGeometry geometryFilter = new GMLFilterGeometry(geometryHandler);
        GMLFilterDocument handler = new GMLFilterDocument(geometryFilter);

        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            AbstractGMLJDBCIndexWorker indexWorker = (AbstractGMLJDBCIndexWorker) broker.getIndexController().getWorkerByIndexId(AbstractGMLJDBCIndex.ID);
            //Unplugged
            if (indexWorker != null) {
                SAXParserFactory factory = ExistSAXParserFactory.getSAXParserFactory();
                factory.setNamespaceAware(true);
                InputSource src = new InputSource(new StringReader(IN_MEMORY_GML));
                SAXParser parser = factory.newSAXParser();
                XMLReader reader = parser.getXMLReader();
                SAXAdapter adapter = new SAXAdapter();
                reader.setContentHandler(handler);
                reader.setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
                reader.parse(src);

                Geometry EPSG4326_geometry = indexWorker.transformGeometry(currentGeometry, "osgb:BNG", "EPSG:4326");
                assertNotNull(EPSG4326_geometry);

                NodeSet ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.EQUALS);
                assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.DISJOINT);
                assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.INTERSECTS);
                assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.TOUCHES);
                //assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.CROSSES);
                //assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.WITHIN);
                assertTrue(ns.getLength() > 0);
                ns = indexWorker.search(broker, null, EPSG4326_geometry, SpatialOperator.CONTAINS);
                assertTrue(ns.getLength() > 0);
                //ns = ((GMLIndexWorker)index.getWorker()).search(broker, EPSG4326_geometry, SpatialOperator.OVERLAPS);
                //assertTrue(ns.getLength() > 0);
            }
        }
    }

    @Test
    public void highLevelSearch() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            String query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:equals(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            Sequence seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:disjoint(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:intersects(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:touches(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            //assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:crosses(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            //assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:within(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:contains(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:overlaps(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            //assertTrue(seq.getItemCount() > 0);

            //Tests with empty sequences
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:equals(//gml:*, ())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:overlaps((), //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            //In-memory test
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:equals(//gml:*, " + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
        }
    }

    @Test
    public void geometricProperties() throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            String query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKT((//gml:Polygon)[1])";
            Sequence seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKB((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMinX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMaxX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMinY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMaxY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getCentroidX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getCentroidY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getArea((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326WKT((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326WKB((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MinX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MaxX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MinY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MaxY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326CentroidX((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326CentroidY((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326Area((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getSRS((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getGeometryType((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isClosed((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isSimple((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isValid((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);

            //Tests with empty sequences
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKT(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getArea(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            //In-memory tests
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKT(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKB(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMinX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMaxX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMinY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getMaxY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getCentroidX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getCentroidY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getArea(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326WKT(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326WKB(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MinX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MaxX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MinY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326MaxY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326CentroidX(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326CentroidY(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getEPSG4326Area(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getSRS(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getGeometryType(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isClosed(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isSimple(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:isValid(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
        }
    }

    @Test
    public void gmlProducers() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            String query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:transform((//gml:Polygon)[1], 'EPSG:4326')";
            Sequence seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKT((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer((//gml:Polygon)[1], 100)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer((//gml:Polygon)[1], 100, 1)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getBbox((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:convexHull((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:boundary((//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:intersection((//gml:Polygon)[1], (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union((//gml:Polygon)[1], (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:difference((//gml:Polygon)[1], (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:symetricDifference((//gml:Polygon)[1], (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);

            //Tests with empty sequences
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:transform((), 'EPSG:4326')";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getWKT(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer((), 100)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer((), 100, 1)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getBbox(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:convexHull(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:boundary(())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union((), ())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union((//gml:Polygon)[1], ())";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() == 1);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union((), (//gml:Polygon)[1])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() == 1);

            //In-memory tests
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:transform(" + IN_MEMORY_GML + ", 'EPSG:4326')";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer(" + IN_MEMORY_GML + ", 100)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:buffer(" + IN_MEMORY_GML + ", 100, 1)";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:getBbox(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:convexHull(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:boundary(" + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:intersection(" + IN_MEMORY_GML + ", (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union(" + IN_MEMORY_GML + ", (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:difference(" + IN_MEMORY_GML + ", (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:symetricDifference(" + IN_MEMORY_GML + ", (//gml:Polygon)[2])";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:intersection((//gml:Polygon)[1]," + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:union((//gml:Polygon)[1]," + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:difference((//gml:Polygon)[1]," + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "spatial:symetricDifference((//gml:Polygon)[1]," + IN_MEMORY_GML + ")";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() > 0);
        }
    }    

    @Ignore("Spatial Index does not currently work with XQuery Update / XUpdate")
    @Test
    public void update() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = server.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            String query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "(# exist:force-index-use #) { " +
                    "spatial:getArea((//gml:Polygon)[1]) " +
                    "}";
            Sequence seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() == 1);
            final String area1 = seq.toString();

            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "update value (//gml:Polygon)[1]/gml:outerBoundaryIs/gml:LinearRing/gml:coordinates " +
                    "(: strip decimals :) " +
                    "with fn:replace((//gml:Polygon)[1], '(\\d+).(\\d+)', '$1')";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
                    "at 'java:org.exist.xquery.modules.spatial.SpatialModule'; " +
                    "declare namespace gml = 'http://www.opengis.net/gml'; " +
                    "(# exist:force-index-use #) { " +
                    "spatial:getArea((//gml:Polygon)[1]) " +
                    "}";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            assertTrue(seq.getItemCount() == 1);
            final String area2 = seq.toString();
            assertNotEquals(area1, area2);
        }
    }

    private class GeometryHandler extends XMLFilterImpl implements GMLHandlerJTS {
        public void geometry(Geometry geometry) {
            currentGeometry = geometry;
        }
    }
}
