/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.storage.lock;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.TestDataGenerator;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.EXistXPathQueryService;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Resource;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * Test deadlock detection and resolution.
 * 
 * @author wolf
 */
@RunWith(Parameterized.class)
public class DeadlockTest {

	private static final Logger LOG = LogManager.getLogger(DeadlockTest.class);

	/** pick a set of random collections to query */
	private static final int TEST_RANDOM_COLLECTION = 0;
	/** pick a single collection to query */
	private static final int TEST_SINGLE_COLLECTION = 1;
	/** query the root collection */
	private static final int TEST_ALL_COLLECTIONS = 2;
	/** query a single document */
	private static final int TEST_SINGLE_DOC = 3;
	/** apply a random mixture of the other modes */
	private static final int TEST_MIXED = 4;

    private static final int TEST_REMOVE = 5;

    private static final int DELAY = 7000;

    /** Use 4 test runs, querying different collections */
    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][] {
            { "testRandomCollection", TEST_RANDOM_COLLECTION },
            { "testSingleCollection", TEST_SINGLE_COLLECTION },
            { "testAllCollections", TEST_ALL_COLLECTIONS },
            { "testSingleDoc", TEST_SINGLE_DOC },
            { "testMixed", TEST_MIXED },
            { "testRemoved", TEST_REMOVE }
        });
    }
	
	private static final int COLL_COUNT = 20;

    private static final int QUERY_COUNT = 1000;

    private static final int DOC_COUNT = 70;

    private static final int REMOVE_COUNT = 50;
    
    private static final int N_THREADS = 40;

	private final static String generateXQ =
			"declare function local:random-sequence($length as xs:integer, $G as map(xs:string, item())) {\n"
					+ "  if ($length eq 0)\n"
					+ "  then ()\n"
					+ "  else ($G?number, local:random-sequence($length - 1, $G?next()))\n"
					+ "};\n"
					+ "let $rnd := fn:random-number-generator() return"
					+ "<book id=\"{$filename}\" n=\"{$count}\">"
					+ "   <chapter xml:id=\"chapter{$count}\">"
					+ "       <title>{local:random-sequence(7, $rnd)}</title>"
					+ "       {"
					+ "           for $section in 1 to 8 return"
					+ "               <section id=\"sect{$section}\">"
					+ "                   <title>{local:random-sequence(7, $rnd)}</title>"
					+ "                   {"
					+ "                       for $para in 1 to 10 return"
					+ "                           <para>{local:random-sequence(120, $rnd)}</para>"
					+ "                   }"
					+ "               </section>"
					+ "       }"
					+ "   </chapter>"
					+ "</book>";

	private final Random random = new Random();

	@Parameter
	public String testName;
        
	@Parameter(value = 1)
	public int mode;

	@ClassRule
	public static ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	@BeforeClass
	public static void startDB() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException, ClassNotFoundException, IllegalAccessException, InstantiationException, XMLDBException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

			final Collection root = broker.getOrCreateCollection(transaction,
					XmldbURI.ROOT_COLLECTION_URI);
			assertNotNull(root);
			broker.saveCollection(transaction, root);

			final Collection test = broker.getOrCreateCollection(transaction,
					TestConstants.TEST_COLLECTION_URI);
			assertNotNull(test);
			broker.saveCollection(transaction, test);

			transact.commit(transaction);

			// initialize XML:DB driver
			final Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			final Database database = (Database) cl.newInstance();
			DatabaseManager.registerDatabase(database);
		}
	}

    @After
    public void clearDB() throws XMLDBException {
		final org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", "");
		CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
		service.removeCollection(".");
    }

    @Test
	public void runTasks() {
		final ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        executor.submit(new StoreTask("store", COLL_COUNT, DOC_COUNT));
        synchronized (this) {
            try {
                wait(DELAY);
            } catch (InterruptedException e) {
            	Thread.currentThread().interrupt();
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
		for (int i = 0; i < QUERY_COUNT; i++) {
			executor.submit(new QueryTask(COLL_COUNT));
		}
        if (mode == TEST_REMOVE) {
            for (int i = 0; i < REMOVE_COUNT; i++) {
                executor.submit(new RemoveDocumentTask(COLL_COUNT, DOC_COUNT));
            }
        }
        executor.shutdown();
		boolean terminated = false;
		try {
			terminated = executor.awaitTermination(60 * 60, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			LOG.error(e.getMessage(), e);
			fail(e.getMessage());
		}
		assertTrue(terminated);
	}

	private static class StoreTask implements Runnable {

		@SuppressWarnings("unused")
		private final String id;
		private final int docCount;
		private final int collectionCount;

		public StoreTask(final String id, final int collectionCount, final int docCount) {
			this.id = id;
			this.collectionCount = collectionCount;
			this.docCount = docCount;
		}

		@Override
		public void run() {
			final BrokerPool pool = existEmbeddedServer.getBrokerPool();
			final TransactionManager transact = pool.getTransactionManager();
			try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

				final TestDataGenerator generator = new TestDataGenerator("xdb", docCount);
				Collection coll;
				int fileCount = 0;
				for (int i = 0; i < collectionCount; i++) {
                    try(final Txn transaction = transact.beginTransaction()) {
                        coll = broker.getOrCreateCollection(transaction,
                                TestConstants.TEST_COLLECTION_URI.append(Integer
                                        .toString(i)));
                        assertNotNull(coll);
                        broker.saveCollection(transaction, coll);
                        transact.commit(transaction);
                    }

                    final Path[] files = generator.generate(broker, coll, generateXQ);
                    for (int j = 0; j < files.length; j++, fileCount++) {
                        try(final Txn transaction = transact.beginTransaction()) {
                            InputSource is = new InputSource(files[j].toUri()
                                    .toASCIIString());
                            assertNotNull(is);
                            IndexInfo info = coll.validateXMLResource(transaction,
                                    broker, XmldbURI.create("test" + fileCount
                                            + ".xml"), is);
                            assertNotNull(info);
                            coll.store(transaction, broker, info, is);
                            transact.commit(transaction);
                        }
                    }
					generator.releaseAll();
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
//				fail(e.getMessage());
			}
		}
	}

	private class QueryTask implements Runnable {

		private int collectionCount;

		public QueryTask(int collectionCount) {
			this.collectionCount = collectionCount;
		}

		public void run() {
			final StringBuilder buf = new StringBuilder();
			String collection = "/db";
			int currentMode = mode;
			if (mode == TEST_MIXED || currentMode == TEST_REMOVE)
				currentMode = random.nextInt(4);
            if (currentMode == TEST_SINGLE_COLLECTION) {
				int collectionId = random.nextInt(collectionCount);
				collection = "/db/test/" + collectionId;
				buf.append("collection('").append(collection)
					.append("')//chapter/section[@id = 'sect1']");
			} else if (currentMode == TEST_RANDOM_COLLECTION) {
				List<Integer> collIds = new ArrayList<Integer>(7);
				for (int i = 0; i < 3; i++) {
					int r;
					do {
						r = random.nextInt(collectionCount);
					} while (collIds.contains(r));
					collIds.add(r);
				}
				buf.append("(");
				for (int i = 0; i < 3; i++) {
					if (i > 0)
						buf.append(", ");
					buf.append("collection('/db/test/").append(collIds.get(i))
							.append("')");
				}
				buf.append(")//chapter/section[@id = 'sect1']");
				collection = "/db/test";
			} else if (currentMode == TEST_SINGLE_DOC) {
				int collectionId = random.nextInt(collectionCount);
				collection = "/db/test/" + collectionId;
				buf.append("doc('").append(collection).append("/test1.xml')//chapter/section[@id = 'sect1']");
			} else {
				buf.append("//chapter/section[@id = 'sect1']");
			}
			
			String query = buf.toString();
			try {
				org.xmldb.api.base.Collection testCollection = DatabaseManager
						.getCollection("xmldb:exist://" + collection, "admin", null);
                if (testCollection == null)
                    return;
                EXistXPathQueryService service = (EXistXPathQueryService) testCollection
						.getService("XQueryService", "1.0");
				service.beginProtected();
				try {
					ResourceSet result = service.query(query);
                    result.getSize();
				} finally {
					service.endProtected();
				}
			} catch (Exception e) {
				LOG.error(e.getMessage(), e);
				fail(e.getMessage());
			}
		}
	}

    private class RemoveDocumentTask implements Runnable {
        private final int collectionCount;
        private final int documentCount;

        public RemoveDocumentTask(final int collectionCount, final int documentCount) {
            this.collectionCount = collectionCount;
            this.documentCount = documentCount;
        }

        @Override
        public void run() {
            boolean removed = false;
            do {
                final int collectionId = random.nextInt(collectionCount);
                final String collection = "/db/test/" + collectionId;
                final int docId = random.nextInt(documentCount) * collectionId;
                final String document = "test" + docId + ".xml";
                try {
                    final org.xmldb.api.base.Collection testCollection = DatabaseManager.getCollection("xmldb:exist://" + collection, "admin", "");
                    final Resource resource = testCollection.getResource(document);
                    if (resource != null) {
                        testCollection.removeResource(resource);
                        removed = true;
                    }
                } catch (final XMLDBException e) {
					LOG.error(e.getMessage(), e);
                    fail(e.getMessage());
                }
            } while (!removed);
        }
    }
}
