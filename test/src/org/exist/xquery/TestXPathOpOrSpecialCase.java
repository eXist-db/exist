package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;


/**
 * This is the simplest test that demonstrates the <tt>Predicate</tt>/<tt>OpOr</tt>
 * bug. Right now, there is only one test - at the very bottom of the 
 * source code. 
 * @author Jason Smith
 */
public class TestXPathOpOrSpecialCase extends Assert {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

	/** Database test collection (<tt>/db/blah</tt>). */
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
	 * Given an essentially empty XML document at path <tt>/db/blah/blah.xml</tt>,
	 * query the document with a bogus predicate containing an <tt>or<tt> operation;
	 * expect <tt>org.exist.xquery.XPathException: exerr:ERROR cannot convert xs:boolean('false') to a node set</tt>.
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
			e.printStackTrace();
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
