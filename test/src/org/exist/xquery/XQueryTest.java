/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.custommonkey.xmlunit.DetailedDiff;
import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.TestUtils;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import javax.xml.transform.OutputKeys;
import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

/** I propose that we put here in XQueryTest the tests involving all the 
 * others constructs of the XQuery language, besides XPath expressions.
 * And in {@link XPathQueryTest} we will put the tests involving only XPath expressions.
 * TODO maybe move the various eXist XQuery extensions in another class ... */
public class XQueryTest extends XMLTestCase {

    private static final String NUMBERS_XML = "numbers.xml";
    private static final String BOWLING_XML = "bowling.xml";
    private static final String attributesSERIALIZATION = "attributes_serialization.xml";
    private static final String MODULE1_NAME = "module1.xqm";
    private static final String MODULE2_NAME = "module2.xqm";
    private static final String MODULE3_NAME = "module3.xqm";
    private static final String MODULE4_NAME = "module4.xqm";
    private static final String MODULE5_NAME = "module5.xqm";
    private static final String MODULE6_NAME = "module6.xqm";
    private static final String MODULE7_NAME = "module7.xqm";
    private static final String MODULE8_NAME = "module8.xqm";
    private static final String FATHER_MODULE_NAME = "father.xqm";
    private static final String CHILD1_MODULE_NAME = "child1.xqm";
    private static final String CHILD2_MODULE_NAME = "child2.xqm";
    private static final String NAMESPACED_NAME = "namespaced.xml";
    private final static String URI = XmldbURI.LOCAL_DB;
    private final static String numbers =
            "<test>" + "<item id='1'><price>5.6</price><stock>22</stock></item>" + "<item id='2'><price>7.4</price><stock>43</stock></item>" + "<item id='3'><price>18.4</price><stock>5</stock></item>" + "<item id='4'><price>65.54</price><stock>16</stock></item>" + "</test>";
    private final static String module1 =
            "module namespace blah=\"blah\";\n" + "declare variable $blah:param {\"value-1\"};";
    private final static String module2 =
            "module namespace foo=\"\";\n" + "declare variable $foo:bar {\"bar\"};";
    private final static String module3 =
            "module namespace foo=\"foo\";\n" + "declare variable $bar:bar {\"bar\"};";
    private final static String module4 =
            "module namespace foo=\"foo\";\n" //An external prefix in the statically known namespaces
            + "declare variable $exist:bar external;\n" + "declare function foo:bar() {\n" + "$exist:bar\n" + "};";
    private final static String module5 =
            "module namespace foo=\"foo\";\n" + "declare variable $foo:bar := \"bar\";";
    private final static String module6 =
            "module namespace foo=\"foo\";\n" + "declare variable $foo:bar := \"bar\";" + "declare variable $foo:bar := \"bar\";";
    private final static String module7 =
            "module namespace foo=\"foo\";\n" +
            "declare namespace xhtml=\"http://www.w3.org/1999/xhtml\";\n" +
            "declare function foo:link() { <a href='#'>Link</a> };" +
            "declare function foo:copy($node) { element { node-name($node) } { $node/text() } };";
    private final static String module8 =
            "module namespace dr = \"double-root2\"; \n"
            +"declare function dr:documentIn() as document-node() { \n"
            +" let $doc :=  <root> <contents/> </root> \n"
            +" return document { $doc } \n" 
            +"};";
    
    private final static String fatherModule =
            "module namespace foo=\"foo\";\n" + "import module namespace foo1=\"foo1\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n" + "import module namespace foo2=\"foo2\" at \"" + URI + "/test/" + CHILD2_MODULE_NAME + "\";\n" + "declare variable $foo:bar { \"bar\" };\n " + "declare variable $foo:bar1 { $foo1:bar };\n" + "declare variable $foo:bar2 { $foo2:bar };\n";
    private final static String child1Module =
            "module namespace foo=\"foo1\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "declare variable $foo:bar {\"bar1\"};";
    private final static String child2Module =
            "module namespace foo=\"foo2\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "declare variable $foo:bar {\"bar2\"};";
    private final static String namespacedDocument =
            "<rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" \n" +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" \n" +
            "xmlns:x=\"http://exist.sourceforge.net/dc-ext\"> \n" +
            "<rdf:Description id=\"3\"> \n" +
            "<dc:title>title</dc:title> \n" +
            "<dc:creator>creator</dc:creator> \n" +
            "<x:place>place</x:place> \n" +
            "<x:edition>place</x:edition> \n" +
            "</rdf:Description> \n" +
            "</rdf:RDF>";
    private final static String bowling =
            "<series>" +
            "<game>" +
            "<frame/>" +
            "</game>" +
            "<game>" +
            "<frame/>" +
            "</game>" +
            "</series>";
    private final static String attributes =
        "<blob>" +
        "<test att='a' />" +
        "<test att='b' />" +
        "<test att='c' />" +
        "</blob>";
    private static String attributeXML;
    private static int stringSize = 512;
    private static int nbElem = 1;
    private String file_name = "detail_xml.xml";
    private String xml;
    private Database database;

    public XQueryTest(String arg0) {
        super(arg0);
    }

    public void setUp() {
        try {
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            Collection root =
                    DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
            CollectionManagementService service =
                    (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection testCollection = service.createCollection("test");
            assertNotNull(testCollection);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
     * @see TestCase#tearDown()
     */
    public void tearDown() throws Exception {
        // testCollection.removeResource( testCollection .getResource(file_name));
        TestUtils.cleanupDB();
        DatabaseInstanceManager dim =
                (DatabaseInstanceManager) DatabaseManager.getCollection("xmldb:exist:///db", "admin", null).getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        DatabaseManager.deregisterDatabase(database);
        database = null;

        System.out.println("tearDown PASSED");
    }

    private Collection getTestCollection() throws XMLDBException {
        return DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", null);
    }

    public void testLet() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            //Non null context sequence
            System.out.println("testLet 1: ========");
            query = "/test/item[let $id := ./@id return $id]";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            assertEquals("XQuery: " + query, 4, result.getSize());

            System.out.println("testLet 2: ========");
            query = "/test/item[let $id := ./@id return not(/test/set[@id=$id])]";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            assertEquals("XQuery: " + query, 4, result.getSize());

            System.out.println("testLet 3: ========");
            query = "let $test := <test><a> a </a><a>a</a></test> " +
                    "return distinct-values($test/a/normalize-space(.))";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());

            //Ordered value sequence
            System.out.println("testLet 4: ========");
            query = "let $unordset := (for $val in reverse(1 to 100) return " +
                    "<value>{$val}</value>)" +
                    "let $ordset := (for $newval in $unordset " +
                    "where $newval mod 2 eq 1 " +
                    "order by $newval " +
                    "return $newval/text()) " +
                    "return $ordset/ancestor::node()";

            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            assertEquals("XQuery: " + query, 50, result.getSize());

            //WARNING : the return order CHANGES !!!!!!!!!!!!!!!!!!

            assertXMLEqual("<value>99</value>", ((XMLResource) result.getResource(0)).getContent().toString());
            assertXMLEqual("<value>1</value>", ((XMLResource) result.getResource(49)).getContent().toString());

        } catch (Exception e) {
            System.out.println("testLet(): XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testFor() {
        ResourceSet result;
        String query;
        XMLResource resu;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testFor 1: ========");
            query = "for $f in /*/item return $f";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            assertEquals("XQuery: " + query, 4, result.getSize());

            System.out.println("testFor 2: ========");
            query = "for $f in /*/item  order by $f ascending  return $f";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "3", ((Element) resu.getContentAsDOM()).getAttribute("id"));

            System.out.println("testFor 3: ========");
            query = "for $f in /*/item  order by $f descending  return $f";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "2", ((Element) resu.getContentAsDOM()).getAttribute("id"));

            System.out.println("testFor 4: ========");
            query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "4", ((Element) resu.getContentAsDOM()).getAttribute("id"));

            System.out.println("testFor 5: ========");
            query = "for $f in //item where $f/@id = '3' return $f";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "3", ((Element) resu.getContentAsDOM()).getAttribute("id"));

            //Non null context sequence
            System.out.println("testFor 6: ========");
            query = "/test/item[for $id in ./@id return $id]";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, 4, result.getSize());

