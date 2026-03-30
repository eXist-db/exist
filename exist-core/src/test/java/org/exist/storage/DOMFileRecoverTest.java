/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.btree.IndexQuery;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.ReadOnlyException;
import org.exist.xquery.TerminatedException;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

/**
 * Tests transaction management  and basic recovery for the DOMFile class.
 * 
 * @author wolf
 *
 */
public class DOMFileRecoverTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
	public void add() throws EXistException, ReadOnlyException, TerminatedException, IOException, BTreeException {
		BrokerPool.FORCE_CORRUPTION = false;

		final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final NodeIdFactory idFact = pool.getNodeFactory();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
			//Add some random data and force db corruption

            broker.flush();
            final DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            domDb.setOwnerObject(this);

            final TransactionManager mgr = pool.getTransactionManager();

            long firstToRemove = -1;

            try(final Txn txn = mgr.beginTransaction()) {

                // put 1000 values into the btree
                for (int i = 1; i <= 10000; i++) {
                    byte[] data = ("Value" + i).getBytes();
                    NodeId id = idFact.createInstance(i);
                    long addr = domDb.put(txn, new NativeBroker.NodeRef(500, id), data);
//              TODO : test addr ?
                    if (i == 1)
                        firstToRemove = addr;
                }

                domDb.closeDocument();

                // remove all
                NativeBroker.NodeRef ref = new NativeBroker.NodeRef(500);
                assertNotNull(ref);
                IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, ref);
                assertNotNull(idx);
                domDb.remove(txn, idx, null);
                domDb.removeAll(txn, firstToRemove);

                // put some more
                for (int i = 1; i <= 10000; i++) {
                    byte[] data = ("Value" + i).getBytes();
                    @SuppressWarnings("unused")
                    long addr = domDb.put(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)), data);
//              TODO : test addr ?
                }

                domDb.closeDocument();
                mgr.commit(txn);
            }
            
            try(final Txn txn = mgr.beginTransaction()) {

                // put 1000 new values into the btree
                for (int i = 1; i <= 1000; i++) {
                    byte[] data = ("Value" + i).getBytes();
                    long addr = domDb.put(txn, new NativeBroker.NodeRef(501, idFact.createInstance(i)), data);
//              TODO : test addr ?                
                    if (i == 1)
                        firstToRemove = addr;
                }

                domDb.closeDocument();
                mgr.commit(txn);
            }
            
            // the following transaction is not committed and will be rolled back during recovery
            try(final Txn txn = mgr.beginTransaction()) {

                for (int i = 1; i <= 200; i++) {
                    domDb.remove(txn, new NativeBroker.NodeRef(500, idFact.createInstance(i)));
                }

                final IndexQuery idx = new IndexQuery(IndexQuery.TRUNC_RIGHT, new NativeBroker.NodeRef(501));
                domDb.remove(txn, idx, null);
                domDb.removeAll(txn, firstToRemove);

                // Don't commit...
                mgr.commit(txn);
            }
            pool.getJournalManager().get().flush(true, false);

            Writer writer = new StringWriter();
            domDb.dump(writer);
	    }
	}

    @Test
    public void get() throws EXistException, IOException, BTreeException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
        	//Recover and read the data

            assertNotNull(broker);
            TransactionManager mgr = pool.getTransactionManager();
            assertNotNull(mgr);
            
            DOMFile domDb = ((NativeBroker) broker).getDOMFile();
            assertNotNull(domDb);
            domDb.setOwnerObject(this);
            
            IndexQuery query = new IndexQuery(IndexQuery.GT, new NativeBroker.NodeRef(500));
            assertNotNull(query);
            List<?> keys = domDb.findKeys(query);
            assertNotNull(keys);
            int count = 0;
            for (Iterator<?> i = keys.iterator(); i.hasNext(); count++) {
                Value key = (Value) i.next();
                assertNotNull(key);
                Value value = domDb.get(key);
                assertNotNull(value);
            }
            
            Writer writer = new StringWriter();
            domDb.dump(writer);
	    }
    }
}
