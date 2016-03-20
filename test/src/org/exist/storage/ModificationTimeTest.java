package org.exist.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.stream.Stream;

import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.util.LockException;
import org.junit.BeforeClass;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.Database;
import static org.exist.TestUtils.cleanupDataDir;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.start.Main;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.FileUtils;
import org.junit.Test;
import static org.junit.Assert.*;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.junit.After;
import org.xml.sax.SAXException;


public class ModificationTimeTest {

    protected static String XML_FILENAME = "test.xml";
    protected static String VALID_XML = "<?xml version=\"1.0\"?>" +
                                        "<valid/>";
    protected static String INVALID_XML = "<?xml version=\"1.0\"?>"
            + "<invalid>";

    
    @BeforeClass
    public static void setup() throws IOException, DatabaseConfigurationException, EXistException {
        cleanupDataDir();
    }

     /**
     * Store a binary document, wait for a while and then overwrite it 
     * with another binary document. The document's modification time should
     * have been updated afterwards.
     */
    @Test
    public void check_if_modification_time_is_updated_binary() throws EXistException, InterruptedException, PermissionDeniedException, LockException, IOException, TriggerException, DatabaseConfigurationException {
        
        final String mimeType = "application/octet-stream";
        final String filename = "data.dat";
        final String data = "some data";

            BinaryDocument binaryDoc = storeBinary(filename, data, mimeType);
            assertNotNull(binaryDoc);

            long modificationTimeBefore = binaryDoc.getMetadata().getLastModified();
            
            Thread.sleep(500);
            
            binaryDoc = storeBinary(filename, data, mimeType);
            assertNotNull(binaryDoc);

            long modificationTimeAfter = binaryDoc.getMetadata().getLastModified();
            //check the mimetype has been preserved across database restarts
            assertNotEquals(modificationTimeBefore, modificationTimeAfter);
    }
    
     /**
     * Store a valid XML resource, wait for a while and then overwrite it 
     * with another valid XML resource. The resource's modification time should
     * have been updated afterwards.
     */
    @Test
    public void check_if_modification_time_is_updated_xml() throws EXistException, InterruptedException, PermissionDeniedException, LockException, IOException, TriggerException, SAXException, DatabaseConfigurationException {
            
            IndexInfo info = storeXML(XML_FILENAME, VALID_XML);
            assertNotNull(info);
            DocumentImpl doc = info.getDocument();
            
            long modificationTimeBefore = doc.getMetadata().getLastModified();
            Thread.sleep(500);
            
            info = storeXML(XML_FILENAME, VALID_XML);
            assertNotNull(info);
            doc = info.getDocument();
            
            long modificationTimeAfter = doc.getMetadata().getLastModified();
            
            assertNotEquals(modificationTimeBefore, modificationTimeAfter);
    }
    
     /**
     * Store a valid XML resource, wait for a while and then try to overwrite
     * it with an invalid XML resource. The invalid XML should be rejected and 
     * the resource's modification time should be the same afterwards.
     */
    @Test
    public void check_if_modification_time_is_not_updated_on_parse_error() throws EXistException, InterruptedException, PermissionDeniedException, LockException, IOException, TriggerException, SAXException, DatabaseConfigurationException {
        
            
            IndexInfo info = storeXML(XML_FILENAME, VALID_XML);
            assertNotNull(info);
            DocumentImpl doc = info.getDocument();
            final XmldbURI docUri = doc.getFileURI();
            
            long modificationTimeBefore = doc.getMetadata().getLastModified();
            
            Thread.sleep(500);
            
            boolean threw = false;
            info = null;
            try {
                info = storeXML(XML_FILENAME, INVALID_XML);
            } catch (SAXException e) {
                threw = true;
            }
            assertTrue(threw);
            assertNull(info);
            
            doc = getDocument(docUri);
            assertNotNull(doc);
            
            long modificationTimeAfter = doc.getMetadata().getLastModified();
            assertEquals(modificationTimeBefore, modificationTimeAfter);
    }

   protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }

    private DocumentImpl getDocument(final XmldbURI uri) throws EXistException, PermissionDeniedException, DatabaseConfigurationException {
        DocumentImpl doc = null;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {
            assertNotNull(broker);

            final Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);

            doc = root.getDocument(broker, uri);

        }

        return doc;
    }

    private BinaryDocument storeBinary(String name,  String data, String mimeType) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException, DatabaseConfigurationException {
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        BinaryDocument binaryDoc = null;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
    		broker.saveCollection(transaction, root);
            assertNotNull(root);

            binaryDoc = root.addBinaryResource(transaction, broker, XmldbURI.create(name), data.getBytes(), mimeType);

            transact.commit(transaction);
        }

        return binaryDoc;
    }
    
    private IndexInfo storeXML(String name, String xml) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException, SAXException, DatabaseConfigurationException {
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        IndexInfo info = null;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
    		broker.saveCollection(transaction, root);
            assertNotNull(root);

            info = root.validateXMLResource(transaction, broker, XmldbURI.create(name), xml);
                    
            transact.commit(transaction);
        }

        return info;
    }
}
