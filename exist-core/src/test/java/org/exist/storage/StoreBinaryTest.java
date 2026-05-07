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
import java.util.Optional;

import org.exist.collections.triggers.TriggerException;
import org.exist.security.PermissionDeniedException;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.*;
import org.junit.*;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.EXistException;
import org.exist.xmldb.XmldbURI;
import org.exist.test.TestConstants;
import org.exist.collections.Collection;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.*;

/**
 *
 * @author aretter
 */
public class StoreBinaryTest {

    @Test
    public void check_MimeType_is_preserved() throws EXistException, PermissionDeniedException, LockException, IOException, SAXException, DatabaseConfigurationException {

        final String xqueryMimeType = "application/xquery";
        final String xqueryFilename = "script.xql";
        final String xquery = "current-dateTime()";

        //store the xquery document
        BinaryDocument binaryDoc = storeBinary(xqueryFilename, xquery, xqueryMimeType);
        assertNotNull(binaryDoc);
        assertEquals(xqueryMimeType, binaryDoc.getMimeType());

        //make a note of the binary documents uri
        final XmldbURI binaryDocUri = binaryDoc.getFileURI();

        //restart the database
        existEmbeddedServer.restart();

        //retrieve the xquery document
        binaryDoc = getBinary(binaryDocUri);
        assertNotNull(binaryDoc);

        //check the mimetype has been preserved across database restarts
        assertEquals(xqueryMimeType, binaryDoc.getMimeType());
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @After
    public void removeTestResources() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {
            final Collection testCollection = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            broker.removeCollection(transaction, testCollection);
            transaction.commit();
        }
    }

    private BinaryDocument getBinary(final XmldbURI uri) throws EXistException, PermissionDeniedException {
        BinaryDocument binaryDoc = null;

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            assertNotNull(broker);

            final Collection root = broker.getCollection(TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);

            binaryDoc = (BinaryDocument)root.getDocument(broker, uri);

        }

        return binaryDoc;
    }

    private BinaryDocument storeBinary(final String name, final String data, final String mimeType) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();

        BinaryDocument binaryDoc = null;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
    		broker.saveCollection(transaction, root);
            assertNotNull(root);

            root.storeDocument(transaction, broker, XmldbURI.create(name), new StringInputSource(data.getBytes(UTF_8)), new MimeType(mimeType, MimeType.BINARY));
            binaryDoc = (BinaryDocument) root.getDocument(broker, XmldbURI.create(name));

            transact.commit(transaction);
        }

        return binaryDoc;
    }
}
