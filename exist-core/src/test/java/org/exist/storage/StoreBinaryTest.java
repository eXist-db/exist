package org.exist.storage;

import java.io.IOException;
import java.util.Optional;

import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.junit.*;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;

import static org.junit.Assert.*;

/**
 *
 * @author aretter
 */
public class StoreBinaryTest {

    @Test
    public void check_MimeType_is_preserved() throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException, DatabaseConfigurationException {

        final String xqueryMimeType = "application/xquery";
        final String xqueryFilename = "script.xql";
        final String xquery = "current-dateTime()";

        //store the xquery document
        BinaryDocument binaryDoc = storeBinary(xqueryFilename, xquery, xqueryMimeType);
        assertNotNull(binaryDoc);
        assertEquals(xqueryMimeType, binaryDoc.getMetadata().getMimeType());

        //make a note of the binary documents uri
        final XmldbURI binaryDocUri = binaryDoc.getFileURI();

        //restart the database
        existEmbeddedServer.restart();

        //retrieve the xquery document
        binaryDoc = getBinary(binaryDocUri);
        assertNotNull(binaryDoc);

        //check the mimetype has been preserved across database restarts
        assertEquals(xqueryMimeType, binaryDoc.getMetadata().getMimeType());
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @After
    public void removeTestResources() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            final Collection testCollection = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            broker.removeCollection(transaction, testCollection);
            transaction.commit();
        }
    }

    private BinaryDocument getBinary(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        BinaryDocument binaryDoc = null;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {
            assertNotNull(broker);

            final Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);

            binaryDoc = (BinaryDocument)root.getDocument(broker, uri);

        }

        return binaryDoc;
    }

    private BinaryDocument storeBinary(String name,  String data, String mimeType) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
}
