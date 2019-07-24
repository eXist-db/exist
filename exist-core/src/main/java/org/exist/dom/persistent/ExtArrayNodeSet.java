/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2014 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.dom.persistent;

import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
import org.exist.util.ArrayUtils;
import org.exist.util.FastQSort;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import java.util.*;

/**
 * A fast node set implementation, based on arrays to store nodes and documents.
 *
 * The class uses an array to store all nodes belonging to one document. Another sorted
 * array is used to keep track of the document ids. For each document, we maintain an inner
 * class, Part, which stores the array of nodes.
 *
 * Nodes are just appended to the nodes array. No order is guaranteed and calls to
 * get/contains may fail although a node is present in the array (get/contains
 * do a binary search and thus assume that the set is sorted). Also, duplicates
 * are allowed. If you have to ensure that calls to get/contains return valid
 * results at any time and no duplicates occur, use class
 * {@link org.exist.dom.persistent.AVLTreeNodeSet}.
 *
 * Use this class, if you can either ensure that items are added in order, or
 * no calls to contains/get are required during the creation phase. Only after
 * a call to one of the iterator methods, the set will get sorted and
 * duplicates removed.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 * @since 0.9.3
 */
public class ExtArrayNodeSet extends AbstractArrayNodeSet implements DocumentSet {

    private static final int DEFAULT_INITIAL_SIZE = 128;

    private final int initialSize;

    private int documentIds[];
    protected int lastDoc = -1;

    private Part parts[];
    private int partCount = 0;
    protected Part lastPart = null;

    private boolean keepUnOrdered = false;


    public ExtArrayNodeSet() {
        this.initialSize = DEFAULT_INITIAL_SIZE;
        this.documentIds = new int[INITIAL_SIZE];
        this.parts = new Part[INITIAL_SIZE];
        Arrays.fill(documentIds, 0);
    }

    public ExtArrayNodeSet(final int initialDocsCount, final int initialArraySize) {
        this.initialSize = initialArraySize;
        this.documentIds = new int[initialDocsCount > 0 ? initialDocsCount : 1];
        this.parts = new Part[initialDocsCount > 0 ? initialDocsCount : 1];
        Arrays.fill(documentIds, 0);
    }

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     * @param initialArraySize an <code>int</code> value
     */
    public ExtArrayNodeSet(final int initialArraySize) {
        this.initialSize = initialArraySize;
        this.documentIds = new int[INITIAL_SIZE];
        this.parts = new Part[INITIAL_SIZE];
        Arrays.fill(documentIds, 0);
    }

    public void keepUnOrdered(final boolean flag) {
        keepUnOrdered = flag;
    }

    /**
     * The method <code>getPart</code>
     *
     * @param doc      a <code>DocumentImpl</code> value
     * @param create   a <code>boolean</code> value
     * @param sizeHint an <code>int</code> value
     * @return a <code>Part</code> value
     */
    private Part getPart(final DocumentImpl doc, final boolean create, final int sizeHint) {
        if(lastPart != null && doc.getDocId() == lastDoc) {
            return lastPart;
        }
        int idx = ArrayUtils.binarySearch(documentIds, doc.getDocId(), partCount);
        Part part = null;
        if(idx >= 0) {
            part = parts[idx];
        } else if(create) {
            idx = -(idx + 1);
            part = new Part(sizeHint);
            insertPart(doc.getDocId(), part, idx);
        }
        return part;
    }

    private void insertPart(final int docId, final Part part, final int idx) {
        if(partCount == parts.length) {
            final int nsize = parts.length == 0 ? 1 : parts.length * 2;
            int ndocs[] = new int[nsize];
            System.arraycopy(documentIds, 0, ndocs, 0, documentIds.length);
            Arrays.fill(documentIds, -1);
            final Part nparts[] = new Part[nsize];
            System.arraycopy(parts, 0, nparts, 0, parts.length);
            documentIds = ndocs;
            parts = nparts;
        }

        if(idx == partCount) {
            // insert at the end
            documentIds[idx] = docId;
            parts[idx] = part;
        } else {
            // insert at idx
            System.arraycopy(documentIds, idx, documentIds, idx + 1, partCount - idx);
            System.arraycopy(parts, idx, parts, idx + 1, partCount - idx);
            documentIds[idx] = docId;
            parts[idx] = part;
        }

        ++partCount;
    }

