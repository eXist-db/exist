package org.exist.indexing.spatial;

import java.io.File;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.exist.EXistException;
import org.exist.dom.NodeSet;
import org.exist.indexing.spatial.GMLHSQLIndex.SpatialOperator;
import org.exist.memtree.SAXAdapter;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.IndexQueryService;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.geotools.geometry.jts.GeometryCoordinateSequenceTransformer;
import org.geotools.gml.GMLFilterDocument;
import org.geotools.gml.GMLFilterGeometry;
import org.geotools.gml.GMLHandlerJTS;
import org.opengis.referencing.operation.MathTransform;
import org.opengis.referencing.operation.TransformException;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import com.vividsolutions.jts.geom.Geometry;

/**
 * 
 */
public class GMLIndexTest extends TestCase {
		
	private final static String FILES[] = { "port-talbot.gml" };
    static File existDir;
    static {
    	String existHome = System.getProperty("exist.home");
    	existDir = existHome==null ? new File(".") : new File(existHome);
    }
    
	private final static File RESOURCE_DIR_DIR = new File(existDir, "extensions/indexes/spatial/test/resources");
	private static final String TEST_COLLECTION_NAME = "test-spatial-index";

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "        <gml flushAfter='200'/>" +
        "	</index>" +
    	"</collection>";

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
        "</xu:modifications>";
    
    String IN_MEMORY_GML = "<gml:Polygon xmlns:gml = 'http://www.opengis.net/gml' srsName='osgb:BNG'>" +
	"	<gml:outerBoundaryIs><gml:LinearRing><gml:coordinates>" +
	"278515.400,187060.450 278515.150,187057.950 278516.350,187057.150 " +
	"278546.700,187054.000 278580.550,187050.900 278609.500,187048.100 " +
	"278609.750,187051.250 278574.750,187054.650 278544.950,187057.450 " +
	"278515.400,187060.450 " +
	"   </gml:coordinates></gml:LinearRing></gml:outerBoundaryIs>" +
	"</gml:Polygon>";    
    
    private Database database;
    private Collection testCollection;   
    private Geometry currentGeometry;
    
    protected static GeometryCoordinateSequenceTransformer coordinateTransformer = new GeometryCoordinateSequenceTransformer() ;
       
