package org.exist.storage;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
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
import org.exist.start.Main;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.junit.Test;
import static org.junit.Assert.*;
import org.exist.util.ConfigurationHelper;

/**
 *
 * @author aretter
 */
public class StoreBinaryTest {

    @BeforeClass
    public static void ensureCleanDatabase() throws IOException {
        final File home = ConfigurationHelper.getExistHome();
        final File data = new File(home, "webapp/WEB-INF/data");

        final File[] dataFiles = data.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(final File dir, final String name) {
                return !(name.equals("RECOVERY") || name.equals("README") || name.equals(".DO_NOT_DELETE"));
            }
        });

        for(final File dataFile : dataFiles) {
            FileUtils.deleteQuietly(dataFile);
        }
    }

    @Test
    public void check_MimeType_is_preserved() throws EXistException, InterruptedException, PermissionDeniedException, LockException, IOException, TriggerException {

        final String xqueryMimeType = "application/xquery";
        final String xqueryFilename = "script.xql";
        final String xquery = "current-dateTime()";

        Main database = startupDatabase();
        try {
            //store the xquery document
            BinaryDocument binaryDoc = storeBinary(xqueryFilename, xquery, xqueryMimeType);
            assertNotNull(binaryDoc);
            assertEquals(xqueryMimeType, binaryDoc.getMetadata().getMimeType());

            //make a note of the binary documents uri
            final XmldbURI binaryDocUri = binaryDoc.getFileURI();

            //restart the database
            stopDatabase(database);
            Thread.sleep(3000);
            database = startupDatabase();

            //retrieve the xquery document
            binaryDoc = getBinary(binaryDocUri);
            assertNotNull(binaryDoc);

            //check the mimetype has been preserved across database restarts
            assertEquals(xqueryMimeType, binaryDoc.getMetadata().getMimeType());

        } finally {
            stopDatabase(database);
        }
    }

    private Main startupDatabase() {
        Main database = new org.exist.start.Main("jetty");
        database.run(new String[]{"jetty"});
        return database;
    }

    private void stopDatabase(final Main database) {
        try {
            database.shutdown();
        } catch (Exception e) {
            // do not fail. exceptions may occur at this point.
            e.printStackTrace();
        }
    }

    private BinaryDocument getBinary(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        BinaryDocument binaryDoc = null;
        final Database pool = BrokerPool.getInstance();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {
            assertNotNull(broker);

            final Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);

            binaryDoc = (BinaryDocument)root.getDocument(broker, uri);

        }

        return binaryDoc;
    }

    private BinaryDocument storeBinary(String name,  String data, String mimeType) throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final Database pool = BrokerPool.getInstance();;
        final TransactionManager transact = pool.getTransactionManager();

        BinaryDocument binaryDoc = null;
        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
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
