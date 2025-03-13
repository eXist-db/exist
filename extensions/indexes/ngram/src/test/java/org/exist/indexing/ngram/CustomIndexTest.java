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
package org.exist.indexing.ngram;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.CollectionConfigurationManager;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.*;
import org.exist.util.serializer.SAXSerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * 
 */
public class CustomIndexTest {

    private static String XML =
            "<test>" +
            "   <item id='1' attr='attribute'><description>Chair</description></item>" +
            "   <item id='2'><description>Table</description><price>892.25</price></item>" +
            "   <item id='3'><description>Cabinet</description><price>1525.00</price></item>" +
            "</test>";

    private static String XML2 =
            "<section>" +
            "   <para>01234</para>" +
            "   <para>56789</para>" +
            "</section>";
    
    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
        "   <index>" +
        "       <ngram qname=\"item\"/>" +
        "       <ngram qname=\"@attr\"/>" +
        "       <ngram qname=\"para\"/>" +
        "   </index>" +
        "</collection>";

    private static String XUPDATE_START =
        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">";

    private static String XUPDATE_END =
        "</xu:modifications>";

    private MutableDocumentSet docs;

    /**
     * Remove nodes from different levels of the tree and check if the index is
     * correctly updated.
     */
    @Test
    public void xupdateRemove() throws EXistException, PermissionDeniedException, XPathException, ParserConfigurationException, IOException, SAXException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {
            
            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='2']/price\"/>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "892", 0);
            checkIndex(broker, docs, "tab", 1);
            checkIndex(broker, docs, "le8", 0);

