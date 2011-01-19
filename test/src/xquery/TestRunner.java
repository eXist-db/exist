package xquery;

import org.exist.Namespaces;
import org.exist.memtree.SAXAdapter;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XQueryService;
import org.junit.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.File;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.junit.Assert.fail;

public abstract class TestRunner {

	private Collection rootCollection;

    protected abstract String getDirectory();

	@Test
	public void run() {
		File dir = new File(getDirectory());
		File[] files = dir.listFiles(new XMLFilenameFilter());
		
		try {
			StringBuilder fails = new StringBuilder();
			StringBuilder results = new StringBuilder();
			XQueryService xqs = (XQueryService) rootCollection.getService("XQueryService", "1.0");
			Source query = new FileSource(new File("test/src/xquery/runTests.xql"), "UTF-8", false);
			for (File file : files) {
				Document doc = parse(file);
				
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
						fails.append("Test " + test.getAttribute("n") + "in file " + file.getName() + " failed.\n");
					}
				}
			}
			if (fails.length() > 0) {
				System.err.print(results);
				fail(fails.toString());
			}
			System.out.println(results);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		} catch (IOException e) {
			e.printStackTrace();
			fail(e.getMessage());
		} catch (SAXException e) {
			e.printStackTrace();
			fail(e.getMessage());
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@Before
	public void setUpBefore() throws Exception {
		// initialize driver
		Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
		Database database = (Database) cl.newInstance();
		database.setProperty("create-database", "true");
		DatabaseManager.registerDatabase(database);

		rootCollection =
			DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin",	null);
	}

	@After
	public void tearDownAfter() {
		if (rootCollection != null) {
			try {
				DatabaseInstanceManager dim =
				    (DatabaseInstanceManager) rootCollection.getService(
				        "DatabaseInstanceManager", "1.0");
				dim.shutdown();
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
        rootCollection = null;
	}
	
	protected static Document parse(File file) throws IOException, SAXException, ParserConfigurationException {
		SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        InputSource src = new InputSource(file.toURI().toASCIIString());
        SAXParser parser = factory.newSAXParser();
        XMLReader xr = parser.getXMLReader();
        
        SAXAdapter adapter = new SAXAdapter();
        xr.setContentHandler(adapter);
        xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
        xr.parse(src);
        
        return adapter.getDocument();
	}
}
