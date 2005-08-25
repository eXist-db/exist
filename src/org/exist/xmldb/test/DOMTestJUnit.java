/*
 * Created on 7 ao�t 2004
$Id$
 */
package org.exist.xmldb.test;

import java.net.BindException;
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.StandaloneServer;
import org.mortbay.util.MultiException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/** A test case for accessing DOMS remotely
 * @author jmv
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class DOMTestJUnit extends TestCase {
	
	private static StandaloneServer server = null;
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

	protected void setUp() throws Exception {
		//Don't worry about closing the server : the shutdown hook will do the job
		initServer();
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
	
	private void initServer() throws Exception {
		if (server == null) {
			server = new StandaloneServer();
			if (!server.isStarted()) {			
				try {				
					System.out.println("Starting standalone server...");
					String[] args = {};
					server.run(args);
					while (!server.isStarted()) {
						Thread.sleep(1000);
					}
				} catch (MultiException e) {
					boolean rethrow = true;
					Iterator i = e.getExceptions().iterator();
					while (i.hasNext()) {
						Exception e0 = (Exception)i.next();
						if (e0 instanceof BindException) {
							System.out.println("A server is running already !");
							rethrow = false;
							break;
						}
					}
					if (rethrow) throw e;
				}
			}
		}
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
		//Explicit shutdown for the shutdown hook
		System.exit(0);		
	}
}
