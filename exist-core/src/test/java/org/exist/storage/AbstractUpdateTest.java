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
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.AfterClass;
import org.junit.Test;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;

public abstract class AbstractUpdateTest {

	protected static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public final void update() throws EXistException, DatabaseConfigurationException, LockException, SAXException, PermissionDeniedException, IOException, ParserConfigurationException, XPathException {
        BrokerPool.FORCE_CORRUPTION = true;
        BrokerPool pool = startDb();
        try {
            final TransactionManager transact = pool.getTransactionManager();

            try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
                final DocumentImpl doc = init(broker, transact);
                final MutableDocumentSet docs = new DefaultDocumentSet();
                docs.add(doc);

                doUpdate(broker, transact, docs);

                pool.getJournalManager().get().flush(true, false);
            }

            BrokerPool.FORCE_CORRUPTION = false;
            existEmbeddedServer.restart(false);
            pool = existEmbeddedServer.getBrokerPool();

            read(pool);
        } finally {
            existEmbeddedServer.stopDb(true);
        }
    }

    protected abstract void doUpdate(final DBBroker broker, final TransactionManager transact, final MutableDocumentSet docs)  throws ParserConfigurationException, IOException, SAXException, LockException, XPathException, PermissionDeniedException, EXistException;

    private void read(final BrokerPool pool) throws EXistException, PermissionDeniedException, SAXException, XPathException {

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final Serializer serializer = broker.borrowSerializer();
            try(final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append("test2/test.xml"), LockMode.READ_LOCK)) {

                assertNotNull("Document '" + TEST_COLLECTION_URI.append("test2/test.xml") + "' should not be null", lockedDoc);
                final String data = serializer.serialize(lockedDoc.getDocument());
            } finally {
                broker.returnSerializer(serializer);
            }
            
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, "/products/product[last()]", null);
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
            }
        }
    }

    protected DocumentImpl init(final DBBroker broker, final TransactionManager transact) throws PermissionDeniedException, IOException, SAXException, LockException, EXistException {
    	DocumentImpl doc = null;
        try(final Txn transaction = transact.beginTransaction()) {
	        
	        final Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
	        broker.saveCollection(transaction, root);
	        
	        final Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
	        broker.saveCollection(transaction, test);
	        
	        broker.storeDocument(transaction, XmldbURI.create("test.xml"), new StringInputSource(TEST_XML), MimeType.XML_TYPE, test);
            doc = test.getDocument(broker, XmldbURI.create("test.xml"));
	        //TODO : unlock the collection here ?
	
	        transact.commit(transaction);	
	    }
	    return doc;
    }
    
    protected BrokerPool startDb() throws DatabaseConfigurationException, EXistException, IOException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @AfterClass
    public static void cleanup() {
        BrokerPool.FORCE_CORRUPTION = false;
    }
}
