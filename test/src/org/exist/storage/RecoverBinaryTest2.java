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

import java.io.*;
import java.util.Date;
import java.util.Iterator;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class RecoverBinaryTest2 {
    
    private static String directory = "webapp/resources";
    
    //private static File dir = new File(directory);

    @Test
    public void storeAndRead() throws TriggerException, PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, EXistException {
        store();
        tearDown();
        read();
        tearDown();
        read2();
    }

    //@Test
    public void store() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);
            
            final Collection test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
            assertNotNull(test2);
            broker.saveCollection(transaction, test2);            
            
            storeFiles(broker, transaction, test2);
            transact.commit(transaction);
        }
    }

    //@Test
    public void read() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, LockException, IOException, TriggerException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            final Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (final Iterator<DocumentImpl> i = test2.iterator(broker); i.hasNext(); ) {
                DocumentImpl doc = i.next();
            }
            
            BrokerPool.FORCE_CORRUPTION = true;
            final TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);

                storeFiles(broker, transaction, test2);
                transact.commit(transaction);
            }
        }
    }

    //@Test
    public void read2() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, TriggerException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {

            final Collection test2 = broker.getCollection(TestConstants.TEST_COLLECTION_URI2);
            for (final Iterator<DocumentImpl> i = test2.iterator(broker); i.hasNext(); ) {
                final DocumentImpl doc = i.next();
            }
            
            final TransactionManager transact = pool.getTransactionManager();
            assertNotNull(transact);
            try(final Txn transaction = transact.beginTransaction()) {
                assertNotNull(transaction);
                final Collection test1 = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
                broker.removeCollection(transaction, test1);
                transact.commit(transaction);
            }
        }
    }
    
    private void storeFiles(final DBBroker broker, final Txn transaction, final Collection test2) throws IOException, EXistException, PermissionDeniedException, LockException, TriggerException {
        // Get files in directory
        final File dir = new File(ConfigurationHelper.getExistHome(), directory);
        final File files[] = dir.listFiles();
        assertNotNull("Check directory '"+dir.getAbsolutePath()+"'.",files);
        
        // store some documents.
        for (int j = 0; j < 10; j++) {
            for (final File f : files) {
                assertNotNull(f);
                if (f.isFile()) {
                    final XmldbURI uri = test2.getURI().append(j + "_" + f.getName());
                    try(final InputStream is = new FileInputStream(f)) {
                        final BinaryDocument doc =
                            test2.addBinaryResource(transaction, broker, uri, is, MimeType.BINARY_TYPE.getName(),
                                f.length(), new Date(), new Date());
                        assertNotNull(doc);
                    }
                }
            }
        }
    }
    
    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        final Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
