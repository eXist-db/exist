package org.exist.xmldb;

import java.io.File;
import java.io.StringReader;
import java.io.StringWriter;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import junit.framework.TestCase;

import org.apache.xml.serialize.OutputFormat;
import org.apache.xml.serialize.XMLSerializer;
import org.exist.storage.DBBroker;
import org.exist.util.XMLFilenameFilter;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceIterator;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

public class ResourceTest extends TestCase {

	private final static String URI = "xmldb:exist://" + DBBroker.ROOT_COLLECTION;
	private final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

	/**
	 * Constructor for XMLDBTest.
	 * @param arg0
	 */
	public ResourceTest(String arg0) {
		super(arg0);
	}
	
	public void testReadNonExistingResource() {
		try {
			Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			Resource nonExistent = testCollection.getResource("12345.xml");
			assertNull(nonExistent);
		} catch(Exception e) {
			System.out.println("testReadNonExistingResource(): Exception: " + e);
            e.printStackTrace();
			fail(e.getMessage());
		}
	}
	
	public void testReadResource() {
		try {
			Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
			String[] resources = testCollection.listResources();
			assertEquals(resources.length, testCollection.getResourceCount());

			System.out.println("reading " + resources[0]);
			XMLResource doc = (XMLResource) testCollection.getResource(resources[0]);
			assertNotNull(doc);

			System.out.println("testing XMLResource.getContentAsSAX()");
			StringWriter sout = new StringWriter();
			OutputFormat format = new OutputFormat("xml", "ISO-8859-1", true);
			format.setLineWidth(60);
			XMLSerializer xmlout = new XMLSerializer(sout, format);
			doc.getContentAsSAX(xmlout);
			System.out.println("----------------------------------------");
			System.out.println(sout.toString());
			System.out.println("----------------------------------------");
		} catch (Exception e) {
			System.out.println("testReadResource(): Exception: " + e);
            e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testReadDOM() {
		try {
			Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);

			XMLResource doc = (XMLResource) testCollection.getResource("r_and_j.xml");
			assertNotNull(doc);
			Node n = doc.getContentAsDOM();
            Element elem=null;
            if ( n instanceof Element ) {
                elem = (Element)n;
            } else if ( n instanceof Document ) {
                elem = ((Document)n).getDocumentElement();
            }
			assertNotNull(elem);
			assertEquals(elem.getNodeName(), "PLAY");
			System.out.println("Root element: " + elem.getNodeName());
			NodeList children = elem.getChildNodes();
			Node node;
			for (int i = 0; i < children.getLength(); i++) {
				node = children.item(i);
				System.out.println("Child: " + node.getNodeName());
				assertNotNull(node);
				node = node.getFirstChild();
				while(node != null) {
					System.out.println("child: " + node.getNodeName());
					node = node.getNextSibling();
				}
			}
		} catch (XMLDBException e) {
            System.out.println("testReadDOM(): Exception: " + e);
            e.printStackTrace();
			fail(e.getMessage());
		}
	}

	public void testSetContentAsSAX() {
		try {
			Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);

			XMLResource doc =
				(XMLResource) testCollection.createResource("test.xml", "XMLResource");
			String xml =
				"<test><title>Title</title>"
					+ "<para>Paragraph1</para>"
					+ "<para>Paragraph2</para>"
					+ "</test>";
			ContentHandler handler = doc.setContentAsSAX();
			SAXParserFactory saxFactory = SAXParserFactory.newInstance();
			saxFactory.setNamespaceAware(true);
			saxFactory.setValidating(false);
			SAXParser sax = saxFactory.newSAXParser();
			XMLReader reader = sax.getXMLReader();
			reader.setContentHandler(handler);
			reader.parse(new InputSource(new StringReader(xml)));
			testCollection.storeResource(doc);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	public void testSetContentAsDOM() {
		try {
			Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);

			XMLResource doc = (XMLResource) testCollection.createResource("dom.xml", "XMLResource");
			String xml =
				"<test><title>Title</title>"
					+ "<para>Paragraph1</para>"
					+ "<para>Paragraph2</para>"
					+ "</test>";
			DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder builder = docFactory.newDocumentBuilder();
			Document dom = builder.parse(new InputSource(new StringReader(xml)));
			doc.setContentAsDOM(dom.getDocumentElement());
			testCollection.storeResource(doc);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	public void testQueryRemoveResource() {
		Resource resource = null;
        try {
        	Collection testCollection = DatabaseManager.getCollection(URI + "/test");
			assertNotNull(testCollection);
            String resourceName = "QueryTestPerson.xml";
            String id = "test." + System.currentTimeMillis();
            String content = "<?xml version='1.0'?><person id=\"" + id + "\"><name>Jason</name></person>";
            resource = testCollection.createResource(resourceName, "XMLResource");
            resource.setContent(content);
            testCollection.storeResource(resource);

            XPathQueryService service = (XPathQueryService) testCollection.getService("XPathQueryService", "1.0");
            ResourceSet rs = service.query("/person[@id='" + id + "']");

            for (ResourceIterator iterator = rs.getIterator(); iterator.hasMoreResources();) {
                Resource r = iterator.nextResource();
                System.err.println("Resource id=" + r.getId() + " xml=" + r.getContent());
                testCollection.removeResource(r);
                resource = null;
            }
        } catch (XMLDBException xe) {
            System.err.println("Unexpected Exception occured: " + xe.getMessage());
            xe.printStackTrace();
        }
	}
	
	public void testAddRemove() {
		try {
			final String resourceID = "addremove.xml";

			XMLResource created = addResource(resourceID, xmlForTest());
			assertNotNull(created);
			// need to test documents xml structure			

			XMLResource located = resourceForId(resourceID);
			assertNotNull(located);
			//assertEquals((String) created.getContent(), (String) located.getContent());

			removeDocument(resourceID);
			XMLResource locatedAfterRemove = resourceForId(resourceID);
			assertNull(locatedAfterRemove);
		} catch (Exception e) {
			fail(e.getMessage());
		}
	}

	private void removeDocument(String id) {		
		try {
			XMLResource resource = resourceForId(id);
	
			if (null != resource) {
				Collection collection = null;
	
				try {
					collection = DatabaseManager.getCollection(URI + "/test");
					collection.removeResource(resource);
				} finally {
					closeCollection(collection);
				}
			}
		} catch (Exception e) {			
			fail(e.getMessage());
		}			
	}

	private XMLResource addResource(String id, String content) {
		Collection collection = null;
		XMLResource result = null;

		try {
			collection = DatabaseManager.getCollection(URI + "/test");
			result = (XMLResource) collection.createResource(id, XMLResource.RESOURCE_TYPE);
			result.setContent(content);
			collection.storeResource(result);
		} catch (Exception e) {
			fail(e.getMessage());					
		} finally {
			closeCollection(collection);
		}

		return result;
	}

	private XMLResource resourceForId(String id) {
		Collection collection = null;
		XMLResource result = null;

		try {
			collection = DatabaseManager.getCollection(URI + "/test");
			result = (XMLResource) collection.getResource(id);
		} catch (Exception e) {
			fail(e.getMessage());	
		} finally {
			closeCollection(collection);
		}
		
		return result;
	}

	private void closeCollection(Collection collection) {
		try {
			if (null != collection) {
				collection.close();
			}
		} catch (Exception e) {
			fail(e.getMessage());
		}			
	}

	private String xmlForTest() {
		return "<test><title>Title</title>"
			+ "<para>Paragraph1</para>"
			+ "<para>Paragraph2</para>"
			+ "</test>";
	}
	
	protected void setUp() {
		try {
			// initialize driver
			Class cl = Class.forName(DRIVER);
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
            Collection root = DatabaseManager.getCollection(URI);
            CollectionManagementService service =
                (CollectionManagementService) root.getService(
                    "CollectionManagementService",
                    "1.0");
            assertNotNull(service);
            Collection testCollection = service.createCollection("test");
            assertNotNull(testCollection);
            
            String directory = "samples/shakespeare";
            String existHome = System.getProperty("exist.home");
            File existDir = existHome==null ? new File(".") : new File(existHome);
            File dir = new File(existDir,directory);
            File files[] = dir.listFiles(new XMLFilenameFilter());

            for (int i = 0; i < files.length; i++) {
                XMLResource res = (XMLResource) testCollection.createResource(files[i].getName(), "XMLResource");
                res.setContent(files[i]);
                testCollection.storeResource(res);
            }
		} catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
		}
	}
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ResourceTest.class);
	}
}