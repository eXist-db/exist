/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.value;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.collections.Collection;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.persistent.*;
import org.exist.numbering.NodeId;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

import javax.annotation.Nullable;
import java.util.*;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;

/**
 * An immutable sequence that wraps an existing
 * sequence, and provides access to a subset
 * of the wrapped sequence, i.e. a sub-sequence.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class SubSequence extends AbstractSequence {
    private static final Logger LOG = LogManager.getLogger(SubSequence.class);

    private final long fromInclusive;
    private final long toExclusive;
    private final Sequence sequence;

    /**
     * @param fromInclusive The starting position in the {@code sequence} for the sub-sequence,
     *     should be 1 for the first item in the {@code sequence}. This can be out-of-bounds
     *     for the {@code sequence}.
     * @param sequence The underlying sequence, for which we will provide a sub-sequence.
     */
    public SubSequence(final long fromInclusive, final Sequence sequence) {
        this(fromInclusive, Long.MAX_VALUE, sequence);
    }

    /**
     * @param fromInclusive The starting position in the {@code sequence} for the sub-sequence,
     *     should be 1 for the first item in the {@code sequence}. This can be out-of-bounds
     *     for the {@code sequence}.
     * @param toExclusive The End of sequence position for the sub-sequence. If you want everything
     *     from the sequence, then this is the {@link Sequence#getItemCountLong()} + 1.
     *     Specifying an ending position past the end of the sequence is allowed.
     *     If you don't know the length of the sequence, then {@link Long#MAX_VALUE} can be used.
     * @param sequence The underlying sequence, for which we will provide a sub-sequence.
     */
    public SubSequence(final long fromInclusive, final long toExclusive, final Sequence sequence) {
        this.fromInclusive = fromInclusive <= 0 ? 1 : fromInclusive;
        this.toExclusive = toExclusive;
        this.sequence = sequence;
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new XPathException((Expression) null, "Cannot add an item to a sub-sequence");
    }

    @Override
    public int getItemType() {
        return sequence.getItemType();
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        if (isEmpty()) {
            return SequenceIterator.EMPTY_ITERATOR;
        }

        return new SubSequenceIterator(fromInclusive, toExclusive, sequence);
    }

    @Override
    public SequenceIterator unorderedIterator() throws XPathException {
        return iterate();
    }

    @Override
    public long getItemCountLong() {
        if (toExclusive < 1) {
            return 0;
        }

        long subseqAvailable = sequence.getItemCountLong() - (fromInclusive - 1);
        if (subseqAvailable < 0) {
            subseqAvailable = 0;
        }

        long length = toExclusive - fromInclusive;
        if (length < 0) {
            length = 0;
        }

        return Math.min(length, subseqAvailable);
    }

    @Override
    public boolean isEmpty() {
        final long length = toExclusive - fromInclusive;
        return length < 1 || sequence.isEmpty() || sequence.getItemCountLong() - fromInclusive < 0;
    }

    @Override
    public boolean hasOne() {
        final long subseqAvailable = sequence.getItemCountLong() - (fromInclusive - 1);
        final long length = toExclusive - fromInclusive;
        return subseqAvailable > 0 && length == 1;
    }

    @Override
    public boolean hasMany() {
        final long subseqAvailable = sequence.getItemCountLong() - (fromInclusive - 1);
        final long length = toExclusive - fromInclusive;
        return subseqAvailable > 1 && length > 1;
    }

    @Override
    public void removeDuplicates() {
    }

    @Override
    public Cardinality getCardinality() {
        final long length = toExclusive - fromInclusive;
        if (length < 1 || sequence.isEmpty()) {
           return Cardinality.EMPTY_SEQUENCE;
        }

        final long subseqAvailable = sequence.getItemCountLong() - (fromInclusive - 1);
        if (subseqAvailable < 1) {
            return Cardinality.EMPTY_SEQUENCE;
        }

        if (subseqAvailable > 0 && length == 1) {
            return Cardinality.EXACTLY_ONE;
        }

        if (subseqAvailable > 1 && length > 1) {
            return Cardinality._MANY;
        }

        throw new IllegalStateException("Unknown Cardinality of: " + this);
    }

    @Override
    public Item itemAt(final int pos) {
        // NOTE: remember that itemAt(pos) is zero based index addressing!
        final long length = toExclusive - fromInclusive;
        if (pos < 0 || pos >= length) {
            return null;
        }

        final long subseqAvailable = sequence.getItemCountLong() - (fromInclusive - 1);
        if (pos >= subseqAvailable) {
            return null;
        }

        return sequence.itemAt((int) fromInclusive - 1 + pos);
    }

    @Override
    public Sequence tail() {
        if (isEmpty() || hasOne()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        return new SubSequence(fromInclusive + 1, toExclusive, sequence);
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        if (isEmpty()) {
            return NodeSet.EMPTY_SET;
        }

        final Map<DocumentImpl, Tuple2<DocumentImpl, org.exist.dom.persistent.DocumentImpl>> expandedDocs = new HashMap<>();
        final NodeSet nodeSet = new NewArrayNodeSet();
        final SequenceIterator iterator = iterate();
        while (iterator.hasNext()) {
            final Item item = iterator.nextItem();

            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException((Expression) null, "Type error: the sub-sequence cannot be converted into" +
                        " a node set. It contains an item of type: " + Type.getTypeName(item.getType()));
            }

            final NodeValue v = (NodeValue) item;
            if (v.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                final NodeProxy p = makePersistent((NodeImpl)v, expandedDocs);
                if (p == null) {
                    throw new XPathException((Expression) null, "Type error: the sub-sequence cannot be converted into" +
                            " a node set. It contains an in-memory node which cannot be persisted.");
                } else {
                    nodeSet.add(p);
                }
            } else {
                nodeSet.add((NodeProxy) v);
            }
        }

        return nodeSet;
    }

    private @Nullable NodeProxy makePersistent(NodeImpl node, final Map<DocumentImpl, Tuple2<DocumentImpl, org.exist.dom.persistent.DocumentImpl>> expandedDocs) throws XPathException {
        // found an in-memory document
        final DocumentImpl doc;
        if (node.getType() == Type.DOCUMENT) {
            doc = (DocumentImpl) node;
        } else {
            doc = node.getOwnerDocument();
        }

        if (doc == null) {
            return null;
        }

        final DocumentImpl expandedDoc;
        final org.exist.dom.persistent.DocumentImpl newDoc;
        if(expandedDocs.containsKey(doc)) {
            final Tuple2<DocumentImpl, org.exist.dom.persistent.DocumentImpl> expandedDocNewDoc = expandedDocs.get(doc);
            expandedDoc = expandedDocNewDoc._1;
            newDoc = expandedDocNewDoc._2;
        } else {
            // make this document persistent: doc.makePersistent()
            // returns a map of all root node ids mapped to the corresponding
            // persistent node. We scan the current sequence and replace all
            // in-memory nodes with their new persistent node objects.
            expandedDoc = doc.expandRefs(null);
            newDoc = expandedDoc.makePersistent();
            expandedDocs.put(doc, Tuple(expandedDoc, newDoc));
        }


        if (newDoc != null) {
            final NodeId rootId = newDoc.getBrokerPool().getNodeFactory().createInstance();
            if (node.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                final Document nodeOwnerDoc;
                if (node.getNodeType() == Node.DOCUMENT_NODE) {
                    nodeOwnerDoc = (Document) node;
                } else {
                    nodeOwnerDoc = node.getOwnerDocument();
                }

                if (nodeOwnerDoc == doc) {
                    if (node.getNodeType() == Node.ATTRIBUTE_NODE) {
                        node = expandedDoc.getAttribute(node.getNodeNumber());
                    } else {
                        node = expandedDoc.getNode(node.getNodeNumber());
                    }
                    NodeId nodeId = node.getNodeId();
                    if (nodeId == null) {
                        throw new XPathException((Expression) null, "Internal error: nodeId == null");
                    }
                    if (node.getNodeType() == Node.DOCUMENT_NODE) {
                        nodeId = rootId;
                    } else {
                        nodeId = rootId.append(nodeId);
                    }
                    final NodeProxy p = new NodeProxy(node.getExpression(), newDoc, nodeId, node.getNodeType());
                    // replace the node by the NodeProxy
                    return p;
                }
            }
        }

        return null;
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        if (isEmpty()) {
            return MemoryNodeSet.EMPTY;
        }

        final ValueSequence memNodeSet = new ValueSequence(getItemCount());
        final Set<DocumentImpl> expandedDocs = new HashSet<>();
        final SequenceIterator iterator = iterate();
        while (iterator.hasNext()) {
            final Item item = iterator.nextItem();
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                throw new XPathException((Expression) null, "Type error: the sub-sequence cannot be converted into" +
                        " a MemoryNodeSet. It contains items which are not nodes");
            }

            final NodeValue v = (NodeValue) item;
            if (v.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                throw new XPathException((Expression) null, "Type error: the sub-sequence cannot be converted into" +
                        " a MemoryNodeSet. It contains nodes from stored resources.");
            }

            final org.exist.dom.memtree.NodeImpl node = (org.exist.dom.memtree.NodeImpl)item;
            final DocumentImpl ownerDoc = node.getNodeType() == Node.DOCUMENT_NODE ? (DocumentImpl) node : node.getOwnerDocument();

            if (ownerDoc.hasReferenceNodes()  && !expandedDocs.contains(ownerDoc)) {
                ownerDoc.expand();
                expandedDocs.add(ownerDoc);
            }

            memNodeSet.add(node);
        }
        return memNodeSet;
    }

    @Override
    public DocumentSet getDocumentSet() {
        try {
            final MutableDocumentSet docs = new DefaultDocumentSet();
            final SequenceIterator iterator = iterate();
            while (iterator.hasNext()) {
                final Item item = iterator.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue node = (NodeValue) item;
                    if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        docs.add((org.exist.dom.persistent.DocumentImpl) node.getOwnerDocument());
                    }
                }
            }
            return docs;
        } catch (final XPathException e) {
            LOG.error(e);
            return DocumentSet.EMPTY_DOCUMENT_SET;
        }
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        try {
            return new CollectionIterator(iterate());
        } catch (final XPathException e) {
            LOG.error(e);
            return super.getCollectionIterator();
        }
    }

    @Override
    public boolean isPersistentSet() {
        final SequenceIterator iterator;
        try {
             iterator = iterate();
        } catch (final XPathException e) {
            throw new RuntimeException(e); // should never happen!
        }

        // needed to guard against returning true for an empty-sequence below
        if (!iterator.hasNext()) {
            return false;
        }

        while (iterator.hasNext()) {
            final Item item = iterator.nextItem();
            if (!(item instanceof NodeValue nv)) {
                return false;
            }
            if (nv.getImplementationType() != NodeValue.PERSISTENT_NODE) {
                return false;
            }
        }
        // else, all items were persistent
        return true;
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        return sequence.conversionPreference(javaClass);
    }

    @Override
    public boolean isCacheable() {
        return sequence.isCacheable();
    }

    @Override
    public int getState() {
        return sequence.getState();
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return sequence.hasChanged(previousState);
    }

    @Override
    public boolean isCached() {
        return sequence.isCached();
    }

    @Override
    public void setIsCached(final boolean cached) {
        sequence.setIsCached(cached);
    }

    @Override
    public void setSelfAsContext(final int contextId) throws XPathException {
        sequence.setSelfAsContext(contextId);
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        sequence.clearContext(contextId);
    }

    @Override
    public boolean containsReference(final Item item) {
        try {
            for (final SequenceIterator it = iterate(); it.hasNext(); ) {
                final Item i = it.nextItem();
                if (i == item) {
                    return true;
                }
            }
            return false;
        } catch (final XPathException e) {
            LOG.warn(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public boolean contains(final Item item) {
        try {
            for (final SequenceIterator it = iterate(); it.hasNext(); ) {
                final Item i = it.nextItem();
                if (i.equals(item)) {
                    return true;
                }
            }
            return false;
        } catch (final XPathException e) {
            LOG.warn(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public void destroy(final XQueryContext context, @Nullable final Sequence contextSequence) {
        sequence.destroy(context, contextSequence);
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("SubSequence(")
        .append("fi=").append(fromInclusive)
        .append(", ")
        .append("te=").append(toExclusive)
        .append(", ")
        .append(sequence.toString())
        .append(')');
        return builder.toString();
    }

    private static class CollectionIterator implements Iterator<Collection> {
        private final SequenceIterator iterator;
        private Collection nextCollection = null;

        CollectionIterator(final SequenceIterator iterator) {
            this.iterator = iterator;
            next();
        }

        @Override
        public boolean hasNext() {
            return nextCollection != null;
        }

        @Override
        public Collection next() {
            final Collection oldCollection = nextCollection;
            nextCollection = null;
            while (iterator.hasNext()) {
                final Item item = iterator.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue node = (NodeValue) item;
                    if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                        final NodeProxy p = (NodeProxy) node;
                        if (!p.getOwnerDocument().getCollection().equals(oldCollection)) {
                            nextCollection = p.getOwnerDocument().getCollection();
                            break;
                        }
                    }
                }
            }
            return oldCollection;
        }
    }

    private static class SubSequenceIterator implements SequenceIterator {
        private long position;
        private final long toExclusive;
        private final SequenceIterator iterator;

        public SubSequenceIterator(final long fromInclusive, final long toExclusive, final Sequence sequence) throws XPathException {
            this.position = 1;
            this.toExclusive = toExclusive;
            this.iterator = sequence.iterate();

            // move sequence iterator to start of sub-sequence
            if (position != fromInclusive) {
                // move to start
                if (iterator.skip(fromInclusive - position) > -1) {
                    position = fromInclusive;
                } else {
                    // SequenceIterator does not support skipping, we have to iterate through each item :-/
                    for (; position < fromInclusive; position++) {
                        iterator.nextItem();
                    }
                }
            }
        }

        @Override
        public boolean hasNext() {
            return iterator.hasNext() && position < toExclusive;
        }

        @Override
        public Item nextItem() {
            if (iterator.hasNext() && position < toExclusive) {
                final Item item = iterator.nextItem();
                position++;
                return item;
            }

            return null;
        }

        @Override
        public long skippable() {
            return Math.min(iterator.skippable(), toExclusive - position);
        }

        @Override
        public long skip(final long n) {
            final long seqSkipable = iterator.skippable();
            if (seqSkipable == -1) {
                return -1; // underlying iterator does not support skipping
            }

            final long skip = Math.min(n, Math.min(seqSkipable, toExclusive - position));
            if (skip <= 0) {
                return 0;  // nothing to skip
            }

            final long skipped = iterator.skip(skip);
            position += skipped;
            return skipped;
        }
    }
}
