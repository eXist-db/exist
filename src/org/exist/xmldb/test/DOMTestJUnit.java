/*
 * Created on 7 août 2004
$Id$
 */
package org.exist.xmldb.test;

import org.exist.xmldb.DatabaseInstanceManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import junit.framework.TestCase;

/**
 * @author jmv
 */
public class DOMTestJUnit extends TestCase {
	private static String driver = "org.exist.xmldb.DatabaseImpl";
	private static String baseURI = "xmldb:exist:///db";

	private static String username = "admin";
	private static String password = "";
	private static String name = "test.xml";
	private Collection rootColl;
	private Database database;
		
	/**
	 * @param name
	 */
	public DOMTestJUnit(String name) {
		super(name);
	}

	/*
	 * @see TestCase#setUp()
	 */
	protected void setUp() throws Exception {
		System.out.println("Running setUp ...");

		// Starting database
		System.setProperty("exist.initdb", "true");
		Class dbc = Class.forName(driver);
		database = (Database) dbc.newInstance();
		DatabaseManager.registerDatabase(database);
		rootColl = DatabaseManager.getCollection(baseURI, "admin", "");
		// Storing an XML string
		XMLResource r =
			(XMLResource) rootColl.createResource(
				name,
				XMLResource.RESOURCE_TYPE);
		r.setContent(
			"<properties><property key=\"type\">Table</property></properties>");
		rootColl.storeResource(r);
	}

	/*
	 * @see TestCase#tearDown()
	 */
	protected void tearDown() throws Exception {
		rootColl.removeResource(rootColl.getResource(name));
		
		DatabaseManager.deregisterDatabase(database);
		DatabaseInstanceManager dim =
			(DatabaseInstanceManager) rootColl.getService(
				"DatabaseInstanceManager", "1.0");
		dim.shutdown();
		System.out.println("tearDown PASSED");
	}
	
	/** test Update of an existing document through DOM */
	public void testDOMUpdate() throws XMLDBException{
		XMLResource index = (XMLResource) rootColl.getResource(name);
		{
			System.out.println("Retrieving initial content:");
			String content = (String) index.getContent();
			System.out.println(content);
		}
		Node rootNode = index.getContentAsDOM();
		Document doc = rootNode.getOwnerDocument();
		Element schemaNode = doc.createElement("schema");
		schemaNode.setAttribute("targetNamespace", "targetNamespace");
		schemaNode.setAttribute("resourceName", "filename");
		
		rootNode.appendChild(schemaNode);
		index.setContentAsDOM(rootNode);
		rootColl.storeResource(index);

		System.out.println("Retrieving modified content:");
		index = (XMLResource) rootColl.getResource(name);
		String content = (String) index.getContent();
		System.out.println(content);
		rootNode = index.getContentAsDOM();
		Element rootElem = ((Element)rootNode);
		assertEquals( "One more element after update", 2, rootElem.getChildNodes().getLength() );
	}
}
