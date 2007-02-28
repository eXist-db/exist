/*
 * Created on 7 ao�t 2004
$Id$
 */
package org.exist.xmldb;

import java.net.BindException;
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.StandaloneServer;
import org.exist.storage.DBBroker;
import org.mortbay.util.MultiException;
import org.w3c.dom.Comment;
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
	private static String baseURI = "xmldb:exist://localhost:8088/xmlrpc" + DBBroker.ROOT_COLLECTION;
	private static String name = "test.xml";
	private Collection rootColl;
	private Database database;
		
	/**
	 * @param name
	 */
	public DOMTestJUnit(String name) {
		super(name);
	}

	protected void setUp() {
		try {
			//Don't worry about closing the server : the shutdown hook will do the job
			initServer();
			System.setProperty("exist.initdb", "true");
			Class dbc = Class.forName(driver);
			database = (Database) dbc.newInstance();
			DatabaseManager.registerDatabase(database);
			
			rootColl = DatabaseManager.getCollection(baseURI, "admin", "");
			assertNotNull(rootColl);			

			XMLResource r = (XMLResource)rootColl.createResource(name, XMLResource.RESOURCE_TYPE);
			r.setContent("<?xml-stylesheet type=\"text/xsl\" href=\"test.xsl\"?><!-- Root Comment --><properties><property key=\"type\">Table</property></properties>");
			rootColl.storeResource(r);
		} catch(Exception e) {			
			fail(e.getMessage());
		}			
	}
	
	private void initServer() {
		try {
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
		} catch(Exception e) {	
			e.printStackTrace();
			fail(e.getMessage());
		}			
	}	
	
	/** test Update of an existing document through DOM */
	public void testDOMUpdate() {
		try {
			XMLResource index = (XMLResource) rootColl.getResource(name);
			{
				System.out.println("Retrieving initial content:");
				String content = (String) index.getContent();
				System.out.println(content);
			}
            Document doc=null;
            Element root=null;
            NodeList nl=null;
			Node n = index.getContentAsDOM();
            if (n instanceof Document) { 
                doc=(Document)n;
                root=doc.getDocumentElement();
            }
            else if (n instanceof Element) {
                doc = n.getOwnerDocument();
                root=(Element)n;
            }
            else {
                fail("RemoteXMLResource unable to return a Document either an Element");
            }

            System.out.println("Retrieving root comments and PIs");
            nl = doc.getChildNodes();
            for (int i = 0; i < nl.getLength(); i++) {
                System.out.println(" * "+nl.item(i).getNodeName());
            }

            
			Element schemaNode = doc.createElement("schema");
			schemaNode.setAttribute("targetNamespace", "targetNamespace");
			schemaNode.setAttribute("resourceName", "filename");
			
			root.appendChild(schemaNode);
			index.setContentAsDOM(doc);
			rootColl.storeResource(index);
	
			System.out.println("Retrieving modified content:");
			index = (XMLResource) rootColl.getResource(name);
			String content = (String) index.getContent();
			System.out.println(content);
			n = index.getContentAsDOM();
            if (n instanceof Document) { 
                doc=(Document)n;
                root=doc.getDocumentElement();
            }
            else if (n instanceof Element) {
                doc = n.getOwnerDocument();
                root=(Element)n;
            }
			nl = root.getChildNodes();
			for (int i = 0; i < nl.getLength(); i++) {
				System.out.println(nl.item(i).getNodeName());
			}
		} catch(Exception e) {			
			fail(e.getMessage());
		}			
	}
	
	public static void main(String[] args) {
		TestRunner.run(DOMTestJUnit.class);
		//Explicit shutdown for the shutdown hook
		System.exit(0);		
	}
}
