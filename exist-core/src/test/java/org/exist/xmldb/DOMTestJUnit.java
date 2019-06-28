/*
 * Created on 7 ao�t 2004
$Id$
 */
package org.exist.xmldb;

import org.junit.Before;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/** A test case for accessing DOMS remotely
 * @author <a href="mailto:pierrick.brihaye@free.fr">jmv
 * @author Pierrick Brihaye</a>
 */
public class DOMTestJUnit extends RemoteDBTest {
	private static String name = "test.xml";
	private Collection rootColl;
	private Database database;

	private static String getBaseURI() {
		return getUri() + XmldbURI.ROOT_COLLECTION;
	}

	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
		System.setProperty("exist.initdb", "true");
		Class<?> dbc = Class.forName(DB_DRIVER);
		database = (Database) dbc.newInstance();
		DatabaseManager.registerDatabase(database);

		rootColl = DatabaseManager.getCollection(getBaseURI(), "admin", "");
		assertNotNull(rootColl);

		XMLResource r = (XMLResource)rootColl.createResource(name, XMLResource.RESOURCE_TYPE);
		r.setContent("<?xml-stylesheet type=\"text/xsl\" href=\"test.xsl\"?><!-- Root Comment --><properties><property key=\"type\">Table</property></properties>");
		rootColl.storeResource(r);
	}
	
	/** test Update of an existing document through DOM */
	@Test
	public void domUpdate() throws XMLDBException {
		XMLResource index = (XMLResource) rootColl.getResource(name);
		String content = (String) index.getContent();
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

		nl = doc.getChildNodes();
		for (int i = 0; i < nl.getLength(); i++) {
			nl.item(i).getNodeName();
		}


		Element schemaNode = doc.createElement("schema");
		schemaNode.setAttribute("targetNamespace", "targetNamespace");
		schemaNode.setAttribute("resourceName", "filename");

		root.appendChild(schemaNode);
		index.setContentAsDOM(doc);
		rootColl.storeResource(index);

		index = (XMLResource) rootColl.getResource(name);
		content = (String) index.getContent();
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
			nl.item(i).getNodeName();
		}
	}
}
