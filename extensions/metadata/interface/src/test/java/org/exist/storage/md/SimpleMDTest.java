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
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SimpleMDTest {

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    /** /db/test **/
    private static XmldbURI col1uri = TestConstants.TEST_COLLECTION_URI;
    /** /db/test/test2 **/
    private static XmldbURI col2uri = TestConstants.TEST_COLLECTION_URI2;
    /** /db/moved **/
    private static XmldbURI col3uri = XmldbURI.ROOT_COLLECTION_URI.append("moved");

    /** /db/test/test_string.xml **/
    private static XmldbURI doc1uri = col1uri.append("test_string.xml");
    /** /db/test/test_string2.xml **/
    private static XmldbURI doc2uri = col1uri.append("test_string2.xml");
    /** /db/test/test2/test_2.xml **/
    private static XmldbURI doc3uri = col2uri.append("test_2.xml");
    /** /db/moved/test_string.xml **/
    private static XmldbURI doc4uri = col3uri.append("test_string.xml");
    /** /db/test/test.binary **/
    private static XmldbURI doc5uri = col1uri.append("test.binary");
    /** /db/moved/test.binary **/
//    private static XmldbURI doc6uri = col3uri.append("test.binary");
    
    private static String XML1 = "<test/>";
    private static String XML2 = "<test2/>";

    private static String BINARY = "test";

    private static String wrongXML = "<test>";

    private static String KEY1 = "key1";
    private static String VALUE1 = "value1";

    private static String KEY2 = "key2";
    private static String VALUE2 = "value2";
    
    private static BrokerPool pool;
    private static DocumentImpl doc1 = null;
    private static DocumentImpl doc2 = null;

    @Test
	public void test_00() throws Exception {
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	
    	assertNotNull(md);
    	
    	Metas docMD = MetaData.get().getMetas(doc1uri);
    	
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	assertNotNull(uuid);
    	
    	DocumentImpl doc = MetaData.get().getDocument(uuid);
    	assertNotNull(doc);
    	assertTrue(doc1.equals(doc));

    	//add first key-value
    	docMD.put(KEY1, VALUE1);
    	
    	Meta meta = docMD.get(KEY1);
    	assertNotNull(meta);

    	assertEquals(VALUE1, meta.getValue());

    	//add second key-value
    	docMD.put(KEY2, VALUE2);
    	
    	meta = docMD.get(KEY2);
    	assertNotNull(meta);

    	assertEquals(VALUE2, meta.getValue());

    	//replace first key-value
    	docMD.put(KEY1, VALUE2);
    	
    	meta = docMD.get(KEY1);
    	assertNotNull(meta);

    	assertEquals(VALUE2, meta.getValue());
    	
    	//second document
    	docMD = MetaData.get().getMetas(doc2uri);
    	
    	assertNotNull(docMD);
    	
    	uuid = docMD.getUUID();
    	assertNotNull(uuid);
    	
    	doc = MetaData.get().getDocument(uuid);
    	assertNotNull(doc);
    	assertTrue(doc2.equals(doc));

    	//add first key-value
    	docMD.put(KEY1, VALUE2);
    	
    	meta = docMD.get(KEY1);
    	assertNotNull(meta);

    	assertEquals(VALUE2, meta.getValue());

    	//add second key-value
    	docMD.put(KEY2, VALUE1);
    	
    	meta = docMD.get(KEY2);
    	assertNotNull(meta);

    	assertEquals(VALUE1, meta.getValue());

    	//replace first key-value
    	docMD.put(KEY1, VALUE1);
    	
    	meta = docMD.get(KEY1);
    	assertNotNull(meta);

    	assertEquals(VALUE1, meta.getValue());

    	cleanup();
    }

    @Test
	public void test_01() throws Exception {
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = MetaData.get().getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);
    	docMD.put(KEY2, VALUE2);
    	
    	clean();
    	
    	Metas _docMD = MetaData.get().getMetas(doc1uri);
    	assertNull(_docMD);

    	Meta meta = docMD.get(KEY1);
    	assertNull(meta);
    	
    	cleanup();
	}
    
    @Test
	public void test_02() throws Exception {
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);
    	docMD.put(KEY2, VALUE2);
    	
    	docMD = md.getMetas(doc2uri);
    	assertNotNull(docMD);

    	docMD.put(KEY2, VALUE2);

    	List<DocumentImpl> ds = md.matchDocuments(KEY1, VALUE1);
    	
    	assertEquals(1, ds.size());
    	assertEquals(doc1, ds.get(0));

    	ds = md.matchDocuments(KEY2, VALUE2);
    	
    	assertEquals(2, ds.size());

    	cleanup();
	}

	//keep uuid after restart & store new data
    @Test
	public void _test_03() throws Exception {
    	
    	startDB();

    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	String docUUID = null;

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

	    	Metas docMD = md.getMetas(doc1uri);
	    	assertNotNull(docMD);
	    	
	    	docUUID = docMD.getUUID();
	    	
	    	//set metas
	    	docMD.put(KEY1, VALUE1);
	    	docMD.put(KEY2, VALUE2);
	    	
	        final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {

		    	IndexInfo info = col.validateXMLResource(txn, broker, doc1uri.lastSegment(), XML1);
	            assertNotNull(info);
	            col.store(txn, broker, info, XML1, false);
	            
	            //XXX: need to simulate unfinished transaction & crash
	            txnManager.commit(txn);

	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }
	        
        }
	        
        shutdown();
    	startDB();

    	md = MetaData.get();
    	assertNotNull(md);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection root = broker.getCollection(col1uri);
        	assertNotNull(root);

	        final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {

		    	IndexInfo info = root.validateXMLResource(txn, broker, doc1uri.lastSegment(), XML1);
	            assertNotNull(info);
	            root.store(txn, broker, info, XML1, false);

	            txnManager.commit(txn);
	            
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	        Metas docMD = md.getMetas(doc1uri);
	    	assertNotNull(docMD);
	    	
	    	assertNotSame(docUUID, docMD.getUUID());

        }

    	cleanup();
	}
	
    //resource rename
	@Test
	public void test_04() throws Exception {
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {
            
	            broker.moveResource(txn, doc1, col, doc2uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	    	docMD = md.getMetas(doc2uri);
	    	assertNotNull(docMD);
	    	
	    	assertEquals(uuid, docMD.getUUID());

        }
        

    	cleanup();
	}

    //resource move
	@Test
	public void test_recource_move() throws Exception {
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection col = broker.getCollection(col2uri);
        	assertNotNull(col);

        	final TransactionManager txnManager = pool.getTransactionManager();
	        try(final Txn txn = txnManager.beginTransaction()) {
	            broker.moveResource(txn, doc1, col, doc3uri.lastSegment());
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	    	docMD = md.getMetas(doc3uri);
	    	assertNotNull(docMD);
	    	
	    	assertEquals(uuid, docMD.getUUID());

        }

    	cleanup();
	}

    //collection move
	@Test
	public void test_collection_move() throws Exception {
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection parent = broker.getCollection(col3uri.removeLastSegment());
        	assertNotNull(parent);

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {
	            
	            broker.moveCollection(txn, col, parent, col3uri.lastSegment());
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

            Collection moved = broker.getCollection(col3uri);
        	assertNotNull(moved);

	        docMD = md.getMetas(doc4uri);
	    	assertNotNull(docMD);
	    	
	    	assertEquals(uuid, docMD.getUUID());
	    	assertEquals(VALUE1, docMD.get(KEY1).getValue());

	    	Collection nCol = null;
            try(final Txn txn = txnManager.beginTransaction()) {
	            nCol = broker.getOrCreateCollection(txn, col1uri);
	            assertNotNull(nCol);
	            broker.saveCollection(txn, nCol);
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	        docMD = md.getMetas(doc1uri);
	    	assertNull(docMD);
	    	
	    	DocumentImpl doc = md.getDocument(uuid);
	    	assertNotNull(doc);
	    	assertEquals(doc4uri.toString(), doc.getURI().toString());
	    	
        }

    	cleanup();
	}

    //collection move with subcollections
	@Test
	public void test_collection_move_with_subcollection() throws Exception {
    	startDB();
    	
        Collection test2;
        DocumentImpl doc3;

        final TransactionManager txnManager = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn txn = txnManager.beginTransaction()) {
            
            test2 = broker.getOrCreateCollection(txn, col2uri);
            assertNotNull(test2);
            broker.saveCollection(txn, test2);

            doc3 = storeDocument(txn, broker, test2, doc3uri, XML1);
            assertNotNull(doc3);

            txnManager.commit(txn);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }

    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);

    	//set second document
    	docMD = md.getMetas(doc3uri);
    	assertNotNull(docMD);
    	
    	String uuid2 = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY2, VALUE2);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection parent = broker.getCollection(col3uri.removeLastSegment());
        	assertNotNull(parent);

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            try(final Txn txn = txnManager.beginTransaction()) {
	            
	            broker.moveCollection(txn, col, parent, col3uri.lastSegment());
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

            Collection moved = broker.getCollection(col3uri);
        	assertNotNull(moved);

	        docMD = md.getMetas(doc4uri);
	    	assertNotNull(docMD);
	    	
	    	assertEquals(uuid, docMD.getUUID());
	    	assertEquals(VALUE1, docMD.get(KEY1).getValue());

	        docMD = md.getMetas(XmldbURI.create("/db/moved/test2/test_2.xml"));
	    	assertNotNull(docMD);
	    	
	    	assertEquals(uuid2, docMD.getUUID());
	    	assertEquals(VALUE2, docMD.get(KEY2).getValue());

	    	Collection nCol = null;
            try(final Txn txn = txnManager.beginTransaction()) {
	            
	            nCol = broker.getOrCreateCollection(txn, col1uri);
	            assertNotNull(nCol);
	            broker.saveCollection(txn, nCol);
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

	        docMD = md.getMetas(doc1uri);
	    	assertNull(docMD);
	    	
	    	DocumentImpl doc = md.getDocument(uuid);
	    	assertNotNull(doc);
	    	assertEquals(doc4uri.toString(), doc.getURI().toString());
	    	
        }

    	cleanup();
	}

	//collection copy
	@Test
	public void test_collection_copy() throws Exception {
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid = docMD.getUUID();
    	
    	//add first key-value
    	docMD.put(KEY1, VALUE1);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection parent = broker.getCollection(col3uri.removeLastSegment());
        	assertNotNull(parent);

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {
	            
	            broker.copyCollection(txn, col, parent, col3uri.lastSegment());
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

            Collection moved = broker.getCollection(col3uri);
        	assertNotNull(moved);

	        docMD = md.getMetas(doc4uri);
	    	assertNotNull(docMD);
	    	
	    	assertNotSame(uuid, docMD.getUUID());
	    	assertEquals(VALUE1, docMD.get(KEY1).getValue());

	        docMD = md.getMetas(doc1uri);
	    	assertNotNull(docMD);
	    	
	    	DocumentImpl doc = md.getDocument(uuid);
	    	assertNotNull(doc);
	    	assertEquals(doc1uri.toString(), doc.getURI().toString());
	    	
        }

    	cleanup();
	}

    //collection copy
	@Test
	public void test_collection_delete() throws Exception {
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	String uuid1 = docMD.getUUID();
    	
    	docMD = md.getMetas(doc5uri);
    	assertNotNull(docMD);
    	
    	String uuid2 = docMD.getUUID();

    	//add first key-value
    	docMD.put(KEY1, VALUE1);

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            Collection parent = broker.getCollection(col3uri.removeLastSegment());
        	assertNotNull(parent);

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            final TransactionManager txnManager = pool.getTransactionManager();
            try(final Txn txn = txnManager.beginTransaction()) {
	            
	            broker.removeCollection(txn, col);
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            fail(e.getMessage());
	        }

            col = broker.getCollection(col1uri);
        	assertNull(col);

	        docMD = md.getMetas(doc1uri);
	    	assertNull(docMD);
	    	
	        docMD = md.getMetas(doc2uri);
	    	assertNull(docMD);
	    	
	        docMD = md.getMetas(doc5uri);
	    	assertNull(docMD);

	    	DocumentImpl doc = md.getDocument(uuid1);
	    	assertNull(doc);
	    	
	    	doc = md.getDocument(uuid2);
	    	assertNull(doc);

        }

    	cleanup();
	}

	@Test
	public void test_08() throws Exception {

        try {
            final Path confFile = ConfigurationHelper.lookup("conf.xml");
            final Configuration config = new Configuration(confFile.toAbsolutePath().toString());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	pool.getPluginsManager().addPlugin("org.exist.storage.md.Plugin");


            try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

                final MetaData md = MetaData.get();
                assertNotNull(md);

                final TransactionManager txnManager = pool.getTransactionManager();

                try(final Txn txn = txnManager.beginTransaction()) {

                    final Collection root = broker.getOrCreateCollection(txn, col1uri);
                    assertNotNull(root);
                    broker.saveCollection(txn, root);

                    final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                    mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

                    final IndexInfo info = root.validateXMLResource(txn, broker, doc1uri.lastSegment(), XML1);
                    assertNotNull(info);
                    root.store(txn, broker, info, XML1, false);

                    txnManager.commit(txn);
                } catch (Exception e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

                Metas docMD = md.getMetas(doc1uri);
                assertNotNull(docMD);

                final String uuid = docMD.getUUID();

                try(final Txn txn = txnManager.beginTransaction()) {

                    final Collection root = broker.getOrCreateCollection(txn, col1uri);
                    assertNotNull(root);
                    broker.saveCollection(txn, root);

                    final CollectionConfigurationManager mgr = pool.getConfigurationManager();
                    mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

                    final IndexInfo info = root.validateXMLResource(txn, broker, doc1uri.lastSegment(), wrongXML);
                    assertNotNull(info);
                    root.store(txn, broker, info, wrongXML, false);

                    txnManager.commit(txn);
                } catch (Exception e) {
                    e.printStackTrace();
                    //txnManager.abort(txn);
                    //fail(e.getMessage());
                }

                docMD = md.getMetas(doc1uri);
                assertNotNull(docMD);

                assertEquals(uuid, docMD.getUUID());
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
			cleanup();
		}
	}

//	private DocumentImpl getDoc(DBBroker broker, Collection col, XmldbURI uri) throws PermissionDeniedException {
//        DocumentImpl doc = col.getDocument(broker, uri);
//    	assertNotNull(doc);
//		
//    	return doc;
//	}
	
	private static DocumentImpl storeDocument(Txn txn, DBBroker broker, Collection col, XmldbURI uri, String data) throws TriggerException, EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        IndexInfo info = col.validateXMLResource(txn, broker, uri.lastSegment(), data);
        assertNotNull(info);
        col.store(txn, broker, info, data, false);
        assertNotNull(info.getDocument());

        return info.getDocument();
	}

	//@BeforeClass
    public static void startDB() throws DatabaseConfigurationException, EXistException {
        final Path confFile = ConfigurationHelper.lookup("conf.xml");
        final Configuration config = new Configuration(confFile.toAbsolutePath().toString());
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
        assertNotNull(pool);
        pool.getPluginsManager().addPlugin("org.exist.storage.md.MDStorageManager");

        final TransactionManager txnManager = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn txn = txnManager.beginTransaction()) {
            
            clean(broker, txn);

            Collection root = broker.getOrCreateCollection(txn, col1uri);
            assertNotNull(root);
            broker.saveCollection(txn, root);

            Collection test2 = broker.getOrCreateCollection(txn, col2uri);
            assertNotNull(test2);
            broker.saveCollection(txn, test2);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

            doc1 = storeDocument(txn, broker, root, doc1uri, XML1);
            doc2 = storeDocument(txn, broker, root, doc2uri, XML2);
            root.addBinaryResource(txn, broker, doc5uri.lastSegment(), BINARY.getBytes(), null);

            txnManager.commit(txn);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    //@AfterClass
    public static void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        clean();
    	shutdown();
    }

    //@AfterClass
    private static void shutdown() {
        BrokerPool.stopAll(false);
        pool = null;
        doc1 = null;
        doc2 = null;
    }

    private static void clean() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final TransactionManager txnManager = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn txn = txnManager.beginTransaction()) {
            clean(broker, txn);
            txn.commit();
        }
    }

    private static void clean(final DBBroker broker, final Txn txn) throws PermissionDeniedException, IOException, TriggerException {
        Collection col = broker.getOrCreateCollection(txn, col1uri);
        assertNotNull(col);
        broker.removeCollection(txn, col);

//            col = broker.getOrCreateCollection(txn, col2uri);
//            assertNotNull(col);
//        	broker.removeCollection(txn, col);

        col = broker.getOrCreateCollection(txn, col3uri);
        assertNotNull(col);
        broker.removeCollection(txn, col);
    }
}
