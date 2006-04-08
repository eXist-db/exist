/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.dom;

import java.util.Arrays;
import java.util.Iterator;

import org.exist.numbering.NodeId;
import org.exist.util.ArrayUtils;
import org.exist.util.FastQSort;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

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
public class ExtArrayNodeSet extends AbstractNodeSet {

    private final static int INITIAL_DOC_SIZE = 64;
    
    private int documentIds[];
    private Part parts[];
    
    protected int initalSize = 128;
    protected int size = 0;
    
    private int partCount = 0;
    
    private boolean isSorted = false;
    
    protected int lastDoc = -1;
    protected Part lastPart = null;
    
    private int state = 0;

    private DocumentSet cachedDocuments = null;
    
    //  used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;
    
    public ExtArrayNodeSet() {
        documentIds = new int[INITIAL_DOC_SIZE];
        parts = new Part[INITIAL_DOC_SIZE];
        Arrays.fill(documentIds, 0);
    }

    public ExtArrayNodeSet(int initialDocsCount, int initialArraySize) {
        initalSize = initialArraySize;
        documentIds = new int[initialDocsCount];
        parts = new Part[initialDocsCount];
        Arrays.fill(documentIds, 0);
    }

    public ExtArrayNodeSet(int initialArraySize) {
        initalSize = initialArraySize;
        documentIds = new int[INITIAL_DOC_SIZE];
        parts = new Part[INITIAL_DOC_SIZE];
        Arrays.fill(documentIds, 0);
    }

    protected Part getPart(DocumentImpl doc, boolean create, int sizeHint) {
        if (lastPart != null && doc.getDocId() == lastDoc)
            return lastPart;
        int idx = ArrayUtils.binarySearch(documentIds, doc.getDocId(), partCount);
        Part part = null;
        if (idx >= 0) {
            part = parts[idx];
        } else if (create) {
            idx = - (idx + 1);
            part = new Part(sizeHint, doc);
            insertPart(doc.getDocId(), part, idx);
        }
        return part;
    }

