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
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.TerminatedException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests transaction management and basic recovery for the BTree base class.
 * 
 * @author wolf
 *
 */
public class BTreeRecoverTest {

    private BrokerPool pool;
    private int count = 0;

    @Test
    public void add() throws EXistException, IOException, BTreeException, TerminatedException {
        //Add some random data and force db corruption
        
        TransactionManager mgr = pool.getTransactionManager();
        NodeIdFactory idFact = pool.getNodeFactory();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            broker.flush();
            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            domDb.setOwnerObject(this);

            BrokerPool.FORCE_CORRUPTION = true;

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
            
            mgr.getJournal().flushToLog(true);
            
            Writer writer = new StringWriter();
            domDb.dump(writer);
        }
    }

    @Test
    public void get() throws EXistException, TerminatedException, IOException, BTreeException {
        //Recover and read the data
        @SuppressWarnings("unused")
		TransactionManager mgr = pool.getTransactionManager();
        NodeIdFactory idFact = pool.getNodeFactory();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            domDb.setOwnerObject(this);
            
            IndexQuery query = new IndexQuery(IndexQuery.GEQ, new NativeBroker.NodeRef(500, idFact.createInstance(1)));
            domDb.query(query, new IndexCallback());
            assertEquals(count, 800);
            
            Writer writer = new StringWriter();
            domDb.dump(writer);
        }
    }

    @Before
    public void setUp() throws DatabaseConfigurationException, EXistException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        pool = BrokerPool.getInstance();
    }

    @After
    protected void tearDown() {
        BrokerPool.stopAll(false);
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
