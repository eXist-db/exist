package org.exist.collections;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.EXistXPathQueryService;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test concurrent access to collections.
 */
public class ConcurrencyTest {

    private static final Logger LOG = LogManager.getLogger(ConcurrencyTest.class);

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private static int CONCURRENT_THREADS = Runtime.getRuntime().availableProcessors() * 3;
    private static final int DOC_COUNT = CONCURRENT_THREADS * 10;

    private static final int QUERY_COUNT = 20;
    
    private static final String QUERY = "collection('/db/test')/test/c";

    private static final String REMOVE =
        "declare namespace xmldb=\"http://exist-db.org/xquery/xmldb\";\n" +
        "declare namespace util=\"http://exist-db.org/xquery/util\";" +
        "declare variable $start as xs:integer external; " +
        "let $dummy := util:log('DEBUG', ('Removing $start: ', $start, ' to ', $start + 9)) " +
        "for $i in $start to $start + 9 " +
        "let $resource := collection('/db/test')/test[xs:integer(@id) eq $i] " +
        "return" +
        "   xmldb:remove(util:collection-name($resource), util:document-name($resource))";

    @Test
	public void runTasks() {
        final ExecutorService executorRemove = newFixedThreadPool(CONCURRENT_THREADS, "concurrencyTest-remove");
        final ExecutorService executorQuery = newFixedThreadPool(CONCURRENT_THREADS, "concurrencyTest-query");

        for (int i = 1; i <= CONCURRENT_THREADS; i++) {
            executorRemove.submit(new QueryTask(REMOVE, i * 10, true));
            for (int j = 0; j < QUERY_COUNT; j++) {
                executorQuery.submit(new QueryTask(QUERY, 0, true));
            }
        }

        executorRemove.shutdown();
        executorQuery.shutdown();
		boolean terminatedRemove = false;
        boolean terminatedQuery = false;
		try {
			terminatedRemove = executorRemove.awaitTermination(60 * 60, TimeUnit.SECONDS);
            terminatedQuery = executorQuery.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (final InterruptedException e) {
            //Nothing to do
		}
		assertTrue(terminatedRemove);
        assertTrue(terminatedQuery);
    }

    private ExecutorService newFixedThreadPool(final int nThreads, final String threadsBaseName) {
        return Executors.newFixedThreadPool(nThreads, new ThreadFactory() {
            private final AtomicInteger counter = new AtomicInteger();

            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(r, threadsBaseName + "-" + counter.getAndIncrement());
            }
        });
    }

    private static class QueryTask implements Runnable {
        private final String query;
        private final int start;
		private final boolean protect;
        
        private QueryTask(final String query, final int start, final boolean protect) {
            this.query = query;
            this.protect = protect;
            this.start = start;
        }

        @Override
        public void run() {
            try {
                final Collection collection = DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", "");
                final EXistXPathQueryService service = (EXistXPathQueryService) collection.getService("XQueryService", "1.0");
                if(protect) {
                    service.beginProtected();
                }
                try {
                    if (start > 0) {
                        service.declareVariable("start", new Integer(start));
                    }
                    service.query(query);
                } finally {
                    if(protect) {
                        service.endProtected();
                    }
                }
            } catch (final Exception e) {
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
    }

    @BeforeClass
    public static void initDB() throws XMLDBException {
        final CollectionManagementService mgmt = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        final Collection test = mgmt.createCollection("test");

        for (int i = 1; i <= DOC_COUNT; i++) {
            final Resource r = test.createResource("test" + i + ".xml", "XMLResource");
            final String XML =
                "<test id='" + i + "'>" +
                "   <a>b</a>" +
                "   <c>d</c>" +
                "</test>";
            r.setContent(XML);
            test.storeResource(r);
        }
    }

    @AfterClass
    public static void cleanup() throws XMLDBException {
        final CollectionManagementService cmgr = (CollectionManagementService) existEmbeddedServer.getRoot().getService("CollectionManagementService", "1.0");
        cmgr.removeCollection("test");
    }
}
