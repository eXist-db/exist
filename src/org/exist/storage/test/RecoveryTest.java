/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage.test;

import java.io.File;
import java.io.StringWriter;
import java.io.Writer;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.NativeBroker;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Test recovery after a forced database corruption.
 * 
 * @author wolf
 *
 */
public class RecoveryTest extends TestCase {
    
    public static void main(String[] args) {
        TestRunner.run(RecoveryTest.class);
    }
    
    private static String directory = "samples/shakespeare";
    
    private static File dir = new File(directory);
    
    private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";
    
    public void testStore() throws Exception {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDB();
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            System.out.println("Transaction started ...");
            
            Collection root = broker.getOrCreateCollection(transaction, DBBroker.ROOT_COLLECTION + "/test");
            broker.saveCollection(transaction, root);
            
            Collection test = broker.getOrCreateCollection(transaction, DBBroker.ROOT_COLLECTION + "/test/test2");
            broker.saveCollection(transaction, test);
            
            
            File files[] = dir.listFiles();
            
            File f;
            IndexInfo info;
            
            BinaryDocument doc = test.addBinaryResource(transaction, broker, "binary.txt", "Some text data".getBytes(), null);
            
            // store some documents. Will be replaced below
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                try {
                    info = test.validate(transaction, broker, f.getName(), new InputSource(f.toURI().toASCIIString()));
                    test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
            }
            
            // replace some documents
            for (int i = 0; i < files.length; i++) {
                f = files[i];
                try {
                    info = test.validate(transaction, broker, f.getName(), new InputSource(f.toURI().toASCIIString()));
                    test.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                } catch (SAXException e) {
                    System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                }
            }
    
            info = test.validate(transaction, broker, "test_string.xml", TEST_XML);
            test.store(transaction, broker, info, TEST_XML, false);
            
            // remove last document
            test.removeDocument(transaction, broker, files[files.length - 1].getName());
            
            
            transact.commit(transaction);
            
            // the following transaction will not be committed. It will thus be rolled back by recovery
            transaction = transact.beginTransaction();
            
            test.removeDocument(transaction, broker, files[0].getName());
            
            test.removeBinaryResource(transaction, broker, doc);
            
            transact.getJournal().flushToLog(true);
            
            DOMFile domDb = ((NativeBroker)broker).getDOMFile();
            Writer writer = new StringWriter();
            domDb.dump(writer);
            System.out.println(writer.toString());
        } finally {
            pool.release(broker);
        }
    }
    
    public void testRead() throws Exception {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();
        
        System.out.println("testRead() ...\n");
        
        File files[] = dir.listFiles();
        
        DBBroker broker = null;
        try {
            broker = pool.get(SecurityManager.SYSTEM_USER);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc;
            String data;
            
            doc = broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/test2/hamlet.xml", Lock.READ_LOCK);
            assertNotNull("Document '" + DBBroker.ROOT_COLLECTION + "/test/test2/hamlet.xml' should not be null", doc);
            data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
            
            doc = broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/test2/test_string.xml", Lock.READ_LOCK);
            assertNotNull("Document '" + DBBroker.ROOT_COLLECTION + "/test/test2/test_string.xml' should not be null", doc);
            data = serializer.serialize(doc);
            System.out.println(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
            
            doc = broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/test2/" + files[files.length - 1].getName(), Lock.READ_LOCK);
            assertNull("Document '" + DBBroker.ROOT_COLLECTION + "/test/test2/'" + files[files.length - 1].getName() + " should not exist anymore", doc);
            
            XQuery xquery = broker.getXQueryService();
            Sequence seq = xquery.execute("//SPEECH[LINE &= 'king']", null);
            System.out.println("Found: " + seq.getLength());
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                System.out.println(serializer.serialize((NodeValue) next));
            }
            
            BinaryDocument binDoc = (BinaryDocument) broker.openDocument(DBBroker.ROOT_COLLECTION + "/test/test2/binary.txt", Lock.READ_LOCK);
            assertNotNull("Binary document is null", binDoc);
            data = new String(broker.getBinaryResourceData(binDoc));
            System.out.println(data);
            
            DOMFile domDb = ((NativeBroker)broker).getDOMFile();
            Writer writer = new StringWriter();
            domDb.dump(writer);
            System.out.println(writer.toString());
            
            TransactionManager transact = pool.getTransactionManager();
            Txn transaction = transact.beginTransaction();
            
            Collection root = broker.openCollection(DBBroker.ROOT_COLLECTION + "/test", Lock.WRITE_LOCK);
            transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);
            
            broker.removeCollection(transaction, root);
            
            transact.commit(transaction);
        } finally {
            pool.release(broker);
        }
    }
    
    protected BrokerPool startDB() throws Exception {
        String home, file = "conf.xml";
        home = System.getProperty("exist.home");
        if (home == null)
            home = System.getProperty("user.dir");
        try {
            Configuration config = new Configuration(file, home);
            BrokerPool.configure(1, 5, config);
            return BrokerPool.getInstance();
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        return null;
    }

    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
}
