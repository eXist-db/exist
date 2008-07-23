package org.exist.xupdate;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.exist.storage.DBBroker;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.Indexer;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;
import org.xml.sax.InputSource;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * @author berlinge-to
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class XUpdateTest {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
	private final static String XUPDATE_COLLECTION = "xupdate_tests";

        static File existDir;
        static {
           String existHome = System.getProperty("exist.home");
           existDir = existHome==null ? new File(".") : new File(existHome);
        }
	private final static String MODIFICATION_DIR =
		(new File(existDir,"test/src/org/exist/xupdate/modifications")).getAbsolutePath();
	private final static String RESTULT_DIR =
		(new File(existDir,"test/src/org/exist/xupdate/results")).getAbsolutePath();
	private final static String SOURCE_DIR = (new File(existDir,"test/src/org/exist/xupdate/input")).getAbsolutePath();

	private final static String XUPDATE_FILE = "xu.xml";       // xlm document name in eXist

	private Collection col = null;

	/**
	 * Constructor for xupdate.
	 */
	public XUpdateTest() {
		setUp();
	}

	public void setUp() {
		try {
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			col = DatabaseManager.getCollection(URI + "/" + XUPDATE_COLLECTION);
			if (col == null) {
				Collection root = DatabaseManager.getCollection(URI);
				CollectionManagementService mgtService =
					(CollectionManagementService) root.getService(
						"CollectionManagementService",
						"1.0");
				col = mgtService.createCollection(XUPDATE_COLLECTION);
				System.out.println("collection created.");
			}
            Configuration config = BrokerPool.getInstance().getConfiguration();
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.FALSE);
        } catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void doTest(String testName, String sourceFile) throws Exception {
		addDocument(sourceFile);

		//update input xml file
        Document xupdateResult = updateDocument(MODIFICATION_DIR + "/" + testName + ".xml");
		removeWhiteSpace(xupdateResult);
		
        //Read reference xml file
        DocumentBuilderFactory parserFactory =
			DocumentBuilderFactory.newInstance();
		parserFactory.setNamespaceAware(true);
		DocumentBuilder builder = parserFactory.newDocumentBuilder();
		Document referenceXML = builder.parse(RESTULT_DIR + "/" + testName + ".xml");
		removeWhiteSpace(referenceXML);
        
		//compare
		System.out.println("\n");
        new CompareDocuments().compare(referenceXML, xupdateResult);
        
		removeDocument();
	}

	/*
	 * helperfunctions
	 * 
	 */
	public void addDocument(String sourceFile) throws Exception {
		XMLResource document =
			(XMLResource) col.createResource(XUPDATE_FILE, "XMLResource");
		File f = new File(SOURCE_DIR + "/" + sourceFile);
		if (!f.canRead())
			System.err.println("can't read file " + sourceFile);
		document.setContent(f);
		col.storeResource(document);
		System.out.println("document stored.");
	}

	public void removeDocument() throws Exception {
		Resource document = col.getResource(XUPDATE_FILE);
		col.removeResource(document);
		System.out.println("document removed.");
	}

	private Document updateDocument(String updateFile) throws Exception {
		XUpdateQueryService service =
			(XUpdateQueryService) col.getService("XUpdateQueryService", "1.0");

		// Read XUpdate-Modifcations
		System.out.println("update file: " + updateFile);
		File file = new File(updateFile);
		BufferedReader br = new BufferedReader(new FileReader(file));
		char[] characters = new char[new Long(file.length()).intValue()];
		br.read(characters, 0, new Long(file.length()).intValue());
		br.close();
		String xUpdateModifications = new String(characters);
		System.out.println("modifications: " + xUpdateModifications);
		//

		service.update(xUpdateModifications);

		//col.setProperty("pretty", "true");
		//col.setProperty("encoding", "UTF-8");
		XMLResource ret = (XMLResource) col.getResource(XUPDATE_FILE);
		String xmlString = ((String) ret.getContent());
		System.out.println("Result:");
		System.out.println(xmlString);

		// convert xml string to dom
		// todo: make it nicer
		DocumentBuilderFactory parserFactory =
			DocumentBuilderFactory.newInstance();
		parserFactory.setNamespaceAware(true);
		InputSource in =
			new InputSource(
				(InputStream) new ByteArrayInputStream(xmlString.getBytes()));
		DocumentBuilder builder = parserFactory.newDocumentBuilder();
		return builder.parse(in);
	}
    
	private void removeWhiteSpace(Document document) throws Exception {
		DocumentTraversal dt = (DocumentTraversal) document;
		NodeIterator nodeIterator =
			dt.createNodeIterator(document, NodeFilter.SHOW_TEXT, null, true);
		Node node = nodeIterator.nextNode();
		while (node != null) {
			if (node.getNodeValue().trim().compareTo("") == 0) {
				node.getParentNode().removeChild(node);
			}
			node = nodeIterator.nextNode();
		}
	}

}
