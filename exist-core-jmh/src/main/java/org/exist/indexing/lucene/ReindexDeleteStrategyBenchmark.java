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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.IntField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.FieldExistsQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks reindex-time Lucene delete strategies for mixed document shapes:
 * node-backed entries and named-field entries sharing the same docId.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
public class ReindexDeleteStrategyBenchmark {

    private static final String FIELD_DOC_ID = "docId";
    private static final String FIELD_INDEXED = "indexed";
    private static final int DELETE_BATCH_SIZE = 128;
    private static final Query NODE_EXISTS_QUERY = new FieldExistsQuery(LuceneUtil.FIELD_NODE_ID_DV);

    @State(Scope.Thread)
    public static class BenchmarkState {
        @Param({"1000", "5000"})
        public int docCount;
    }

    @Benchmark
    public int deletePerDoc(final BenchmarkState state) throws IOException {
        try (Directory directory = buildIndex(state.docCount);
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (int docId = 1; docId <= state.docCount; docId++) {
                final Query docIdQuery = IntField.newExactQuery(FIELD_DOC_ID, docId);
                writer.deleteDocuments(nodeScopedDeleteQuery(docIdQuery));
            }
            writer.commit();
            return remainingNamedFieldDocs(directory);
        }
    }

    @Benchmark
    public int deleteInBatches(final BenchmarkState state) throws IOException {
        try (Directory directory = buildIndex(state.docCount);
             IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {

            int nextDocId = 1;
            while (nextDocId <= state.docCount) {
                final BooleanQuery.Builder batchedDocIdQuery = new BooleanQuery.Builder();
                int clauses = 0;
                final int maxClausesPerBatch = Math.min(DELETE_BATCH_SIZE, IndexSearcher.getMaxClauseCount());
                while (nextDocId <= state.docCount && clauses < maxClausesPerBatch) {
                    batchedDocIdQuery.add(IntField.newExactQuery(FIELD_DOC_ID, nextDocId++), BooleanClause.Occur.SHOULD);
                    clauses++;
                }
                writer.deleteDocuments(nodeScopedDeleteQuery(batchedDocIdQuery.build()));
            }
            writer.commit();
            return remainingNamedFieldDocs(directory);
        }
    }

    private static Query nodeScopedDeleteQuery(final Query docIdQuery) {
        return new BooleanQuery.Builder()
                .add(docIdQuery, BooleanClause.Occur.MUST)
                .add(NODE_EXISTS_QUERY, BooleanClause.Occur.MUST)
                .build();
    }

    private static Directory buildIndex(final int docCount) throws IOException {
        final Directory directory = new ByteBuffersDirectory();
        try (IndexWriter writer = new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            for (int docId = 1; docId <= docCount; docId++) {
                writer.addDocument(nodeDocument(docId));
                writer.addDocument(namedFieldDocument(docId));
            }
            writer.commit();
        }
        return directory;
    }

    private static Document nodeDocument(final int docId) {
        final Document doc = new Document();
        doc.add(new IntField(FIELD_DOC_ID, docId, Field.Store.NO));
        doc.add(new StringField(FIELD_INDEXED, "node", Field.Store.NO));
        doc.add(new SortedDocValuesField(LuceneUtil.FIELD_NODE_ID_DV, new BytesRef("n-" + docId)));
        return doc;
    }

    private static Document namedFieldDocument(final int docId) {
        final Document doc = new Document();
        doc.add(new IntField(FIELD_DOC_ID, docId, Field.Store.NO));
        doc.add(new StringField(FIELD_INDEXED, "named", Field.Store.NO));
        doc.add(new StoredField("foo-field", "Foobar index data"));
        return doc;
    }

    private static int remainingNamedFieldDocs(final Directory directory) throws IOException {
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            final IndexSearcher searcher = new IndexSearcher(reader);
            return searcher.count(new TermQuery(new Term(FIELD_INDEXED, "named")));
        }
    }
}
