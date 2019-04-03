/**
 * XMLNodeAsXQueryParameterTest.java
 *
 * 2005 by O2 IT Engineering
 * Zurich,  Switzerland (CH)
 */

package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.fail;

/**
 * Class to test eXist's capability to handle XML Nodes as XQuery parameter.
 *
 * @author Tobias Wunden
 * @version 1.0
 */

public class XMLNodeAsXQueryParameterTest {

	@ClassRule
	public final static ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

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
	@Test
	public final void xmlNodeAsXQueryParameter() throws XMLDBException, ParserConfigurationException, IOException, SAXException, IllegalAccessException, InstantiationException, ClassNotFoundException {
		final String document = "test.xml";

		// create document
		final String xmlDocument = "<XmlNodeTest/>";

		// write document to the database
        store(xmlDocument, document);


		// add content using XUpdate
		final String xmlData = "<content/>";
		try(final Reader reader = new StringReader(xmlData)) {
			final InputSource is = new InputSource(reader);
			final DocumentBuilder docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
			final Document doc = docBuilder.parse(is);
			xupdate(doc.getFirstChild());

			// read document back from database
			final Node root = load(document);
			if (root == null) {
				fail("Document " + document + " was not found in the database!");
			}

			// issue xpath query
			final Node node = root.getFirstChild();
			if (node == null) {
				fail("XUpdate:append using w3c dom node failed! Content node was not returned.");
			}
		}
	}

	/**
	 * Stores the given xml fragment into the database.
	 *
	 * @param xml the xml document
	 * @param document the document name
	 * @throws XMLDBException on database error
	 */
	private void store(final String xml, final String document) throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("declare variable $local:document external;");
		query.append("declare variable $local:data external;");
		query.append("let $loggedIn := xmldb:login(\"" + XmldbURI.ROOT_COLLECTION + "\", \"admin\", \"\"),");
		query.append("$doc := xmldb:store(\"" + XmldbURI.ROOT_COLLECTION + "\", $local:document, $local:data)");
		query.append("return <result/>");

		final Map<String, Object> externalVariables = new HashMap<>();
		externalVariables.put("local:document", document);
		externalVariables.put("local:data", xml);
		existEmbeddedServer.executeQuery(query.toString(), externalVariables);
	}

	/**
	 * Updates the given xml fragment in the database using XUpdate.
	 *
	 * @param data the data node
	 * @throws XMLDBException on database error
	 */
	private void xupdate(final Object data) throws XMLDBException {
		if (data == null) {
			fail("Cannot update because data is 'null'");
		}

		final StringBuilder query = new StringBuilder();
		query.append("xquery version \"1.0\";");
		query.append("declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";");
		query.append("declare variable $local:data external;");
		query.append("declare variable $xupdate {");
		query.append("<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">");
		query.append("<xu:append select=\"xmldb:xcollection('" + XmldbURI.ROOT_COLLECTION + "')/XmlNodeTest\">");
		query.append("{$local:data}");
		query.append("</xu:append>");
		query.append("</xu:modifications>");
		query.append("};");
		query.append("let $isLoggedIn := xmldb:login('" + XmldbURI.ROOT_COLLECTION + "', \"admin\", \"\"),");
		query.append("$mods := xmldb:update(\"" + XmldbURI.ROOT_COLLECTION + "\", $xupdate)");
		query.append("return <modifications>{$mods}</modifications>");

		final Map<String, Object> externalVariables = new HashMap<>();
		externalVariables.put("local:data", data);
		existEmbeddedServer.executeQuery(query.toString(), externalVariables);
	}

	/**
	 * Loads the xml document identified by <code>document</code> from the database.
	 *
	 * @param document the document to load
	 * @throws XMLDBException on database error
	 */
	private Node load(final String document) throws XMLDBException {
		final StringBuilder query = new StringBuilder();
		query.append("xquery version \"1.0\";");
		query.append("declare variable $local:document external;");
		query.append("let $survey := doc(string-join(('" + XmldbURI.ROOT_COLLECTION + "', $local:document), '/'))");
		query.append("return $survey");

		final Map<String, Object> externalVariables = new HashMap<>();
		externalVariables.put("local:document", document);
		final ResourceSet results = existEmbeddedServer.executeQuery(query.toString(), externalVariables);
		if (results != null && results.getSize() > 0) {
			return ((XMLResource)results.getIterator().nextResource()).getContentAsDOM();
		}
		return null;
	}
}
