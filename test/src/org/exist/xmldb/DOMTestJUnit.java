/*
 * Created on 7 ao�t 2004
$Id$
 */
package org.exist.xmldb;

import junit.textui.TestRunner;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.modules.XMLResource;

/** A test case for accessing DOMS remotely
 * @author jmv
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class DOMTestJUnit extends RemoteDBTest {
	
    // jetty.port.standalone
	private static String baseURI = "xmldb:exist://localhost:" + System.getProperty("jetty.port") + "/xmlrpc" + XmldbURI.ROOT_COLLECTION;
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
			//Don't worry about closing the server : the shutdownDB hook will do the job
			initServer();
			System.setProperty("exist.initdb", "true");
			Class<?> dbc = Class.forName(DB_DRIVER);
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
		//Explicit shutdownDB for the shutdownDB hook
		System.exit(0);		
	}
}
