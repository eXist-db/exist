package org.exist.storage;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.junit.Test;
import org.xml.sax.InputSource;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Random;

/**
 * Test indexing and recovery of large string sequences.
 */
public class LargeValuesTest {

    private String CONFIG_QNAME =
    	"<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" +
    	"		<fulltext default=\"none\">" +
    	"		</fulltext>" +
        "       <create qname=\"key\" type=\"xs:string\"/>" +
        "	</index>" +
    	"</collection>";

    @Test
    public void storeAndRecover() {
        for (int i = 0; i < 1; i++) {
            System.out.println("-----------------------------------------------------");
            System.out.println("Run: " + i);
            storeDocuments();
            restart();
            remove();
        }
    }

    private File createDocument() throws IOException {
        File file = File.createTempFile("eXistTest", ".xml");
        OutputStream os = new FileOutputStream(file);
        Writer writer = new BufferedWriter(new OutputStreamWriter(os, "UTF-8"));

        Random r = new Random();
        writer.write("<test>");
        for (int i = 0; i < 5000; i++) {
            writer.write("<key>");
            int keySize = r.nextInt(5000);
            for (int j = 0; j < keySize; j++) {
                char ch;
                do {
                    ch = (char) r.nextInt(0x5A);
                } while (ch < 0x41);
                writer.write(ch);
            }
            writer.write("</key>");
        }
        writer.write("</test>");
        writer.close();
        return file;
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            pool.getConfigurationManager().addConfiguration(transaction, broker, root, CONFIG_QNAME);

            transact.commit(transaction);
            transact.getJournal().flushToLog(true);
            
            BrokerPool.FORCE_CORRUPTION = true;
            transaction = transact.beginTransaction();
            File file = createDocument();
            IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                    new InputSource(file.toURI().toASCIIString()));
            assertNotNull(info);
            root.store(transaction, broker, info, new InputSource(file.toURI().toASCIIString()), false);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            transact.getJournal().flushToLog(true);
            System.out.println("Transaction interrupted ...");
            file.delete();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    /**
     * Just recover.
     */
    private void restart() {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	System.out.println("testRead2() ...\n");
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);

            DocumentImpl doc = root.getDocument(broker, XmldbURI.create("test.xml"));
            assertNotNull(doc);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            String out = serializer.serialize(doc);
        } catch (Exception e) {
            e.printStackTrace();
	        fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private void remove() {
        BrokerPool pool = null;
        DBBroker broker = null;
        try {
        	System.out.println("remove() ...\n");
        	pool = startDB();
        	assertNotNull(pool);
            broker = pool.get(org.exist.security.SecurityManager.SYSTEM_USER);
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
	        fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    @After
    public void closeDB() {
        BrokerPool.stopAll(false);
    }

    protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
}
