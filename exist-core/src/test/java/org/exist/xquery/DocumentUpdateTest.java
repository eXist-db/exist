package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class DocumentUpdateTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	private static final String TEST_COLLECTION_NAME = "testup";
    private Collection testCollection;
    
    /**
     * Test if the doc, collection and document functions are correctly
     * notified upon document updates. Call a function once on the empty collection, 
     * then call it again after a document was added, and compare the results.
     */
	@Test
    public void update() throws XMLDBException {
    	String imports = 
    		"import module namespace xdb='http://exist-db.org/xquery/xmldb';\n" + 
    		"import module namespace util='http://exist-db.org/xquery/util';\n";

        //TEST 1: doc() function
        String query = imports +
            "declare function local:get-doc($path as xs:string) {\n" +
            "    if (doc-available($path)) then doc($path) else ()\n" +
            "};\n" +
            "let $col := xdb:create-collection('/db', 'testup')\n" +
            "let $path := '/db/testup/test1.xml'\n" +
            "let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
            "let $d1 := local:get-doc($path)\n" +
            "let $remove := xdb:remove('/db/testup', 'test1.xml')\n" +
            "return string-join((string(count(local:get-doc($path))), string(doc-available($path))), ' ')";
        String result = execQuery(query);
        assertEquals(result, "0 false");

        //TEST 2: doc()
        query = imports +
            "declare function local:get-doc($path as xs:string) {\n" +
            "    if (doc-available($path)) then doc($path) else ()\n" +
            "};\n" +
            "let $col := xdb:create-collection('/db', 'testup')\n" +
            "let $path := '/db/testup/test1.xml'\n" +
            "let $d1 := local:get-doc($path)\n" +
            "let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
            "return string-join((string(count(local:get-doc($path))), string(doc-available($path))), ' ')";
        result = execQuery(query);
        assertEquals(result, "1 true");

        //TEST 3: collection()
        query = imports +
            "declare function local:xpath($collection as xs:string) {\n" +
            "    for $c in collection($collection) return $c//n\n" +
            "};\n" +
            "let $col := xdb:create-collection('/db', 'testup')\n" +
            "let $path := '/db/testup'\n" +
            "let $d1 := local:xpath($path)//n/text()\n" +
            "let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
            "return local:xpath($path)/text()";
        result = execQuery(query);
        assertEquals(result, "1");

        //TEST 4: 'update insert' statement
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

        //TEST 5: 'update replace' statement
        query = imports + "let $doc := xdb:store('/db', 'test1.xml', " +
            "<test> " +
                "<link href=\"features\"/> " +
                "(: it works with only 1 link :) " +
                "<link href=\"features/test\"/> " +
            "</test>) " +
            "let $links := doc($doc)/test/link/@href " +
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
    }

    @Test
    public void updateAttribute() throws XMLDBException {
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

        String result1 = execQuery(query1);
        String result2 = execQuery(query2);

    }
    
    private String execQuery(String query) throws XMLDBException {
    	XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
    	ResourceSet result = service.query(query);
    	assertEquals(result.getSize(), 1);
    	return result.getResource(0).getContent().toString();
    }

    @Before
    public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        CollectionManagementService service =
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }

    @After
    public void tearDown() throws XMLDBException {
        CollectionManagementService service =
            (CollectionManagementService) existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