            //Ordered value sequence
            System.out.println("testFor 7: ========");
            query = "let $doc := <doc><value>Z</value><value>Y</value><value>X</value></doc> " +
                    "return " +
                    "let $ordered_values := " +
                    "	for $value in $doc/value order by $value ascending " +
                    "	return $value " +
                    "for $value in $doc/value " +
                    "	return $value[. = $ordered_values[position() = 1]]";

            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "<value>X</value>", resu.getContent());

            //Ordered value sequence
            System.out.println("testFor 8: ========");
            query = "for $e in (1) order by $e return $e";
            result = service.queryResource(NUMBERS_XML, query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "1", resu.getContent());

        } catch (XMLDBException e) {
            System.out.println("testFor(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testRecursion() {
        try {
            String q1 =
                    "declare function local:append($head, $i) {\n" +
                    "   if ($i < 5000) then\n" +
                    "       local:append(($head, $i), $i + 1)\n" +
                    "   else\n" +
                    "       $head\n" +
                    "};\n" +
                    "local:append((), 0)";
            XPathQueryService service =
                    (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService",
                    "1.0");
            ResourceSet result = service.query(q1);
            assertEquals(result.getSize(), 5000);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testConstructedNode1() {
        try {
            String q1 =
                    "let $a := <A/> for $b in $a//B/string() return \"Oops!\"";
            XPathQueryService service =
                    (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService",
                    "1.0");
            ResourceSet result = service.query(q1);
            assertEquals(0, result.getSize());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testCombiningNodeSequences() {
        ResourceSet result;
        String query;

        try {
            XPathQueryService service =
                    (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService",
                    "1.0");

            System.out.println("testCombiningNodeSequences 1: ========");
            query = "let $a := <a/> \n" +
                    "let $aa := ($a, $a) \n" +
                    "for $b in ($aa intersect $aa \n)" +
                    "return $b";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<a/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testCombiningNodeSequences 2: ========");
            query = "let $a := <a/> \n" +
                    "let $aa := ($a, $a) \n" +
                    "for $b in ($aa union $aa \n)" +
                    "return $b";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<a/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testCombiningNodeSequences 3: ========");
            query = "let $a := <a/> \n" +
                    "let $aa := ($a, $a) \n" +
                    "for $b in ($aa except $aa \n)" +
                    "return $b";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 0, result.getSize());


        } catch (XMLDBException e) {
            System.out.println("testCombiningNodeSequences(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    // Testcase by Gev
    public void bugtestInMemoryNodeSequences() {
        ResourceSet result;
        String query;

        try {
            XPathQueryService service =
                    (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService",
                    "1.0");

            System.out.println("testInMemoryNodeSequences 1: ========");
            query = "let $c := (<a/>,<b/>) return <t>text{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 2: ========");
            query = "let $c := (<a/>,<b/>) return <t><text/>{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t><text/><a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 3: ========");
            query = "let $c := (<a/>,<b/>) return <t>{\"text\"}{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 4: ========");
            query = "let $c := (<a/>,\"b\") return <t>text{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 5: ========");
            query = "let $c := (<a/>,\"b\") return <t><text/>{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t><text/><a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 6: ========");
            query = "let $c := (<a/>,\"b\") return <t>{\"text\"}{$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 7: ========");
            query = "let $c := (<a/>,<b/>) return <t>{<text/>,$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 8: ========");
            query = "let $c := (<a/>,<b/>) return <t>{\"text\",$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 9: ========");
            query = "let $c := (<a/>,\"b\") return <t>{<text/>,$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

            System.out.println("testInMemoryNodeSequences 10: ========");
            query = "let $c := (<a/>,\"b\") return <t>{\"text\",$c[1]}</t>";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, "<t>text<a/></t>", result.getResource(0).getContent());

        } catch (XMLDBException e) {
            System.out.println("testInMemoryNodeSequences(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testVariable() {
        ResourceSet result;
        String query;
        XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testVariable 1: ========");
            query = "xquery version \"1.0\";\n" + "declare namespace param=\"param\";\n" + "declare variable $param:a {\"a\"};\n" + "declare function param:a() {$param:a};\n" + "let $param:a := \"b\" \n" + "return ($param:a, $param:a)";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, "b", ((XMLResource) result.getResource(0)).getContent());
            assertEquals("XQuery: " + query, "b", ((XMLResource) result.getResource(1)).getContent());

            System.out.println("testVariable 2: ========");
            query = "xquery version \"1.0\";\n" + "declare namespace param=\"param\";\n" + "declare variable $param:a {\"a\"};\n" + "declare function param:a() {$param:a};\n" + "let $param:a := \"b\" \n" + "return param:a(), param:a()";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, "a", ((XMLResource) result.getResource(0)).getContent());
            assertEquals("XQuery: " + query, "a", ((XMLResource) result.getResource(1)).getContent());

            System.out.println("testVariable 3: ========");
            query = "declare variable $foo {\"foo1\"};\n" + "let $foo := \"foo2\" \n" + "for $bar in (1 to 1) \n" + "  let $foo := \"foo3\" \n" + "  return $foo";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "foo3", ((XMLResource) result.getResource(0)).getContent());

            try {
                message = "";
                System.out.println("testVariable 4 ========");
                query = "xquery version \"1.0\";\n" + "declare variable $a {\"1st instance\"};\n" + "declare variable $a {\"2nd instance\"};\n" + "$a";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQST0049") > -1);

            System.out.println("testVariable 5: ========");
            query = "xquery version \"1.0\";\n" + "declare namespace param=\"param\";\n" + "declare function param:f() { $param:a };\n" + "declare variable $param:a {\"a\"};\n" + "param:f()";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "a", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testVariable 6: ========");
            query = "let $a := <root> " +
                    "<b name='1'>" +
                    "  <c name='x'> " +
                    "    <bar name='2'/> " +
                    "    <bar name='3'> " +
                    "      <bar name='4'/> " +
                    "    </bar> " +
                    "  </c> " +
                    "</b> " +
                    "</root> " +
                    "let $b := for $bar in $a/b/c/bar " +
                    "where ($bar/../@name = 'x') " +
                    "return $bar " +
                    "return $b";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "2", ((Element) resu.getContentAsDOM()).getAttribute("name"));
            resu = (XMLResource) result.getResource(1);
            assertEquals("XQuery: " + query, "3", ((Element) resu.getContentAsDOM()).getAttribute("name"));

        } catch (XMLDBException e) {
            System.out.println("testVariable : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testVirtualNodesets() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
            service.setProperty(OutputKeys.INDENT, "no");

            query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant::*/attribute::id " +
                    "return <a>{$node}</a>";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertXMLEqual("<a id='cool'/>", ((XMLResource) result.getResource(0)).getContent().toString());

            query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant-or-self::*/child::b " +
                    "return <a>{$node}</a>";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertXMLEqual("<a><b id='cool'/></a>", ((XMLResource) result.getResource(0)).getContent().toString());

            query = "let $node := (<c id='OK'><b id='cool'/></c>)/descendant-or-self::*/descendant::b " +
                    "return <a>{$node}</a>";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertXMLEqual("<a><b id='cool'/></a>", ((XMLResource) result.getResource(0)).getContent().toString());

            query = "let $doc := <a id='a'><b id='b'/></a> " +
                    "return $doc/*/(<id>{@id}</id>)";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertXMLEqual("<id id='b' />", ((XMLResource) result.getResource(0)).getContent().toString());

        } catch (Exception e) {
            System.out.println("testVirtualNodesets : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testWhereClause() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);
            service.setProperty(OutputKeys.INDENT, "no");

            query = "let $a := element node1 { " +
                    "attribute id {'id'}, " +
                    "element node1 {'1'}, " +
                    "element node2 {'2'} " +
                    "} " +
                    "for $x in $a " +
                    "where $x/@id eq 'id' " +
                    "return $x";
            result = service.queryResource(NUMBERS_XML, query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertXMLEqual("<node1 id='id'><node1>1</node1><node2>2</node2></node1>",
                    ((XMLResource) result.getResource(0)).getContent().toString());

        } catch (Exception e) {
            System.out.println("testWhereClause : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testTypedVariables() {
        ResourceSet result;
        String query;
        boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testTypedVariables 1: ========");
            query = "let $v as element()* := ( <assign/> , <assign/> )\n" + "let $w := <r>{ $v }</r>\n" + "let $x as element()* := $w/assign\n" + "return $x";
            result = service.query(query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType());
            assertEquals("XQuery: " + query, "assign", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName());

            System.out.println("testTypedVariables 2: ========");
            query = "let $v as node()* := ()\n" + "return $v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 3: ========");
            query = "let $v as item()* := ()\n" + "return $v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 4: ========");
            query = "let $v as empty() := ()\n" + "return $v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 5: ========");
            query = "let $v as item() := ()\n" + "return $v";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue("XQuery: " + query, exceptionThrown);

            System.out.println("testTypedVariables 6: ========");
            query = "let $v as item()* := ( <a/> , 1 )\n" + "return $v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType());
            assertEquals("XQuery: " + query, "a", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName());
            assertEquals("XQuery: " + query, "1", ((XMLResource) result.getResource(1)).getContent());

            System.out.println("testTypedVariables 7: ========");
            query = "let $v as node()* := ( <a/> , 1 )\n" + "return $v";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            System.out.println("testTypedVariables 8: ========");
            query = "let $v as item()* := ( <a/> , 1 )\n" + "let $w as element()* := $v\n" + "return $w";
            try {
                exceptionThrown = false;
                result = service.query(query);
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            System.out.println("testTypedVariables 9: ========");
            query = "declare variable $v as element()* {( <assign/> , <assign/> ) };\n" + "declare variable $w { <r>{ $v }</r> };\n" + "declare variable $x as element()* { $w/assign };\n" + "$x";
            result = service.query(query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType());
            assertEquals("XQuery: " + query, "assign", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName());

            System.out.println("testTypedVariables 10: ========");
            query = "declare variable $v as node()* { () };\n" + "$v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 11: ========");
            query = "declare variable $v as item()* { () };\n" + "$v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 12: ========");
            query = "declare variable $v as empty() { () };\n" + "$v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testTypedVariables 13: ========");
            query = "declare variable $v as item() { () };\n" + "$v";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue("XQuery: " + query, exceptionThrown);

            System.out.println("testTypedVariables 14: ========");
            query = "declare variable $v as item()* { ( <a/> , 1 ) }; \n" + "$v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeType());
            assertEquals("XQuery: " + query, "a", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName());
            assertEquals("XQuery: " + query, "1", ((XMLResource) result.getResource(1)).getContent());

            System.out.println("testTypedVariables 15: ========");
            query = "declare variable $v as node()* { ( <a/> , 1 ) };\n" + "$v";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            System.out.println("testTypedVariables 16: ========");
            query = "declare variable $v as item()* { ( <a/> , 1 ) };\n" + "declare variable $w as element()* { $v };\n" + "$w";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            System.out.println("testTypedVariables 15: ========");
            query = "let $v as document-node() :=  doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "') \n" + "return $v";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            //TODO : no way to test the node type ?
            //assertEquals( "XQuery: " + query, Node.DOCUMENT_NODE, ((XMLResource)result.getResource(0)));
            assertEquals("XQuery: " + query, "test", ((XMLResource) result.getResource(0)).getContentAsDOM().getNodeName());

        } catch (XMLDBException e) {
            System.out.println("testTypedVariables : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testPrecedence() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testPrecedence 1: ========");
            query = "xquery version \"1.0\";\n" + "declare namespace blah=\"blah\";\n" + "declare variable $blah:param  {\"value-1\"};\n" + "let $blah:param := \"value-2\"\n" + "(:: FLWOR expressions have a higher precedence than the comma operator ::)\n" + "return $blah:param, $blah:param ";
            result = service.query(query);
            assertEquals("XQuery: " + query, 2, result.getSize());
            assertEquals("XQuery: " + query, "value-2", ((XMLResource) result.getResource(0)).getContent());
            assertEquals("XQuery: " + query, "value-1", ((XMLResource) result.getResource(1)).getContent());

        } catch (XMLDBException e) {
            System.out.println("testTypedVariables : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testImprobableAxesAndNodeTestsCombinations() {
        ResourceSet result;
        String query;
        boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testImprobableAxesAndNodeTestsCombinations 1: ========");
            query = "let $a := <x>a<!--b-->c</x>/self::comment() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 2: ========");
            query = "let $a := <x>a<!--b-->c</x>/parent::comment() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 3: ========");
            query = "let $a := <x>a<!--b-->c</x>/ancestor::comment() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 4: ========");
            query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::comment() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

//			This one is intercepted by the parser
            System.out.println("testImprobableAxesAndNodeTestsCombinations 5: ========");
            query = "let $a := <x>a<!--b-->c</x>/attribute::comment() return <z>{$a}</z>";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

