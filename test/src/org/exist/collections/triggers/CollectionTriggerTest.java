package org.exist.collections.triggers;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.security.PermissionDeniedException;
import org.xmldb.api.base.Collection;
import org.exist.xmldb.CollectionManagementServiceImpl;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.IndexQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;


public class CollectionTriggerTest {

    private final static String TEST_COLLECTION = "testCollectionTrigger";
    private static Collection root;
    private static Collection testCollection;
    private static CollectionManagementServiceImpl rootSrv;


    @Test
    public void move() throws XMLDBException, EXistException, PermissionDeniedException {

        //create /db/testCollectionTrigger/srcCollection
        final CollectionManagementServiceImpl colMgmtSrv = (CollectionManagementServiceImpl)testCollection.getService("CollectionManagementService", "1.0");
        final Collection srcCollection = colMgmtSrv.createCollection("col1");

        final XmldbURI baseUri = XmldbURI.create(testCollection.getName());
        final XmldbURI srcUri = XmldbURI.create(srcCollection.getName());
        final XmldbURI newDest = XmldbURI.create("moved");

        //perform the move
        colMgmtSrv.move(srcUri, baseUri, newDest);


        //get the trigger and check its count
        CountingCollectionTrigger.CountingCollectionTriggerState triggerState = CountingCollectionTrigger.CountingCollectionTriggerState.getInstance();

        //trigger move methods should have only been
        //invoked once as we only moved one resource
        assertEquals(1, triggerState.getBeforeMove());
        assertEquals(1, triggerState.getAfterMove());
    }

    @Before
    public void createTestCollection() throws XMLDBException {
        //create a test collection
        testCollection = rootSrv.createCollection(TEST_COLLECTION);

        // configure the test collection with the trigger
        IndexQueryService idxConf = (IndexQueryService)testCollection.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);
    }

    @After
    public void removeTestCollection() throws XMLDBException {
        rootSrv.removeCollection(XmldbURI.create(testCollection.getName()));
    }

    /** just start the DB and create the test collection */
    @BeforeClass
    public static void startDB() {
        try {
            // initialize driver
            Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
            assertNotNull(root);

            rootSrv = (CollectionManagementServiceImpl)root.getService("CollectionManagementService", "1.0");
        } catch (ClassNotFoundException e) {
            fail(e.getMessage());
        } catch (InstantiationException e) {
            fail(e.getMessage());
        } catch (IllegalAccessException e) {
            fail(e.getMessage());
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void shutdownDB() {
        TestUtils.cleanupDB();
        try {
            org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist://" + XmldbURI.ROOT_COLLECTION, "admin", "");
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        testCollection = null;
    }

    private final static String COLLECTION_CONFIG =
        "<exist:collection xmlns:exist='http://exist-db.org/collection-config/1.0'>" +
        "  <exist:triggers>" +
        "     <exist:trigger class='org.exist.collections.triggers.CountingCollectionTrigger'/>" +
        "  </exist:triggers>" +
        "</exist:collection>";
}
