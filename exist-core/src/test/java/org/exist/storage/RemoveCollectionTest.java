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

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.TestDataGenerator;
import org.junit.After;
import org.junit.AfterClass;
import org.xml.sax.InputSource;
import static org.junit.Assert.*;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Optional;

/**
 * @author wolf
 *
 */
public class RemoveCollectionTest {

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private final static String generateXQ =
            "declare function local:random-sequence($length as xs:integer, $G as map(xs:string, item())) {\n"
            + "  if ($length eq 0)\n"
            + "  then ()\n"
            + "  else ($G?number, local:random-sequence($length - 1, $G?next()))\n"
            + "};\n"
            + "let $rnd := fn:random-number-generator() return"
            + "<book id=\"{$filename}\" n=\"{$count}\">"
            + "   <chapter xml:id=\"chapter{$count}\">"
            + "       <title>{local:random-sequence(7, $rnd)}</title>"
            + "       {"
            + "           for $section in 1 to 8 return"
            + "               <section id=\"sect{$section}\">"
            + "                   <title>{local:random-sequence(7, $rnd)}</title>"
            + "                   {"
            + "                       for $para in 1 to 10 return"
            + "                           <para>{local:random-sequence(120, $rnd)}</para>"
            + "                   }"
            + "               </section>"
            + "       }"
            + "   </chapter>"
            + "</book>";
    
    private final static int COUNT = 300;

    @Test
    public void removeCollectionTests() throws PermissionDeniedException, IOException, LockException, CollectionConfigurationException, SAXException, EXistException, DatabaseConfigurationException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        removeCollection(pool);
        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        recover(pool, true);
        stopDb();

        BrokerPool.FORCE_CORRUPTION = true;
        pool = startDb();
        removeResources(pool);
        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        recover(pool, true);
        stopDb();

        BrokerPool.FORCE_CORRUPTION = true;
        pool = startDb();
        replaceResources(pool);
        stopDb();

        BrokerPool.FORCE_CORRUPTION = false;
        pool = startDb();
        recover(pool, false);
    }

    public void removeCollection(final BrokerPool pool) throws PermissionDeniedException, IOException, CollectionConfigurationException, SAXException, EXistException, LockException, DatabaseConfigurationException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Collection test = storeDocs(broker, transact);

            try(final Txn transaction = transact.beginTransaction()) {
                broker.removeCollection(transaction, test);

                transact.commit(transaction);
            }
        }
    }

    public void removeResources(final BrokerPool pool) throws PermissionDeniedException, IOException, SAXException, EXistException, LockException, CollectionConfigurationException, DatabaseConfigurationException {
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

            final Collection test = storeDocs(broker, transact);

            try(final Txn transaction = transact.beginTransaction()) {

                for (final Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext(); ) {
                    final DocumentImpl doc = i.next();
                    broker.removeXMLResource(transaction, doc);
                }
                broker.saveCollection(transaction, test);
                transact.commit(transaction);
            }
        }
    }

    public void replaceResources(final BrokerPool pool) throws SAXException, PermissionDeniedException, EXistException, LockException, IOException, CollectionConfigurationException, DatabaseConfigurationException {
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Collection test = storeDocs(broker, transact);

            try(final Txn transaction = transact.beginTransaction()) {

                TestDataGenerator generator = new TestDataGenerator("xdb", COUNT);
                final Path[] files = generator.generate(broker, test, generateXQ);

                int j = 0;
                for (final Iterator<DocumentImpl> i = test.iterator(broker); i.hasNext() && j < files.length; j++) {
                    final DocumentImpl doc = i.next();
                    final InputSource is = new InputSource(files[j].toUri().toASCIIString());
                    broker.storeDocument(transaction, doc.getURI(), is, MimeType.XML_TYPE, test);
                }
                generator.releaseAll();
                transact.commit(transaction);
            }
        }
    }

    private Collection storeDocs(final DBBroker broker, final TransactionManager transact) throws PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException, EXistException {
        Collection test;

        try(final Txn transaction = transact.beginTransaction()) {

            test = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(test);
            broker.saveCollection(transaction, test);

            transact.commit(transaction);
        }

        try(final Txn transaction = transact.beginTransaction()) {
            final TestDataGenerator generator = new TestDataGenerator("xdb", COUNT);
            final Path[] files = generator.generate(broker, test, generateXQ);
            for(final Path file : files) {
                final InputSource is = new InputSource(file.toUri().toASCIIString());

                broker.storeDocument(transaction, XmldbURI.create(file.getFileName().toString()), is, MimeType.XML_TYPE, test);
            }
            generator.releaseAll();
            transact.commit(transaction);
        }
        return test;
    }

    public void recover(final BrokerPool pool, final boolean checkResource) throws EXistException, PermissionDeniedException, DatabaseConfigurationException, IOException {
        LockedDocument lockedDoc = null;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            if (checkResource) {
                lockedDoc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append("hamlet.xml"), LockMode.READ_LOCK);
                assertNull("Resource should have been removed", lockedDoc);
            }
	    } finally {
            if (lockedDoc != null) {
                lockedDoc.close();
            }
        }
    }

    private BrokerPool startDb() throws EXistException, IOException, DatabaseConfigurationException {
        existEmbeddedServer.startDb();
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
