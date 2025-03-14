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

import org.exist.test.ExistWebServer;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class XPathQueryTest {

    @ClassRule
    public static final TemporaryFolder tempFolder = new TemporaryFolder();

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, true, true, true);
    private static final String PORT_PLACEHOLDER = "${PORT}";

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
                { "local", XmldbURI.LOCAL_DB },
                { "remote", "xmldb:exist://localhost:" + PORT_PLACEHOLDER + "/xmlrpc" +  XmldbURI.ROOT_COLLECTION}
        });
    }

    @Parameterized.Parameter
    public String apiName;

    @Parameterized.Parameter(value = 1)
    public String baseUri;

    private final String getBaseUri() {
        return baseUri.replace(PORT_PLACEHOLDER, Integer.toString(existWebServer.getPort()));
    }

    private final static String nested =
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<test><c></c><b><c><b></b></c></b><b></b><c></c></test>";
    
    private final static String numbers =
            "<test>"
            + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
            + "<item id='2'><price>7.4</price><stock>43</stock></item>"
            + "<item id='3'><price>18.4</price><stock>5</stock></item>"
            + "<item id='4'><price>65.54</price><stock>16</stock></item>"
            + "</test>";
    
    private final static String numbers2 =
            "<test xmlns=\"http://numbers.org\">"
            + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
            + "<item id='2'><price>7.4</price><stock>43</stock></item>"
            + "<item id='3'><price>18.4</price><stock>5</stock></item>"
            + "<item id='4'><price>65.54</price><stock>16</stock></item>"
            + "</test>";
    
    private final static String namespaces =
            "<test xmlns='http://www.foo.com'>"
            + "  <section>"
            + "      <title>Test Document</title>"
            + "      <c:comment xmlns:c='http://www.other.com'>This is my comment</c:comment>"
            + "  </section>"
            + "</test>";
    
    private final static String strings =
            "<test>"
            + "<string>Hello World!</string>"
            + "<string value='Hello World!'/>"
            + "<string>Hello</string>"
            + "</test>";
    
    private final static String nested2 =
            "<RootElement>" +
            "<ChildA>" +
            "<ChildB id=\"2\"/>" +
            "</ChildA>" +
            "</RootElement>";
    
    private final static String nested3 =
            "<test>" +
            "   <a>" +
            "       <t>1</t>" +
            "       <a>" +
            "           <t>2</t>" +
            "           <a>" +
            "               <t>3</t>" +
            "           </a>" +
            "       </a>" +
            "   </a>" +
            "</test>";
    
    private final static String siblings =
            "<!-- 1 --><!-- 2 -->" +
            "<test>" +
            "   <a> <s>A</s> <n>1</n> </a>" +
            "   <a> <s>Z</s> <n>2</n> </a>" +
            "   <a> <s>B</s> <n>3</n> </a>" +
            "   <a> <s>Z</s> <n>4</n> </a>" +
            "   <a> <s>C</s> <n>5</n> </a>" +
            "   <a> <s>Z</s> <n>6</n> </a>" +
            "</test>" +
            "<!-- 3 -->";

    private final static String siblings_attr = "<a b='c' bb='cc'/>";

    private final static String siblings_named1 =
            "<x>\n" +
            "    <y n=\"1\"/>\n" +
            "    <y n=\"2\"/>\n" +
            "    <y n=\"3\"/>\n" +
            "</x>";

    private final static String siblings_named2 =
            "<y>\n" +
            "    <y n=\"1\"/>\n" +
            "    <y n=\"2\"/>\n" +
            "    <y n=\"3\"/>\n" +
            "</y>";

    private final static String ids_content =
            "<test xml:space=\"preserve\">" +
            "<a ref=\"id1\"/>" +
            "<a ref=\"id1\"/>" +
            "<d ref=\"id2\"/>" +
            "<b id=\"id1\"><name>one</name></b>" +
            "<c xml:id=\"     id2     \"><name>two</name></c>" +
            "</test>";

    private final static String ids =
            "<!DOCTYPE test [" +
            "<!ELEMENT test (a | b | c | d)*>" +
            "<!ATTLIST test xml:space CDATA #IMPLIED>" +
            "<!ELEMENT a EMPTY>" +
            "<!ELEMENT b (name)>" +
            "<!ELEMENT c (name)>" +
            "<!ELEMENT d EMPTY>" +
            "<!ATTLIST d ref IDREF #IMPLIED>" +
            "<!ELEMENT name (#PCDATA)>" +
            "<!ATTLIST a ref IDREF #IMPLIED>" +
            "<!ATTLIST b id ID #IMPLIED>" +
            "<!ATTLIST c xml:id ID #IMPLIED>]>" +
            ids_content;
    
    private final static String date =
            "<timestamp date=\"2006-04-29+02:00\"/>";
    
    private final static String quotes =
            "<test><title>&quot;Hello&quot;</title></test>";
    
    private final static String ws =
            "<test><parent xml:space=\"preserve\"><text> </text><text xml:space=\"default\"> </text></parent></test>";
    
    private final static String self =
            "<test-self><a>Hello</a><b>World!</b></test-self>";

    private final static String predicates =
        "<elem1>\n" +
        " <elem2>\n" +
        "    <elem3/>\n" +
        " </elem2>\n" +
        " <elem2>\n" +
        "    <elem3>val1</elem3>\n" +
        " </elem2>\n" +
        " <elem2>\n" +
        "    <elem3>val2</elem3>\n" +
        " </elem2>\n" +
        "</elem1>";
    
    // Added by Geoff Shuetrim (geoff@galexy.net) to highlight problems with XPath queries of elements called 'xpointer'.
    private final static String xpointerElementName =
            "<test><xpointer/></test>";

    private final static String cdata_content = "Hello there \"Bob?\"";
    private final static String cdata_xml = "<elem1><![CDATA[" + cdata_content + "]]></elem1>";
    
    private Collection testCollection;
    
    @Before
    public void setUp() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root =
                DatabaseManager.getCollection(
                getBaseUri(),
                "admin",
                "");
        CollectionManagementService service =
                root.getService(
                CollectionManagementService.class);
        testCollection = service.createCollection("test");
        assertNotNull(testCollection);
    }

    @Test
    public void childWildcards() throws XMLDBException {
        final String docName = "testChildWildcards.xml";
        final XQueryService service =
            storeXMLStringAndGetQueryService(docName, "<test xmlns=\"http://test\"/>");

        service.setNamespace("t", "http://test");

        queryResource(service, docName, "/t:test", 1); //make sure all is well!

        queryResource(service, docName, "/*", 1);
        queryResource(service, docName, "/t:*", 1);
        queryResource(service, docName, "/*:test", 1);

        queryResource(service, docName, "/child::*", 1);
        queryResource(service, docName, "/child::t:*", 1);
        queryResource(service, docName, "/child::*:test", 1);
    }

    @Test
    public void pathExpression() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        //Invalid path expression left operand (not a node set).
        String message = "";
        try {
            queryAndAssert(service, "('a', 'b', 'c')/position()", -1, null);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue("Exception wanted: " + message, message.indexOf("XPTY0019") > -1);

        //Undefined context sequence
        message = "";
        try {
            queryAndAssert(service, "for $a in (<a/>, <b/>, doh, <c/>) return $a", -1, null);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue("Exception wanted: " + message, message.indexOf("XPDY0002") > -1);

        message = "";
        try {
            //"1 to 2" is resolved as a (1, 2), i.e. a sequence of *integers* which is *not* a singleton
            queryAndAssert(service, "let $a := (1, 2, 3) for $b in $a[1 to 2] return $b", -1, null);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        //No effective boolean value for such a kind of sequence !
        assertTrue("Exception wanted: " + message, message.indexOf("FORG0006") >-1);

        queryAndAssert(service, "let $a := ('a', 'b', 'c') return $a[2 to 2]", 1, null);
        queryAndAssert(service, "let $a := ('a', 'b', 'c') return $a[(2 to 2)]", 1, null);
        queryAndAssert(service, "let $x := <a min='1' max='10'/> return ($x/@min to $x/@max)", 10, null);
        queryAndAssert(service, "(1,2,3)[xs:decimal(.)]", 3, null);
        queryAndAssert(service, "(1,2,3)[. lt 3]", 2, null);
        queryAndAssert(service, "(0, 1, 2)[if(. eq 1) then 0 else position()]", 2, null);
        queryAndAssert(service, "(1, 2, 3)[if(1) then 1 else last()]", 1, null);
        queryAndAssert(service, "(1, 2, 3)[if(1) then 1 else position()]", 1, null);
        queryAndAssert(service, "()/position()", 0, null);
        queryAndAssert(service, "(0, 1, 2)[if(. eq 1) then 2 else 3]", 2, null);
        queryAndAssert(service, "(0, 1, 2)[remove((1, 'a string'), 2)]", 1, null);
        queryAndAssert(service, "let $page-ix := (1,3) return ($page-ix[1] to $page-ix[2])", 3, null);
    }

    /** test simple queries involving attributes */
    @Test
    public void attributes() throws XMLDBException {
        final String testDocument = "numbers.xml";

        final XQueryService service = storeXMLStringAndGetQueryService(
                testDocument, numbers);

        String query = "/test/item[ @id='1' ]";
        ResourceSet result = service.queryResource(testDocument, query);
        assertEquals("XPath: " + query, 1, result.getSize());

        XMLResource resource = (XMLResource)result.getResource(0);
        Node node = resource.getContentAsDOM();
        if (node.getNodeType() == Node.DOCUMENT_NODE)
            node = node.getFirstChild();
        assertEquals("XPath: " + query, "item", node.getNodeName());

        query = "/test/item [ @type='alphanum' ]";
        result = service.queryResource(testDocument, query);
        assertEquals("XPath: " + query, 1, result.getSize());
    }

    @Test
    public void starAxis() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        ResourceSet result = service.queryResource("numbers.xml", "/*/item");
        assertEquals("XPath: /*/item", 4, result.getSize());

        result = service.queryResource("numbers.xml", "/test/*");
        assertEquals("XPath: /test/*", 4, result.getSize());

        result = service.queryResource("numbers.xml", "/test/descendant-or-self::*");
        assertEquals("XPath: /test/descendant-or-self::*", 13, result.getSize());

        result = service.queryResource("numbers.xml", "/*/*");
        //Strange !!! Should be 8
        assertEquals("XPath: /*/*", 4, result.getSize());
    }

    @Test
    public void starAxisConstraints() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
        service.setNamespace("t", "http://www.foo.com");

        String query = "// t:title/text() [ . != 'aaaa' ]";
        ResourceSet result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize() );

        query = "/t:test/*:section[contains(., 'comment')]";
        result = service.queryResource("namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());

        query = "/t:test/t:*[contains(., 'comment')]";
        result = service.queryResource("namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());

        query = "/t:test/t:section[contains(., 'comment')]";
        result = service.queryResource("namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());

        query = "/t:test/t:section/*[contains(., 'comment')]";
        result = service.queryResource("namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());

        query = "/ * / * [ t:title ]";
        result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize() );

        query = "/ t:test / t:section [ t:title ]";
        result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize() );

        query = "/ t:test / t:section";
        result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize() );
    }

    @Test
    public void starAxisConstraints2() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
        service.setNamespace("t", "http://www.foo.com");

        String query =  "/ * [ ./ * / t:title ]";
        ResourceSet result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());

        query =  "/ * [ * / t:title ]";
        result = service.queryResource( "namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
    }

    @Test
    public void starAxisConstraints3() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
        service.setNamespace("t", "http://www.foo.com");

        final String query =  "// * [ . = 'Test Document' ]";
        final ResourceSet result = service.queryResource("namespaces.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
    }

    @Test
    public void root() throws XMLDBException {
        storeXMLStringAndGetQueryService("nested2.xml", nested2);
        final XQueryService service = storeXMLStringAndGetQueryService("numbers.xml", numbers);
        String query = "let $doc := <a><b/></a> return root($doc)";
        ResourceSet result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
        final XMLResource resource = (XMLResource)result.getResource(0);
        Node node = resource.getContentAsDOM();
        //Oh dear ! Don't tell me that *I* have written this :'( -pb
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        assertEquals("XPath: " + query, "a", node.getLocalName());

        query = "let $c := (<a/>,<b/>,<c/>,<d/>,<e/>) return count($c/root())";
        result = service.queryResource("numbers.xml", query);
        assertEquals("5", result.getResource(0).getContent().toString());
    }

    @Test
    public void name() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("nested2.xml", nested2);

        final String query = "(<a/>,<b/>)/name()";
        final ResourceSet result = service.queryResource("nested2.xml", query);

        assertEquals("XPath: " + query, 2, result.getSize());
        assertEquals("a", result.getResource(0).getContent().toString());
        assertEquals("b", result.getResource(1).getContent().toString());
    }

    @Test
    public void parentAxis() throws XMLDBException {
        XQueryService service =
                storeXMLStringAndGetQueryService("nested2.xml", nested2);

        queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/parent::*", 0);
        queryResource(service, "nested2.xml", "/RootElement//ChildB/parent::*", 1);
        queryResource(service, "nested2.xml", "/RootElement//ChildB/parent::*/ChildB", 1);
        queryResource(service, "nested2.xml", "/RootElement/ChildA/parent::*/ChildA/ChildB", 1);

        service =
                storeXMLStringAndGetQueryService("numbers2.xml", numbers2);
        service.setNamespace("n", "http://numbers.org");
        queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::*[@id = '3']", 1);
        queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::n:item[@id = '3']", 1);
        queryResource(service, "numbers2.xml", "//n:price/parent::n:item[@id = '3']", 1);
        ResourceSet result =
                queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::n:*/string(@id)", 1);
        assertEquals("3", result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::*:item/string(@id)", 1);
        assertEquals("3", result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/../string(@id)", 1);
        assertEquals("3", result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::n:item/string(@id)", 1);
        assertEquals("3", result.getResource(0).getContent().toString());
        queryResource(service, "numbers2.xml",
            "for $price in //n:price where $price/parent::*[@id = '3']/n:stock = '5' return $price", 1);
    }

    @Test
    public void parentSelfAxis() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("nested2.xml", nested2);
        storeXMLStringAndGetQueryService("numbers.xml", numbers);
        queryResource(service, "nested2.xml", "/RootElement/descendant::*/parent::ChildA", 1);
        queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildB]/parent::RootElement", 0);
        queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildA]/parent::RootElement", 1);
        queryResource(service, "nested2.xml", "let $a := ('', 'b', '', '') for $b in $a[.] return <blah>{$b}</blah>", 1);

        final String query = "let $doc := <root><page><a>a</a><b>b</b></page></root>" +
                "return " +
                "for $element in $doc/page/* " +
                "return " +
                "if($element[self::a] or $element[self::b]) then (<found/>) else (<notfound/>)";
        final ResourceSet result = service.queryResource("numbers.xml", query);
        assertEquals(2, result.getSize());
    }

    @Test
    public void selfAxis() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("self.xml", self);

        queryResource(service, "self.xml", "/test-self/self::document-node()", 0);
        queryResource(service, "self.xml", "/test-self/self::node()", 1);
        queryResource(service, "self.xml", "/test-self/self::attribute()", 0);
        queryResource(service, "self.xml", "/test-self/self::element()", 1);
        queryResource(service, "self.xml", "/test-self/self::comment()", 0);
        queryResource(service, "self.xml", "/test-self/self::processing-instruction()", 0);
        queryResource(service, "self.xml", "/test-self/self::text()", 0);
        queryResource(service, "self.xml", "/test-self/self::namespace-node()", 0);

        queryResource(service, "self.xml", "/test-self/*[not(self::a)]", 1);
        queryResource(service, "self.xml", "/test-self/*[self::a]", 1);

        queryResource(service, "self.xml", "/self::document-node()", 1);
        queryResource(service, "self.xml", "/self::node()", 1);
        queryResource(service, "self.xml", "/self::attribute()", 0);
        queryResource(service, "self.xml", "/self::element()", 0);
        queryResource(service, "self.xml", "/self::comment()", 0);
        queryResource(service, "self.xml", "/self::processing-instruction()", 0);
        queryResource(service, "self.xml", "/self::text()", 0);
        queryResource(service, "self.xml", "/self::namespace-node()", 0);
    }

    @Test
    public void ancestorAxis() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("nested3.xml", nested3);

        // test ancestor axis with positional predicate
        queryResource(service, "nested3.xml", "//a[ancestor::a[2]/t = '1']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor::*[2]/t = '1']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor::a[1]/t = '2']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor::*[1]/t = '2']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor-or-self::*[3]/t = '1']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor-or-self::a[3]/t = '1']", 1);
        // Following test fails
