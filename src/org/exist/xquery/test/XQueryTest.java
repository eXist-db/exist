package org.exist.xquery.test;

import java.io.File;

import junit.framework.TestCase;

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

	public XQueryTest(String arg0) {
		super(arg0);
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

	public void testFor() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			XPathQueryService service = 
				storeXMLStringAndGetQueryService(NUMBERS_XML, numbers);

			query = "for $f in /*/item return $f";
			result = service.queryResource(NUMBERS_XML, query );
			System.out.println("testFor 1: ========" );
			printResult(result);
			assertEquals( "XQuery: " + query, 4, result.getSize() );

			query = "for $f in /*/item  order by $f ascending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			System.out.println("testFor 2: ========" );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "3", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			query = "for $f in /*/item  order by $f descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			System.out.println("testFor 3: ========" );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "2", ((Element)resu.getContentAsDOM()).getAttribute("id") );

			query = "for $f in /*/item  order by xs:double($f/price) descending  return $f";
			result = service.queryResource(NUMBERS_XML, query );
			System.out.println("testFor 4: ========" );
			printResult(result);
			resu = (XMLResource) result.getResource(0);
			assertEquals( "XQuery: " + query, "4", ((Element)resu.getContentAsDOM()).getAttribute("id") );
		} catch (XMLDBException e) {
			System.out.println("testFor(): XMLDBException: "+e);
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
	
	public void testLargeAttribute() {
		ResourceSet result;
		String query;
		XMLResource resu;
		try {
			String large = makeString(2000); // 592);
			large = "challengesininformationretrievalandlanguagemodelingreportofaworkshopheldatthecenterforintelligentinformationretrievaluniversityofmassachusettsamherstseptember2002-extdocid-howardturtlemarksandersonnorbertfuhralansmeatonjayaslamdragomirradevwesselkraaijellenvoorheesamitsinghaldonnaharmanjaypontejamiecallannicholasbelkinjohnlaffertylizliddyronirosenfeldvictorlavrenkodavidjharperrichschwartzjohnpragerchengxiangzhaijinxixusalimroukosstephenrobertsonandrewmccallumbrucecroftrmanmathasuedumaisdjoerdhiemstraeduardhovyralphweischedelthomashofmannjamesallanchrisbuckleyphilipresnikdavidlewis2003";
			String xml = "<details format='xml'><metadata docid='" + large +
				"'></metadata></details>";
			XPathQueryService service = 
				// storeXMLStringAndGetQueryService(NUMBERS_XML, xml);
				storeXMLStringAndGetQueryService("detail_xml.xml");

			query = "doc('"+ NUMBERS_XML+"') / details/metadata[@docid= '" + large + "' ]";
			result = service.queryResource("detail_xml.xml", query );
			System.out.println("testLargeAttribute 1: ========" );
			printResult(result);
			assertEquals( "XQuery: " + query, 1, result.getSize() );
		} catch (XMLDBException e) {
			System.out.println("testLargeAttribute(): XMLDBException: "+e);
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
		junit.textui.TestRunner.run(XQueryTest.class);
	}
}