    public void testIndexDocument() {
        BrokerPool pool = null;
        DBBroker broker = null;    	
    	try {
	        for (int i = 0; i < FILES.length; i++) {
	            XMLResource doc =
	                (XMLResource) testCollection.createResource(
	                        FILES[i], "XMLResource" );
	            doc.setContent(new File(RESOURCE_DIR_DIR, FILES[i]));
	            testCollection.storeResource(doc); 
	            assertNotNull(testCollection.getResource(FILES[i]));
	        }
	        
	        pool = BrokerPool.getInstance();
	        assertNotNull(pool);
	        broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
	        assertNotNull(broker);
	        GMLHSQLIndexWorker indexWorker = (GMLHSQLIndexWorker)broker.getIndexController().getIndexWorkerById(GMLHSQLIndex.ID);
	        //Unplugged
	    	if (indexWorker == null)
	        	return;	       	
	       	
	        try {
	        	Connection conn = null;
	        	try {
	        		conn = indexWorker.acquireConnection();	        	
		        	for (int i = 0; i < FILES.length; i++) {
		        		XMLResource doc =
			                (XMLResource) testCollection.getResource(FILES[i]);        		
				        PreparedStatement ps = conn.prepareStatement(
				        		"SELECT * FROM " + GMLHSQLIndex.TABLE_NAME + " WHERE DOCUMENT_URI = ?;"      		
				        	); 		       
				        ps.setString(1, testCollection.getName() + "/" + doc.getDocumentId());
				        ResultSet rs = ps.executeQuery(); 
				        while (rs.next()) {}
				        int count = rs.getRow();
				        System.out.println(count + " geometries in the index");
				        ps.close();
				        assertTrue(count > 0);
		        	}	       	  
	        	} finally {
	        		indexWorker.releaseConnection(conn);
	        	}
	        } catch (SQLException e) {
	        	e.printStackTrace();
	        	fail(e.getMessage());	            	
	        }
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());	        
    	} catch (EXistException e) {
    		e.printStackTrace();
    		fail(e.getMessage());	  
        } finally {        	
            pool.release(broker);
        }  
    }   
    
    public void testCheckIndex() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {    	
	        pool = BrokerPool.getInstance();
	        assertNotNull(pool);
	        broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
	        GMLHSQLIndex index = (GMLHSQLIndex)pool.getIndexManager().getIndexById("spatial-index");
	        //Unplugged
	    	if (index == null)
	        	return;	        
	        assertTrue(index.checkIndex(broker)); 
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {        	
            pool.release(broker);
        }  
    }
    
    public void testScanIndex() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {    	
        	pool = BrokerPool.getInstance();
	    	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
	        XQuery xquery = broker.getXQueryService();
	        assertNotNull(xquery);
	        Sequence seq = xquery.execute(
	        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
	        	"declare function local:key-callback($term as xs:string, $data as xs:int+) as element() { " +
	        	"   <entry>" +
	        	"     <term>{$term}</term>" +
	        	"     <frequency>{$data[1]}</frequency>" +
	        	"     <documents>{$data[2]}</documents>" +
	        	"     <position>{$data[3]}</position>" +
	        	"   </entry> " +	        	
	        	"}; " +
	        	//"util:index-keys(//gml:*, '', util:function('local:key-callback', 2), 1000, 'spatial-index')[entry/frequency > 1] ",
	        	"util:index-keys(//gml:*, '', util:function('local:key-callback', 2), 1000, 'spatial-index') ",
	        	null, AccessContext.TEST);
	        assertNotNull(seq);
	        assertTrue(seq.getItemCount() > 1);    
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }
    }
    
    public void testLowLevelSearch() {
    	GMLHandlerJTS geometryHandler = new GeometryHandler(); 
        GMLFilterGeometry geometryFilter = new GMLFilterGeometry(geometryHandler); 
        GMLFilterDocument handler = new GMLFilterDocument(geometryFilter);
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	pool = BrokerPool.getInstance();
        	assertNotNull(pool);
	    	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
	    	assertNotNull(broker);
    	    	
	    	GMLHSQLIndexWorker indexWorker = (GMLHSQLIndexWorker)broker.getIndexController().getIndexWorkerById(GMLHSQLIndex.ID);
	        //Unplugged
	    	if (indexWorker == null)
	        	return;
	        
	    	SAXParserFactory factory = SAXParserFactory.newInstance();
	        factory.setNamespaceAware(true);
	        InputSource src = new InputSource(new StringReader(IN_MEMORY_GML));
	        SAXParser parser = factory.newSAXParser();
	        XMLReader reader = parser.getXMLReader();
	        SAXAdapter adapter = new SAXAdapter();
	        reader.setContentHandler(handler);
	        reader.setProperty("http://xml.org/sax/properties/lexical-handler", adapter);
	        reader.parse(src);
	        
	        String srsName = "osgb:BNG";
	        //provisional workaround
	        if ("osgb:BNG".equals(srsName))
    			srsName = "EPSG:27700";    		
            MathTransform mathTransform = indexWorker.getTransformToWGS84(srsName);
            if (mathTransform == null) {
        		fail("Unable to get a transformation from '" + srsName + "' to 'EPSG:4326'");        		           	
            }
            coordinateTransformer.setMathTransform(mathTransform);        
            Geometry wsg84_geometry = null;
            try {
            	wsg84_geometry = coordinateTransformer.transform(currentGeometry);
            } catch (TransformException e) {
        		fail(e.getMessage());
            }	        

            System.out.println(wsg84_geometry);
            
	        NodeSet ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.EQUALS);
	        assertTrue(ns.getLength() > 0);    
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.DISJOINT);
	        assertTrue(ns.getLength() > 0); 
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.INTERSECTS);
	        assertTrue(ns.getLength() > 0); 
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.TOUCHES);
	        //assertTrue(ns.getLength() > 0);  
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.CROSSES);
	        //assertTrue(ns.getLength() > 0);	  
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.WITHIN);
	        assertTrue(ns.getLength() > 0);		
	        ns = indexWorker.search(broker, null, wsg84_geometry, SpatialOperator.CONTAINS);
	        assertTrue(ns.getLength() > 0);			        
	        //ns = ((GMLIndexWorker)index.getWorker()).search(broker, wsg84_geometry, SpatialOperator.OVERLAPS);
	        //assertTrue(ns.getLength() > 0);			        
    	} catch (Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage()); 
        } finally {        	
            pool.release(broker);
        }    	    
    }
  
    public void testHighLevelSearch() {
    	GMLHandlerJTS geometryHandler = new GeometryHandler(); 
        GMLFilterGeometry geometryFilter = new GMLFilterGeometry(geometryHandler); 
        GMLFilterDocument handler = new GMLFilterDocument(geometryFilter); 
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	pool = BrokerPool.getInstance();
        	assertNotNull(pool);
	    	broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
	    	XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            String query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
            	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
            	"declare namespace gml = 'http://www.opengis.net/gml'; " +
            	"spatial:equals(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            Sequence seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:disjoint(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);     
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:intersects(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);    
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:touches(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            //assertTrue(seq.getItemCount() > 0); 
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:crosses(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            //assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:within(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:contains(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);   
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:overlaps(//gml:*, //gml:Point[gml:coordinates[. = '278697.450,187740.900']])";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            //assertTrue(seq.getItemCount() > 0); 
            
            //In-memory test
            query = "import module namespace spatial='http://exist-db.org/xquery/spatial' " +
        	"at 'java:org.exist.examples.indexing.spatial.module.SpatialModule'; " +
        	"declare namespace gml = 'http://www.opengis.net/gml'; " +
        	"spatial:equals(//gml:*, " + IN_MEMORY_GML + ")";
            seq = xquery.execute(query, null, AccessContext.TEST);
            assertNotNull(seq);		
            assertTrue(seq.getItemCount() > 0);            
            
    	} catch (Exception e) {
    		e.printStackTrace();
    		fail(e.getMessage()); 
        } finally {        	
            pool.release(broker);
        }    	    
    }    

    protected void setUp() {
        try {
            // initialize driver
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            
	    	Collection root =
	            DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
	        CollectionManagementService service =
	            (CollectionManagementService) root.getService(
	                "CollectionManagementService",
	                "1.0");
	        testCollection = service.createCollection(TEST_COLLECTION_NAME);
	        assertNotNull(testCollection);
	        
	        IndexQueryService idxConf = (IndexQueryService)
			testCollection.getService("IndexQueryService", "1.0");
	        idxConf.configureCollection(COLLECTION_CONFIG);	  
            
    	} catch (ClassNotFoundException e) {
    		e.printStackTrace();
        } catch (InstantiationException e) {
        	e.printStackTrace();
        } catch (IllegalAccessException e) {	
        	e.printStackTrace();
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    protected void tearDown() {   	
    	try {
    		DatabaseManager.deregisterDatabase(database);
        } catch (XMLDBException e) {
            e.printStackTrace();        
        }              
    }
    
    private class GeometryHandler extends XMLFilterImpl implements GMLHandlerJTS {
        public void geometry(Geometry geometry) {
        	currentGeometry = geometry;
        }
    }    
}
