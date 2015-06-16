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

import java.io.*;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.btree.BTreeException;
import org.exist.storage.dom.DOMFile;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Test recovery after a forced database corruption.
 * 
 * @author wolf
 *
 */
public class RecoveryTest {
    
    private static String directory = "samples/shakespeare";
    
    private static File dir = null;
    static {
      final String existHome = System.getProperty("exist.home");
      final File existDir = existHome == null ? new File(".") : new File(existHome);
      dir = new File(existDir, directory);
    }
    
    private static String TEST_XML =
        "<?xml version=\"1.0\"?>" +
        "<test>" +
        "  <title>Hello</title>" +
        "  <para>Hello World!</para>" +
        "</test>";

    @Test
    public void storeAndRead() throws PermissionDeniedException, DatabaseConfigurationException, IOException, LockException, SAXException, EXistException, BTreeException, XPathException {
        store();
        tearDown();
        read();
    }

    private void store() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, IOException, SAXException, LockException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager transact = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject());) {

            Collection test2;
            File files[];
            BinaryDocument doc;

            try(final Txn transaction = transact.beginTransaction()) {

                Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
                assertNotNull(root);
                broker.saveCollection(transaction, root);

                test2 = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI2);
                broker.saveCollection(transaction, test2);


                files = dir.listFiles();
                assertNotNull(files);

                doc = test2.addBinaryResource(transaction, broker, TestConstants.TEST_BINARY_URI, "Some text data".getBytes(), null);
                assertNotNull(doc);

                // store some documents. Will be replaced below
	            for(final File f : files) {
                    try {
                        final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                        assertNotNull(info);
                        test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                    } catch (SAXException e) {
//                	    TODO : why pass invalid couments ?
                        System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                    }
                }

                // replace some documents
            for(final File f : files) {
                    try {
                        final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create(f.getName()), new InputSource(f.toURI().toASCIIString()));
                        assertNotNull(info);
                        test2.store(transaction, broker, info, new InputSource(f.toURI().toASCIIString()), false);
                    } catch (SAXException e) {
//                	    TODO : why pass invalid documents ?
                        System.err.println("Error found while parsing document: " + f.getName() + ": " + e.getMessage());
                    }
                }

                final IndexInfo info = test2.validateXMLResource(transaction, broker, XmldbURI.create("test_string.xml"), TEST_XML);
                assertNotNull(info);
                //TODO : unlock the collection here ?

                test2.store(transaction, broker, info, TEST_XML, false);
                // remove last document
                test2.removeXMLResource(transaction, broker, XmldbURI.create(files[files.length - 1].getName()));

                transact.commit(transaction);
            }

            // the following transaction will not be committed. It will thus be rolled back by recovery
            final Txn transaction = transact.beginTransaction();

            test2.removeXMLResource(transaction, broker, XmldbURI.create(files[0].getName()));            
            test2.removeBinaryResource(transaction, broker, doc);
            
//DO NOT COMMIT TRANSACTION
            transact.getJournal().flushToLog(true);

            //DOMFile domDb = ((NativeBroker)broker).getDOMFile();
            //assertNotNull(domDb);
            //Writer writer = new StringWriter();
            //domDb.dump(writer);
            //System.out.println(writer.toString());
	    }
    }

    private void read() throws EXistException, DatabaseConfigurationException, PermissionDeniedException, SAXException, XPathException, IOException, BTreeException {

        BrokerPool.FORCE_CORRUPTION = false;
        final BrokerPool pool = startDB();


        try(final DBBroker broker = pool.get(pool.getSecurityManager().getSystemSubject())) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();
            
            DocumentImpl doc = null;
            try {
                doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/test2/hamlet.xml"), Lock.READ_LOCK);
                assertNotNull("Document '" + XmldbURI.ROOT_COLLECTION + "/test/test2/hamlet.xml' should not be null", doc);
                final String data = serializer.serialize(doc);
                assertNotNull(data);
            } finally {
                if(doc != null) {
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                    doc = null;
                }
            }

            try {
                doc = broker.getXMLResource(XmldbURI.ROOT_COLLECTION_URI.append("test/test2/test_string.xml"), Lock.READ_LOCK);
                assertNotNull("Document '" + XmldbURI.ROOT_COLLECTION + "/test/test2/test_string.xml' should not be null", doc);
                final String data = serializer.serialize(doc);
                assertNotNull(data);
            } finally {
                if(doc != null) {
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                }
            }
            
            final File files[] = dir.listFiles();
            assertNotNull(files);
            
            doc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append(files[files.length - 1].getName()), Lock.READ_LOCK);
            assertNull("Document '" + XmldbURI.ROOT_COLLECTION + "/test/test2/'" + files[files.length - 1].getName() + " should not exist anymore", doc);
            
            final XQuery xquery = broker.getXQueryService();
            assertNotNull(xquery);
            final Sequence seq = xquery.execute("//SPEECH[ft:query(LINE, 'king')]", null, AccessContext.TEST);
            assertNotNull(seq);
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                final String value = serializer.serialize((NodeValue) next);
            }
            
            final BinaryDocument binDoc = (BinaryDocument) broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append(TestConstants.TEST_BINARY_URI), Lock.READ_LOCK);
            assertNotNull("Binary document is null", binDoc);
            try(final InputStream is = broker.getBinaryResource(binDoc)) {
                final byte[] bdata = new byte[(int) broker.getBinaryResourceSize(binDoc)];
                is.read(bdata);
                final String data = new String(bdata);
                assertNotNull(data);
            }
            
            final DOMFile domDb = ((NativeBroker)broker).getDOMFile();
            assertNotNull(domDb);
            try(final Writer writer = new StringWriter()) {
                domDb.dump(writer);
            }
            
            final TransactionManager transact = pool.getTransactionManager();
            try(final Txn transaction = transact.beginTransaction()) {

                final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, Lock.WRITE_LOCK);
                assertNotNull(root);
                transaction.registerLock(root.getLock(), Lock.WRITE_LOCK);
                broker.removeCollection(transaction, root);

                transact.commit(transaction);
            }
	    }
    }
    
    protected BrokerPool startDB() throws DatabaseConfigurationException, EXistException {
        Configuration config = new Configuration();
        BrokerPool.configure(1, 5, config);
        return BrokerPool.getInstance();
    }

    @After
    public void tearDown() {
        BrokerPool.stopAll(false);
    }
}
