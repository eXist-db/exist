package org.exist.xquery;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.jetty.JettyStart;
import org.exist.TestUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import java.io.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

public class XPathQueryTest extends XMLTestCase {
    
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
            "<test>" +
            "   <a> <s>A</s> <n>1</n> </a>" +
            "   <a> <s>Z</s> <n>2</n> </a>" +
            "   <a> <s>B</s> <n>3</n> </a>" +
            "   <a> <s>Z</s> <n>4</n> </a>" +
            "   <a> <s>C</s> <n>5</n> </a>" +
            "   <a> <s>Z</s> <n>6</n> </a>" +
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
            "<test xml:space=\"preserve\">" +
            "<a ref=\"id1\"/>" +
            "<a ref=\"id1\"/>" +
            "<d ref=\"id2\"/>" +
            "<b id=\"id1\"><name>one</name></b>" +
            "<c xml:id=\"     id2     \"><name>two</name></c>" +
            "</test>";
    
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
    
    private static String uri = XmldbURI.LOCAL_DB;

    public static void setURI(String collectionURI) {
        uri = collectionURI;
    }
    
    private static JettyStart server = null;
    
    private Collection testCollection;
    private String query;
    
    @Override
    protected void setUp() throws Exception {
        if (uri.startsWith("xmldb:exist://localhost")) {
            initServer();
        }
        
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        Collection root =
                DatabaseManager.getCollection(
                uri,
                "admin",
                "");
        CollectionManagementService service =
                (CollectionManagementService) root.getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection("test");
        assertNotNull(testCollection);
    }
    
	private void initServer() {
        try {
            if (server == null) {
                server = new JettyStart();
                System.out.println("Starting standalone server...");
                server.run();
            }
        } catch(Exception e) {
            fail(e.getMessage());
        }
    }
    
