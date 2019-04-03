/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class BinaryDocumentTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void removeCollection() throws PermissionDeniedException, IOException, TriggerException, LockException, EXistException {
        final XmldbURI testCollectionUri = XmldbURI.create("/db/remove-collection-test");
        final XmldbURI thingUri = testCollectionUri.append("thing");

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // create a collection
            final Collection thingCollection = broker.getOrCreateCollection(transaction, thingUri);
            broker.saveCollection(transaction, thingCollection);

            // add a binary document to the collection
            final byte[] binaryData1 = "binary-file1".getBytes(UTF_8);
            try (final InputStream is = new FastByteArrayInputStream(binaryData1)) {
                thingCollection.addBinaryResource(transaction, broker, XmldbURI.create("file1.bin"), is, "application/octet-stream", binaryData1.length);
            }

            // remove the collection
            assertTrue(broker.removeCollection(transaction, thingCollection));

            // try and store a binary doc with the same name as the thing collection (should succeed)
            final Collection testCollection = broker.getCollection(testCollectionUri);
            final byte[] binaryData2 = "binary-file2".getBytes(UTF_8);
            try (final InputStream is = new FastByteArrayInputStream(binaryData2)) {
                testCollection.addBinaryResource(transaction, broker, XmldbURI.create("thing"), is, "application/octet-stream", binaryData2.length);
            }
        }
    }

    @Test
    public void overwriteCollection() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        final XmldbURI testCollectionUri = XmldbURI.create("/db/overwrite-collection-test");
        final XmldbURI thingUri = testCollectionUri.append("thing");

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            // create a collection
            final Collection thingCollection = broker.getOrCreateCollection(transaction, thingUri);
            broker.saveCollection(transaction, thingCollection);

            // attempt to create a binary document with the same uri as the thingCollection (should fail)
            final Collection testCollection = broker.getCollection(testCollectionUri);

            final byte[] binaryData = "binary-file".getBytes(UTF_8);
            try (final InputStream is = new FastByteArrayInputStream(binaryData)) {

                try {
                    testCollection.addBinaryResource(transaction, broker, thingUri.lastSegment(), is, "application/octet-stream", binaryData.length);
                    fail("Should not have been able to overwrite Collection with Binary Document");

                } catch (final EXistException e) {
                    assertEquals(
                            "The collection '" + testCollectionUri.getRawCollectionPath() + "' already has a sub-collection named '" + thingUri.lastSegment().toString() + "', you cannot create a Document with the same name as an existing collection.",
                            e.getMessage()
                    );
                }
            }
        }
    }
}
