package org.exist.xquery;

import org.exist.TestUtils;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

    

/**
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class UnionTest {
    
    private final static String TEST_COLLECTION_NAME = "test-pubmed";
    
    private final static String PUBMED_DOC_NAME = "pubmed.xml";
    
    private final static String PUBMED =
        "<PubmedArticleSet>"
        + "<PubmedArticle>"
            + "<MedlineCitation Owner=\"NLM\" Status=\"In-Process\">"
                + "<Article PubModel=\"Print\">"
                    + "<AuthorList CompleteYN=\"Y\">"
                        + "<Author ValidYN=\"Y\">"
                            + "<LastName>Castellano</LastName>"
                            + "<ForeName>Christopher R</ForeName>"
                            + "<Initials>CR</Initials>"
                        + "</Author>"
                        + "<Author ValidYN=\"Y\">"
                            + "<LastName>Rizzolo</LastName>"
                            + "<ForeName>Denise</ForeName>"
                            + "<Initials>D</Initials>"
                        + "</Author>"
                    + "</AuthorList>"
                    + "<Language>eng</Language>"
                    + "<PublicationTypeList>"
                        + "<PublicationType>Journal Article</PublicationType>"
                    + "</PublicationTypeList>"
                + "</Article>"
            + "</MedlineCitation>"
        + "</PubmedArticle>"
    + "</PubmedArticleSet>";
    
    private final static String INDEX_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">"
            + "<index>"
                + "<create qname=\"ForeName\" type=\"xs:string\"/>"
                + "<create qname=\"LastName\" type=\"xs:string\"/>"
            + "</index>"
        +" </collection>";
    
    private final static String XQUERY = "/PubmedArticleSet/PubmedArticle[MedlineCitation/Article/AuthorList/Author/(ForeName|LastName) = \"Castellano\"]";

    private static Collection testCollection;    
    
    @Test
    public void unionInPredicate_withoutIndex() throws XMLDBException {
         final XQueryService service = storeXMLStringAndGetQueryService(PUBMED_DOC_NAME, PUBMED);
         final ResourceSet result = service.queryResource(PUBMED_DOC_NAME, XQUERY);
         
         assertEquals(1, result.getSize());
    }
    
    @Test
    public void testUnionInPredicate_withIndex() throws XMLDBException {
        storeCollectionConfig();
        
        final XQueryService service = storeXMLStringAndGetQueryService(PUBMED_DOC_NAME, PUBMED);
        final ResourceSet result = service.queryResource(PUBMED_DOC_NAME, XQUERY);
         
        assertEquals(1, result.getSize());
    }
    
    private void storeCollectionConfig() throws XMLDBException {
        
        final Collection colConfig = getOrCreateCollection("/db/system/config/db/" + TEST_COLLECTION_NAME);
        final XMLResource docConfig = (XMLResource) colConfig.createResource("collection.xconf", "XMLResource");
        docConfig.setContent(INDEX_CONFIG);
        colConfig.storeResource(docConfig);
    }
    
    private Collection getOrCreateCollection(final String collectionPath) throws XMLDBException {
        return getOrCreateCollection(testCollection.getParentCollection(), collectionPath.replaceFirst("/db/", ""));
    }
    
    private Collection getOrCreateCollection(final Collection currentCollection, final String collectionPath) throws XMLDBException {
       
        final int offset = collectionPath.indexOf("/") > -1 ? collectionPath.indexOf("/") : collectionPath.length();
        final String colName = collectionPath.substring(0, offset);
        
        if(colName.length() == 0) {
            return currentCollection;
        }
        
        Collection child = currentCollection.getChildCollection(colName);
        if(child == null) {
            final CollectionManagementService service = (CollectionManagementService)currentCollection.getService("CollectionManagementService", "1.0");
            child = service.createCollection(colName);
        }
        
        if(collectionPath.indexOf("/") == -1) {
            return child;
        } else {
            final String subPath = collectionPath.substring(collectionPath.indexOf("/") + 1);
            return getOrCreateCollection(child, subPath);
        }
    }
    
    private XQueryService storeXMLStringAndGetQueryService(String documentName, String content) throws XMLDBException {
       final XMLResource doc = (XMLResource) testCollection.createResource(documentName, "XMLResource");
       doc.setContent(content);
       testCollection.storeResource(doc);
       final XQueryService service = (XQueryService) testCollection.getService("XPathQueryService", "1.0");
       return service;
    }
    
    @Before
    public void clearCollectionConfig() throws XMLDBException {
        final Collection colDb = testCollection.getParentCollection();
        
        final Collection colSystem = colDb.getChildCollection("system");
        if(colSystem == null) {
            return;
        }
        
        final Collection colConfig = colSystem.getChildCollection("config");
        if(colConfig == null) {
            return;
        }
        
        final Collection colConfigDb = colConfig.getChildCollection("db");
        if(colConfigDb == null) {
            return;
        }
        
        boolean foundPubmedConfig = false;
        final String configCols[] = colConfigDb.listChildCollections();
        for(final String configCol : configCols) {
            if(configCol.equals(TEST_COLLECTION_NAME)) {
                foundPubmedConfig = true;
                break;
            }
        }
        
        if(foundPubmedConfig) {
            final CollectionManagementService service = (CollectionManagementService)colConfigDb.getService("CollectionManagementService", "1.0");
            service.removeCollection(TEST_COLLECTION_NAME);
        }
    }
    
    @BeforeClass
    public static void startDbAndCreateTestCollexction() throws Exception {
        
        // initialize driver
        final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        final Database database = (Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        final Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
        final CollectionManagementService service = (CollectionManagementService)root.getService("CollectionManagementService", "1.0");
        testCollection = service.createCollection(TEST_COLLECTION_NAME);
        assertNotNull(testCollection);
    }
    
    @AfterClass
    public static void tearDown() throws Exception {
        TestUtils.cleanupDB();

        final DatabaseInstanceManager dim = (DatabaseInstanceManager)testCollection.getService("DatabaseInstanceManager", "1.0");
        dim.shutdown();
        testCollection = null;
    }
}
