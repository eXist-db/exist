package org.exist.xmldb.test;

import junit.framework.TestCase;
import org.xmldb.api.*;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.*;

public class XPathQueryTest extends TestCase {

	private final static String URI = "xmldb:exist:///db";

	private final static String[] queries = {
		"//SPEECH",
		"//SPEECH/LINE",
		"//PERSONAE",
		"//SPEECH[LINE &= 'love']",
		"//SPEECH[SPEAKER = 'HAMLET']",
		"//SPEECH[near(LINE, 'fenny snake')][SPEAKER &= 'witch']"
	};
		
	private final static int[] results = { 2628, 9492, 3,
		160, 359, 1 };
	
	private final static String testDoc =
			"<?xml version=\"1.0\" encoding=\"UTF-8\"?>" +
			"<test><c></c><b><c><b></b></c></b><b></b><c></c></test>";
			
	public XPathQueryTest(String arg0) {
		super(arg0);
	}

	public void testXPath() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XPathQueryService service = (XPathQueryService)
				testCollection.getService("XPathQueryService", "1.0");
			for(int i = 0; i < queries.length; i++) {
				ResourceSet result = service.query(queries[i]);
				assertEquals(result.getSize(), results[i]);
			}
		} catch(XMLDBException e) {
			fail(e.getMessage());
		}
	}
	
	public void testNestedElements() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XMLResource doc = (XMLResource)
				testCollection.createResource("nested.xml", "XMLResource");
			doc.setContent(testDoc);
			testCollection.storeResource(doc);
			XPathQueryService service = (XPathQueryService)
				testCollection.getService("XPathQueryService", "1.0");
			ResourceSet result = 
				service.queryResource("nested.xml", "//c");
			for(ResourceIterator i = result.getIterator(); i.hasMoreResources(); ) {
				Resource r = i.nextResource();
				System.out.println(r.getContent());
			}
			assertEquals(result.getSize(), 3);
		} catch(XMLDBException e) {
			fail(e.getMessage());
		}
	}
	
	public void testMembersAsResource() {
		try {
			Collection testCollection =
				DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			XPathQueryService service = (XPathQueryService)
				testCollection.getService("XPathQueryService", "1.0");
			ResourceSet result = 
				service.query("//SPEECH[LINE &= 'marriage']");
			Resource r = result.getMembersAsResource();
			System.out.println(r.getContent());
		} catch(XMLDBException e) {
			fail(e.getMessage());
		}
	}
}
