/**
 * XMLNodeAsXQueryParameterTest.java
 *
 * 2005 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */

package org.exist.xquery;

import org.exist.storage.DBBroker;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import junit.framework.TestCase;

/**
 * Class to test eXist's capability to handle XML Nodes as XQuery parameter.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class XMLNodeAsXQueryParameterTest extends TestCase {

	/** eXist database url */
	static final String eXistUrl ="xmldb:exist://";

	public static void main(String[] args) {
		junit.textui.TestRunner.run(XMLNodeAsXQueryParameterTest.class);
	}

	/**
	 * This test passes a W3C dom node as an xquery parameter to eXist and tries
	 * to read it back in:
	 * <ul>
	 * <li>Register a database instance</li>
	 * <li>Write a document to the database using the XQueryService</li>
	 * <li>Read the document from the database using XmlDB</li>
	 * <li>Check for the document content</li>
	 * </ul>
	 */
	public final void testXMLNodeAsXQueryParameter() {
		Database eXist = null;
		String document = "test.xml";

		try {
			eXist = registerDatabase();
		} catch (XMLDBException e) {
			fail("Unable to register database: "  + e.getMessage());
		}

		// Obtain XQuery service
		XQueryService service = null;
		try {
			service = getXQueryService(eXist);
			if (service == null) {
				fail("Failed to obtain xquery service instance!");
			}
		} catch (Exception e) {
			fail("Failed to obtain xquery service instance: "  + e.getMessage());
		}

		// create document
		StringBuffer xmlDocument = new StringBuffer();
		xmlDocument.append("<XmlNodeTest/>");

		// write document to the database
		try {
			store(xmlDocument.toString(), service, document);
		} catch (Exception e) {
			fail("Failed to write document to database: " + e.getMessage());
		}

		// add content using XUpdate
		StringBuffer xmlData = new StringBuffer();
		xmlData.append("<content/>");
		try {
			InputSource is = new InputSource(new StringReader(xmlData.toString()));
			DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			Document doc = docBuilder.parse(is);
			xupdate(service, doc.getFirstChild());
		} catch (SAXException e) {
			fail("Error building dom tree: " + e.getMessage());
		} catch (IOException e) {
			fail("Error reading xml: " + e.getMessage());
		} catch (ParserConfigurationException e) {
			fail("Error parsing xml: " + e.getMessage());
		} catch (FactoryConfigurationError e) {
			fail("Error receiving dom factory: " + e.getMessage());
		} catch (Exception e) {
			fail("Failed to update document in database: " + e.getMessage());
		}

		// read document back from database
		Node root = null;
		try {
			root = load(service, document);
			if (root == null) {
				fail("Document " + document + " was not found in the database!");
			}
		} catch (Exception e) {
			fail("Failed to write document to database: " + e.getMessage());
		}

		// issue xpath query
		try {
			Node node = root.getFirstChild();
			if (node != null) {
				System.out.println("Found '" + node.getNodeName() + "'");
			} else {
				fail("XUpdate:append using w3c dom node failed! Content node was not returned.");
			}
		} catch (Exception e) {
			fail("Failed to issue xpath on root node: " + e.getMessage());
		}
	}

	/**
	 * Stores the given xml fragment into the database.
	 *
	 * @param xml the xml document
	 * @param service the xquery service
	 * @param document the document name
	 * @throws XMLDBException on database error
	 */
	private final void store(String xml, XQueryService service, String document) throws XMLDBException {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("let $root := xdb:collection('" + eXistUrl + DBBroker.ROOT_COLLECTION + "', 'admin', 'admin'),");
		query.append("$doc := xdb:store($root, $document, $data)");
		query.append("return <result/>");

		service.declareVariable("document", document);
		service.declareVariable("data", xml);
		CompiledExpression cQuery = service.compile(query.toString());
		service.execute(cQuery);
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 *
	 * @param service the xquery service
	 * @param data the data node
	 * @throws XMLDBException on database error
	 */
	private final void xupdate(XQueryService service, Object data) throws XMLDBException {
		if (data == null) {
			fail("Cannot update because data is 'null'");
		}
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("declare variable $xupdate {");
		query.append("<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">");
		query.append("<xu:append select=\"xcollection('" + DBBroker.ROOT_COLLECTION + "')/XmlNodeTest\">");
		query.append("{$data}");
		query.append("</xu:append>");
		query.append("</xu:modifications>");
		query.append("};");
		query.append("let $root := xdb:collection('" + eXistUrl + DBBroker.ROOT_COLLECTION + "', \"admin\", \"admin\"),");
		query.append("$mods := xdb:update($root, $xupdate)");
		query.append("return <modifications>{$mods}</modifications>");

		service.declareVariable("data", data);
		CompiledExpression cQuery = service.compile(query.toString());
		service.execute(cQuery);
	}

	/**
	 * Loads the xml document identified by <code>document</code> from the database.
	 *
	 * @param service the xquery service
	 * @param document the document to load
	 * @throws XMLDBException on database error
	 */
	private final Node load(XQueryService service, String document) throws XMLDBException {
		StringBuffer query = new StringBuffer();
		query.append("xquery version \"1.0\";");
		query.append("let $survey := xmldb:document(concat('" + DBBroker.ROOT_COLLECTION + "', '/', $document))");
		query.append("return ($survey)");

		service.declareVariable("document", document);
		CompiledExpression cQuery = service.compile(query.toString());
		ResourceSet set = service.execute(cQuery);
		if (set != null && set.getSize() > 0) {
			return ((XMLResource)set.getIterator().nextResource()).getContentAsDOM();
		}
		return null;
	}

	/**
	 * Registers a new database instance and returns it.
	 *
	 * @throws XMLDBException
	 */
	private final Database registerDatabase() throws XMLDBException {
		Class driver = null;
		String driverName = "org.exist.xmldb.DatabaseImpl";
		try {
			driver = Class.forName(driverName);
			Database database = (Database)driver.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			return database;
		} catch (ClassNotFoundException e) {
			System.err.println("Driver class " + driverName + " was not found!");
			throw new XMLDBException();
		} catch (InstantiationException e) {
			System.err.println("Driver class " + driverName + " could not be instantiated!");
			throw new XMLDBException();
		} catch (IllegalAccessException e) {
			System.err.println("Access violation when trying to instantiate XMLDB Driver " + driverName + "!");
			throw new XMLDBException();
		}
	}

	/**
	 * Retrieves the base collection and thereof returns a reference to the collection's
	 * xquery service.
	 *
	 * @param db the database
	 * @return the xquery service
	 * @throws XMLDBException on database error
	 */
	private final XQueryService getXQueryService(Database db) throws XMLDBException {
		Collection collection = DatabaseManager.getCollection(eXistUrl + DBBroker.ROOT_COLLECTION, "admin", "admin");
		if (collection != null) {
			XQueryService service = (XQueryService)collection.getService("XQueryService", "1.0");
			collection.close();
			return service;
		}
		return null;
	}

}