    public void reset() {
        for (int i = 0; i < partCount; i++) {
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

    public boolean containsDoc(DocumentImpl doc) {
        return ArrayUtils.binarySearch(documentIds, doc.getDocId(), partCount) > -1;
    }
    
    private void insertPart(int docId, Part part, int idx) {
        if (partCount == parts.length) {
            int ndocs[] = new int[documentIds.length * 2];
            System.arraycopy(documentIds, 0, ndocs, 0, documentIds.length);
            Arrays.fill(documentIds, -1);
            
            Part nparts[] = new Part[parts.length * 2];
            System.arraycopy(parts, 0, nparts, 0, parts.length);
            
            documentIds = ndocs;
            parts = nparts;
        }
        
        if (idx == partCount) {
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
    
    public void add(NodeProxy proxy) {
        getPart(proxy.getDocument(), true, initalSize).add(proxy);
        ++size;
        isSorted = false;
        setHasChanged();
        checkItemType(proxy.getType());
    }

    /**
     * Add a new node to the set. If a new array of nodes has to be allocated
     * for the document, use the sizeHint parameter to determine the size of
     * the newly allocated array. This will overwrite the default array size.
     * 
     * If the size hint is correct, no further reallocations will be required.
     */
    public void add(NodeProxy proxy, int sizeHint) {
        getPart(proxy.getDocument(), true, sizeHint != Constants.NO_SIZE_HINT ? sizeHint : initalSize).add(
                proxy);
        ++size;
        isSorted = false;
        setHasChanged();
        checkItemType(proxy.getType());
    }

    private void checkItemType(int type) {
        if(itemType == Type.NODE || itemType == type)
            return;
        if(itemType == Type.ANY_TYPE)
            itemType = type;
        else
            itemType = Type.NODE;
    }
    
    public int getItemType() {
        return itemType;
    }
    
    private void setHasChanged() {
        state = (state == Integer.MAX_VALUE ? state = 0 : state + 1);
        cachedDocuments = null;
    }

    public int getSizeHint(DocumentImpl doc) {
        Part part = getPart(doc, false, 0);
        return part == null ? Constants.NO_SIZE_HINT : part.length;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.NodeSet#iterator()
     */
    public NodeSetIterator iterator() {
        if (!isSorted())
            sort();
        return new ExtArrayIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.value.Sequence#iterate()
     */
    public SequenceIterator iterate() {
        sortInDocumentOrder();
        return new ExtArrayIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
     */
    public SequenceIterator unorderedIterator() {
        if (!isSorted())
            sort();
        return new ExtArrayIterator();
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
     */
    public boolean contains(NodeProxy proxy) {
        final Part part = getPart(proxy.getDocument(), false, 0);
        return part == null ? false : part.contains(proxy.getNodeId());
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
     */
    public void addAll(NodeSet other) {
        if (other.getLength() == 0)
            return;
        if (other.getLength() == 1) {
            add((NodeProxy) other.itemAt(0));
        } else {
            for (Iterator i = other.iterator(); i.hasNext();) {
                add((NodeProxy) i.next());
            }
        }
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
        int count = 0;
        Part part;
        for (int i = 0; i < partCount; i++) {
            part = parts[i];
            if (count + part.length > pos)
                return part.get(pos - count);
            count += part.length;
        }
        return null;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
     */
    public NodeProxy get(NodeProxy p) {
        final Part part = getPart(p.getDocument(), false, 0);
        return part == null ? null : part.get(p.getNodeId());
    }

    public NodeProxy get(DocumentImpl doc, NodeId nodeId) {
        sort();
        final Part part = getPart(doc, false, 0);
        return part == null ? null : part.get(nodeId);
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

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.dom.NodeSet#remove(org.exist.dom.NodeProxy)
     */
    public void remove(NodeProxy node) {
        final Part part = getPart(node.getDocument(), false, 0);
        if (part == null)
            return;
        part.remove(node);
        setHasChanged();
    }

    public void getRange(NodeSet result, DocumentImpl doc, long lower, long upper) {
        final Part part = getPart(doc, false, 0);
        part.getRange(result, lower, upper);
    }

    public NodeSet hasChildrenInSet(NodeSet al, int mode, int contextId) {
    	NodeSet result = new ExtArrayNodeSet();
		NodeProxy node;
		Part part;
		for (Iterator i = al.iterator(); i.hasNext(); ) {
			node = (NodeProxy) i.next();
			part = getPart(node.getDocument(), false, 0);
	        if (part != null)
	        	part.getChildrenInSet(result, node, mode, contextId);
		}
		return result;
    }

    private boolean isSorted() {
        return isSorted;
    }
    
    /**
     * Remove all duplicate nodes, but merge their
     * contexts.
     */
    public void mergeDuplicates() {
        sort(true);
    }
    
    public void sort() {
        sort(false);
    }
    
    public void sort(boolean mergeContexts) {
//              long start = System.currentTimeMillis();
        if (isSorted)
            return;
        Part part;
        size = 0;
        for (int i = 0; i < partCount; i++) {
            part = parts[i];
            part.sort();
            size += part.removeDuplicates(mergeContexts);
        }
        isSorted = true;
//              System.out.println("sort took " + (System.currentTimeMillis() -
//       start) + "ms.");
    }

    public final void sortInDocumentOrder() {
        sort(false);        
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.exist.xquery.value.AbstractSequence#setSelfAsContext()
     */
    public void setSelfAsContext(int contextId) {
        for (int i = 0; i < partCount; i++) {
            parts[i].setSelfAsContext(contextId);
        }
    }

    public NodeSet selectParentChild(NodeSet al, int mode, int contextId) {
        sort();
        return super.selectParentChild(al, mode, contextId);
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectAncestorDescendant(org.exist.dom.NodeSet, int, boolean, boolean)
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode,
            boolean includeSelf, int contextId) {
        sort();
        return super.selectAncestorDescendant(al, mode, includeSelf,
                contextId);
    }
    
    
    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId) {
        sort();
        return super.selectPrecedingSiblings(siblings, contextId);
    }
    
    public NodeSet selectFollowingSiblings(NodeSet siblings, int contextId) {
        sort();
        return super.selectFollowingSiblings(siblings, contextId);
    }    
    
    
    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#selectAncestors(org.exist.dom.NodeSet, boolean, boolean)
     */
    public NodeSet selectAncestors(NodeSet al, boolean includeSelf, int contextId) {
        sort();
        return super.selectAncestors(al, includeSelf, contextId);
    }
    
    public NodeProxy parentWithChild(DocumentImpl doc, long gid, boolean directParent, boolean includeSelf, 
            int level) {
        return null;
    }

    public NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
        sort();
        lastPart = getPart(doc, false, initalSize);
        return lastPart == null ? null : lastPart.parentWithChild(doc, nodeId, directParent, includeSelf);
    }

    public String debugParts() {
    	StringBuffer buf = new StringBuffer();
    	for (int i = 0; i < partCount; i++) {
    		buf.append(documentIds[i]);
    		buf.append(' ');
    	}
    	return buf.toString();
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getIndexType()
     */
    public int getIndexType() {
    	if(indexType == Type.ANY_TYPE) {
		    hasTextIndex = true;
		    hasMixedContent = false;
		    
		    for (int i = 0; i < partCount; i++) {
		    	parts[i].determineIndexType();
		    }
    	}
    	return indexType;
    }
    
    public DocumentSet getDocumentSet() {
        if(cachedDocuments != null)
            return cachedDocuments;
        cachedDocuments = new DocumentSet(partCount);
        sort();
        for (int i = 0; i < partCount; i++) {
            cachedDocuments.add(parts[i].getDocument(), false);
        }
        isSorted = true;
        return cachedDocuments;
    }

    public void setDocumentSet(DocumentSet docs) {
    	cachedDocuments = docs;
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
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("ExtArrayTree#").append(super.toString());
        return result.toString();
    }  
    
    private final class Part {

        private NodeProxy array[];
        private int length = 0;

        Part(int initialSize, DocumentImpl myDoc) {
            array = new NodeProxy[initialSize];
        }

        void add(NodeProxy p) {
            // just check if this node has already been added. We only
            // check the last entry, which should avoid most of the likely
            // duplicates. The remaining duplicates are removed by
            // removeDuplicates().
            if (length > 0 && array[length - 1].getNodeId().equals(p.getNodeId())) {
                return;
            }
            if (length == array.length) {
                //int newLength = (length * 3)/2 + 1;
                final int newLength = length << 1;
                NodeProxy temp[] = new NodeProxy[newLength];
                System.arraycopy(array, 0, temp, 0, length);
                array = temp;
            }
            array[length++] = p;
        }

        boolean contains(NodeId nodeId) {
            return get(nodeId) != null;
        }

        NodeProxy get(int pos) {
            return array[pos];
        }

        NodeProxy get(long gid) {
            int low = 0;
            int high = length - 1;
            int mid;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                if (p.getGID() == gid)
                    return p;
                if (p.getGID() > gid)
                    high = mid - 1;
                else
                    low = mid + 1;
            }
            return null;
        }

        NodeProxy get(NodeId nodeId) {
            int low = 0;
            int high = length - 1;
            int mid, cmp;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                cmp = p.getNodeId().compareTo(nodeId);
                if (cmp == 0)
                    return p;
                if (cmp > 0)
                    high = mid - 1;
                else
                    low = mid + 1;
            }
            return null;
        }

        DocumentImpl getDocument() {
            if(length == 0)
                return null;
            return array[0].getDocument();
        }
        
        void sort() {
            FastQSort.sortByNodeId(array, 0, length - 1);
        }

        void sortInDocumentOrder() {
            sort();
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
         */
        NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
            NodeProxy temp;
            if (includeSelf && (temp = get(nodeId)) != null)
                return temp;
            nodeId = nodeId.getParentId();
            while (nodeId != null) {
                if ((temp = get(nodeId)) != null) {
                    return temp;
                } else if (directParent)
                    return null;
                nodeId = nodeId.getParentId();
            }
            return null;
        }

        /**
         * Find all nodes in the current set being children of the specified
         * parent.
         * 
         * @param parent
         * @param mode
         * @param rememberContext
         * @return
         */
        NodeSet getChildrenInSet(NodeSet result, NodeProxy parent, int mode, int contextId) {
            // get the range of node ids reserved for children of the parent
            // node
            int low = 0;
            int high = length - 1;
            int mid = 0;
            int cmp;
            NodeProxy p;
            NodeId parentId = parent.getNodeId();
            // do a binary search to pick some node in the range of valid child
            // ids
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                if (p.getNodeId().isChildOf(parentId))
                	break;	// found a child node, break out.
                
                cmp = p.getNodeId().compareTo(parentId);
                if (cmp > 0)
                    high = mid - 1;
                else
                    low = mid + 1;
            }
            if (low > high)
                return result; // no node found
            // find the first child node in the range
            while (mid > 0 && array[mid - 1].getNodeId().compareTo(parentId) > 0)
                --mid;
            // walk through the range of child nodes we found
            for (int i = mid; i < length && array[i].getNodeId().isChildOf(parentId); i++) {
                switch (mode) {
                    case NodeSet.DESCENDANT :
                        if (Expression.NO_CONTEXT_ID != contextId)
                            array[i].addContextNode(contextId, parent);
                        else
                            array[i].copyContext(parent);
                        array[i].addMatches(parent);
                        result.add(array[i]);
                        break;
                    case NodeSet.ANCESTOR :
                        if (Expression.NO_CONTEXT_ID != contextId)
                            parent.addContextNode(contextId, array[i]);
                        else
                            parent.copyContext(array[i]);
                        parent.addMatches(array[i]);
                        result.add(parent, 1);
                        break;
                }
            }
            return result;
        }

        NodeSet getRange(NodeSet result, long lower, long upper) {
            int low = 0;
            int high = length - 1;
            int mid = 0;
            NodeProxy p;
            // do a binary search to pick some node in the range of valid node
            // ids
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                if (p.getGID() >= lower && p.getGID() <= upper)
                    break; // found a node, break out
                if (p.getGID() > lower)
                    high = mid - 1;
                else
                    low = mid + 1;
            }
            if (low > high)
                return result; // no node found
            // find the first child node in the range
            while (mid > 0 && array[mid - 1].getGID() >= lower)
                --mid;
            for (int i = mid; i < length && array[i].getGID() <= upper; i++) {
                result.add(array[i]);
            }
            return result;
        }

        void remove(NodeProxy node) {
            int low = 0;
            int high = length - 1;
            int mid = -1;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
                if (p.getGID() == node.getGID())
                    break;
                if (p.getGID() > node.getGID())
                    high = mid - 1;
                else
                    low = mid + 1;
            }
            if (low > high)
                return; // not found
            if (mid < length - 1)
                System.arraycopy(array, mid + 1, array, mid, length - mid - 1);
            --length;
        }

        /**
         * Remove all duplicate nodes from this part.
         * 
         * @return the new length of the part, after removing all duplicates
         */
        int removeDuplicates(boolean mergeContext) {
            int j = 0;
            for (int i = 1; i < length; i++) {
                if (!array[i].getNodeId().equals(array[j].getNodeId())) {
                    if (i != ++j)
                        array[j] = array[i];
                } else if (mergeContext) {
                    array[j].addContext(array[i]);
                }
            }
            length = ++j;
            return length;
        }

        void determineIndexType() {
        	int type;
		    NodeProxy p;
		    for (int i = 0; i < length; i++) {
		    	p = array[i];
		    	if (p.getDocument().getCollection().isTempCollection()) {
                    indexType = Type.ITEM;
                    hasTextIndex = false;
                    break;
                }
			    type = p.getIndexType();
				if(indexType == Type.ANY_TYPE)
				    indexType = type;
				else if(indexType != type) {
                    if (indexType != Type.ITEM)
                        LOG.debug("Found: " + Type.getTypeName(type) + "; node = " + p.toString());
				    indexType = Type.ITEM;
				}
				if(!p.hasTextIndex()) {
				    hasTextIndex = false;
				}
				if(p.hasMixedContent()) {
				    hasMixedContent = true;
				}
		    }
        }
        
        void setSelfAsContext(int contextId) {
            for (int i = 0; i < length; i++) {
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
            if (partPos < partCount)
                currentPart = parts[partPos];
            if (currentPart != null && currentPart.length > 0)
                next = currentPart.get(0);
        }

        public void setPosition(NodeProxy proxy) {
            partPos = ArrayUtils.binarySearch(documentIds, proxy.getDocument().getDocId(), partCount);
            if (partPos >= 0) {
                currentPart = parts[partPos];
                int low = 0;
                int high = currentPart.length - 1;
                int mid;
                NodeProxy p;
                while (low <= high) {
                    mid = (low + high) / 2;
                    p = currentPart.array[mid];
                    if (p.getGID() == proxy.getGID()) {
                        pos = mid;
                        next = p;
                        return;
                    }
                    if (p.getGID() > proxy.getGID())
                        high = mid - 1;
                    else
                        low = mid + 1;
                }
            }
            next = null;
        }
        
        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#hasNext()
         */
        public boolean hasNext() {
            return next != null;
        }

        /*
         * (non-Javadoc)
         * 
         * @see java.util.Iterator#next()
         */
        public Object next() {
            if (next == null)
                return null;
            NodeProxy n = next;
            next = null;
            if (++pos == currentPart.length) {
                if (++partPos < partCount) {
                    currentPart = parts[partPos];
                    if (currentPart != null && currentPart.length > 0) {
                        next = currentPart.get(0);
                        pos = 0;
                    }
                }
            } else
                next = currentPart.get(pos);
            return n;
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
}
