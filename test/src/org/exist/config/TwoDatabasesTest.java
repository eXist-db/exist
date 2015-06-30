/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
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
package org.exist.config;

import org.apache.commons.io.output.ByteArrayOutputStream;

import java.io.File;
import java.io.IOException;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.security.Subject;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * @author alex
 */
public class TwoDatabasesTest {

    final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    BrokerPool pool1;
    Subject user1;

    BrokerPool pool2;
    Subject user2;

    @Before
    public void setUp() throws Exception {
        // Setup the log4j configuration
        String log4j = System.getProperty("log4j.configurationFile");
        if (log4j == null) {
            File lf = new File("log42j.xml");
            if (lf.canRead()) {
                System.setProperty("log4j.configurationFile", lf.toURI().toASCIIString());
            }
        }

        int threads = 5;

        String packagePath = TwoDatabasesTest.class.getPackage().getName().replace('.', '/');
        String existHome = System.getProperty("exist.home");
        if (existHome == null) {
            existHome = ".";
        }
        File config1File = new File(existHome + "/test/src/" + packagePath + "/conf1.xml");
        File data1Dir = new File(existHome + "/test/temp/" + packagePath + "/data1");
        assertTrue(data1Dir.exists() || data1Dir.mkdirs());

        File config2File = new File(existHome + "/test/src/" + packagePath + "/conf2.xml");
        File data2Dir = new File(existHome + "/test/temp/" + packagePath + "/data2");
        assertTrue(data2Dir.exists() || data2Dir.mkdirs());

        // Configure the database
        Configuration config1 = new Configuration(config1File.getAbsolutePath());
        BrokerPool.configure("db1", 1, threads, config1);
        pool1 = BrokerPool.getInstance("db1");
        user1 = pool1.getSecurityManager().getSystemSubject();
        DBBroker broker1 = pool1.get(user1);


        Configuration config2 = new Configuration(config2File.getAbsolutePath());
        BrokerPool.configure("db2", 1, threads, config2);
        pool2 = BrokerPool.getInstance("db2");
        user2 = pool1.getSecurityManager().getSystemSubject();
        DBBroker broker2 = pool2.get(user2);

        Collection top1 = broker1.getCollection(XmldbURI.create("xmldb:exist:///"));
        assertTrue(top1 != null);
        top1.getLock().release(Lock.READ_LOCK);
        pool1.release(broker1);

        Collection top2 = broker2.getCollection(XmldbURI.create("xmldb:exist:///"));
        assertTrue(top2 != null);
        top2.getLock().release(Lock.READ_LOCK);
        pool2.release(broker2);

    }

    @After
    public void tearDown() {
        pool1.shutdown();
        pool2.shutdown();
    }

    @Test
    public void putGet() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException {
        put();
        get();
    }

    private void put() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException {
        try (final DBBroker broker1 = pool1.get(user1);
             final Txn transaction1 = pool1.getTransactionManager().beginTransaction()) {
            Collection top1 = storeBin(broker1, transaction1, "1");
            pool1.getTransactionManager().commit(transaction1);
            top1.release(Lock.READ_LOCK);
        }

        try (final DBBroker broker2 = pool2.get(user1);
             final Txn transaction2 = pool2.getTransactionManager().beginTransaction()) {
            Collection top2 = storeBin(broker2, transaction2, "2");
            pool2.getTransactionManager().commit(transaction2);
            top2.release(Lock.READ_LOCK);
        }
    }

    private void get() throws EXistException, IOException, PermissionDeniedException {
        try (final DBBroker broker1 = pool1.get(user1)) {
            assertTrue(getBin(broker1, "1"));
        }

        try (final DBBroker broker2 = pool2.get(user2)) {
            assertTrue(getBin(broker2, "2"));
        }
    }

    static String bin = "ABCDEFG";

    private Collection storeBin(final DBBroker broker, final Txn txn, String suffix) throws PermissionDeniedException, LockException, TriggerException, EXistException, IOException {
        String data = bin + suffix;
        Collection top = broker.getCollection(XmldbURI.create("xmldb:exist:///"));
        top.addBinaryResource(txn, broker, XmldbURI.create("xmldb:exist:///bin"), data.getBytes(), "text/plain");
        return top;
    }

    private boolean getBin(final DBBroker broker, final String suffix) throws PermissionDeniedException, IOException {
        BinaryDocument binDoc = null;
        try {
            Collection top = broker.getCollection(XmldbURI.create("xmldb:exist:///"));
            int count = top.getDocumentCount(broker);
            MutableDocumentSet docs = new DefaultDocumentSet();
            top.getDocuments(broker, docs);
            XmldbURI[] uris = docs.getNames();
            //binDoc = (BinaryDocument)broker.getXMLResource(XmldbURI.create("xmldb:exist:///bin"),Lock.READ_LOCK);
            binDoc = (BinaryDocument) top.getDocument(broker, XmldbURI.create("xmldb:exist:///bin"));
            top.release(Lock.READ_LOCK);
            assertTrue(binDoc != null);
            ByteArrayOutputStream os = new ByteArrayOutputStream();
            broker.readBinaryResource(binDoc, os);
            String comp = os.size() > 0 ? new String(os.toByteArray()) : "";
            return comp.equals(bin + suffix);
        } finally {
            if (binDoc != null) {
                binDoc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
    }

}
