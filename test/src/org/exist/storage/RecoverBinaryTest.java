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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.junit.Rule;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 *
 */
public class RecoverBinaryTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void storeAndLoad() throws LockException, TriggerException, PermissionDeniedException, EXistException, IOException, DatabaseConfigurationException {
        store();
        existEmbeddedServer.restart();
        load();
    }

    private void store() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {

    	BrokerPool.FORCE_CORRUPTION = true;

    	final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            try(final Txn transaction = transact.beginTransaction()) {
            
    	        final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
        	    assertNotNull(root);
            	broker.saveCollection(transaction, root);
    
	            final String existHome = System.getProperty("exist.home");
                Path existDir = existHome == null ? Paths.get(".") : Paths.get(existHome);
                existDir = existDir.normalize();

                final byte[] bin = Files.readAllBytes(existDir.resolve("LICENSE"));
                BinaryDocument doc =
                    root.addBinaryResource(transaction, broker, TestConstants.TEST_BINARY_URI, bin, "text/text");
                assertNotNull(doc);

				transact.commit(transaction);
			}
            
            // the following transaction will not be committed. It will thus be rolled back by recovery
//          final Txn transaction = transact.beginTransaction();
//          root.removeBinaryResource(transaction, broker, doc);
            
            //TODO : remove ?
            pool.getJournalManager().get().flush(true, false);
		}
    }
    
    private void load() throws EXistException, PermissionDeniedException, IOException {

        BrokerPool.FORCE_CORRUPTION = false;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append(TestConstants.TEST_BINARY_URI), LockMode.READ_LOCK)) {
            assertNotNull("Binary document is null", lockedDoc);

            final BinaryDocument binDoc = (BinaryDocument)lockedDoc.getDocument();

            try(final InputStream is = broker.getBinaryResource(binDoc)) {
                final byte[] bdata = new byte[(int) broker.getBinaryResourceSize(binDoc)];
                is.read(bdata);
                final String data = new String(bdata);
                assertNotNull(data);
            }
	    }
    }
}
