package org.exist.xmldb;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Random;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXResult;

import junit.framework.TestCase;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import static java.nio.charset.StandardCharsets.UTF_8;

/** Reproduce the EXistException "the document is too complex/irregularily structured
 * to be mapped into eXist's numbering scheme"
 * raised in {@link org.exist.dom.DocumentImpl} .
 * It creates with DOM a simple document having a branch of 16 elements depth
 * connected to the root, with width (arity) of 16 at each level.
 */
public class IndexingTest extends TestCase {
	
	private int siblingCount;
	private int depth;
	private Node deepBranch;
	private Random random;
	
//	private static String driver = "org.exist.xmldb.DatabaseImpl";
	private static String baseURI = XmldbURI.LOCAL_DB;

	private static String username = "admin";
	private static String password = ""; // <<<
	private static String name = "test.xml";
	private String EXIST_HOME = ""; // <<<
	private int effectiveSiblingCount;
	@SuppressWarnings("unused")
	private int effectiveDepth;
	private long startTime;
	private int arity;
	private boolean randomSizes;

	/**
	 * @see junit.framework.TestCase#setUp()
	 */
	protected void setUp() {		
		siblingCount = 2;
		depth = 16;
		arity = 16;
		randomSizes = false;
		random = new Random(1234);
	}

	public IndexingTest(String arg0) {
		super(arg0);
	}
	
	public static void main(String[] args) 
	throws XMLDBException, ParserConfigurationException, 
	SAXException, IOException, ClassNotFoundException, 
	IllegalAccessException, InstantiationException {
		System.setProperty("exist.initdb", "true");
		IndexingTest tester = new IndexingTest("");
		// tester.runTestrregularilyStructured(false);
		tester.testIrregularilyStructured(true);
	}
	
	public void testIrregularilyStructured( ) 
		throws XMLDBException, ParserConfigurationException, SAXException, 
		IOException, ClassNotFoundException, InstantiationException, IllegalAccessException
 {
		
		testIrregularilyStructured( true);
	}
	
	public void testIrregularilyStructured(boolean getContentAsDOM) 
		throws XMLDBException, ParserConfigurationException, SAXException, IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
		Database database = null;
		final String testName = "IrregularilyStructured";
		startTime = System.currentTimeMillis();
		
