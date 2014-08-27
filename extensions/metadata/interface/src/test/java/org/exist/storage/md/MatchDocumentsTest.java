/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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

import java.io.File;
import java.util.List;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.XmldbURI;
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
    private static DocumentImpl doc1 = null;
    private static DocumentImpl doc2 = null;
    private static DocumentImpl doc3 = null;

    @Test
	public void test_deleteCollection() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	Metas bDocMD = md.getMetas(doc3uri);
    	assertNotNull(bDocMD);

    	DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add metas for XML document
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	//add metas for binaty document
	    	bDocMD.put(KEY1, VALUE2);
	    	bDocMD.put(KEY2, doc2);

	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc1uri, list.get(0).getURI());

	    	list = md.matchDocuments(KEY1, VALUE2);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc3uri, list.get(0).getURI());

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	System.out.println("DELETE...");
        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
	            
	            broker.removeCollection(txn, col);
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	assertEquals(0, list.size());

	    	list = md.matchDocuments(KEY1, VALUE2);
	    	assertEquals(0, list.size());
	    	
        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_moveCollection() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
    	Metas bDocMD = md.getMetas(doc3uri);
    	assertNotNull(bDocMD);

    	DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add metas for XML document
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	//add metas for binaty document
	    	bDocMD.put(KEY1, VALUE2);
	    	bDocMD.put(KEY2, doc2);

	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc1uri, list.get(0).getURI());

	    	list = md.matchDocuments(KEY1, VALUE2);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc3uri, list.get(0).getURI());

        	System.out.println("MOVE...");
	    	Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

            Collection parent = broker.getCollection(col2uri.removeLastSegment());
        	assertNotNull(parent);

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
	            
	            broker.moveCollection(txn, col, parent, col2uri.lastSegment());
            
	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc4uri, list.get(0).getURI());
	    	
        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_renameXMLResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc1uri, list.get(0).getURI());

	    	System.out.println("RENAMING...");
            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            broker.moveResource(txn, doc1, col, doc2uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc2uri, list.get(0).getURI());

        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_moveXMLResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc1uri, list.get(0).getURI());

	    	System.out.println("MOVING...");
        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            Collection col = broker.getOrCreateCollection(txn, col2uri);
	        	assertNotNull(col);
	    		broker.saveCollection(txn, col);

	            broker.moveResource(txn, doc1, col, doc4uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc4uri, list.get(0).getURI());

        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_deleteXMLResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc1uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc1uri, list.get(0).getURI());

	    	System.out.println("DELETING...");
            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            broker.removeXMLResource(txn, doc1);

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	assertEquals(0, list.size());
	    	
        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_renameBinaryResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc3uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc3uri, list.get(0).getURI());

	    	System.out.println("MOVING...");
            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            broker.moveResource(txn, doc3, col, doc6uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc6uri, list.get(0).getURI());
	    	
        } finally {
        	pool.release(broker);
        }
    }

    @Test
	public void test_moveBinaryResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc3uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc3uri, list.get(0).getURI());

	    	System.out.println("MOVING...");

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            Collection col = broker.getOrCreateCollection(txn, col2uri);
	        	assertNotNull(col);
	    		broker.saveCollection(txn, col);
	        	
	        	broker.moveResource(txn, doc3, col, doc5uri.lastSegment());

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc5uri, list.get(0).getURI());
	    	
        } finally {
        	pool.release(broker);
        }
    }
    @Test
	public void test_deleteBinaryResource() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = md.getMetas(doc3uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	docMD.put(KEY2, VALUE1);
	    	
	    	List<DocumentImpl> list = md.matchDocuments(KEY2, VALUE1);
	    	
	    	assertEquals(1, list.size());
	    	assertEquals(doc3uri, list.get(0).getURI());

	    	System.out.println("DELETING...");
            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);

        	TransactionManager txnManager = null;
	        Txn txn = null;
	        try {
	            txnManager = pool.getTransactionManager();
	            assertNotNull(txnManager);
	            txn = txnManager.beginTransaction();
	            assertNotNull(txn);
            
	            broker.removeXMLResource(txn, doc3);

	            txnManager.commit(txn);
	        } catch (Exception e) {
	            e.printStackTrace();
	            txnManager.abort(txn);
	            fail(e.getMessage());
	        }
	    	System.out.println("DONE.");

	    	list = md.matchDocuments(KEY2, VALUE1);
	    	assertEquals(0, list.size());
	    	
        } finally {
        	pool.release(broker);
        }
    }

    public Properties contentsOutputProps = new Properties(); 
    {
        contentsOutputProps.setProperty( OutputKeys.INDENT, "yes" );
        contentsOutputProps.setProperty( EXistOutputKeys.OUTPUT_DOCTYPE, "yes" );
    }
    
//    private String serializer(DBBroker broker, DocumentImpl document) throws SAXException {
//		Serializer serializer = broker.getSerializer();
//		serializer.setUser(broker.getSubject());
//		serializer.setProperties(contentsOutputProps);
//		return serializer.serialize(document);
//	}

	//@BeforeClass
    public static void startDB() {
        DBBroker broker = null;
        TransactionManager txnManager = null;
        Txn txn = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
        	pool.getPluginsManager().addPlugin("org.exist.storage.md.Plugin");
            
        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
            clean();
            
            txnManager = pool.getTransactionManager();
            assertNotNull(txnManager);
            txn = txnManager.beginTransaction();
            assertNotNull(txn);
            
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(txn, col1uri);
            assertNotNull(root);
            broker.saveCollection(txn, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

            System.out.println("STORING DOCUMENT....");
            IndexInfo info = root.validateXMLResource(txn, broker, doc1uri.lastSegment(), XML1);
            assertNotNull(info);
            System.out.println("STORING DOCUMENT....SECOND ROUND....");
            root.store(txn, broker, info, XML1, false);
            assertNotNull(info.getDocument());
            System.out.println("STORING DOCUMENT....DONE.");

            doc1 = info.getDocument();

            info = root.validateXMLResource(txn, broker, doc2uri.lastSegment(), XML2);
            assertNotNull(info);
            root.store(txn, broker, info, XML2, false);

            doc2 = info.getDocument();

            System.out.println("store "+doc3uri);
            doc3 = root.addBinaryResource(txn, broker, doc3uri.lastSegment(), BINARY.getBytes(), null);

            txnManager.commit(txn);
        } catch (Exception e) {
            e.printStackTrace();
            txnManager.abort(txn);
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    //@AfterClass
    public static void cleanup() {
    	clean();
    	shutdown();
    }

    //@AfterClass
    private static void shutdown() {
        BrokerPool.stopAll(false);
        pool = null;
        doc1 = null;
        doc2 = null;
        System.out.println("stopped");
    }

    private static void clean() {
    	System.out.println("CLEANING...");
    	
        DBBroker broker = null;
        TransactionManager txnManager = null;
        Txn txn = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            txnManager = pool.getTransactionManager();
            assertNotNull(txnManager);
            txn = txnManager.beginTransaction();
            assertNotNull(txn);
            System.out.println("Transaction started ...");

            Collection col = broker.getCollection(col1uri);
            if (col != null) {
            	broker.removeCollection(txn, col);
            }

            col = broker.getCollection(col2uri);
            if (col != null) {
            	broker.removeCollection(txn, col);
            }

        	txnManager.commit(txn);
        } catch (Exception e) {
        	txnManager.abort(txn);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    	System.out.println("CLEANED.");
    }
}
