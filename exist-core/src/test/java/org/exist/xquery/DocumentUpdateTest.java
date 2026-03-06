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
        // Under W3C XQuery Update, updating and non-updating expressions cannot
        // be mixed in a comma expression (XUST0001). Split into update + read.
        query = imports +
            "let $col := xdb:create-collection('/db', 'testup')\n" +
            "let $path := '/db/testup'\n" +
            "let $doc := xdb:store($col, 'test1.xml', <test><n>1</n></test>)\n" +
            "return insert node <n>2</n> into collection($path)/test";
        XQueryService service1 = testCollection.getService(XQueryService.class);
        service1.query(query);
        result = execQuery("count(collection('/db/testup')//n)");
        assertEquals(result, "2");

        //TEST 5: 'update replace' statement
        // Under W3C XQuery Update, split update + read to avoid XUST0001.
        query = imports + "let $doc := xdb:store('/db', 'test1.xml', " +
            "<test> " +
                "<link href=\"features\"/> " +
                "(: it works with only 1 link :) " +
                "<link href=\"features/test\"/> " +
            "</test>) " +
            "let $links := doc($doc)/test/link/@href " +
            "return " +
            "for $link in $links " +
            "return replace value of node $link with '123'";
        XQueryService service = testCollection.getService(XQueryService.class);
        service.query(query);
        ResourceSet r = service.query("doc('/db/test1.xml')/test/link/@href/string()");
        assertEquals(r.getSize(), 2);
        assertEquals(r.getResource(0).getContent().toString(), "123");
        assertEquals(r.getResource(1).getContent().toString(), "123");
    }

    @Test
    public void updateAttribute() throws XMLDBException {
        // Under W3C XQuery Update, split update + read to avoid XUST0001.
        String query1a ="let $content :="
                +"<A><B><C d=\"xxx\">ccc1</C><C d=\"yyy\" e=\"zzz\">ccc2</C></B></A> "
                +"let $uri := xmldb:store(\"/db/\", \"marktest7.xml\", $content) "
                +"return delete node doc($uri)//@*";
        XQueryService service2 = testCollection.getService(XQueryService.class);
        service2.query(query1a);
        String result1 = execQuery("doc(\"/db/marktest7.xml\")");

        String query2a ="let $doc := doc(\"/db/marktest7.xml\") "
                +"return "
                +"for $elem in $doc//* "
                +"return insert node attribute AAA {\"BBB\"} into $elem";
        service2.query(query2a);
        String result2 = execQuery("doc(\"/db/marktest7.xml\")");

    }
    
    private String execQuery(String query) throws XMLDBException {
    	XQueryService service = testCollection.getService(XQueryService.class);
    	ResourceSet result = service.query(query);
    	assertEquals(result.getSize(), 1);
    	return result.getResource(0).getContent().toString();
    }

    @Before
    public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        CollectionManagementService service = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }

    @After
    public void tearDown() throws XMLDBException {
        CollectionManagementService service = existEmbeddedServer.getRoot().getService(CollectionManagementService.class);
        service.removeCollection(TEST_COLLECTION_NAME);
        testCollection = null;
    }
}
