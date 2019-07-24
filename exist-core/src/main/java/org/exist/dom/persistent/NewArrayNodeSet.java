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

import org.exist.collections.Collection;
import org.exist.collections.ManagedLocks;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.LockManager;
import org.exist.storage.lock.ManagedDocumentLock;
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
public class NewArrayNodeSet extends AbstractArrayNodeSet implements ExtNodeSet, DocumentSet {

    private Set<Collection> cachedCollections = null;

    private int documentCount = 0;

    /**
     * An array of Document IDs of length {@link #documentCount}
     */
    private int documentIds[] = new int[16];

    /**
     * An array of offsets into {@link #nodes},
     * the index is the index from {@link #documentNodesOffset}.
     */
    private int documentNodesOffset[] = new int[16];

    /**
     * An array of the node count for each document,
     * each value is a count of the nodes for a specific document,
     * the index is the index from {@link #documentNodesOffset}.
     */
    private int documentNodesCount[] = new int[16];

    /**
     * An array of nodes from documents,
     * the range of nodes for a document is retrieved
     * by:
     * <pre>{@code
     * int docId = ...;
     * int docIdx = findDoc(docId);  // lookup in documentIds
     *
     * int nodeStartIdx = documentNodesOffset[docIdx];
     * int nodeCount = documentNodesCount[docIdx];
     *
     * NodeProxy docNodes[] = new NodeProxy[nodeCount];
     * System.arraycopy(nodes, nodeStartIdx, docNodes, 0, nodeCount);
     * }</pre>
     */
    private NodeProxy nodes[];

    public NewArrayNodeSet() {
        nodes = new NodeProxy[INITIAL_SIZE];
    }

    public NewArrayNodeSet(final NewArrayNodeSet other) {
        size = other.size;
        isSorted = other.isSorted;
        hasOne = other.hasOne;
        itemType = other.itemType;
        nodes = new NodeProxy[other.nodes.length];
        System.arraycopy(other.nodes, 0, nodes, 0, nodes.length);
        documentCount = other.documentCount;
        documentIds = new int[other.documentIds.length];
        System.arraycopy(other.documentIds, 0, documentIds, 0, documentIds.length);
        documentNodesOffset = new int[other.documentNodesOffset.length];
        System.arraycopy(other.documentNodesOffset, 0, documentNodesOffset, 0, documentNodesOffset.length);
        documentNodesCount = new int[other.documentNodesCount.length];
        System.arraycopy(other.documentNodesCount, 0, documentNodesCount, 0, documentNodesCount.length);
    }

    private void ensureCapacity() {
        if(size == nodes.length) {
            final int nsize = size << 1;
            final NodeProxy temp[] = new NodeProxy[nsize];
            System.arraycopy(nodes, 0, temp, 0, size);
            nodes = temp;
        }
    }

    private int findDoc(DocumentImpl doc) {
        return findDoc(doc.getDocId());
    }

    private int findDoc(int docId) {
        int low = 0;
        int high = documentCount - 1;
        while(low <= high) {
            final int mid = (low + high) >>> 1;
            final int midVal = documentIds[mid];
            if(midVal < docId) {
                low = mid + 1;
            } else if(midVal > docId) {
                high = mid - 1;
            } else {
                return mid;
            } // key found
        }
        return -(low + 1);  // key not found.
    }

    @Override
    public NodeSet copy() {
        return new NewArrayNodeSet(this);
    }

    @Override
    public void reset() {
        Arrays.fill(nodes, null);
        documentCount = 0;
        size = 0;
        isSorted = false;
        state = 0;
    }

    @Override
    protected final void addInternal(final NodeProxy proxy, final int sizeHint) {
        ensureCapacity();
        nodes[size++] = proxy;
    }

    @Override
    public int getSizeHint(final DocumentImpl doc) {
        if(!isSorted()) {
            sort();
        }
        final int idx = findDoc(doc);
        return idx < 0 ? Constants.NO_SIZE_HINT : documentNodesCount[idx];
    }

    @Override
    public NodeSetIterator iterator() {
        if(!isSorted()) {
            sort();
        }
        return new NewArrayIterator();
    }

    @Override
    public SequenceIterator iterate() {
        sortInDocumentOrder();
        return new NewArrayIterator();
    }

