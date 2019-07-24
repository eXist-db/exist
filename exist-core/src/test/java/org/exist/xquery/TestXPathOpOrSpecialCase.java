package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;


/**
 * This is the simplest test that demonstrates the <code>Predicate</code>/<code>OpOr</code>
 * bug. Right now, there is only one test - at the very bottom of the 
 * source code. 
 * @author Jason Smith
 */
public class TestXPathOpOrSpecialCase extends Assert {

	private static final Logger LOG = LogManager.getLogger(TestXPathOpOrSpecialCase.class);

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

	/** Database test collection (<code>/db/blah</code>). */
	private Collection testCollection;
	
	@Before
	public void setUp() throws Exception 
	{
        final CollectionManagementService service = (CollectionManagementService)existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection("blah");
        assertNotNull(testCollection);
    }

	@After
	public void tearDown() throws Exception {
		final CollectionManagementService service =
				(CollectionManagementService) existEmbeddedServer.getRoot().getService(
						"CollectionManagementService",
						"1.0");
		service.removeCollection("blah");
		testCollection = null;
	}

	/**
	 * Given an essentially empty XML document at path <code>/db/blah/blah.xml</code>,
	 * query the document with a bogus predicate containing an <code>or<code> operation;
	 * expect <code>org.exist.xquery.XPathException: exerr:ERROR cannot convert xs:boolean('false') to a node set</code>.
	 */
	@Test
	public void verifyOpOrInPredicate() throws Exception
	{
		try
		{
			storeXML(testCollection, "blah.xml", "<blah>No element content.</blah>");
			existEmbeddedServer.executeQuery("/blah[a='A' or b='B']");
		}
		catch(final XMLDBException e)
		{
			LOG.error(e.getMessage(), e);
			throw e;
		}
	}

	/** 
	 * Store the XML string into the specified collection and document.
	 * @param collection The target collection.
     * @param documentName The target document name.
     * @param content The XML content to be stored.
     * @throws XMLDBException See {@link XMLDBException}.
     */
    private void storeXML(final Collection collection, final String documentName, final String content) throws XMLDBException 
    {
        final XMLResource doc = (XMLResource)collection.createResource(documentName, "XMLResource");
        doc.setContent(content);
        collection.storeResource(doc);
    }
}
