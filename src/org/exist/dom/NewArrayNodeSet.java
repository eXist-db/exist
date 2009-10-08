/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
 *  $Id: ExtArrayNodeSet.java 7654 2008-04-22 09:07:04Z wolfgang_m $
 */
package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.FastQSort;
import org.exist.util.LockException;
import org.exist.util.hashtable.ObjectHashSet;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import java.util.Arrays;
import java.util.Iterator;

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
 * {@link org.exist.dom.AVLTreeNodeSet}.
 *
 * Use this class, if you can either ensure that items are added in order, or
 * no calls to contains/get are required during the creation phase. Only after
 * a call to one of the iterator methods, the set will get sorted and
 * duplicates removed.
 *
 * @author Wolfgang <wolfgang@exist-db.org>
 * @since 0.9.3
 */
public class NewArrayNodeSet extends AbstractNodeSet implements ExtNodeSet, DocumentSet {

    private final static int INITIAL_DOC_SIZE = 64;

    private int documentIds[] = new int[16];
    private int documentOffsets[] = new int[16];
    private int documentLengths[] = new int[16];
    private int documentCount = 0;
    
    private NodeProxy nodes[];

    protected int size = 0;

    private boolean isSorted = false;

    private boolean hasOne = false;

    protected NodeProxy lastAdded = null;

    private int state = 0;

    private DocumentSet cachedDocuments = null;

    //  used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     */
    public NewArrayNodeSet() {
        nodes = new NodeProxy[INITIAL_DOC_SIZE];
    }

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     * @param initialDocsCount an <code>int</code> value
     * @param initialArraySize an <code>int</code> value
     */
    public NewArrayNodeSet(int initialDocsCount, int initialArraySize) {
        nodes = new NodeProxy[INITIAL_DOC_SIZE];
    }

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     * @param initialArraySize an <code>int</code> value
     */
    public NewArrayNodeSet(int initialArraySize) {
        nodes = new NodeProxy[INITIAL_DOC_SIZE];
    }

    /**
     * The method <code>reset</code>
     *
     */
    public void reset() {
        Arrays.fill(nodes, null);
        documentCount = 0;
        size = 0;
        isSorted = false;
        state = 0;
    }

    /**
     * The method <code>isEmpty</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isEmpty() {
    	return (size == 0);
    }

    /**
     * The method <code>hasOne</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasOne() {
    	return hasOne;
    }

    private void ensureCapacity() {
        if (size == nodes.length) {
            int nsize = size << 1;
            NodeProxy temp[] = new NodeProxy[nsize];
            System.arraycopy(nodes, 0, temp, 0, size);
            nodes = temp;
        }
    }

    /**
     * The method <code>add</code>
     *
     * @param proxy a <code>NodeProxy</code> value
     */
    public void add(NodeProxy proxy) {
    	if (size > 0) {
            if (hasOne) {
                if (isSorted) {
                    hasOne = get(proxy) != null;
                } else {
                    hasOne = lastAdded == null || lastAdded.compareTo(proxy) == 0;
                }
            }
    	} else {
            hasOne = true;
        }

        ensureCapacity();
        nodes[size++] = proxy;
        isSorted = false;
        setHasChanged();
        checkItemType(proxy.getType());

        lastAdded = proxy;
    }

    /**
     * Add a new node to the set. If a new array of nodes has to be allocated
     * for the document, use the sizeHint parameter to determine the size of
     * the newly allocated array. This will overwrite the default array size.
     *
     * If the size hint is correct, no further reallocations will be required.
     */
    public void add(NodeProxy proxy, int sizeHint) {
    	add(proxy);
    }

//    public void addAll(Sequence other) throws XPathException {
//        if (other instanceof AbstractNodeSet)
//            addAll((NodeSet) other);
//        else
//            super.addAll(other);
//    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
     */
    public void addAll(NodeSet other) {
        if (other.isEmpty())
            return;
        if (other.hasOne()) {
            add((NodeProxy) other.itemAt(0));
        } else {
            for (Iterator i = other.iterator(); i.hasNext();) {
                add((NodeProxy) i.next());
            }
        }
    }
    
    private void checkItemType(int type) {
        if(itemType == Type.NODE || itemType == type) {
            return;
        }
        if(itemType == Type.ANY_TYPE) {
            itemType = type;
        } else {
            itemType = Type.NODE;
        }
    }

