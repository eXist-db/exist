package org.exist.xquery;

import org.junit.Before;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import org.exist.TestUtils;
import org.exist.jetty.JettyStart;
import org.exist.xmldb.CollectionImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;

/**
 * This is the simplest test that demonstrates the <tt>Predicate</tt>/<tt>OpOr</tt>
 * bug. Right now, there is only one test - at the very bottom of the 
 * source code. 
 * @author Jason Smith
 */
public class TestXPathOpOrSpecialCase extends Assert
{
	/** The local database URI. */
	private static final String uri = XmldbURI.LOCAL_DB;
	/** The test Jetty server. */
	private JettyStart server = null;
	
	/** Database root collection. */
	private Collection rootCollection;
	/** Database test collection (<tt>/db/blah</tt>). */
	private Collection testCollection;
	
	@Before
	public void setUp() throws Exception 
	{
        if (server == null) 
		{
			server = new JettyStart();
			server.run();
		}
        
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database)cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        rootCollection = DatabaseManager.getCollection(uri, "admin", "");
        final CollectionManagementService service = (CollectionManagementService)rootCollection.getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection("blah");
        assertNotNull(testCollection);
    }
    
    @After
    public void tearDown() throws Exception 
    {
		try 
		{
			TestUtils.cleanupDB();
            if (!((CollectionImpl)testCollection).isRemoteCollection()) 
            {
                DatabaseInstanceManager dim = (DatabaseInstanceManager)testCollection.getService("DatabaseInstanceManager", "1.0");
                dim.shutdown();
            }
            testCollection = null;
        } 
        catch(final XMLDBException e) 
        {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

	/** 
	 * Store the XML string into the specified collection and document.
	 * @param The target collection.
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
    
    /**
     * Get the XQuery service.
     * @param collection The target collection.
     * @throws XMLDBException See {@link XMLDBException}.
     */
    private XQueryService getXQueryService(final Collection collection) throws XMLDBException
    {
		return (XQueryService)collection.getService("XPathQueryService", "1.0");
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
			getXQueryService(rootCollection).query("/blah[a='A' or b='B']");
		}
		catch(final XMLDBException e)
		{
			e.printStackTrace();
			throw e;
		}	
	}
}
