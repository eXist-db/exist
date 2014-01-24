package org.exist.storage;

import java.io.File;
import java.io.FilenameFilter;

import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ShutdownTest {

	private static String directory = "samples/shakespeare";
    
    private static File dir = null;
    static {
       String existHome = System.getProperty("exist.home");
       File existDir = existHome==null ? new File(".") : new File(existHome);
       dir = new File(existDir,directory);
    }

    @Test
	public void shutdown() {
		for (int i = 0; i < 2; i++) {
			storeAndShutdown();
			
			System.out.println("-----------------------------------------------------");
		}
	}
	
	public void storeAndShutdown() {
		BrokerPool pool = null;
		DBBroker broker = null;
		try {
			pool = startDB();
			assertNotNull(pool);
			broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            Txn transaction = transact.beginTransaction();
            assertNotNull(transaction);            
            System.out.println("Transaction started ...");
            
            Collection test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(test); 
            broker.saveCollection(transaction, test);
            
            File files[] = dir.listFiles(new FilenameFilter() {
	        	public boolean accept(File dir, String name) {
	        		if (name.endsWith(".xml"))
	        			return true;
	        		return false;
	        	}
	        });
            assertNotNull(files); 
            
            File f;
            IndexInfo info;
            
            // store some documents.
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                assertNotNull(f); 
                try {
                    info = test.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                    assertNotNull(info); 
                    test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
            }
            
            XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery); 
            Sequence result = xquery.execute("//SPEECH[ft:query(LINE, 'love')]", Sequence.EMPTY_SEQUENCE, AccessContext.TEST);
            assertNotNull(result); 
            assertEquals(result.getItemCount(), 160);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
            
            transaction = transact.beginTransaction();
            System.out.println("Transaction started ...");
            
            broker.removeCollection(transaction, test);
            
            transact.commit(transaction);
            System.out.println("Transaction commited ...");
        } catch (Exception e) {            
            fail(e.getMessage()); 
		} finally {
			pool.release(broker);
		}
		
		// shut down the database
        shutdownDB();
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
//	    		files[i].delete();;
//	        }
//        } catch (Exception e) {
//        	System.err.println("Error while deleting database files:\n" + e.getMessage());
//        	e.printStackTrace();
//        }
	}
	
	protected BrokerPool startDB() {
        try {
            Configuration config = new Configuration();
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {            
            fail(e.getMessage());
        }
        return null;
    }
	
	protected void shutdownDB() {
		BrokerPool.stopAll(false);
	}
}
