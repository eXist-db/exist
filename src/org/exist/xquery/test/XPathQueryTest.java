package org.exist.xquery.test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.OutputKeys;

import org.custommonkey.xmlunit.XMLTestCase;
import org.exist.storage.DBBroker;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;
import org.xmldb.api.modules.XQueryService;

public class XPathQueryTest extends XMLTestCase {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	
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

	private final static String namespaces =
		"<test xmlns='http://www.foo.com'>"
			+ "<section>"
			+ "<title>Test Document</title>"
			+ "<c:comment xmlns:c='http://www.other.com'>This is my comment</c:comment>"
			+ "</section>"
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

	private final static String ids =
	    "<!DOCTYPE test [" +
	    "<!ELEMENT test (a*, b*)>" +
	    "<!ELEMENT a EMPTY>" +
	    "<!ELEMENT b (name)>" +
	    "<!ELEMENT name (#PCDATA)>" +
	    "<!ATTLIST a ref IDREF #IMPLIED>" +
	    "<!ATTLIST b id ID #IMPLIED>]>" +
	    "<test xml:space=\"preserve\">" +
	    "<a ref=\"id1\"/>" +
	    "<a ref=\"id1\"/>" +
	    "<d ref=\"id2\"/>" +
	    "<b id=\"id1\"><name>one</name></b>" +
	    "<c xml:id=\"     id2     \"><name>two</name></c>" +
	    "</test>";
	
	private final static String quotes =
		"<test><title>&quot;Hello&quot;</title></test>";
	
	private final static String ws =
		"<test><parent xml:space=\"preserve\"><text> </text><text xml:space=\"default\"> </text></parent></test>";
	
	private Collection testCollection;
	private String query;
	
	public XPathQueryTest(String name) {
		super(name);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection(
					URI,
					"admin",
					null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			testCollection = service.createCollection("test");
			assertNotNull(testCollection);
			
//			XMLResource doc =
//				(XMLResource) root.createResource("r_and_j.xml", "XMLResource");
//			doc.setContent(new File("samples/shakespeare/r_and_j.xml"));
//			root.storeResource(doc);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}
    
    public void testPathExpression() {
        try {
            XQueryService service = 
                storeXMLStringAndGetQueryService("numbers.xml", numbers); 
            
            boolean exceptionThrown = false;
            String message = "";
            try {
                queryAndAssert(service, "('a', 'b', 'c')/position()", -1, null);                
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue("Exception wanted: " + message, exceptionThrown);
            
            exceptionThrown = false;
            message = "";
            try {
                queryAndAssert(service, "for $a in (<a/>, <b/>, doh, <c/>) return $a", -1, null);                
            } catch (XMLDBException e) {
                exceptionThrown = true;
                message = e.getMessage();
            }
            assertTrue("Exception wanted: " + message, exceptionThrown);            
            
            queryAndAssert(service, "()/position()", 0, null);
            
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
            assertEquals("XPath: " + query, "item", resource.getContentAsDOM().getNodeName());
		
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

            query =  "// t:title/text() [ . != 'aaaa' ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxis2 : ========" );        printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );            

            result = service.queryResource("namespaces.xml", "/t:test/*:section[. &= 'comment']");
            assertEquals(1, result.getSize());
            
            result = service.queryResource("namespaces.xml", "/t:test/t:*[. &= 'comment']");
            assertEquals(1, result.getSize());
            result = service.queryResource("namespaces.xml", "/t:test/t:section[. &= 'comment']");
            assertEquals(1, result.getSize());
			
            result = service.queryResource("namespaces.xml", "/t:test/t:section/*[. &= 'comment']");
			assertEquals("", 1, result.getSize());

			 query =  "/ * / * [ t:title ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxis2 : ========" );        
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );   
            
			query =  "/ t:test / t:section [ t:title ]";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxis2 : ========" );        
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() ); 
            
