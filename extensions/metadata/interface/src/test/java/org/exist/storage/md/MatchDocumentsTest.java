/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.md;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class MatchDocumentsTest {

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    /** /db/test **/
    private static XmldbURI col1uri = TestConstants.TEST_COLLECTION_URI;
    /** /db/moved **/
    private static XmldbURI col2uri = XmldbURI.ROOT_COLLECTION_URI.append("moved");

    /** /db/test/test_string1.xml **/
    private static XmldbURI doc1uri = col1uri.append("test_string1.xml");
    /** /db/test/test_string2.xml **/
    private static XmldbURI doc2uri = col1uri.append("test_string2.xml");
    /** /db/test/test.binary **/
    private static XmldbURI doc3uri = col1uri.append("test.binary");
    private static XmldbURI doc4uri = col2uri.append("test_string1.xml");
    private static XmldbURI doc5uri = col2uri.append("test.binary");
    private static XmldbURI doc6uri = col1uri.append("test1.binary");
    
    private static String XML1 = "<test1/>";
    private static String XML2 = "<test2/>";

    private static String BINARY = "test";

    private static String KEY1 = "key1";
    private static String KEY2 = "key2";

    private static String VALUE1 = "value1";
    private static String VALUE2 = "value2";
    
    private static BrokerPool pool;

    @Test
	public void deleteCollection() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc1Metadata = md.getMetas(doc1uri);
    	assertNotNull(doc1Metadata);

    	final Metas doc3Metadata = md.getMetas(doc3uri);
    	assertNotNull(doc3Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            final DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add metas for XML document
	    	doc1Metadata.put(KEY1, doc2);
	    	doc1Metadata.put(KEY2, VALUE1);

	    	//add metas for binaty document
	    	doc3Metadata.put(KEY1, VALUE2);
	    	doc3Metadata.put(KEY2, doc2);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc1uri, matching.get(0).getURI());

	    	matching = md.matchDocuments(KEY1, VALUE2);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc3uri, matching.get(0).getURI());

            final TransactionManager txnManager = pool.getTransactionManager();

	        try(final Txn txn = txnManager.beginTransaction()) {
	            broker.removeCollection(txn, col1);
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	    	matching = md.matchDocuments(KEY2, VALUE1);
	    	assertEquals(0, matching.size());

	    	matching = md.matchDocuments(KEY1, VALUE2);
	    	assertEquals(0, matching.size());

        }
    }

    @Test
	public void moveCollection() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc1Metadata = md.getMetas(doc1uri);
    	assertNotNull(doc1Metadata);

    	final Metas doc3metadata = md.getMetas(doc3uri);
    	assertNotNull(doc3metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK);
                final Collection parentCol = broker.openCollection(col2uri.removeLastSegment(), LockMode.WRITE_LOCK)) {

            final DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add metas for XML document
	    	doc1Metadata.put(KEY1, doc2);
	    	doc1Metadata.put(KEY2, VALUE1);

	    	//add metas for binary document
	    	doc3metadata.put(KEY1, VALUE2);
	    	doc3metadata.put(KEY2, doc2);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc1uri, matching.get(0).getURI());

	    	matching = md.matchDocuments(KEY1, VALUE2);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc3uri, matching.get(0).getURI());


            final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {
	            broker.moveCollection(txn, col1, parentCol, col2uri.lastSegment());
	            txnManager.commit(txn);
	        }

	    	matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc4uri, matching.get(0).getURI());

        }
    }

    @Test
	public void renameXMLResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc1Metadata = md.getMetas(doc1uri);
    	assertNotNull(doc1Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            final DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add first key-value
	    	doc1Metadata.put(KEY1, doc2);
	    	doc1Metadata.put(KEY2, VALUE1);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc1uri, matching.get(0).getURI());

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {
                final DocumentImpl doc1 = col1.getDocument(broker, doc1uri.lastSegment());
	            broker.moveResource(txn, doc1, col, doc2uri.lastSegment());
	            txnManager.commit(txn);
	        } catch (Exception e) {
                e.printStackTrace();
	            fail(e.getMessage());
            }

	    	matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc2uri, matching.get(0).getURI());

        }
    }

    @Test
	public void moveXMLResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc1Metadata = md.getMetas(doc1uri);
    	assertNotNull(doc1Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add first key-value
	    	doc1Metadata.put(KEY1, doc2);
	    	doc1Metadata.put(KEY2, VALUE1);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc1uri, matching.get(0).getURI());

            final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {
	            final Collection col2 = broker.getOrCreateCollection(txn, col2uri);
	    		broker.saveCollection(txn, col2);

                final DocumentImpl doc1 = col1.getDocument(broker, doc1uri.lastSegment());
	            broker.moveResource(txn, doc1, col2, doc4uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	    	matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc4uri, matching.get(0).getURI());

        }
    }

    /*
     * Set to @Ignore because even though it seems to delete
     * the XML document /db/test/test_string1.xml
     * when @After/clean() function is called
     * it causes an error: As it thinks the document is
     * still present but then cannot delete it?!?
     */
    @Ignore
    @Test
	public void deleteXMLResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc1Metadata = md.getMetas(doc1uri);
    	assertNotNull(doc1Metadata);

        //add some test key-values to metadata of doc1
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            final DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

            //add first key-value
            doc1Metadata.put(KEY1, doc2);
            doc1Metadata.put(KEY2, VALUE1);

            List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

            assertEquals(1, matching.size());
            assertEquals(doc1uri, matching.get(0).getURI());


            final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {
                DocumentImpl doc1 = col1.getDocument(broker, doc1uri.lastSegment());
                broker.removeXMLResource(txn, doc1);
                broker.saveCollection(txn, col1);
                txnManager.commit(txn);
            } catch(Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }

            //check the metadata was deleted
            matching = md.matchDocuments(KEY2, VALUE1);
            assertEquals(0, matching.size());

        }
    }

    @Test
	public void renameBinaryResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc3Metadata = md.getMetas(doc3uri);
    	assertNotNull(doc3Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());
	    	//add first key-value
	    	doc3Metadata.put(KEY1, doc2);
	    	doc3Metadata.put(KEY2, VALUE1);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc3uri, matching.get(0).getURI());

            final TransactionManager txnManager = pool.getTransactionManager();
            final Txn txn = txnManager.beginTransaction();
	        try {
                DocumentImpl doc3 = col1.getDocument(broker, doc3uri.lastSegment());
	            broker.moveResource(txn, doc3, col1, doc6uri.lastSegment());
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        } finally {
                txnManager.close(txn);
            }

	    	matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc6uri, matching.get(0).getURI());

        }
    }

    @Test
	public void moveBinaryResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc3Metadata = md.getMetas(doc3uri);
    	assertNotNull(doc3Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add first key-value
	    	doc3Metadata.put(KEY1, doc2);
	    	doc3Metadata.put(KEY2, VALUE1);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc3uri, matching.get(0).getURI());

            final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {
	            Collection col2 = broker.getOrCreateCollection(txn, col2uri);
	    		broker.saveCollection(txn, col2);

                DocumentImpl doc3 = col1.getDocument(broker, doc3uri.lastSegment());
	        	broker.moveResource(txn, doc3, col2, doc5uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	    	matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc5uri, matching.get(0).getURI());

        }
    }

    @Test
	public void deleteBinaryResource() throws Exception {
    	final MetaData md = MetaData.get();
    	assertNotNull(md);

    	final Metas doc3Metadata = md.getMetas(doc3uri);
    	assertNotNull(doc3Metadata);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK)) {

            DocumentImpl doc2 = col1.getDocument(broker, doc2uri.lastSegment());

	    	//add first key-value
	    	doc3Metadata.put(KEY1, doc2);
	    	doc3Metadata.put(KEY2, VALUE1);

	    	List<DocumentImpl> matching = md.matchDocuments(KEY2, VALUE1);

	    	assertEquals(1, matching.size());
	    	assertEquals(doc3uri, matching.get(0).getURI());

            final TransactionManager txnManager = pool.getTransactionManager();

	        try(final Txn txn = txnManager.beginTransaction()) {
                DocumentImpl doc3 = col1.getDocument(broker, doc3uri.lastSegment());
	            broker.removeXMLResource(txn, doc3);
                broker.saveCollection(txn, col1);
	            txnManager.commit(txn);
	        } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }

	    	matching = md.matchDocuments(KEY2, VALUE1);
	    	assertEquals(0, matching.size());

        }
    }

	@Before
    public void startDB() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {

        final Path confFile = ConfigurationHelper.lookup("conf.xml");
        Configuration config = new Configuration(confFile.toAbsolutePath().toString());
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
        pool.getPluginsManager().addPlugin("org.exist.storage.md.Plugin");

        final TransactionManager txnManager = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = txnManager.beginTransaction();
                final Collection root = broker.getOrCreateCollection(txn, col1uri)) {
            assertNotNull(root);
            broker.saveCollection(txn, root);

            final CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

            //store test data
            IndexInfo info = root.validateXMLResource(txn, broker, doc1uri.lastSegment(), XML1);
            root.store(txn, broker, info, XML1);

            info = root.validateXMLResource(txn, broker, doc2uri.lastSegment(), XML2);
            root.store(txn, broker, info, XML2);

            root.addBinaryResource(txn, broker, doc3uri.lastSegment(), BINARY.getBytes(), null);

            txnManager.commit(txn);
        }
    }

    @After
    public void cleanup() throws TriggerException, PermissionDeniedException, EXistException, IOException {
    	clean();
    	shutdown();
    }

    //@AfterClass
    private void shutdown() {
        BrokerPool.stopAll(false);
        pool = null;
    }

    private void clean() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final TransactionManager txnManager = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn txn = txnManager.beginTransaction();
                final Collection col1 = broker.openCollection(col1uri, LockMode.WRITE_LOCK);
                final Collection col2 = broker.openCollection(col2uri, LockMode.WRITE_LOCK)) {

            if(col1 != null) {
            	broker.removeCollection(txn, col1);
            }


            if(col2 != null) {
            	broker.removeCollection(txn, col2);
            }

        	txnManager.commit(txn);
        }
    }
}
