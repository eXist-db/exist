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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.exist.TestDataGenerator;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XPathQueryServiceImpl;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.After;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.xml.sax.InputSource;
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
	@Parameters 
	public static LinkedList<Integer[]> data() {
		LinkedList<Integer[]> params = new LinkedList<Integer[]>();
		params.add(new Integer[] { TEST_RANDOM_COLLECTION });
		params.add(new Integer[] { TEST_SINGLE_COLLECTION });
		params.add(new Integer[] { TEST_ALL_COLLECTIONS });
		params.add(new Integer[] { TEST_SINGLE_DOC });
		params.add(new Integer[] { TEST_MIXED });
        params.add(new Integer[] { TEST_REMOVE });
        return params;
	}
	
	private static final int COLL_COUNT = 20;

    private static final int QUERY_COUNT = 1000;

    private static final int DOC_COUNT = 70;

    private static final int REMOVE_COUNT = 50;
    
    private static final int N_THREADS = 40;

    private final static String COLLECTION_CONFIG =
		"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
		"	<index>" +
		"		<fulltext default=\"all\" attributes=\"false\"/>" +
		"		<create path=\"//section/@id\" type=\"xs:string\"/>" +
		"	</index>" +
		"</collection>";
	
	private final static String generateXQ = "<book id=\"{$filename}\" n=\"{$count}\">"
			+ "   <chapter>"
			+ "       <title>{pt:random-text(7)}</title>"
			+ "       {"
			+ "           for $section in 1 to 8 return"
			+ "               <section id=\"sect{$section}\">"
			+ "                   <title>{pt:random-text(7)}</title>"
			+ "                   {"
			+ "                       for $para in 1 to 10 return"
			+ "                           <para>{pt:random-text(40)}</para>"
			+ "                   }"
			+ "               </section>"
			+ "       }"
			+ "   </chapter>" + "</book>";

	private static BrokerPool pool;

	private Random random = new Random();

	private int mode = 0;
	
	public DeadlockTest(int mode) {
		this.mode = mode;
		System.out.println("MODE: " + mode);
	}
	
	@BeforeClass
	public static void startDB() {
		TransactionManager transact = null;
		Txn transaction = null;
		DBBroker broker = null;
		try {
			Configuration config = new Configuration();
			BrokerPool.configure(1, 40, config);
			pool = BrokerPool.getInstance();

			broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
			transact = pool.getTransactionManager();
			assertNotNull(transact);
			transaction = transact.beginTransaction();
			Collection root = broker.getOrCreateCollection(transaction,
					XmldbURI.ROOT_COLLECTION_URI);
			assertNotNull(root);
			broker.saveCollection(transaction, root);

			Collection test = broker.getOrCreateCollection(transaction,
					TestConstants.TEST_COLLECTION_URI);
			assertNotNull(test);
			broker.saveCollection(transaction, test);

			CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);
            
			InputSource is = new InputSource(new File(
					"samples/shakespeare/hamlet.xml").toURI().toASCIIString());
			assertNotNull(is);
			IndexInfo info = test.validateXMLResource(transaction, broker,
					XmldbURI.create("hamlet.xml"), is);
			assertNotNull(info);
			test.store(transaction, broker, info, is, false);
			transact.commit(transaction);

			// initialize XML:DB driver
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			DatabaseManager.registerDatabase(database);
		} catch (Exception e) {
			transact.abort(transaction);
			e.printStackTrace();
			fail(e.getMessage());
		} finally {
			pool.release(broker);
		}
	}

	@AfterClass
	public static void stopDB() {
		try {
			org.xmldb.api.base.Collection root = DatabaseManager.getCollection(
					"xmldb:exist:///db", "admin", null);

			DatabaseInstanceManager dim = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
			dim.shutdown();
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		pool = null;
	}

    @After
    public void clearDB() {
        try {
			org.xmldb.api.base.Collection root = DatabaseManager.getCollection("xmldb:exist:///db/test", "admin", null);
			CollectionManagementService service = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			service.removeCollection(".");
        } catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
    }

    @Test
	public void runTasks() {
		ExecutorService executor = Executors.newFixedThreadPool(N_THREADS);
        executor.submit(new StoreTask("store", COLL_COUNT, DOC_COUNT));
        synchronized (this) {
            try {
                wait(DELAY);
            } catch (InterruptedException e) {
                e.printStackTrace();
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
		}
		assertTrue(terminated);
	}

	private static class StoreTask implements Runnable {

		private String id;
		private int docCount;
		private int collectionCount;

		public StoreTask(String id, int collectionCount, int docCount) {
			this.id = id;
			this.collectionCount = collectionCount;
			this.docCount = docCount;
		}

		public void run() {
			TransactionManager transact = null;
			Txn transaction = null;
			DBBroker broker = null;
			try {
				broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
				transact = pool.getTransactionManager();
				assertNotNull(transact);

				TestDataGenerator generator = new TestDataGenerator("xdb", docCount);
				Collection coll;
				int fileCount = 0;
				for (int i = 0; i < collectionCount; i++) {
                    transaction = transact.beginTransaction();
                    coll = broker.getOrCreateCollection(transaction,
							TestConstants.TEST_COLLECTION_URI.append(Integer
									.toString(i)));
					assertNotNull(coll);
					broker.saveCollection(transaction, coll);
                    transact.commit(transaction);

                    transaction = transact.beginTransaction();
                    System.out.println("Generating " + docCount + " files...");
					File[] files = generator.generate(broker, coll, generateXQ);
					for (int j = 0; j < files.length; j++, fileCount++) {
						InputSource is = new InputSource(files[j].toURI()
								.toASCIIString());
						assertNotNull(is);
						IndexInfo info = coll.validateXMLResource(transaction,
								broker, XmldbURI.create("test" + fileCount
										+ ".xml"), is);
						assertNotNull(info);
						coll.store(transaction, broker, info, is, false);
                        transact.commit(transaction);
                    }
					generator.releaseAll();
				}
			} catch (Exception e) {
				transact.abort(transaction);
				e.printStackTrace();
//				fail(e.getMessage());
			} finally {
				pool.release(broker);
			}
		}
	}

	private class QueryTask implements Runnable {

		private int collectionCount;

		public QueryTask(int collectionCount) {
			this.collectionCount = collectionCount;
		}

		public void run() {
			StringBuilder buf = new StringBuilder();
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
			System.out.println("Query: " + query);
			try {
				org.xmldb.api.base.Collection testCollection = DatabaseManager
						.getCollection("xmldb:exist://" + collection, "admin", null);
                if (testCollection == null)
                    return;
                XPathQueryServiceImpl service = (XPathQueryServiceImpl) testCollection
						.getService("XQueryService", "1.0");
				service.beginProtected();
				try {
					ResourceSet result = service.query(query);
					System.out.println("Result: " + result.getSize());
				} finally {
					service.endProtected();
				}
			} catch (Exception e) {
				e.printStackTrace();
				fail(e.getMessage());
			}
		}
	}

    private class RemoveDocumentTask implements Runnable {

        private int collectionCount;
        private int documentCount;

        public RemoveDocumentTask(int collectionCount, int documentCount) {
            this.collectionCount = collectionCount;
            this.documentCount = documentCount;
        }

        public void run() {
            boolean removed = false;
            do {
                int collectionId = random.nextInt(collectionCount);
                String collection = "/db/test/" + collectionId;
                int docId = random.nextInt(documentCount) * collectionId;
                String document = "test" + docId + ".xml";
                try {
                    org.xmldb.api.base.Collection testCollection = DatabaseManager.getCollection("xmldb:exist://" + collection, "admin", null);
                    Resource resource = testCollection.getResource(document);
                    if (resource != null) {
                        testCollection.removeResource(resource);
                        removed = true;
                    }
                } catch (XMLDBException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }
            } while (!removed);
        }
    }
}
