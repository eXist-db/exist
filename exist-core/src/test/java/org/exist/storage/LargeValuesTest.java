package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.TestUtils;

import org.junit.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Random;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

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

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void storeAndRecover() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, CollectionConfigurationException, SAXException, EXistException {
        storeDocuments();
        restart();
        remove();
    }

    /**
     * Store some documents, reindex the collection and crash without commit.
     */
    private void storeDocuments() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection root;

            try(final Txn transaction = transact.beginTransaction()) {

                root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                pool.getConfigurationManager().addConfiguration(transaction, broker, root, CONFIG_QNAME);

                transact.commit(transaction);
            }

            pool.getJournalManager().get().flush(true, false);

            BrokerPool.FORCE_CORRUPTION = true;

            final Path file = createDocument();
            try(final Txn transaction = transact.beginTransaction()) {
                final IndexInfo info = root.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"),
                        new InputSource(file.toUri().toASCIIString()));
                assertNotNull(info);
                root.store(transaction, broker, info, new InputSource(file.toUri().toASCIIString()));
                broker.saveCollection(transaction, root);

                transact.commit(transaction);
            } finally {
                FileUtils.deleteQuietly(file);
            }

            pool.getJournalManager().get().flush(true, false);
        }
    }

    /**
     * Just recover.
     */
    private void restart() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException {

        BrokerPool.FORCE_CORRUPTION = false;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK)) {
            assertNotNull(root);

            try(final LockedDocument lockedDoc = root.getDocumentWithLock(broker, XmldbURI.create("test.xml"), LockMode.READ_LOCK)) {
                assertNotNull(lockedDoc);

                final Serializer serializer = broker.getSerializer();
                serializer.reset();

                final Path tempFile = Files.createTempFile("eXist", ".xml");
                try (final Writer writer = Files.newBufferedWriter(tempFile, UTF_8)) {
                    serializer.serialize(lockedDoc.getDocument(), writer);
                }

                // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                root.close();

                FileUtils.deleteQuietly(tempFile);

//            XQuery xquery = broker.getXQueryService();
//            DocumentSet docs = broker.getAllXMLResources(new DefaultDocumentSet());
//            Sequence result = xquery.execute(broker, "//key/@id/string()", docs.docsToNodeSet(), AccessContext.TEST);
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
    }

    private void remove() throws EXistException, PermissionDeniedException, DatabaseConfigurationException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();
                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        }
    }

    private Path createDocument() throws IOException {
        final Path file = Files.createTempFile("eXistTest", ".xml");
        try(final Writer writer = Files.newBufferedWriter(file, UTF_8)) {
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

    @AfterClass
    public static void cleanupDb() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        TestUtils.cleanupDB();
    }
}