			query =  "/ t:test / t:section";
            result = service.queryResource( "namespaces.xml", query );
            System.out.println("testStarAxis2 : ========" );        
            printResult(result);
            assertEquals( "XPath: "+query, 1, result.getSize() );

        } catch (XMLDBException e) {
            System.out.println("testStarAxis(): XMLDBException: "+e);
            fail(e.getMessage());
    }
}
    
    public void bugtestStarAxisConstraints2() {
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
            
        } catch (XMLDBException e) {
            //org.xmldb.api.base.XMLDBException: Internal evaluation error: context node is missing for node 3 !
            System.out.println("testStarAxisConstraints2(): XMLDBException: "+e);
            fail(e.getMessage());
        }
    }
    
    public void bugtestStarAxisConstraints3() {
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
    
    
    public void testParentAxis() {
        try {
            XQueryService service = 
                storeXMLStringAndGetQueryService("nested2.xml", nested2);
            
            queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/parent::*", 0);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }    
    
    public void testParentSelfAxis() {
        try {
            XQueryService service = 
                storeXMLStringAndGetQueryService("nested2.xml", nested2);
            
            queryResource(service, "nested2.xml", "/RootElement/descendant::*/parent::ChildA", 1);
            queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildB]/parent::RootElement", 0);
            queryResource(service, "nested2.xml", "/RootElement/descendant::*[self::ChildA]/parent::RootElement", 1);
            queryResource(service, "nested2.xml", "let $a := ('', 'b', '', '') for $b in $a[.] return <blah>{$b}</blah>", 1);

        } catch (XMLDBException e) {
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
                storeXMLStringAndGetQueryService("nested2.xml", nested2);            
            queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/following-sibling::*", 0);
        } catch (XMLDBException e) {
            fail(e.getMessage());
        }
    }     
    
    public void testFollowingSiblingAxis() {
        try {
            XQueryService service = 
                storeXMLStringAndGetQueryService("nested2.xml", nested2);            
            queryResource(service, "nested2.xml", "(<a/>, <b/>, <c/>)/following-sibling::*", 0);
        } catch (XMLDBException e) {
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
            
            String query = "for $a in (<a/>, <b/>, <c/>) return $a/position()";
            ResourceSet  result = service.queryResource("numbers.xml", query);           
            assertEquals("XPath: " + query, 3, result.getSize());            
            XMLResource resource = (XMLResource)result.getResource(0); 
            assertEquals("XPath: " + query, "1", resource.getContent().toString());
            resource = (XMLResource)result.getResource(1); 
            assertEquals("XPath: " + query, "1", resource.getContent().toString());
            resource = (XMLResource)result.getResource(2); 
            assertEquals("XPath: " + query, "1", resource.getContent().toString()); 
            
            query = "let $a := ('a', 'b', 'c') for $b in $a[position()] return <blah>{$b}</blah>";
            result = service.queryResource("numbers.xml", query); 
            assertEquals("XPath: " + query, 3, result.getSize());             
            
            
        } catch (XMLDBException e) {
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
			assertEquals( "97.0", result.getResource(0).getContent() );

			result = queryResource(service, "numbers.xml", "floor(sum(/test/item/stock))", 1);
			assertEquals( "86.0", result.getResource(0).getContent());

			queryResource(service, "numbers.xml", "/test/item[round(price + 3) > 60]", 1);

			result = queryResource(service, "numbers.xml", "min( 123456789123456789123456789, " +
					          "123456789123456789123456789123456789123456789 )", 1);
			assertEquals("minimum of big integers",
					"123456789123456789123456789", 
					result.getResource(0).getContent() );
		} catch (XMLDBException e) {
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
		} catch (XMLDBException e) {
			fail(e.getMessage());
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
			
			result = queryResource(service, "numbers.xml", "boolean(current-time())", 1);
			assertEquals("boolean value of current-time() should be true", "true", 
					result.getResource(0).getContent());
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
			queryResource(	service, "strings.xml", "document()/blah[not(blah)]", 0);
			queryResource(service, "strings.xml", "//*[string][not(@value)]", 1);
			queryResource(service, "strings.xml", "//*[string][not(@blah)]", 1);
			queryResource(service, "strings.xml", "//*[blah][not(@blah)]", 0);
		} catch (XMLDBException e) {
			System.out.println("testStrings(): XMLDBException: "+e);
			e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testIds() {
		try {
			XQueryService service = 
				storeXMLStringAndGetQueryService("ids.xml", ids);
			
			queryResource(service, "ids.xml", "//a/id(@ref)", 1);
			queryResource(service, "ids.xml", "id(//a/@ref)", 1);
			
			ResourceSet result = queryResource(service, "ids.xml", "//a/id(@ref)/name", 1);
			Resource r = result.getResource(0);
			assertEquals("<name>one</name>", r.getContent().toString());
			
			result = queryResource(service, "ids.xml", "//d/id(@ref)/name", 1);
			r = result.getResource(0);
			assertEquals("<name>two</name>", r.getContent().toString());
			
			String update = "update insert <t xml:id=\"id3\">Hello</t> into /test";
			queryResource(service, "ids.xml", update, 0);
			
			queryResource(service, "ids.xml", "id('id3')", 1);
			
			update = "update value //t/@xml:id with 'id4'";
			queryResource(service, "ids.xml", update, 0);
			queryResource(service, "ids.xml", "id('id4')", 1);
		} catch (XMLDBException e) {
			System.out.println("testIds(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
    public void testExternalVars() {
        try {
            XQueryService service = (XQueryService)
                storeXMLStringAndGetQueryService("strings.xml", strings);
            
            String query =
                "declare variable $local:string external;" +
                "/test/string[. = $local:string]";
            CompiledExpression expr = service.compile(query);
            service.declareVariable("local:string", "Hello");
            
            ResourceSet result = service.execute(expr);
            
            Resource r = result.getResource(0);
            assertEquals("string", ((XMLResource)r).getContentAsDOM().getNodeName());
        } catch (XMLDBException e) {
            System.out.println("testExternalVars(): XMLDBException");
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
			assertEquals(expected, result.getSize());
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
			
			System.out.println( "testStaticVariables 1: ========" ); 		printResult(result);
			result = service2.query("$name");
			assertEquals( 1, result.getSize() );

			System.out.println("testStaticVariables 2: ========" ); 		printResult(result);
			result = service2.query( doc, "//item[stock=43]");
			assertEquals( 1, result.getSize() );

			System.out.println("testStaticVariables 3: ========" ); 		printResult(result);
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
			String content = (String)r.getContent();
			System.out.println(content);
			
			Pattern p = Pattern.compile( ".*(<price>.*){4}", Pattern.DOTALL);
			Matcher m = p.matcher(content);
			assertTrue( "get whole document numbers.xml", m.matches() );
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
				"	let $bar := 'baz'" +
				"		return false() ", 
				1,  "" );
			assertEquals( "satisfies + FLWR expression allways false 1", "false", result.getResource(0).getContent() );

			result = queryAndAssert( service,
					"declare function local:foo() { false() };" +
					"	every $bar in (1,2,3) satisfies" +
					"	local:foo()",
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

	public static void main(String[] args) {
		junit.textui.TestRunner.run(XPathQueryTest.class);
	}
}