            checkIndex(broker, docs, "cab", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='3']/description/text()\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "cab", 0);

            checkIndex(broker, docs, "att", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='1']/@attr\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "att", 0);

            checkIndex(broker, docs, "cha", 1);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:remove select=\"//item[@id='1']\"/>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "cha", 0);
            
            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateInsert() throws EXistException, LockException, XPathException, PermissionDeniedException, SAXException, IOException, ParserConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:append select=\"/test\">" +
                    "       <item id='4'><description>Armchair</description><price>340</price></item>" +
                    "   </xu:append>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "arm", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']\">" +
                    "           <item id='0'><description>Wheelchair</description><price>1230</price></item>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "hee", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']\">" +
                    "           <item id='1.1'><description>refrigerator</description><price>777</price></item>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "ref", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-after select=\"//item[@id = '1']/description\">" +
                    "           <price>999</price>" +
                    "       </xu:insert-after>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "999", 1);
            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "ir9", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:insert-before select=\"//item[@id = '1']/description\">" +
                    "           <price>888</price>" +
                    "       </xu:insert-before>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "999", 1);
            checkIndex(broker, docs, "888", 1);
            checkIndex(broker, docs, "88c", 1);

            checkIndex(broker, docs, "att", 1);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "       <xu:append select=\"//item[@id = '1']\">" +
                    "           <xu:attribute name=\"attr\">abc</xu:attribute>" +
                    "       </xu:append>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();
            checkIndex(broker, docs, "att", 0);
            checkIndex(broker, docs, "abc", 1);

            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateUpdate() throws EXistException, LockException, XPathException, PermissionDeniedException, SAXException, IOException, ParserConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description\">wardrobe</xu:update>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "war", 1);
            checkIndex(broker, docs, "cha", 0);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/description/text()\">Wheelchair</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "whe", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:update select=\"//item[@id = '1']/@attr\">abc</xu:update>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();
            checkIndex(broker, docs, "abc", 1);

            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateReplace() throws LockException, XPathException, PermissionDeniedException, SAXException, EXistException, IOException, ParserConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:replace select=\"//item[@id = '1']\">" +
                    "       <item id='4'><description>Wheelchair</description><price>809.50</price></item>" +
                    "   </xu:replace>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "whe", 1);

            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            xupdate =
                    XUPDATE_START +
                    "   <xu:replace select=\"//item[@id = '4']/description\">" +
                    "       <description>Armchair</description>" +
                    "   </xu:replace>" +
                    XUPDATE_END;
            modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "whe", 0);
            checkIndex(broker, docs, "arm", 1);

            transact.commit(transaction);
        }
    }

    @Test
    public void xupdateRename() throws EXistException, LockException, XPathException, PermissionDeniedException, SAXException, IOException, ParserConfigurationException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
            assertNotNull(proc);
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            String xupdate =
                    XUPDATE_START +
                    "   <xu:rename select=\"//item[@id='2']\">renamed</xu:rename>" +
                    XUPDATE_END;
            Modification[] modifications = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            checkIndex(broker, docs, "tab", 0);

            transact.commit(transaction);
        }
    }
 
    @Test
    public void reindex() throws PermissionDeniedException, XPathException, URISyntaxException, EXistException, IOException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            	final Txn transaction = transact.beginTransaction()) {

            //Doh ! This reindexes *all* the collections for *every* index
            broker.reindexCollection(transaction, XmldbURI.xmldbUriFor("/db"));

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//section[ngram:contains(para, '123')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//section[ngram:contains(para, '123')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            transact.commit(transaction);
        }
    }

    @Test
    public void dropIndex() throws EXistException, PermissionDeniedException, XPathException, LockException, TriggerException, IOException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
            final Txn transaction = transact.beginTransaction()) {

            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            checkIndex(broker, docs, "cha", 1);
            checkIndex(broker, docs, "le8", 1);

            try(final Collection root = broker.openCollection(TestConstants.TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {
                assertNotNull(root);
                root.removeXMLResource(transaction, broker, XmldbURI.create("test_string.xml"));
            }

            checkIndex(broker, docs, "cha", 0);

            seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(0, seq.getItemCount());

            transact.commit(transaction);
        }
    }

    @Test
    public void query() throws PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            Sequence seq = xquery.execute(broker, "//item[ngram:contains(., 'cha')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//section[ngram:contains(*, '123')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//section[ngram:contains(para, '123')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "//*[ngram:contains(., '567')]", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

        }
    }

    @Test
    public void indexKeys() throws SAXException, PermissionDeniedException, XPathException, EXistException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            XQuery xquery = pool.getXQueryService();
            assertNotNull(xquery);
            
            Sequence seq = xquery.execute(broker, "util:index-key-occurrences(/test/item, 'cha', 'ngram-index')", null);
            //Sequence seq = xquery.execute("util:index-key-occurrences(/test/item, 'cha', 'org.exist.indexing.impl.NGramIndex')", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "util:index-key-occurrences(/test/item, 'le8', 'ngram-index')", null);
            //seq = xquery.execute("util:index-key-occurrences(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());

            seq = xquery.execute(broker, "util:index-key-documents(/test/item, 'le8', 'ngram-index')", null);
            //seq = xquery.execute("util:index-key-documents(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            
            seq = xquery.execute(broker, "util:index-key-documents(/test/item, 'le8', 'ngram-index')", null);
            //seq = xquery.execute("util:index-key-doucments(/test/item, 'le8', 'org.exist.indexing.impl.NGramIndex')", null);
            assertNotNull(seq);
            assertEquals(1, seq.getItemCount());
            
            String queryBody =
                "declare function local:callback($key as item(), $data as xs:int+)\n" +
                "as element()+ {\n" + 
                "    <item>\n" + 
                "        <key>{$key}</key>\n" + 
                "        <frequency>{$data[1]}</frequency>\n" + 
                "    </item>\n" + 
                "};\n" + 
                "\n";
            
            String query = queryBody + "util:index-keys(/test/item, \'\', util:function(xs:QName(\'local:callback\'), 2), 1000, 'ngram-index')";
            //String query = queryBody + "util:index-keys(/test/item, \'\', util:function(xs:QName(\'local:callback\'), 2), 1000, 'org.exist.indexing.impl.NGramIndex')";
            seq = xquery.execute(broker, query, null);
            assertNotNull(seq);
            //TODO : check cardinality
            StringWriter out = new StringWriter();
            Properties props = new Properties();
            props.setProperty(OutputKeys.INDENT, "yes");
            SAXSerializer serializer = new SAXSerializer(out, props);
            serializer.startDocument();
            for (SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                Item next = i.nextItem();
                next.toSAX(broker, serializer, props);
            }
            serializer.endDocument();
            //TODO : check content


        }
    }

    //TODO : could be replaced by an XQuery call to index-keys(). See above
    private void checkIndex(DBBroker broker, DocumentSet docs, String term, int count) {
        NGramIndexWorker index = (NGramIndexWorker) broker.getIndexController().getWorkerByIndexId(NGramIndex.ID);
        XQueryContext context = new XQueryContext(broker.getBrokerPool());
        Occurrences[] occurrences = index.scanIndex(context, docs, null, null);
        int found = 0;
        for (Occurrences occurrence : occurrences) {
            if (occurrence.getTerm().compareTo(term) == 0)
                found++;
        }        
        assertEquals(count, found);        
    }

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Before
    public void setUp() throws DatabaseConfigurationException, EXistException, PermissionDeniedException, IOException, SAXException, CollectionConfigurationException, LockException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.saveCollection(transaction, root);

            CollectionConfigurationManager mgr = pool.getConfigurationManager();
            mgr.addConfiguration(transaction, broker, root, COLLECTION_CONFIG);

            docs = new DefaultDocumentSet();

            broker.storeDocument(transaction, XmldbURI.create("test_string.xml"), new StringInputSource(XML), MimeType.XML_TYPE, root);
            docs.add(root.getDocument(broker, XmldbURI.create("test_string.xml")));

            broker.storeDocument(transaction, XmldbURI.create("test_string2.xml"), new StringInputSource(XML2), MimeType.XML_TYPE, root);
            docs.add(root.getDocument(broker, XmldbURI.create("test_string2.xml")));

            transact.commit(transaction);
        }
    }

    @After
    public void tearDown() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = BrokerPool.getInstance();

        final TransactionManager transact = pool.getTransactionManager();
        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final Txn transaction = transact.beginTransaction()) {

            Collection root = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            assertNotNull(root);
            broker.removeCollection(transaction, root);

            Collection config = broker.getOrCreateCollection(transaction,
                XmldbURI.create(CollectionConfigurationManager.CONFIG_COLLECTION + "/db"));
            assertNotNull(config);
            broker.removeCollection(transaction, config);
            
            transact.commit(transaction);
        }
    }
}
