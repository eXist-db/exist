package org.exist.storage;

import static org.junit.Assert.*;

import java.io.File;

import org.exist.collections.*;
import org.exist.security.SecurityManager;
import org.exist.storage.txn.*;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xml.sax.InputSource;


public class RemoveRootCollectionTest {
	
	private BrokerPool pool;
	private DBBroker broker;
	private TransactionManager transact;
	Collection root;
	
	@Test public void removeEmptyRootCollection() throws Exception {
		Txn transaction = transact.beginTransaction();
		broker.removeCollection(transaction, root);
		transact.commit(transaction);
		assertEquals(0, root.getChildCollectionCount());
		assertEquals(0, root.getDocumentCount());
	}

	@Test public void removeRootCollectionWithChildCollection() throws Exception {
		addChildToRoot();
		Txn transaction = transact.beginTransaction();
		broker.removeCollection(transaction, root);
		transact.commit(transaction);
		assertEquals(0, root.getChildCollectionCount());
		assertEquals(0, root.getDocumentCount());
	}

	@Ignore @Test public void removeRootCollectionWithDocument() throws Exception {
		addDocumentToRoot();
		Txn transaction = transact.beginTransaction();
		broker.removeCollection(transaction, root);
		transact.commit(transaction);
		assertEquals(0, root.getChildCollectionCount());
		assertEquals(0, root.getDocumentCount());
	}


	@Before public void startDB() throws Exception {
		Configuration config = new Configuration();
		BrokerPool.configure(1, 1, config);
		pool = BrokerPool.getInstance();  assertNotNull(pool);
		broker = pool.get(SecurityManager.SYSTEM_USER);  assertNotNull(broker);
		transact = pool.getTransactionManager();  assertNotNull(transact);
		root = broker.getCollection(XmldbURI.ROOT_COLLECTION_URI);  assertNotNull(root);
	}

	@After public void stopDB() {
		pool.release(broker);
		BrokerPool.stopAll(false);
	}
	
	private void addDocumentToRoot() throws Exception {
		Txn transaction = transact.beginTransaction();
		InputSource is = new InputSource(new File("samples/shakespeare/hamlet.xml").toURI().toASCIIString());
		assertNotNull(is);
		IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"), is);
		assertNotNull(info);
		root.store(transaction, broker, info, is, false);
		transact.commit(transaction);
	}
	
	private void addChildToRoot() throws Exception {
		Txn transaction = transact.beginTransaction();
		Collection child = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append("child"));
		broker.saveCollection(transaction, child);
		transact.commit(transaction);
	}
}
