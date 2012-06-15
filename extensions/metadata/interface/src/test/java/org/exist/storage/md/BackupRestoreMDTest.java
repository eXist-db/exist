/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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

import java.io.File;

import junit.framework.TestCase;

import org.exist.backup.Backup;
import org.exist.backup.Restore;
import org.exist.backup.restore.listener.DefaultRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class BackupRestoreMDTest extends TestCase {

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    private static XmldbURI doc1uri = XmldbURI.create("test_string.xml");
    private static XmldbURI doc2uri = XmldbURI.create("test.binary");
    
    private static String XML = "<test/>";

    private static String BINARY = "test";

    private static String KEY1 = "key1";
    private static String VALUE1 = "value1";

    private static String KEY2 = "key2";
    private static String VALUE2 = "value2";
    
    private static BrokerPool pool;

    //@Test
	public void test_01() throws Exception {
    	System.out.println("test_01");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	String docUUID = null;
    	String doc2UUID = null;
    	
    	String key1UUID = null;
    	String key2UUID = null;
    	String key3UUID = null;
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
        	assertNotNull(root);

	    	Metas docMD = md.getMetas(doc1uri);
	    	assertNotNull(docMD);
	    	
	    	docUUID = docMD.getUUID();
	    	
	    	//set metas
	    	docMD.put(KEY1, VALUE1);
	    	docMD.put(KEY2, VALUE2);
	    	
	    	Meta meta = docMD.get(KEY1);
	    	key1UUID = meta.getUUID();
	
	    	meta = docMD.get(KEY2);
	    	key2UUID = meta.getUUID();
	
	    	//binary
	    	docMD = MetaData.get().getMetas(doc2uri);
	    	assertNotNull(docMD);
	
	    	doc2UUID = docMD.getUUID();
	
	    	//set metas
	    	meta = docMD.put(KEY1, VALUE2);
	    	key3UUID = meta.getUUID();
	
	    	Backup backup = new Backup("admin", "", "backup");
			backup.backup(false, null);
        } finally {
        	pool.release(broker);
        }
    	
    	clean();
    	
		Restore restore = new Restore();
		RestoreListener listener = new DefaultRestoreListener();
		restore.restore(listener, "admin", "", "", new File("backup"), "xmldb:exist://");

        broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
        	assertNotNull(root);

        	Metas docMD = md.getMetas(doc1uri);
	    	
	    	assertNotNull(docMD);
	    	
	    	assertEquals(docUUID, docMD.getUUID());
	
	    	Meta meta = docMD.get(KEY1);
	    	assertNotNull(meta);
	
	    	assertEquals(VALUE1, meta.getValue());
	    	assertEquals(key1UUID, meta.getUUID());
	
	    	meta = docMD.get(KEY2);
	    	assertNotNull(meta);
	
	    	assertEquals(VALUE2, meta.getValue());
	    	assertEquals(key2UUID, meta.getUUID());
	
	    	//binary
	    	docMD = MetaData.get().getMetas(doc2uri);
	    	assertNotNull(docMD);
	    	
	    	assertEquals(doc2UUID, docMD.getUUID());
	
	    	meta = docMD.get(KEY1);
	    	assertNotNull(meta);
	
	    	assertEquals(VALUE2, meta.getValue());
	    	assertEquals(key3UUID, meta.getUUID());
        } finally {
        	pool.release(broker);
        }
	}
	
	private DocumentImpl getDoc(DBBroker broker, Collection col, XmldbURI uri) throws PermissionDeniedException {
        DocumentImpl doc = col.getDocument(broker, uri);
    	assertNotNull(doc);
		
    	return doc;
	}
    
	//@BeforeClass
    public static void startDB() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            System.out.println("store "+doc1uri);
            IndexInfo info = root.validateXMLResource(transaction, broker, doc1uri, XML);
            assertNotNull(info);
            root.store(transaction, broker, info, XML, false);

            System.out.println("store "+doc2uri);
            BinaryDocument doc = root.addBinaryResource(transaction, broker, doc2uri, BINARY.getBytes(), null);

            transact.commit(transaction);
        } catch (Exception e) {
            e.printStackTrace();
            transact.abort(transaction);
            fail(e.getMessage());
        } finally {
            if (pool != null)
                pool.release(broker);
        }

        rundb();
    }


    private static void rundb() {
		try {
			Class cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    //@AfterClass
    public static void cleanup() {
    	clean();
        BrokerPool.stopAll(false);
        pool = null;
        System.out.println("stoped");
    }

    private static void clean() {
    	System.out.println("CLEANING...");
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
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            transact.commit(transaction);
        } catch (Exception e) {
        	transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    	System.out.println("CLEANED.");
    }
}
