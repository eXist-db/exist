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

import org.apache.lucene.index.BinaryDocValues;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.SortedNumericDocValues;
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
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.util.ByteConversion;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The exact answer of a vector query, as an access path: {@link #select} gives the k candidates with the best score,
 * where the score of a candidate is the best similarity among the entries it owns. The ownership rule: a candidate
 * owns its own entry if it has one, otherwise every entry below it, at any depth; a document owns the entries in it.
 * The score is never a sum, so it does not depend on k. It is a top-k, not a filter: which candidates are kept depends
 * on the others.
 *
 * <p>The candidates are the nodes the step selects, so an entry can count for several of them (nested candidates
 * without an entry of their own) and the units that are ranked are the nodes, not the entries.</p>
 *
 * <p>One pass over the entries of the field: an entry is looked up by its document, then by its node and the nodes
 * above it among that document's candidates, and is scored only if some candidate owns it.</p>
 */
final class VectorExactSearch {

    private static final int OWN = 0;
    private static final int BELOW = 1;

    private final NodeIdFactory nodeFactory;
    private final IndexSearcher searcher;
    private final float[] vector;
    private final int k;
    private final List<Probe> probes;
    private final Int2ObjectOpenHashMap<Slot> slots = new Int2ObjectOpenHashMap<>();
    private final List<Owner> owners = new ArrayList<>();
    /** The candidates of the document of the entry being looked at that own it, nearest first: reused for every entry. */
    private int[] found = new int[16];
    private int foundCount;
    /** Whether the first of {@link #found} is at the entry's own node. */
    private boolean ownEntry;

    /** The entry that gave a candidate its score. */
    record Entry(int luceneDoc, NodeId node, int docId, float score) {
    }

    /**
     * Where the entries are: one vector field of a space and the filter that tells which of its entries count (the index
     * type and the call's filters), or null for all.
     *
     * @param field the Lucene field of the space
     * @param filter the entries that count, or null for all
     */
    record Probe(String field, @Nullable Query filter) {
    }

    /** A candidate that owns at least one entry, and the best entry it owns by its own entry and by those below. */
    static final class Owner {
        private final NodeProxy node;
        private final Entry[] best = new Entry[2];

        private Owner(final NodeProxy node) {
            this.node = node;
        }

        /** The entry that gives the candidate its score: its own entry if it has one, else the best below it. */
        Entry entry() {
            return best[OWN] != null ? best[OWN] : best[BELOW];
        }

        NodeProxy node() {
            return node;
        }

        float score() {
            return best[OWN] != null ? best[OWN].score() : best[BELOW].score();
        }

        void credit(final int kind, final float score, final int luceneDoc, final NodeId entryNode, final int docId) {
            final Entry held = best[kind];
            if (held == null || score > held.score()) {
                best[kind] = new Entry(luceneDoc, entryNode, docId, score);
            }
        }
    }

    /** The candidates of one document, sorted by node id, with their owners; the document node apart. */
    private static final class Slot {
        private final NodeProxy[] nodes;
        private final Owner[] owners;
        private final int from;
        private final int to;
        @Nullable private final NodeProxy document;
        @Nullable private Owner documentOwner;

        private Slot(final NodeProxy[] nodes, final Owner[] owners, final int from, final int to, @Nullable final NodeProxy document) {
            this.nodes = nodes;
            this.owners = owners;
            this.from = from;
            this.to = to;
            this.document = document;
        }

        /** @return the index of the candidate with this node id, or -1 */
        int find(final NodeId id) {
            int low = from;
            int high = to - 1;
            while (low <= high) {
                final int mid = (low + high) >>> 1;
                final int order = nodes[mid].getNodeId().compareTo(id);
                if (order < 0) {
                    low = mid + 1;
                } else if (order > 0) {
                    high = mid - 1;
                } else {
                    return mid;
                }
            }
            return -1;
        }
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
     * @param within the nodes the step selects, which are ranked; entries of other nodes and documents are ignored
     * @return the selected candidates, each with the entry that gives it its score
     */
    List<Owner> select(final NodeSet within) throws IOException {
        slots.clear();
        owners.clear();
        index(within);
        for (final Probe probe : probes) {
            collect(probe.field(), probe.filter());
        }
        return best();
    }

    /** Groups the candidates by document, in node order. */
    private void index(final NodeSet within) {
        final NodeProxy[] nodes = inOrder(within);
        final Owner[] ownersOf = new Owner[nodes.length];
        int start = 0;
        while (start < nodes.length) {
            final int docId = nodes[start].getOwnerDocument().getDocId();
            int end = start + 1;
            while (end < nodes.length && nodes[end].getOwnerDocument().getDocId() == docId) {
                end++;
            }
            // the document node, which sorts first, owns what is in the document: it is not found by an entry's node ids
            final boolean hasDocument = nodes[start].getNodeId() == NodeId.DOCUMENT_NODE;
            slots.put(docId, new Slot(nodes, ownersOf, hasDocument ? start + 1 : start, end, hasDocument ? nodes[start] : null));
            start = end;
        }
    }

    /** @return the candidates, by document and then by node id */
    private static NodeProxy[] inOrder(final NodeSet within) {
        final NodeProxy[] all = new NodeProxy[within.getItemCount()];
        int count = 0;
        boolean sorted = true;
        for (final NodeProxy node : within) {
            sorted &= count == 0 || all[count - 1].compareTo(node) <= 0;
            all[count++] = node;
        }
        final NodeProxy[] nodes = count == all.length ? all : Arrays.copyOf(all, count);
        if (!sorted) {
            Arrays.sort(nodes, Comparator.comparingInt((NodeProxy node) -> node.getOwnerDocument().getDocId())
                    .thenComparing(NodeProxy::getNodeId));
        }
        return nodes;
    }

    /**
     * Scores the entries of one vector field that pass a filter, and credits them to the candidates that own them.
     *
     * @param field the Lucene field of the space
     * @param filter the entries that count (index type, the call's filters), or null for all
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
            if ((live != null && !live.get(doc)) || !docIds.advanceExact(doc)) {
                continue;
            }
            final int docId = (int) docIds.nextValue();
            final Slot slot = slots.get(docId);
            if (slot == null || !nodeIds.advanceExact(doc)) {
                continue;
            }
            final BytesRef ref = nodeIds.binaryValue();
            final NodeId node = nodeFactory.createFromData(ByteConversion.byteToShortH(ref.bytes, ref.offset), ref.bytes, ref.offset + 2);
            credit(slot, node, docId, leaf.docBase + doc, scorer);
        }
    }

    /**
     * Finds the candidates of a document that own an entry at this node: the one at the node itself, which owns it as its
     * own entry, and those above it. They are left in {@link #found}, the nearest first.
     */
    private void locate(final Slot slot, final NodeId entryNode) {
        foundCount = 0;
        ownEntry = false;
        boolean atEntry = true;
        for (NodeId id = entryNode; id != null && id != NodeId.DOCUMENT_NODE; id = id.getParentId()) {
            final int i = slot.find(id);
            if (i >= 0) {
                if (foundCount == found.length) {
                    found = Arrays.copyOf(found, foundCount * 2);
                }
                found[foundCount++] = i;
                ownEntry |= atEntry;
            }
            atEntry = false;
        }
    }

    /**
     * Gives the entry to the candidate it is the own entry of, to the candidates above it, and to its document if that
     * is a candidate. The entry is scored only if one of them owns it.
     */
    private void credit(final Slot slot, final NodeId entryNode, final int docId, final int luceneDoc, final VectorScorer scorer)
            throws IOException {
        locate(slot, entryNode);
        if (foundCount == 0 && slot.document == null) {
            return;
        }
        final float score = scorer.score();
        if (Float.isNaN(score)) {
            // it would sort above every real score
            return;
        }
        for (int n = 0; n < foundCount; n++) {
            // the first of them is at the entry's own node, if any is
            ownerAt(slot, found[n]).credit(n == 0 && ownEntry ? OWN : BELOW, score, luceneDoc, entryNode, docId);
        }
        if (slot.document != null) {
            documentOwnerOf(slot).credit(BELOW, score, luceneDoc, entryNode, docId);
        }
    }

    private Owner ownerAt(final Slot slot, final int index) {
        Owner owner = slot.owners[index];
        if (owner == null) {
            owner = new Owner(slot.nodes[index]);
            slot.owners[index] = owner;
            owners.add(owner);
        }
        return owner;
    }

    private Owner documentOwnerOf(final Slot slot) {
        if (slot.documentOwner == null) {
            slot.documentOwner = new Owner(slot.document);
            owners.add(slot.documentOwner);
        }
        return slot.documentOwner;
    }

    private List<Owner> best() {
        // the worst of the kept candidates first, so that it is the one a better candidate replaces
        final Comparator<Owner> worstFirst = Comparator
                .comparingDouble(Owner::score)
                .thenComparing((Owner a, Owner b) -> b.node().compareTo(a.node()));
        final PriorityQueue<Owner> heap = new PriorityQueue<>(k + 1, worstFirst);
        for (final Owner owner : owners) {
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
