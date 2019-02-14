package org.exist.storage;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.*;
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

    private static Path dir = TestUtils.shakespeareSamples();

    @Test
	public void shutdown() throws EXistException, DatabaseConfigurationException, LockException, TriggerException, PermissionDeniedException, XPathException, IOException, CollectionConfigurationException {
		for (int i = 0; i < 2; i++) {
			storeAndShutdown();
		}
	}
	
	public void storeAndShutdown() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException, XPathException, CollectionConfigurationException {
		final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            Collection test;

            try(final Txn transaction = transact.beginTransaction()) {

                test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(test);
                broker.saveCollection(transaction, test);

	            final CollectionConfigurationManager mgr = broker.getBrokerPool().getConfigurationManager();
	            mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);

	            final List<Path> files = FileUtils.list(dir, XMLFilenameFilter.asPredicate());

                // store some documents.
	            for(final Path f : files) {
                    try {
                        final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(FileUtils.fileName(f)), new InputSource(f.toUri().toASCIIString()));
                        assertNotNull(info);
                        test.store(transaction, broker, info, new InputSource(f.toUri().toASCIIString()));
                    } catch (SAXException e) {
                        fail("Error found while parsing document: " + FileUtils.fileName(f) + ": " + e.getMessage());
                    }
                }

                final XQuery xquery = pool.getXQueryService();
                assertNotNull(xquery);
                final Sequence result = xquery.execute(broker, "//SPEECH[ft:query(LINE, 'love')]", Sequence.EMPTY_SEQUENCE);
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
