package xquery;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.Namespaces;
import org.exist.memtree.SAXAdapter;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.XMLReader;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

public class TestRunnerMain {

	private static Collection rootCollection;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		init();
		
		runTests(args);
		
		shutdown();
	}

	private static void runTests(String[] files) {
		try {
			StringBuilder results = new StringBuilder();
			XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
			Source query = new FileSource(new File("test/src/xquery/runTests.xql"), "UTF-8", false);
			for (String fileName : files) {
				File file = new File(fileName);
				if (!file.canRead()) {
					System.console().printf("Test file not found: %s\n", fileName);
					return;
				}
				
				Document doc = TestRunner.parse(file);
				
				xqs.declareVariable("doc", doc);
				ResourceSet result = xqs.execute(query);
				XMLResource resource = (XMLResource) result.getResource(0);
                results.append(resource.getContent()).append('\n');
				Element root = (Element) resource.getContentAsDOM();
				NodeList tests = root.getElementsByTagName("test");
				for (int i = 0; i < tests.getLength(); i++) {
					Element test = (Element) tests.item(i);
					String passed = test.getAttribute("pass");
					if (passed.equals("false")) {
						System.err.println(resource.getContent());
						return;
					}
				}
			}
			System.out.println(results);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void init() {
		// initialize driver
		try {
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
			
			rootCollection = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	private static void shutdown() {
		if (rootCollection != null) {
			try {
				DatabaseInstanceManager dim =
				    (DatabaseInstanceManager) rootCollection.getService("DatabaseInstanceManager", "1.0");
				dim.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}
}
