package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.TestUtils;
import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
        "       <create qname=\"@id\" type=\"xs:string\"/>" +
        "	</index>" +
    	"</collection>";

    private static final int KEY_COUNT = 1000;

    private static final int KEY_LENGTH = 5000;

    @Test
    public void storeAndRecover() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, CollectionConfigurationException, SAXException, EXistException {
        for (int i = 0; i < 1; i++) {
            storeDocuments();
            restart();
            remove();
        }
    }

    private File createDocument() throws IOException {
        final File file = File.createTempFile("eXistTest", ".xml");

        try(final Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"))) {
            final Random r = new Random();
            writer.write("<test>");
            for(int i = 0; i < KEY_COUNT; i++) {
                writer.write("<key id=\"");
                int keySize = r.nextInt(KEY_LENGTH);
                if(keySize == 0) {
                    keySize = 1;
                }
                for(int j = 0; j < keySize; j++) {
                    char ch;
                    do {
                        ch = (char) r.nextInt(0x5A);
                    } while(ch < 0x41);
                    writer.write(ch);
                }
                writer.write("\"/>");
            }
            writer.write("</test>");
        }

        return file;
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            Collection root;

            try(final Txn transaction = transact.beginTransaction()) {

                root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                pool.getConfigurationManager().addConfiguration(transaction, broker, root, CONFIG_QNAME);

                transact.commit(transaction);
            }

            transact.getJournal().flushToLog(true);

            BrokerPool.FORCE_CORRUPTION = true;

            final File file = createDocument();
            try(final Txn transaction = transact.beginTransaction()) {
                final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                        new InputSource(file.toURI().toASCIIString()));
                assertNotNull(info);
                root.store(transaction, broker, info, new InputSource(file.toURI().toASCIIString()), false);
                broker.saveCollection(transaction, root);

                transact.commit(transaction);
            }

            transact.getJournal().flushToLog(true);
            file.delete();
        }
    }

    /**
     * Just recover.
     */
    private void restart() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
            final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);

            final DocumentImpl doc = root.getDocument(broker, XmldbURI.create("test.xml"));
            assertNotNull(doc);

            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final File tempFile = File.createTempFile("eXist", ".xml");
            try(final Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), "UTF-8")) {
                serializer.serialize(doc, writer);
                tempFile.delete();
            }
//            XQuery xquery = broker.getXQueryService();
//            DocumentSet docs = broker.getAllXMLResources(new DefaultDocumentSet());
//            Sequence result = xquery.execute("//key/@id/string()", docs.docsToNodeSet(), AccessContext.TEST);
//            assertEquals(KEY_COUNT, result.getItemCount());
//            for (SequenceIterator i = result.iterate(); i.hasNext();) {
//                Item item = i.nextItem();
//                String s = item.getStringValue();
//                assertTrue(s.length() > 0);
//                if (s.length() == 0)
//                    break;
//            }
        }
    }

    private void remove() throws EXistException, PermissionDeniedException, DatabaseConfigurationException, IOException, TriggerException {

        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.READ_LOCK);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        }
    }

    @After
    public void closeDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
    }

    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }
}
