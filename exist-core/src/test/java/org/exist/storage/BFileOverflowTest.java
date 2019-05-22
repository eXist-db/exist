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

import org.exist.EXistException;
import org.exist.storage.btree.Value;
import org.exist.storage.index.BFile;
import org.exist.storage.sync.Sync;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.FixedByteArray;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author wolf
 *
 */
public class BFileOverflowTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void add() throws EXistException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager mgr = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            broker.flush();
            broker.sync(Sync.MAJOR);

            final BFile collectionsDb = (BFile) ((NativeBroker) broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            BrokerPool.FORCE_CORRUPTION = true;

            final Value key = new Value("test".getBytes());

            try(final Txn txn = mgr.beginTransaction()) {

                byte[] data = "_HELLO_YOU_".getBytes();
                collectionsDb.put(txn, key, new FixedByteArray(data, 0, data.length), true);

                for (int i = 1; i < 101; i++) {
                    String value = "_HELLO_" + i;
                    data = value.getBytes(UTF_8);
                    collectionsDb.append(txn, key, new FixedByteArray(data, 0, data.length));
                }

                mgr.commit(txn);
            }
            
            // start a new transaction that will not be committed and thus undone
            final Txn txn = mgr.beginTransaction();
            
            for (int i = 1001; i < 2001; i++) {
                String value = "_HELLO_" + i;
                final byte[] data = value.getBytes(UTF_8);
                collectionsDb.append(txn, key, new FixedByteArray(data, 0, data.length));
            }
       
            collectionsDb.remove(txn, key);

            pool.getJournalManager().get().flush(true, false);

        }
    }

    @Test
    public void read() throws EXistException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            BFile collectionsDb = (BFile)((NativeBroker)broker).getStorage(NativeBroker.COLLECTIONS_DBX_ID);
            
            Value key = new Value("test".getBytes());
            Value val = collectionsDb.get(key);
        }
    }
}
