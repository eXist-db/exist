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
package org.exist.indexing.lucene;

import org.apache.lucene.document.IntField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.util.BytesRef;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.NodeProxy;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.w3c.dom.Element;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a binary field of a node (the value that {@code ft:binary-field} returns) must work when the segment
 * that holds the node's Lucene document also holds deleted documents.
 * <p>
 * Lucene numbers the documents of a segment from 0 and a deleted document keeps its number until the segment is
 * merged, so such a segment has more numbers than live documents. The lookup used to compare a document's number
 * with the count of live documents, and so found no value for the documents numbered above it.
 */
public class LuceneBinaryFieldLookupTest {

    private static final String COLLECTION_NAME = "binary-field-lookup";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);
    private static final String DROP = "drop.xml";
    private static final String KEEP = "keep.xml";
    private static final String FIELD = "sortable";
    private static final int DROP_ITEMS = 3;
    private static final int KEEP_ITEMS = 14;

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0" xmlns:xs="http://www.w3.org/2001/XMLSchema">
              <index>
                <lucene>
                  <text qname="item">
                    <field name="%s" expression="./@sortable/string()" type="xs:string" binary="yes"/>
                  </text>
                </lucene>
              </index>
            </collection>
            """.formatted(FIELD);

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @AfterEach
    void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = transactionManager.beginTransaction()) {
            for (final XmldbURI uri : List.of(COLLECTION, CONFIG_COLLECTION)) {
                try (final Collection collection = broker.openCollection(uri, LockMode.WRITE_LOCK)) {
                    if (collection != null) {
                        broker.removeCollection(transaction, collection);
                    }
                }
            }
            transactionManager.commit(transaction);
        }
    }

    @Test
    void binaryFieldOfEveryNodeIsFoundAfterAnotherDocumentWasDeleted() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            final LuceneIndex index = worker.index;

            // the document that is deleted is stored first, so that it is numbered below the one that is kept
            try (final Txn transaction = transactionManager.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, CONFIGURATION);
                store(broker, transaction, collection, DROP, DROP_ITEMS);
                store(broker, transaction, collection, KEEP, KEEP_ITEMS);
                transactionManager.commit(transaction);
            }

            final List<NodeProxy> items = items(pool, broker);
            assertEquals(KEEP_ITEMS, items.size());
            final int keepDocId = items.get(0).getOwnerDocument().getDocId();

            // a search makes Lucene write what was indexed so far, so every value can be read
            index.withSearcher(searcher -> null);
            final List<BytesRef> before = new ArrayList<>();
            for (final NodeProxy item : items) {
                final BytesRef value = worker.getBinaryFieldByExistDocId(keepDocId, item.getNodeId(), FIELD);
                assertNotNull(value, "before the deletion, the value of " + describe(item));
                before.add(BytesRef.deepCopyOf(value));
            }

            try (final Txn transaction = transactionManager.beginTransaction();
                 final Collection collection = broker.openCollection(COLLECTION, LockMode.WRITE_LOCK)) {
                collection.removeXMLResource(transaction, broker, XmldbURI.create(DROP));
                transactionManager.commit(transaction);
            }
            // and a second search applies the deletion to that segment
            index.withSearcher(searcher -> null);

            assertTrue(isNumberedAboveLiveDocuments(index, keepDocId),
                    "this test only tests the lookup if a Lucene document of the kept document is numbered above the "
                            + "live documents of its segment; if it is not, the two documents were not written as one segment: "
                            + positions(index, keepDocId));

            for (int i = 0; i < items.size(); i++) {
                final BytesRef value = worker.getBinaryFieldByExistDocId(keepDocId, items.get(i).getNodeId(), FIELD);
                assertNotNull(value, "after the deletion, the value of " + describe(items.get(i)));
                assertEquals(before.get(i), value, "after the deletion, the value of " + describe(items.get(i)));
            }
        }
    }

    private static void store(final DBBroker broker, final Txn transaction, final Collection collection, final String name, final int items) throws Exception {
        final StringBuilder xml = new StringBuilder("<div>");
        for (int i = 1; i <= items; i++) {
            xml.append("""
                    <item sortable="%03d">match</item>""".formatted(i));
        }
        xml.append("</div>");
        broker.storeDocument(transaction, XmldbURI.create(name), new StringInputSource(xml.toString()), MimeType.XML_TYPE, collection);
    }

    /** the items of the document that is kept, found without Lucene, so that nothing is written to the index */
    private static List<NodeProxy> items(final BrokerPool pool, final DBBroker broker) throws Exception {
        final XQuery xquery = pool.getXQueryService();
        final Sequence sequence = xquery.execute(broker, "doc('" + COLLECTION + "/" + KEEP + "')//item", null);
        final List<NodeProxy> items = new ArrayList<>();
        for (final SequenceIterator it = sequence.iterate(); it.hasNext(); ) {
            items.add((NodeProxy) it.nextItem());
        }
        return items;
    }

    private static String describe(final NodeProxy item) {
        return "item " + ((Element) item.getNode()).getAttribute(FIELD);
    }

    /**
     * @return where the Lucene documents of the given eXist document are: for each, the segment it is in, its number
     * within the segment and the number of live and of all documents of that segment
     */
    private static List<String> positions(final LuceneIndex index, final int existDocId) throws Exception {
        return index.withSearcher(searcher -> {
            final List<LeafReaderContext> leaves = searcher.searcher().getIndexReader().leaves();
            final ScoreDoc[] hits = searcher.searcher().search(IntField.newExactQuery(LuceneUtil.FIELD_DOC_ID, existDocId), 1000).scoreDocs;
            final List<String> positions = new ArrayList<>();
            for (final ScoreDoc hit : hits) {
                final int segment = ReaderUtil.subIndex(hit.doc, leaves);
                final LeafReaderContext leaf = leaves.get(segment);
                positions.add("segment " + segment + ": document " + (hit.doc - leaf.docBase) + " of " + leaf.reader().numDocs()
                        + " live, " + leaf.reader().maxDoc() + " in all");
            }
            return positions;
        });
    }

    /** @return true if a document is numbered at or above the number of live documents of its segment, which is where the lookup used to find nothing */
    private static boolean isNumberedAboveLiveDocuments(final LuceneIndex index, final int existDocId) throws Exception {
        return index.withSearcher(searcher -> {
            final List<LeafReaderContext> leaves = searcher.searcher().getIndexReader().leaves();
            final ScoreDoc[] hits = searcher.searcher().search(IntField.newExactQuery(LuceneUtil.FIELD_DOC_ID, existDocId), 1000).scoreDocs;
            for (final ScoreDoc hit : hits) {
                final LeafReaderContext leaf = leaves.get(ReaderUtil.subIndex(hit.doc, leaves));
                if (hit.doc - leaf.docBase >= leaf.reader().numDocs()) {
                    return true;
                }
            }
            return false;
        });
    }
}
