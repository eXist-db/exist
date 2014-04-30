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
package org.exist.backup;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.backup.SystemExport;
import org.exist.backup.SystemImport;
import org.exist.backup.restore.listener.DefaultRestoreListener;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
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
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class SystemExportImportTest {

    private static String COLLECTION_CONFIG =
            "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        	"	<index>" +
            "	</index>" +
        	"</collection>";

    private static XmldbURI col1uri = TestConstants.TEST_COLLECTION_URI;

    private static XmldbURI doc01uri = col1uri.append("test1.xml");
    private static XmldbURI doc02uri = col1uri.append("test2.xml");
    private static XmldbURI doc03uri = col1uri.append("test3.xml");
    private static XmldbURI doc11uri = col1uri.append("test.binary");
    
    private static String XML1 = "<test attr=\"test\"/>";
    private static String XML2 = 
		"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\"\n" +
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"></html>";
    private static String XML2_PROPER = 
		"<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Strict//EN\" " +
        "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd\">\n" +
        "<html xmlns=\"http://www.w3.org/1999/xhtml\"/>";


    private static String XML3 = "<!DOCTYPE html><html></html>";
    private static String XML3_PROPER = "<!DOCTYPE html>\n<html/>";

    private static String BINARY = "test";

    private static BrokerPool pool;

    @Test
	public void test_01() throws Exception {
    	System.out.println("test_01");
    	
    	startDB();
    	
    	File file;
    	
        DBBroker broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection root = broker.getCollection(col1uri);
        	assertNotNull(root);

            SystemExport sysexport = new SystemExport( broker, null, null, true );
            file = sysexport.export( "backup", false, false, null );
        } finally {
        	pool.release(broker);
        }
    	
    	clean();
    	
    	//check that it clean
    	broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());

            Collection col = broker.getCollection(col1uri);
            assertNull(col);
        } finally {
        	pool.release(broker);
        }

    	SystemImport restore = new SystemImport(pool);
		RestoreListener listener = new DefaultRestoreListener();
		restore.restore(listener, "admin", "", "", file, "xmldb:exist://");

        broker = null;
        try {
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);

            Collection col = broker.getCollection(col1uri);
        	assertNotNull(col);
        	
        	DocumentImpl doc = getDoc(broker, col, doc01uri.lastSegment());
        	assertEquals(XML1, serializer(broker, doc));

        	doc = getDoc(broker, col, doc02uri.lastSegment());
        	assertEquals(XML2_PROPER, serializer(broker, doc));

        	doc = getDoc(broker, col, doc03uri.lastSegment());
        	assertEquals(XML3_PROPER, serializer(broker, doc));

        } finally {
        	pool.release(broker);
        }
        
        BrokerPool.stopAll(false);
	}
	
	private DocumentImpl getDoc(DBBroker broker, Collection col, XmldbURI uri) throws PermissionDeniedException {
        DocumentImpl doc = col.getDocument(broker, uri);
    	assertNotNull(doc);
		
    	return doc;
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
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            BrokerPool.configure(1, 5, config);
            pool = BrokerPool.getInstance();
        	assertNotNull(pool);
        	pool.getPluginsManager().addPlugin("org.exist.storage.md.Plugin");

        	broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            System.out.println("Transaction started ...");

            Collection root = broker.getOrCreateCollection(transaction, col1uri);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            System.out.println("store "+doc01uri);
            IndexInfo info = root.validateXMLResource(transaction, broker, doc01uri.lastSegment(), XML1);
            assertNotNull(info);
            root.store(transaction, broker, info, XML1, false);

            System.out.println("store "+doc02uri);
            info = root.validateXMLResource(transaction, broker, doc02uri.lastSegment(), XML2);
            assertNotNull(info);
            root.store(transaction, broker, info, XML2, false);

            System.out.println("store "+doc03uri);
            info = root.validateXMLResource(transaction, broker, doc03uri.lastSegment(), XML3);
            assertNotNull(info);
            root.store(transaction, broker, info, XML3, false);

            System.out.println("store "+doc11uri);
            root.addBinaryResource(transaction, broker, doc11uri.lastSegment(), BINARY.getBytes(), null);

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

            Collection root = broker.getOrCreateCollection(transaction, col1uri);
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
