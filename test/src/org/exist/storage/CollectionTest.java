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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.btree.BTree;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 *
 */
public class CollectionTest {

    @SuppressWarnings("unused")
	private static String docs[] = { "hamlet.xml", "r_and_j.xml", "macbeth.xml" };
    
    private static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    
    @SuppressWarnings("unused")
	private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void storeRead() throws EXistException, IOException, PermissionDeniedException, BTreeException, DatabaseConfigurationException, TriggerException, LockException {
        store();

        stopDb();

        read();
    }

    private void store() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, TriggerException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            broker.saveCollection(transaction, root);
            
            Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
            broker.saveCollection(transaction, test);
            
            transact.commit(transaction);
        }
    }
    
    public void read() throws EXistException, IOException, PermissionDeniedException, BTreeException, DatabaseConfigurationException, LockException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDb();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            BTree btree = ((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            Writer writer = new StringWriter();
            btree.dump(writer);

            Collection test = broker.getCollection(TEST_COLLECTION_URI.append("test2"));
            assertNotNull(test);
            for (Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                DocumentImpl next = i.next();
            }
        }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }
}
