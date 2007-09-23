package org.exist.collections;

import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XQueryService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Test concurrent access to collections.
 */
public class ConcurrencyTest {

    private static final int N_THREADS = 10;
    private static final int DOC_COUNT = 200; 

    private static final int QUERY_COUNT = 20;
    
    private static final String QUERY = "/test/c";

    private static final String REMOVE =
        "declare namespace xdb=\"http://exist-db.org/xquery/xmldb\";\n" +
        "declare namespace util=\"http://exist-db.org/xquery/util\";" +
        "declare variable $start external; " +
        "let $dummy := util:log('DEBUG', ('Removing $start: ', $start, ' to ', $start + 9)) " +
        "for $i in $start to $start + 9 " +
        "let $resource := /test[@id = xs:integer($i)] " +
        "return" +
        "   xdb:remove(util:collection-name($resource), util:document-name($resource))";

    @Test
	public void runTasks() {
		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);

        for (int i = 1; i <= 20; i++) {
            executor.submit(new QueryTask(REMOVE, i * 10, true));
            for (int j = 0; j < QUERY_COUNT; j++) {
                executor.submit(new QueryTask(QUERY, 0, true));
            }
        }

        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
		}
		assertTrue(terminated);
    }

    private class QueryTask implements Runnable {

        String query;
        int start = 0;
        boolean protect = false;
        
        private QueryTask(String query, int start, boolean protect) {
            this.query = query;
            this.protect = protect;
            this.start = start;
        }

        public void run() {
            try {
                Collection collection = DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", null);
                XPathQueryServiceImpl service = (XPathQueryServiceImpl) collection.getService("XQueryService", "1.0");
                service.beginProtected();
                try {
                    if (start > 0)
                        service.declareVariable("start", new Integer(start));
                    service.query(query);
                } finally {
                    service.endProtected();
                }
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
    }

    @BeforeClass
    public static void initDB() {
        // initialize XML:DB driver
        try {
            Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
            Database database = (Database) cl.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);

            org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            Collection test = mgmt.createCollection("test");

            for (int i = 1; i <= DOC_COUNT; i++) {
                Resource r = test.createResource("test" + i + ".xml", "XMLResource");
                String XML =
                    "<test id='" + i + "'>" +
                    "   <a>b</a>" +
                    "   <c>d</c>" +
                    "</test>";
                r.setContent(XML);
                test.storeResource(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void closeDB() {
        try {
            Collection root = DatabaseManager.getCollection("xmldb:exist://" + DBBroker.ROOT_COLLECTION, "admin", null);
            CollectionManagementService cmgr = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
            cmgr.removeCollection("test");

//            Collection configRoot = DatabaseManager.getCollection("xmldb:exist://" + CollectionConfigurationManager.CONFIG_COLLECTION,
//                    "admin", null);
//            cmgr = (CollectionManagementService) configRoot.getService("CollectionManagementService", "1.0");
//            cmgr.removeCollection("db");
            DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
            mgr.shutdown();
        } catch (XMLDBException e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }
}
