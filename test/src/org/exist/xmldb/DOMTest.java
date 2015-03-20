package org.exist.xmldb;

import java.io.ByteArrayInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jmv
 */
public class DOMTest {

	private static String driver = "org.exist.xmldb.DatabaseImpl";
	private static String baseURI = XmldbURI.LOCAL_DB;

	private static String username = "admin";
	private static String password = "";
	private static String name = "test.xml";

	public static void main(String[] args) {
		System.setProperty("exist.initdb", "true");
		DOMTest tester = new DOMTest();
		tester.runTest1();
		tester.runTest2();
		tester.runTest3();
		tester.runTest4(false);
		tester.runTest4(true);
	}
	
	/** 
	 * - Storing XML resource from XML string
	 * - simple XQuery
	 * - removing resource
	 * - shutdownDB with the DatabaseInstanceManager
	 */
	public void runTest1() {
		try {
			Class<?> dbc = Class.forName(driver);
			Database database = (Database) dbc.newInstance();
			DatabaseManager.registerDatabase(database);
			Collection rootColl =
				DatabaseManager.getCollection(baseURI, "admin", "");
			CollectionManagementService cms =
				(CollectionManagementService) rootColl.getService(
					"CollectionManagementService",
					"1.0");
			cms.createCollection("A"); // jmv
			cms.removeCollection("A");
			cms.createCollection("A");
			Collection coll = rootColl.getChildCollection("A");

			XMLResource r =
				(XMLResource) coll.createResource(
					name,
					XMLResource.RESOURCE_TYPE);
			r.setContent(
				"<properties><property key=\"type\">Table</property></properties>");
			coll.storeResource(r);

			XPathQueryService xpqs =
				(XPathQueryService) coll.getService("XPathQueryService", "1.0");
			ResourceSet rs =
				xpqs.query(
					"//properties[property[@key='type' and text()='Table']]");
			for (ResourceIterator i = rs.getIterator();
				i.hasMoreResources();
				) {
				r = (XMLResource) i.nextResource();
				String s = (String) r.getContent();
				Node content = r.getContentAsDOM();
				coll.removeResource(r);
			}

			cms.removeCollection("A");
			DatabaseManager.deregisterDatabase(database);
			DatabaseInstanceManager dim =
				(DatabaseInstanceManager) rootColl.getService(
					"DatabaseInstanceManager",
					"1.0");
			dim.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	/** 
	 * - create and fill a simple document via DOM and JAXP
	 * - store it with setContentAsDOM()
	 * - simple access via getContentAsDOM()
	 * */
	public void runTest2() {
		try {
			for (int i = 0; i < 2; i++) {
				Class<?> dbc = Class.forName(driver);
				Database database = (Database) dbc.newInstance();
				DatabaseManager.registerDatabase(database);

				Collection coll =
					DatabaseManager.getCollection(baseURI, username, password);
				XMLResource resource = (XMLResource) coll.getResource(name);
				if (resource == null) {
					resource =
						(XMLResource) coll.createResource(
							name,
							XMLResource.RESOURCE_TYPE);

					DocumentBuilderFactory dbf =
						DocumentBuilderFactory.newInstance();
					DocumentBuilder db = dbf.newDocumentBuilder();
					Document doc = db.newDocument();
					Element rootElem = doc.createElement("element");
					doc.appendChild(rootElem);

					resource.setContentAsDOM(doc);
					coll.storeResource(resource);

					coll =
						DatabaseManager.getCollection(
							baseURI,
							username,
							password);
					resource = (XMLResource) coll.getResource(name);
				}

				String s = (String) resource.getContent();
				Node content = resource.getContentAsDOM();

				DatabaseManager.deregisterDatabase(database);
				DatabaseInstanceManager dim =
					(DatabaseInstanceManager) coll.getService(
						"DatabaseInstanceManager",
						"1.0");
				dim.shutdown();
			}

			Class<?> dbc = Class.forName(driver);
			Database database = (Database) dbc.newInstance();
			DatabaseManager.registerDatabase(database);
			Collection coll =
				DatabaseManager.getCollection(baseURI, username, password);
			XMLResource resource = (XMLResource) coll.getResource(name);
			coll.removeResource(resource);
			DatabaseManager.deregisterDatabase(database);
			DatabaseInstanceManager dim =
				(DatabaseInstanceManager) coll.getService(
					"DatabaseInstanceManager",
					"1.0");
			dim.shutdown();

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	/** like test 2 but add attribute and text as well */
	public void runTest3() {
		try {

			Class<?> dbc = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) dbc.newInstance();
			DatabaseManager.registerDatabase(database);

			Collection coll =
				DatabaseManager.getCollection(baseURI, username, password);
			XMLResource resource =
				(XMLResource) coll.createResource(
					name,
					XMLResource.RESOURCE_TYPE);

			Document doc =
				DocumentBuilderFactory
					.newInstance()
					.newDocumentBuilder()
					.newDocument();
			Element rootElem = doc.createElement("element");
			Element propertyElem = doc.createElement("property");
			propertyElem.setAttribute("key", "value");
			propertyElem.appendChild(doc.createTextNode("text"));
			rootElem.appendChild(propertyElem);
			doc.appendChild(rootElem);
			resource.setContentAsDOM(doc);

			coll.storeResource(resource);
			coll.close();

			coll = DatabaseManager.getCollection(baseURI, username, password);
			resource = (XMLResource) coll.getResource(name);
			String s = (String) resource.getContent();
			Node n = resource.getContentAsDOM();

			coll.removeResource(resource);

			DatabaseManager.deregisterDatabase(database);
			DatabaseInstanceManager dim =
				(DatabaseInstanceManager) coll.getService(
					"DatabaseInstanceManager",
					"1.0");
			dim.shutdown();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** like test 3 but uses the DOM as input to an (identity) XSLT transform */
	public void runTest4(boolean getContentAsDOM) {
		Database database = null;
		try {

			Class<?> dbc = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) dbc.newInstance();
			DatabaseManager.registerDatabase(database);

			Collection coll =
				DatabaseManager.getCollection(baseURI, username, password);
			XMLResource resource =
				(XMLResource) coll.createResource(
					name,
					XMLResource.RESOURCE_TYPE);

			Document doc =
				DocumentBuilderFactory
					.newInstance()
					.newDocumentBuilder()
					.newDocument();
			Element rootElem = doc.createElement("element");
			Element propertyElem = doc.createElement("property");
			propertyElem.setAttribute("key", "value");
			propertyElem.appendChild(doc.createTextNode("text"));
			rootElem.appendChild(propertyElem);
			doc.appendChild(rootElem);
			resource.setContentAsDOM(doc);

			coll.storeResource(resource);
			coll.close();

			coll = DatabaseManager.getCollection(baseURI, username, password);
			resource = (XMLResource) coll.getResource(name);

			Node n;
			if (getContentAsDOM) {
				n = resource.getContentAsDOM();
			} else {
				String s = (String) resource.getContent();
				byte[] bytes;
				bytes = s.getBytes(UTF_8);
				ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder();
				n = db.parse(bais);
			}

			Transformer t = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(n);
			SAXResult result = new SAXResult(new DOMTest.SAXHandler());
			t.transform(source, result);

			coll.removeResource(resource);

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (database != null) {
				try {
					Collection coll =
						DatabaseManager.getCollection(
							baseURI,
							username,
							password);
					DatabaseManager.deregisterDatabase(database);
					DatabaseInstanceManager dim =
						(DatabaseInstanceManager) coll.getService(
							"DatabaseInstanceManager",
							"1.0");
					dim.shutdown();
				} catch (Exception e) {
                    e.printStackTrace();
				}
			}
		}
	}

	public static class SAXHandler implements ContentHandler {
		SAXHandler() {
		}

		public void characters(char[] ch, int start, int length) {
			System.out.println(
				"SAXHandler.characters("
					+ new String(ch)
					+ ", "
					+ start
					+ ", "
					+ length
					+ ")");
		}

		public void endDocument() {
			System.out.println("SAXHandler.endDocument()");
		}

		public void endElement(
			String namespaceURI,
			String localName,
			String qName) {
			System.out.println(
				"SAXHandler.endElement("
					+ namespaceURI
					+ ", "
					+ localName
					+ ", "
					+ qName
					+ ")");
		}

		public void endPrefixMapping(String prefix) {
			System.out.println("SAXHandler.endPrefixMapping(" + prefix + ")");
		}

		public void ignorableWhitespace(char[] ch, int start, int length) {
			System.out.println(
				"SAXHandler.ignorableWhitespace("
					+ new String(ch)
					+ ", "
					+ start
					+ ", "
					+ length
					+ ")");
		}

		public void processingInstruction(String target, String data) {
			System.out.println(
				"SAXHandler.processingInstruction("
					+ target
					+ ", "
					+ data
					+ ")");
		}

		public void setDocumentLocator(Locator locator) {
			System.out.println(
				"SAXHandler.setDocumentLocator(" + locator + ")");
		}

		public void skippedEntity(String name) {
			System.out.println("SAXHandler.skippedEntity(" + name + ")");
		}

		public void startDocument() {
			System.out.println("SAXHandler.startDocument()");
		}

		public void startElement(
			String namespaceURI,
			String localName,
			String qName,
			Attributes atts) {
			System.out.println(
				"SAXHandler.startElement("
					+ namespaceURI
					+ ", "
					+ localName
					+ ", "
					+ qName
					+ ","
					+ atts
					+ ")");
		}

		public void startPrefixMapping(String prefix, String xuri) {
			System.out.println(
				"SAXHandler.startPrefixMapping(" + prefix + ", " + xuri + ")");
		}

	}

}