    @Override
    public void reset() {
        for(int i = 0; i < partCount; i++) {
            parts[i] = null;
            documentIds[i] = 0;
        }
        size = 0;
        partCount = 0;
        isSorted = false;
        lastPart = null;
        lastDoc = -1;
        state = 0;
    }

    @Override
    protected final void addInternal(final NodeProxy proxy, final int sizeHint) {
        getPart(proxy.getOwnerDocument(), true,
            sizeHint != Constants.NO_SIZE_HINT ? sizeHint : initialSize).add(proxy);
        ++size;
    }

    @Override
    public int getSizeHint(final DocumentImpl doc) {
        final Part part = getPart(doc, false, 0);
        return part == null ? Constants.NO_SIZE_HINT : part.length;
    }

    @Override
    public NodeSetIterator iterator() {
        if(!isSorted()) {
            sort();
        }
        return new ExtArrayIterator();
    }

    @Override
    public SequenceIterator iterate() {
        sortInDocumentOrder();
        return new ExtArrayIterator();
    }

    @Override
    public SequenceIterator unorderedIterator() {
        if(!isSorted()) {
            sort();
        }
        return new ExtArrayIterator();
    }

    @Override
    public boolean contains(final NodeProxy proxy) {
        final Part part = getPart(proxy.getOwnerDocument(), false, 0);
        return part == null ? false : part.contains(proxy.getNodeId());
    }

    @Override
    public NodeProxy get(final int pos) {
        int count = 0;
        for (int i = 0; i < partCount; i++) {
            final Part part = parts[i];
            if(count + part.length > pos) {
                return part.get(pos - count);
            }
            count += part.length;
        }
        return null;
    }

    @Override
    public NodeProxy get(final NodeProxy p) {
        final Part part = getPart(p.getOwnerDocument(), false, 0);
        return part == null ? null : part.get(p.getNodeId());
    }

