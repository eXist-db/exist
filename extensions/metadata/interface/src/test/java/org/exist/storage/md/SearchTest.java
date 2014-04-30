/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.storage.md;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.Test;
import org.xml.sax.SAXException;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SearchTest {

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
    private static XmldbURI doc6uri = col3uri.append("test.binary");
    
    private static String XML1 = "<test/>";
    private static String XML2 = "<test2/>";

    private static String BINARY = "test";

    private static String wrongXML = "<test>";

    private static String KEY1 = "key1";
    private static String VALUE1 = "value1";

    private static String KEY2 = "key2";
    private static String VALUE2 = "value2 auth";
    
    private static BrokerPool pool;
    private static DocumentImpl doc1 = null;
    private static DocumentImpl doc2 = null;

    @Test
	public void test_00() throws Exception {
    	
    	startDB();
    	
        BrokerPool db = null;
        DBBroker broker = null;
        try {
            db = BrokerPool.getInstance();
            broker = db.get(db.getSecurityManager().getSystemSubject());
            
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
        	
        	List<String> dbRoot = new ArrayList<String>();
        	dbRoot.add("/db");

//        	assertEquals(
//                "in-memory#element {results} {in-memory#element {search} {in-memory#attribute {uri} {/db/test/test_string.xml}  in-memory#attribute {score} {0.30685282} } } ",
//                md.search("value1", dbRoot).toString()
//            );
//
//        	assertEquals(
//    	        "in-memory#element {results} {in-memory#element {search} {in-memory#attribute {uri} {/db/test/test_string.xml}  in-memory#attribute {score} {0.30685282} in-memory#element {field} {in-memory#attribute {name} {key1} in-memory#element {exist:match} {in-memory#text {value1} } } } } ",
//    	        md.search("key1:value1", dbRoot).toString()
//	        );
//
//            assertEquals(
//                "in-memory#element {results} {in-memory#element {search} {in-memory#attribute {uri} {/db/test/test_string.xml}  in-memory#attribute {score} {0.30685282} in-memory#element {field} {in-memory#attribute {name} {key1} in-memory#element {exist:match} {in-memory#text {value1} } } } } ",
//                md.search("key1:value*", dbRoot).toString()
//            );
//
//            assertEquals(
//                "in-memory#element {results} {} ",
//                md.search("key1:value2", dbRoot).toString()
//            );

//    	//add second key-value
//    	docMD.put(KEY2, VALUE2);
//    	
//    	meta = docMD.get(KEY2);
//    	assertNotNull(meta);
//
//    	assertEquals(VALUE2, meta.getValue());
//
//    	//replace first key-value
//    	docMD.put(KEY1, VALUE2);
//    	
//    	meta = docMD.get(KEY1);
//    	assertNotNull(meta);
//
//    	assertEquals(VALUE2, meta.getValue());
//    	
//    	//second document
//    	docMD = MetaData.get().getMetas(doc2uri);
//    	
//    	assertNotNull(docMD);
//    	
//    	uuid = docMD.getUUID();
//    	assertNotNull(uuid);
//    	
//    	doc = MetaData.get().getDocument(uuid);
//    	assertNotNull(doc);
//    	assertTrue(doc2.equals(doc));
//
//    	//add first key-value
//    	docMD.put(KEY1, VALUE2);
//    	
//    	meta = docMD.get(KEY1);
//    	assertNotNull(meta);
//
//    	assertEquals(VALUE2, meta.getValue());
//
//    	//add second key-value
//    	docMD.put(KEY2, VALUE1);
//    	
//    	meta = docMD.get(KEY2);
//    	assertNotNull(meta);
//
//    	assertEquals(VALUE1, meta.getValue());
//
//    	//replace first key-value
//    	docMD.put(KEY1, VALUE1);
//    	
//    	meta = docMD.get(KEY1);
//    	assertNotNull(meta);
//
//    	assertEquals(VALUE1, meta.getValue());

        } finally {
            if (db != null)
                db.release(broker);
        }

    	cleanup();
    }
    
	private static DocumentImpl storeDocument(Txn txn, DBBroker broker, Collection col, XmldbURI uri, String data) throws TriggerException, EXistException, PermissionDeniedException, SAXException, LockException, IOException {
        System.out.println("STORING DOCUMENT....");
        IndexInfo info = col.validateXMLResource(txn, broker, uri.lastSegment(), data);
        assertNotNull(info);
        System.out.println("STORING DOCUMENT....SECOND ROUND....");
        col.store(txn, broker, info, data, false);
        assertNotNull(info.getDocument());
        System.out.println("STORING DOCUMENT....DONE.");

        return info.getDocument();
	}

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
        	pool.getPluginsManager().addPlugin("org.exist.storage.md.MDStorageManager");
            
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

            Collection test2 = broker.getOrCreateCollection(txn, col2uri);
            assertNotNull(test2);
            broker.saveCollection(txn, test2);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(txn, broker, root, COLLECTION_CONFIG);

            doc1 = storeDocument(txn, broker, root, doc1uri, XML1);
            doc2 = storeDocument(txn, broker, root, doc2uri, XML2);

            System.out.println("store "+doc5uri);
            root.addBinaryResource(txn, broker, doc5uri.lastSegment(), BINARY.getBytes(), null);

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

            Collection col = broker.getOrCreateCollection(txn, col1uri);
            assertNotNull(col);
        	broker.removeCollection(txn, col);

//            col = broker.getOrCreateCollection(txn, col2uri);
//            assertNotNull(col);
//        	broker.removeCollection(txn, col);

            col = broker.getOrCreateCollection(txn, col3uri);
            assertNotNull(col);
        	broker.removeCollection(txn, col);

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
