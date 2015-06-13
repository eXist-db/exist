package org.exist.storage;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class ShutdownTest {

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
            "	<index>" +
            "       <lucene>" +
            "           <text match=\"/*\"/>" +
            "       </lucene>" +
            "	</index>" +
            "</collection>";

	private static String directory = "samples/shakespeare";
    
    private static File dir = null;
    static {
       final String existHome = System.getProperty("exist.home");
       final File existDir = existHome == null ? new File(".") : new File(existHome);
       dir = new File(existDir, directory);
    }

    @Test
	public void shutdown() throws EXistException, DatabaseConfigurationException, LockException, TriggerException, PermissionDeniedException, XPathException, IOException, CollectionConfigurationException {
		for (int i = 0; i < 2; i++) {
			storeAndShutdown();
		}
	}
	
	public void storeAndShutdown() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException, XPathException, CollectionConfigurationException {
		final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            Collection test;

            try(final Txn transaction = transact.beginTransaction()) {

                test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(test);
                broker.saveCollection(transaction, test);

	            final CollectionConfigurationManager mgr = broker.getBrokerPool().getConfigurationManager();
	            mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);

	            final File files[] = dir.listFiles(new FilenameFilter() {
		        	public boolean accept(final File dir, final String name) {
		        		return name.endsWith(".xml");
		        	}
		        });
	            assertNotNull(files);


                // store some documents.
	            for(final File f : files) {
                    try {
                        final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                        assertNotNull(info);
                        test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                    } catch (SAXException e) {
                        System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                    }
                }

                final XQuery xquery = broker.getXQueryService();
                assertNotNull(xquery);
                final Sequence result = xquery.execute("//SPEECH[ft:query(LINE, 'love')]", Sequence.EMPTY_SEQUENCE, AccessContext.TEST);
                assertNotNull(result);
                assertEquals(result.getItemCount(), 160);

                transact.commit(transaction);
            }

            try(final Txn transaction = transact.beginTransaction()) {

                broker.removeCollection(transaction, test);

                transact.commit(transaction);
            }
        }
		
		// shut down the database
        shutdownDB();
        
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
//	    		files[i].delete();;
//	        }
//        } catch (Exception e) {
//        	System.err.println("Error while deleting database files:\n" + e.getMessage());
//        	e.printStackTrace();
//        }
	}
	
	protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }
	
	protected void shutdownDB() {
		BrokerPool.stopAll(false);
	}
}
