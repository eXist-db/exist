package org.exist.storage;


import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import org.apache.commons.io.FileUtils;
import org.junit.BeforeClass;
import org.exist.dom.BinaryDocument;
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
        File home = ConfigurationHelper.getExistHome();
        File data = new File(home, "webapp/WEB-INF/data");

        File dataFiles[] = data.listFiles(new FilenameFilter(){
            @Override
            public boolean accept(File file, String name) {
                return name.endsWith(".dbx") || name.endsWith(".journal") || name.endsWith(".log");
            }
        });
        for(File dataFile : dataFiles){
            dataFile.delete();
        }

        for(String subFolderName : new String[]{"journal", "fs", "sanity", "lucene"} ) {
            File subFolder = new File(data, subFolderName);
            if(subFolder.exists()) {
                FileUtils.deleteDirectory(subFolder);
            }
        }
    }

    @Test
    public void check_MimeType_is_preserved() throws EXistException, InterruptedException {

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

    private void stopDatabase(Main database) {
        try {
            database.shutdown();
        } catch (Exception e) {
            // do not fail. exceptions may occur at this point.
            e.printStackTrace();
        }
    }

    private BinaryDocument getBinary(XmldbURI uri) throws EXistException {
        BinaryDocument binaryDoc = null;
        Database pool = BrokerPool.getInstance();;

        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);

            binaryDoc = (BinaryDocument)root.getDocument(broker, uri);

        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }

        return binaryDoc;
    }

    private BinaryDocument storeBinary(String name,  String data, String mimeType) throws EXistException {
    	BinaryDocument binaryDoc = null;
        Database pool = BrokerPool.getInstance();;

        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
    		broker.saveCollection(transaction, root);
            assertNotNull(root);

            binaryDoc = root.addBinaryResource(transaction, broker, XmldbURI.create(name), data.getBytes(), mimeType);

            if(transact != null) {
                transact.commit(transaction);
            }
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            pool.release(broker);
        }

        return binaryDoc;
    }
}