//			This one is intercepted by the parser
            System.out.println("testImprobableAxesAndNodeTestsCombinations 6: ========");
            query = "let $a := <x>a<!--b-->c</x>/namespace::comment() return <z>{$a}</z>";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            System.out.println("testImprobableAxesAndNodeTestsCombinations 7: ========");
            query = "let $a := <x>a<!--b-->c</x>/self::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 8: ========");
            query = "let $a := <x>a<!--b-->c</x>/parent::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 9: ========");
            query = "let $a := <x>a<!--b-->c</x>/ancestor::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 10: ========");
            query = "let $a := <x>a<!--b-->c</x>/ancestor-or-self::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 11: ========");
            query = "let $a := <x>a<!--b-->c</x>/child::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 12: ========");
            query = "let $a := <x>a<!--b-->c</x>/descendant::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 13: ========");
            query = "let $a := <x>a<!--b-->c</x>/descendant-or-self::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 14: ========");
            query = "let $a := <x>a<!--b-->c</x>/preceding::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 15: ========");
            query = "let $a := <x>a<!--b-->c</x>/preceding-sibling::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 16: ========");
            query = "let $a := <x>a<!--b-->c</x>/following::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testImprobableAxesAndNodeTestsCombinations 17: ========");
            query = "let $a := <x>a<!--b-->c</x>/following-sibling::attribute() return <z>{$a}</z>";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<z/>", ((XMLResource) result.getResource(0)).getContent());

//			This one is intercepted by the parser
            System.out.println("testImprobableAxesAndNodeTestsCombinations 18: ========");
            query = "let $a := <x>a<!--b-->c</x>/namespace::attribute() return <z>{$a}</z>";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

            //TODO : uncomment when PI are OK

            /*
            System.out.println("testImprobableAxesAndNodeTestsCombinations 19: ========" );
            query = "let $a := <x>a<?foo ?>c</x>/self::processing-instruction('foo') return <z>{$a}</z>";
            result = service.query(query);				
            assertEquals( "XQuery: " + query, 1, result.getSize() );
            assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
            
            System.out.println("testImprobableAxesAndNodeTestsCombinations 20: ========" );
            query = "let $a := <x>a<?foo ?>c</x>/parent::processing-instruction('foo') return <z>{$a}</z>";
            result = service.query(query);				
            assertEquals( "XQuery: " + query, 1, result.getSize() );
            assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
            
            System.out.println("testImprobableAxesAndNodeTestsCombinations 21: ========" );
            query = "let $a := <x>a<?foo ?>c</x>/ancestor::processing-instruction('foo') return <z>{$a}</z>";
            result = service.query(query);				
            assertEquals( "XQuery: " + query, 1, result.getSize() );
            assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());
            
            System.out.println("testImprobableAxesAndNodeTestsCombinations 22: ========" );
            query = "let $a := <x>a<?foo ?>c</x>/ancestor-or-self::processing-instruction('foo') return <z>{$a}</z>";
            result = service.query(query);				
            assertEquals( "XQuery: " + query, 1, result.getSize() );
            assertEquals( "XQuery: " + query, "<z/>", ((XMLResource)result.getResource(0)).getContent());	
             */

//			This one is intercepted by the parser
            System.out.println("testImprobableAxesAndNodeTestsCombinations 23: ========");
            query = "let $a := <x>a<?foo ?>c</x>/attribute::processing-instruction('foo') return <z>{$a}</z>";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

