package org.exist.storage.test;

import java.io.File;
import java.io.FilenameFilter;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import junit.framework.TestCase;
import junit.textui.TestRunner;

public class ShutdownTest extends TestCase {

	private static String directory = "samples/shakespeare";
    
    private static File dir = new File(directory);
    
	public ShutdownTest(String name) {
		super(name);
	}
	
	public void testShutdown() throws Exception {
		for (int i = 0; i < 5; i++) {
			storeAndShutdown();
			
			System.out.println("-----------------------------------------------------");
		}
	}
	
	public void storeAndShutdown() throws Exception {
		BrokerPool pool = startDB();
		DBBroker broker = null;
		try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            System.out.println("Transaction started ...");
            
            Collection test = broker.getOrCreateCollection(transaction, DBBroker.ROOT_COLLECTION + "/test");
            broker.saveCollection(transaction, test);
            
            File files[] = dir.listFiles(new FilenameFilter() {
	        	public boolean accept(File dir, String name) {
	        		if (name.endsWith(".xml"))
	        			return true;
	        		return false;
	        	}
	        });
            
            File f;
            IndexInfo info;
            
            // store some documents.
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                try {
                    info = test.validate(transaction, broker, f.getName(), new InputSource(f.toURI().toASCIIString()));
                    test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
            }
            
            XQuery xquery = broker.getXQueryService();
            Sequence result = xquery.execute("//SPEECH[LINE &= 'love']", Sequence.EMPTY_SEQUENCE);
            assertEquals(result.getLength(), 160);
            
            transact.commit(transaction);
            
            transaction = transact.beginTransaction();
            
            broker.removeCollection(transaction, test);
            
            transact.commit(transaction);
		} finally {
			pool.release(broker);
		}
		
		// shut down the database
        shutdown();
        System.out.println("Database stopped.");
        
        // try to remove the database files
//        try {
//	        File dataDir = new File("webapp/WEB-INF/data");
//	        File files[] = dataDir.listFiles(new FilenameFilter() {
//	        	public boolean accept(File dir, String name) {
//	        		if (name.endsWith(".dbx") || name.endsWith(".log"))
//	        			return true;
//	        		return false;
//	        	}
//	        });
//	        for (int i = 0; i < files.length; i++) {
//	        	System.out.println("Removing " + files[i].getAbsolutePath());
//	    		files[i].delete();
//	        }
//        } catch (Exception e) {
//        	System.err.println("Error while deleting database files:\n" + e.getMessage());
//        	e.printStackTrace();
//        }
	}
	
	protected BrokerPool startDB() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }
	
	protected void shutdown() {
		BrokerPool.stopAll(false);
	}
	
	public static void main(String[] args) throws Exception {
		TestRunner.run(ShutdownTest.class);
	}
}
