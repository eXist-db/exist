package org.exist.xpath.test;

import java.io.File;

import junit.framework.TestCase;

import org.exist.xmldb.XPathQueryServiceImpl;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

public class XPathQueryTest extends TestCase {

	private final static String URI = "xmldb:exist:///db";

	private final static String nested =
		"<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
			+ "<test><c></c><b><c><b></b></c></b><b></b><c></c></test>";

	private final static String numbers =
		"<test>"
			+ "<item id='1'><price>5.6</price><stock>22</stock></item>"
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

	public XPathQueryTest(String arg0) {
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
			root = service.createCollection("test");

			XMLResource doc =
				(XMLResource) root.createResource("r_and_j.xml", "XMLResource");
			doc.setContent(new File("samples/shakespeare/r_and_j.xml"));
			root.storeResource(doc);
		} catch (ClassNotFoundException e) {
		} catch (InstantiationException e) {
		} catch (IllegalAccessException e) {
		} catch (XMLDBException e) {
			e.printStackTrace();
		}
	}

	public void testNumbers() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc =
				(XMLResource) testCollection.createResource(
					"numbers.xml",
					"XMLResource");
			doc.setContent(numbers);
			testCollection.storeResource(doc);
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			ResourceSet result =
				service.queryResource("numbers.xml", "sum(/test/item/price)");
			assertEquals(result.getSize(), 1);
			assertEquals(result.getResource(0).getContent(), "96.94");

			result =
				service.queryResource(
					"numbers.xml",
					"round(sum(/test/item/price))");
			assertEquals(result.getSize(), 1);
			assertEquals(result.getResource(0).getContent(), "97.0");

			result =
				service.queryResource(
					"numbers.xml",
					"floor(sum(/test/item/stock))");
			assertEquals(result.getSize(), 1);
			assertEquals(result.getResource(0).getContent(), "86.0");

			result =
				service.queryResource(
					"numbers.xml",
					"/test/item[round(price + 3) > 60]");
			assertEquals(result.getSize(), 1);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public void testStrings() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc =
				(XMLResource) testCollection.createResource(
					"strings.xml",
					"XMLResource");
			doc.setContent(strings);
			testCollection.storeResource(doc);
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			ResourceSet result =
				service.queryResource(
					"strings.xml",
					"substring(/test/string[1], 1, 5)");
			assertEquals(1, result.getSize());
			assertEquals(result.getResource(0).getContent(), "Hello");

			result =
				service.queryResource(
					"strings.xml",
					"/test/string[starts-with(string(.), 'Hello')]");
			assertEquals(2, result.getSize());
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public void testNamespaces() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc =
				(XMLResource) testCollection.createResource(
					"namespaces.xml",
					"XMLResource");
			doc.setContent(namespaces);
			testCollection.storeResource(doc);
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			service.setNamespace("t", "http://www.foo.com");
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
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public void testNestedElements() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc =
				(XMLResource) testCollection.createResource(
					"nested.xml",
					"XMLResource");
			doc.setContent(nested);
			testCollection.storeResource(doc);
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			ResourceSet result = service.queryResource("nested.xml", "//c");
			for (ResourceIterator i = result.getIterator();
				i.hasMoreResources();
				) {
				Resource r = i.nextResource();
				System.out.println(r.getContent());
			}
			assertEquals(result.getSize(), 3);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public void testStaticVariables() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc =
				(XMLResource) testCollection.createResource(
					"nested.xml",
					"XMLResource");
			doc.setContent(nested);
			testCollection.storeResource(doc);
			XPathQueryServiceImpl service =
				(XPathQueryServiceImpl) testCollection.getService(
					"XPathQueryService",
					"1.0");
			service.declareVariable("name", "MONTAGUE");
			ResourceSet result = service.query("//SPEECH[SPEAKER=$name]");
			for (ResourceIterator i = result.getIterator();
				i.hasMoreResources();
				) {
				Resource r = i.nextResource();
				System.out.println(r.getContent());
			}
			assertEquals(result.getSize(), 10);
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public void testMembersAsResource() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XPathQueryService service =
				(XPathQueryService) testCollection.getService(
					"XPathQueryService",
					"1.0");
			ResourceSet result = service.query("//SPEECH[LINE &= 'marriage']");
			Resource r = result.getMembersAsResource();
			System.out.println(r.getContent());
		} catch (XMLDBException e) {
			fail(e.getMessage());
		}
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(XPathQueryTest.class);
	}
}