    @Override
    public SequenceIterator unorderedIterator() {
        if(!isSorted()) {
            sort();
        }
        return new NewArrayIterator();
    }

    @Override
    public boolean contains(final NodeProxy proxy) {
        sort();
        final int idx = findDoc(proxy.getOwnerDocument());
        if(idx < 0) {
            return false;
        }
        return get(idx, proxy.getNodeId()) != null;
    }

    @Override
    public NodeProxy get(final int pos) {
        if(pos < 0 || pos >= size) {
            return null;
        }
        return nodes[pos];
    }

    @Override
    public NodeProxy get(final NodeProxy proxy) {
        sort();
        final int idx = findDoc(proxy.getOwnerDocument());
        if(idx < 0) {
            return null;
        }
        return get(idx, proxy.getNodeId());
    }

    @Override
    public NodeProxy get(final DocumentImpl doc, final NodeId nodeId) {
        sort();
        final int idx = findDoc(doc);
        if(idx < 0) {
            return null;
        }
        return get(idx, nodeId);
    }

    private NodeProxy get(final int docIdx, final NodeId nodeId) {
        if(!isSorted()) {
            sort();
        }
        int low = documentNodesOffset[docIdx];
        int high = low + (documentNodesCount[docIdx] - 1);
        int mid;
        int cmp;
        NodeProxy p;
        while(low <= high) {
            mid = (low + high) / 2;
            p = nodes[mid];
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

    @Override
    protected final NodeSet getDescendantsInSet(final NodeSet al, final boolean childOnly,
            final boolean includeSelf, final int mode, final int contextId, final boolean copyMatches) {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        int docIdx;
        for(final NodeProxy node : al) {
            docIdx = findDoc(node.getOwnerDocument());
            if(docIdx > -1) {
                getDescendantsInSet(docIdx, result, node, childOnly, includeSelf,
                    mode, contextId, copyMatches);
            }
        }
        return result;
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
    private NodeSet getDescendantsInSet(final int docIdx, final NodeSet result, final NodeProxy parent,
            final boolean childOnly, final boolean includeSelf, final int mode, final int contextId, final boolean copyMatches) {
        NodeProxy p;
        final NodeId parentId = parent.getNodeId();
        // document nodes are treated specially
        if(parentId == NodeId.DOCUMENT_NODE) {
            final int end = documentNodesOffset[docIdx] + documentNodesCount[docIdx];
            for(int i = documentNodesOffset[docIdx]; i < end; i++) {
                boolean add;
                if(childOnly) {
                    add = nodes[i].getNodeId().getTreeLevel() == 1;
                } else if(includeSelf) {
                    add = true;
                } else {
                    add = nodes[i].getNodeId() != NodeId.DOCUMENT_NODE;
                }
                if(add) {
                    switch(mode) {
                        case NodeSet.DESCENDANT:
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                nodes[i].deepCopyContext(parent, contextId);
                            } else {
                                nodes[i].copyContext(parent);
                            }
                            if(copyMatches) {
                                nodes[i].addMatches(parent);
                            }
                            result.add(nodes[i]);
                            break;
                        case NodeSet.ANCESTOR:
                            if(Expression.NO_CONTEXT_ID != contextId) {
                                parent.deepCopyContext(nodes[i], contextId);
                            } else {
                                parent.copyContext(nodes[i]);
                            }
                            if(copyMatches) {
                                parent.addMatches(nodes[i]);
                            }
                            result.add(parent, 1);
                            break;
                    }
                }
            }
        } else {
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentNodesOffset[docIdx];
            int high = low + (documentNodesCount[docIdx] - 1);
            final int end = low + documentNodesCount[docIdx];
            int mid = low;
            int cmp;
            while(low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
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
            while(mid > documentNodesOffset[docIdx] && nodes[mid - 1].getNodeId().compareTo(parentId) > -1) {
                --mid;
            }
            // walk through the range of child nodes we found
            for(int i = mid; i < end; i++) {
                cmp = nodes[i].getNodeId().computeRelation(parentId);
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
                                    nodes[i].deepCopyContext(parent, contextId);
                                } else {
                                    nodes[i].copyContext(parent);
                                }
                                if(copyMatches) {
                                    nodes[i].addMatches(parent);
                                }
                                result.add(nodes[i]);
                                break;
                            case NodeSet.ANCESTOR:
                                if(Expression.NO_CONTEXT_ID != contextId) {
                                    parent.deepCopyContext(nodes[i], contextId);
                                } else {
                                    parent.copyContext(nodes[i]);
                                }
                                if(copyMatches) {
                                    parent.addMatches(nodes[i]);
                                }
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

    @Override
    public NodeProxy hasDescendantsInSet(final DocumentImpl doc, final NodeId ancestorId,
            final boolean includeSelf, final int contextId, final boolean copyMatches) {
        sort();
        final int docIdx = findDoc(doc);
        if(docIdx < 0) {
            return null;
        }
        return hasDescendantsInSet(docIdx, ancestorId, contextId, includeSelf, copyMatches);
    }

    /**
     * The method <code>hasDescendantsInSet</code>
     *
     * @param ancestorId  a <code>NodeId</code> value
     * @param contextId   an <code>int</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy hasDescendantsInSet(final int docIdx, final NodeId ancestorId,
            final int contextId, final boolean includeSelf, final boolean copyMatches) {
        // do a binary search to pick some node in the range of valid child ids
        int low = documentNodesOffset[docIdx];
        int high = low + (documentNodesCount[docIdx] - 1);
        final int end = low + documentNodesCount[docIdx];
        int mid = 0;
        int cmp;
        NodeId id;
        while(low <= high) {
            mid = (low + high) / 2;
            id = nodes[mid].getNodeId();
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
        while(mid > documentNodesOffset[docIdx] && nodes[mid - 1].getNodeId().compareTo(ancestorId) >= 0) {
            --mid;
        }
        final NodeProxy ancestor = new NodeProxy(nodes[documentNodesOffset[docIdx]].getOwnerDocument(),
            ancestorId, Node.ELEMENT_NODE);
        // we need to check if self should be included
        boolean foundOne = false;
        for(int i = mid; i < end; i++) {
            cmp = nodes[i].getNodeId().computeRelation(ancestorId);
            if(cmp > -1) {
                boolean add = true;
                if(cmp == NodeId.IS_SELF) {
                    add = includeSelf;
                }
                if(add) {
                    if(Expression.NO_CONTEXT_ID != contextId) {
                        ancestor.deepCopyContext(nodes[i], contextId);
                    } else {
                        ancestor.copyContext(nodes[i]);
                    }
                    if(copyMatches) {
                        ancestor.addMatches(nodes[i]);
                    }
                    foundOne = true;
                }
            } else {
                break;
            }
        }
        return foundOne ? ancestor : null;
    }

    @Override
    public void sort(final boolean mergeContexts) {
        if(isSorted) {
            return;
        } else if(hasOne) {
            isSorted = true; // shortcut: don't sort if there's just one item
            removeDuplicates(mergeContexts);
            updateDocs();
            return;
        } else {
            if(size > 0) {
                FastQSort.sort(nodes, 0, size - 1);
                removeDuplicates(mergeContexts);
            }
            updateDocs();
            isSorted = true;
        }
    }

    public void updateNoSort() {
        if (needsSort()) {
            sort(false);
        } else {
            updateDocs();
            isSorted = true;
        }
    }


    /**
     * Check if this node set is sorted in document order
     *
     * @return true if sorted
     */
    private boolean needsSort() {
        if (hasOne) {
            return false;
        }
        for(int i = 1; i < size; i++) {
            if (nodes[i].compareTo(nodes[i - 1]) < 0) {
                return true;
            }
        }
        return false;
    }

    private void updateDocs() {
        if(size == 1) {
            documentIds[0] = nodes[0].getOwnerDocument().getDocId();
            documentNodesOffset[0] = 0;
            documentNodesCount[0] = 1;
            documentCount = 1;
        } else {
            documentCount = 0;
            for(int i = 0; i < size; i++) {
                if(i == 0) {
                    // first document in the set
                    documentIds[0] = nodes[0].getOwnerDocument().getDocId();
                    documentNodesOffset[0] = 0;
                    documentNodesCount[0] = 1;
                    ++documentCount;
                } else if(documentIds[documentCount - 1] == nodes[i].getOwnerDocument().getDocId()) {
                    // node belongs to same document as previous node
                    ++documentNodesCount[documentCount - 1];
                } else {
                    // new document
                    ensureDocCapacity();
                    documentIds[documentCount] = nodes[i].getOwnerDocument().getDocId();
                    documentNodesOffset[documentCount] = i;
                    documentNodesCount[documentCount++] = 1;
                }
            }
        }
    }

    private void ensureDocCapacity() {
        if(documentCount == documentIds.length) {
            final int nlen = documentCount << 1;
            int[] temp = new int[nlen];
            System.arraycopy(documentIds, 0, temp, 0, documentCount);
            documentIds = temp;
            temp = new int[nlen];
            System.arraycopy(documentNodesOffset, 0, temp, 0, documentCount);
            documentNodesOffset = temp;
            temp = new int[nlen];
            System.arraycopy(documentNodesCount, 0, temp, 0, documentCount);
            documentNodesCount = temp;
        }
    }

    /**
     * Remove all duplicate nodes from this set.
     *
     * @param mergeContext a <code>boolean</code> value
     * @return the new length of the part, after removing all duplicates
     */
    private int removeDuplicates(final boolean mergeContext) {
        int j = 0;
        for(int i = 1; i < size; i++) {
            if(nodes[i].compareTo(nodes[j]) != 0) {
                if(i != ++j) {
                    nodes[j] = nodes[i];
                }
            } else {
                if(mergeContext) {
                    nodes[j].addContext(nodes[i]);
                }
                nodes[j].addMatches(nodes[i]);
            }
        }
        size = ++j;
        return size;
    }

    @Override
    public void setSelfAsContext(final int contextId) throws XPathException {
        for(int i = 0; i < size; i++) {
            nodes[i].addContextNode(contextId, nodes[i]);
        }
    }

    @Override
    public NodeSet selectPrecedingSiblings(final NodeSet contextSet, final int contextId) {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy reference : contextSet) {
            final NodeId parentId = reference.getNodeId().getParentId();
            final int docIdx = findDoc(reference.getOwnerDocument());
            if(docIdx < 0) {
                continue;
            }
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentNodesOffset[docIdx];
            int high = low + (documentNodesCount[docIdx] - 1);
            final int end = low + documentNodesCount[docIdx];
            int mid = low;
            int cmp;
            NodeProxy p = null;
            while(low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
                if(p.getNodeId().isDescendantOf(parentId)
                        || (parentId.equals(NodeId.DOCUMENT_NODE) && p.getNodeId().getTreeLevel() == 1)) {
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
                continue; // no node found
            }
            // find the first child node in the range
            while(mid < end && nodes[mid].getNodeId().isDescendantOf(parentId)) {
                ++mid;
            }

            if (mid == 0 && parentId.equals(NodeId.DOCUMENT_NODE)) {
                mid = getLength();
            }

            --mid;

            final NodeId refId = reference.getNodeId();
            for(int i = mid; i >= documentNodesOffset[docIdx]; i--) {
                final NodeId currentId = nodes[i].getNodeId();
                if(!(currentId.isDescendantOf(parentId) || (p != null && parentId.equals(NodeId.DOCUMENT_NODE) && p.getNodeId().getTreeLevel() == 1))) {
                    break;
                }
                if(currentId.getTreeLevel() == refId.getTreeLevel() && currentId.compareTo(refId) < 0) {
                    if (contextId != Expression.IGNORE_CONTEXT
                            && nodes[i].getContext() != null
                            && reference.getContext() != null
                            && nodes[i].getContext().getContextId() == reference.getContext().getContextId()) {
                        continue;
                    }

                    if(Expression.IGNORE_CONTEXT != contextId) {
                        if(Expression.NO_CONTEXT_ID == contextId) {
                            nodes[i].copyContext(reference);
                        } else {
                            nodes[i].addContextNode(contextId, reference);
                        }
                    }
                    result.add(nodes[i]);
                }
            }
        }
        return result;
    }

    /**
     * The method <code>selectFollowingSiblings</code>
     *
     * @param contextSet a <code>NodeSet</code> value
     * @param contextId  an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    @Override
    public NodeSet selectFollowingSiblings(final NodeSet contextSet, final int contextId) {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy reference : contextSet) {
            final NodeId parentId = reference.getNodeId().getParentId();
            final int docIdx = findDoc(reference.getOwnerDocument());
            if(docIdx < 0) {
                continue;
            } //BUG: can't be null, make trouble @LocationStep line 388 -shabanovd
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentNodesOffset[docIdx];
            int high = low + (documentNodesCount[docIdx] - 1);
            final int end = low + documentNodesCount[docIdx];
            int mid = low;
            int cmp;
            NodeProxy p = null;
            while(low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
                if(p.getNodeId().isDescendantOf(parentId)
                        || (parentId.equals(NodeId.DOCUMENT_NODE) && p.getNodeId().getTreeLevel() == 1)) {
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
                continue; // no node found
            }
            // find the first child node in the range
            while(mid > documentNodesOffset[docIdx] && nodes[mid - 1].getNodeId().compareTo(parentId) > -1) {
                --mid;
            }
            final NodeId refId = reference.getNodeId();
            for(int i = mid; i < end; i++) {
                final NodeId currentId = nodes[i].getNodeId();
                if(!(currentId.isDescendantOf(parentId) || (p != null && parentId.equals(NodeId.DOCUMENT_NODE) && p.getNodeId().getTreeLevel() == 1))) {
                    continue;
                }
                if(currentId.getTreeLevel() == refId.getTreeLevel() && currentId.compareTo(refId) > 0) {
                    if (contextId != Expression.IGNORE_CONTEXT
                            && nodes[i].getContext() != null
                            && reference.getContext() != null
                            && nodes[i].getContext().getContextId() == reference.getContext().getContextId()) {
                        continue;
                    }

                    if(Expression.IGNORE_CONTEXT != contextId) {
                        if(Expression.NO_CONTEXT_ID == contextId) {
                            nodes[i].copyContext(reference);
                        } else {
                            nodes[i].addContextNode(contextId, reference);
                        }
                    }
                    result.add(nodes[i]);
                }
            }
        }
        return result;
    }

    @Override
    public NodeSet selectFollowing(final NodeSet fl, final int contextId) throws XPathException {
        return selectFollowing(fl, -1, contextId);
    }

    @Override
    public NodeSet selectFollowing(final NodeSet pl, final int position, final int contextId) throws XPathException, UnsupportedOperationException {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy reference : pl) {
            final int idx = findDoc(reference.getOwnerDocument());
            if(idx < 0) {
                continue;
            }
            int i = documentNodesOffset[idx];
            for(; i < size; i++) {
                if(nodes[i].getOwnerDocument().getDocId() != reference.getOwnerDocument().getDocId() ||
                    (nodes[i].compareTo(reference) > 0 &&
                        !nodes[i].getNodeId().isDescendantOf(reference.getNodeId()))) {
                    break;
                }
            }
            int n = 0;
            for(int j = i; j < size; j++) {
                if(nodes[j].getOwnerDocument().getDocId() != reference.getOwnerDocument().getDocId()) {
                    break;
                }
                if(!reference.getNodeId().isDescendantOf(nodes[j].getNodeId())) {
                    if(position < 0 || ++n == position) {
                        if (contextId != Expression.IGNORE_CONTEXT
                                && nodes[j].getContext() != null
                                && reference.getContext() != null
                                && nodes[j].getContext().getContextId() == reference.getContext().getContextId()) {
                            continue;
                        }

                        if(Expression.IGNORE_CONTEXT != contextId) {
                            if(Expression.NO_CONTEXT_ID == contextId) {
                                nodes[j].copyContext(reference);
                            } else {
                                nodes[j].addContextNode(contextId, reference);
                            }
                        }
                        result.add(nodes[j]);
                    }
                    if(n == position) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public NodeSet selectPreceding(final NodeSet pl, final int contextId)
            throws XPathException {
        return selectPreceding(pl, -1, contextId);
    }

    @Override
    public NodeSet selectPreceding(final NodeSet pl, final int position,
            final int contextId) throws XPathException,
            UnsupportedOperationException {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        for(final NodeProxy reference : pl) {
            final int idx = findDoc(reference.getOwnerDocument());
            if(idx < 0) {
                continue;
            }
            int i = documentNodesOffset[idx];
            // TODO: check document id
            for(; i < size; i++) {
                if(nodes[i].compareTo(reference) >= 0) {
                    break;
                }
            }
            --i;
            int n = 0;
            for(int j = i; j >= documentNodesOffset[idx]; j--) {
                if(!reference.getNodeId().isDescendantOf(nodes[j].getNodeId())) {
                    if(position < 0 || ++n == position) {
                        if (contextId != Expression.IGNORE_CONTEXT
                                && nodes[j].getContext() != null
                                && reference.getContext() != null
                                && nodes[j].getContext().getContextId() == reference.getContext().getContextId()) {
                            continue;
                        }

                        if(Expression.IGNORE_CONTEXT != contextId) {
                            if(Expression.NO_CONTEXT_ID == contextId) {
                                nodes[j].copyContext(reference);
                            } else {
                                nodes[j].addContextNode(contextId, reference);
                            }
                        }
                        result.add(nodes[j]);
                    }
                    if(n == position) {
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl doc, final NodeId nodeId, final boolean directParent, final boolean includeSelf) {
        sort();
        final int docIdx = findDoc(doc);
        if(docIdx < 0) {
            return null;
        }
        return parentWithChild(docIdx, nodeId, directParent, includeSelf);
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
     * @param nodeId       a <code>NodeId</code> value
     * @param directParent a <code>boolean</code> value
     * @param includeSelf  a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy parentWithChild(final int docIdx, final NodeId nodeId, final boolean directParent, final boolean includeSelf) {
        NodeProxy temp;
        if(includeSelf && (temp = get(docIdx, nodeId)) != null) {
            return temp;
        }
        NodeId parentNodeId = nodeId.getParentId();
        while(parentNodeId != null) {
            if((temp = get(docIdx, parentNodeId)) != null) {
                return temp;
            } else if(directParent) {
                return null;
            }
            parentNodeId = parentNodeId.getParentId();
        }
        return null;
    }

    @Override
    public NodeSet except(final NodeSet other) {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        for(int i = 0; i < size; i++) {
            if(!other.contains(nodes[i])) {
                result.add(nodes[i]);
            }
        }
        return result;
    }

    @Override
    public NodeSet getContextNodes(final int contextId) {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        DocumentImpl lastDoc = null;
        for(int i = 0; i < size; i++) {
            final NodeProxy current = nodes[i];
            ContextItem contextNode = current.getContext();
            while(contextNode != null) {
                if(contextNode.getContextId() == contextId) {
                    final NodeProxy context = contextNode.getNode();
                    context.addMatches(current);
                    if(Expression.NO_CONTEXT_ID != contextId) {
                        context.addContextNode(contextId, context);
                    }
                    if(lastDoc != null && lastDoc.getDocId() != context.getOwnerDocument().getDocId()) {
                        lastDoc = context.getOwnerDocument();
                        result.add(context, getSizeHint(lastDoc));
                    } else {
                        result.add(context);
                    }
                }
                contextNode = contextNode.getNextDirect();
            }
        }
        return result;
    }

    /**
     * The method <code>debugParts</code>
     *
     * @return a <code>String</code> value
     */
    public String debugParts() {
        final StringBuilder buf = new StringBuilder();
        for(int i = 0; i < documentCount; i++) {
            buf.append(documentIds[i]);
            buf.append(' ');
        }
        return buf.toString();
    }

    @Override
    public int getIndexType() {
        //Is the index type initialized ?
        if(indexType == Type.ANY_TYPE) {
            for(int i = 0; i < size; i++) {
                final NodeProxy node = nodes[i];
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
        return indexType;
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        if(this == other) {
            return true;
        }

        sort();
        if(documentCount != other.getDocumentCount()) {
            return false;
        } else {
            for(int i = 0; i < documentCount; i++) {
                if(!other.contains(documentIds[i])) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        sort();
        if(cachedCollections == null) {
            cachedCollections = new HashSet<>();
            for(int i = 0; i < documentCount; i++) {
                final DocumentImpl doc = nodes[documentNodesOffset[i]].getOwnerDocument();
                if(!cachedCollections.contains(doc.getCollection())) {
                    cachedCollections.add(doc.getCollection());
                }
            }
        }
        return cachedCollections.iterator();
    }

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        sort();
        return new DocumentIterator();
    }

    @Override
    public int getDocumentCount() {
        sort();
        return documentCount;
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        sort();
        final int idx = findDoc(docId);
        if(idx < 0) {
            return null;
        }
        return nodes[documentNodesOffset[idx]].getOwnerDocument();
    }

    @Override
    public XmldbURI[] getNames() {
        sort();
        final XmldbURI[] uris = new XmldbURI[documentCount];
        for(int i = 0; i < documentCount; i++) {
            uris[i] = nodes[documentNodesOffset[i]].getOwnerDocument().getURI();
        }
        return uris;
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        sort();
        final DefaultDocumentSet set = new DefaultDocumentSet();

        //left
        for(int i = 0; i < documentCount; i++) {
            final DocumentImpl doc = nodes[documentNodesOffset[i]].getOwnerDocument();
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
        sort();
        if(other.getDocumentCount() > documentCount) {
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
        sort();
        return findDoc(docId) > -1;
    }

    @Override
    public NodeSet docsToNodeSet() {
        sort();
        final NodeSet result = new NewArrayNodeSet();
        DocumentImpl doc;
        for(int i = 0; i < documentCount; i++) {
            doc = nodes[documentNodesOffset[i]].getOwnerDocument();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) { // skip binary resources
                result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    @Override
    public ManagedLocks<ManagedDocumentLock> lock(final DBBroker broker, final boolean exclusive) throws LockException {
        sort();
        final LockManager lockManager = broker.getBrokerPool().getLockManager();
        final ManagedDocumentLock[] managedDocumentLocks = new ManagedDocumentLock[documentCount];
        try {
            for (int idx = 0; idx < documentCount; idx++) {
                final DocumentImpl doc = nodes[documentNodesOffset[idx]].getOwnerDocument();
                final ManagedDocumentLock managedDocumentLock;
                if (exclusive) {
                    managedDocumentLock = lockManager.acquireDocumentWriteLock(doc.getURI());
                } else {
                    managedDocumentLock = lockManager.acquireDocumentReadLock(doc.getURI());
                }
                managedDocumentLocks[idx] = managedDocumentLock;
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
            return currentDoc < documentCount;
        }

        @Override
        public final DocumentImpl next() {
            if(currentDoc == documentCount) {
                throw new NoSuchElementException();
            } else {
                return nodes[documentNodesOffset[currentDoc++]].getOwnerDocument();
            }
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }
    }

    @Override
    public void clearContext(final int contextId) throws XPathException {
        for(int i = 0; i < size; i++) {
            nodes[i].clearContext(contextId);
        }
    }

    protected class NewArrayIterator implements NodeSetIterator, SequenceIterator {
        private int pos = 0;

        @Override
        public final boolean hasNext() {
            return pos < size && pos > -1;
        }

        @Override
        public final NodeProxy next() {
            if(pos == size || pos < 0) {
                pos = -1;
                throw new NoSuchElementException();
            }
            return nodes[pos++];
        }

        @Override
        public long skippable() {
            if (pos == -1) {
                return 0;
            }
            return size - pos;
        }

        @Override
        public long skip(final long n) {
            final long skip = Math.min(n, pos == -1 ? 0 : size - pos);
            pos += skip;
            return skip;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        public final NodeProxy peekNode() {
            if(pos == size || pos < 0) {
                pos = -1;
                return null;
            }
            return nodes[pos];
        }

        @Override
        public final Item nextItem() {
            if(pos == size || pos < 0) {
                pos = -1;
                return null;
            }
            return nodes[pos++];
        }

        @Override
        public void setPosition(final NodeProxy proxy) {
            final int docIdx = findDoc(proxy.getOwnerDocument());
            if(docIdx > -1) {
                int low = documentNodesOffset[docIdx];
                int high = low + (documentNodesCount[docIdx] - 1);
                int mid, cmp;
                NodeProxy p;
                while(low <= high) {
                    mid = (low + high) / 2;
                    p = nodes[mid];
                    cmp = p.getNodeId().compareTo(proxy.getNodeId());
                    if(cmp == 0) {
                        pos = mid;
                        return;
                    }
                    if(cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
            }
            pos = -1;
        }
    }
}
