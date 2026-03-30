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
import java.io.InputStream;
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
import org.exist.util.*;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

/**
 *  0 byte binary files cannot be retrieved from database. This test
 * displays the error.
 *
 * @author wessels
 */
public class ResourceTest {
    
    private final static String EMPTY_BINARY_FILE = "";
    private final static XmldbURI DOCUMENT_NAME_URI = XmldbURI.create("empty.txt");

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }

    @Test
    public void storeAndRead() throws SAXException, PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, EXistException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        store(pool);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = restartDb();

        read(pool);
    }

    private void store(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, SAXException, LockException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final Collection collection = broker
                    .getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            
            broker.saveCollection(transaction, collection);

            broker.storeDocument(transaction, DOCUMENT_NAME_URI, new StringInputSource(EMPTY_BINARY_FILE.getBytes(UTF_8)), MimeType.TEXT_TYPE, collection);
            
            transact.commit(transaction);
        }
    }

    private void read(final BrokerPool pool) throws EXistException,  PermissionDeniedException, IOException, LockException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();
        
        byte[] data = null;
        
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {


            final XmldbURI docPath = TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_NAME_URI);

            try(final LockedDocument lockedDoc = broker.getXMLResource(docPath, LockMode.READ_LOCK)) {
                // if document is not present, null is returned
                if(lockedDoc == null) {
                    fail("Binary document '" + docPath + " does not exist.");
                } else {
                    final BinaryDocument binDoc = (BinaryDocument)lockedDoc.getDocument();
                    try(final InputStream is = broker.getBinaryResource(transaction, binDoc)) {
                        data = new byte[(int) binDoc.getContentLength()];
                        is.read(data);
                    }
                }
            }

            try(final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                try (final LockedDocument lockedDoc = broker.getXMLResource(docPath, LockMode.WRITE_LOCK)) {
                    collection.removeBinaryResource(transaction, broker, lockedDoc.getDocument());
                    broker.saveCollection(transaction, collection);

                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    collection.close();
                }
            }
            transact.commit(transaction);
        }
        
        assertEquals(0, data.length);
    }

    @Test
    public void storeAndRead2() throws SAXException, PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, EXistException {
        BrokerPool.FORCE_CORRUPTION = false;
        BrokerPool pool = startDb();
    	store(pool);

        BrokerPool.FORCE_CORRUPTION = false;
        pool = restartDb();

        read2(pool);
    }

    private void read2(final BrokerPool pool) throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final TransactionManager transact = pool.getTransactionManager();

        byte[] data = null;
        
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            final XmldbURI docPath = TestConstants.TEST_COLLECTION_URI.append(DOCUMENT_NAME_URI);

            try(final LockedDocument lockedDoc = broker.getXMLResource(docPath, LockMode.READ_LOCK)) {

                // if document is not present, null is returned
                if(lockedDoc == null) {
                    fail("Binary document '" + docPath + " does not exist.");
                } else {
                    final BinaryDocument binDoc = (BinaryDocument)lockedDoc.getDocument();
                    try(final InputStream is = broker.getBinaryResource(transaction, binDoc)) {
                        data = new byte[(int) binDoc.getContentLength()];
                        is.read(data);
                    }
                }
            }
            
            try(final Collection collection = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                broker.removeCollection(transaction, collection);
            }

            transact.commit(transaction);
        }
        
        assertEquals(0, data.length);
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    private BrokerPool restartDb() throws DatabaseConfigurationException, IOException, EXistException {
        existEmbeddedServer.restart(false);
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