    @Override
    public NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        sort();
        final Part part = getPart(doc, false, 0);
        return part == null ? null : part.get(nodeId);
    }

    @Override
    protected final NodeSet getDescendantsInSet(final NodeSet al, final boolean childOnly,
            final boolean includeSelf, final int mode, final int contextId, final boolean copyMatches) {
        sort();
        final NodeSet result = new ExtArrayNodeSet();
        Part part;
        for(final NodeProxy node : al) {
            part = getPart(node.getOwnerDocument(), false, 0);
            if(part != null) {
                part.getDescendantsInSet(result, node, childOnly, includeSelf, mode, contextId, copyMatches);
            }
        }
        return result;
    }

    /**
     * The method <code>hasDescendantsInSet</code>
     *
     * @param doc         a <code>DocumentImpl</code> value
     * @param ancestorId  a <code>NodeId</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param contextId   an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy hasDescendantsInSet(final DocumentImpl doc, final NodeId ancestorId,
            final boolean includeSelf, final int contextId) {
        sort();
        final Part part = getPart(doc, false, 0);
        return part == null ? null : part.hasDescendantsInSet(ancestorId, contextId, includeSelf);
    }

    /**
     * The method <code>sort</code>
     *
     * @param mergeContexts a <code>boolean</code> value
     */
    @Override
    public void sort(final boolean mergeContexts) {
        if(isSorted || keepUnOrdered) {
            return;
        } else if(hasOne) {
            isSorted = true; // shortcut: don't sort if there's just one item
            size = parts[0].removeDuplicates(mergeContexts);
            return;
        } else {
            size = 0;
            for (int i = 0; i < partCount; i++) {
                final Part part = parts[i];
                part.sort();
                size += part.removeDuplicates(mergeContexts);
            }
            isSorted = true;
        }
    }

    @Override
    public void setSelfAsContext(final int contextId) throws XPathException {
        for(int i = 0; i < partCount; i++) {
            parts[i].setSelfAsContext(contextId);
        }
    }

    @Override
    public NodeSet selectPrecedingSiblings(final NodeSet siblings, final int contextId) {
        sort();
        return super.selectPrecedingSiblings(siblings, contextId);
    }

    @Override
    public NodeSet selectFollowingSiblings(final NodeSet siblings, final int contextId) {
        sort();
        return super.selectFollowingSiblings(siblings, contextId);
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl doc, final NodeId nodeId,
            final boolean directParent, final boolean includeSelf) {
        sort();
        lastPart = getPart(doc, false, initialSize);
        return lastPart == null ? null : lastPart.parentWithChild(doc, nodeId,
            directParent, includeSelf);
    }

    /**
     * The method <code>debugParts</code>
     *
     * @return a <code>String</code> value
     */
    public String debugParts() {
        final StringBuilder buf = new StringBuilder();
        for(int i = 0; i < partCount; i++) {
            buf.append(documentIds[i]);
            buf.append(' ');
        }
        return buf.toString();
    }

    @Override
    public int getIndexType() {
        //Is the index type initialized ?
        if(indexType == Type.ANY_TYPE) {
            for(int i = 0; i < partCount; i++) {
                parts[i].determineIndexType();
            }
        }
        return indexType;
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        if(this == other) {
            return true;
        } else if(partCount != other.getDocumentCount()) {
            return false;
        } else {
            for (int i = 0; i < partCount; i++) {
                if(!other.contains(parts[i].getOwnerDocument().getDocId())) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return new CollectionIterator();
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return new DocumentIterator();
    }

    @Override
    public int getDocumentCount() {
        return partCount;
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        final int idx = ArrayUtils.binarySearch(documentIds, docId, partCount);
        if(idx > -1) {
            return parts[idx].getOwnerDocument();
        }
        return null;
    }

    @Override
    public XmldbURI[] getNames() {
        final XmldbURI[] uris = new XmldbURI[partCount];
        for (int i = 0; i < partCount; i++) {
            uris[i] = parts[i].getOwnerDocument().getURI();
        }
        return uris;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        final DefaultDocumentSet set = new DefaultDocumentSet();

        //left
        for (int i = 0; i < partCount; i++) {
            final DocumentImpl doc = parts[i].getOwnerDocument();
            if(other.contains(doc.getDocId())) {
                set.add(doc);
            }
        }

        //right
        for(final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            if(contains(doc.getDocId()) && (!set.contains(doc.getDocId()))) {
                set.add(doc);
            }
        }

        return set;
    }

    @Override
    public boolean contains(final DocumentSet other) {
        if(other.getDocumentCount() > partCount) {
            return false;
        }

        for(final Iterator<DocumentImpl> i = other.getDocumentIterator(); i.hasNext(); ) {
            final DocumentImpl doc = i.next();
            if(!contains(doc.getDocId())) {
                return false;
            }
        }

        return true;
    }

    @Override
    public boolean contains(final int docId) {
        return ArrayUtils.binarySearch(documentIds, docId, partCount) > -1;
    }

    @Override
    public NodeSet docsToNodeSet() {
        final NodeSet result = new ExtArrayNodeSet(partCount);
        for (int i = 0; i < partCount; i++) {
            final DocumentImpl doc = parts[i].getOwnerDocument();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) { // skip binary resources
                result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    @Override
    public ManagedLocks<ManagedDocumentLock> lock(final DBBroker broker, final boolean exclusive) throws LockException {
        final LockManager lockManager = broker.getBrokerPool().getLockManager();
        final ManagedDocumentLock[] managedDocumentLocks = new ManagedDocumentLock[partCount];
        try {
            for (int i = 0; i < partCount; i++) {
                final DocumentImpl doc = parts[i].getOwnerDocument();
                final ManagedDocumentLock docLock;
                if (exclusive) {
                    docLock = lockManager.acquireDocumentWriteLock(doc.getURI());
                } else {
                    docLock = lockManager.acquireDocumentReadLock(doc.getURI());
                }
                managedDocumentLocks[i] = docLock;
            }
            return new ManagedLocks<>(managedDocumentLocks);
        } catch (final LockException e) {
            // unlock any previously locked documents
            new ManagedLocks<>(managedDocumentLocks).close();
            throw e;
        }
    }

    private class DocumentIterator implements Iterator<DocumentImpl> {
        private int currentDoc = 0;

        @Override
        public final boolean hasNext() {
            return currentDoc < partCount;
        }

        @Override
        public final DocumentImpl next() {
            if(currentDoc == partCount) {
                throw new NoSuchElementException();
            } else {
                return parts[currentDoc++].getOwnerDocument();
            }
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private class CollectionIterator implements Iterator<Collection> {

        private final Iterator<Collection> iterator;

        CollectionIterator() {
            if(partCount > 0) {
                final ObjectSet<Collection> collections = new ObjectOpenHashSet<>(partCount);
                for (int i = 0; i < partCount; i++) {
                    collections.add(parts[i].getOwnerDocument().getCollection());
                }
                iterator = collections.iterator();
            } else {
                iterator = null;
            }
        }

        @Override
        public final boolean hasNext() {
            return iterator != null && iterator.hasNext();
        }

        @Override
        public final Collection next() {
            return iterator.next();
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }

    private final class Part {

        private boolean isSorted = false;
        private NodeProxy array[];
        private int length = 0;

        Part(final int initialSize) {
            this.array = new NodeProxy[initialSize];
        }

        public void selectParentChild(final NodeSet result, NodeProxy na,
                final NodeSetIterator ia, final int mode, final  int contextId) {
            if(length == 0) {
                return;
            }
            int pos = 0;
            int startPos = 0;
            NodeProxy nb = array[pos];
            NodeId lastMarked = na.getNodeId();
            while(true) {
                // first, try to find nodes belonging to the same doc
                if(na.getOwnerDocument().getDocId() != nb.getOwnerDocument().getDocId()) {
                    break;
                }
                // same document
                final NodeId pa = na.getNodeId();
                final NodeId pb = nb.getNodeId();
                final int relation = pb.computeRelation(pa);
                if(relation != -1) {
                    if(relation == NodeId.IS_CHILD) {
                        if(mode == NodeSet.DESCENDANT) {
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                nb.addContextNode(contextId, na);
                            } else {
                                nb.copyContext(na);
                            }
                            result.add(nb);
                        } else {
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                na.addContextNode(contextId, nb);
                            } else {
                                na.copyContext(nb);
                            }
                            result.add(na);
                        }
                    }
                    if(++pos < length) {
                        nb = array[pos];
                    } else if(ia.hasNext()) {
                        final NodeProxy next = ia.peekNode();
                        if(next.getNodeId().isDescendantOf(pa)) {
                            pos = startPos;
                            nb = array[pos];
                            na = ia.next();
                            startPos = pos;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    final int cmp = pa.compareTo(pb);
                    if(cmp < 0) {
                        if(ia.hasNext()) {
                            NodeProxy next = ia.next();
                            if(next.getNodeId().isDescendantOf(pa)) {
                                pos = startPos;
                                nb = array[pos];
                            } else {
                                if(!next.getNodeId().isDescendantOf(lastMarked)) {
                                    lastMarked = next.getNodeId();
                                    startPos = pos;
                                }
                            }
                            na = next;
                        } else {
                            break;
                        }
                    } else {
                        if(++pos < length) {
                            nb = array[pos];
                        } else {
                            if(ia.hasNext()) {
                                NodeProxy next = ia.next();
                                if(next.getNodeId().isDescendantOf(pa)) {
                                    pos = startPos;
                                    nb = array[pos];
                                }
                                na = next;
                            }
                        }
                    }
                }
            }
        }

        void add(final NodeProxy p) {
            // just check if this node has already been added. We only
            // check the last entry, which should avoid most of the likely
            // duplicates. The remaining duplicates are removed by
            // removeDuplicates().
            /* ljo's modification, currently breaks the test suite (in-memory vs stored nodes ?) :
               NodeId nodeId = p.getNodeId();
               if (!NodeId.ROOT_NODE.equals(nodeId)) {
               if (length > 0 &&
               array[length - 1].getNodeId().equals(nodeId)) {
            */
            if(length > 0 && array[length - 1].getNodeId().equals(p.getNodeId())) {
                array[length - 1].addMatches(p);
                return;
                //} ljo's modification
            } else if(length == array.length) {
                final int newLength = length << 1;
                final NodeProxy temp[] = new NodeProxy[newLength];
                System.arraycopy(array, 0, temp, 0, length);
                array = temp;
            }
            array[length++] = p;
        }

        boolean contains(final NodeId nodeId) {
            return get(nodeId) != null;
        }

        NodeProxy get(final int pos) {
            return array[pos];
        }

        NodeProxy get(final NodeId nodeId) {
            int low = 0;
            int high = length - 1;
            int mid;
            int cmp;
            NodeProxy p;
            while(low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                cmp = p.getNodeId().compareTo(nodeId);
                if(cmp == 0) {
                    return p;
                }
                if(cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            return null;
        }

        DocumentImpl getOwnerDocument() {
            if(length == 0) {
                return null;
            } else {
                return array[0].getOwnerDocument();
            }
        }

        void sort() {
            if(isSorted) {
                return;
            }
            FastQSort.sortByNodeId(array, 0, length - 1);
        }

        /**
         * Check if the node identified by its node id has an ancestor
         * contained in this node set and return the ancestor found.
         *
         * If directParent is true, only immediate ancestors (parents) are
         * considered. Otherwise the method will call itself recursively for
         * all the node's parents.
         *
         * If includeSelf is true, the method returns also true if the node
         * itself is contained in the node set.
         *
         * @param doc          a <code>DocumentImpl</code> value
         * @param nodeId       a <code>NodeId</code> value
         * @param directParent a <code>boolean</code> value
         * @param includeSelf  a <code>boolean</code> value
         * @return a <code>NodeProxy</code> value
         */
        NodeProxy parentWithChild(final DocumentImpl doc, final NodeId nodeId, final boolean directParent, final boolean includeSelf) {
            NodeProxy temp;
            if(includeSelf && (temp = get(nodeId)) != null) {
                return temp;
            }

            NodeId parentNodeId = nodeId.getParentId();
            while(parentNodeId != null) {
                if((temp = get(parentNodeId)) != null) {
                    return temp;
                } else if(directParent) {
                    return null;
                }
                parentNodeId = parentNodeId.getParentId();
            }
            return null;
        }

        NodeProxy hasDescendantsInSet(final NodeId ancestorId, final int contextId, final boolean includeSelf) {
            // do a binary search to pick some node in the range
            // of valid child ids
            int low = 0;
            int high = length - 1;
            int mid = 0;
            int cmp;
            NodeId id;
            while(low <= high) {
                mid = (low + high) / 2;
                id = array[mid].getNodeId();
                if(id.isDescendantOrSelfOf(ancestorId)) {
                    break; // found a child node, break out.
                }
                cmp = id.compareTo(ancestorId);
                if(cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            if(low > high) {
                return null; // no node found
            }
            // find the first child node in the range
            while(mid > 0 && array[mid - 1].getNodeId().compareTo(ancestorId) >= 0) {
                --mid;
            }
            final NodeProxy ancestor = new NodeProxy(getOwnerDocument(), ancestorId, Node.ELEMENT_NODE);
            // we need to check if self should be included
            boolean foundOne = false;
            for(int i = mid; i < length; i++) {
                cmp = array[i].getNodeId().computeRelation(ancestorId);
                if(cmp > -1) {
                    boolean add = true;
                    if(cmp == NodeId.IS_SELF) {
                        add = includeSelf;
                    }
                    if(add) {
                        if(Expression.NO_CONTEXT_ID != contextId) {
                            ancestor.deepCopyContext(array[i], contextId);
                        } else {
                            ancestor.copyContext(array[i]);
                        }
                        ancestor.addMatches(array[i]);
                        foundOne = true;
                    }
                } else {
                    break;
                }
            }
            return foundOne ? ancestor : null;
        }

        /**
         * Find all nodes in the current set being children or descendants of
         * the given parent node.
         *
         * @param result      the node set to which matching nodes will be appended.
         * @param parent      the parent node to search for.
         * @param childOnly   only include child nodes, not descendant nodes
         * @param includeSelf include the self:: axis
         * @param mode
         * @param contextId
         */
        NodeSet getDescendantsInSet(final NodeSet result, final NodeProxy parent, final boolean childOnly,
                final boolean includeSelf, final int mode, final int contextId, final boolean copyMatches) {

            final NodeId parentId = parent.getNodeId();
            // document nodes are treated specially
            if(parentId == NodeId.DOCUMENT_NODE) {
                for(int i = 0; i < length; i++) {
                    boolean add;
                    if(childOnly) {
                        add = array[i].getNodeId().getTreeLevel() == 1;
                    } else if(includeSelf) {
                        add = true;
                    } else {
                        add = array[i].getNodeId() != NodeId.DOCUMENT_NODE;
                    }
                    if(add) {
                        switch(mode) {

                            case NodeSet.DESCENDANT:
                                if(Expression.NO_CONTEXT_ID != contextId) {
                                    array[i].deepCopyContext(parent, contextId);
                                } else {
                                    array[i].copyContext(parent);
                                }
                                if(copyMatches) {
                                    array[i].addMatches(parent);
                                }
                                result.add(array[i]);
                                break;

                            case NodeSet.ANCESTOR:
                                if(Expression.NO_CONTEXT_ID != contextId) {
                                    parent.deepCopyContext(array[i], contextId);
                                } else {
                                    parent.copyContext(array[i]);
                                }
                                if(copyMatches) {
                                    parent.addMatches(array[i]);
                                }
                                result.add(parent, 1);
                                break;
                        }
                    }
                }
            } else {
                // do a binary search to pick some node in the range of valid
                // child ids
                int low = 0;
                int high = length - 1;
                int mid = 0;
                int cmp;
                while(low <= high) {
                    mid = (low + high) / 2;
                    final NodeProxy p = array[mid];
                    if(p.getNodeId().isDescendantOrSelfOf(parentId)) {
                        break;    // found a child node, break out.
                    }
                    cmp = p.getNodeId().compareTo(parentId);
                    if(cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
                if(low > high) {
                    return result; // no node found
                }
                // find the first child node in the range
                while(mid > 0 && array[mid - 1].getNodeId().compareTo(parentId) > -1) {
                    --mid;
                }
                // walk through the range of child nodes we found
                for(int i = mid; i < length; i++) {
                    cmp = array[i].getNodeId().computeRelation(parentId);
                    if(cmp > -1) {
                        boolean add = true;
                        if(childOnly) {
                            add = cmp == NodeId.IS_CHILD;
                        } else if(cmp == NodeId.IS_SELF) {
                            add = includeSelf;
                        }
                        if(add) {
                            switch(mode) {

                                case NodeSet.DESCENDANT:
                                    if(Expression.NO_CONTEXT_ID != contextId) {
                                        array[i].deepCopyContext(parent, contextId);
                                    } else {
                                        array[i].copyContext(parent);
                                    }
                                    array[i].addMatches(parent);
                                    result.add(array[i]);
                                    break;

                                case NodeSet.ANCESTOR:
                                    if(Expression.NO_CONTEXT_ID != contextId) {
                                        //parent.addContextNode(contextId, array[i]);
                                        parent.deepCopyContext(array[i], contextId);
                                    } else {
                                        parent.copyContext(array[i]);
                                    }
                                    parent.addMatches(array[i]);
                                    result.add(parent, 1);
                                    break;
                            }
                        }
                    } else {
                        break;
                    }
                }
            }
            return result;
        }

        /**
         * Remove all duplicate nodes from this part.
         *
         * @param mergeContext a <code>boolean</code> value
         * @return the new length of the part, after removing all duplicates
         */
        int removeDuplicates(final boolean mergeContext) {
            int j = 0;
            for(int i = 1; i < length; i++) {
                if(!array[i].getNodeId().equals(array[j].getNodeId())) {
                    if(i != ++j) {
                        array[j] = array[i];
                    }
                } else {
                    if(mergeContext) {
                        array[j].addContext(array[i]);
                    }
                    array[j].addMatches(array[i]);
                }
            }
            length = ++j;
            return length;
        }

        void determineIndexType() {
            //Is the index type initialized ?
            if(indexType == Type.ANY_TYPE) {
                for(int i = 0; i < length; i++) {
                    final NodeProxy node = array[i];
                    if(node.getOwnerDocument().getCollection().isTempCollection()) {
                        //Temporary nodes return default values
                        indexType = Type.ITEM;
                        break;
                    }
                    int nodeIndexType = node.getIndexType();
                    //Refine type
                    //TODO : use common subtype
                    if(indexType == Type.ANY_TYPE) {
                        indexType = nodeIndexType;
                    } else {
                        //Broaden type
                        //TODO : use common supertype
                        if(indexType != nodeIndexType) {
                            indexType = Type.ITEM;
                        }
                    }
                }
            }
        }

        void setSelfAsContext(final int contextId) {
            for(int i = 0; i < length; i++) {
                array[i].addContextNode(contextId, array[i]);
            }
        }

    }

    private class ExtArrayIterator implements NodeSetIterator, SequenceIterator {

        Part currentPart = null;
        int partPos = 0;
        int pos = 0;
        NodeProxy next = null;

        ExtArrayIterator() {
            if(partPos < partCount) {
                this.currentPart = parts[partPos];
            }
            if(currentPart != null && currentPart.length > 0) {
                this.next = currentPart.get(0);
            }
        }

        @Override
        public final void setPosition(final NodeProxy proxy) {
            partPos = ArrayUtils.binarySearch(documentIds, proxy.getOwnerDocument().getDocId(), partCount);
            if(partPos >= 0) {
                currentPart = parts[partPos];
                int low = 0;
                int high = currentPart.length - 1;
                int mid;
                NodeProxy p;
                while(low <= high) {
                    mid = (low + high) / 2;
                    p = currentPart.array[mid];
                    final int cmp = p.getNodeId().compareTo(proxy.getNodeId());
                    if(cmp == 0) {
                        pos = mid;
                        next = p;
                        return;
                    }
                    if(cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
            }
            next = null;
        }

        @Override
        public final boolean hasNext() {
            return next != null;
        }

        @Override
        public final NodeProxy next() {
            if(next == null) {
                throw new NoSuchElementException();
            }

            final NodeProxy n = next;
            next = null;
            if(++pos == currentPart.length) {
                if(++partPos < partCount) {
                    currentPart = parts[partPos];
                    if(currentPart != null && currentPart.length > 0) {
                        next = currentPart.get(0);
                        pos = 0;
                    }
                }
            } else {
                next = currentPart.get(pos);
            }
            return n;
        }

        @Override
        public final NodeProxy peekNode() {
            return next;
        }

        @Override
        public final Item nextItem() {
            if(next == null) {
                return null;
            } else {
                return next();
            }
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
