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
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.After;
import org.junit.Test;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.util.Optional;

import static org.junit.Assert.assertNotNull;

public abstract class AbstractUpdateTest {

	protected static XmldbURI TEST_COLLECTION_URI = XmldbURI.ROOT_COLLECTION_URI.append("test");
    protected static String TEST_XML = 
        "<?xml version=\"1.0\"?>" +
        "<products/>";

    // we don't use @ClassRule/@Rule as we want to force corruption in some tests
    private ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, false);

    @Test
    public void read() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, SAXException, XPathException, IOException {
        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDb();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            

            DocumentImpl doc = null;
            try {
                doc = broker.getXMLResource(TEST_COLLECTION_URI.append("test2/test.xml"), LockMode.READ_LOCK);
                assertNotNull("Document '" + TEST_COLLECTION_URI.append("test2/test.xml") + "' should not be null", doc);
                final String data = serializer.serialize(doc);
            } finally {
                if(doc != null) {
                    doc.getUpdateLock().release(LockMode.READ_LOCK);
                }
            }
            
            final XQuery xquery = pool.getXQueryService();
            final Sequence seq = xquery.execute(broker, "/products/product[last()]", null);
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
            }
        }
    }

    protected IndexInfo init(final DBBroker broker, final TransactionManager transact) throws PermissionDeniedException, IOException, SAXException, LockException, EXistException {
    	IndexInfo info = null;
    	try(final Txn transaction = transact.beginTransaction()) {
	        
	        final Collection root = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
	        broker.saveCollection(transaction, root);
	        
	        final Collection test = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI.append("test2"));
	        broker.saveCollection(transaction, test);
	        
	        info = test.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), TEST_XML);
	        //TODO : unlock the collection here ?
	        test.store(transaction, broker, info, TEST_XML);
	
	        transact.commit(transaction);	
	    }
	    return info;
    }
    
    protected BrokerPool startDb() throws DatabaseConfigurationException, EXistException, IOException {
        existEmbeddedServer.startDb();
        return existEmbeddedServer.getBrokerPool();
    }

    @After
    public void stopDb() {
        existEmbeddedServer.stopDb();
    }    
}
