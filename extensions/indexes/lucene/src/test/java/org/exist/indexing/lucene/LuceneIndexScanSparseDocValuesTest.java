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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.IntField;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scanning the terms of a Lucene index (what {@code util:index-keys} does) must work when the segment also holds
 * documents that have no node id.
 * <p>
 * The scan reads the node id of each hit from a doc values iterator, which only moves forward. It used to keep one
 * iterator for all terms, although the hits of every term start again at the first document. A segment in which
 * every document has a node id tolerates that, a segment in which only some do (documents without one are
 * written by {@code ft:index}) does not: with assertions on, as in tests, it failed with an AssertionError.
 */
public class LuceneIndexScanSparseDocValuesTest {

    private static final String COLLECTION_NAME = "index-scan-sparse";
    private static final XmldbURI COLLECTION = XmldbURI.create("/db/" + COLLECTION_NAME);
    private static final XmldbURI CONFIG_COLLECTION = XmldbURI.CONFIG_COLLECTION_URI.append("db").append(COLLECTION_NAME);
    /** the eXist document id of the Lucene document without a node id: no document has it */
    private static final int WITHOUT_NODE_ID = 987654321;

    private static final String CONFIGURATION = """
            <collection xmlns="http://exist-db.org/collection-config/1.0">
                <index>
                    <lucene>
                        <text qname="item"/>
                    </lucene>
                </index>
            </collection>""";

    @RegisterExtension
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private LuceneIndex index;

    @AfterEach
    public void cleanup() throws EXistException, PermissionDeniedException, IOException, TriggerException {
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
        if (index != null) {
            final IndexWriter writer = index.getWriter();
            writer.deleteDocuments(IntField.newExactQuery(LuceneUtil.FIELD_DOC_ID, WITHOUT_NODE_ID));
            index.releaseWriter(writer);
        }
    }

    @Test
    public void termsAreScannedWhenTheSegmentHasADocumentWithoutANodeId() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final TransactionManager transactionManager = pool.getTransactionManager();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final LuceneIndexWorker worker = (LuceneIndexWorker) broker.getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            index = worker.index;

            // the terms are chosen so that the hits of the second term start before the last hit of the first:
            // "a" is in items 1 and 2, "b" only in item 1
            try (final Txn transaction = transactionManager.beginTransaction()) {
                final Collection collection = broker.getOrCreateCollection(transaction, COLLECTION);
                broker.saveCollection(transaction, collection);
                pool.getConfigurationManager().addConfiguration(transaction, broker, collection, CONFIGURATION);
                broker.storeDocument(transaction, XmldbURI.create("items.xml"),
                        new StringInputSource("<div><item>a b</item><item>a</item><item>c</item></div>"), MimeType.XML_TYPE, collection);
                transactionManager.commit(transaction);
            }

            // a Lucene document without a node id, in the segment that is written next, beside the three above
            final IndexWriter writer = index.getWriter();
            final Document withoutNodeId = new Document();
            withoutNodeId.add(new IntField(LuceneUtil.FIELD_DOC_ID, WITHOUT_NODE_ID, org.apache.lucene.document.Field.Store.NO));
            writer.addDocument(withoutNodeId);
            index.releaseWriter(writer);

            assertTrue(index.<Boolean>withReader(reader -> reader.leaves().stream().anyMatch(LuceneIndexScanSparseDocValuesTest::hasNodeIdOnlyForSomeDocuments)),
                    "this test only tests the scan if a segment has a node id for some of its documents only");

            final XQuery xquery = pool.getXQueryService();
            final Sequence terms = xquery.execute(broker,
                    "util:index-keys(collection('" + COLLECTION + "')//item, '', function($term, $data) { $term }, 100, 'lucene-index')", null);
            final List<String> found = new ArrayList<>();
            for (final SequenceIterator it = terms.iterate(); it.hasNext(); ) {
                final Item item = it.nextItem();
                found.add(item.getStringValue());
            }
            assertEquals(List.of("a", "b", "c"), found);
        }
    }

    /** @return true if some, but not all, documents of the segment have a node id */
    private static boolean hasNodeIdOnlyForSomeDocuments(final LeafReaderContext leaf) {
        try {
            final BinaryDocValues nodeIds = leaf.reader().getBinaryDocValues(LuceneUtil.FIELD_NODE_ID_DV);
            if (nodeIds == null) {
                return false;
            }
            int withNodeId = 0;
            while (nodeIds.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
                withNodeId++;
            }
            return withNodeId > 0 && withNodeId < leaf.reader().maxDoc();
        } catch (final IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
