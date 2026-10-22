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
import org.apache.lucene.search.ConstantScoreScorer;
import org.apache.lucene.search.ConstantScoreWeight;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.ScorerSupplier;
import org.apache.lucene.search.TwoPhaseIterator;
import org.apache.lucene.search.Weight;
import org.apache.lucene.util.BytesRef;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.numbering.NodeId;
import org.exist.numbering.NodeIdFactory;
import org.exist.util.ByteConversion;

import java.io.IOException;

/**
 * Matches the Lucene documents of a vector field whose node is in an XQuery context, or below a node of it.
 * <p>
 * A vector query looks for its k nearest among the documents its filter matches, so with this query in the filter it
 * finds the k nearest of the context's nodes: in {@code $articles[ft:query-vector(., $v, 5)]} the 5 nearest of
 * {@code $articles}, rather than those of the 5 nearest of all that happen to be in {@code $articles}.
 * <p>
 * It is evaluated against a node set of one query, so it is neither cached nor equal to another instance.
 */
final class InContextQuery extends Query {
    /** how costly matching a document is, in comparisons: reading two doc values and a node set lookup */
    private static final float MATCH_COST = 100;

    private final String vectorField;
    private final DocumentSet docs;
    private final NodeSet contextSet;
    private final NodeIdFactory nodeFactory;

    InContextQuery(final String vectorField, final DocumentSet docs, final NodeSet contextSet, final NodeIdFactory nodeFactory) {
        this.vectorField = vectorField;
        this.docs = docs;
        this.contextSet = contextSet;
        this.nodeFactory = nodeFactory;
    }

    @Override
    public Weight createWeight(final IndexSearcher searcher, final ScoreMode scoreMode, final float boost) {
        return new ConstantScoreWeight(this, boost) {
            @Override
            public ScorerSupplier scorerSupplier(final LeafReaderContext leaf) throws IOException {
                final FloatVectorValues vectors = leaf.reader().getFloatVectorValues(vectorField);
                if (vectors == null) {
                    return null;
                }
                // only the documents with a vector of the field can match
                final DocIdSetIterator approximation = vectors.iterator();
                final SortedNumericDocValues docIds = DocValues.getSortedNumeric(leaf.reader(), LuceneIndexWorker.FIELD_DOC_ID);
                final BinaryDocValues nodeIds = DocValues.getBinary(leaf.reader(), LuceneUtil.FIELD_NODE_ID_DV);
                final TwoPhaseIterator inContext = new TwoPhaseIterator(approximation) {
                    @Override
                    public boolean matches() throws IOException {
                        final int doc = approximation.docID();
                        if (!docIds.advanceExact(doc) || !nodeIds.advanceExact(doc)) {
                            return false;
                        }
                        final DocumentImpl document = docs.getDoc((int) docIds.nextValue());
                        if (document == null) {
                            return false;
                        }
                        final BytesRef ref = nodeIds.binaryValue();
                        final NodeId nodeId = nodeFactory.createFromData(ByteConversion.byteToShortH(ref.bytes, ref.offset), ref.bytes, ref.offset + 2);
                        return contextSet.parentWithChild(new NodeProxy(null, document, nodeId), false, true, NodeProxy.UNKNOWN_NODE_LEVEL) != null;
                    }

                    @Override
                    public float matchCost() {
                        return MATCH_COST;
                    }
                };
                return new DefaultScorerSupplier(new ConstantScoreScorer(score(), scoreMode, inContext));
            }

            @Override
            public boolean isCacheable(final LeafReaderContext leaf) {
                return false;
            }
        };
    }

    @Override
    public void visit(final QueryVisitor visitor) {
        if (visitor.acceptField(vectorField)) {
            visitor.visitLeaf(this);
        }
    }

    @Override
    public String toString(final String field) {
        return "InContext(" + vectorField + ")";
    }

    @Override
    public boolean equals(final Object other) {
        return this == other;
    }

    @Override
    public int hashCode() {
        return System.identityHashCode(this);
    }
}
