/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing.lucene;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;

import org.apache.lucene.index.AtomicReader;
import org.apache.tools.ant.filters.StringInputStream;
import org.exist.Indexer;
import org.exist.TestUtils;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.IndexInfo;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DefaultDocumentSet;
import org.exist.dom.DocumentSet;
import org.exist.dom.MutableDocumentSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.md.MetaData;
import org.exist.storage.md.Metas;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.ConfigurationHelper;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import static org.exist.collections.CollectionConfigurationManager.ROOT_COLLECTION_CONFIG_URI;

public class FacetAbstract {

    protected static final String STATUS = "status";

    protected static BrokerPool db;
    protected static Collection root;
    protected Boolean savedConfig;

    protected DocumentSet configureAndStore(String configuration, Resource[] resources) {

        MetaData md = MetaData.get();
        assertNotNull(md);

        MutableDocumentSet docs = new DefaultDocumentSet();
        try (DBBroker broker = db.get(db.getSecurityManager().getSystemSubject())) {
            assertNotNull(broker);

            try (Txn txn = broker.beginTx()) {

                assertNotNull(txn);

                if (configuration != null) {
                    CollectionConfigurationManager mgr = db.getConfigurationManager();
                    mgr.addConfiguration(txn, broker, root, configuration);
                }

                for (Resource resource : resources) {

                    XmldbURI docURL = root.getURI().append(resource.docName);

                    Metas docMD = md.getMetas(docURL);
                    if (docMD == null) {
                        docMD = md.addMetas(docURL);
                    }
                    assertNotNull(docMD);

                    for (Entry<String, String> entry : resource.metas.entrySet()) {
                        docMD.put(entry.getKey(), entry.getValue());
                    }

                    if (resource.type == "XML") {

                        IndexInfo info = root.validateXMLResource(txn, broker, XmldbURI.create(resource.docName), resource.data);
                        assertNotNull(info);

                        root.store(txn, broker, info, resource.data, false);

                        docs.add(info.getDocument());

                        //broker.reindexXMLResource(transaction, info.getDocument());
                    } else {

                        final MimeTable mimeTable = MimeTable.getInstance();

                        final MimeType mimeType = mimeTable.getContentTypeFor(resource.docName);

                        InputStream is = new StringInputStream(resource.data);

                        XmldbURI name = XmldbURI.create(resource.docName);

                        BinaryDocument binary = root.validateBinaryResource(txn, broker, name, is, mimeType.toString(), (long) -1, (Date) null, (Date) null);

                        binary = root.addBinaryResource(txn, broker, name, is, mimeType.getName(), -1, (Date) null, (Date) null);

                        docs.add(binary);
                    }

                }
                txn.success();
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
        
        return docs;
    }

    @Before
    public void setup() {
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            broker = db.get(db.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = db.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            savedConfig = (Boolean) config.getProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT);
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, Boolean.TRUE);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (db != null)
                db.release(broker);
        }
    }

    @After
    public void cleanup() {
        BrokerPool pool = null;
        DBBroker broker = null;
        TransactionManager transact = null;
        Txn transaction = null;
        try {
            pool = BrokerPool.getInstance();
            assertNotNull(pool);
            broker = pool.get(pool.getSecurityManager().getSystemSubject());
            assertNotNull(broker);
            transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);

            Collection collConfig = broker.getOrCreateCollection(transaction, ROOT_COLLECTION_CONFIG_URI);
            assertNotNull(collConfig);
            broker.removeCollection(transaction, collConfig);

            if (root != null) {
                assertNotNull(root);
                broker.removeCollection(transaction, root);
            }
            transact.commit(transaction);

            Configuration config = BrokerPool.getInstance().getConfiguration();
            config.setProperty(Indexer.PROPERTY_PRESERVE_WS_MIXED_CONTENT, savedConfig);
        } catch (Exception e) {
            if (transact != null)
                transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
        } finally {
            if (pool != null) pool.release(broker);
        }
    }

    @BeforeClass
    public static void startDB() {
        try {
            File confFile = ConfigurationHelper.lookup("conf.xml");
            Configuration config = new Configuration(confFile.getAbsolutePath());
            config.setProperty(Indexer.PROPERTY_SUPPRESS_WHITESPACE, "none");
            config.setProperty(Indexer.PRESERVE_WS_MIXED_CONTENT_ATTRIBUTE, Boolean.TRUE);
            BrokerPool.configure(1, 5, config);
            db = BrokerPool.getInstance();
            assertNotNull(db);
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @AfterClass
    public static void stopDB() {
        TestUtils.cleanupDB();
        BrokerPool.stopAll(false);
        db = null;
        root = null;
    }
    
    protected class Resource {
        final String docName;
        final String data;
        final Map<String, String> metas;
        final String type;
        
        Resource(String docName, String data, Map<String, String> metas) {
            this.docName = docName;
            this.data = data;
            this.metas = metas;
            
            type = "XML";
        }

        Resource(String type, String docName, String data, Map<String, String> metas) {
            this.type = type;
            this.docName = docName;
            this.data = data;
            this.metas = metas;
        }
    }
    
    protected class Counter<T> implements SearchCallback<T> {

        int count = 0;
        int total = 0;
        
        @Override
        public void found(AtomicReader reader, int docNum, T element, float score) {
        	System.out.println("score " + score + " : " + element);
            count++;
        }

		@Override
		public void totalHits(Integer number) {
			total = number;
		}
        
		public void reset() {
			count = 0;
			total = 0;
		}
    }
}

