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
import java.util.Optional;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.btree.BTreeCallback;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.TerminatedException;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests transaction management and basic recovery for the BTree base class.
 * 
 * @author wolf
 *
 */
public class BTreeRecoverTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);
    private int count = 0;

    @Test
    public void addAndRead() throws EXistException, IOException, BTreeException, TerminatedException, DatabaseConfigurationException {
        BrokerPool pool = existEmbeddedServer.getBrokerPool();
        BrokerPool.FORCE_CORRUPTION = true;

        add(pool);

        BrokerPool.FORCE_CORRUPTION = false;
        existEmbeddedServer.restart();
        pool = existEmbeddedServer.getBrokerPool();

        get(pool);
    }

    private void add(final BrokerPool pool) throws EXistException, IOException, BTreeException, TerminatedException {
        //Add some random data and force db corruption
        final TransactionManager mgr = pool.getTransactionManager();
        final NodeIdFactory idFact = pool.getNodeFactory();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            broker.flush();
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            domDb.setOwnerObject(this);

            try(final Txn txn = mgr.beginTransaction()) {
                // put 1000 values into the btree
                for (int i = 1; i < 1001; i++) {
                    final NodeId id = idFact.createInstance(i);
                    domDb.addValue(txn, new NativeBroker.NodeRef(500, id), i);
                }

                final IndexQuery idx = new IndexQuery(IndexQuery.GT, new NativeBroker.NodeRef(500, idFact.createInstance(800)));
                domDb.remove(txn, idx, null);

                mgr.commit(txn);
            }
            
            // start a dirty, uncommitted transaction. This will be rolled back by the recovery.
            final Txn txn = mgr.beginTransaction();
            
            for (int i = 801; i < 2001; i++) {
                domDb.addValue(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)), i);
            }
            
            for (int i = 101; i < 301; i++) {
                domDb.addValue(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)), i * 3);
            }
            
            final IndexQuery idx = new IndexQuery(IndexQuery.GT, new NativeBroker.NodeRef(500, idFact.createInstance(600)));
            domDb.remove(txn, idx, null);

            // DO NOT COMMIT THE TRANSACTION!

            pool.getJournalManager().get().flush(true, false);
        }
    }


    private void get(final BrokerPool pool) throws EXistException, TerminatedException, IOException, BTreeException {
        final NodeIdFactory idFact = pool.getNodeFactory();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            domDb.setOwnerObject(this);
            
            final IndexQuery query = new IndexQuery(IndexQuery.GEQ, new NativeBroker.NodeRef(500, idFact.createInstance(1)));
            domDb.query(query, new IndexCallback());
            assertEquals(count, 800);
        }
    }
    
    private final class IndexCallback implements BTreeCallback {

        @Override
        public boolean indexInfo(Value value, long pointer)
                throws TerminatedException {
        	@SuppressWarnings("unused")
			final byte[] data = value.data();
//        	NodeId id = pool.getNodeFactory().createFromData(data[value.start() + 4], data, value.start() + 5);
            count++;
            return false;
        }
    }
}
