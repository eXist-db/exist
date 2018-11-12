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
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
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
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    private final static String generateXQ =
            "<book id=\"{$filename}\" n=\"{$count}\">" +
            "   <chapter>" +
            "       <title>{pt:random-text(7)}</title>" +
            "       {" +
            "           for $section in 1 to 8 return" +
            "               <section id=\"sect{$section}\">" +
            "                   <title>{pt:random-text(7)}</title>" +
            "                   {" +
            "                       for $para in 1 to 10 return" +
            "                           <para>{pt:random-text(40)}</para>" +
            "                   }" +
            "               </section>" +
            "       }" +
            "   </chapter>" +
            "</book>";

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text match=\"/*\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";
    
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
                    assertNotNull(is);
                    final IndexInfo info = test.validateXMLResource(transaction, broker, doc.getURI(), is);
                    assertNotNull(info);
                    test.store(transaction, broker, info, is);
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

            final CollectionConfigurationManager mgr = broker.getBrokerPool().getConfigurationManager();
            mgr.addConfiguration(transaction, broker, test, COLLECTION_CONFIG);

            final InputSource is = new InputSource(TestUtils.resolveShakespeareSample("hamlet.xml").toUri().toASCIIString());
            assertNotNull(is);
            final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create("hamlet.xml"), is);
            assertNotNull(info);
            test.store(transaction, broker, info, is);
            transact.commit(transaction);
        }

        try(final Txn transaction = transact.beginTransaction()) {
            final TestDataGenerator generator = new TestDataGenerator("xdb", COUNT);
            final Path[] files = generator.generate(broker, test, generateXQ);
            for(final Path file : files) {
                final InputSource is = new InputSource(file.toUri().toASCIIString());
                assertNotNull(is);
                final IndexInfo info = test.validateXMLResource(transaction, broker, XmldbURI.create(file.getFileName().toString()), is);
                assertNotNull(info);
                test.store(transaction, broker, info, is);
            }
            generator.releaseAll();
            transact.commit(transaction);
        }
        return test;
    }

    public void recover(final BrokerPool pool, final boolean checkResource) throws EXistException, PermissionDeniedException, DatabaseConfigurationException, IOException {
        DocumentImpl doc = null;
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {
            if (checkResource) {
                doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI.append("hamlet.xml"), LockMode.READ_LOCK);
                assertNull("Resource should have been removed", doc);
            }
	    } finally {
            if (doc != null) {
                doc.getUpdateLock().release(LockMode.READ_LOCK);
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