    @Override
    protected void tearDown() throws Exception {
        try {
            TestUtils.cleanupDB();
            if (!((CollectionImpl) testCollection).isRemoteCollection()) {
                DatabaseInstanceManager dim =
                        (DatabaseInstanceManager) testCollection.getService(
                        "DatabaseInstanceManager", "1.0");
                dim.shutdown();
            }
            testCollection = null;
            
            System.out.println("tearDown PASSED");
            
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testPathExpression() {
        try {
            XQueryService service =
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
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    /** test simple queries involving attributes */
    public void testAttributes() {
        ResourceSet result;
        try {
            String testDocument = "numbers.xml";
            String query;
            
            XQueryService service = storeXMLStringAndGetQueryService(
                    testDocument, numbers);
            
            query = "/test/item[ @id='1' ]";
            result = service.queryResource(testDocument, query);
            System.out.println("testAttributes 1: ========");
            printResult(result);
            assertEquals("XPath: " + query, 1, result.getSize());
            
            XMLResource resource = (XMLResource)result.getResource(0);
            Node node = resource.getContentAsDOM();
            if (node.getNodeType() == Node.DOCUMENT_NODE)
                node = node.getFirstChild();
            assertEquals("XPath: " + query, "item", node.getNodeName());
            
            query = "/test/item [ @type='alphanum' ]";
            result = service.queryResource(testDocument, query);
            System.out.println("testAttributes 2: ========");
            printResult(result);
            assertEquals("XPath: " + query, 1, result.getSize());
            
        } catch (XMLDBException e) {
            System.out.println("testAttributes(): XMLDBException: " + e);
            fail(e.getMessage());
        }
    }
    
    public void testStarAxis() {
        ResourceSet result;
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            
            result = service.queryResource("numbers.xml", "/*/item");
            System.out.println("testStarAxis 1: ========");
            printResult(result);
            assertEquals("XPath: /*/item", 4, result.getSize());
            
            result = service.queryResource("numbers.xml", "/test/*");
            System.out.println("testStarAxis  2: ========");
            printResult(result);
            assertEquals("XPath: /test/*", 4, result.getSize());
            
            result = service.queryResource("numbers.xml", "/test/descendant-or-self::*");
            System.out.println("testStarAxis  3: ========");
            printResult(result);
            assertEquals( "XPath: /test/descendant-or-self::*", 13, result.getSize());
            
            result = service.queryResource("numbers.xml", "/*/*");
            System.out.println("testStarAxis 4: ========" );
            printResult(result);
            //Strange !!! Should be 8
            assertEquals("XPath: /*/*", 4, result.getSize());
            
        } catch (XMLDBException e) {
            System.out.println("testStarAxis(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testStarAxisConstraints() {
        ResourceSet result;
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
            service.setNamespace("t", "http://www.foo.com");
            System.out.println("testStarAxisConstraints : ========" );

            query = "// t:title/text() [ . != 'aaaa' ]";
            result = service.queryResource( "namespaces.xml", query);
            printResult(result);
            assertEquals("XPath: "+query, 1, result.getSize() );
            System.out.println("testStarAxisConstraints : ========" );

            query = "/t:test/*:section[contains(., 'comment')]";
            result = service.queryResource("namespaces.xml", query);
            printResult(result);
            assertEquals("XPath: "+query, 1, result.getSize());
            System.out.println("testStarAxisConstraints : ========" );

            query = "/t:test/t:*[contains(., 'comment')]";
            result = service.queryResource("namespaces.xml", query);
            printResult(result);
            assertEquals("XPath: "+query, 1, result.getSize());
            System.out.println("testStarAxisConstraints : ========" );

            query = "/t:test/t:section[contains(., 'comment')]";
            result = service.queryResource("namespaces.xml", query);
            printResult(result);
            assertEquals("XPath: "+query, 1, result.getSize());
            System.out.println("testStarAxisConstraints : ========" );

            query = "/t:test/t:section/*[contains(., 'comment')]";
            result = service.queryResource("namespaces.xml", query);
            printResult(result); 
            assertEquals("XPath: "+query, 1, result.getSize());
            System.out.println("testStarAxisConstraints : ========" );

            query = "/ * / * [ t:title ]";
            result = service.queryResource( "namespaces.xml", query);
            printResult(result);
            assertEquals("XPath: "+query, 1, result.getSize() );
            System.out.println("testStarAxisConstraints : ========" );
         
            query = "/ t:test / t:section [ t:title ]";
            result = service.queryResource( "namespaces.xml", query);
            printResult(result);
            System.out.println("g) 1 / " +  result.getSize());
            assertEquals("XPath: "+query, 1, result.getSize() );
            System.out.println("testStarAxisConstraints : ========" );

            query = "/ t:test / t:section";
            result = service.queryResource( "namespaces.xml", query);
            printResult(result);
            System.out.println("h) 1 / " +  result.getSize());
            assertEquals("XPath: "+query, 1, result.getSize() );
            System.out.println("testStarAxisConstraints : ========" );        
        } catch (XMLDBException e) {
            System.out.println("testStarAxisConstraints(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testStarAxisConstraints2() {
        ResourceSet result;
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
            service.setNamespace("t", "http://www.foo.com");
            
            query =  "/ * [ ./ * / t:title ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxisConstraints2 : ========" );
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );
            
            query =  "/ * [ * / t:title ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxisConstraints2 : ========" );
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );
            
        } catch (XMLDBException e) {
            //org.xmldb.api.base.XMLDBException: Internal evaluation error: context node is missing for node 3 !
            System.out.println("testStarAxisConstraints2(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testStarAxisConstraints3() {
        ResourceSet result;
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("namespaces.xml", namespaces);
            service.setNamespace("t", "http://www.foo.com");
            
            query =  "// * [ . = 'Test Document' ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxisConstraints3 : ========" );
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );
            
        } catch (XMLDBException e) {
            //org.xmldb.api.base.XMLDBException: Internal evaluation error: context node is missing !
            System.out.println("testStarAxisConstraints3(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testRoot() {
        try {
            storeXMLStringAndGetQueryService("nested2.xml", nested2);
            XQueryService service = storeXMLStringAndGetQueryService("numbers.xml", numbers);
            String query = "let $doc := <a><b/></a> return root($doc)";
            ResourceSet result = service.queryResource("numbers.xml", query);
            assertEquals("XPath: " + query, 1, result.getSize());
            XMLResource resource = (XMLResource)result.getResource(0);
            Node node = resource.getContentAsDOM();
            //Oh dear ! Don't tell me that *I* have written this :'( -pb
            if (node.getNodeType() == Node.DOCUMENT_NODE)
                node = node.getFirstChild();
            assertEquals("XPath: " + query, "a", node.getLocalName());
            
            query = "let $c := (<a/>,<b/>,<c/>,<d/>,<e/>) return count($c/root())";
            result = service.queryResource("numbers.xml", query);
            assertEquals( "5", result.getResource(0).getContent() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testName() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("nested2.xml", nested2);
            
            String query = "(<a/>,<b/>)/name()";
            ResourceSet result = service.queryResource("nested2.xml", query);
            assertEquals("XPath: " + query, 2, result.getSize());
            assertEquals( "a", result.getResource(0).getContent() );
            assertEquals( "b", result.getResource(1).getContent() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testParentAxis() {
        try {
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
            assertEquals(result.getResource(0).getContent().toString(), "3");
            result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::*:item/string(@id)", 1);
            assertEquals(result.getResource(0).getContent().toString(), "3");
            result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/../string(@id)", 1);
            assertEquals(result.getResource(0).getContent().toString(), "3");
            result = queryResource(service, "numbers2.xml", "//n:price[. = 18.4]/parent::n:item/string(@id)", 1);
            assertEquals(result.getResource(0).getContent().toString(), "3");
            queryResource(service, "numbers2.xml",
                    "for $price in //n:price where $price/parent::*[@id = '3']/n:stock = '5' return $price", 1);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testParentSelfAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("nested2.xml", nested2);
            storeXMLStringAndGetQueryService("numbers.xml", numbers);
            queryResource(service, "nested2.xml", "/RootElement/descendant::*/parent::ChildA", 1);
            queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildB]/parent::RootElement", 0);
            queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildA]/parent::RootElement", 1);
            queryResource(service, "nested2.xml", "let $a := ('', 'b', '', '') for $b in $a[.] return <blah>{$b}</blah>", 1);
            
            String query = "let $doc := <root><page><a>a</a><b>b</b></page></root>" +
                    "return " +
                    "for $element in $doc/page/* " +
                    "return " +
                    "if($element[self::a] or $element[self::b]) then (<found/>) else (<notfound/>)";
            ResourceSet result = service.queryResource("numbers.xml", query);
            assertEquals(2, result.getSize());
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testSelfAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("self.xml", self);
            
            queryResource(service, "self.xml", "/test-self/*[not(self::a)]", 1);
            queryResource(service, "self.xml", "/test-self/*[self::a]", 1);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testAncestorAxis() {
        try {
            XQueryService service =
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
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testAncestorIndex() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("nested2.xml", nested2);
            
            queryResource(service, "nested2.xml", "//ChildB/ancestor::*[1]/self::ChildA", 1);
            queryResource(service, "nested2.xml", "//ChildB/ancestor::*[2]/self::RootElement", 1);
            queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 1]/self::ChildA", 1);
            queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 2]/self::RootElement", 1);
            queryResource(service, "nested2.xml", "//ChildB/ancestor::*[position() = 2]/self::RootElement", 1);
            queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/ancestor::*", 0);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testPrecedingSiblingAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("siblings.xml", siblings);
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");
            ResourceSet result = queryResource(service, "siblings.xml", "//a[preceding-sibling::*[1]/s = 'B']", 1);
            assertXMLEqual("<a><s>Z</s><n>4</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[preceding-sibling::a[1]/s = 'B']", 1);
            assertXMLEqual("<a><s>Z</s><n>4</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[preceding-sibling::*[2]/s = 'B']", 1);
            assertXMLEqual("<a><s>C</s><n>5</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[preceding-sibling::a[2]/s = 'B']", 1);
            assertXMLEqual("<a><s>C</s><n>5</n></a>", result.getResource(0).getContent().toString());
            
            queryResource(service, "siblings.xml", "(<a/>, <b/>, <c/>)/following-sibling::*", 0);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testFollowingSiblingAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("siblings.xml", siblings);
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");
            ResourceSet result = queryResource(service, "siblings.xml", "//a[following-sibling::*[1]/s = 'B']", 1);
            assertXMLEqual("<a><s>Z</s><n>2</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[following-sibling::a[1]/s = 'B']", 1);
            assertXMLEqual("<a><s>Z</s><n>2</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[following-sibling::*[2]/s = 'B']", 1);
            assertXMLEqual("<a><s>A</s><n>1</n></a>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a[following-sibling::a[2]/s = 'B']", 1);
            assertXMLEqual("<a><s>A</s><n>1</n></a>", result.getResource(0).getContent().toString());
            
            queryResource(service, "siblings.xml", "(<a/>, <b/>, <c/>)/following-sibling::*", 0);
            
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");	    
    	    ResourceSet rs = service.query("let $doc := <doc><div id='1'><div id='2'/></div><div id='3'/></doc> " +
    	    		"return $doc/div[1]/following-sibling::div");
    	    assertEquals(1, rs.getSize());
    	    assertXMLEqual("<div id='3'/>", rs.getResource(0).getContent().toString());  
    	    
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");	    
    	    rs = service.query("let $doc := <doc><div id='1'/><div id='2'><div id='3'/></div><div id='4'/><div id='5'><div id='6'/></div></doc> " +
    	    		"return $doc/div/preceding-sibling::div");
    	    assertEquals(3, rs.getSize());
    	    assertXMLEqual("<div id='1'/>", rs.getResource(0).getContent().toString());      	    
    	    assertXMLEqual("<div id='2'><div id='3'/></div>", rs.getResource(1).getContent().toString());      	    
    	    assertXMLEqual("<div id='4'/>", rs.getResource(2).getContent().toString());      	    
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testFollowingAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("siblings.xml", siblings);
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");
            queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s", 3);
            queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::n", 4);
            ResourceSet result = queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s[1]", 1);
            assertXMLEqual("<s>Z</s>", result.getResource(0).getContent().toString());
            result = queryResource(service, "siblings.xml", "//a/s[. = 'B']/following::s[2]", 1);
            assertXMLEqual("<s>C</s>", result.getResource(0).getContent().toString());
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testPrecedingAxis() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("siblings.xml", siblings);
            service.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            service.setProperty(OutputKeys.INDENT, "no");
            queryResource(service, "siblings.xml", "//a/s[. = 'B']/preceding::s", 2);
            queryResource(service, "siblings.xml", "//a/s[. = 'C']/preceding::s", 4);
            queryResource(service, "siblings.xml", "//a/n[. = '3']/preceding::s", 3);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testPosition() {
        try {
            XQueryService service =
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
            
            
            query = "declare variable $doc { <root>" +
                    "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                    "</root> }; " +
                    "(for $x in $doc/a return $x)[position() mod 3 = 2]";
            result = service.queryResource("numbers.xml", query);
            assertEquals("XPath: " + query, 2, result.getSize());
            
            query = "declare variable $doc { <root>" +
                    "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                    "</root> }; " +
                    "for $x in $doc/a return $x[position() mod 3 = 2]";
            result = service.queryResource("numbers.xml", query);
            assertEquals("XPath: " + query, 0, result.getSize());
            
            query = "declare variable $doc { <root>" +
                    "<a>1</a><a>2</a><a>3</a><a>4</a><a>5</a><a>6</a><a>7</a>" +
                    "</root> }; " +
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
            
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testLast() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            
            String query = "<a><b>test1</b><b>test2</b></a>/b/last()";
            ResourceSet  result = service.queryResource("numbers.xml", query);
            assertEquals("XPath: " + query, 2, result.getSize());
            XMLResource resource = (XMLResource)result.getResource(0);
            assertEquals("XPath: " + query, "2", resource.getContent().toString());
            resource = (XMLResource)result.getResource(1);
            assertEquals("XPath: " + query, "2", resource.getContent().toString());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testNumbers() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            
            ResourceSet result = queryResource(service, "numbers.xml", "sum(/test/item/price)", 1);
            assertEquals( "96.94", result.getResource(0).getContent() );
            
            result = queryResource(service, "numbers.xml", "round(sum(/test/item/price))", 1);
            assertEquals( "97", result.getResource(0).getContent() );
            
            result = queryResource(service, "numbers.xml", "floor(sum(/test/item/stock))", 1);
            assertEquals( "86", result.getResource(0).getContent());
            
            queryResource(service, "numbers.xml", "/test/item[round(price + 3) > 60]", 1);
            
            result = queryResource(service, "numbers.xml", "min(( 123456789123456789123456789, " +
                    "123456789123456789123456789123456789123456789 ))", 1);
            assertEquals("minimum of big integers",
                    "123456789123456789123456789",
                    result.getResource(0).getContent() );
            
            @SuppressWarnings("unused")
			boolean exceptionThrown = false;
            String message = "";
            try {
                result = queryResource(service, "numbers.xml", "empty(() + (1, 2))", 1);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPTY0004") > -1);
            
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testDates() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            
            String query = "xs:untypedAtomic(\"--12-05:00\") cast as xs:gMonth";
            ResourceSet  result = service.queryResource("numbers.xml", query);
            XMLResource resource = (XMLResource)result.getResource(0);
            assertEquals("XPath: " + query, "--12-05:00", resource.getContent().toString());
            
            query = "(xs:dateTime(\"0001-01-01T01:01:01Z\") + xs:yearMonthDuration(\"-P20Y07M\"))";
            result = service.queryResource("numbers.xml", query);
            resource = (XMLResource)result.getResource(0);
            assertEquals("XPath: " + query, "-0021-06-01T01:01:01Z", resource.getContent().toString());            
            
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }    
    
    public void testGeneralComparison() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("dates.xml", date);
            
            queryResource(service, "dates.xml", "/timestamp[@date = xs:date('2006-04-29+02:00')]", 1);
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testPredicates() throws Exception {
        String numbers =
                "<test>"
                + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
                + "<item id='2'><price>7.4</price><stock>43</stock></item>"
                + "<item id='3'><price>18.4</price><stock>5</stock></item>"
                + "<item id='4'><price>65.54</price><stock>16</stock></item>"
                + "</test>";
        try {
            XQueryService service =
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
            assertEquals(result.getResource(0).getContent().toString(), "pass");
            result = queryResource(service, "numbers.xml", "let $credentials := ('test', 'pass') let $user := $credentials[1] return $user", 1);
            assertEquals(result.getResource(0).getContent().toString(), "test");
            result = queryResource(service, "numbers.xml", "let $credentials := ('test', 'pass') let $user := $credentials[2] return $user", 1);
            assertEquals(result.getResource(0).getContent().toString(), "pass");
            
            result = queryResource(service, "numbers.xml", "let $els := <els><el>text1</el><el>text2</el></els> return $els/el[xs:string(.) eq 'text1'] ", 1);
            assertEquals(result.getResource(0).getContent().toString(), "<el>text1</el>");
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testPredicates2() throws Exception {
        String numbers =
                "<test>"
                + "<item id='1' type='alphanum'><price>5.6</price><stock>22</stock></item>"
                + "<item id='2'><price>7.4</price><stock>43</stock></item>"
                + "<item id='3'><price>18.4</price><stock>5</stock></item>"
                + "<item id='4'><price>65.54</price><stock>16</stock></item>"
                + "</test>";
        try {
            XQueryService service =
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

        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    
    // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1460610&group_id=17691&atid=117691
    public void testPredicatesBUG1460610() throws Exception {
        String xQuery = "(1, 2, 3)[ . lt 3]";
        
        XQueryService service = getQueryService();
        ResourceSet rs = service.query(xQuery);
        
        assertEquals("SFBUG 1460610 nr of results", 2, rs.getSize());
        assertEquals("SFBUG 1460610 1st result", "1",
                rs.getResource(0).getContent());
        assertEquals("SFBUG 1460610 2nd result", "2",
                rs.getResource(1).getContent());
    }
    
    // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1537355&group_id=17691&atid=117691
    public void testPredicatesBUG1537355() throws Exception {
        String xQuery = "let $one := 1 return (1, 2, 3)[$one + 1]";
        
        XQueryService service = getQueryService();
        ResourceSet rs = service.query(xQuery);
        
        assertEquals("SFBUG 1537355 nr of results", 1, rs.getSize());
        assertEquals("SFBUG 1537355 result", "2",
                rs.getResource(0).getContent());
    }

    //@see http://sourceforge.net/tracker/index.php?func=detail&aid=1533053&group_id=17691&atid=117691
    public void testNestedPredicates() throws Exception {
        String xQuery = "let $doc := <objects>" +
    	"<detail><class/><source><dynamic>false</dynamic></source></detail>" + 
    	"<detail><class/><source><dynamic>true</dynamic></source></detail>" +
    	"</objects> " +
    	"let $matches := $doc/detail[source[dynamic='false'] or class] " +
    	"return count($matches) eq 2";
    
	    XQueryService service = getQueryService();
	    ResourceSet rs = service.query(xQuery);
	    
	    assertEquals(1, rs.getSize());
	    assertEquals("true", rs.getResource(0).getContent());

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

    
    // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1488303&group_id=17691&atid=117691
    public void testPredicateBUG1488303() throws Exception {
        XQueryService service = getQueryService();
        ResourceSet rs=null;
        
        // test one
        String xQuery1 = "let $q := <q><t>eXist</t></q> return $q//t";
        rs = service.query(xQuery1);
        assertEquals("nr of results", 1, rs.getSize());
        assertEquals("result", "<t>eXist</t>",
                rs.getResource(0).getContent());
        
        // test two
        String xQuery2 = "let $q := <q><t>eXist</t></q> return ($q//t)[1]";
        rs = service.query(xQuery2);
        assertEquals("nr of results", 1, rs.getSize());
        assertEquals("result", "<t>eXist</t>",
                rs.getResource(0).getContent());
        
        // This one fails http://sourceforge.net/tracker/index.php?func=detail&aid=1488303&group_id=17691&atid=117691
        String xQuery3 = "let $q := <q><t>eXist</t></q> return $q//t[1]";
        rs = service.query(xQuery3);
        assertEquals("SFBUG 1488303 nr of results", 1, rs.getSize());
        assertEquals("SFBUG 1488303 result", "<t>eXist</t>",
                rs.getResource(0).getContent());
    }

    
    // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1460791&group_id=17691&atid=117691
    public void bugtestDescendantOrSelfBUG1460791() throws Exception {
        String xQuery = "declare option exist:serialize \"method=xml indent=no\"; let $test:=<z><a>aaa</a><z>zzz</z></z> "
                +"return ( <one>{$test//z}</one>, <two>{$test/descendant-or-self::node()/child::z}</two> )";
        
        XQueryService service = getQueryService();
        ResourceSet rs = service.query(xQuery);
        
        System.out.println("BUG1460791/1"+rs.getResource(0).getContent().toString() );
        System.out.println("BUG1460791/2"+rs.getResource(1).getContent().toString() );
        
        assertEquals("SFBUG 1460791 nr of results", 2, rs.getSize());
        
        assertEquals("SFBUG 1460791 result part 1", "<one><z>zzz</z></one>",
                rs.getResource(0).getContent().toString());
        
        assertEquals("SFBUG 1460791 result part 2", "<two><z>zzz</z></two>",
                rs.getResource(1).getContent().toString());
    }
    
    // @see http://sourceforge.net/tracker/index.php?func=detail&aid=1462120&group_id=17691&atid=117691
    public void bugtestXpathBUG1462120() throws Exception {
        String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                +"let $m:=<Units><Unit name=\"g\" size=\"1\"/>"
                +"<Unit name=\"kg\" size=\"1000\"/></Units> "
                +"let $list:=(<Product aaa=\"g\"/>, <Product aaa=\"kg\"/>) "
                +"let $one:=$list[1] return ( "
                +"$m/Unit[string(data(@name)) eq string(data($list[1]/@aaa))],"
                +"<br/>,$m/Unit[string(data(@name)) eq string(data($one/@aaa))] )";
        
        XQueryService service = getQueryService();
        ResourceSet rs = service.query(xQuery);
        
//        System.out.println("BUG1462120/1"+rs.getResource(0).getContent().toString() );
//        System.out.println("BUG1462120/2"+rs.getResource(1).getContent().toString() );
//        System.out.println("BUG1462120/3"+rs.getResource(2).getContent().toString() );
        
        assertEquals("SFBUG 1462120 nr of results", 3, rs.getSize());
        
        assertEquals("SFBUG 1462120 result part 1", "<Unit name=\"g\" size=\"1\"/>",
                rs.getResource(0).getContent().toString());
        
        assertEquals("SFBUG 1462120 result part 2", "<br/>",
                rs.getResource(1).getContent().toString());
        
        assertEquals("SFBUG 1462120 result part 3", "<Unit name=\"g\" size=\"1\"/>",
                rs.getResource(1).getContent().toString());
    }
    
    
    /*
     * In Predicate.java, the contextSet and the outerSequence.toNodeSet()
     * documents are different so that no match can occur.
     *
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    public void testPredicateBUG_wiki_1() throws Exception {
        String xQuery = "let $dum := <dummy><el>1</el><el>2</el></dummy> return $dum/el[2]";
        
        XQueryService service = getQueryService();
        ResourceSet rs = service.query(xQuery);
        
        assertEquals("Predicate bug wiki_1", 1, rs.getSize());
        assertEquals("Predicate bug wiki_1", "<el>2</el>",
                rs.getResource(0).getContent());
    }

    public void testPredicateBUGAndrzej() throws Exception {
        String xQuery =
            "doc('/db/test/predicates.xml')//elem1/elem2[ string-length( ./elem3 ) > 0][1]/elem3/text()";
        XQueryService service =
            storeXMLStringAndGetQueryService("predicates.xml", predicates);
        ResourceSet rs = service.query(xQuery);
        assertEquals("testPredicateBUGAndrzej", 1, rs.getSize());
        assertEquals("testPredicateBUGAndrzej", "val1", rs.getResource(0).getContent());
    }

    /*
     * removing Self: makes the query work OK
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    
    public void testCardinalitySelfBUG_wiki_2()  {
        String xQuery = "let $test := <test><works><employee>a</employee><employee>b</employee></works></test> "
                +"for $h in $test/works/employee[2] return fn:name($h/self::employee)";
        
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("CardinalitySelfBUG bug wiki_2", 1, rs.getSize());
            assertEquals("CardinalitySelfBUG bug wiki_2", "employee",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
        
    }
    
    /*
     * Problem in VirtualNodeSet which return 2 attributes because it 
     * computes every level
     * @see http://wiki.exist-db.org/space/XQueryBugs
     */
    
    public void testVirtualNodesetBUG_wiki_3()  {
        String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                +"let $node := (<c id=\"OK\"><b id=\"cool\"/></c>)"
                +"/descendant::*/attribute::id return <a>{$node}</a>";
        
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("VirtualNodesetBUG_wiki_3", 1, rs.getSize());
            assertEquals("VirtualNodesetBUG_wiki_3", "<a id=\"cool\"/>",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
        
    } 
    
    /*
     * Problem in VirtualNodeSet because it computes the wrong level
     *
     *@see http://wiki.exist-db.org/space/XQueryBugs
     */
    public void testVirtualNodesetBUG_wiki_4()  {
        String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                +"let $node := (<c id=\"OK\">"
                +"<b id=\"cool\"/></c>)/descendant-or-self::*/child::b "
                +"return <a>{$node}</a>";
        
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("VirtualNodesetBUG_wiki_4", 1, rs.getSize());
            assertEquals("VirtualNodesetBUG_wiki_4", "<a><b id=\"cool\"/></a>",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
        
    } 
    
    /*
     * Problem in VirtualNodeSet because it computes the wrong level
     *
     *@see http://wiki.exist-db.org/space/XQueryBugs
     */
    public void testVirtualNodesetBUG_wiki_5()  {
        String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                +"let $node := (<c id=\"OK\"><b id=\"cool\"/>"
                +"</c>)/descendant-or-self::*/descendant::b return <a>{$node}</a>";
        
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("VirtualNodesetBUG_wiki_5", 1, rs.getSize());
            assertEquals("VirtualNodesetBUG_wiki_5", "<a><b id=\"cool\"/></a>",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
        
    } 
    
    // It seems that the document builder receives events that are irrelevant.
    public void testDocumentBuilderBUG_wiki_6()  {
            String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                    +"declare function local:test() {let $results := <dummy/>"
                    +"return \"id\" }; "
                    +"<wrapper><string id=\"{local:test()}\"/></wrapper>";
            
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("testDocumentBuilderBUG_wiki_6", 1, rs.getSize());
            assertEquals("testDocumentBuilderBUG_wiki_6", "<wrapper><string id=\"id\"/></wrapper>",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
            
    }
    
     public void testCastInPredicate_wiki_7()  {
         String xQuery = "let $number := 2, $list := (\"a\", \"b\", \"c\") return $list[xs:int($number * 2) - 1]";
         
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("testCalculationInPredicate_wiki_7", 1, rs.getSize());
            assertEquals("testCalculationInPredicate_wiki_7", "c",
                    rs.getResource(0).getContent());
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
         
     }
     
     /*
      * Miscomputation of the expression context in where clause when no 
      * wrapper expression is used. Using, e.g. where data($x/@id) eq "id" works !
      */
     public void testComputationBug_wiki_8()  {
         String xQuery = "declare option exist:serialize \"method=xml indent=no\"; "
                 +"let $a := element node1 { attribute id {'id'}, "
                 +"element node1 { '1'},element node2 { '2'} }"
                 +"for $x in $a where $x/@id eq \"id\" return $x";
         
        try {
            XQueryService service = getQueryService();
            ResourceSet rs = service.query(xQuery);
            
            assertEquals("testComputationBug_wiki_8", 1, rs.getSize());
            assertEquals("testComputationBug_wiki_8", "<node1 id=\"id\"><node1>1</node1><node2>2</node2></node1>",
                    rs.getResource(0).getContent());
 
        } catch (XMLDBException ex) {
            fail( "xquery returned exception: " +ex.toString() );
        }
         
     }
     
     

    
    
    public void testStrings() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("strings.xml", strings);
            
            ResourceSet result = queryResource(service, "strings.xml", "substring(/test/string[1], 1, 5)", 1);
            assertEquals( "Hello", result.getResource(0).getContent() );
            
            queryResource(service, "strings.xml", "/test/string[starts-with(string(.), 'Hello')]", 2);
            
            result = queryResource(service, "strings.xml", "count(/test/item/price)", 1,
                    "Query should return an empty set (wrong document)");
            assertEquals("0", result.getResource(0).getContent());
        } catch (XMLDBException e) {
            System.out.println("testStrings(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testQuotes() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("quotes.xml", quotes);
            
            // ResourceSet result =
            queryResource(service, "quotes.xml", "/test[title = '&quot;Hello&quot;']", 1);
            
            service.declareVariable("content", "&quot;Hello&quot;");
            // result =
            queryResource(service, "quotes.xml", "/test[title = $content]", 1);
        } catch (XMLDBException e) {
            System.out.println("testQuotes(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testBoolean() {
        try {
            System.out.println("Testing effective boolean value of expressions ...");
            
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            
            ResourceSet result = queryResource(service, "numbers.xml", "boolean(1.0)", 1);
            assertEquals("boolean value of 1.0 should be true", "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(0.0)", 1);
            assertEquals("boolean value of 0.0 should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:double(0.0))", 1);
            assertEquals("boolean value of double 0.0 should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:double(1.0))", 1);
            assertEquals("boolean value of double 1.0 should be true", "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:float(1.0))", 1);
            assertEquals("boolean value of float 1.0 should be true", "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:float(0.0))", 1);
            assertEquals("boolean value of float 0.0 should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:integer(0))", 1);
            assertEquals("boolean value of integer 0 should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(xs:integer(1))", 1);
            assertEquals("boolean value of integer 1 should be true", "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "'true' cast as xs:boolean", 1);
            assertEquals("boolean value of 'true' cast to xs:boolean should be true",
                    "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "'false' cast as xs:boolean", 1);
            assertEquals("boolean value of 'false' cast to xs:boolean should be false",
                    "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean('Hello')", 1);
            assertEquals("boolean value of string 'Hello' should be true", "true", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean('')", 1);
            assertEquals("boolean value of empty string should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(())", 1);
            assertEquals("boolean value of empty sequence should be false", "false", result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(('Hello'))", 1);
            assertEquals("boolean value of sequence with non-empty string should be true",
                    "true", result.getResource(0).getContent());
            
//			result = queryResource(service, "numbers.xml", "boolean((0.0, 0.0))", 1);
//			assertEquals("boolean value of sequence with two elements should be true", "true",
//					result.getResource(0).getContent());
            
            result = queryResource(service, "numbers.xml", "boolean(//item[@id = '1']/price)", 1);
            assertEquals("boolean value of 5.6 should be true", "true",
                    result.getResource(0).getContent());
            
            @SuppressWarnings("unused")
			boolean exceptionThrown = false;
            String message = "";
            try {
                result = queryResource(service, "numbers.xml", "boolean(current-time())", 1);
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(message.indexOf("FORG0006") > -1);
            
        } catch (XMLDBException e) {
            System.out.println("testBoolean(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void testNot() {
        try {
            XQueryService service =
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
            queryResource(	service, "strings.xml", "xmldb:document()/blah[not(blah)]", 0);
            queryResource(service, "strings.xml", "//*[string][not(@value)]", 1);
            queryResource(service, "strings.xml", "//*[string][not(@blah)]", 1);
            queryResource(service, "strings.xml", "//*[blah][not(@blah)]", 0);
        } catch (XMLDBException e) {
            System.out.println("testStrings(): XMLDBException: "+e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testLogicalOr() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("strings.xml", strings);
            
            ResourceSet result = queryResource(service, "strings.xml",	"<test>{() or ()}</test>", 1);
            Resource r = result.getResource(0);
            assertXMLEqual("<test>false</test>", r.getContent().toString()); 

            result = queryResource(service, "strings.xml",	"() or ()", 1);
            r = result.getResource(0);
            assertEquals("false", r.getContent().toString());                  
            
        } catch (Exception e) {
            System.out.println("testStrings(): "+e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    } 
    
    public void testLogicalAnd() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("strings.xml", strings);
            
            ResourceSet result = queryResource(service, "strings.xml",	"<test>{() and ()}</test>", 1);
            Resource r = result.getResource(0);
            assertXMLEqual("<test>false</test>", r.getContent().toString());  
            
            result = queryResource(service, "strings.xml",	"() and ()", 1);
            r = result.getResource(0);
            assertEquals("false", r.getContent().toString());             
            
        } catch (Exception e) {
            System.out.println("testStrings(): "+e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }     
    
    public void testIds() {
        try {
            XQueryService service =
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
        } catch (XMLDBException e) {
            System.out.println("testIds(): XMLDBException: "+e);
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testIdsOnEmptyCollection() throws XMLDBException {
      Collection root = DatabaseManager.getCollection(uri, "admin", null);
		CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
		Collection emptyCollection = service.createCollection("empty");
      XQueryService queryService = (XQueryService) emptyCollection.getService("XPathQueryService", "1.0");
 	  	queryAndAssert(queryService, "/*", 0, null);
 	  	queryAndAssert(queryService, "/id('foo')", 0, null);
    }
    
    public void testIdRefs() throws XMLDBException {
       XQueryService service =
          storeXMLStringAndGetQueryService("ids.xml", ids);
  
       queryResource(service, "ids.xml", "/idref('id2')", 1);
       queryResource(service, "ids.xml", "/idref('id1')", 2);
       queryResource(service, "ids.xml", "/idref(('id2', 'id1'))", 3);
       queryResource(service, "ids.xml", "<results>{/idref('id2')}</results>", 1);
    }
    
    public void testExternalVars() {
        try {
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
            
            XMLResource r = (XMLResource) result.getResource(0);
            Node node = r.getContentAsDOM();
            if (node.getNodeType() == Node.DOCUMENT_NODE)
                node = node.getFirstChild();
            assertEquals("string", node.getNodeName());
            
            
            //Instanciate a new service to prevent variable reuse
            //TODO : consider auto-reset ?
            service = storeXMLStringAndGetQueryService("strings.xml", strings);
            
         	query =
                "declare variable $local:string as xs:string external;" +
                "$local:string";
         	expr = service.compile(query);
         	//TODO : we should virtually pass any kind of value
         	service.declareVariable("local:string", new Integer(1));
        
         	exceptionThrown = false;
         	String message = "";
            try {
            	service.execute(expr);
            	System.out.println(query);
            } catch (XMLDBException e) {
            	e.printStackTrace();
            	exceptionThrown = true;
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
        
         	exceptionThrown = false;
         	message = "";
            try {
            	System.out.println(query);
            	service.execute(expr);
            } catch (XMLDBException e) {
            	e.printStackTrace();
            	exceptionThrown = true;
                message = e.getMessage();                
            }
            assertTrue(message.indexOf("XPTY0004") > -1);
            
        } catch (Exception e) {
            System.out.println("testExternalVars(): ");
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void bugtestExternalVars2() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputSource source = new InputSource(new StringReader(strings));
            Document doc = builder.parse(source);
            
            XQueryService service = (XQueryService) testCollection.getService("XQueryService", "1.0");
            CompiledExpression expr = service.compile("declare variable $local:node external; $local:node//string");
            service.declareVariable("local:node", doc.getDocumentElement());
            ResourceSet result = service.execute(expr);
            assertEquals(result.getSize(), 3);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
    
    public void testQueryResource() {
        try {
            XMLResource doc =
                    (XMLResource) testCollection.createResource(
                    "strings.xml", "XMLResource" );
            doc.setContent(strings);
            testCollection.storeResource(doc);
            
            doc =
                    (XMLResource) testCollection.createResource(
                    "strings2.xml", "XMLResource" );
            doc.setContent(strings);
            testCollection.storeResource(doc);
            
            XPathQueryService query =
                    (XPathQueryService) testCollection.getService(
                    "XPathQueryService",
                    "1.0");
            ResourceSet result = query.queryResource("strings2.xml", "/test/string[. = 'Hello World!']");
            assertEquals(1, result.getSize());
            
            result = query.query("/test/string[. = 'Hello World!']");
            assertEquals(2, result.getSize());
            
        } catch (XMLDBException e) {
            System.out.println("testQueryResource(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    /** test involving ancestor::
     * >>>>>>> currently this produces variable corruption :
     * 			The result is the ancestor <<<<<<<<<< */
    public void testAncestor() {
        ResourceSet result;
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers );
            
            query =
                    "let $all_items := /test/item " +
                    
                    "(: Note: variable non used but computed anyway :)" +
                    "let $unused_variable :=" +
                    "	for $one_item in $all_items " +
                    "			/ ancestor::*	(: <<<<< if you remove this line all is normal :)" +
                    "		return 'foo'" +
                    "return $all_items";
            
            result = service.queryResource("numbers.xml", query );
            assertEquals(4, result.getSize());
            
        } catch (XMLDBException e) {
            System.out.println("testAncestor(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    /** Helper that performs an XQuery and does JUnit assertion on result size.
     * @see #queryResource(XQueryService, String, String, int, String)
     */
    private ResourceSet queryResource(XQueryService service, String resource,
            String query, int expected) throws XMLDBException {
        return queryResource(service, resource, query, expected, null);
    }
    
    /** Helper that performs an XQuery and does JUnit assertion on result size.
     * @param service XQuery service
     * @param resource database resource (collection) to query
     * @param query
     * @param expected size of result
     * @param message for JUnit
     * @return a ResourceSet, allowing to do more assertions if necessary.
     * @throws XMLDBException
     */
    private ResourceSet queryResource(XQueryService service, String resource,
            String query, int expected, String message) throws XMLDBException {
        ResourceSet result = service.queryResource(resource, query);
        if(message == null)
            assertEquals(query, expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }
    
    /** For queries without associated data */
    private ResourceSet queryAndAssert(XQueryService service, String query,
            int expected, String message) throws XMLDBException {
        ResourceSet result = service.query(query);
        if(message == null)
            assertEquals(expected, result.getSize());
        else
            assertEquals(message, expected, result.getSize());
        return result;
    }
    
    /** For queries without associated data */
    private XQueryService getQueryService() throws XMLDBException {
        XQueryService service = (XQueryService) testCollection.getService(
                "XPathQueryService", "1.0");
        return service;
    }
    
    /** stores XML String and get Query Service
     * @param documentName to be stored in the DB
     * @param content to be stored in the DB
     * @return the XQuery Service
     * @throws XMLDBException
     */
    private XQueryService storeXMLStringAndGetQueryService(String documentName,
            String content) throws XMLDBException {
        XMLResource doc =
                (XMLResource) testCollection.createResource(
                documentName, "XMLResource" );
        doc.setContent(content);
        testCollection.storeResource(doc);
        XQueryService service =
                (XQueryService) testCollection.getService(
                "XPathQueryService",
                "1.0");
        return service;
    }
    
    public void testNamespaces() {
        try {
            XQueryService service =
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
            assertEquals("http://www.foo.com", result.getResource(0).getContent());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testPreserveSpace() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("whitespace.xml", ws);
            
            ResourceSet result =
                    service.queryResource("whitespace.xml", "//text");
            assertEquals(2, result.getSize());
            
            String item = result.getResource(0).getContent().toString();
            assertXMLEqual("<text> </text>", item);
            item = result.getResource(1).getContent().toString();
            assertXMLEqual("<text xml:space=\"default\"/>", item);
        } catch (Exception e) {
            fail(e.getMessage());
        }
    }
    
    public void testNestedElements() {
        try {
            XQueryService service =
                    storeXMLStringAndGetQueryService("nested.xml", nested);
            
            ResourceSet result = service.queryResource("nested.xml", "//c");
            printResult(result);
            assertEquals( 3, result.getSize() );
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testStaticVariables() {
        ResourceSet result = null;
        try {
            XMLResource doc =
                    (XMLResource) testCollection.createResource(
                    "numbers.xml", "XMLResource" );
            doc.setContent(numbers);
            testCollection.storeResource(doc);
            XPathQueryService service =
                    (XPathQueryService) testCollection.getService(
                    "XPathQueryService",
                    "1.0");
            
            XPathQueryServiceImpl service2 = (XPathQueryServiceImpl) service;
            service2.declareVariable("name", "MONTAGUE");
            service2.declareVariable("name", "43");
            
            //ResourceSet result = service.query("//SPEECH[SPEAKER=$name]");
            result = service2.query( doc, "//item[stock=$name]");
            
            System.out.println( "testStaticVariables 1: ========" );
            printResult(result);
            result = service2.query("$name");
            assertEquals( 1, result.getSize() );
            
            System.out.println("testStaticVariables 2: ========" );
            printResult(result);
            result = service2.query( doc, "//item[stock=43]");
            assertEquals( 1, result.getSize() );
            
            System.out.println("testStaticVariables 3: ========" );
            printResult(result);
            result = service2.query( doc, "//item");
            assertEquals( 4, result.getSize() );
            
            // assertEquals( 10, result.getSize() );
        } catch (XMLDBException e) {
            System.out.println("testStaticVariables(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    /**
     * @param result
     * @throws XMLDBException
     */
    private void printResult(ResourceSet result) throws XMLDBException {
        for (ResourceIterator i = result.getIterator();
        i.hasMoreResources();
        ) {
            Resource r = i.nextResource();
            System.out.println(r.getContent());
        }
    }
    
    public void testMembersAsResource() {
        try {
//			XPathQueryService service =
//				(XPathQueryService) testCollection.getService(
//					"XPathQueryService",
//					"1.0");
//			ResourceSet result = service.query("//SPEECH[LINE &= 'marriage']");
            XQueryService service =
                    storeXMLStringAndGetQueryService("numbers.xml", numbers);
            ResourceSet result = service.query("//item/price");
            
            Resource r = result.getMembersAsResource();
            Object rawContent = r.getContent();
            String content = null;
            if(rawContent instanceof File) {
                FileInputStream fis = new FileInputStream((File)rawContent);
                ByteArrayOutputStream bos = new ByteArrayOutputStream(2048);
                byte[] temp = new byte[1024];
                int count = 0;
                while((count = fis.read(temp)) > -1) {
                    bos.write(temp, 0, count);
                }
                content = new String(bos.toByteArray(),UTF_8);
            } else {
                content = (String)r.getContent();
            }
            System.out.println(content);
            
            Pattern p = Pattern.compile( ".*(<price>.*){4}", Pattern.DOTALL);
            Matcher m = p.matcher(content);
            assertTrue( "get whole document numbers.xml", m.matches() );
        } catch (UnsupportedEncodingException uee) {
            fail(uee.getMessage());
        } catch (IOException ioe) {
            fail(ioe.getMessage());
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testSatisfies() {
        try {
            XQueryService service = getQueryService();
            ResourceSet result;
            
            result = queryAndAssert( service,
                    "every $foo in (1,2,3) satisfies" +
                    "   let $bar := 'baz'" +
                    "       return false() ",
                    1,  "" );
            assertEquals( "satisfies + FLWR expression allways false 1", "false", result.getResource(0).getContent() );
            
            result = queryAndAssert( service,
                    "declare function local:foo() { false() };" +
                    "   every $bar in (1,2,3) satisfies" +
                    "   local:foo()",
                    1,  "" );
            assertEquals( "satisfies + FLWR expression allways false 2", "false", result.getResource(0).getContent() );
            
            query = "every $x in () satisfies false()";
            result = queryAndAssert( service, query, 1,  "" );
            assertEquals( query, "true", result.getResource(0).getContent() );
            
            query = "some $x in () satisfies true()";
            result = queryAndAssert( service, query, 1,  "" );
            assertEquals( query, "false", result.getResource(0).getContent() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testIntersect() {
        try {
            XQueryService service = getQueryService();
            @SuppressWarnings("unused")
			ResourceSet result;
            
            query = "()  intersect ()";
            result = queryAndAssert( service, query, 0, "");
            
            query = "()  intersect  (1)";
            result = queryAndAssert( service, query, 0, "");
            
            query = "(1)  intersect  ()";
            result = queryAndAssert( service, query, 0, "");
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testUnion() {
        try {
            XQueryService service = getQueryService();
            @SuppressWarnings("unused")
			ResourceSet result;
            
            query = "()  union ()";
            result = queryAndAssert( service, query, 0, "");
            
            @SuppressWarnings("unused")
			boolean exceptionThrown = false;
            String message = "";
            try {
                query = "()  union  (1)";
                result = queryAndAssert( service, query, 0, "");
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPTY0004") > -1);
            
            exceptionThrown = false;
            message = "";
            try {
                query = "(1)  union  ()";
                result = queryAndAssert( service, query, 0, "");
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPTY0004") > -1);
            
            query = "<a/>  union ()";
            result = queryAndAssert( service, query, 1, "");
            query = "()  union <a/>";
            result = queryAndAssert( service, query, 1, "");
            //Not the same nodes
            query = "<a/> union <a/>";
            result = queryAndAssert( service, query, 2, "");
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testExcept() {
        try {
            XQueryService service = getQueryService();
            
            query = "()  except ()";
            queryAndAssert( service, query, 0, "");
            
            query = "()  except  (1)";
            queryAndAssert( service, query, 0, "");
            
            String message = "";
            try {
                query = "(1)  except  ()";
                queryAndAssert( service, query, 0, "");
            } catch (XMLDBException e) {
                message = e.getMessage();
            }
            assertTrue(message.indexOf("XPTY0004") > -1);
            
            query = "<a/>  except ()";
            queryAndAssert( service, query, 1, "");
            query = "()  except <a/>";
            queryAndAssert( service, query, 0, "");
            //Not the same nodes
            query = "<a/> except <a/>";
            queryAndAssert( service, query, 1, "");
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    public void testConvertToBoolean() throws XMLDBException {
        
        XQueryService service = getQueryService();
        ResourceSet result;
        
        try {
            result = queryAndAssert(
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
            for (int i = 0; i < 5; i++) {
                assertEquals("true " + (i + 1), "<true>true</true>", result
                        .getResource(i).getContent());
            }
            for (int i = 5; i < 10; i++) {
                assertEquals("false " + (i + 1), "<false>false</false>", result
                        .getResource(i).getContent());
            }
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
        
        boolean exceptionThrown = false;
        String message = "";
        try {
            result = queryAndAssert(service,
                    "let $doc := <element attribute=''/>"
                    + " return boolean( (1,2,$doc) )", 1, "");
        } catch (XMLDBException e) {
            exceptionThrown = true;
            message = e.getMessage();
        }
        assertTrue("Exception wanted: " + message, exceptionThrown);
    }
    
    public void testCompile() throws XMLDBException {
        String invalidQuery = "for $i in (1 to 10)\n return $b";
        String validQuery = "for $i in (1 to 10) return $i";
        String validModule = "module namespace foo=\"urn:foo\";\n" +
                "declare function foo:test() { \"Hello World!\" };";
        String invalidModule = "module namespace foo=\"urn:foo\";\n" +
                "declare function foo:test() { \"Hello World! };";
        
        org.exist.xmldb.XQueryService service = (org.exist.xmldb.XQueryService) getQueryService();
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
        
        try {
            service.compile(validQuery);
            service.compile(validModule);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }
    
    /**
     * Added by Geoff Shuetrim on 15 July 2006 (geoff@galexy.net).
     * This test has been added following identification of a problem running
     * XPath queries that involved the name of elements with the name 'xpointer'.
     * @throws XMLDBException
     */
    public void bugtestXPointerElementNameHandling() {
        try {
            
            XQueryService service = storeXMLStringAndGetQueryService(
                    "xpointer.xml", xpointerElementName);
            
            ResourceSet result = service.queryResource("xpointer.xml",
                    "/test/.[local-name()='xpointer']");
            printResult(result);
            assertEquals(1, result.getSize());
            
            result = service.queryResource("xpointer.xml", "/test/xpointer");
            printResult(result);
            assertEquals(1, result.getSize());
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }

    public void testAtomization() {
        try {
            String query =
                    "declare namespace ex = \"http://example.org\";\n" +
                    "declare function ex:elementName() as xs:QName {\n" +
                    "   QName(\"http://test.org\", \"test:name\")\n" +
                    "};\n" +
                    "<test>{\n" +
                    "   element {QName(\"http://test.org\", \"test:name\") }{},\n" +
                    "   element {ex:elementName()} {}\n" +
                    "}</test>";

            org.exist.xmldb.XQueryService service = (org.exist.xmldb.XQueryService) getQueryService();
            service.setProperty(OutputKeys.INDENT, "no");
            ResourceSet result = service.query(query);
            assertEquals(1, result.getSize());
            printResult(result);
            assertXMLEqual(result.getResource(0).getContent().toString(),
                    "<test><test:name xmlns:test=\"http://test.org\"/><test:name xmlns:test=\"http://test.org\"/></test>");
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    public void testSubstring() throws XMLDBException {
        
        XQueryService service = getQueryService();
        ResourceSet result;
        
        // Testcases by MIKA
        try {
            String validQuery="substring(\"MK-1234\", 4,1)";
            result = queryAndAssert( service, validQuery, 1, validQuery);
            assertEquals("1", result.getResource(0).getContent().toString() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
        
        try {
            String invalidQuery="substring(\"MK-1234\", 4,4)";
            result = queryAndAssert( service, invalidQuery, 1, invalidQuery);
            assertEquals("1234", result.getResource(0).getContent().toString() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
        
        
        // Testcase by Toar
        try {
            String toarQuery="let $num := \"2003.123\" \n return substring($num, 1, 7)";
            result = queryAndAssert( service, toarQuery, 1, toarQuery);
            assertEquals("2003.12", result.getResource(0).getContent().toString() );
            
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
        
        
    }
    
    public static void main(String[] args) {
        junit.textui.TestRunner.run(XPathQueryTest.class);
    }
}
