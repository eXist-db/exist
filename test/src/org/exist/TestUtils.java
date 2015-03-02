package org.exist;

import org.apache.commons.io.FileUtils;
import org.exist.collections.Collection;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.start.Main;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;

/**
 * Created by IntelliJ IDEA.
 * User: wolf
 * Date: Oct 29, 2007
 * Time: 4:28:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class TestUtils {
	
    public static void cleanupDB() {

        try {
            BrokerPool pool = BrokerPool.getInstance();
            assertNotNull(pool);
            try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

                // Remove all collections below the /db root, except /db/system
                Collection root = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI);
                assertNotNull(root);
                for (Iterator<DocumentImpl> i = root.iterator(broker); i.hasNext(); ) {
                    DocumentImpl doc = i.next();
                    root.removeXMLResource(transaction, broker, doc.getURI().lastSegment());
                }
                broker.saveCollection(transaction, root);
                for (Iterator<XmldbURI> i = root.collectionIterator(broker); i.hasNext(); ) {
                    XmldbURI childName = i.next();
                    if (childName.equals("system"))
                        continue;
                    Collection childColl = broker.getOrCreateCollection(transaction, XmldbURI.ROOT_COLLECTION_URI.append(childName));
                    assertNotNull(childColl);
                    broker.removeCollection(transaction, childColl);
                }

                // Remove /db/system/config/db and all collection configurations with it
                Collection config = broker.getOrCreateCollection(transaction,
                        XmldbURI.create(XmldbURI.CONFIG_COLLECTION + "/db"));
                assertNotNull(config);
                broker.removeCollection(transaction, config);
                
                pool.getTransactionManager().commit(transaction);
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }


    public static Main startupDatabase() {
        Main database = new org.exist.start.Main("jetty");
        database.run(new String[]{"jetty"});
        return database;
    }

    public static void stopDatabase(Main database) {
       	database.shutdown();
    }
    
    /**
     * Moves the current data directory to a backup location and places a 
     * clean data directory in it's place. This way we don't loose data when 
     * we run a unit test
     * 
     * @return the path to the data backup directory
     * @throws IOException
     * @author Patrick Bosek <patrick.bosek@jorsek.com>
     * @throws DatabaseConfigurationException 
     */
    public static String moveDataDirToTempAndCreateClean() throws IOException, DatabaseConfigurationException {
    	
		Configuration conf = new Configuration();
		
		String dataDirPath = (String) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);
		
		if(dataDirPath == null || dataDirPath.equals(""))
			throw new DatabaseConfigurationException("Could not find configuration for data directory");
		
		dataDirPath = dataDirPath.replaceAll("/$", "");
		
//		java.util.GregorianCalendar cal = new java.util.GregorianCalendar();
//		String dateString = cal.toString();
		
		File data = new File(dataDirPath);
		File dataBackup = new File(dataDirPath + ".temp-test-bak");
		
		data.renameTo(dataBackup);
		
		data = new File(dataDirPath);
		data.mkdir();
		
		return dataBackup.getAbsolutePath();
	}
	
	/**
	 * Restores the data directory from before the test run and moves
	 * the data directory used durring the test to data.last-test-run
	 * so it can be inspected if desired.
	 * 
	 * @param backupDataDirPath
	 * @throws IOException
	 * @author Patrick Bosek <patrick.bosek@jorsek.com>
	 * @throws DatabaseConfigurationException 
	 */
	public static void moveDataDirBack(String backupDataDirPath) throws IOException, DatabaseConfigurationException {

		Configuration conf = new Configuration();
		
		String dataDirPath = (String) conf.getProperty(BrokerPool.PROPERTY_DATA_DIR);
		dataDirPath = dataDirPath.replaceAll("/$", "");
		
		File lastTestRunDataDir = new File(dataDirPath + ".last-test-run");
		
		if(lastTestRunDataDir.exists()) FileUtils.deleteDirectory(lastTestRunDataDir);
		
		File data = new File(dataDirPath);
		
		data.renameTo(lastTestRunDataDir);
		
		File dataBackup = new File(backupDataDirPath);
		
		dataBackup.renameTo(data);
	}
}
