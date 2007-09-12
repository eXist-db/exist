package org.exist.xquery;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

public class DocumentUpdateTest extends TestCase {

	private static final String TEST_COLLECTION_NAME = "testup";
    
    private Database database;
    private Collection testCollection;
    
    /**
     * Test if the doc, collection and document functions are correctly
     * notified upon document updates. Call a function once on the empty collection, 
     * then call it again after a document was added, and compare the results.
     */
    public void testUpdate() {
    	String imports = 
    		"import module namespace xdb='http://exist-db.org/xquery/xmldb';\n" + 
    		"import module namespace util='http://exist-db.org/xquery/util';\n";
    	
		try {
			System.out.println("-- TEST 1: doc() function --");
			String query = imports +
	    		"declare function local:get-doc($path as xs:string) {\n" + 
	    		"    doc($path)\n" + 
	    		"};\n" +
	    		"let $col := xdb:create-collection('/db', 'testup')\n" + 
	    		"let $path := '/db/testup/test1.xml'\n" +
	    		"let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
	    		"let $d1 := local:get-doc($path)\n" +
	    		"let $remove := xdb:remove('/db/testup', 'test1.xml')\n" +
	    		"return string-join((string(count(local:get-doc($path))), string(doc-available($path))), ' ')";
			String result = execQuery(query);
			assertEquals(result, "0 false");
			
			System.out.println("-- TEST 2: xmldb:document() function --");
			query = imports +
	    		"declare function local:get-doc($path as xs:string) {\n" + 
	    		"    xmldb:document($path)\n" + 
	    		"};\n" +
	    		"let $col := xdb:create-collection('/db', 'testup')\n" + 
	    		"let $path := '/db/testup/test1.xml'\n" +
	    		"let $d1 := local:get-doc($path)\n" +
	    		"let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" + 
	    		"return string-join((string(count(local:get-doc($path))), string(doc-available($path))), ' ')";
			result = execQuery(query);
			assertEquals(result, "1 true");
			
			System.out.println("-- TEST 3: collection() function --");
			query = imports +
				"declare function local:xpath($collection as xs:string) {\n" + 
	    		"    for $c in collection($collection) return $c//n\n" + 
	    		"};\n" +
	    		"let $col := xdb:create-collection('/db', 'testup')\n" + 
	    		"let $path := '/db/testup'\n" +
	    		"let $d1 := local:xpath($path)//n/text()\n" +
	    		"let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" + 
	    		"return local:xpath($path)//n/text()";
			result = execQuery(query);
			assertEquals(result, "1");
		
			System.out.println("-- TEST 4: 'update insert' statement --");
			query = imports +
				"declare function local:xpath($collection as xs:string) {\n" + 
	    		"    collection($collection)\n" + 
	    		"};\n" +
	    		"let $col := xdb:create-collection('/db', 'testup')\n" + 
	    		"let $path := '/db/testup'\n" +
	    		"let $d1 := local:xpath($path)//n\n" +
	    		"let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
	    		"return (\n" +
	    		"	update insert <n>2</n> into collection($path)/test,\n" +
	    		"	count(local:xpath($path)//n)\n" +
	    		")";
			result = execQuery(query);
			assertEquals(result, "2");

			System.out.println("-- TEST 5: 'update replace' statement --");
			query = "let $doc := " + 
				"<test> " +
					"<link href=\"features\"/> " +
					"(: it works with only 1 link :) " +
					"<link href=\"features/test\"/> " +
				"</test> " +
				"let $links := $doc/link/@href " +
				"return " + 
				"for $link in $links " +  
				"return ( " +
					"update replace $link with \"123\", " +
					"(: without the output on the next line, it works :) " +
					"xs:string($link) " +
				")";
	    	XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
	    	ResourceSet r = service.query(query);
	    	assertEquals(r.getSize(), 2);	    	
			assertEquals(r.getResource(0).getContent().toString(), "123");
			assertEquals(r.getResource(1).getContent().toString(), "123");
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }
    
    public void testUpdateAttribute(){
        String query1="let $content :="
                +"<A><B><C d=\"xxx\">ccc1</C><C d=\"yyy\" e=\"zzz\">ccc2</C></B></A> "
                +"let $uri := xmldb:store(\"/db/\", \"marktest7.xml\", $content) "
                +"let $doc := doc($uri) "
                +"let $xxx := update delete $doc//@*"
                +"return $doc";

        String query2="let $doc := doc(\"/db/marktest7.xml\") "
                +"return "
                +"( for $elem in $doc//* "
                +"return update insert attribute AAA {\"BBB\"} into $elem, $doc) ";

        try {
            String result1 = execQuery(query1);
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        try {   
            String result2 = execQuery(query2);
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

    }
    
    private String execQuery(String query) throws XMLDBException {
    	XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
    	ResourceSet result = service.query(query);
    	assertEquals(result.getSize(), 1);
    	return result.getResource(0).getContent().toString();
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
		TestRunner.run(DocumentUpdateTest.class);
	}
}
