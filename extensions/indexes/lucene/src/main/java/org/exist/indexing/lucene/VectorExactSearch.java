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

import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.search.ConjunctionUtils;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.VectorScorer;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.Bits;
import org.apache.lucene.util.BytesRef;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.util.ByteConversion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * The exact answer of a vector query, as an access path: {@link #select} gives the k candidates with the best score,
 * where the score of a candidate is the best similarity among the entries it owns. The ownership rule: a candidate
 * owns its own entry if it has one, otherwise every entry below it, at any depth; a document owns the entries in it.
 * The score is never a sum, so it does not depend on k. It is a top-k, not a filter: which candidates are kept depends
 * on the others.
 *
 * <p>The candidates are the nodes the step selects, so an entry can count for several of them (nested candidates
 * without an entry of their own) and the units that are ranked are the nodes, not the entries.</p>
 */
final class VectorExactSearch {

    private final NodeIdFactory nodeFactory;
    private final IndexSearcher searcher;
    private final float[] vector;
    private final int k;
    private final List<Probe> probes;
    private DocumentSet docs;
    private NodeSet candidates;
    private final Map<Key, Owner> owners = new TreeMap<>(Key.ORDER);

    /** The entry that gave a candidate its score. */
    record Entry(int luceneDoc, NodeId node, int docId, float score) {
    }

    /** A candidate that owns at least one entry, and the best entry it owns by its own entry and by those below. */
    static final class Owner {
        private final NodeProxy node;
        private Entry own;
        private Entry below;

        private Owner(final NodeProxy node) {
            this.node = node;
        }

        /** The entry that gives the candidate its score: its own entry if it has one, else the best below it. */
        Entry entry() {
            return own != null ? own : below;
        }

        NodeProxy node() {
            return node;
        }

        void credit(final Entry entry, final boolean isOwn) {
            if (isOwn) {
                if (own == null || entry.score() > own.score()) {
                    own = entry;
                }
            } else if (below == null || entry.score() > below.score()) {
                below = entry;
            }
        }
    }

    /** A node, by document and node id: node ids have no hash code, so the owners are kept in order, not hashed. */
    private record Key(int docId, NodeId node) {
        private static final Comparator<Key> ORDER = Comparator.comparingInt(Key::docId).thenComparing(Key::node);
    }

    /**
     * Where the entries are: one vector field of a space and the filter that tells which of its entries count (the index
     * type, the call's filters, the candidates).
     *
     * @param field the Lucene field of the space
     * @param filter the entries that count, or null for all
     */
    record Probe(String field, @Nullable Query filter) {
    }

    /**
     * @param vector the query vector
     * @param k how many candidates to select
     * @param probes the vector fields to look in: the entries of all of them are ranked together
     */
    VectorExactSearch(final NodeIdFactory nodeFactory, final IndexSearcher searcher, final float[] vector, final int k,
            final List<Probe> probes) {
        this.nodeFactory = nodeFactory;
        this.searcher = searcher;
        this.vector = vector;
        this.k = k;
        this.probes = probes;
    }

    /**
     * Selects the k candidates with the best score, best first; a tie is broken by document order.
     *
     * @param docs the documents the entries are in
     * @param within the nodes the step selects, which are ranked
     * @return the selected candidates, each with the entry that gives it its score
     */
    List<Owner> select(final DocumentSet docs, final NodeSet within) throws IOException {
        this.docs = docs;
        this.candidates = within;
        owners.clear();
        for (final Probe probe : probes) {
            collect(probe.field(), probe.filter());
        }
        return best();
    }

    /**
     * Scores the entries of one vector field that pass a filter, and credits them to the candidates that own them.
     *
     * @param field the Lucene field of the space
     * @param filter the entries that count (index type, the call's filters, the candidates), or null for all
     */
    private void collect(final String field, @Nullable final Query filter) throws IOException {
        final Weight filterWeight = filter == null ? null
                : searcher.createWeight(searcher.rewrite(filter), ScoreMode.COMPLETE_NO_SCORES, 1f);
        for (final LeafReaderContext leaf : searcher.getIndexReader().leaves()) {
            final FloatVectorValues values = leaf.reader().getFloatVectorValues(field);
            final VectorScorer scorer = values == null ? null : values.scorer(vector);
            if (scorer == null) {
                continue;
            }
            DocIdSetIterator entries = scorer.iterator();
            if (filterWeight != null) {
                final Scorer filtered = filterWeight.scorer(leaf);
                if (filtered == null) {
                    continue;
                }
                entries = ConjunctionUtils.intersectIterators(List.of(filtered.iterator(), entries));
            }
            collectLeaf(leaf, scorer, entries);
        }
    }

    private void collectLeaf(final LeafReaderContext leaf, final VectorScorer scorer, final DocIdSetIterator entries)
            throws IOException {
        final Bits live = leaf.reader().getLiveDocs();
        final SortedNumericDocValues docIds = DocValues.getSortedNumeric(leaf.reader(), LuceneFieldNames.FIELD_DOC_ID);
        final BinaryDocValues nodeIds = DocValues.getBinary(leaf.reader(), LuceneFieldNames.FIELD_NODE_ID_DV);
        for (int doc = entries.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = entries.nextDoc()) {
            if ((live != null && !live.get(doc)) || !docIds.advanceExact(doc) || !nodeIds.advanceExact(doc)) {
                continue;
            }
            final int docId = (int) docIds.nextValue();
            final DocumentImpl document = docs.getDoc(docId);
            if (document == null) {
                continue;
            }
            final BytesRef ref = nodeIds.binaryValue();
            final NodeId node = nodeFactory.createFromData(ByteConversion.byteToShortH(ref.bytes, ref.offset), ref.bytes, ref.offset + 2);
            credit(document, new Entry(leaf.docBase + doc, node, docId, scorer.score()));
        }
    }

    /** Gives the entry to the candidate it is the own entry of, and to the candidates above it. */
    private void credit(final DocumentImpl document, final Entry entry) {
        for (NodeId id = entry.node(); id != null; id = id.getParentId()) {
            final NodeProxy candidate = candidates.get(document, id);
            if (candidate != null) {
                owners.computeIfAbsent(new Key(entry.docId(), id), key -> new Owner(candidate))
                        .credit(entry, id.equals(entry.node()));
            }
        }
    }

    private List<Owner> best() {
        // the worst of the kept candidates first, so that it is the one a better candidate replaces
        final Comparator<Owner> worstFirst = Comparator
                .comparingDouble((Owner owner) -> owner.entry().score())
                .thenComparing((Owner a, Owner b) -> b.node().compareTo(a.node()));
        final PriorityQueue<Owner> heap = new PriorityQueue<>(k + 1, worstFirst);
        for (final Owner owner : owners.values()) {
            heap.add(owner);
            if (heap.size() > k) {
                heap.poll();
            }
        }
        final List<Owner> best = new ArrayList<>(heap);
        best.sort(worstFirst.reversed());
        return best;
    }
}