//            queryResource(service, "nested3.xml", "//a[ancestor-or-self::*[2]/t = '2']", 1);
        queryResource(service, "nested3.xml", "//a[ancestor-or-self::a[2]/t = '2']", 1);
        queryResource(service, "nested3.xml", "//a[t = '3'][ancestor-or-self::a[3]/t = '1']", 1);
        queryResource(service, "nested3.xml", "//a[t = '3'][ancestor-or-self::*[3]/t = '1']", 1);
    }

    @Test
    public void ancestorIndex() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("nested2.xml", nested2);

        queryResource(service, "nested2.xml", "//ChildB/ancestor::*[1]/self::ChildA", 1);
        queryResource(service, "nested2.xml", "//ChildB/ancestor::*[2]/self::RootElement", 1);
        queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 1]/self::ChildA", 1);
        queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 2]/self::RootElement", 1);
        queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 2]/self::RootElement", 1);
        queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/ancestor::*", 0);
    }

    @Test
    public void precedingSiblingAxis_persistent() throws XMLDBException, IOException, SAXException {
        XQueryService service =
                storeXMLStringAndGetQueryService("siblings.xml", siblings);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = queryResource(service, "siblings.xml", "//a[preceding-sibling::*[1]/s = 'B']", 1);
        assertXMLEqual("<a> <s>Z</s> <n>4</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[preceding-sibling::a[1]/s = 'B']", 1);
        assertXMLEqual("<a> <s>Z</s> <n>4</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[preceding-sibling::*[2]/s = 'B']", 1);
        assertXMLEqual("<a> <s>C</s> <n>5</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[preceding-sibling::a[2]/s = 'B']", 1);
        assertXMLEqual("<a> <s>C</s> <n>5</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/test/preceding-sibling::node()", 2);
        assertEquals("<!-- 1 -->", result.getResource(0).getContent().toString());
        assertEquals("<!-- 2 -->", result.getResource(1).getContent().toString());

        queryResource(service, "siblings.xml", "/node()[1]/preceding-sibling::node()", 0);

        result = queryResource(service, "siblings.xml", "/node()[2]/preceding-sibling::node()", 1);
        assertEquals("<!-- 1 -->", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/node()[3]/preceding-sibling::node()", 2);
        assertEquals("<!-- 1 -->", result.getResource(0).getContent().toString());
        assertEquals("<!-- 2 -->", result.getResource(1).getContent().toString());

        queryResource(service, "siblings.xml", "/comment()[1]/preceding-sibling::comment()", 0);

        result = queryResource(service, "siblings.xml", "/comment()[2]/preceding-sibling::comment()[1]", 1);
        assertEquals("<!-- 1 -->", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/comment()[3]/preceding-sibling::comment()[1]", 1);
        assertEquals("<!-- 2 -->", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/comment()[3]/preceding-sibling::comment()[2]", 1);
        assertEquals("<!-- 1 -->", result.getResource(0).getContent().toString());

        service = storeXMLStringAndGetQueryService("siblings_attr.xml", siblings_attr);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_attr.xml", "/a/@bb/preceding-sibling::*", 0);

        service = storeXMLStringAndGetQueryService("siblings_named1.xml", siblings_named1);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_named1.xml", "//y[@n eq '2']/preceding-sibling::*:y", 1);
        queryResource(service, "siblings_named1.xml", "//y[@n eq '2']/preceding-sibling::y", 1);

        service = storeXMLStringAndGetQueryService("siblings_named2.xml", siblings_named2);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_named2.xml", "//y[@n eq '2']/preceding-sibling::*:y", 1);
        queryResource(service, "siblings_named2.xml", "//y[@n eq '2']/preceding-sibling::y", 1);
    }

    @Test
    public void precedingSiblingAxis_memtree() throws XMLDBException, IOException, SAXException {
        final XQueryService service = getQueryService();
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet rs = service.query("(<a/>, <b/>, <c/>)/preceding-sibling::*");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := <doc><div id='1'/><div id='2'><div id='3'/></div><div id='4'/><div id='5'><div id='6'/></div></doc> " +
                "return $doc/div/preceding-sibling::div");
        assertEquals(3, rs.getSize());
        assertXMLEqual("<div id='1'/>", rs.getResource(0).getContent().toString());
        assertXMLEqual("<div id='2'><div id='3'/></div>", rs.getResource(1).getContent().toString());
        assertXMLEqual("<div id='4'/>", rs.getResource(2).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/node()[1]/preceding-sibling::node()");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/node()[2]/preceding-sibling::node()");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 1 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/node()[3]/preceding-sibling::node()");
        assertEquals(2, rs.getSize());
        assertEquals("<!-- 1 -->", rs.getResource(0).getContent().toString());
        assertEquals("<!-- 2 -->", rs.getResource(1).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[1]/preceding-sibling::comment()");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[2]/preceding-sibling::comment()[1]");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 1 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[3]/preceding-sibling::comment()[1]");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 2 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[3]/preceding-sibling::comment()[2]");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 1 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $elem := <a b='c' bb='cc'/> return $elem/@bb/preceding-sibling::*");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := document { <x><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></x> } return $doc //y[@n eq '2']/preceding-sibling::*:y");
        assertEquals(1, rs.getSize());
        rs = service.query("let $doc := document { <x><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></x> } return $doc //y[@n eq '2']/preceding-sibling::y");
        assertEquals(1, rs.getSize());

        rs = service.query("let $doc := document { <y><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></y> } return $doc //y[@n eq '2']/preceding-sibling::*:y");
        assertEquals(1, rs.getSize());
        rs = service.query("let $doc := document { <y><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></y> } return $doc //y[@n eq '2']/preceding-sibling::y");
        assertEquals(1, rs.getSize());
    }

    @Test
    public void followingSiblingAxis_persistent() throws XMLDBException, IOException, SAXException {
        XQueryService service = storeXMLStringAndGetQueryService("siblings.xml", siblings);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet result = queryResource(service, "siblings.xml", "//a[following-sibling::*[1]/s = 'B']", 1);
        assertXMLEqual("<a> <s>Z</s> <n>2</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[following-sibling::a[1]/s = 'B']", 1);
        assertXMLEqual("<a> <s>Z</s> <n>2</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[following-sibling::*[2]/s = 'B']", 1);
        assertXMLEqual("<a> <s>A</s> <n>1</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "//a[following-sibling::a[2]/s = 'B']", 1);
        assertXMLEqual("<a> <s>A</s> <n>1</n> </a>", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/test/following-sibling::node()", 1);
        assertEquals("<!-- 3 -->", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/node()[1]/following-sibling::node()", 3);
        assertEquals("<!-- 2 -->", result.getResource(0).getContent().toString());
        final Node testElem = ((XMLResource)result.getResource(1)).getContentAsDOM();
        assertTrue(testElem instanceof Element);
        assertEquals("test", testElem.getNodeName());
        assertEquals("<!-- 3 -->", result.getResource(2).getContent().toString());

        result = queryResource(service, "siblings.xml", "/comment()[1]/following-sibling::comment()[1]", 1);
        assertEquals("<!-- 2 -->", result.getResource(0).getContent().toString());

        result = queryResource(service, "siblings.xml", "/comment()[1]/following-sibling::comment()[2]", 1);
        assertEquals("<!-- 3 -->", result.getResource(0).getContent().toString());

        service = storeXMLStringAndGetQueryService("siblings_attr.xml", siblings_attr);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_attr.xml", "/a/@b/following-sibling::*", 0);

        service = storeXMLStringAndGetQueryService("siblings_named1.xml", siblings_named1);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_named1.xml", "//y[@n eq '2']/following-sibling::*:y", 1);
        queryResource(service, "siblings_named1.xml", "//y[@n eq '2']/following-sibling::y", 1);

        service = storeXMLStringAndGetQueryService("siblings_named2.xml", siblings_named2);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings_named2.xml", "//y[@n eq '2']/following-sibling::*:y", 1);
        queryResource(service, "siblings_named2.xml", "//y[@n eq '2']/following-sibling::y", 1);
    }

    @Test
    public void followingSiblingAxis_memtree() throws XMLDBException, IOException, SAXException {
        final XQueryService service = getQueryService();
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        ResourceSet rs = service.query("(<a/>, <b/>, <c/>)/following-sibling::*");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := <doc><div id='1'><div id='2'/></div><div id='3'/></doc> " +
                "return $doc/div[1]/following-sibling::div");
        assertEquals(1, rs.getSize());
        assertXMLEqual("<div id='3'/>", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/test/following-sibling::node()");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 3 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/node()[1]/following-sibling::node()");
        assertEquals(3, rs.getSize());
        assertEquals("<!-- 2 -->", rs.getResource(0).getContent().toString());
        assertXMLEqual("<test/>", rs.getResource(1).getContent().toString());
        assertEquals("<!-- 3 -->", rs.getResource(2).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[1]/following-sibling::comment()[1]");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 2 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $doc := document { <!-- 1 -->,<!-- 2 -->,<test/>,<!-- 3 --> } return $doc/comment()[1]/following-sibling::comment()[2]");
        assertEquals(1, rs.getSize());
        assertEquals("<!-- 3 -->", rs.getResource(0).getContent().toString());

        rs = service.query("let $elem := <a b='c' bb='cc'/> return $elem/@b/following-sibling::*");
        assertEquals(0, rs.getSize());

        rs = service.query("let $doc := document { <x><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></x> } return $doc //y[@n eq '2']/following-sibling::*:y");
        assertEquals(1, rs.getSize());
        rs = service.query("let $doc := document { <x><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></x> } return $doc //y[@n eq '2']/following-sibling::y");
        assertEquals(1, rs.getSize());

        rs = service.query("let $doc := document { <y><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></y> } return $doc //y[@n eq '2']/following-sibling::*:y");
        assertEquals(1, rs.getSize());
        rs = service.query("let $doc := document { <y><y n=\"1\"/><y n=\"2\"/><y n=\"3\"/></y> } return $doc //y[@n eq '2']/following-sibling::y");
        assertEquals(1, rs.getSize());
    }

    @Test
    public void followingAxis() throws XMLDBException, IOException, SAXException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("siblings.xml", siblings);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");

        queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s", 3);
        queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::n", 4);
        ResourceSet result = queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s[1]", 1);
        assertXMLEqual("<s>Z</s>", result.getResource(0).getContent().toString());
        result = queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s[2]", 1);
        assertXMLEqual("<s>C</s>", result.getResource(0).getContent().toString());
    }

    @Test
    public void precedingAxis() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("siblings.xml", siblings);
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");
        queryResource(service, "siblings.xml", "//a/s[. = 'B']/preceding::s", 2);
        queryResource(service, "siblings.xml", "//a/s[. = 'C']/preceding::s", 4);
        queryResource(service, "siblings.xml", "//a/n[. = '3']/preceding::s", 3);
    }

    @Test
    public void position() throws XMLDBException, IOException, SAXException {

        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        queryResource(service, "numbers.xml", "//item[position() = 3]", 1);
        queryResource(service, "numbers.xml", "//item[position() < 3]", 2);
        queryResource(service, "numbers.xml", "//item[position() <= 3]", 3);
        queryResource(service, "numbers.xml", "//item[position() > 3]", 1);
        queryResource(service, "numbers.xml", "//item[position() >= 3]", 2);
        queryResource(service, "numbers.xml", "//item[position() eq 3]", 1);
        queryResource(service, "numbers.xml", "//item[position() lt 3]", 2);
        queryResource(service, "numbers.xml", "//item[position() le 3]", 3);
        queryResource(service, "numbers.xml", "//item[position() gt 3]", 1);
        queryResource(service, "numbers.xml", "//item[position() ge 3]", 2);

        queryResource(service, "numbers.xml", "//item[last() - 1]", 1);
        queryResource(service, "numbers.xml", "//item[count(('a','b')) - 1]", 1);

        String query = "for $a in (<a/>, <b/>, <c/>) return $a/position()";
        ResourceSet  result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 3, result.getSize());
        XMLResource resource = (XMLResource)result.getResource(0);
        assertEquals("XPath: " + query, "1", resource.getContent().toString());
        resource = (XMLResource)result.getResource(1);
        assertEquals("XPath: " + query, "1", resource.getContent().toString());
        resource = (XMLResource)result.getResource(2);
        assertEquals("XPath: " + query, "1", resource.getContent().toString());
            

        query = "declare variable $doc := <root>" +
                "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                "</root>; " +
                "(for $x in $doc/a return $x)[position() mod 3 = 2]";
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 2, result.getSize());

        query = "declare variable $doc := <root>" +
                "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                "</root>; " +
                "for $x in $doc/a return $x[position() mod 3 = 2]";
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 0, result.getSize());

        query = "declare variable $doc := <root>" +
                "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                "</root>; " +
                "for $x in $doc/a[position() mod 3 = 2] return $x";
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 2, result.getSize());


        query = "let $test := <test><a> a </a><a>a</a></test>" +
                "return distinct-values($test/a/normalize-space(.))";
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
        resource = (XMLResource)result.getResource(0);
        assertEquals("XPath: " + query, "a", resource.getContent().toString());

        query = "let $doc := document {<a><b n='1'/><b n='2'/></a>} " +
            "return $doc//b/(if (@n = '1') then position() else ())";
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());
        //Try a second time to see if the position is reset
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
        assertEquals("1", result.getResource(0).getContent().toString());

        query = "let $doc := document {<a><b/></a>} " +
        "return $doc/a[1] [b[1]]";
        service.setProperty(OutputKeys.INDENT, "no");
        result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 1, result.getSize());
        assertXMLEqual("<a><b/></a>", result.getResource(0).getContent().toString());

        //TODO : make this work ! It currently returns some content
        //query = "let $doc := document {<a><b><c>1</c></b><b><c>a</c></b></a>} " +
        //	"return $doc/a[b[position() = 2]/c[.='1']]";
        //result = service.queryResource("numbers.xml", query);
        //assertEquals("XPath: " + query, 0, result.getSize());

        // TODO: make this work ! It currently returns 1
        //query = "let $a := ('a', 'b', 'c') for $b in $a[position()] return <blah>{$b}</blah>";
        //result = service.queryResource("numbers.xml", query);
        //assertEquals("XPath: " + query, 3, result.getSize());
    }

    @Test
    public void last() throws XMLDBException {
        final XQueryService service =
            storeXMLStringAndGetQueryService("numbers.xml", numbers);

        final String query = "<a><b>test1</b><b>test2</b></a>/b/last()";
        final ResourceSet  result = service.queryResource("numbers.xml", query);
        assertEquals("XPath: " + query, 2, result.getSize());
        XMLResource resource = (XMLResource)result.getResource(0);
        assertEquals("XPath: " + query, "2", resource.getContent().toString());
        resource = (XMLResource)result.getResource(1);
        assertEquals("XPath: " + query, "2", resource.getContent().toString());
    }


    @Test
    public void numbers() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        ResourceSet result = queryResource(service, "numbers.xml", "sum(/test/item/price)", 1);
        assertEquals("96.94", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "round(sum(/test/item/price))", 1);
        assertEquals("97", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "floor(sum(/test/item/stock))", 1);
        assertEquals( "86", result.getResource(0).getContent().toString());

        queryResource(service, "numbers.xml", "/test/item[round(price + 3) > 60]", 1);

        result = queryResource(service, "numbers.xml", "min(( 123456789123456789123456789, " +
                "123456789123456789123456789123456789123456789 ))", 1);
        assertEquals("minimum of big integers",
                "123456789123456789123456789",
                result.getResource(0).getContent().toString());

        String message = "";
        try {
            queryResource(service, "numbers.xml", "empty(() + (1, 2))", 1);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);
    }

    @Test
    public void dates() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        String query = "xs:untypedAtomic(\"--12-05:00\") cast as xs:gMonth";
        ResourceSet  result = service.queryResource("numbers.xml", query);
        XMLResource resource = (XMLResource)result.getResource(0);
        assertEquals("XPath: " + query, "--12-05:00", resource.getContent().toString());

        query = "(xs:dateTime(\"0001-01-01T01:01:01Z\") + xs:yearMonthDuration(\"-P20Y07M\"))";
        result = service.queryResource("numbers.xml", query);
        resource = (XMLResource)result.getResource(0);
        assertEquals("XPath: " + query, "-0021-06-01T01:01:01Z", resource.getContent().toString());
    }    
    
    @Test
    public void generalComparison() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("dates.xml", date);
        queryResource(service, "dates.xml", "/timestamp[@date = xs:date('2006-04-29+02:00')]", 1);
    }
    
    @Test
    public void predicates() throws XMLDBException, IOException, SAXException {
        final String numbers =
                "<test>"
                + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
                + "<item id='2'><price>7.4</price><stock>43</stock></item>"
                + "<item id='3'><price>18.4</price><stock>5</stock></item>"
                + "<item id='4'><price>65.54</price><stock>16</stock></item>"
                + "</test>";

        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);
        service.setProperty(OutputKeys.INDENT, "no");
        ResourceSet result = queryResource(service, "numbers.xml", "/test/item[2]/price/text()", 1);
        assertEquals("7.4", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "/test/item[5]", 0);

        result = queryResource(service, "numbers.xml", "/test/item[@id='4'][1]/price[1]/text()", 1);
        assertEquals("65.54",
                result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "for $i in //item return " +
                "<item>{$i/price, $i/stock}</item>", 4);
        assertXMLEqual("<item><price>5.6</price><stock>22</stock></item>",
                result.getResource(0).getContent().toString());
        assertXMLEqual("<item><price>65.54</price><stock>16</stock></item>",
                result.getResource(3).getContent().toString());

        // test positional predicates
        result = queryResource(service, "numbers.xml", "/test/node()[2]", 1);
        assertXMLEqual("<item id='2'><price>7.4</price><stock>43</stock></item>",
                result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers.xml", "/test/element()[2]", 1);
        assertXMLEqual("<item id='2'><price>7.4</price><stock>43</stock></item>",
                result.getResource(0).getContent().toString());

        // positional predicate on sequence of atomic values
        result = queryResource(service, "numbers.xml", "('test', 'pass')[2]", 1);
        assertEquals("pass", result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers.xml", "let $credentials := ('test', 'pass') let $user := $credentials[1] return $user", 1);
        assertEquals("test", result.getResource(0).getContent().toString());
        result = queryResource(service, "numbers.xml", "let $credentials := ('test', 'pass') let $user := $credentials[2] return $user", 1);
        assertEquals("pass", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "let $els := <els><el>text1</el><el>text2</el></els> return $els/el[xs:string(.) eq 'text1'] ", 1);
        assertEquals("<el>text1</el>", result.getResource(0).getContent().toString());
    }

    @Test
    public void predicates2() throws XMLDBException, IOException, SAXException {
        final String numbers =
                "<test>"
                + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
                + "<item id='2'><price>7.4</price><stock>43</stock></item>"
                + "<item id='3'><price>18.4</price><stock>5</stock></item>"
                + "<item id='4'><price>65.54</price><stock>16</stock></item>"
                + "</test>";

        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);
        service.setProperty(OutputKeys.INDENT, "no");


        String query = "let $t := <test>" + "<a> <s>A</s> 1 </a>"
                + "<a> <s>Z</s> 2 </a>" + "<a> <s>B</s> 3 </a>"
                + "<a> <s>Z</s> 4 </a>" + "<a> <s>C</s> 5 </a>"
                + "<a> <s>Z</s> 6 </a>" + "</test>"
                + "return $t//a[s='Z' and preceding-sibling::*[1]/s='B']";
        ResourceSet result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<a><s>Z</s> 4 </a>", result.getResource(0)
        .getContent().toString());

        query = "let $t := <test>" + "<a> <s>A</s> 1 </a>"
                + "<a> <s>Z</s> 2 </a>" + "<a> <s>B</s> 3 </a>"
                + "<a> <s>Z</s> 4 </a>" + "<a> <s>C</s> 5 </a>"
                + "<a> <s>Z</s> 6 </a>" + "</test>"
                + "return $t//a[s='Z' and ./preceding-sibling::*[1]/s='B']";
        result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<a><s>Z</s> 4 </a>", result.getResource(0)
        .getContent().toString());

        query = "let $doc := <doc><rec n='1'><a>first</a><b>second</b></rec>" +
                "<rec n='2'><a>first</a><b>third</b></rec></doc> " +
                "return $doc//rec[fn:not(b = 'second') and (./a = 'first')]";
        result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<rec n=\"2\"><a>first</a><b>third</b></rec>", result.getResource(0)
        .getContent().toString());

        query = "let $doc := <doc><a b='c' d='e'/></doc> " +
                "return $doc/a[$doc/a/@b or $doc/a/@d]";
        result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<a b=\"c\" d=\"e\"/>", result.getResource(0)
                .getContent().toString());

        query = "let $x := <a><b><x/><x/></b><b><x/></b></a>" +
            "return $x//b[count(x) = 2]";
        result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<b><x/><x/></b>", result.getResource(0)
                .getContent().toString());



        //Boolean evaluation for "." (atomic sequence)
        query = "(1,2,3)[xs:decimal(.)]";
        result = queryResource(service, "numbers.xml", query, 3);

        query = "(1,2,3)[number()]";
        result = queryResource(service, "numbers.xml", query, 3);

        query = " 	let $c := (<a/>,<b/>), $i := 1 return $c[$i]";
        result = queryResource(service, "numbers.xml", query, 1);
        assertXMLEqual("<a/>", result.getResource(0)
                .getContent().toString());

        query = "(1,2,3)[position() = last()]";
        result = queryResource(service, "numbers.xml", query, 1);
        assertEquals("3", result.getResource(0).getContent().toString());

        query = "(1,2,3)[max(.)]";
        result = queryResource(service, "numbers.xml", query, 3);

        query = "(1,2,3)[max(.[. gt 1])]";
        result = queryResource(service, "numbers.xml", query, 2);
        assertEquals("2", result.getResource(0).getContent().toString());
        assertEquals("3", result.getResource(1).getContent().toString());

        query = "(1,2,3)[.]";
        result = queryResource(service, "numbers.xml", query, 3);

        query = "declare function local:f ($n) { " +
            "$n " +
            "}; " +
            " " +
            "declare function local:g( $n ) { " +
            "('OK','Fine','Wrong') [local:f($n) + 1 ] " +
            "} ; " +
            " " +
            "declare function local:h( $n ) { " +
            "('OK','Fine','Wrong') [local:f($n) ] " +
            "} ; " +
            " " +
            "declare function local:j( $n ) { " +
            "let $m := local:f($n) " +
            "return " +
            "('OK','Fine','Wrong') [$m + 1 ] " +
            "} ; " +
            " " +
            "declare function local:k ( $n ) { " +
            "('OK','Fine','Wrong') [ $n + 1 ] " +
            "} ; " +
            " " +
            "local:f(1),local:g(1), local:h(1), local:j(1), local:k(1) ";
        result = queryResource(service, "numbers.xml", query, 5);
        assertEquals("1", result.getResource(0).getContent().toString());
        assertEquals("Fine", result.getResource(1).getContent().toString());
        assertEquals("OK", result.getResource(2).getContent().toString());
        assertEquals("Fine", result.getResource(3).getContent().toString());
        assertEquals("Fine", result.getResource(4).getContent().toString());

        //The collection doesn't exist : let's see how the query behaves with empty sequences
        query = "let $checkDate := xs:date(adjust-date-to-timezone(current-date(), ())) " +
        "let $collection := if (xmldb:collection-available(\"/db/lease\")) then collection(\"/db/lease\") else () " +
        "for $x in " +
        "$collection//Lease/Events/Type/Event[(When/Date<=$checkDate or " +
        "When/EstimateDate<=$checkDate) and not(Status='Complete')] " +
        "return $x";
        result = queryResource(service, "numbers.xml", query, 0);

        query = "let $res := <test><element name='A'/><element name='B'/></test> " +
            "return " +
            "for $name in ('A', 'B') return " +
            "$res/element[@name=$name][1]";
        result = queryResource(service, "numbers.xml", query, 2);
        assertXMLEqual("<element name='A'/>", result.getResource(0).getContent().toString());
        assertXMLEqual("<element name='B'/>", result.getResource(1).getContent().toString());
    }


    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1460610&group_id=17691&atid=117691
     */
    @Test
    public void predicates_bug1460610() throws XMLDBException {
        final String xQuery = "(1, 2, 3)[ . lt 3]";
        
        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
        
        assertEquals("SFBUG 1460610 nr of results", 2, rs.getSize());
        assertEquals("SFBUG 1460610 1st result", "1",
                rs.getResource(0).getContent().toString());
        assertEquals("SFBUG 1460610 2nd result", "2",
                rs.getResource(1).getContent().toString());
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1537355&group_id=17691&atid=117691
     */
    @Test
    public void predicates_bug1537355() throws XMLDBException {
        final String xQuery = "let $one := 1 return (1, 2, 3)[$one + 1]";
        
        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
        
        assertEquals("SFBUG 1537355 nr of results", 1, rs.getSize());
        assertEquals("SFBUG 1537355 result", "2",
                rs.getResource(0).getContent().toString());
    }

    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1533053&group_id=17691&atid=117691
     */
    @Test
    public void nestedPredicates_bug1533053() throws XMLDBException, IOException, SAXException {
        String xQuery = "let $doc := <objects>" +
    	    "<detail><class/><source><dynamic>false</dynamic></source></detail>" +
    	    "<detail><class/><source><dynamic>true</dynamic></source></detail>" +
    	    "</objects> " +
    	    "let $matches := $doc/detail[source[dynamic='false'] or class] " +
    	    "return count($matches) eq 2";
    
	    XQueryService service = getQueryService();
	    ResourceSet rs = service.query(xQuery);
	    
	    assertEquals(1, rs.getSize());
	    assertEquals("true", rs.getResource(0).getContent().toString());

	    xQuery = "let $xml := <test><element>" +
	    	"<complexType><attribute name=\"design\" fixed=\"1\"/></complexType>" +
        	"</element></test> " +
        	"return $xml//element[complexType/attribute[@name eq \"design\"]/@fixed eq \"1\"]";

	    service = getQueryService();
        service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        service.setProperty(OutputKeys.INDENT, "no");	    
	    rs = service.query(xQuery);

	    assertEquals(1, rs.getSize());
	    assertXMLEqual("<element><complexType><attribute name=\"design\" fixed=\"1\"/></complexType></element>", 
	    		rs.getResource(0).getContent().toString());

    }

    
    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1488303&group_id=17691&atid=117691
     */
    @Test
    public void predicate_bug1488303() throws XMLDBException {
        XQueryService service = getQueryService();
        ResourceSet rs=null;
        
        // test one
        final String xQuery1 = "let $q := <q><t>eXist</t></q> return $q//t";
        rs = service.query(xQuery1);
        assertEquals("nr of results", 1, rs.getSize());
        assertEquals("result", "<t>eXist</t>",
                rs.getResource(0).getContent().toString());
        
        // test two
        final String xQuery2 = "let $q := <q><t>eXist</t></q> return ($q//t)[1]";
        rs = service.query(xQuery2);
        assertEquals("nr of results", 1, rs.getSize());
        assertEquals("result", "<t>eXist</t>",
                rs.getResource(0).getContent().toString());
        
        // This one fails http://sourceforge.net/tracker/index.php?func=detail&aid=1488303&group_id=17691&atid=117691
        final String xQuery3 = "let $q := <q><t>eXist</t></q> return $q//t[1]";
        rs = service.query(xQuery3);
        assertEquals("SFBUG 1488303 nr of results", 1, rs.getSize());
        assertEquals("SFBUG 1488303 result", "<t>eXist</t>",
                rs.getResource(0).getContent().toString());
    }

    
    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1460791&group_id=17691&atid=117691
     */
    @Test
    public void descendantOrSelf_bug1460791() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; let $test:=<z><a>aaa</a><z>zzz</z></z> "
                +"return ( <one>{$test//z}</one>, <two>{$test/descendant-or-self::node()/child::z}</two> )";
        
        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
        
//        System.out.println("BUG1460791/1" + rs.getResource(0).getContent().toString() );
//        System.out.println("BUG1460791/2" + rs.getResource(1).getContent().toString() );
        
        assertEquals("SFBUG 1460791 nr of results", 2, rs.getSize());
        
        assertEquals("SFBUG 1460791 result part 1", "<one><z>zzz</z></one>",
                rs.getResource(0).getContent().toString());
        
        assertEquals("SFBUG 1460791 result part 2", "<two><z>zzz</z></two>",
                rs.getResource(1).getContent().toString());
    }
    
    /**
     * @see http://sourceforge.net/tracker/index.php?func=detail&aid=1462120&group_id=17691&atid=1176
     */
    @Test
    public void xpath_bug1462120() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                +"let $m:=<Units><Unit name=\"g\" size=\"1\"/>"
                +"<Unit name=\"kg\" size=\"1000\"/></Units> "
                +"let $list:=(<Product aaa=\"g\"/>, <Product aaa=\"kg\"/>) "
                +"let $one:=$list[1] return ( "
                +"$m/Unit[string(data(@name)) eq string(data($list[1]/@aaa))],"
                +"<br/>,$m/Unit[string(data(@name)) eq string(data($one/@aaa))] )";
        
        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
        
        assertEquals("SFBUG 1462120 nr of results", 3, rs.getSize());
        
        assertEquals("SFBUG 1462120 result part 1", "<Unit name=\"g\" size=\"1\"/>",
                rs.getResource(0).getContent().toString());
        
        assertEquals("SFBUG 1462120 result part 2", "<br/>",
                rs.getResource(1).getContent().toString());
        
        assertEquals("SFBUG 1462120 result part 3", "<Unit name=\"g\" size=\"1\"/>",
                rs.getResource(2).getContent().toString());
    }
    
    
    /**
     * In Predicate.java, the contextSet and the outerSequence.toNodeSet()
     * documents are different so that no match can occur.
     *
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    @Test
    public void predicate_bug_wiki_1() throws XMLDBException {
        final String xQuery = "let $dum := <dummy><el>1</el><el>2</el></dummy> return $dum/el[2]";
        
        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
        
        assertEquals("Predicate bug wiki_1", 1, rs.getSize());
        assertEquals("Predicate bug wiki_1", "<el>2</el>",
                rs.getResource(0).getContent().toString());
    }

    @Test
    public void predicate_bug_andrzej() throws XMLDBException {
        final String xQuery =
            "doc('/db/test/predicates.xml')//elem1/elem2[ string-length( ./elem3 ) > 0][1]/elem3/text()";
        final XQueryService service =
            storeXMLStringAndGetQueryService("predicates.xml", predicates);
        final ResourceSet rs = service.query(xQuery);
        assertEquals("testPredicateBUGAndrzej", 1, rs.getSize());
        assertEquals("testPredicateBUGAndrzej", "val1", rs.getResource(0).getContent().toString());
    }

    /**
     * removing Self: makes the query work OK
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    @Test
    public void cardinalitySelf_bug_wiki_2() throws XMLDBException {
        final String xQuery = "let $test := <test><works><employee>a</employee><employee>b</employee></works></test> "
                + "for $h in $test/works/employee[2] return fn:name($h/self::employee)";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("CardinalitySelfBUG bug wiki_2", 1, rs.getSize());
        assertEquals("CardinalitySelfBUG bug wiki_2", "employee",
                rs.getResource(0).getContent().toString());
        
    }
    
    /**
     * Problem in VirtualNodeSet which return 2 attributes because it 
     * computes every level
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    @Test
    public void virtualNodeset_bug_wiki_3() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                + "let $node := (<c id=\"OK\"><b id=\"cool\"/></c>)"
                + "/descendant::*/attribute::id return <a>{$node}</a>";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);
            
        assertEquals("VirtualNodesetBUG_wiki_3", 1, rs.getSize());
        assertEquals("VirtualNodesetBUG_wiki_3", "<a id=\"cool\"/>",
                rs.getResource(0).getContent().toString());
    } 
    
    /**
     * Problem in VirtualNodeSet because it computes the wrong level
     *
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    @Test
    public void virtualNodeset_bug_wiki_4() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                + "let $node := (<c id=\"OK\">"
                + "<b id=\"cool\"/></c>)/descendant-or-self::*/child::b "
                + "return <a>{$node}</a>";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("VirtualNodesetBUG_wiki_4", 1, rs.getSize());
        assertEquals("VirtualNodesetBUG_wiki_4", "<a><b id=\"cool\"/></a>",
                rs.getResource(0).getContent().toString());
    } 
    
    /**
     * Problem in VirtualNodeSet because it computes the wrong level
     *
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    @Test
    public void virtualNodeset_bug_wiki_5() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                + "let $node := (<c id=\"OK\"><b id=\"cool\"/>"
                + "</c>)/descendant-or-self::*/descendant::b return <a>{$node}</a>";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("VirtualNodesetBUG_wiki_5", 1, rs.getSize());
        assertEquals("VirtualNodesetBUG_wiki_5", "<a><b id=\"cool\"/></a>",
                rs.getResource(0).getContent().toString());
    } 
    
    // It seems that the document builder receives events that are irrelevant.
    @Test
    public void documentBuilder_bug_wiki_6() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                + "declare function local:test() {let $results := <dummy/>"
                + "return \"id\" }; "
                + "<wrapper><string id=\"{local:test()}\"/></wrapper>";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("testDocumentBuilderBUG_wiki_6", 1, rs.getSize());
        assertEquals("testDocumentBuilderBUG_wiki_6", "<wrapper><string id=\"id\"/></wrapper>",
                rs.getResource(0).getContent().toString());
    }
    
    @Test
    public void castInPredicate_bug_wiki_7() throws XMLDBException {
        final String xQuery = "let $number := 2, $list := (\"a\", \"b\", \"c\") return $list[xs:int($number * 2) - 1]";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("testCalculationInPredicate_wiki_7", 1, rs.getSize());
        assertEquals("testCalculationInPredicate_wiki_7", "c",
                rs.getResource(0).getContent().toString());
     }
     
     /**
      * Miscomputation of the expression context in where clause when no 
      * wrapper expression is used. Using, e.g. where data($x/@id) eq "id" works !
      */
    @Test
    public void computation_bug_wiki_8() throws XMLDBException {
        final String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                 + "let $a := element node1 { attribute id {'id'}, "
                 + "element node1 { '1'},element node2 { '2'} }"
                 + "for $x in $a where $x/@id eq \"id\" return $x";

        final XQueryService service = getQueryService();
        final ResourceSet rs = service.query(xQuery);

        assertEquals("testComputationBug_wiki_8", 1, rs.getSize());
        assertEquals("testComputationBug_wiki_8", "<node1 id=\"id\"><node1>1</node1><node2>2</node2></node1>",
            rs.getResource(0).getContent().toString());
    }

    @Test
    public void strings() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("strings.xml", strings);

        ResourceSet result = queryResource(service, "strings.xml", "substring(/test/string[1], 1, 5)", 1);
        assertEquals("Hello", result.getResource(0).getContent().toString());

        queryResource(service, "strings.xml", "/test/string[starts-with(string(.), 'Hello')]", 2);

        result = queryResource(service, "strings.xml", "count(/test/item/price)", 1,
                "Query should return an empty set (wrong document)");
        assertEquals("0", result.getResource(0).getContent().toString());
    }

    @Test
    public void quotes() throws XMLDBException {

        final XQueryService service =
                storeXMLStringAndGetQueryService("quotes.xml", quotes);

        queryResource(service, "quotes.xml", "/test[title = '&quot;Hello&quot;']", 1);

        service.declareVariable("content", "&quot;Hello&quot;");
        queryResource(service, "quotes.xml", "/test[title = $content]", 1);
    }

    @Test
    public void booleans() throws XMLDBException {

        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        ResourceSet result = queryResource(service, "numbers.xml", "boolean(1.0)", 1);
        assertEquals("boolean value of 1.0 should be true", "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(0.0)", 1);
        assertEquals("boolean value of 0.0 should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:double(0.0))", 1);
        assertEquals("boolean value of double 0.0 should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:double(1.0))", 1);
        assertEquals("boolean value of double 1.0 should be true", "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:float(1.0))", 1);
        assertEquals("boolean value of float 1.0 should be true", "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:float(0.0))", 1);
        assertEquals("boolean value of float 0.0 should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:integer(0))", 1);
        assertEquals("boolean value of integer 0 should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(xs:integer(1))", 1);
        assertEquals("boolean value of integer 1 should be true", "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "'true' cast as xs:boolean", 1);
        assertEquals("boolean value of 'true' cast to xs:boolean should be true",
                "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "'false' cast as xs:boolean", 1);
        assertEquals("boolean value of 'false' cast to xs:boolean should be false",
                "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean('Hello')", 1);
        assertEquals("boolean value of string 'Hello' should be true", "true", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean('')", 1);
        assertEquals("boolean value of empty string should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(())", 1);
        assertEquals("boolean value of empty sequence should be false", "false", result.getResource(0).getContent().toString());

        result = queryResource(service, "numbers.xml", "boolean(('Hello'))", 1);
        assertEquals("boolean value of sequence with non-empty string should be true",
                "true", result.getResource(0).getContent().toString());

//			result = queryResource(service, "numbers.xml", "boolean((0.0, 0.0))", 1);
//			assertEquals("boolean value of sequence with two elements should be true", "true",
//					result.getResource(0).getContent());

        result = queryResource(service, "numbers.xml", "boolean(//item[@id = '1']/price)", 1);
        assertEquals("boolean value of 5.6 should be true", "true",
                result.getResource(0).getContent().toString());

        String message = "";
        try {
            queryResource(service, "numbers.xml", "boolean(current-time())", 1);
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("FORG0006") > -1);
    }

    @Test
    public void not() throws XMLDBException {

        final XQueryService service =
                storeXMLStringAndGetQueryService("strings.xml", strings);

        queryResource(service, "strings.xml", "/test/string[not(@value)]", 2);

        ResourceSet result = queryResource(service, "strings.xml",	"not(/test/abcd)", 1);
        Resource r = result.getResource(0);
        assertEquals("true", r.getContent().toString());

        result = queryResource(service, "strings.xml",	"not(/test)", 1);
        r = result.getResource(0);
        assertEquals("false", r.getContent().toString());

        result = queryResource(service, "strings.xml", "/test/string[not(@id)]", 3);
        r = result.getResource(0);
        assertEquals("<string>Hello World!</string>", r.getContent().toString());

        // test with non-existing items
        queryResource(service, "strings.xml", "/blah[not(blah)]", 0);
        queryResource(service, "strings.xml", "//*[string][not(@value)]", 1);
        queryResource(service, "strings.xml", "//*[string][not(@blah)]", 1);
        queryResource(service, "strings.xml", "//*[blah][not(@blah)]", 0);
    }

    @Test
    public void logicalOr() throws XMLDBException, IOException, SAXException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("strings.xml", strings);
            
        ResourceSet result = queryResource(service, "strings.xml",	"<test>{() or ()}</test>", 1);
        Resource r = result.getResource(0);
        assertXMLEqual("<test>false</test>", r.getContent().toString());

        result = queryResource(service, "strings.xml",	"() or ()", 1);
        r = result.getResource(0);
        assertEquals("false", r.getContent().toString());
    } 
    
    @Test
    public void logicalAnd() throws XMLDBException, IOException, SAXException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("strings.xml", strings);

        ResourceSet result = queryResource(service, "strings.xml",	"<test>{() and ()}</test>", 1);
        Resource r = result.getResource(0);
        assertXMLEqual("<test>false</test>", r.getContent().toString());

        result = queryResource(service, "strings.xml",	"() and ()", 1);
        r = result.getResource(0);
        assertEquals("false", r.getContent().toString());
    }     
    
    @Test
    public void ids_persistent() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("ids.xml", ids);

        queryResource(service, "ids.xml", "//a/id(@ref)", 1);
        queryResource(service, "ids.xml", "/test/id(//a/@ref)", 1);

        ResourceSet result = queryResource(service, "ids.xml", "//a/id(@ref)/name", 1);
        Resource r = result.getResource(0);
        assertEquals("<name>one</name>", r.getContent().toString());

        result = queryResource(service, "ids.xml", "//d/id(@ref)/name", 1);
        r = result.getResource(0);
        assertEquals("<name>two</name>", r.getContent().toString());

        String update = "update insert <t xml:id=\"id3\">Hello</t> into /test";
        queryResource(service, "ids.xml", update, 0);

        queryResource(service, "ids.xml", "/test/id('id3')", 1);

        update = "update value //t/@xml:id with 'id4'";
        queryResource(service, "ids.xml", update, 0);
        queryResource(service, "ids.xml", "id('id4', /test)", 1);
    }

    @Ignore("Not yet supported in eXist")
    @Test
    public void ids_memtree() throws XMLDBException {
        final XQueryService service = getQueryService();

        ResourceSet result = service.query("document { " + ids_content + " }//a/id(@ref)");
        assertEquals(1, result.getSize());

        result = service.query("document { " + ids_content + " }/test/id(//a/@ref)");
        assertEquals(1, result.getSize());

        result = service.query("document { " + ids_content + " }//a/id(@ref)/name");
        assertEquals(1, result.getSize());
        Resource r = result.getResource(0);
        assertEquals("<name>one</name>", r.getContent().toString());

        result = service.query("document { " + ids_content + " }//d/id(@ref)/name");
        r = result.getResource(0);
        assertEquals("<name>two</name>", r.getContent().toString());
    }
    
    @Test
    public void idsOnEmptyCollection() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection(getBaseUri(), "admin", "");
        final CollectionManagementService service = root.getService(CollectionManagementService.class);
		final Collection emptyCollection = service.createCollection("empty");
        final XQueryService queryService = (XQueryService) emptyCollection.getService(XPathQueryService.class);
 	  	queryAndAssert(queryService, "/*", 0, null);
 	  	queryAndAssert(queryService, "/id('foo')", 0, null);
    }
    
    @Test
    public void idRefs_persistent() throws XMLDBException {
       final XQueryService service =
          storeXMLStringAndGetQueryService("ids.xml", ids);
  
       queryResource(service, "ids.xml", "/idref('id2')", 1);
       queryResource(service, "ids.xml", "/idref('id1')", 2);
       queryResource(service, "ids.xml", "/idref(('id2', 'id1'))", 3);
       queryResource(service, "ids.xml", "<results>{/idref('id2')}</results>", 1);
    }

    @Ignore("Not yet supported in eXist")
    @Test
    public void idRefs_memtree() throws XMLDBException {
        final XQueryService service = getQueryService();

        ResourceSet result = service.query("document {" + ids_content + "}/idref('id2')");
        assertEquals(1, result.getSize());

        result = service.query("document {" + ids_content + "}/idref('id1')");
        assertEquals(2, result.getSize());

        result = service.query("document {" + ids_content + "}/idref(('id2', 'id1'))");
        assertEquals(3, result.getSize());

        result = service.query("let $doc := document {" + ids_content + "} return <results>{$doc/idref('id2')}</results>");
        assertEquals(1, result.getSize());
    }
    
    @Test
    public void externalVars() throws XMLDBException {
        XQueryService service =
            storeXMLStringAndGetQueryService("strings.xml", strings);

        String query =
            "declare variable $x external;" +
            "$x";
        CompiledExpression expr = service.compile(query);
        //Do not declare the variable...
        boolean exceptionThrown = false;
        try {
            service.execute(expr);
        } catch (XMLDBException e) {
            exceptionThrown = true;
        }
        assertTrue("Expected XPTY0002", exceptionThrown);

        query =
            "declare variable $local:string external;" +
            "/test/string[. = $local:string]";
        expr = service.compile(query);
        service.declareVariable("local:string", "Hello");

        ResourceSet result = service.execute(expr);

        final XMLResource r = (XMLResource) result.getResource(0);
        Node node = r.getContentAsDOM();
        if (node.getNodeType() == Node.DOCUMENT_NODE) {
            node = node.getFirstChild();
        }
        assertEquals("string", node.getNodeName());


        //Instanciate a new service to prevent variable reuse
        //TODO : consider auto-reset ?
        service = storeXMLStringAndGetQueryService("strings.xml", strings);

        query =
            "declare variable $local:string as xs:string external;" +
            "$local:string";
        expr = service.compile(query);
        //TODO : we should virtually pass any kind of value
        service.declareVariable("local:string", Integer.valueOf(1));

        String message = "";
        try {
            service.execute(expr);
        } catch (XMLDBException e) {
            //e.printStackTrace();
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);

        service = storeXMLStringAndGetQueryService("strings.xml", strings);

        query =
            "declare variable $x as xs:integer external; " +
            "$x";
        expr = service.compile(query);
        //TODO : we should virtually pass any kind of value
        service.declareVariable("x", "1");

        message = "";
        try {
            service.execute(expr);
        } catch (XMLDBException e) {
            //e.printStackTrace();
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);
    }
    
    @Test
    public void externalVars2() throws ParserConfigurationException, IOException, SAXException, XMLDBException {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        final DocumentBuilder builder = factory.newDocumentBuilder();
        final InputSource source = new InputSource(new StringReader(strings));
        final Document doc = builder.parse(source);

        final XQueryService service = testCollection.getService(XQueryService.class);
        final CompiledExpression expr = service.compile("declare variable $local:node external; $local:node//string");
        service.declareVariable("local:node", doc.getDocumentElement());
        final ResourceSet result = service.execute(expr);
        assertEquals(3, result.getSize());
    }

    @Test
    public void queryResource() throws XMLDBException {
        XMLResource doc = testCollection.createResource("strings.xml", XMLResource.class);
        doc.setContent(strings);
        testCollection.storeResource(doc);
            
        doc = testCollection.createResource("strings2.xml", XMLResource.class);
        doc.setContent(strings);
        testCollection.storeResource(doc);

        final XPathQueryService query =
                testCollection.getService(
                XPathQueryService.class);
        ResourceSet result = query.queryResource("strings2.xml", "/test/string[. = 'Hello World!']");
        assertEquals(1, result.getSize());

        result = query.query("/test/string[. = 'Hello World!']");
        assertEquals(2, result.getSize());
    }
    
    /**
     * test involving ancestor::
     * >>>>>>> currently this produces variable corruption :
     * 			The result is the ancestor <<<<<<<<<<
     */
    @Test
    public void ancestor() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);

        final String query =
                "let $all_items := /test/item " +

                "(: Note: variable non used but computed anyway :)" +
                "let $unused_variable :=" +
                "	for $one_item in $all_items " +
                "			/ ancestor::*	(: <<<<< if you remove this line all is normal :)" +
                "		return 'foo'" +
                "return $all_items";

        final ResourceSet result = service.queryResource("numbers.xml", query );
        assertEquals(4, result.getSize());
    }

    @Test
    public void namespaces() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("namespaces.xml", namespaces);

        service.setNamespace("t", "http://www.foo.com");
        service.setNamespace("c", "http://www.other.com");
        ResourceSet result =
                service.queryResource("namespaces.xml", "//t:section");
        assertEquals(1, result.getSize());

        result =
                service.queryResource("namespaces.xml", "/t:test//c:comment");
        assertEquals(1, result.getSize());

        result = service.queryResource("namespaces.xml", "//c:*");
        assertEquals(1, result.getSize());

        result = service.queryResource("namespaces.xml", "//*:comment");
        assertEquals(1, result.getSize());

        result = service.queryResource("namespaces.xml", "namespace-uri(//t:test)");
        assertEquals(1, result.getSize());
        assertEquals("http://www.foo.com", result.getResource(0).getContent().toString());
    }

    @Test
    public void preserveSpace() throws XMLDBException, IOException, SAXException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("whitespace.xml", ws);

        final ResourceSet result =
                service.queryResource("whitespace.xml", "//text");
        assertEquals(2, result.getSize());

        String item = result.getResource(0).getContent().toString();
        assertXMLEqual("<text> </text>", item);
        item = result.getResource(1).getContent().toString();
        assertXMLEqual("<text xml:space=\"default\"> </text>", item);
    }
    
    @Test
    public void nestedElements() throws XMLDBException {
        final XQueryService service =
                storeXMLStringAndGetQueryService("nested.xml", nested);

        final ResourceSet result = service.queryResource("nested.xml", "//c");
        assertEquals(3, result.getSize());
    }
    
    @Test
    public void staticVariables() throws XMLDBException {
        final XMLResource doc =
                testCollection.createResource(
                "numbers.xml", XMLResource.class );
        doc.setContent(numbers);
        testCollection.storeResource(doc);
        XPathQueryService service =
                testCollection.getService(
                XPathQueryService.class);

        final EXistXPathQueryService service2 = (EXistXPathQueryService) service;
        service2.declareVariable("name", "MONTAGUE");
        service2.declareVariable("name", "43");

        //ResourceSet result = service.query("//SPEECH[SPEAKER=$name]");
        ResourceSet result = service2.query( doc, "//item[stock=$name]");
        result = service2.query("$name");
        assertEquals(1, result.getSize());
        result = service2.query( doc, "//item[stock=43]");
        assertEquals(1, result.getSize());
        result = service2.query(doc, "//item");
        assertEquals(4, result.getSize());

        // assertEquals(10, result.getSize());
    }

    @Test
    public void membersAsResource() throws XMLDBException, IOException {
//			XPathQueryService service =
//				(XPathQueryService) testCollection.getService(
//					"XPathQueryService",
//					"1.0");
//			ResourceSet result = service.query("//SPEECH[LINE &= 'marriage']");
        final XQueryService service =
                storeXMLStringAndGetQueryService("numbers.xml", numbers);
        final ResourceSet result = service.query("//item/price");

        final Resource r = result.getMembersAsResource();
        final Object rawContent = r.getContent();
        String content = null;
        if(rawContent instanceof File) {
            final Path p = ((File) rawContent).toPath();
            content = new String(Files.readAllBytes(p), UTF_8);
        } else {
            content = (String)r.getContent();
        }

        final Pattern p = Pattern.compile(".*(<price>.*){4}", Pattern.DOTALL);
        final Matcher m = p.matcher(content);
        assertTrue("get whole document numbers.xml", m.matches());
    }

    @Test
    public void satisfies() throws XMLDBException {
        final XQueryService service = getQueryService();

        ResourceSet result = queryAndAssert(service,
                "every $foo in (1,2,3) satisfies" +
                "   let $bar := 'baz'" +
                "       return false() ",
                1,  "");
        assertEquals("satisfies + FLWR expression allways false 1", "false", result.getResource(0).getContent().toString());

        result = queryAndAssert(service,
                "declare function local:foo() { false() };" +
                "   every $bar in (1,2,3) satisfies" +
                "   local:foo()",
                1,  "");
        assertEquals("satisfies + FLWR expression allways false 2", "false", result.getResource(0).getContent().toString());

        String query = "every $x in () satisfies false()";
        result = queryAndAssert(service, query, 1,  "");
        assertEquals(query, "true", result.getResource(0).getContent().toString());

        query = "some $x in () satisfies true()";
        result = queryAndAssert(service, query, 1,  "");
        assertEquals(query, "false", result.getResource(0).getContent().toString());
    }
    
    @Test
    public void intersect() throws XMLDBException {
        final XQueryService service = getQueryService();

        String query = "()  intersect ()";
        queryAndAssert(service, query, 0, "");

        query = "()  intersect  (1)";
        queryAndAssert(service, query, 0, "");

        query = "(1)  intersect  ()";
        queryAndAssert(service, query, 0, "");
    }
    
    @Test
    public void union() throws XMLDBException {
        final XQueryService service = getQueryService();

        String query = "()  union ()";
        queryAndAssert( service, query, 0, "");

        String message = "";
        try {
            query = "()  union  (1)";
            queryAndAssert( service, query, 0, "");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);

        message = "";
        try {
            query = "(1)  union  ()";
            queryAndAssert(service, query, 0, "");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);

        query = "<a/>  union ()";
        queryAndAssert(service, query, 1, "");
        query = "()  union <a/>";
        queryAndAssert(service, query, 1, "");
        //Not the same nodes
        query = "<a/> union <a/>";
        queryAndAssert(service, query, 2, "");
    }

    @Test
    public void except() throws XMLDBException {
        final XQueryService service = getQueryService();

        String query = "()  except ()";
        queryAndAssert(service, query, 0, "");

        query = "()  except  (1)";
        queryAndAssert(service, query, 0, "");

        String message = "";
        try {
            query = "(1)  except  ()";
            queryAndAssert(service, query, 0, "");
        } catch (XMLDBException e) {
            message = e.getMessage();
        }
        assertTrue(message.indexOf("XPTY0004") > -1);

        query = "<a/>  except ()";
        queryAndAssert(service, query, 1, "");
        query = "()  except <a/>";
        queryAndAssert(service, query, 0, "");
        //Not the same nodes
        query = "<a/> except <a/>";
        queryAndAssert(service, query, 1, "");
    }

    @Test
    public void convertToBoolean() throws XMLDBException {
        final XQueryService service = getQueryService();
        

        ResourceSet result = queryAndAssert(
                service,
                "let $doc := <element attribute=''/>" + "return ("
                + "  <true>{boolean(($doc,2,3))}</true> ,"
                + "  <true>{boolean(($doc/@*,2,3))}</true> ,"
                + "  <true>{boolean(true())}</true> ,"
                + "  <true>{boolean('test')}</true> ,"
                + "  <true>{boolean(number(1))}</true> ,"
                + "  <false>{boolean((0))}</false> ,"
                + "  <false>{boolean(false())}</false> ,"
                + "  <false>{boolean('')}</false> ,"
                + "  <false>{boolean(number(0))}</false> ,"
                + "  <false>{boolean(number('NaN'))}</false>" + ")",
                10, "");

        for (int i = 0; i < 10; i++) {
            if(i < 5) {
                assertEquals("true " + (i + 1), "<true>true</true>", result
                    .getResource(i).getContent().toString());
            } else {
                assertEquals("false " + (i + 1), "<false>false</false>", result
                    .getResource(i).getContent().toString());
            }
        }
        
        boolean exceptionThrown = false;
        String message = "";
        try {
            queryAndAssert(service,
                    "let $doc := <element attribute=''/>"
                    + " return boolean( (1,2,$doc) )", 1, "");
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue("Exception wanted: " + message, exceptionThrown);
    }

    @Test
    public void compile() throws XMLDBException {
        final String invalidQuery = "for $i in (1 to 10)\n return $b";
        final String validQuery = "for $i in (1 to 10) return $i";
        final String validModule = "module namespace foo=\"urn:foo\";\n" +
                "declare function foo:test() { \"Hello World!\" };";
        final String invalidModule = "module namespace foo=\"urn:foo\";\n" +
                "declare function foo:test() { \"Hello World! };";
        
        final EXistXQueryService service = (EXistXQueryService) getQueryService();
        boolean exceptionOccurred = false;
        try {
            service.compile(invalidQuery);
        } catch (XMLDBException e) {
            assertEquals(((XPathException)e.getCause()).getLine(), 2);
            exceptionOccurred = true;
        }
        assertTrue("Expected an exception", exceptionOccurred);
        
        exceptionOccurred = false;
        try {
            service.compileAndCheck(invalidModule);
        } catch (XPathException e) {
            exceptionOccurred = true;
        }
        assertTrue("Expected an exception", exceptionOccurred);

        service.compile(validQuery);
        service.compile(validModule);
    }
    
    /**
     * Added by Geoff Shuetrim on 15 July 2006 (geoff@galexy.net).
     * This test has been added following identification of a problem running
     * XPath queries that involved the name of elements with the name 'xpointer'.
     * @throws XMLDBException
     */
    @Ignore
    @Test
    public void xpointerElementNameHandling() throws XMLDBException {
        final XQueryService service = storeXMLStringAndGetQueryService(
                "xpointer.xml", xpointerElementName);

        ResourceSet result = service.queryResource("xpointer.xml",
                "/test/.[local-name()='xpointer']");
        assertEquals(1, result.getSize());

        result = service.queryResource("xpointer.xml", "/test/xpointer");
        assertEquals(1, result.getSize());
    }

    @Test
    public void atomization() throws XMLDBException, IOException, SAXException {
        final String query =
                "declare namespace ex = \"http://example.org\";\n" +
                "declare function ex:elementName() as xs:QName {\n" +
                "   QName(\"http://test.org\", \"test:name\")\n" +
                "};\n" +
                "<test>{\n" +
                "   element {QName(\"http://test.org\", \"test:name\") }{},\n" +
                "   element {ex:elementName()} {}\n" +
                "}</test>";

        final EXistXQueryService service = (EXistXQueryService)getQueryService();
        service.setProperty(OutputKeys.INDENT, "no");
        final ResourceSet result = service.query(query);
        assertEquals(1, result.getSize());
        assertXMLEqual("<test><test:name xmlns:test=\"http://test.org\"/><test:name xmlns:test=\"http://test.org\"/></test>", result.getResource(0).getContent().toString());
    }

    @Test
    public void substring() throws XMLDBException {
        final XQueryService service = getQueryService();
        
        // Test cases by MIKA
        final String validQuery = "substring(\"MK-1234\", 4,1)";
        ResourceSet result = queryAndAssert( service, validQuery, 1, validQuery);
        assertEquals("1", result.getResource(0).getContent().toString());

        String invalidQuery = "substring(\"MK-1234\", 4,4)";
        result = queryAndAssert( service, invalidQuery, 1, invalidQuery);
        assertEquals("1234", result.getResource(0).getContent().toString());

        // Test case by Toar
        final String toarQuery="let $num := \"2003.123\" \n return substring($num, 1, 7)";
        result = queryAndAssert( service, toarQuery, 1, toarQuery);
        assertEquals("2003.12", result.getResource(0).getContent().toString());
    }

    @Test
    public void cdataPersistentDom() throws XMLDBException {
        final String docName = "cdata.xml";

        final XQueryService service =
                storeXMLStringAndGetQueryService(docName, cdata_xml);

        String query = "/elem1";
        ResourceSet result = queryResource(service, docName, query, 1);
        final String expected = "<elem1>" + cdata_content.replace("<", "&lt;").replace(">", "&gt;") + "</elem1>";
        assertEquals(expected, result.getResource(0).getContent().toString());

        query =
                "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";\n" +
                "declare option output:cdata-section-elements \"elem1\";\n" +
                "/elem1\n";
        result = queryResource(service, docName, query, 1);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(doc(\"/db/test/" + docName + "\"),\n" +
                    "<output:serialization-parameters xmlns:output = \"http://www.w3.org/2010/xslt-xquery-serialization\">\n" +
                    "    <output:method value=\"xml\"/>\n" +
                    "    <output:cdata-section-elements value=\"elem1\"/>\n" +
                    "</output:serialization-parameters>)";
        result = queryResource(service, docName, query, 1);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(doc(\"/db/test/" + docName + "\"),\n" +
                        "map {\n" +
                        "    \"method\": \"xml\",\n" +
                        "    \"cdata-section-elements\": xs:QName(\"elem1\")\n" +
                        "})";
        result = queryResource(service, docName, query, 1);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());
    }

    @Test
    public void cdataMemtreeDom() throws XMLDBException, IOException {
        final String docName = "cdata.xml";
        final Path tempFile = tempFolder.newFile().toPath();
        Files.write(tempFile, Arrays.asList(cdata_xml));

        final XQueryService service = getQueryService();

        String query = "doc(\"" + tempFile.toUri() + "\")";
        ResourceSet result = queryAndAssert(service, query, 1, null);
        final String expected = "<elem1>" + cdata_content.replace("<", "&lt;").replace(">", "&gt;") + "</elem1>";
        assertEquals(expected, result.getResource(0).getContent().toString());

        query =
                "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";\n" +
                "declare option output:cdata-section-elements \"elem1\";\n" +
                "doc(\"" + tempFile.toUri() + "\")\n";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(doc(\"" + tempFile.toUri() + "\"),\n" +
                        "<output:serialization-parameters xmlns:output = \"http://www.w3.org/2010/xslt-xquery-serialization\">\n" +
                        "    <output:method value=\"xml\"/>\n" +
                        "    <output:cdata-section-elements value=\"elem1\"/>\n" +
                        "</output:serialization-parameters>)";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(doc(\"" + tempFile.toUri() + "\"),\n" +
                        "map {\n" +
                        "    \"method\": \"xml\",\n" +
                        "    \"cdata-section-elements\": xs:QName(\"elem1\")\n" +
                        "})";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());
    }

    @Test
    public void cdataComputedDom() throws XMLDBException {
        final XQueryService service = getQueryService();

        String query =
                "document {\n" +
                    cdata_xml + "\n" +
                "}";
        ResourceSet result = queryAndAssert(service, query, 1, null);
        final String expected = "<elem1>" + cdata_content.replace("<", "&lt;").replace(">", "&gt;") + "</elem1>";
        assertEquals(expected, result.getResource(0).getContent().toString());

        query =
                "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";\n" +
                "declare option output:cdata-section-elements \"elem1\";\n" +
                "document {\n" +
                    cdata_xml + "\n" +
                "}";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(document {" + cdata_xml + "},\n" +
                        "<output:serialization-parameters xmlns:output = \"http://www.w3.org/2010/xslt-xquery-serialization\">\n" +
                        "    <output:method value=\"xml\"/>\n" +
                        "    <output:cdata-section-elements value=\"elem1\"/>\n" +
                        "</output:serialization-parameters>)";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());

        query =
                "fn:serialize(document {" + cdata_xml + "},\n" +
                        "map {\n" +
                        "    \"method\": \"xml\",\n" +
                        "    \"cdata-section-elements\": xs:QName(\"elem1\")\n" +
                        "})";
        result = queryAndAssert(service, query, 1, null);
        assertEquals(cdata_xml, result.getResource(0).getContent().toString());
    }

    /**
     * Helper that performs an XQuery and does JUnit assertion on result size.
     *
     * @see #queryResource(XQueryService, String, String, int, String)
     */
    private ResourceSet queryResource(final XQueryService service, final String resource,
                                      final String query, final int expected) throws XMLDBException {
        return queryResource(service, resource, query, expected, null);
    }

    /**
     * Helper that performs an XQuery and does JUnit assertion on result size.
     *
     * @param service XQuery service
     * @param resource database resource (collection) to query
     * @param query
     * @param expected size of result
     * @param message for JUnit
     *
     * @return a ResourceSet, allowing to do more assertions if necessary.
     * @throws XMLDBException
     */
    private ResourceSet queryResource(final XQueryService service, final String resource,
                                      final String query, final int expected, final String message) throws XMLDBException {
        final ResourceSet result = service.queryResource(resource, query);
        if(message == null) {
            assertEquals(query, expected, result.getSize());
        } else {
            assertEquals(message, expected, result.getSize());
        }
        return result;
    }

    /** For queries without associated data */
    private ResourceSet queryAndAssert(final XQueryService service, final String query,
                                       final int expected, final String message) throws XMLDBException {
        final ResourceSet result = service.query(query);
        if(message == null) {
            assertEquals(expected, result.getSize());
        } else {
            assertEquals(message, expected, result.getSize());
        }
        return result;
    }

    /** For queries without associated data */
    private XQueryService getQueryService() throws XMLDBException {
        final XQueryService service = (XQueryService) testCollection.getService(
            XPathQueryService.class);
        return service;
    }

    /** stores XML String and get Query Service
     * @param documentName to be stored in the DB
     * @param content to be stored in the DB
     * @return the XQuery Service
     * @throws XMLDBException
     */
    private XQueryService storeXMLStringAndGetQueryService(final String documentName,
                                                           final String content) throws XMLDBException {
        final XMLResource doc =
            testCollection.createResource(
                documentName, XMLResource.class );
        doc.setContent(content);
        testCollection.storeResource(doc);
        final XQueryService service =
            (XQueryService) testCollection.getService(
                XPathQueryService.class);
        return service;
    }
}
