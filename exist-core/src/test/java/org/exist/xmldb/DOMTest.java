package org.exist.xmldb;

import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.io.FastByteArrayInputStream;
import org.junit.ClassRule;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.*;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author jmv
 */
public class DOMTest {

	private static final Logger LOG =  LogManager.getLogger(DOMTest.class);

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	private static String name = "test.xml";
	
	/** 
	 * - Storing XML resource from XML string
	 * - simple XQuery
	 * - removing resource
	 * - shutdownDB with the DatabaseInstanceManager
	 */
	@Test
	public void test1() throws XMLDBException {


		CollectionManagementService cms =
			(CollectionManagementService) existEmbeddedServer.getRoot().getService(
				"CollectionManagementService",
				"1.0");
		cms.createCollection("A"); // jmv
		cms.removeCollection("A");
		cms.createCollection("A");
		Collection coll = existEmbeddedServer.getRoot().getChildCollection("A");

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
	}
	/** 
	 * - create and fill a simple document via DOM and JAXP
	 * - store it with setContentAsDOM()
	 * - simple access via getContentAsDOM()
	 * */
	@Test
	public void test2() throws XMLDBException, InstantiationException, IllegalAccessException, ClassNotFoundException, ParserConfigurationException, IOException {
		for (int i = 0; i < 2; i++) {
			XMLResource resource = (XMLResource) existEmbeddedServer.getRoot().getResource(name);
			if (resource == null) {
				resource =
					(XMLResource) existEmbeddedServer.getRoot().createResource(
						name,
						XMLResource.RESOURCE_TYPE);

				DocumentBuilderFactory dbf =
					DocumentBuilderFactory.newInstance();
				DocumentBuilder db = dbf.newDocumentBuilder();
				Document doc = db.newDocument();
				Element rootElem = doc.createElement("element");
				doc.appendChild(rootElem);

				resource.setContentAsDOM(doc);
				existEmbeddedServer.getRoot().storeResource(resource);

				resource = (XMLResource) existEmbeddedServer.getRoot().getResource(name);
			}

			String s = (String) resource.getContent();
			Node content = resource.getContentAsDOM();
		}

		existEmbeddedServer.restart();

		XMLResource resource = (XMLResource) existEmbeddedServer.getRoot().getResource(name);
		existEmbeddedServer.getRoot().removeResource(resource);
	}
	
	/** like test 2 but add attribute and text as well */
	@Test
	public void test3() throws XMLDBException, ParserConfigurationException {
		Collection coll = existEmbeddedServer.getRoot();
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

		coll = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
		resource = (XMLResource) coll.getResource(name);
		String s = (String) resource.getContent();
		Node n = resource.getContentAsDOM();

		coll.removeResource(resource);
	}

	/** like test 3 but uses the DOM as input to an (identity) XSLT transform */
	@Test
	public void test4_getContentAsString() throws XMLDBException, ParserConfigurationException, IOException, SAXException, TransformerException {
		_test4(false);
	}

	@Test
	public void test4_getContentAsDOM() throws XMLDBException, ParserConfigurationException, IOException, SAXException, TransformerException {
		_test4(true);
	}

	private void _test4(boolean getContentAsDOM) throws TransformerException, ParserConfigurationException, XMLDBException, IOException, SAXException {
		Collection coll =  existEmbeddedServer.getRoot();
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

		coll = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
		resource = (XMLResource) coll.getResource(name);

		Node n;
		if (getContentAsDOM) {
			n = resource.getContentAsDOM();
		} else {
			String s = (String) resource.getContent();
			byte[] bytes;
			bytes = s.getBytes(UTF_8);
			try(final FastByteArrayInputStream bais = new FastByteArrayInputStream(bytes)) {
				DocumentBuilder db =
						DocumentBuilderFactory.newInstance().newDocumentBuilder();
				n = db.parse(bais);
			}
		}

		Transformer t = TransformerFactory.newInstance().newTransformer();
		DOMSource source = new DOMSource(n);
		SAXResult result = new SAXResult(new DOMTest.SAXHandler());
		t.transform(source, result);

		coll.removeResource(resource);
	}

	public static class SAXHandler implements ContentHandler {
		SAXHandler() {
		}

		public void characters(char[] ch, int start, int length) {
			LOG.trace(
				"SAXHandler.characters("
					+ new String(ch)
					+ ", "
					+ start
					+ ", "
					+ length
					+ ")");
		}

		public void endDocument() {
			LOG.trace("SAXHandler.endDocument()");
		}

		public void endElement(
			String namespaceURI,
			String localName,
			String qName) {
			LOG.trace(
				"SAXHandler.endElement("
					+ namespaceURI
					+ ", "
					+ localName
					+ ", "
					+ qName
					+ ")");
		}

		public void endPrefixMapping(String prefix) {
			LOG.trace("SAXHandler.endPrefixMapping(" + prefix + ")");
		}

		public void ignorableWhitespace(char[] ch, int start, int length) {
			LOG.trace(
				"SAXHandler.ignorableWhitespace("
					+ new String(ch)
					+ ", "
					+ start
					+ ", "
					+ length
					+ ")");
		}

		public void processingInstruction(String target, String data) {
			LOG.trace(
				"SAXHandler.processingInstruction("
					+ target
					+ ", "
					+ data
					+ ")");
		}

		public void setDocumentLocator(Locator locator) {
			LOG.trace(
				"SAXHandler.setDocumentLocator(" + locator + ")");
		}

		public void skippedEntity(String name) {
			LOG.trace("SAXHandler.skippedEntity(" + name + ")");
		}

		public void startDocument() {
			LOG.trace("SAXHandler.startDocument()");
		}

		public void startElement(
			String namespaceURI,
			String localName,
			String qName,
			Attributes atts) {
			LOG.trace(
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
			LOG.trace(
				"SAXHandler.startPrefixMapping(" + prefix + ", " + xuri + ")");
		}

	}

}
