/*
 * Created on 7 ao�t 2004
$Id$
 */
package org.exist.xmldb.test;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author jmv
 */
public class DOMTestJUnit extends TestCase {
	private static String driver = "org.exist.xmldb.DatabaseImpl";
	private static String baseURI = "xmldb:exist://localhost:8088/xmlrpc/db";

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
		NodeList nl = rootElem.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			System.out.println(nl.item(i).getNodeName());
		}
	}
	
	public static void main(String[] args) {
		TestRunner.run(DOMTestJUnit.class);
	}
}