		try {
			System.out.println("Running test " + testName + " ...");

			// Tell eXist where conf.xml is :
                        // DWES #### use Configuration.getExistHome()
			if ( EXIST_HOME != "" )
				System.setProperty("exist.home", EXIST_HOME );
			
			Class<?> dbc = Class.forName("org.exist.xmldb.DatabaseImpl");
			database = (Database) dbc.newInstance();
            database.setProperty( "create-database", "true" );
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
			effectiveSiblingCount = populate(doc);
			printTime();
			resource.setContentAsDOM(doc);
			printTime();
			coll.storeResource(resource);
			System.out.println("TEST> stored Resource " + name );
			printTime();
			coll.close();

			coll = DatabaseManager.getCollection(baseURI, username, password);
			resource = (XMLResource) coll.getResource(name);
			System.out.println("TEST> retrieved Resource " + name );
			printTime();
			
			Node n;
			if (getContentAsDOM) {
				n = resource.getContentAsDOM();
			} else {
				String s = (String) resource.getContent();
				byte[] bytes = s.getBytes(UTF_8);
				ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
				DocumentBuilder db =
					DocumentBuilderFactory.newInstance().newDocumentBuilder();
				n = db.parse(bais);
			}

			System.out.println("getContentAsDOM: " + n.getNodeName());
			Element documentElement = null;
			if ( n instanceof Element ) {
				documentElement = (Element)n;
			} else if ( n instanceof Document ) {
				documentElement = ((Document)n).getDocumentElement();
			}
			
			assertions(documentElement);
			
			coll.removeResource(resource);

			System.out.println("TEST> " + testName + " : PASSED");

		} finally {
			printTime();
			if (database != null) {
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
					printTime();
			}
		}
	}

	private void printTime() {
		System.out.println( "Current ellapsed time : " + ( System.currentTimeMillis() - startTime )/ 1000.f );
	}
	/** Assertions and output: */
	private void assertions(Element documentElement) {
		int computedSiblingCount = documentElement.getChildNodes().getLength();
		int computedDepth = ((Element)deepBranch).getElementsByTagName("element").getLength();
		int computedElementCount = 
			documentElement.getElementsByTagName("element").getLength();

		System.out.println(" documentElement.getChildNodes().getLength(): " +
			computedSiblingCount );
		System.out.println(" documentElement.getElementsByTagName(\"element\").getLength(): " +
			computedElementCount );

		assertEquals("siblingCount", effectiveSiblingCount, computedSiblingCount );
		assertEquals("depth", depth*arity + depth, computedDepth );

		System.out.println("TEST> assertions PASSED");
		printTime();
		// dumpCatabaseContent(n);
	}

	/** This one provokes the Exception */
	private int populate(Document doc) {
		int childrenCount = addChildren (doc, siblingCount);
		
		// Add a long fat branch at root's first child :
		deepBranch = doc.getDocumentElement().getFirstChild();
		effectiveDepth = addFatBranch( doc, deepBranch, depth, null);

		System.out.println("TEST> populate() done.");
		return childrenCount;
		}
	
	/** This one doesn't provoke the Exception */
	@SuppressWarnings("unused")
	private int populateOK(Document doc) {
		int childrenCount = addChildren (doc, siblingCount);
		
		// Add large branches at root's first and last children :
		addBranch ( doc, doc.getDocumentElement().getFirstChild(), depth, null);
		deepBranch = doc.getDocumentElement().getLastChild();
		effectiveDepth = addBranch ( doc, deepBranch, depth, null);
		
		Element documentElement = doc.getDocumentElement();
		
		// Add (small) branches everywhere at level 1 :
		int firstLevelWidth = doc.getDocumentElement().getChildNodes().getLength();
		{
			Node current = documentElement.getFirstChild();
			for (int j = 0; j < firstLevelWidth - 1; j++) {
				addBranch ( doc, current, 10, "branch");
				current = current.getNextSibling();
			}
		}
		
		// Add level 2 siblings everywhere at level 1 :
		{
			Node current = documentElement.getFirstChild();
			for (int j = 0; j < firstLevelWidth - 1; j++) {
				addChildren (current, arity, doc);
				current = current.getNextSibling();
			}
		}
		System.out.println("TEST> " + firstLevelWidth + " first Level elements populated.");
		return childrenCount;
	}

	private int addBranch( Document doc, Node branchNode, int depth, String elementName ) {
		int rdepth = 0;
		if ( branchNode != null ) {
			Node current = branchNode;
			if ( elementName == null || elementName == "" )
				elementName = "element";
			
			if ( randomSizes )
				rdepth = random.nextInt(depth);
			else
				rdepth  = depth;
			for (int j = 0; j < rdepth; j++) {
				Element el = doc.createElement(elementName);
				current.appendChild(el);
				current = el;
			}
		}
		return rdepth;
	}

	private int addFatBranch( Document doc, Node branchNode, int depth, String elementName ) {
		int rdepth = 0;
		if ( branchNode != null ) {
			Node current = branchNode;
			if ( elementName == null || elementName == "" )
				elementName = "element";
			
			if ( randomSizes )
				rdepth = random.nextInt(depth);
			else
				rdepth  = depth;
			for (int j = 0; j < rdepth; j++) {
				Element el = doc.createElement(elementName);
				addChildren( el,  arity, doc );
				current.appendChild(el);
				current = el;
			}
		}
		return rdepth;
	}
	
	/**
	 * @param doc
	 * @param i
	 */
	private int addChildren(Document doc, int length ) {
		Element rootElem = doc.createElement("root");
		doc.appendChild(rootElem);
		return addChildren(rootElem, length, doc);
	}

	private int addChildren( Node rootElem, int length, Document doc) {
		int rlength = 0;
		if ( rootElem != null ) {
			if ( randomSizes )
				rlength = random.nextInt(length);
			else
				rlength = length;
			for (int j = 0; j < rlength; j++) {
				Element el = doc.createElement("element");
				rootElem.appendChild(el);			
			}
		}
		return rlength;
	}
	
	@SuppressWarnings("unused")
	private void dumpCatabaseContent(Node n) {
		try {
			Transformer t = TransformerFactory.newInstance().newTransformer();
			DOMSource source = new DOMSource(n);
			SAXResult result = new SAXResult(new IndexingTest.SAXHandler());
			t.transform(source, result);
        } catch (Exception e) {            
            fail(e.getMessage()); 
		}			
	}
	
	class SAXHandler implements ContentHandler {
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

