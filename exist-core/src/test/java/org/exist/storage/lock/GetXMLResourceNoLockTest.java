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
package org.exist.storage.lock;


import java.io.IOException;
import java.util.Optional;

import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistWebServer;
import org.exist.util.LockException;
import org.junit.*;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import uk.ac.ic.doc.slurp.multilock.MultiLock;

import static org.junit.Assert.*;

/**
 * 
 * @author Patrick Bosek<patrick.bosek@jorsek.com>
 *
 */
public class GetXMLResourceNoLockTest {

	@ClassRule
	public static final ExistWebServer existWebServer = new ExistWebServer(false, false, false, true);

    private static String EMPTY_BINARY_FILE = "What's an up dog?";
    private static XmldbURI DOCUMENT_NAME_URI = XmldbURI.create("empty.txt");
	
	@Test
	public void testCollectionMaintainsLockWhenResourceIsSelectedNoLock() throws EXistException, InterruptedException, LockException, TriggerException, PermissionDeniedException, IOException {

		storeTestResource();

        final BrokerPool pool = BrokerPool.getInstance();

		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
				final Collection testCollection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.READ_LOCK)) {

            final XmldbURI docPath = TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_NAME_URI);

            try(final LockedDocument lockedDoc = broker.getXMLResource(docPath, LockMode.NO_LOCK)) {
                // if document is not present, null is returned
                if (lockedDoc == null) {
                    fail("Binary document '" + docPath + " does not exist.");
                }
            }

            final LockManager lockManager = broker.getBrokerPool().getLockManager();
            final MultiLock colLock = lockManager.getPathLock(testCollection.getURI().toString());
            assertEquals("Collection does not have lock!", true, colLock.getReadHoldCount() > 0);
		}
	}

    private void storeTestResource() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final BrokerPool pool = BrokerPool.getInstance();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection collection = broker
                    .getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            
            broker.saveCollection(transaction, collection);
            
            @SuppressWarnings("unused")
			final BinaryDocument doc =
                    collection.addBinaryResource(transaction, broker,
                    DOCUMENT_NAME_URI , EMPTY_BINARY_FILE.getBytes(), "text/text");
            
            transact.commit(transaction);
        }
    }
}
