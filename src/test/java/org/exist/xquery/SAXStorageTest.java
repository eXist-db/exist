/*
 * Created on 23 juin 2004
$Id$
 */
package org.exist.xquery;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.LocalCollection;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;

/**
 * This test case is for direct storage of SAX events in the database; one has to implement an XMLReader.
 * It is also a stress test that creates large documents using SAX, use main() for this.               
 * @author jmv
 */
public class SAXStorageTest {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

	private XMLResource doc;
	private Collection testCollection;
	private static String FILE_STORED;

	@Before
	public void setUp() throws ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        final CollectionManagementService service =
            (CollectionManagementService)  existEmbeddedServer.getRoot().getService(
                "CollectionManagementService",
                "1.0");
        testCollection = service.createCollection("test");
        FILE_STORED = "big.xml";
        doc = (XMLResource) testCollection.createResource(FILE_STORED, "XMLResource");
		testCollection.storeResource(doc);
	}

    @After
    public void cleanup() throws XMLDBException {
		final CollectionManagementService service =
				(CollectionManagementService) existEmbeddedServer.getRoot().getService(
						"CollectionManagementService",
						"1.0");
		service.removeCollection("test");
		testCollection = null;
    }

	/**
	 * @param xquery
	 * @param mess
	 * @return TODO	 
	 */
	private ResourceSet querySingleLine(String xquery, String mess) throws XMLDBException {
		// query a single line:
		XPathQueryService service =
			(XPathQueryService) testCollection.getService(
					"XPathQueryService", 	"1.0");
		ResourceSet result = null;
		if (!xquery.isEmpty()) {
			// xquery = "/*/*[2]";
			long t0 = System.currentTimeMillis();
			result = service.queryResource( "big.xml", xquery );
			// assertEquals(1, result.getSize());
			long t1 = System.currentTimeMillis();
		}
		return result;
	}
	
	/** Store in the "classical" eXist way: the XMLResource stores an XML string before
	 * storeResource() stores it in the database.
	 */
    @Test
	public void queryStoreContentAsSAX() throws XMLDBException, SAXException {
        ContentHandler databaseInserter = doc.setContentAsSAX();
        (new TabularXMLReader()).writeDocument(databaseInserter);
        testCollection.storeResource(doc);
        querySingleLine("", "testQueryStoreContentAsSAX");
	}

	/** Store in the new way: the XMLResource stores just a File object before
	 * storeResource() stores the SAX events in the database.
	 * @throws XMLDBException
*/
    @Test
	public void queryBigDocument() throws XMLDBException {
        XMLReader dataSource = new TabularXMLReader();
        storeSAXEvents(dataSource);
        ResourceSet result = querySingleLine("", "testQueryBigDocument");
        assertEquals(1, result.getSize());
	}

	/**
	 * @param dataSource
	 * @throws XMLDBException
	 */
	private void storeSAXEvents(XMLReader dataSource) throws XMLDBException {
        if ( testCollection instanceof LocalCollection ) {
            long t0 = System.currentTimeMillis();
            LocalCollection coll = (LocalCollection)testCollection;
            coll.setReader(dataSource);
			String existHome = System.getProperty("exist.home");
			Path existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
			existDir = existDir.normalize();
            doc.setContent(existDir.resolve(FILE_STORED));
            coll.storeResource(doc);
        }
	}

	/** arguments: lines , columns, XQuery string */
	public static void main(String[] args) throws ClassNotFoundException, InstantiationException, XMLDBException, IllegalAccessException {
		String xquery = "";
		int lines = 20; int columns = 20;
		if ( args.length >= 2 ) {
			lines = Integer.parseInt(args[0]);
			columns = Integer.parseInt(args[1]);
		}
		if ( args.length == 3 ) {
			xquery = args[2];
		}
		if ( args.length < 2 ) {
			System.out.println("Taking default values");
		}
		
		SAXStorageTest tester = new SAXStorageTest();
		tester.setUp();
		XMLReader dataSource = new TabularXMLReader( lines , columns);
		tester.storeSAXEvents(dataSource);
		System.out.println("Stored tabular data, " +lines+" lines, "+columns+" columns");

        if (!xquery.isEmpty()) {
            ResourceSet result = tester.querySingleLine(xquery, "testQueryBigDocument" );
            System.out.println("result size: " + result.getSize() );
        }
        tester.cleanup();
	}
}
