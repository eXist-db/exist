/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.collections;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Optional;

import static junit.framework.TestCase.assertNotNull;

public class OpenCollectionTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    private static XmldbURI TEST_COLLECTION = XmldbURI.ROOT_COLLECTION_URI.append("testCollection");

    @BeforeClass
    public static void init() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            transaction.commit();

            try (final Collection col = broker.openCollection(TEST_COLLECTION, Lock.LockMode.READ_LOCK)) {
                assertNotNull(col);
            }
        }
    }

    /**
     * Test opening a collection using a full XmldbURI including scheme.
     */
    @Test
    public void loadFullXmldbURI() throws PermissionDeniedException, IOException, EXistException, URISyntaxException, DatabaseConfigurationException {
        loadCollection(XmldbURI.xmldbUriFor("xmldb:exist:///db/testCollection"));
    }

    @Test
    public void loadRelativeXmldbURI() throws PermissionDeniedException, IOException, EXistException, URISyntaxException, DatabaseConfigurationException {
        loadCollection(XmldbURI.xmldbUriFor("testCollection"));
    }

    private void loadCollection(XmldbURI uri) throws DatabaseConfigurationException, IOException, EXistException, PermissionDeniedException {
        // Restart database, otherwise collection would be read from cache
        existEmbeddedServer.restart();

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            try (final Collection col = broker.openCollection(uri, Lock.LockMode.READ_LOCK)) {
                assertNotNull(col);
            }
        }
    }
}