//			This one is intercepted by the parser
            System.out.println("testImprobableAxesAndNodeTestsCombinations 24: ========");
            query = "let $a := <x>a<?foo ?>c</x>/namespace::processing-instruction('foo') return <z>{$a}</z>";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(exceptionThrown);

        } catch (XMLDBException e) {
            System.out.println("testTypedVariables : XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    public void testNamespace() {
        Resource doc;
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;
        try {
            Collection testCollection = getTestCollection();
            doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
            doc.setContent(module1);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(MODULE2_NAME, "BinaryResource");
            doc.setContent(module2);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(NAMESPACED_NAME, "XMLResource");
            doc.setContent(namespacedDocument);
            ((EXistResource) doc).setMimeType("application/xml");
            testCollection.storeResource(doc);

            XPathQueryService service =
                    (XPathQueryService) testCollection.getService(
                    "XPathQueryService",
                    "1.0");

            System.out.println("testNamespace 1: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "(:: redefine existing prefix ::)\n" + "declare namespace blah=\"bla\";\n" + "$blah:param";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQST0033") > -1);

            System.out.println("testNamespace 2: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "(:: redefine existing prefix with same URI ::)\n" + "declare namespace blah=\"blah\";\n" + "declare variable $blah:param  {\"value-2\"};\n" + "$blah:param";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQST0033") > -1);

            System.out.println("testNamespace 3: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"ho\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "$foo:bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                e.printStackTrace();
                message = e.getMessage();
            }
            assertTrue(message.indexOf("does not match namespace URI") > -1);

            System.out.println("testNamespace 4: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"ho\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n" + "$bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("No namespace defined for prefix") > -1);

            System.out.println("testNamespace 5: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"blah\" at \"" + URI + "/test/" + MODULE2_NAME + "\";\n" + "$bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("No namespace defined for prefix") > -1);

            System.out.println("testNamespace 6: ========");
            query = "declare namespace x = \"http://www.foo.com\"; \n" +
                    "let $a := doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                    "return $a//x:edition";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testNamespace 7: ========");
            query = "declare namespace x = \"http://www.foo.com\"; \n" +
                    "declare namespace y = \"http://exist.sourceforge.net/dc-ext\"; \n" +
                    "let $a := doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NAMESPACED_NAME + "') \n" +
                    "return $a//y:edition";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "<x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>",
                    ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testNamespace 8: ========");
            query = "<result xmlns:rdf='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//rdf:Description}</result>";
            result = service.query(query);
            assertEquals("XQuery: " + query,
                    "<result xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                    "    <rdf:Description id=\"3\">\n" +
                    "        <dc:title xmlns:dc=\"http://purl.org/dc/elements/1.1/\">title</dc:title>\n" +
                    "        <dc:creator xmlns:dc=\"http://purl.org/dc/elements/1.1/\">creator</dc:creator>\n" +
                    "        <x:place xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:place>\n" +
                    "        <x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>\n" +
                    "    </rdf:Description>\n" +
                    "</result>",
                    ((XMLResource) result.getResource(0)).getContent());

            System.out.println("testNamespace 9: ========");
            query = "<result xmlns='http://www.w3.org/1999/02/22-rdf-syntax-ns#'>{//Description}</result>";
            result = service.query(query);
            assertEquals("XQuery: " + query,
                    "<result xmlns=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n" +
                    "    <rdf:Description xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" id=\"3\">\n" +
                    "        <dc:title xmlns:dc=\"http://purl.org/dc/elements/1.1/\">title</dc:title>\n" +
                    "        <dc:creator xmlns:dc=\"http://purl.org/dc/elements/1.1/\">creator</dc:creator>\n" +
                    "        <x:place xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:place>\n" +
                    "        <x:edition xmlns:x=\"http://exist.sourceforge.net/dc-ext\">place</x:edition>\n" +
                    "    </rdf:Description>\n" +
                    "</result>",
                    ((XMLResource) result.getResource(0)).getContent());

            //Interesting one : let's see with XQuery gurus :-)
            //declare namespace fn="";
            //fn:current-time()
			/*
            If the URILiteral part of a namespace declaration is a zero-length string, 
            any existing namespace binding for the given prefix is removed from 
            the statically known namespaces. This feature provides a way 
            to remove predeclared namespace prefixes such as local.
             */

            System.out.println("testNamespace 9: ========");
            query = "declare option exist:serialize 'indent=no';" +
                    "for $x in <parent4 xmlns=\"http://www.example.com/parent4\"><child4/></parent4> " +
                    "return <new>{$x//*:child4}</new>";
            result = service.query(query);
            assertXMLEqual("<new><child4 xmlns='http://www.example.com/parent4'/></new>",
                    ((XMLResource) result.getResource(0)).getContent().toString());

        } catch (Exception e) {
            System.out.println("testNamespace : " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testNamespaceWithTransform() {
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");

            String query =
                    "xquery version \"1.0\";\n" +
                    "declare namespace transform=\"http://exist-db.org/xquery/transform\";\n" +
                    "declare variable $xml {\n" +
                    "	<node>text</node>\n" +
                    "};\n" +
                    "declare variable $xslt {\n" +
                    "	<xsl:stylesheet xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"2.0\">\n" +
                    "		<xsl:template match=\"node\">\n" +
                    "			<div><xsl:value-of select=\".\"/></div>\n" +
                    "		</xsl:template>\n" +
                    "	</xsl:stylesheet>\n" +
                    "};\n" +
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\">\n" +
                    "	<body>\n" +
                    "		{transform:transform($xml, $xslt, ())}\n" +
                    "	</body>\n" +
                    "</html>";

            ResourceSet result = service.query(query);

            //check there is one result
            assertEquals(1, result.getSize());

            String content = (String) result.getResource(0).getContent();

            //check the namespace
            assertTrue(content.startsWith("<html xmlns=\"http://www.w3.org/1999/xhtml\">"));

            //check the content
            assertTrue(content.indexOf("<div>text</div>") > -1);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testModule() {
        Resource doc;
        ResourceSet result;
        String query;
        String message;
        try {
            Collection testCollection = getTestCollection();
            doc = testCollection.createResource(MODULE1_NAME, "BinaryResource");
            doc.setContent(module1);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(MODULE3_NAME, "BinaryResource");
            doc.setContent(module3);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(MODULE4_NAME, "BinaryResource");
            doc.setContent(module4);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(FATHER_MODULE_NAME, "BinaryResource");
            doc.setContent(fatherModule);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(CHILD1_MODULE_NAME, "BinaryResource");
            doc.setContent(child1Module);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(CHILD2_MODULE_NAME, "BinaryResource");
            doc.setContent(child2Module);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            XPathQueryService service =
                    (XPathQueryService) testCollection.getService(
                    "XPathQueryService",
                    "1.0");

            System.out.println("testModule 1: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "$blah:param";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "value-1", result.getResource(0).getContent());

//            System.out.println("testModule 2: ========");
//            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "(:: redefine variable ::)\n" + "declare variable $blah:param  {\"value-2\"};\n" + "$blah:param";
//            try {
//                message = "";
//                result = service.query(query);
//            } catch (XMLDBException e) {
//                message = e.getMessage();
//            }
//            assertTrue(message.indexOf("XQST0049") > -1);

            System.out.println("testModule 3: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"blah\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "declare namespace blah2=\"blah\";\n" + "$blah2:param";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "value-1", result.getResource(0).getContent());

            System.out.println("testModule 4: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace blah=\"bla\" at \"" + URI + "/test/" + MODULE1_NAME + "\";\n" + "$blah:param";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("does not match namespace URI") > -1);

            System.out.println("testModule 5: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n" + "$foo:bar, $foo:bar1, $foo:bar2";
            result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 3, result.getSize());
            assertEquals("XQuery: " + query, "bar", result.getResource(0).getContent());
            assertEquals("XQuery: " + query, "bar1", result.getResource(1).getContent());
            assertEquals("XQuery: " + query, "bar2", result.getResource(2).getContent());

//			Non-heritance check
            System.out.println("testModule 6: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n" + "declare namespace foo1=\"foo1\"; \n" + "$foo1:bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPDY0002") > -1);

//			Non-heritance check
            System.out.println("testModule 7: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + FATHER_MODULE_NAME + "\";\n" + "declare namespace foo2=\"foo2\"; \n" + "$foo2:bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPDY0002") > -1);

            System.out.println("testModule 8: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo1=\"foo\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n" + "import module namespace foo2=\"foo\" at \"" + URI + "/test/" + CHILD1_MODULE_NAME + "\";\n" + "$foo1:bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
//			Should be a XQST0047 error
            assertTrue(message.indexOf("does not match namespace URI") > -1);

            System.out.println("testModule 9: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE3_NAME + "\";\n" + "$bar:bar";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("No namespace defined for prefix") > -1);

            System.out.println("testModule 10: ========");
            query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE4_NAME + "\";\n" + "foo:bar()";
            try {
                message = "";
                result = service.query(query);
                //WARNING !
                //This result is false ! The external vairable has not been resolved
                //Furthermore it is not in the module's namespace !
                printResult(result);
                assertEquals("XQuery: " + query, 0, result.getSize());
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
        //This is the good result !
        //assertTrue(message.indexOf("XQST0048") > -1);

        } catch (XMLDBException e) {
            System.out.println("testModule : XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testModulesAndNS() {
        try {
            Collection testCollection = getTestCollection();
            Resource doc = testCollection.createResource(MODULE7_NAME, "BinaryResource");
            doc.setContent(module7);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
            service.setProperty(OutputKeys.INDENT, "no");
            String query = "xquery version \"1.0\";\n" +
                    "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE7_NAME + "\";\n" +
                    "<div xmlns='http://www.w3.org/1999/xhtml'>" +
                    "{ foo:link() }" +
                    "</div>";
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            System.out.println("testModulesAndNS result: " + result.getResource(0).getContent().toString());
            assertXMLEqual("<div xmlns='http://www.w3.org/1999/xhtml'><a xmlns=\"\" href='#'>Link</a></div>",
                    result.getResource(0).getContent().toString());

            query = "xquery version \"1.0\";\n" +
                    "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE7_NAME + "\";\n" +
                    "<div xmlns='http://www.w3.org/1999/xhtml'>" +
                    "{ foo:copy(<a>Link</a>) }" +
                    "</div>";
            result = service.query(query);
            assertEquals(1, result.getSize());
            System.out.println("testModulesAndNS result: " + result.getResource(0).getContent().toString());
            assertXMLEqual("<div xmlns='http://www.w3.org/1999/xhtml'><a>Link</a></div>",
                    result.getResource(0).getContent().toString());
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testDoubleDocNode_2078755() {
        try {
            Collection testCollection = getTestCollection();
            Resource doc = testCollection.createResource(MODULE8_NAME, "BinaryResource");
            doc.setContent(module8);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
            service.setProperty(OutputKeys.INDENT, "no");
            String query = "import module namespace dr = \"double-root2\" at \"" + URI + "/test/" + MODULE8_NAME + "\";\n"
                    +"let $doc1 := dr:documentIn() \n"
                    +"let $count1 := count($doc1/element()) \n"
                    +"let $doc2 := dr:documentIn() \n"
                    +"let $count2 := count($doc2/element()) \n"
                    +"return ($count1, $count2) \n";
 
            ResourceSet result = service.query(query);
            assertEquals(2, result.getSize());
            assertEquals("1", result.getResource(0).getContent().toString());
            assertEquals("1", result.getResource(1).getContent().toString());
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testGlobalVars() {
        try {
            Collection testCollection = getTestCollection();
            Resource doc = testCollection.createResource(MODULE5_NAME, "BinaryResource");
            doc.setContent(module5);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            doc = testCollection.createResource(MODULE6_NAME, "BinaryResource");
            doc.setContent(module6);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            System.out.println("testGlobalVars 1: ========");
            XQueryService service = (XQueryService) testCollection.getService("XPathQueryService", "1.0");
            String query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE5_NAME + "\";\n" + "$foo:bar";
            ResourceSet result = service.query(query);
            assertEquals(result.getSize(), 1);
            assertEquals(result.getResource(0).getContent(), "bar");

            System.out.println("testGlobalVars 2: ========");
            query = "xquery version \"1.0\";\n" + "declare variable $local:a := 'abc';" + "$local:a";
            result = service.query(query);
            assertEquals(result.getSize(), 1);
            assertEquals(result.getResource(0).getContent(), "abc");

            System.out.println("testGlobalVars 3: ========");
            boolean gotException = false;
            try {
                query = "xquery version \"1.0\";\n" + "import module namespace foo=\"foo\" at \"" + URI + "/test/" + MODULE6_NAME + "\";\n" + "$foo:bar";
                result = service.query(query);
            } catch (XMLDBException e) {
                assertTrue("Test should generate err:XQST0049, got: " + e.getMessage(), e.getMessage().indexOf("err:XQST0049") > -1);
                gotException = true;
            }
            assertTrue("Duplicate global variable should generate error", gotException);

            System.out.println("testGlobalVars 4: ========");
            gotException = false;
            try {
                query = "xquery version \"1.0\";\n" + "declare variable $local:a := 'abc';" + "declare variable $local:a := 'abc';" + "$local:a";
                result = service.query(query);
            } catch (XMLDBException e) {
                assertTrue("Test should generate err:XQST0049, got: " + e.getMessage(), e.getMessage().indexOf("err:XQST0049") > -1);
                gotException = true;
            }
            assertTrue("Duplicate global variable should generate error", gotException);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testFunctionDoc() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        @SuppressWarnings("unused")
		String message;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testFunctionDoc 1: ========");
            query = "doc('" + XmldbURI.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "')";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            try {
                Node n = ((XMLResource) result.getResource(0)).getContentAsDOM();
                DetailedDiff d = new DetailedDiff(compareXML(numbers, n.toString()));
                System.out.println(d.toString());
                assertEquals(0, d.getAllDifferences().size());
            //ignore eXist namespace's attributes				
            //assertEquals(1, d.getAllDifferences().size());
            } catch (Exception e) {
                System.out.println("testFunctionDoc : XMLDBException: " + e);
                fail(e.getMessage());
            }

            System.out.println("testFunctionDoc 2: ========");
            query = "let $v := ()\n" + "return doc($v)";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testFunctionDoc 3: ========");
            query = "doc('" + XmldbURI.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML + "')";
            try {
                exceptionThrown = false;
                result = service.query(query);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            //TODO : to be decided !
            //assertTrue(exceptionThrown);
            assertEquals(0, result.getSize());

            System.out.println("testFunctionDoc 4: ========");
            query = "doc-available('" + XmldbURI.ROOT_COLLECTION + "/test/" + NUMBERS_XML + "')";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "true", result.getResource(0).getContent());

            System.out.println("testFunctionDoc 5: ========");
            query = "let $v := ()\n" + "return doc-available($v)";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "false", result.getResource(0).getContent());

            System.out.println("testFunctionDoc 6: ========");
            query = "doc-available('" + XmldbURI.ROOT_COLLECTION + "/test/dummy" + NUMBERS_XML + "')";
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "false", result.getResource(0).getContent());

        } catch (XMLDBException e) {
            System.out.println("testFunctionDoc : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    //This test only works if there is an Internet access
    public void testFunctionDocExternal() {
        boolean hasInternetAccess = false;
        ResourceSet result;
        String query;

        //Checking that we have an Internet Aceess
        try {
            URL url = new URL("http://www.w3.org/");
            URLConnection con = url.openConnection();
            if (con instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) con;
                hasInternetAccess = (httpConnection.getResponseCode() == HttpURLConnection.HTTP_OK);
            }
        } catch (MalformedURLException e) {
            fail("Stupid error... " + e.getMessage());
        } catch (IOException e) {
            //Ignore
        }

        if (!hasInternetAccess) {
            System.out.println("No Internet access: skipping 'testFunctionDocExternal' tests");
            return;
        }

        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testFunctionDocExternal 1: ========");
            query = "if (doc-available(\"http://www.w3.org/XML/\")) then doc(\"http://www.w3.org/XML/\") else ()";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());

            System.out.println("testFunctionDocExternal 2: ========");
            query = "if (doc-available(\"http://www.w3.org/XML/dummy\")) then doc(\"http://www.w3.org/XML/dummy\") else ()";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testFunctionDocExternal 3: ========");
            query = "doc-available(\"http://www.w3.org/XML/\")";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "true", result.getResource(0).getContent());

            System.out.println("testFunctionDocExternal 4: ========");
            query = "doc-available(\"http://www.404brain.net/true404\")";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "false", result.getResource(0).getContent());

            System.out.println("testFunctionDocExternal 5: ========");
            //A redirected 404
            query = "doc-available(\"http://java.sun.com/404\")";
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "false", result.getResource(0).getContent());

            System.out.println("testFunctionDocExternal 6: ========");
            query = "if (doc-available(\"file:////doesnotexist.xml\")) then doc(\"file:////doesnotexist.xml\") else ()";
            result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());

            System.out.println("testFunctionDocExternal 7: ========");
            query = "doc-available(\"file:////doesnotexist.xml\")";
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, "false", result.getResource(0).getContent());

        } catch (XMLDBException e) {
            System.out.println("testFunctionDoc : XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    private String makeString(int n) {
        StringBuffer b = new StringBuffer();
        char c = 'a';
        for (int i = 0; i < n; i++) {
            b.append(c);
        }
        return b.toString();
    }

    public void testTextConstructor() {
        System.out.println("testTextConstructor 1: ========");

        String query = "text{ \"a\" }, text{ \"b\" }, text{ \"c\" }, text{ \"d\" }";

        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            printResult(result);
            assertEquals("XQuery: " + query, 4, result.getSize());

            assertEquals("XQuery: " + query, "a", result.getResource(0).getContent().toString());
            assertEquals("XQuery: " + query, "b", result.getResource(1).getContent().toString());
            assertEquals("XQuery: " + query, "c", result.getResource(2).getContent().toString());
            assertEquals("XQuery: " + query, "d", result.getResource(3).getContent().toString());

        } catch (XMLDBException e) {
            System.out.println("testAttributeAxis(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testUserEscalationForInMemoryNodes() {
        System.out.println("testUserEscalationForInMemoryNodes 1: ========");

        String query = "xmldb:login(\"xmldb:exist:///db\", \"guest\", \"guest\"), xmldb:get-current-user(), let $node := <node id=\"1\">value</node>, $null := $node[@id eq '1'] return xmldb:get-current-user()";

        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            printResult(result);

            Resource loggedIn = result.getResource(0);
            Resource currentUser = result.getResource(1);
            Resource currentUserAfterInMemoryOp = result.getResource(2);

            //check the login as guest worked
            assertEquals("Logged in as quest: " + loggedIn.getContent().toString(), "true", loggedIn.getContent().toString());

            //check that we are guest
            assertEquals("After Login as guest, User should be guest and is: " + currentUser.getContent().toString(), "guest", currentUser.getContent().toString());

            //check that we are still guest
            assertEquals("After Query, User should still be guest and is: " + currentUserAfterInMemoryOp.getContent().toString(), "guest", currentUserAfterInMemoryOp.getContent().toString());
        } catch (XMLDBException e) {
            System.out.println("testUserEscalationForInMemoryNodes(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testConstructedAttributeValue() {
        String query = "let $attr := attribute d { \"xxx\" } " + "return string($attr)";
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            assertEquals("xxx", result.getResource(0).getContent().toString());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testAttributeAxis() {
        ResourceSet result;
        String query;
        XMLResource resu;
        try {
            System.out.println("testAttributeAxis 1: ========");
            @SuppressWarnings("unused")
			String large = createXMLContentWithLargeString();
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(file_name, xml);

            query = "let $node := (<c id=\"OK\">b</c>)/descendant-or-self::*/attribute::id " +
                    "return <a>{$node}</a>";
            result = service.query(query);
            printResult(result);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "OK", ((Element) resu.getContentAsDOM()).getAttribute("id"));
        } catch (XMLDBException e) {
            System.out.println("testAttributeAxis(): XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testInstanceOfDocumentNode() {
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            String query = "let $doc := document { <element/> } " +
                    "return $doc/root() instance of document-node()";
            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, "true", result.getResource(0).getContent().toString());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testLargeAttributeSimple() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        try {
            System.out.println("testLargeAttributeSimple 1: ========");
            String large = createXMLContentWithLargeString();
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(file_name, xml);

            query = "doc('" + file_name + "') / details/metadata[@docid= '" + large + "' ]";
            result = service.queryResource(file_name, query);
            printResult(result);
            assertEquals("XQuery: " + query, nbElem, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testLargeAttributeSimple(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testCDATASerialization() {
        ResourceSet result;
        String query;
        XMLResource resu;
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService", "1.0");

            query = "let $doc := document{ <root><![CDATA[gaga]]></root> } " +
                    "return $doc/root/string()";
            result = service.query(query);
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "gaga", resu.getContent().toString());
        } catch (XMLDBException e) {
            System.out.println("testAttributeAxis(): XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testCDATAQuery() {
        ResourceSet result;
        String query;
        XMLResource resu;
        String xml = "<root><node><![CDATA[world]]></node></root>";
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService("cdata.xml", xml);
            service.setProperty(OutputKeys.INDENT, "no");
            query = "//text()";
            result = service.queryResource("cdata.xml", query);
            assertEquals(1, result.getSize());
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "world", resu.getContent().toString());

            query = "//node/text()";
            result = service.queryResource("cdata.xml", query);
            assertEquals(1, result.getSize());
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "world", resu.getContent().toString());

            query = "//node/node()";
            result = service.queryResource("cdata.xml", query);
            assertEquals(1, result.getSize());
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, "world", resu.getContent().toString());

            query = "/root[node = 'world']";
            result = service.queryResource("cdata.xml", query);
            assertEquals(1, result.getSize());
            resu = (XMLResource) result.getResource(0);
            assertEquals("XQuery: " + query, xml, resu.getContent().toString());
        } catch (XMLDBException e) {
            System.out.println("testCDATAQuery(): XMLDBException: " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    /**
     * Tests that no result will be returned if an attribute's value is selected on a node which wasn't found
     */
    public void testAttributeForNoResult() {
        String query = "let $a := <a><b>-1</b><b>-2</b></a> " + //
                "return /a[./c]/@id/string()";
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(0, result.getSize());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testLargeAttributeContains() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        try {
            System.out.println("testLargeAttributeSimple 1: ========");
            @SuppressWarnings("unused")
			String large = createXMLContentWithLargeString();
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(file_name, xml);

            query = "doc('" + file_name + "') / details/metadata[ contains(@docid, 'aa') ]";
            result = service.queryResource(file_name, query);
            assertEquals("XQuery: " + query, nbElem, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testLargeAttributeSimple(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testLargeAttributeKeywordOperator() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        try {
            System.out.println("testLargeAttributeSimple 1: ========");
            String large = createXMLContentWithLargeString();
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(file_name, xml);

            query = "doc('" + file_name + "') / details/metadata[ @docid = '" + large + "' ]";
            result = service.queryResource(file_name, query);
            assertEquals("XQuery: " + query, nbElem, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testLargeAttributeSimple(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testAttributeNamespace() {

        String query = "declare function local:copy($nodes as node()*) as node()* {" + "for $n in $nodes return " + "if ($n instance of element()) then " + "  element {node-name($n)} {(local:copy($n/@*), local:copy($n/node()))} " + "else if ($n instance of attribute()) then " + "  attribute {node-name($n)} {$n} " + "else if ($n instance of text()) then " + "  text {$n} " + "else " + "  <Other/>" + "};" + "let $c :=" + "<c:C  xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">" + "ccc" + "</c:C>" + "return local:copy($c)";
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            assertEquals("<c:C xmlns:d=\"http://d\" xmlns:c=\"http://c\" d:d=\"ddd\">" + "ccc" + "</c:C>", result.getResource(0).getContent().toString());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testNameConflicts() {
        String query = "let $a := <name name=\"Test\"/> return <wrap>{$a//@name}</wrap>";
        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService(
                    "XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            assertEquals("<wrap name=\"Test\"/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testSerialization() {
        @SuppressWarnings("unused")
		ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		boolean exceptionThrown;
        String message;

        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            query = "let $a := <test><foo name='bar'/><foo name='bar'/></test>" +
                    "return <attribute>{$a/foo/@name}</attribute>";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XQDY0025") > -1);

            query = "let $a := <foo name='bar'/> return $a/@name";
            try {
                message = "";
                result = service.query(query);
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
        //TODO : how toserialize this resultand get the error ? -pb
        //assertTrue(message.indexOf("XQDY0025") > -1); 

        } catch (XMLDBException e) {
            System.out.println("testVariable : XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    /** CAUTION side effect on field xml
     * @return the large string contained in the atrbute(s)
     */
    private String createXMLContentWithLargeString() {
        String large = makeString(stringSize);
        String head = "<details format='xml'>";
        String elem = "<metadata docid='" + large + "'></metadata>";
        String tail = "</details>";
        xml = head;
        for (int i = 0; i < nbElem; i++) {
            xml += elem;
        }
        xml += tail;
        System.out.println("XML:\n" + xml);
        return large;
    }

    public void testRetrieveLargeAttribute() throws XMLDBException {
        System.out.println("testRetrieveLargeAttribute 1: ========");
        createXMLContentWithLargeString();
        storeXMLStringAndGetQueryService(file_name, xml);
        XMLResource res = (XMLResource) getTestCollection().getResource(file_name);
        System.out.println("res.getContent(): " + res.getContent());
    }

    /** This test is obsolete because testLargeAttributeSimple() reproduces the problem without a file,
     * but I keep it to show how one can test with an XML file. */
    public void obsoleteTestLargeAttributeRealFile() {
        ResourceSet result;
        String query;
        @SuppressWarnings("unused")
		XMLResource resu;
        try {
            System.out.println("testLargeAttributeRealFile 1: ========");
            String large;
            large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
            if (attributeXML != null) {
                large = attributeXML;
            }
            @SuppressWarnings("unused")
			String xml = "<details format='xml'><metadata docid='" + large +
                    "'></metadata></details>";
            final String FILE_NAME = "detail_xml.xml";
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(FILE_NAME);

            query = "doc('" + FILE_NAME + "') / details/metadata[@docid= '" + large + "' ]"; // fails !!!
            // query = "doc('"+ FILE_NAME+"') / details/metadata[ docid= '" + large + "' ]"; // test passes!

            result = service.queryResource(FILE_NAME, query);
            printResult(result);
            assertEquals("XQuery: " + query, 2, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testLargeAttributeRealFile(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void bugtestXUpdateWithAdjacentTextNodes() {
        ResourceSet result;
        String query;

        query = "let $name := xmldb:store('/db' , 'xupdateTest.xml', <test>aaa</test>)" +
                "let $xu :=" +
                "<xu:modifications xmlns:xu='http://www.xmldb.org/xupdate' version='1.0'>" +
                "<xu:append select='/test'>" +
                "<xu:text>yyy</xu:text>" +
                "</xu:append>" +
                "</xu:modifications>" +
                "let $count := xmldb:update('/db' , $xu)" +
                "for $textNode in xmldb:document('/db/xupdateTest.xml')/test/text()" +
                "	return <text id='{util:node-id($textNode)}'>{$textNode}</text>";

        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

            System.out.println("testXUpdateWithAdvancentTextNodes 1: ========");
            result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testXUpdateWithAdvancentTextNodes(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    //TODO : understand this test and make sure that the expected result is correct
    //expected:<3> but was:<2>
    public void bugtestXUpdateAttributesAndElements() {
        ResourceSet result;
        String query;

        query =
                "declare function local:update-game($game) {\n" +
                "local:update-frames($game),\n" +
                "update insert\n" +
                "<stats>\n" +
                "<strikes>4</strikes>\n" +
                "<spares>\n" +
                "<attempted>4</attempted>\n" +
                "</spares>\n" +
                "</stats>\n" +
                "into $game\n" +
                "};\n" +
                "declare function local:update-frames($game) {\n" +
                // Uncomment this, and it works:
                //"for $frame in $game/frame return update insert <processed/> into $frame,\n" +
                "for $frame in $game/frame\n" +
                "return update insert attribute points {4} into $frame\n" +
                "};\n" +
                "let $series := xmldb:document('bowling.xml')/series\n" +
                "let $nul1 := for $game in $series/game return local:update-game($game)\n" +
                "return $series/game/stats\n";

        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(BOWLING_XML, bowling);

            System.out.println("testXUpdateAttributesAndElements 1: ========");
            result = service.query(query);
            assertEquals("XQuery: " + query, 3, result.getSize());
        } catch (XMLDBException e) {
            System.out.println("testXUpdateAttributesAndElements(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }

    public void testNodeName() {
        String query = "declare function local:name($node as node()) as xs:string? { " + " if ($node/self::element() != '') then name($node) else () }; " + " let $n := <!-- Just a comment! --> return local:name($n) ";
        XPathQueryService service;
        try {
            service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());
        } catch (XMLDBException ex) {
            fail(ex.toString());
        }
    }

    // DWES Funny in sandbox and REST it fails ; here it is OK... sometimes
    // http://sourceforge.net/tracker/index.php?func=detail&aid=1691112&group_id=17691&atid=117691
    public void testOrder_1691112() {

        String query = "declare namespace tt = \"http://example.com\";" +
                "declare function tt:function( $function as element(Function)) {" +
                "  let $functions :=" +
                "    for $subfunction in $function/Function" +
                "    return tt:function($subfunction)" +
                "   let $unused := distinct-values($functions/NonExistingElement)" +
                "  return" +
                "  <Function>" +
                "  {" +
                "    $function/Name," +
                "    $functions" +
                "  }" +
                "  </Function>" +
                "};" +
                "let $funcs :=" +
                "  <Function>" +
                "      <Name>Airmount 1</Name>" +
                "      <Function>" +
                "          <Name>Position</Name>" +
                "      </Function>" +
                "      <Function>" +
                "          <Name>Velocity</Name>" +
                "      </Function>" +
                "  </Function>" +
                "return" +
                "  tt:function($funcs)";

        String expectedresult =
                "<Function>\n" +
                "    <Name>Airmount 1</Name>\n" +
                "    <Function>\n" +
                "        <Name>Position</Name>\n" +
                "    </Function>\n" +
                "    <Function>\n" +
                "        <Name>Velocity</Name>\n" +
                "    </Function>\n" +
                "</Function>";

        try {

            for (int i = 0; i < 25; i++) { // repeat a few times

                XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
                System.out.println("Attempt " + i);
                ResourceSet result = service.query(query);
                assertEquals(1, result.getSize());
                printResult(result);
                assertEquals(expectedresult, result.getResource(0).getContent().toString());
            }

        } catch (Exception ex) {
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/tracker/index.php?func=detail&aid=1691177&group_id=17691&atid=117691
    public void testAttribute_1691177() {

        String query = "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; " + "let $uri := xmldb:store(\"/db\", \"insertAttribDoc.xml\", <C/>) " + "let $node := doc($uri)/element() " + "let $attrib := <Value f=\"ATTRIB VALUE\"/>/@* " + "return update insert $attrib into $node  ";

        XPathQueryService service;
        try {
            service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, 0, result.getSize());
        } catch (XMLDBException ex) {
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/tracker/index.php?func=detail&aid=1691174&group_id=17691&atid=117691
    public void testAttribute_1691174() {
        String query = "declare function local:show($el1, $el2) { " 
                + "	<Foobar> "
                + "	{ (\"first: \", $el1, \" second: \", $el2) } "
                + "	</Foobar> " + "}; "
                + "declare function local:attrib($n as node()) { "
                + "	<Attrib>{$n}</Attrib> "
                + "}; "
                + "local:show( "
                + "	<Attrib name=\"value\"/>, "
                + "	local:attrib(attribute name {\"value\"})  (: Exist bug! :) "
                + ")  ";
        XPathQueryService service;
        try {
            service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
        } catch (XMLDBException ex) {
            fail(ex.toString());
        }
    }

    public void testQnameToString_1632365() {
        String query = "let $qname := QName(\"http://test.org\", \"test:name\") " +
                "return xs:string($qname)";
        String expectedresult = "test:name";

        try {
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(expectedresult, result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testComments_1715035() {

        try {
            String query = "<!-- < aa > -->";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(query, result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "<?pi \"<\"aa\">\"?>";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals(query, result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testDocumentNode_1730690() {

        try {
            String query = "let $doc := document { <element/> } " +
                    "return $doc/root() instance of document-node()";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("true", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testEnclosedExpressions() {
        try {
            String query = "let $a := <docum><titolo>titolo</titolo><autor>giulio</autor></docum> " +
                    "return <row>{$a/titolo/text()} {' '} {$a/autor/text()}</row>";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertXMLEqual("<row>titolo giulio</row>", result.getResource(0).getContent().toString());
        } catch (Exception e) {
            System.out.println("testEnclosedExpressions(): " + e);
            e.printStackTrace();
            fail(e.getMessage());
        }

    }

    public void testOrderCompareAtomicType_1733265() {

        try {
            String query = "( ) = \"A\"";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("false", result.getResource(0).getContent().toString());

            query = "\"A\" = ( )";
            result = service.query(query);
            assertEquals("false", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testPositionInPredicate() {

        try {
            String query = "let $example := <Root> <Element>1</Element> <Element>2</Element> </Root>" +
                    "return  $example/Element[1] ";
            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("<Element>1</Element>", result.getResource(0).getContent().toString());

            query = "let $example := <Root> <Element>1</Element> <Element>2</Element> </Root>" +
                    "return  $example/Element[position() = 1] ";
            result = service.query(query);
            assertEquals("<Element>1</Element>", result.getResource(0).getContent().toString());


        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1740880
    public void testElementConstructionWithNamespace_1740880() {

        try {
            String query = "let $a := <foo:Bar xmlns:foo=\"urn:foo\"/> " +
                    "let $b := element { QName(\"urn:foo\", \"foo:Bar\") } { () } " +
                    "return deep-equal($a, $b) ";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);
            assertEquals("Oops", "true", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1740883
    public void testNoErrorNeOperatorWithSequence_1740883() {

        try {
            String query = "let $foo := <Foo> <Bar>A</Bar> <Bar>B</Bar> <Bar>C</Bar> </Foo> " +
                    "return $foo[Bar ne \"B\"]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            @SuppressWarnings("unused")
			ResourceSet result = service.query(query);

            fail("result should have yielded into an error like " +
                    "'A sequence of more than one item is not allowed as the first " + "operand of 'ne'");


        } catch (XMLDBException ex) {
            if (!ex.getMessage().contains("one item")) {
                ex.printStackTrace();
                fail(ex.getMessage());
            }

        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1740885
    public void testNeOperatorDoesNotWork_1740885() {

        try {
            String query = "let $foo := <Foo> <Bar>A</Bar> <Bar>B</Bar> <Bar>C</Bar> </Foo>" +
                    "return $foo/Bar[. ne \"B\"]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals("First", "<Bar>A</Bar>", result.getResource(0).getContent().toString());
            assertEquals("Second", "<Bar>C</Bar>", result.getResource(1).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1740891
    public void testEvalLoosesContext_1740891() {
        String module = "module namespace tst = \"urn:test\"; " +
                "declare namespace util = \"http://exist-db.org/xquery/util\";" +
                "declare function tst:bar() as element(Bar)* { " +
                "let $foo := <Foo><Bar/><Bar/><Bar/></Foo> " +
                "let $query := \"$foo/Bar\" " +
                "let $bar := util:eval($query) " +
                "return $bar };";

        String module_name = "module.xqy";
        Resource doc;

        // Store module
        try {
            Collection testCollection = getTestCollection();
            doc = testCollection.createResource(module_name, "BinaryResource");
            doc.setContent(module);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        try {
            String query = "import module namespace tst = \"urn:test\"" +
                    "at \"xmldb:exist:///db/test/module.xqy\"; " +
                    "tst:bar()";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(3, result.getSize());
            assertEquals("First", "<Bar/>", result.getResource(0).getContent().toString());
            assertEquals("Second", "<Bar/>", result.getResource(1).getContent().toString());
            assertEquals("Third", "<Bar/>", result.getResource(2).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1740886
    public void testCardinalityIssues_1740886() {
        String xmldoc = "<Foo><Bar/><Bar/><Bar/></Foo>";
        String query =
                "declare namespace tst = \"urn:test\"; " +
                "declare option exist:serialize 'indent=no';" +
                //======
                "declare function tst:bar( $foo as element(Foo) ) as element(Foo) { " +
                "let $dummy := $foo/Bar " +
                "return $foo }; " +
                //====== if you leave /test out......
                "let $foo := doc(\"/db/test/foo.xml\")/element() " +
                "return tst:bar($foo)";

        try {
            XPathQueryService service = storeXMLStringAndGetQueryService("foo.xml", xmldoc);
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertXMLEqual("Oops", xmldoc, result.getResource(0).getContent().toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }


    }

    // http://sourceforge.net/support/tracker.php?aid=1755910 
    public void testQNameString_1755910() {

        try {
            String query = "let $qname1 := QName(\"http://www.w3.org/2001/XMLSchema\", \"xs:element\") " + "let $qname2 := QName(\"http://foo.com\", \"foo:bar\") " + "return (xs:string($qname1), xs:string($qname2))";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());

            assertEquals("First", "xs:element", result.getResource(0).getContent().toString());
            assertEquals("Second", "foo:bar", result.getResource(1).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1665215 
    public void testPredicateMinLast_1665215() {

        try {
            String query = "declare option exist:serialize 'indent=no';" +
                    "let $data :=<parent><child>1</child><child>2</child><child>3</child><child>4</child></parent>" +
                    "return <result>{$data/child[min((last(),3))]}</result>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());

            assertEquals("First", "<result><child>3</child></result>", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1665213 
    public void testPredicatePositionLast_1665213() {

        // OK, regression
        try {
            String query = "(1, 2, 3)[ position() = last() ]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());

            assertEquals("First", "3", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "(1, 2, 3)[(position()=last() and position() < 4)]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());

            assertEquals("First", "3", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "(1, 2, 3)[(position()=last())]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());

            assertEquals("First", "3", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    // http://sourceforge.net/support/tracker.php?aid=1769086 
    public void testCce_IndexOf_1769086() {

        try {
            String query = "(\"One\", \"Two\", \"Three\")[index-of((\"1\", \"2\", \"3\"), \"2\")]";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());

            assertEquals("First", "Two", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testShortVersionPositionPredicate() {

        try {
            String query = "declare option exist:serialize 'indent=no';" + "let $foo :=  <foo>    <bar baz=\"\"/>  </foo>" + "let $bar1 := $foo/bar[exists(@baz)][1]" + "let $bar2 := $foo/bar[exists(@baz)][position() = 1]" + "return  <found> <bar1>{$bar1}</bar1> <bar2>{$bar2}</bar2> </found>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<found><bar1><bar baz=\"\"/></bar1><bar2><bar baz=\"\"/></bar2></found>", result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    //    An exception occurred during query execution: XPTY0004: Invalid type for
    //variable $arg1. Expected xs:string, got xs:integer
    // http://sourceforge.net/tracker/index.php?func=detail&aid=1787285&group_id=17691&atid=117691
    public void testWrongInvalidTypeError_1787285() {

        try {
            String query = "let $arg1 as xs:string := \"A String\"" + "let $arg2 as xs:integer := 3 return $arg2";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "3", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // regression http://sourceforge.net/support/tracker.php?aid=1805612
    public void bugtestWrongAttributeTypeCheck_1805612() {

        // OK
        try {
            String query = "declare namespace tst = \"http://test\"; " 
                    + "declare function tst:foo($a as element()?) {   $a }; "
                    + "tst:foo( <result/> )";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<result/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        // NOK
        try {
            String query = "declare namespace tst = \"http://test\"; " 
                    + "declare function tst:foo($a as element()?) {   $a }; "
                    + "tst:foo( "
                    + "  let $a as xs:boolean := true()  "
                    + "  return <result/> "
                    + ")";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<result/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // regression http://sourceforge.net/support/tracker.php?aid=1805609
    public void testWrongAttributeCardinalityCount_1805609() {

        // OK
        try {
            String query = "element {\"a\"} { <element b=\"\" c=\"\" />/attribute()[namespace-uri(.) != " + "\"http://www.asml.com/metainformation\"]}";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<a b=\"\" c=\"\"/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        // NOK
        try {
            String query = "element {\"a\"} { <element b=\"\" c=\"\"/>" + "/attribute()[namespace-uri(.) != \"http://www.asml.com/metainformation\"]}";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<a b=\"\" c=\"\"/>", result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // regression http://sourceforge.net/support/tracker.php?aid=1806901
    public void testDoubleDefaultNamespace_1806901() {

        // OK
        try {
            String query = "declare namespace xf = \"http://a\"; " + "declare option exist:serialize 'indent=no';" + "<html xmlns=\"http://b\"><xf:model><xf:instance xmlns=\"\"/></xf:model></html>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<html xmlns=\"http://b\"><xf:model xmlns:xf=\"http://a\">" + "<xf:instance xmlns=\"\"/></xf:model></html>",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1828168
    public void testPredicateInPredicateEmptyResult_1828168() {

        try {
            String query = "let $docs := <Document/> return $docs[a[1] = 'b']";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(0, result.getSize());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "<a/>[() = 'b']";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(0, result.getSize());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }


    }

    // http://sourceforge.net/support/tracker.php?aid=1846228
    public void bugtestNamespaceHandlingSameModule_1846228() {

        try {
            String query = "declare option exist:serialize 'indent=no';" +
                    "declare function local:table () {" +
                    "<d>Bar</d>};" +
                    "<foobar xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<a><b>Foo</b></a>" +
                    "<c>{local:table()}</c>" +
                    "</foobar>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query,
                    "<foobar xmlns=\"http://www.w3.org/1999/xhtml\">" +
                    "<a><b>Foo</b></a>" +
                    "<c><d xmlns=\"\">Bar</d></c>" +
                    "</foobar>", result.getResource(0).getContent().toString());


        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }


    // http://sourceforge.net/support/tracker.php?aid=1841105
    // in a path expression, a step returning an empty sequence stops the evaluation
    // (and return an empty sequence) as confirmed by Michael Kay on the XQuery mailing list
    public void testStringOfEmptySequence_1841105() {

        // OK
        try {
            String query = "empty( ()/string() )";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "true",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=2871975
    public void bugtestStringOfEmptySequenceWithExplicitContext_2871975() {

        // OK
        try {
            String query = "empty( ()/string() )";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "true",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        // NOK
        try {
            String query = "empty( ()/string(.) )";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "true",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1970717
    public void testConstructTextNodeWithEmptyString_1970717() {

        // OK
        try {
            String query = "text {\"\"} =\"\"";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "true",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1848497
    public void bugtestAttributeNamespaceDeclaration_1848497() {

        // OK
        try {
            String query = "declare namespace foo = \"foo\";" +
                    "declare function foo:boe() { \"boe\" };" +
                    "<xml xmlns:foo2=\"foo\">{ foo2:boe() }</xml>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<xml xmlns:foo2=\"foo\">boe</xml>",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1884403
    public void testAtomization_1884403() {

        try {
            String query = "declare namespace tst = \"tt\"; " +
                    "declare function tst:foo() as xs:string { <string>myTxt</string> }; " +
                    "tst:foo()";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "myTxt",
                    result.getResource(0).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1884360
    public void testCardinalityAttributeNamespace_1884360() {

        try {
            String query = "let $el := <element a=\"1\" b=\"2\"/> " +
                    "for $attr in $el/attribute()[namespace-uri(.) ne \"h\"] " +
                    "return <c>{$attr}</c>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query, "<c a=\"1\"/>",
                    result.getResource(0).getContent().toString());
            assertEquals(query, "<c b=\"2\"/>",
                    result.getResource(1).getContent().toString());
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    public void testCurrentDateTimeInModules_1894009() {
        String module = "module namespace dt = \"dt\";\n" +
                "\n" +
                "declare function dt:fib($n) {\n" +
                "  if ($n < 2) then $n else dt:fib($n - 1) + dt:fib($n - 2) \n" +
                "};\n" +
                "\n" +
                "declare function dt:dateTime() {\n" +
                "  (: Do something time consuming first. :)  \n" +
                "  let $a := dt:fib(25)" +
                "  return current-dateTime()\n" +
                "};";

        String module_name = "dt.xqm";
        Resource doc;

        // Store module
        try {
            Collection testCollection = getTestCollection();
            doc = testCollection.createResource(module_name, "BinaryResource");
            doc.setContent(module);
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }

        try {
            String query = "import module namespace dt = \"dt\" at" +
                    "  \"xmldb:exist:///db/test/dt.xqm\"; " +
                    "(<this>{current-dateTime()}</this>, <this>{dt:dateTime()}</this>)";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals("First", result.getResource(0).getContent().toString(),
                    result.getResource(1).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1909505
    public void testXmldbStoreComment_1909505() {

        try {
            String query = "declare option exist:serialize 'indent=no';" +
                    "let $docIn := <a><!-- b --></a>" +
                    "let $uri := xmldb:store(\"/db\", \"commenttest.xml\", $docIn)" +
                    "let $docOut := doc($uri) return $docOut";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<a><!-- b --></a>",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1938498 
    public void testMemproc_1938498() {

        try {
            String xmldocument = "<Root><Child/></Root>";
            String location = "1938498.xml";
            String query =
                    "let $test := doc(\"1938498.xml\")" + "let $inmems := <InMem>{$test}</InMem>" + "return <Test>{$inmems/X}</Test>";
            String output = "<Test/>";

            XPathQueryService service =
                    storeXMLStringAndGetQueryService(location, xmldocument);

            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, output,
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testCCE_SaxException() {

        try {
            String xmldocument = "<a><b><c>mmm</c></b></a>";
            String location = "ccesax.xml";
            String query =
                    "declare namespace xmldb = \"http://exist-db.org/xquery/xmldb\"; " 
                    + "declare option exist:serialize 'indent=no';"
                    + "let $results := doc(\"ccesax.xml\")/element() "
                    + "let $output := let $body := <e>{$results/b/c}</e>  return <d>{$body}</d> "
                    + "let $id := $output/e/c "
                    + "let $store := xmldb:store(\"/db\", \"output.xml\", $output)"
                    + "return doc('/db/output.xml')";
//            String output = "<d><b><c>mmm</c></b></d>";
            String output = "<d><e><c>mmm</c></e></d>";

            XPathQueryService service =
                    storeXMLStringAndGetQueryService(location, xmldocument);

            ResourceSet result = service.query(query);
            assertEquals("XQuery: " + query, 1, result.getSize());
            assertEquals("XQuery: " + query, output,
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=2003042
    public void testXPTY0018_MixNodesAtomicValues_2003042() {

        try {
            String query = "declare option exist:serialize 'indent=no'; <a>{2}<b/></a>";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<a>2<b/></a>", //checked with saxon
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }
    
    // http://sourceforge.net/support/tracker.php?aid=1816496
    public void testDivYieldsWrongInf_1816496() {

        try {
            String query = "let $negativeZero := xs:double(-1.0e-1024) let $positiveZero := xs:double(1.0e-1024) "
                    +"return ("
                    +"(xs:double(1)  div xs:double(0)),   (xs:double(1)  div $positiveZero),  (xs:double(1)  div $negativeZero), "
                    +"(xs:double(-1) div xs:double(0)),   (xs:double(-1) div $positiveZero),  (xs:double(-1) div $negativeZero), "
                    +"($negativeZero div $positiveZero),  ($positiveZero div $negativeZero), "
                    +"(xs:double(0) div $positiveZero),   (xs:double(0) div $negativeZero),  "
                    +"(xs:double(0) div xs:double(0))  "
                    +")";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(11, result.getSize());
            
            assertEquals(query, "INF", result.getResource(0).getContent().toString());
            assertEquals(query, "INF", result.getResource(1).getContent().toString());
            assertEquals(query, "-INF", result.getResource(2).getContent().toString());
            
            assertEquals(query, "-INF", result.getResource(3).getContent().toString());
            assertEquals(query, "-INF", result.getResource(4).getContent().toString());
            assertEquals(query, "INF", result.getResource(5).getContent().toString());
            
            assertEquals(query, "NaN", result.getResource(6).getContent().toString());
            assertEquals(query, "NaN", result.getResource(7).getContent().toString());
            assertEquals(query, "NaN", result.getResource(8).getContent().toString());
            assertEquals(query, "NaN", result.getResource(9).getContent().toString());
            assertEquals(query, "NaN", result.getResource(10).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
        
        try {
            String query = "xs:float(2) div xs:float(0)";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "INF",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
        
    }
    
    // http://sourceforge.net/support/tracker.php?aid=1841635
    public void testResolveBaseURI_1841635() {
        String xmldoc = "<Root><Node1><Node2><Node3></Node3></Node2></Node1></Root>";

       XPathQueryService service = null;
        
        try {
            service = storeXMLStringAndGetQueryService("baseuri.xml", xmldoc);
            
            String query="xmldb:document('/db/test/baseuri.xml')/Root/Node1/base-uri()";
            
           
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        try {
            String query="xmldb:document('/db/test/baseuri.xml')/Root/Node1/base-uri()";

            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        try {
            String query="xmldb:document('/db/test/baseuri.xml')/Root/Node1/Node2/base-uri()";

            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
        try {
            String query="xmldb:document('/db/test/baseuri.xml')/Root/Node1/Node2/Node3/base-uri()";

            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals("/db/test/baseuri.xml", result.getResource(0).getContent().toString());

        } catch (Exception ex) {
            ex.printStackTrace();
            fail(ex.getMessage());
        }
        
    }


    // http://sourceforge.net/support/tracker.php?aid=2429093
    public void testXPTY0018_mixedsequences_2429093() {

        try {
            String query = "declare variable $a := <A><B/></A>;\n" +
                    "($a/B, \"delete\") ";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query, "<B/>",
                    result.getResource(0).getContent().toString());
            assertEquals(query, "delete",
                    result.getResource(1).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }


    public void testMessageDigester() {

        try {
            String query = "let $value:=\"ABCDEF\"\n" +
                    "let $alg:=\"MD5\"\n" +
                    "return\n" +
                    "(util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query, "8827a41122a5028b9808c7bf84b9fcf6",
                    result.getResource(0).getContent().toString());
            assertEquals(query, "iCekESKlAouYCMe/hLn89g==",
                    result.getResource(1).getContent().toString());


        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "let $value:=\"ABCDEF\"\n" +
                    "let $alg:=\"SHA-1\"\n" +
                    "return\n" +
                    "(util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query, "970093678b182127f60bb51b8af2c94d539eca3a",
                    result.getResource(0).getContent().toString());
            assertEquals(query, "lwCTZ4sYISf2C7UbivLJTVOeyjo=",
                    result.getResource(1).getContent().toString());


        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        try {
            String query = "let $value:=\"ABCDEF\"\n" +
                    "let $alg:=\"SHA-256\"\n" +
                    "return\n" +
                    "(util:hash($value, $alg), util:hash($value, $alg, xs:boolean('true')))";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query, "e9c0f8b575cbfcb42ab3b78ecc87efa3b011d9a5d10b09fa4e96f240bf6a82f5",
                    result.getResource(0).getContent().toString());
            assertEquals(query, "6cD4tXXL/LQqs7eOzIfvo7AR2aXRCwn6TpbyQL9qgvU=",
                    result.getResource(1).getContent().toString());


        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // https://sourceforge.net/tracker/?func=detail&aid=2846187&group_id=17691&atid=317691
    public void testDynamicallySizedNamePool() {
        try {
            String query = "<root> { for $i in 1 to 2000  "
                    + "return element {concat(\"elt-\", $i)} {} } </root>";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            service.query(query);
            
        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }
    }


    // http://sourceforge.net/support/tracker.php?aid=2903815
    public void testReplaceBug_2903815() {

        // failed
        try {
            String query = "let $f := <z>fred</z>" +
                    "let $s:= <s>xxxxtxxx</s>" +
                    "let $t := <t>t</t>" +
                    "return replace($s,$t,$f)";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "xxxxfredxxx",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

        // already OK
        try {
            String query = "let $f := \"fred\"" +
                    "let $s:= <s>xxxxtxxx</s>" +
                    "let $t := <t>t</t>" +
                    "return replace($s,$t,$f)";

            XPathQueryService service = (XPathQueryService) getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "xxxxfredxxx",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            ex.printStackTrace();
            fail(ex.toString());
        }

    }

    public void testMatchRegexp_Orbeon() {
        try {
            String query = "declare namespace text=\"http://exist-db.org/xquery/text\"; "
            + "let $count := count(for $resource in collection() let $resource-uri := document-uri($resource)"
            +" where (text:match-any($resource, 'gaga')) return 1) return  <foobar/>";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            service.query(query);

        } catch (XMLDBException ex) {
            // should not yield into NPE
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1840775
    public void bugtestAsDouble_1840775() {
        try {
            String query = "declare function local:testCase($failure as element(Failure)?)"
                    + "as element(TestCase) { <TestCase/> };"
                    + "local:testCase("
                    + "(: work-around for this eXist 1.1.2dev-rev:6992-20071127 bug: let $ltValue := 0.0 :)"
                    + "let $ltValue as xs:double := 0.0e0 return <Failure/>)";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            service.query(query);

        } catch (XMLDBException ex) {
            // should not yield into NPE
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=2117655
    public void testTypeMismatch_2117655() {
        try {
            String query = "declare namespace t = \"test\"; "
                    +"declare function t:foo() as xs:string{"
                    + "<Value>23</Value>}; "
                    + "t:foo()";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "23",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1959010
    public void testNoNamepaceDefinedForPrefix_1959010() {
        try {
            String query =
                     "declare function local:copy($nodes as node()*) as node()* "
                    +"{ "
                    +"for $n in $nodes "
                    +"return "
                    +"   if ($n instance of element()) then "
                    +"       element {node-name($n)} {(local:copy($n/@*), local:copy($n/node()))} "
                    +"   else if ($n instance of attribute()) then "
                    +"       attribute {node-name($n)} {$n} "
                    +"   else if ($n instance of text()) then "
                    +"       text {$n} "
                    +"   else "
                    +"       <Other/> "
                    +"}; "

                    +"let $c := <c:C xmlns:c=\"http://c\" xmlns:d=\"http://d\" d:d=\"ddd\">ccc</c:C> "
                    +"return local:copy($c)";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<c:C xmlns:d=\"http://d\" xmlns:c=\"http://c\" d:d=\"ddd\">ccc</c:C>",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    // http://sourceforge.net/support/tracker.php?aid=1807014
    public void testWrongAddNamespace_1807014() {
        try {
        	Collection testCollection = getTestCollection();
            Resource doc = testCollection.createResource("a.xqy", "BinaryResource");
            doc.setContent("module namespace a = \"http://www.a.com\"; "
                            +"declare function a:selectionList() as element(ul) { "
                            +"<ul class=\"a\"/> "
                            +"};");
            ((EXistResource) doc).setMimeType("application/xquery");
            testCollection.storeResource(doc);

            String query =
                    "declare option exist:serialize 'indent=no';"
                    +"import module namespace a = \"http://www.a.com\" at \"xmldb:exist://db/test/a.xqy\"; "
                    +"<html xmlns=\"http://www.w3.org/1999/xhtml\"> "
                    +"{ a:selectionList() } "
                    +"</html>";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "<html xmlns=\"http://www.w3.org/1999/xhtml\">"
                    +"<ul xmlns=\"\" class=\"a\"/></html>",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

  // http://sourceforge.net/support/tracker.php?aid=1789370
    public void testOrderBy_1789370() {
        try {
            String query =
                     "(for $vi in <elem>text</elem> order by $vi return $vi)/text()";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query, "text",
                    result.getResource(0).getContent().toString());

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }
    
  // http://sourceforge.net/support/tracker.php?aid=1817822
    public void testVariableScopeBug_1817822() {
        try {
            String query =
                         "declare namespace test = \"http://example.com\"; "
                        +"declare function test:expression($expr) as xs:double? { "
                        +" typeswitch($expr) "
                        +"   case element(Value) return test:value($expr) "
                        +"   case element(SomethingRandom) return test:product($expr/*) "
                        +"   default return () "
                        +"}; "

                        +"declare function test:value($expr) { "
                        +"   xs:double($expr) "
                        +"}; "

                        +"declare function test:product($expressions) { "
                        +"   test:expression($expressions[1]) "
                        +"   * "
                        +"   test:expression($expressions[2]) "
                        +"}; "

                        +"let $values := (<Value>2</Value>,<Value>3</Value>) "
                        +"let $a := test:expression(<AnotherSomethingRandom/>) "
                        +"let $b := test:product($values) "
                        +"return <Result>{$b}</Result>";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(1, result.getSize());
            assertEquals(query,
                    result.getResource(0).getContent().toString(), "<Result>6</Result>");

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

  // http://sourceforge.net/support/tracker.php?aid=1718626
    public void testConstructednodePosition_1718626() {
        try {
            String query =
                     "declare variable $categories := "
                    +" <categories> "
                    +"         <category uid=\"1\">Fruit</category> "
                    +"         <category uid=\"2\">Vegetable</category> "
                    +"         <category uid=\"3\">Meat</category> "
                    +"         <category uid=\"4\">Dairy</category> "
                    +" </categories> "
                    +" ; "

                    +" $categories/category[1], "
                    +" $categories/category[position() eq 1]";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query,
                    result.getResource(0).getContent().toString(), "<category uid=\"1\">Fruit</category>");
            assertEquals(query,
                    result.getResource(1).getContent().toString(), "<category uid=\"1\">Fruit</category>");

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

  // http://sourceforge.net/support/tracker.php?aid=1460791
    public void testDescendantOrSelf_1460791() {
        try {
            String query =
                     "declare option exist:serialize 'indent=no';"
                    +"let $test:=<z> <a> aaa </a> <z> zzz </z> </z> "
                    +"return "
                    +"( "
                    +"<one> {$test//z} </one>, "
                    +"<two> {$test/descendant-or-self::node()/child::z} </two> "
                    +"(: note that these should be the same *by definition* :) "
                    +")";

            XPathQueryService service = (XPathQueryService)
                    getTestCollection().getService("XPathQueryService", "1.0");
            ResourceSet result = service.query(query);

            assertEquals(2, result.getSize());
            assertEquals(query,
                    result.getResource(0).getContent().toString(), "<one><z> zzz </z></one>");
            assertEquals(query,
                    result.getResource(1).getContent().toString(), "<two><z> zzz </z></two>");

        } catch (XMLDBException ex) {
            // should not yield into exception
            ex.printStackTrace();
            fail(ex.toString());
        }
    }

    public void testAttributesSerialization() {
        ResourceSet result;
        String query;
        try {
            XPathQueryService service =
                    storeXMLStringAndGetQueryService(attributesSERIALIZATION, attributes);
            
            query = "//@* \n";
            try {
            	result = service.query(query);
            } catch (Exception e) {
            	//SENR0001 : OK
            	System.out.println(e.getMessage());
            }
            query = "declare option exist:serialize 'method=text'; \n"
            	+ "//@* \n";
            result = service.query(query);
            assertEquals("XQuery: " + query, 3, result.getSize());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    // ======================================
    /**
     * @return
     * @throws XMLDBException
     */
    private XPathQueryService storeXMLStringAndGetQueryService(String documentName,
            String content) throws XMLDBException {
        Collection testCollection = getTestCollection();
        XMLResource doc =
                (XMLResource) testCollection.createResource(
                documentName, "XMLResource");
        doc.setContent(content);
        testCollection.storeResource(doc);
        XPathQueryService service =
                (XPathQueryService) testCollection.getService(
                "XPathQueryService",
                "1.0");
        return service;
    }

    /**
     * @return
     * @throws XMLDBException
     */
    private XPathQueryService storeXMLStringAndGetQueryService(String documentName) throws XMLDBException {
        Collection testCollection = getTestCollection();
        XMLResource doc =
                (XMLResource) testCollection.createResource(
                documentName, "XMLResource");
        doc.setContent(new File(documentName));
        testCollection.storeResource(doc);
        XPathQueryService service = (XPathQueryService) testCollection.getService(
                "XPathQueryService", "1.0");
        return service;
    }

    /**
     * @param result
     * @throws XMLDBException
     */
    private void printResult(ResourceSet result) throws XMLDBException {
        for (ResourceIterator i = result.getIterator();
                i.hasMoreResources();) {
            Resource r = i.nextResource();
            System.out.println(r.getContent());
        }
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            attributeXML = args[0];
        }
        stringSize = 513;
        if (args.length > 1) {
            stringSize = Integer.parseInt(args[1]);
        }
        nbElem = 2;
        if (args.length > 2) {
            nbElem = Integer.parseInt(args[2]);
        }

        junit.textui.TestRunner.run(XQueryTest.class);
    }
}
