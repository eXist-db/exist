package org.exist.xquery.test;

import java.io.File;

import junit.framework.TestCase;

import org.exist.xmldb.DatabaseInstanceManager;
import org.w3c.dom.Node;
import org.w3c.dom.Element;
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

public class XQueryTest extends TestCase {

	private static final String NUMBERS_XML = "numbers.xml";
	private final static String URI = "xmldb:exist:///db";

	private final static String numbers =
		"<test>"
			+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
			+ "<item id='2'><price>7.4</price><stock>43</stock></item>"
			+ "<item id='3'><price>18.4</price><stock>5</stock></item>"
			+ "<item id='4'><price>65.54</price><stock>16</stock></item>"
			+ "</test>";

	private Collection testCollection;
	private static String attributeXML;
	private static int stringSize;
	private static int nbElem;
	private String file_name = "detail_xml.xml";
	private String xml;
	private Database database;

	public XQueryTest(String arg0) {
		super(arg0);
	}

	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			Collection root =
				DatabaseManager.getCollection(
					"xmldb:exist:///db",
					"admin",
					null);
			CollectionManagementService service =
				(CollectionManagementService) root.getService(
					"CollectionManagementService",
					"1.0");
			testCollection = service.createCollection("test");
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
	protected void tearDown() throws Exception {
		// testCollection.removeResource( testCollection .getResource(file_name));
		
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) testCollection.getService(
				"DatabaseInstanceManager", "1.0");
		dim.shutdown();
		System.out.println("tearDown PASSED");
	}
	
	public void testFor() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testFor 1: ========" );
			query = "for $f in /*/item return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

			System.out.println("testFor 2: ========" );
			query = "for $f in /*/item  order by $f ascending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 3: ========" );
			query = "for $f in /*/item  order by $f descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "2", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			System.out.println("testFor 4: ========" );
			query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "4", ((Element)resu.getContentAsDOM()).getAttribute("id") );
		} catch (XMLDBException e) {
			System.out.println("testFor(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	public void testTypedVariables() {
		ResourceSet result;
		String query;
		boolean exceptionThrown;
		String message;		
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			System.out.println("testTypedVariables 1: ========" );
			query = "let $v as element()* := ( <assign/> , <assign/> )\n" 
				+ "let $w := <r>{ $v }</r>\n"
				+ "let $x as element()* := $w/assign\n"
				+ "return $x";
			result = service.queryResource(NUMBERS_XML, query );				
			assertEquals( "XQuery: " + query, 2, result.getSize() );
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "assign", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());

			System.out.println("testTypedVariables 2: ========" );
			query = "let $v as node()* := ()\n" 
			+ "return $v";
			result = service.queryResource(NUMBERS_XML, query );	
			assertEquals( "XQuery: " + query, 0, result.getSize() );
			
			System.out.println("testTypedVariables 3: ========" );
			query = "let $v as item()* := ()\n" 
			+ "return $v";
			result = service.queryResource(NUMBERS_XML, query );
			assertEquals( "XQuery: " + query, 0, result.getSize() );			

			System.out.println("testTypedVariables 4: ========" );
			query = "let $v as empty() := ()\n" 
			+ "return $v";
			result = service.queryResource(NUMBERS_XML, query );
			assertEquals( "XQuery: " + query, 0, result.getSize() );			
			
			System.out.println("testTypedVariables 5: ========" );
			query = "let $v as item() := ()\n" 
			+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.queryResource(NUMBERS_XML, query );					
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue("XQuery: " + query, exceptionThrown);
			
			System.out.println("testTypedVariables 6: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
			+ "return $v";
			result = service.queryResource(NUMBERS_XML, query );
			assertEquals( "XQuery: " + query, 2, result.getSize() );	
			assertEquals( "XQuery: " + query, Node.ELEMENT_NODE, ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeType());
			assertEquals( "XQuery: " + query, "a", ((XMLResource)result.getResource(0)).getContentAsDOM().getNodeName());			
			assertEquals( "XQuery: " + query, "1", ((XMLResource)result.getResource(1)).getContent());		
			
			System.out.println("testTypedVariables 7: ========" );
			query = "let $v as node()* := ( <a/> , 1 )\n" 
			+ "return $v";			
			try {
				exceptionThrown = false;
				result = service.queryResource(NUMBERS_XML, query );	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);	
			
			System.out.println("testTypedVariables 8: ========" );
			query = "let $v as item()* := ( <a/> , 1 )\n" 
				+ "let $w as element()* := $v\n"
				+ "return $v";		
			try {
				exceptionThrown = false;
				result = service.queryResource(NUMBERS_XML, query );	
			} catch (XMLDBException e) {
				exceptionThrown = true;
				message = e.getMessage();
			}
			assertTrue(exceptionThrown);				
			

		
		} catch (XMLDBException e) {
			System.out.println("testTypedVariables : XMLDBException: "+e);
			fail(e.getMessage());
		}
	}	
	
	private String makeString(int n){
		StringBuffer b = new StringBuffer();
		char c = 'a';
		for ( int i=0; i<n; i++ ) {
			b.append(c);
		}
		return b.toString();
	}

	public void testLargeAttributeSimple() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);
			
			query = "doc('"+ file_name+"') / details/metadata[@docid= '" + large + "' ]";
			result = service.queryResource(file_name, query );
			printResult(result);
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testLargeAttributeContains() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ contains(@docid, 'aa') ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}

	public void testLargeAttributeKeywordOperator() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeSimple 1: ========" );
			String large = createXMLContentWithLargeString();
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(file_name, xml);

			query = "doc('"+ file_name+"') / details/metadata[ @docid &= '" + large + "' ]";
			result = service.queryResource(file_name, query );
			assertEquals( "XQuery: " + query, nbElem, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeSimple(): XMLDBException: "+e);
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
		for ( int i=0; i< nbElem; i++ )
			xml += elem;
		xml += tail;
		System.out.println("XML:\n" + xml);
		return large;
	}

	public void testRetrieveLargeAttribute() throws XMLDBException{
		System.out.println("testRetrieveLargeAttribute 1: ========" );
		XMLResource res = (XMLResource) testCollection.getResource(file_name);
		System.out.println("res.getContent(): " + res.getContent() );
	}
	
	/** This test is obsolete because testLargeAttributeSimple() reproduces the problem without a file,
	 * but I keep it to show how one can test with an XML file. */
	public void obsoleteTestLargeAttributeRealFile() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			System.out.println("testLargeAttributeRealFile 1: ========" );
			String large;
			large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
			if (attributeXML != null)
				large = attributeXML;
			String xml = "<details format='xml'><metadata docid='" + large +
				"'></metadata></details>";
			final String FILE_NAME = "detail_xml.xml";
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(FILE_NAME);

			query = "doc('"+ FILE_NAME+"') / details/metadata[@docid= '" + large + "' ]"; // fails !!!
			// query = "doc('"+ FILE_NAME+"') / details/metadata[ docid= '" + large + "' ]"; // test passes!
			result = service.queryResource(FILE_NAME, query );
			printResult(result);
			assertEquals( "XQuery: " + query, 2, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttributeRealFile(): XMLDBException: "+e);
			fail(e.getMessage());
		}
	}
	
	/**
	 * @return
	 * @throws XMLDBException
	 */
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName,
			String content) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
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
	private XPathQueryService storeXMLStringAndGetQueryService(String documentName
			) throws XMLDBException {
		XMLResource doc =
			(XMLResource) testCollection.createResource(
					documentName, "XMLResource" );
		doc.setContent(new File(documentName));
		testCollection.storeResource(doc);
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
				"XPathQueryService",
				"1.0");
		return service;
	}

	/**
	 * @param result
	 * @throws XMLDBException
	 */
	private void printResult(ResourceSet result) throws XMLDBException {
		for (ResourceIterator i = result.getIterator();
			i.hasMoreResources(); ) {
			Resource r = i.nextResource();
			System.out.println(r.getContent());
		}
	}

	public static void main(String[] args) {
		if ( args.length > 0 ) {
			attributeXML = args[0];
		}
		stringSize = 513;
		if ( args.length > 1 ) {
			stringSize = Integer.parseInt( args[1] );
		}
		nbElem = 2;
		if ( args.length > 2 ) {
			nbElem = Integer.parseInt( args[2] );
		}

		junit.textui.TestRunner.run(XQueryTest.class);
	}
}
