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
package org.exist.storage;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import static org.junit.Assert.assertNotNull;

/**
 * Test recovery after a forced database corruption.
 * 
 * @author wolf
 *
 */
public class RecoveryTest2 {
    
    private static String xmlDir = "/home/wolf/xml/Saami";
    
    @SuppressWarnings("unused")
	private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";

    @Test
    public void store() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, SAXException, BTreeException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction();) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);

            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            assertNotNull(domDb);
            Writer writer = new StringWriter();
            domDb.dump(writer);

            File f;
            IndexInfo info;

            // store some documents. Will be replaced below
            File dir = new File(xmlDir);
            assertNotNull(dir);
            File[] docs = dir.listFiles();
            assertNotNull(docs);
            for (int i = 0; i < docs.length; i++) {
                f = docs[i];
                assertNotNull(f);
                info = test2.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                assertNotNull(info);
                test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
            }

            transact.commit(transaction);
        }
    }

    @Test
    public void read() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, SAXException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            assertNotNull(broker);
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append("terms-eng.xml"), Lock.READ_LOCK);
            assertNotNull("Document should not be null", doc);
            String data = serializer.serialize(doc);
            assertNotNull(data);
            doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }
    
    protected BrokerPool startDB() throws EXistException, DatabaseConfigurationException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
