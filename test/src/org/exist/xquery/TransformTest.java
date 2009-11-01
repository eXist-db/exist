package org.exist.xquery;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;


public class TransformTest extends TestCase {
	private static final String TEST_COLLECTION_NAME = "transform-test";
    
    private Database database;
    private Collection testCollection;
    
    /**
     * Tests relative path resolution when parsing stylesheets in
     * the transform:transform function.
     */
    public void testTransform() {
    	String imports = 
    		"import module namespace transform='http://exist-db.org/xquery/transform';\n";
    	
		try {
			System.out.println("-- transform:transform with relative stylesheet paths");
			String query = 
				"import module namespace transform='http://exist-db.org/xquery/transform';\n" +
				"let $xml := <empty />,\n" +
				"	$xsl := 'xmldb:exist:///db/"+TEST_COLLECTION_NAME+"/xsl1/1.xsl'\n" +
				"return transform:transform($xml, $xsl, ())";
			String result = execQuery(query);
			System.out.println("RESULT = "+result);
			assertEquals(result, "<doc>" +
					"<p>Start Template 1</p>" +
					"<p>Start Template 2</p>" +
					"<p>Template 3</p>" +
					"<p>End Template 2</p>" +
					"<p>Template 3</p>" +
					"<p>End Template 1</p>" +
					"</doc>");
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }
    
    
    private String execQuery(String query) throws XMLDBException {
    	XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
        service.setProperty("indent", "no");
    	ResourceSet result = service.query(query);
    	assertEquals(result.getSize(), 1);
    	return result.getResource(0).getContent().toString();
    }
    
    private void addXMLDocument(Collection c, String doc, String id) throws XMLDBException {
    	Resource r = c.createResource(id, XMLResource.RESOURCE_TYPE);
    	r.setContent(doc);
    	((EXistResource) r).setMimeType("text/xml");
    	c.storeResource(r);
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
            
            service =
                (CollectionManagementService) testCollection.getService(
                    "CollectionManagementService",
                    "1.0");
            
            Collection xsl1 = service.createCollection("xsl1");
            assertNotNull(xsl1);
            
            Collection xsl3 = service.createCollection("xsl3");
            assertNotNull(xsl3);
            
            service =
                (CollectionManagementService) xsl1.getService(
                    "CollectionManagementService",
                    "1.0");
            
            Collection xsl2 = service.createCollection("xsl2");
            assertNotNull(xsl2);
            
            
            String	doc1 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
            "<xsl:import href='xsl2/2.xsl' />\n" +
            "<xsl:template match='/'>\n" +
            "<doc>" +
            "<p>Start Template 1</p>" +
            "<xsl:call-template name='template-2' />" +
            "<xsl:call-template name='template-3' />" +
            "<p>End Template 1</p>" +
            "</doc>" +
            "</xsl:template>" +
            "</xsl:stylesheet>";

            String doc2 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
            "<xsl:import href='../../xsl3/3.xsl' />\n" +
            "<xsl:template name='template-2'>\n" +
            "<p>Start Template 2</p>" +
            "<xsl:call-template name='template-3' />" +
            "<p>End Template 2</p>" +
            "</xsl:template>" +
            "</xsl:stylesheet>";
            
            String	doc3 = "<?xml version='1.0' encoding='UTF-8'?>\n" +
            "<xsl:stylesheet xmlns:xsl='http://www.w3.org/1999/XSL/Transform' version='1.0'>\n"+
            "<xsl:template name='template-3'>\n" +
            "<p>Template 3</p>" +
            "</xsl:template>" +
            "</xsl:stylesheet>";
 
            addXMLDocument(xsl1, doc1, "1.xsl");
            addXMLDocument(xsl2, doc2, "2.xsl");
            addXMLDocument(xsl3, doc3, "3.xsl");
        } catch (ClassNotFoundException e) {
        } catch (InstantiationException e) {
        } catch (IllegalAccessException e) {
        } catch (XMLDBException e) {
            e.printStackTrace();
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    protected void tearDown() {
    	try {
	        Collection root =
	            DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
	        CollectionManagementService service =
	            (CollectionManagementService) root.getService(
	                "CollectionManagementService",
	                "1.0");
	        service.removeCollection(TEST_COLLECTION_NAME);
	        
	        DatabaseManager.deregisterDatabase(database);
	        DatabaseInstanceManager dim =
	            (DatabaseInstanceManager) testCollection.getService(
	                "DatabaseInstanceManager", "1.0");
	        dim.shutdown();
            database = null;
            testCollection = null;
	        System.out.println("tearDown PASSED");
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
    }
    
    public static void main(String[] args) {
		TestRunner.run(TransformTest.class);
	}
}