    /**
     * The method <code>getItemType</code>
     *
     * @return an <code>int</code> value
     */
    public int getItemType() {
        return itemType;
    }

    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 0 : state + 1);
        cachedDocuments = null;
    }

    private int findDoc(DocumentImpl doc) {
        return findDoc(doc.getDocId());
    }

    private int findDoc(int docId) {
        int low = 0;
        int high = documentCount - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            int midVal = documentIds[mid];

            if (midVal < docId)
                low = mid + 1;
            else if (midVal > docId)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found.
    }

    /**
     * The method <code>getSizeHint</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @return an <code>int</code> value
     */
    public int getSizeHint(DocumentImpl doc) {
        if (!isSorted()) {
            sort();
        }
        int idx = findDoc(doc);
        return idx < 0 ? Constants.NO_SIZE_HINT : documentLengths[idx];
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.NodeSet#iterator()
     */
    public NodeSetIterator iterator() {
        if (!isSorted()) {
            sort();
        }
        return new NewArrayIterator();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.Sequence#iterate()
     */
    public SequenceIterator iterate() throws XPathException {
        sortInDocumentOrder();
        return new NewArrayIterator();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    public SequenceIterator unorderedIterator() throws XPathException {
        if (!isSorted()) {
            sort();
        }
        return new NewArrayIterator();
    }

    public ByDocumentIterator iterateByDocument() {
    	if (!isSorted()) {
            sort();
        }
    	return new NewDocIterator();
    }

    private NodeProxy get(int docIdx, NodeId nodeId) {
        if (!isSorted())
            sort();
        int low = documentOffsets[docIdx];
        int high = low + (documentLengths[docIdx] - 1);
        int mid, cmp;
        NodeProxy p;
        while (low <= high) {
            mid = (low + high) / 2;
            p = nodes[mid];
            cmp = p.getNodeId().compareTo(nodeId);
            if (cmp == 0) {
                return p;
            }
            if (cmp > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.Sequence#getLength()
     */
    public int getLength() {
        if (!isSorted())
            sort(); // sort to remove duplicates
        return size;
    }

    //TODO : evaluate both semantics
    public int getItemCount() {
        if (!isSorted())
            sort(); // sort to remove duplicates
        return size;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.w3c.dom.NodeList#item(int)
     */
    public Node item(int pos) {
        sortInDocumentOrder();
        NodeProxy p = get(pos);
        return p == null ? null : p.getNode();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.NodeSet#get(int)
     */
    public NodeProxy get(int pos) {
        if (pos < 0 || pos >= size)
            return null;
        return nodes[pos];
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
     */
    public boolean contains(NodeProxy proxy) {
        sort();
        int idx = findDoc(proxy.getDocument());
        if (idx < 0)
            return false;
        return get(idx, proxy.getNodeId()) != null;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
     */
    public NodeProxy get(NodeProxy proxy) {
        sort();
        int idx = findDoc(proxy.getDocument());
        if (idx < 0)
            return null;
        return get(idx, proxy.getNodeId());
    }

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        sort();
        int idx = findDoc(doc);
        if (idx < 0)
            return null;
        return get(idx, nodeId);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.Sequence#itemAt(int)
     */
    public Item itemAt(int pos) {
        sortInDocumentOrder();
        return get(pos);
    }

    /**
     * The method <code>getDescendantsInSet</code>
     *
     * @param al a <code>NodeSet</code> value
     * @param childOnly a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param mode an <code>int</code> value
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet getDescendantsInSet(NodeSet al, boolean childOnly, boolean includeSelf, int mode, int contextId,
                                       boolean copyMatches) {
        sort();
        NodeSet result = new NewArrayNodeSet();
		NodeProxy node;
        int docIdx;
        for (Iterator i = al.iterator(); i.hasNext(); ) {
			node = (NodeProxy) i.next();
            docIdx = findDoc(node.getDocument());
	        if (docIdx > -1) {
	        	getDescendantsInSet(docIdx, result, node, childOnly, includeSelf, mode, contextId, copyMatches);
            }
		}
		return result;
    }

    /**
     * Find all nodes in the current set being children or descendants of
     * the given parent node.
     *
     * @param result the node set to which matching nodes will be appended.
     * @param parent the parent node to search for.
     * @param childOnly only include child nodes, not descendant nodes
     * @param includeSelf include the self:: axis
     * @param mode
     * @param contextId
     */
    private NodeSet getDescendantsInSet(int docIdx, NodeSet result, NodeProxy parent, boolean childOnly,
                                boolean includeSelf, int mode, int contextId, boolean copyMatches) {
        NodeProxy p;
        NodeId parentId = parent.getNodeId();
        // document nodes are treated specially
        if (parentId == NodeId.DOCUMENT_NODE) {
            int end = documentOffsets[docIdx] + documentLengths[docIdx];
            for (int i = documentOffsets[docIdx]; i < end; i++) {
                boolean add;
                if (childOnly) {
                    add = nodes[i].getNodeId().getTreeLevel() == 1;
                } else if (includeSelf) {
                    add = true;
                } else {
                    add = nodes[i].getNodeId() != NodeId.DOCUMENT_NODE;
                }
                if (add) {
                    switch (mode) {
                        case NodeSet.DESCENDANT :
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                //array[i].addContextNode(contextId, parent);
                                nodes[i].deepCopyContext(parent, contextId);
                            } else {
                                nodes[i].copyContext(parent);
                            }
                            if (copyMatches)
                                nodes[i].addMatches(parent);
                            result.add(nodes[i]);
                            break;
                        case NodeSet.ANCESTOR :
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                //parent.addContextNode(contextId, array[i]);
                                parent.deepCopyContext(nodes[i], contextId);
                            } else {
                                parent.copyContext(nodes[i]);
                            }
                            if (copyMatches)
                                parent.addMatches(nodes[i]);
                            result.add(parent, 1);
                            break;
                    }
                }
            }
        } else {
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentOffsets[docIdx];
            int high = low + (documentLengths[docIdx] - 1);
            int end = low + documentLengths[docIdx];
            int mid = low;
            int cmp;
            while (low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
                if (p.getNodeId().isDescendantOrSelfOf(parentId)) {
                    break;	// found a child node, break out.
                }
                cmp = p.getNodeId().compareTo(parentId);
                if (cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            if (low > high) {
                return result; // no node found
            }
            // find the first child node in the range
            while (mid > documentOffsets[docIdx] && nodes[mid - 1].getNodeId().compareTo(parentId) > -1) {
                --mid;
            }
            // walk through the range of child nodes we found
            for (int i = mid; i < end; i++) {
                cmp = nodes[i].getNodeId().computeRelation(parentId);
                if (cmp > -1) {
                    boolean add = true;
                    if (childOnly) {
                        add = cmp == NodeId.IS_CHILD;
                    } else if (cmp == NodeId.IS_SELF) {
                        add = includeSelf;
                    }
                    if (add) {
                        switch (mode) {
                            case NodeSet.DESCENDANT :
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    //array[i].addContextNode(contextId, parent);
                                    nodes[i].deepCopyContext(parent, contextId);
                                } else {
                                    nodes[i].copyContext(parent);
                                }
                                if (copyMatches)
                                    nodes[i].addMatches(parent);
                                result.add(nodes[i]);
                                break;
                            case NodeSet.ANCESTOR :
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    //parent.addContextNode(contextId, array[i]);
                                    parent.deepCopyContext(nodes[i], contextId);
                                } else {
                                    parent.copyContext(nodes[i]);
                                }
                                if (copyMatches)
                                    parent.addMatches(nodes[i]);
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
     * The method <code>hasDescendantsInSet</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param ancestorId a <code>NodeId</code> value
     * @param includeSelf a <code>boolean</code> value
     * @param contextId an <code>int</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy hasDescendantsInSet(DocumentImpl doc, NodeId ancestorId, boolean includeSelf, int contextId,
                                         boolean copyMatches) {
        sort();
        int docIdx = findDoc(doc);
        if (docIdx < 0)
            return null;
        return hasDescendantsInSet(docIdx, ancestorId, contextId, includeSelf, copyMatches);
    }

    /**
     * The method <code>hasDescendantsInSet</code>
     *
     * @param ancestorId a <code>NodeId</code> value
     * @param contextId an <code>int</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy hasDescendantsInSet(int docIdx, NodeId ancestorId, int contextId, boolean includeSelf,
                                          boolean copyMatches) {
        // do a binary search to pick some node in the range of valid child
        // ids
        int low = documentOffsets[docIdx];
        int high = low + (documentLengths[docIdx] - 1);
        int end = low + documentLengths[docIdx];
        int mid = 0;
        int cmp;
        NodeId id;
        while (low <= high) {
            mid = (low + high) / 2;
            id = nodes[mid].getNodeId();
            if (id.isDescendantOrSelfOf(ancestorId)) {
                break;	// found a child node, break out.
            }
            cmp = id.compareTo(ancestorId);
            if (cmp > 0) {
                high = mid - 1;
            } else {
                low = mid + 1;
            }
        }
        if (low > high) {
            return null; // no node found
        }
        // find the first child node in the range
        while (mid > documentOffsets[docIdx] && nodes[mid - 1].getNodeId().compareTo(ancestorId) >= 0) {
            --mid;
        }
        NodeProxy ancestor = new NodeProxy(nodes[documentOffsets[docIdx]].getDocument(), ancestorId, Node.ELEMENT_NODE);
        // we need to check if self should be included
        boolean foundOne = false;
        for (int i = mid; i < end; i++) {
            cmp = nodes[i].getNodeId().computeRelation(ancestorId);
            if (cmp > -1) {
                boolean add = true;
                if (cmp == NodeId.IS_SELF) {
                    add = includeSelf;
                }

                if (add) {
                    if (Expression.NO_CONTEXT_ID != contextId) {
                        ancestor.deepCopyContext(nodes[i], contextId);
                    } else {
                        ancestor.copyContext(nodes[i]);
                    }
                    if (copyMatches)
                        ancestor.addMatches(nodes[i]);
                    foundOne = true;
                }
            } else {
                break;
            }
        }
        return foundOne ? ancestor : null;
    }

    /**
     * The method <code>selectParentChild</code>
     *
     * @param al a <code>NodeSet</code> value
     * @param mode an <code>int</code> value
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet selectParentChild(NodeSet al, int mode, int contextId) {
    	sort();
        if (al instanceof VirtualNodeSet)
            return super.selectParentChild(al, mode, contextId);
    	return getDescendantsInSet(al, true, false, mode, contextId, true);
    }

    private boolean isSorted() {
        return isSorted;
    }

    /**
     * The method <code>setSorted</code>
     *
     * @param document a <code>DocumentImpl</code> value
     * @param sorted a <code>boolean</code> value
     */
    public void setSorted(DocumentImpl document, boolean sorted) {
        // has to be ignored for this node set implementation
    }

    /**
     * Remove all duplicate nodes, but merge their
     * contexts.
     */
    public void mergeDuplicates() {
        sort(true);
    }

    /**
     * The method <code>sort</code>
     *
     */
    public void sort() {
        sort(false);
    }

    /**
     * The method <code>sort</code>
     *
     * @param mergeContexts a <code>boolean</code> value
     */
    public void sort(boolean mergeContexts) {
        if (isSorted)
            return;

        if (hasOne) {
            isSorted = true;    // shortcut: don't sort if there's just one item
            removeDuplicates(mergeContexts);
            updateDocs();
            return;
        }
        if (size > 0) {
            FastQSort.sort(nodes, 0, size - 1);
            removeDuplicates(mergeContexts);
        }
        updateDocs();
        isSorted = true;
    }

    private void updateDocs() {
        if (size == 1) {
            documentIds[0] = nodes[0].getDocument().getDocId();
            documentOffsets[0] = 0;
            documentLengths[0] = 1;
            documentCount = 1;
        } else {
            documentCount = 0;

            for (int i = 0; i < size; i++) {
                if (i == 0) {
                    // first document in the set
                    documentIds[0] = nodes[0].getDocument().getDocId();
                    documentOffsets[0] = 0;
                    documentLengths[0] = 1;
                    ++documentCount;
                } else if (documentIds[documentCount - 1] == nodes[i].getDocument().getDocId()) {
                    // node belongs to same document as previous node
                    ++documentLengths[documentCount - 1];
                } else {
                    // new document
                    ensureDocCapacity();
                    documentIds[documentCount] = nodes[i].getDocument().getDocId();
                    documentOffsets[documentCount] = i;
                    documentLengths[documentCount++] = 1;
                }
            }
        }
    }

    private void ensureDocCapacity() {
        if (documentCount == documentIds.length) {
            int nlen = documentCount << 1;
            int[] temp = new int[nlen];
            System.arraycopy(documentIds, 0, temp, 0, documentCount);
            documentIds = temp;

            temp = new int[nlen];
            System.arraycopy(documentOffsets, 0, temp, 0, documentCount);
            documentOffsets = temp;

            temp = new int[nlen];
            System.arraycopy(documentLengths, 0, temp, 0, documentCount);
            documentLengths = temp;
        }
    }

    /**
     * The method <code>sortInDocumentOrder</code>
     *
     */
    public final void sortInDocumentOrder() {
        sort(false);
    }

    /**
     * Remove all duplicate nodes from this set.
     *
     * @param mergeContext a <code>boolean</code> value
     * @return the new length of the part, after removing all duplicates
     */
    int removeDuplicates(boolean mergeContext) {
        int j = 0;
        for (int i = 1; i < size; i++) {
            if (nodes[i].compareTo(nodes[j]) != 0) {
                if (i != ++j) {
                    nodes[j] = nodes[i];
                }
            } else {
                if (mergeContext)
                    nodes[j].addContext(nodes[i]);
                nodes[j].addMatches(nodes[i]);
            }
        }
        size = ++j;
        return size;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.AbstractSequence#setSelfAsContext()
     */
    public void setSelfAsContext(int contextId) throws XPathException {
        for (int i = 0; i < size; i++) {
            nodes[i].addContextNode(contextId, nodes[i]);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectAncestorDescendant(org.exist.dom.NodeSet, int, boolean, boolean)
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode,
                                            boolean includeSelf, int contextId,
                                            boolean copyMatches) {
        sort();
        if (al instanceof VirtualNodeSet) {
            return super.selectAncestorDescendant(al, mode, includeSelf,
                                                  contextId, copyMatches);
        }
        return getDescendantsInSet(al, false, includeSelf, mode, contextId, copyMatches);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectPrecedingSiblings(NodeSet contextSet, int contextId) {
        sort();
        NodeSet result = new NewArrayNodeSet();
        for (Iterator it = contextSet.iterator(); it.hasNext();) {
            NodeProxy reference = (NodeProxy) it.next();
            NodeId parentId = reference.getNodeId().getParentId();
            int docIdx = findDoc(reference.getDocument());
            if (docIdx < 0)
                return null;
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentOffsets[docIdx];
            int high = low + (documentLengths[docIdx] - 1);
            int end = low + documentLengths[docIdx];
            int mid = low;
            int cmp;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
                if (p.getNodeId().isDescendantOf(parentId)) {
                    break;	// found a child node, break out.
                }
                cmp = p.getNodeId().compareTo(parentId);
                if (cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            if (low > high) {
                continue; // no node found
            }
            // find the first child node in the range
            while (mid < end && nodes[mid].getNodeId().isDescendantOf(parentId)) {
                ++mid;
            }
            --mid;
            NodeId refId = reference.getNodeId();
            for (int i = mid; i >= documentOffsets[docIdx]; i--) {
                NodeId currentId = nodes[i].getNodeId();
                if (!currentId.isDescendantOf(parentId))
                    break;
                if (currentId.getTreeLevel() == refId.getTreeLevel() && currentId.compareTo(refId) < 0) {
                    if (Expression.IGNORE_CONTEXT != contextId) {
                        if (Expression.NO_CONTEXT_ID == contextId) {
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
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet selectFollowingSiblings(NodeSet contextSet, int contextId) {
        sort();
        NodeSet result = new NewArrayNodeSet();
        for (Iterator it = contextSet.iterator(); it.hasNext();) {
            NodeProxy reference = (NodeProxy) it.next();
            NodeId parentId = reference.getNodeId().getParentId();
            int docIdx = findDoc(reference.getDocument());
            if (docIdx < 0)
                return null; //BUG: can't be null, make trouble @LocationStep line 388 -shabanovd 
            // do a binary search to pick some node in the range of valid
            // child ids
            int low = documentOffsets[docIdx];
            int high = low + (documentLengths[docIdx] - 1);
            int end = low + documentLengths[docIdx];
            int mid = low;
            int cmp;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = nodes[mid];
                if (p.getNodeId().isDescendantOf(parentId)) {
                    break;	// found a child node, break out.
                }
                cmp = p.getNodeId().compareTo(parentId);
                if (cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            if (low > high) {
                continue; // no node found
            }
            // find the first child node in the range
            while (mid > documentOffsets[docIdx] && nodes[mid - 1].getNodeId().compareTo(parentId) > -1) {
                --mid;
            }
            NodeId refId = reference.getNodeId();
            for (int i = mid; i < end; i++) {
                NodeId currentId = nodes[i].getNodeId();
                if (!currentId.isDescendantOf(parentId))
                    break;
                if (currentId.getTreeLevel() == refId.getTreeLevel() && currentId.compareTo(refId) > 0) {
                    if (Expression.IGNORE_CONTEXT != contextId) {
                        if (Expression.NO_CONTEXT_ID == contextId) {
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

    public NodeSet selectFollowing(NodeSet fl, int contextId) throws XPathException {
        return selectFollowing(fl, -1, contextId);
    }

    public NodeSet selectFollowing(NodeSet pl, int position, int contextId) throws XPathException, UnsupportedOperationException {
        sort();
        NodeSet result = new NewArrayNodeSet();
        for (Iterator it = pl.iterator(); it.hasNext(); ) {
            NodeProxy reference = (NodeProxy) it.next();
            int idx = findDoc(reference.getDocument());
            if (idx < 0)
                continue;
            int i = documentOffsets[idx];
            for (; i < size; i++) {
                if (nodes[i].getDocument().getDocId() != reference.getDocument().getDocId() ||
                        (nodes[i].compareTo(reference) > 0 && !nodes[i].getNodeId().isDescendantOf(reference.getNodeId())))
                    break;
            }
            int n = 0;
            for (int j = i; j < size; j++) {
                if (nodes[j].getDocument().getDocId() != reference.getDocument().getDocId())
                    break;
                if (!reference.getNodeId().isDescendantOf(nodes[j].getNodeId())) {
                    if (position < 0 || ++n == position) {
                        if (Expression.IGNORE_CONTEXT != contextId) {
                            if (Expression.NO_CONTEXT_ID == contextId) {
                                nodes[j].copyContext(reference);
                            } else {
                                nodes[j].addContextNode(contextId, reference);
                            }
                        }
                        result.add(nodes[j]);
                    }
                    if (n == position)
                        break;
                }
            }
        }
        return result;
    }

    public NodeSet selectPreceding(NodeSet pl, int contextId) throws XPathException {
        return selectPreceding(pl, -1, contextId);
    }

    public NodeSet selectPreceding(NodeSet pl, int position, int contextId) throws XPathException, UnsupportedOperationException {
        sort();
        NodeSet result = new NewArrayNodeSet();
        for (Iterator it = pl.iterator(); it.hasNext(); ) {
            NodeProxy reference = (NodeProxy) it.next();
            int idx = findDoc(reference.getDocument());
            if (idx < 0)
                continue;
            int i = documentOffsets[idx];
            // TODO: check document id
            for (; i < size; i++) {
                if (nodes[i].compareTo(reference) >= 0)
                    break;
            }
            --i;
            int n = 0;
            for (int j = i; j >= documentOffsets[idx]; j--) {
                if (!reference.getNodeId().isDescendantOf(nodes[j].getNodeId())) {
                    if (position < 0 || ++n == position) {
                        if (Expression.IGNORE_CONTEXT != contextId) {
                            if (Expression.NO_CONTEXT_ID == contextId) {
                                nodes[j].copyContext(reference);
                            } else {
                                nodes[j].addContextNode(contextId, reference);
                            }
                        }
                        result.add(nodes[j]);
                    }
                    if (n == position)
                        break;
                }
            }
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectAncestors(org.exist.dom.NodeSet, boolean, boolean)
     */
    public NodeSet selectAncestors(NodeSet al, boolean includeSelf, int contextId) {
        sort();
        return super.selectAncestors(al, includeSelf, contextId);
    }

    /**
     * The method <code>parentWithChild</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param directParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
        sort();
        int docIdx = findDoc(doc);
        if (docIdx < 0)
            return null;
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
     * @param nodeId a <code>NodeId</code> value
     * @param directParent a <code>boolean</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeProxy</code> value
     */
    private NodeProxy parentWithChild(int docIdx, NodeId nodeId, boolean directParent, boolean includeSelf) {
        NodeProxy temp;
        if (includeSelf && (temp = get(docIdx, nodeId)) != null) {
            return temp;
        }
        nodeId = nodeId.getParentId();
        while (nodeId != null) {
            if ((temp = get(docIdx, nodeId)) != null) {
                return temp;
            } else if (directParent) {
                return null;
            }
            nodeId = nodeId.getParentId();
        }
        return null;
    }

    /**
     * The method <code>debugParts</code>
     *
     * @return a <code>String</code> value
     */
    public String debugParts() {
    	StringBuilder buf = new StringBuilder();
    	for (int i = 0; i < documentCount; i++) {
    		buf.append(documentIds[i]);
    		buf.append(' ');
    	}
    	return buf.toString();
    }

    public int getIndexType() {
        //Is the index type initialized ?
        if (indexType == Type.ANY_TYPE) {
            hasTextIndex = true;
            hasMixedContent = true;
            for (int i = 0; i < size; i++) {
                NodeProxy node = nodes[i];
                if (node.getDocument().getCollection().isTempCollection()) {
                    //Temporary nodes return default values
                    indexType = Type.ITEM;
                    hasTextIndex = false;
                    hasMixedContent = false;
                    break;
                }
                int nodeIndexType = node.getIndexType();
                //Refine type
                //TODO : use common subtype
                if (indexType == Type.ANY_TYPE) {
                    indexType = nodeIndexType;
                } else {
                    //Broaden type
                    //TODO : use common supertype
                    if (indexType != nodeIndexType)
                        indexType = Type.ITEM;
                }
                if(!node.hasTextIndex()) {
                    hasTextIndex = false;
                }
                if(!node.hasMixedContent()) {
                    hasMixedContent = false;
                }
            }
        }
        return indexType;
    }

    public void clearContext(int contextId) throws XPathException {
        for (int i = 0; i < size; i++) {
            nodes[i].clearContext(contextId);
        }
    }

    /**
     * The method <code>getDocumentSet</code>
     *
     * @return a <code>DocumentSet</code> value
     */
    public DocumentSet getDocumentSet() {
        return this;
//        if(cachedDocuments != null)
//            return cachedDocuments;
//        sort();
//        cachedDocuments = new DefaultDocumentSet(documentCount);
//        for (int i = 0; i < documentCount; i++) {
//            cachedDocuments.add(nodes[documentOffsets[i]].getDocument(), false);
//        }
//        isSorted = true;
//        return cachedDocuments;
    }

    /**
     * The method <code>setDocumentSet</code>
     *
     * @param docs a <code>DocumentSet</code> value
     */
    public void setDocumentSet(DocumentSet docs) {
    	cachedDocuments = docs;
    }

    // DocumentSet methods

    public Iterator getDocumentIterator() {
        sort();
        return new DocumentIterator();
    }

    private class DocumentIterator implements Iterator {

        int currentDoc = 0;

        public boolean hasNext() {
            return currentDoc < documentCount;
        }

        public Object next() {
            if (currentDoc == documentCount)
                return null;
            else
                return nodes[documentOffsets[currentDoc++]].getDocument();
        }

        public void remove() {
        }
    }
    
    public boolean equalDocs(DocumentSet other) {
		if (this == other)
			// we are comparing the same objects
			return true;
        sort();
        if (documentCount != other.getDocumentCount())
			return false;
        for (int i = 0; i < documentCount; i++) {
			if (!other.contains(documentIds[i]))
				return false;
        }
        return true;
	}

    public int getDocumentCount() {
        sort();
        return documentCount;
    }

    public DocumentImpl getDocumentAt(int pos) {
        sort();
        if (pos < 0 || pos >= documentCount)
            return null;
        return nodes[documentOffsets[pos]].getDocument();
    }

    public DocumentImpl getDoc(int docId) {
        sort();
        int idx = findDoc(docId);
        if (idx < 0)
            return null;
        return nodes[documentOffsets[idx]].getDocument();
    }

    public XmldbURI[] getNames() {
        sort();
        XmldbURI[] uris = new XmldbURI[documentCount];
        for (int i = 0; i < documentCount; i++) {
            uris[i] = nodes[documentOffsets[i]].getDocument().getURI();
        }
        return uris;
    }

    public DocumentSet intersection(DocumentSet other) {
        sort();
        DefaultDocumentSet r = new DefaultDocumentSet();
		DocumentImpl d;
        for (int i = 0; i < documentCount; i++) {
            d = nodes[documentOffsets[i]].getDocument();
			if (other.contains(d.getDocId()))
				r.add(d);
        }
		for (Iterator i = other.getDocumentIterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (contains(d.getDocId()) && (!r.contains(d.getDocId())))
				r.add(d);
		}
		return r;
    }

    public boolean contains(DocumentSet other) {
        sort();
        if (other.getDocumentCount() > documentCount)
			return false;
		DocumentImpl d;
		for (Iterator i = other.getDocumentIterator(); i.hasNext();) {
			d = (DocumentImpl) i.next();
			if (!contains(d.getDocId()))
				return false;
		}
		return true;
    }

    public boolean contains(int docId) {
        sort();
        return findDoc(docId) > -1;
    }

    public NodeSet docsToNodeSet() {
        sort();
        NodeSet result = new NewArrayNodeSet(documentCount);
		DocumentImpl doc;
        for (int i = 0; i < documentCount; i++) {
            doc = nodes[documentOffsets[i]].getDocument();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
            	result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException {
        sort();
        DocumentImpl d;
	    Lock dlock;
        for (int idx = 0; idx < documentCount; idx++) {
	        d = nodes[documentOffsets[idx]].getDocument();
            dlock = d.getUpdateLock();
            if (exclusive)
                dlock.acquire(Lock.WRITE_LOCK);
            else
                dlock.acquire(Lock.READ_LOCK);
        }
    }

    public void unlock(boolean exclusive) {
        sort();
        DocumentImpl d;
	    Lock dlock;
        final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < documentCount; idx++) {
	        d = nodes[documentOffsets[idx]].getDocument();
	        dlock = d.getUpdateLock();
            if(exclusive)
                dlock.release(Lock.WRITE_LOCK);
            else if (dlock.isLockedForRead(thread))
                dlock.release(Lock.READ_LOCK);
        }
    }

    /**
     * The method <code>getCollectionIterator</code>
     *
     * @return an <code>Iterator</code> value
     */
    public Iterator getCollectionIterator() {
        sort();
        return new CollectionIterator();
    }

    /**
     * The class <code>CollectionIterator</code>
     *
     */
    private class CollectionIterator implements Iterator {

        Iterator iterator = null;

        CollectionIterator() {
            if (documentCount > 0) {
                ObjectHashSet collections = new ObjectHashSet();

                for (int i = 0; i < documentCount; i++) {
                    collections.add(nodes[documentOffsets[i]].getDocument().getCollection());
                }
                iterator = collections.iterator();
            }
        }

        /**
         * The method <code>hasNext</code>
         *
         * @return a <code>boolean</code> value
         */
        public boolean hasNext() {
            return iterator != null && iterator.hasNext();
        }

        /**
         * The method <code>next</code>
         *
         * @return an <code>Object</code> value
         */
        public Object next() {
            return iterator.next();
        }

        /**
         * The method <code>remove</code>
         *
         */
        public void remove() {
            // not needed
            throw new IllegalStateException();
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.AbstractNodeSet#hasChanged(int)
     */
    public boolean hasChanged(int previousState) {
        return state != previousState;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.dom.AbstractNodeSet#getState()
     */
    public int getState() {
        return state;
    }

    public boolean isCacheable() {
        return true;
    }

    /**
     * The method <code>toString</code>
     *
     * @return a <code>String</code> value
     */
    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("ExtArrayTree#").append(super.toString());
        return result.toString();
    }

    /**
     * The class <code>ExtArrayIterator</code>
     *
     */
    private class NewArrayIterator implements NodeSetIterator, SequenceIterator {

        int pos = 0;

        /**
         * Creates a new <code>ExtArrayIterator</code> instance.
         *
         */
        NewArrayIterator() {
        }

        /**
         * The method <code>setPosition</code>
         *
         * @param proxy a <code>NodeProxy</code> value
         */
        public void setPosition(NodeProxy proxy) {
            int docIdx = findDoc(proxy.getDocument());
            if (docIdx > -1) {
                int low = documentOffsets[docIdx];
                int high = low + (documentLengths[docIdx] - 1);
                int mid, cmp;
                NodeProxy p;
                while (low <= high) {
                    mid = (low + high) / 2;
                    p = nodes[mid];
                    cmp = p.getNodeId().compareTo(proxy.getNodeId());
                    if (cmp == 0) {
                        pos = mid;
                        return;
                    }
                    if (cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
            }
            pos = -1;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return pos < size && pos > -1;
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#next()
         */
        public Object next() {
            if (pos == size || pos < 0) {
                pos = -1;
                return null;
            }
            return nodes[pos++];
        }

        public NodeProxy peekNode() {
            if (pos == size || pos < 0) {
                pos = -1;
                return null;
            }
            return nodes[pos];
        }

        /*
         * (non-Javadoc)
         *
         * @see org.exist.xquery.value.SequenceIterator#nextItem()
         */
        public Item nextItem() {
            return (Item) next();
        }

        /*
         * (non-Javadoc)
         *
         * @see java.util.Iterator#remove()
         */
        public void remove() {
        }
    }

    /**
     * The class <code>ExtDocIterator</code>
     *
     */
    private class NewDocIterator implements ByDocumentIterator {

        int docIdx = 0;
        int pos = 0;
        NodeProxy next = null;

        /**
         * Creates a new <code>ExtDocIterator</code> instance.
         *
         */
        public NewDocIterator() {
            if (documentCount > 0) {
                next = nodes[0];
            }
        }

    	/**
         * The method <code>nextDocument</code>
         *
         * @param document a <code>DocumentImpl</code> value
         */
        public void nextDocument(DocumentImpl document) {
            docIdx = findDoc(document);
            next = null;
            if (docIdx > -1) {
                pos = 0;
                next = nodes[documentOffsets[docIdx]];
            }
        }

    	/**
         * The method <code>hasNextNode</code>
         *
         * @return a <code>boolean</code> value
         */
        public boolean hasNextNode() {
            return next != null;
    	}

    	/**
         * The method <code>nextNode</code>
         *
         * @return a <code>NodeProxy</code> value
         */
        public NodeProxy nextNode() {
            if (next == null) {
                return null;
            }
            NodeProxy n = next;
            next = null;
            if (++pos < documentLengths[docIdx]) {
            	next = nodes[documentOffsets[docIdx] + pos];
            }
            return n;
    	}
        
    	/**
         * The method <code>peekNode</code>
         *
         * @return a <code>NodeProxy</code> value
         */
        public NodeProxy peekNode() {
            return next;
        }

        /**
         * The method <code>setPosition</code>
         *
         * @param node a <code>NodeProxy</code> value
         */
        public void setPosition(NodeProxy node) {
            next = null;
            docIdx = findDoc(node.getDocument());
            if (docIdx > -1) {
                int low = documentOffsets[docIdx];
                int high = low + (documentLengths[docIdx] - 1);
                int mid, cmp;
                NodeProxy p;
                while (low <= high) {
                    mid = (low + high) / 2;
                    p = nodes[mid];
                    cmp = p.getNodeId().compareTo(node.getNodeId());
                    if (cmp == 0) {
                        pos = mid - documentOffsets[docIdx];
                        return;
                    }
                    if (cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
                }
            }
        }
    }
}
