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

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
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
public class DocumentAsValueTest {

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    /** /db/test **/
    private static XmldbURI col1uri = TestConstants.TEST_COLLECTION_URI;

    /** /db/test/test_string1.xml **/
    private static XmldbURI doc1uri = col1uri.append("test_string1.xml");
    /** /db/test/test_string2.xml **/
    private static XmldbURI doc2uri = col1uri.append("test_string2.xml");
    /** /db/test/test.binary **/
    private static XmldbURI doc3uri = col1uri.append("test.binary");
    
    private static String XML1 = "<test1/>";
    private static String XML2 = "<test2/>";

    private static String BINARY = "test";

    private static String KEY1 = "key1";
    
    private static BrokerPool pool;
    private static DocumentImpl doc1 = null;
    private static DocumentImpl doc2 = null;

    @Test
	public void test_00() throws Exception {
    	System.out.println("test");
    	
    	startDB();
    	
    	MetaData md = MetaData.get();
    	assertNotNull(md);
    	
    	Metas docMD = MetaData.get().getMetas(doc1uri);
    	assertNotNull(docMD);
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            
	    	//add first key-value
	    	docMD.put(KEY1, doc2);
	    	
	    	Meta meta = docMD.get(KEY1);
	    	assertNotNull(meta);
	
	    	assertEquals(serializer(broker, doc2), serializer(broker, (DocumentImpl)meta.getValue()));

        } finally {
        	pool.release(broker);
        }
    }

    public Properties contentsOutputProps = new Properties();
    {
        contentsOutputProps.setProperty( OutputKeys.INDENT, "yes" );
        contentsOutputProps.setProperty( EXistOutputKeys.OUTPUT_DOCTYPE, "yes" );
    }
    private String serializer(DBBroker broker, DocumentImpl document) throws SAXException {
		Serializer serializer = broker.getSerializer();
		serializer.setUser(broker.getSubject());
		serializer.setProperties(contentsOutputProps);
		return serializer.serialize(document);
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

            doc2 =  info.getDocument();

            System.out.println("store "+doc3uri);
            root.addBinaryResource(txn, broker, doc3uri.lastSegment(), BINARY.getBytes(), null);

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
