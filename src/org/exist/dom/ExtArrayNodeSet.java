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
 *  $Id$
 */
package org.exist.dom;

import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.util.ArrayUtils;
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
public class ExtArrayNodeSet extends AbstractNodeSet implements DocumentSet {

    private final static int INITIAL_DOC_SIZE = 64;
    
    private int documentIds[];
    private Part parts[];
    
    protected int initalSize = 128;
    protected int size = 0;
    
    private int partCount = 0;
    
    private boolean isSorted = false;
    
    private boolean hasOne = false;
    
    protected int lastDoc = -1;
    protected Part lastPart = null;
    protected NodeProxy lastAdded = null;
    
    private int state = 0;

    private MutableDocumentSet cachedDocuments = null;
    
    //  used to keep track of the type of added items.
    private int itemType = Type.ANY_TYPE;
    
    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     */
    public ExtArrayNodeSet() {
        documentIds = new int[INITIAL_DOC_SIZE];
        parts = new Part[INITIAL_DOC_SIZE];
        Arrays.fill(documentIds, 0);
    }

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     * @param initialDocsCount an <code>int</code> value
     * @param initialArraySize an <code>int</code> value
     */
    public ExtArrayNodeSet(int initialDocsCount, int initialArraySize) {
        initalSize = initialArraySize;
        if (initialDocsCount == 0)
            initialDocsCount = 1;
        documentIds = new int[initialDocsCount];
        parts = new Part[initialDocsCount];
        Arrays.fill(documentIds, 0);
    }

    /**
     * Creates a new <code>ExtArrayNodeSet</code> instance.
     *
     * @param initialArraySize an <code>int</code> value
     */
    public ExtArrayNodeSet(int initialArraySize) {
        initalSize = initialArraySize;
        documentIds = new int[INITIAL_DOC_SIZE];
        parts = new Part[INITIAL_DOC_SIZE];
        Arrays.fill(documentIds, 0);
    }

    /**
     * The method <code>getPart</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param create a <code>boolean</code> value
     * @param sizeHint an <code>int</code> value
     * @return a <code>Part</code> value
     */
    protected Part getPart(DocumentImpl doc, boolean create, int sizeHint) {
        if (lastPart != null && doc.getDocId() == lastDoc) {
            return lastPart;
        }
        int idx = ArrayUtils.binarySearch(documentIds, doc.getDocId(), partCount);
        Part part = null;
        if (idx >= 0) {
            part = parts[idx];
        } else if (create) {
            idx = - (idx + 1);
            part = new Part(sizeHint);
            insertPart(doc.getDocId(), part, idx);
        }
        return part;
    }

    /**
     * The method <code>reset</code>
     *
     */
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

    private void insertPart(int docId, Part part, int idx) {
        if (partCount == parts.length) {
            int nsize = parts.length == 0 ? 1 : parts.length * 2;
            int ndocs[] = new int[nsize];
            System.arraycopy(documentIds, 0, ndocs, 0, documentIds.length);
            Arrays.fill(documentIds, -1);

            Part nparts[] = new Part[nsize];
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

        getPart(proxy.getDocument(), true, initalSize).add(proxy);
        ++size;
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
    	if (size > 0) {
            if (hasOne) {
                if (isSorted) {
                    hasOne = get(proxy) == null;
                } else {
                    hasOne = lastAdded == null || lastAdded.compareTo(proxy) == 0;
                }

            }
    	} else {
            hasOne = true;
        }

        getPart(proxy.getDocument(), true, sizeHint != Constants.NO_SIZE_HINT ? sizeHint : initalSize).add(
                                                                                                           proxy);
        ++size;
        isSorted = false;
        setHasChanged();
        checkItemType(proxy.getType());

        lastAdded = proxy;
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

    /**
     * The method <code>getSizeHint</code>
     *
     * @param doc a <code>DocumentImpl</code> value
     * @return an <code>int</code> value
     */
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
        if (!isSorted()) {
            sort();
        }
        return new ExtArrayIterator();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.Sequence#iterate()
     */
    public SequenceIterator iterate() throws XPathException {
        sortInDocumentOrder();
        return new ExtArrayIterator();
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
        return new ExtArrayIterator();
    }

    public ByDocumentIterator iterateByDocument() {
    	if (!isSorted()) {
            sort();
        }
    	return new ExtDocIterator();
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
        NodeSet result = new ExtArrayNodeSet();
		NodeProxy node;
		Part part;
		for (Iterator i = al.iterator(); i.hasNext(); ) {
			node = (NodeProxy) i.next();
			part = getPart(node.getDocument(), false, 0);
	        if (part != null) {
	        	part.getDescendantsInSet(result, node, childOnly, includeSelf, mode, contextId, copyMatches);
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
    public NodeProxy hasDescendantsInSet(DocumentImpl doc, NodeId ancestorId, boolean includeSelf, int contextId) {
        sort();
        final Part part = getPart(doc, false, 0);
        return part == null ? null : part.hasDescendantsInSet(ancestorId, contextId, includeSelf);
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

    /**
     * The method <code>filterDocuments</code>
     *
     * @param otherSet an <code>ExtArrayNodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet filterDocuments(ExtArrayNodeSet otherSet) {
        ExtArrayNodeSet other = otherSet;
        ExtArrayNodeSet result = new ExtArrayNodeSet(partCount, other.initalSize);
        for (int i = 0; i < other.partCount; i++) {
            int idx = ArrayUtils.binarySearch(documentIds, other.documentIds[i], partCount);
            if (idx > -1) {
                Part part = parts[idx];
                int otherIdx = ArrayUtils.binarySearch(result.documentIds, documentIds[idx], result.partCount);
                otherIdx = - (otherIdx + 1);
                result.insertPart(documentIds[idx], part, otherIdx);
            }
        }
        return result;
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
    	Part part = getPart(document, false, -1);
    	if (part != null) {
            part.setIsSorted(sorted);
        }
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
            size = parts[0].removeDuplicates(mergeContexts);
            return;
        }
        //        long start = System.currentTimeMillis();
        Part part;
        size = 0;
        for (int i = 0; i < partCount; i++) {
            part = parts[i];
            part.sort();
            size += part.removeDuplicates(mergeContexts);
        }
        isSorted = true;
        //        System.out.println("sort took " + (System.currentTimeMillis() -
        //                start) + "ms.");
    }

    /**
     * The method <code>sortInDocumentOrder</code>
     *
     */
    public final void sortInDocumentOrder() {
        sort(false);
    }

    /*
     * (non-Javadoc)
     *
     * @see org.exist.xquery.value.AbstractSequence#setSelfAsContext()
     */
    public void setSelfAsContext(int contextId)  throws XPathException {
        for (int i = 0; i < partCount; i++) {
            parts[i].setSelfAsContext(contextId);
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
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId) {
        sort();
        return super.selectPrecedingSiblings(siblings, contextId);
    }

    /**
     * The method <code>selectFollowingSiblings</code>
     *
     * @param siblings a <code>NodeSet</code> value
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
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
        lastPart = getPart(doc, false, initalSize);
        return lastPart == null ? null : lastPart.parentWithChild(doc, nodeId, directParent, includeSelf);
    }

    /**
     * The method <code>debugParts</code>
     *
     * @return a <code>String</code> value
     */
    public String debugParts() {
    	StringBuilder buf = new StringBuilder();
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
    	//Is the index type initialized ?
    	if (indexType == Type.ANY_TYPE) {
		    hasTextIndex = false;
		    hasMixedContent = false;
		    for (int i = 0; i < partCount; i++) {
		    	parts[i].determineIndexType();
		    }
    	}
    	return indexType;
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
//        cachedDocuments = new DefaultDocumentSet(partCount);
//        sort();
//        for (int i = 0; i < partCount; i++) {
//            cachedDocuments.add(parts[i].getDocument(), false);
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
    	cachedDocuments = new DefaultDocumentSet();
        cachedDocuments.addAll(docs);
    }

    public boolean equalDocs(DocumentSet other) {
		if (this == other)
			// we are comparing the same objects
			return true;
		if (partCount != other.getDocumentCount())
			return false;
        for (int i = 0; i < partCount; i++) {
			if (!other.contains(parts[i].getDocument().getDocId()))
				return false;
        }
        return true;
	}

    /**
     * The method <code>getCollectionIterator</code>
     *
     * @return an <code>Iterator</code> value
     */
    public Iterator getCollectionIterator() {
        return new CollectionIterator();
    }

    // DocumentSet methods

    public Iterator getDocumentIterator() {
        return new DocumentIterator();
    }

    public int getDocumentCount() {
        return partCount;
    }

    public DocumentImpl getDocumentAt(int pos) {
        return parts[pos].getDocument();
    }

    public DocumentImpl getDoc(int docId) {
        int idx = ArrayUtils.binarySearch(documentIds, docId, partCount);
        if (idx > -1)
            return parts[idx].getDocument();
        else
            return null;
    }

    public XmldbURI[] getNames() {
        XmldbURI[] uris = new XmldbURI[partCount];
        for (int i = 0; i < partCount; i++) {
            uris[i] = parts[i].getDocument().getURI();
        }
        return uris;
    }

    public DocumentSet intersection(DocumentSet other) {
        DefaultDocumentSet r = new DefaultDocumentSet();
		DocumentImpl d;
        for (int i = 0; i < partCount; i++) {
            d = parts[i].getDocument();
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
        if (other.getDocumentCount() > partCount)
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
        return ArrayUtils.binarySearch(documentIds, docId, partCount) > -1;
    }

    public NodeSet docsToNodeSet() {
        NodeSet result = new ExtArrayNodeSet(partCount);
		DocumentImpl doc;
        for (int i = 0; i < partCount; i++) {
            doc = parts[i].getDocument();
            if(doc.getResourceType() == DocumentImpl.XML_FILE) {  // skip binary resources
            	result.add(new NodeProxy(doc, NodeId.DOCUMENT_NODE));
            }
        }
        return result;
    }

    public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException {
        DocumentImpl d;
	    Lock dlock;
        for (int idx = 0; idx < partCount; idx++) {
	        d = parts[idx].getDocument();
            dlock = d.getUpdateLock();
            if (exclusive)
                dlock.acquire(Lock.WRITE_LOCK);
            else
                dlock.acquire(Lock.READ_LOCK);
        }
    }

    public void unlock(boolean exclusive) {
        DocumentImpl d;
	    Lock dlock;
        final Thread thread = Thread.currentThread();
        for(int idx = 0; idx < partCount; idx++) {
	        d = parts[idx].getDocument();
	        dlock = d.getUpdateLock();
            if(exclusive)
                dlock.release(Lock.WRITE_LOCK);
            else if (dlock.isLockedForRead(thread))
                dlock.release(Lock.READ_LOCK);
        }
    }

    private class DocumentIterator implements Iterator {

        int currentDoc = 0;

        public boolean hasNext() {
            return currentDoc < partCount;
        }

        public Object next() {
            if (currentDoc == partCount)
                return null;
            else
                return parts[currentDoc++].getDocument();
        }

        public void remove() {
        }
    }
    
    /**
     * The class <code>CollectionIterator</code>
     *
     */
    private class CollectionIterator implements Iterator {

        Iterator iterator = null;

        CollectionIterator() {
            if (partCount > 0) {
                ObjectHashSet collections = new ObjectHashSet();
                
                Part part;
                for (int i = 0; i < partCount; i++) {
                    part = parts[i];
                    collections.add(part.getDocument().getCollection());
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
     * The class <code>Part</code>
     *
     */
    private final class Part {

    	private boolean isSorted = false;
        private NodeProxy array[];
        private int length = 0;

        /**
         * Creates a new <code>Part</code> instance.
         *
         * @param initialSize an <code>int</code> value
         */
        Part(int initialSize) {
            array = new NodeProxy[initialSize];
        }

        /**
         * The method <code>selectParentChild</code>
         *
         * @param result a <code>NodeSet</code> value
         * @param na a <code>NodeProxy</code> value
         * @param ia a <code>NodeSetIterator</code> value
         * @param mode an <code>int</code> value
         * @param contextId an <code>int</code> value
         */
        public void selectParentChild(NodeSet result, NodeProxy na, NodeSetIterator ia, int mode, int contextId) {
            if (length == 0)
                return;
            int pos = 0;
            int startPos = 0;
            NodeProxy nb = array[pos];
            NodeId lastMarked = na.getNodeId();
            while (true) {
                // first, try to find nodes belonging to the same doc
                if (na.getDocument().getDocId() != nb.getDocument().getDocId()) {
                    break;
                }

                // same document
                NodeId pa = na.getNodeId();
                NodeId pb = nb.getNodeId();
                int relation = pb.computeRelation(pa);
                if (relation != -1) {
                    if (relation == NodeId.IS_CHILD) {
                        if(mode == NodeSet.DESCENDANT) {
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                nb.addContextNode(contextId, na);
                            } else {
                                nb.copyContext(na);
                            }
                            result.add(nb);
                        } else {
                            if (Expression.NO_CONTEXT_ID != contextId) {
                                na.addContextNode(contextId, nb);
                            } else {
                                na.copyContext(nb);
                            }
                            result.add(na);
                        }
                    }
                    if (++pos < length)
                        nb = array[pos];
                    else if (ia.hasNext()) {
                        NodeProxy next = ia.peekNode();
                        if (next.getNodeId().isDescendantOf(pa)) {
                            pos = startPos;
                            nb = array[pos];
                            na = (NodeProxy) ia.next();
                            startPos = pos;
                        } else {
                            break;
                        }
                    } else {
                        break;
                    }
                } else {
                    int cmp = pa.compareTo(pb);
                    if (cmp < 0) {
                        if (ia.hasNext()) {
                            NodeProxy next = (NodeProxy) ia.next();
                            if (next.getNodeId().isDescendantOf(pa)) {
                                pos = startPos;
                                nb = array[pos];
                            } else {
                                if (!next.getNodeId().isDescendantOf(lastMarked)) {
                                    lastMarked = next.getNodeId();
                                    startPos = pos;
                                }
                            }
                            na = next;
                        } else {
                            break;
                        }
                    } else {
                        if (++pos < length) {
                            nb = array[pos];
                        } else {
                            if (ia.hasNext()) {
                                NodeProxy next = (NodeProxy) ia.next();
                                if (next.getNodeId().isDescendantOf(pa)) {
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

        /**
         * The method <code>add</code>
         *
         * @param p a <code>NodeProxy</code> value
         */
        void add(NodeProxy p) {
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
            if (length > 0 && array[length - 1].getNodeId().equals(p.getNodeId())) {
                array[length - 1].addMatches(p);
                return;
                //} ljo's modification
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

        /**
         * The method <code>contains</code>
         *
         * @param nodeId a <code>NodeId</code> value
         * @return a <code>boolean</code> value
         */
        boolean contains(NodeId nodeId) {
            return get(nodeId) != null;
        }

        /**
         * The method <code>get</code>
         *
         * @param pos an <code>int</code> value
         * @return a <code>NodeProxy</code> value
         */
        NodeProxy get(int pos) {
            return array[pos];
        }

        /**
         * The method <code>get</code>
         *
         * @param nodeId a <code>NodeId</code> value
         * @return a <code>NodeProxy</code> value
         */
        NodeProxy get(NodeId nodeId) {
            int low = 0;
            int high = length - 1;
            int mid, cmp;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = array[mid];
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

        /**
         * The method <code>getDocument</code>
         *
         * @return a <code>DocumentImpl</code> value
         */
        DocumentImpl getDocument() {
            if(length == 0) {
                return null;
            }
            return array[0].getDocument();
        }

        /**
         * The method <code>setIsSorted</code>
         *
         * @param sorted a <code>boolean</code> value
         */
        void setIsSorted(boolean sorted) {
            this.isSorted = sorted;
        }

        /**
         * The method <code>sort</code>
         *
         */
        void sort() {
            if (isSorted) {
                return;
            }
            FastQSort.sortByNodeId(array, 0, length - 1);
        }

        /**
         * The method <code>sortInDocumentOrder</code>
         *
         */
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
         * @param doc a <code>DocumentImpl</code> value
         * @param nodeId a <code>NodeId</code> value
         * @param directParent a <code>boolean</code> value
         * @param includeSelf a <code>boolean</code> value
         * @return a <code>NodeProxy</code> value
         */
        NodeProxy parentWithChild(DocumentImpl doc, NodeId nodeId, boolean directParent, boolean includeSelf) {
            NodeProxy temp;
            if (includeSelf && (temp = get(nodeId)) != null) {
                return temp;
            }
            nodeId = nodeId.getParentId();
            while (nodeId != null) {
                if ((temp = get(nodeId)) != null) {
                    return temp;
                } else if (directParent) {
                    return null;
                }
                nodeId = nodeId.getParentId();
            }
            return null;
        }

        /**
         * The method <code>hasDescendantsInSet</code>
         *
         * @param ancestorId a <code>NodeId</code> value
         * @param contextId an <code>int</code> value
         * @param includeSelf a <code>boolean</code> value
         * @return a <code>NodeProxy</code> value
         */
        NodeProxy hasDescendantsInSet(NodeId ancestorId, int contextId, boolean includeSelf) {
            // do a binary search to pick some node in the range of valid child
            // ids
            int low = 0;
            int high = length - 1;
            int mid = 0;
            int cmp;
            NodeId id;
            while (low <= high) {
                mid = (low + high) / 2;
                id = array[mid].getNodeId();
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
            while (mid > 0 && array[mid - 1].getNodeId().compareTo(ancestorId) >= 0) {
                --mid;
            }
            NodeProxy ancestor = new NodeProxy(getDocument(), ancestorId, Node.ELEMENT_NODE);
            // we need to check if self should be included
            boolean foundOne = false;
            for (int i = mid; i < length; i++) {
                cmp = array[i].getNodeId().computeRelation(ancestorId);
                if (cmp > -1) {
                    boolean add = true;
                    if (cmp == NodeId.IS_SELF) {
                        add = includeSelf;
                    }

                    if (add) {
                        if (Expression.NO_CONTEXT_ID != contextId) {
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
         * @param result the node set to which matching nodes will be appended.
         * @param parent the parent node to search for.
         * @param childOnly only include child nodes, not descendant nodes
         * @param includeSelf include the self:: axis
         * @param mode
         * @param contextId
         */
        NodeSet getDescendantsInSet(NodeSet result, NodeProxy parent, boolean childOnly,
                                    boolean includeSelf, int mode, int contextId, boolean copyMatches) {
            NodeProxy p;
            NodeId parentId = parent.getNodeId();
            // document nodes are treated specially
            if (parentId == NodeId.DOCUMENT_NODE) {
            	for (int i = 0; i < length; i++) {
                    boolean add;
                    if (childOnly) {
                        add = array[i].getNodeId().getTreeLevel() == 1;
                    } else if (includeSelf) {
                        add = true;
                    } else {
                        add = array[i].getNodeId() != NodeId.DOCUMENT_NODE;
                    }
                    if (add) {
                        switch (mode) {
                            case NodeSet.DESCENDANT :
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    //array[i].addContextNode(contextId, parent);
                                    array[i].deepCopyContext(parent, contextId);
                                } else {
                                    array[i].copyContext(parent);
                                }
                                if (copyMatches)
                                    array[i].addMatches(parent);
                                result.add(array[i]);
                                break;
                            case NodeSet.ANCESTOR :
                                if (Expression.NO_CONTEXT_ID != contextId) {
                                    //parent.addContextNode(contextId, array[i]);
                                    parent.deepCopyContext(array[i], contextId);
                                } else {
                                    parent.copyContext(array[i]);
                                }
                                if (copyMatches)
                                    parent.addMatches(array[i]);
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
            	while (low <= high) {
                    mid = (low + high) / 2;
                    p = array[mid];
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
            	while (mid > 0 && array[mid - 1].getNodeId().compareTo(parentId) > -1) {
                    --mid;
                }
            	// walk through the range of child nodes we found
            	for (int i = mid; i < length; i++) {
                    cmp = array[i].getNodeId().computeRelation(parentId); 
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
                                        array[i].deepCopyContext(parent, contextId);
                                    } else {
                                        array[i].copyContext(parent);
                                    }
                                    array[i].addMatches(parent);
                                    result.add(array[i]);
                                    break;
                                case NodeSet.ANCESTOR :
                                    if (Expression.NO_CONTEXT_ID != contextId) {
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
        int removeDuplicates(boolean mergeContext) {
            int j = 0;
            for (int i = 1; i < length; i++) {
                if (!array[i].getNodeId().equals(array[j].getNodeId())) {
                    if (i != ++j) {
                        array[j] = array[i];
                    }
                } else {
                    if (mergeContext)
                        array[j].addContext(array[i]);
                    array[j].addMatches(array[i]);
                }
            }
            length = ++j;
            return length;
        }

        /**
         * The method <code>determineIndexType</code>
         *
         */
        void determineIndexType() {
            //Is the index type initialized ?        	
            if (indexType == Type.ANY_TYPE) {		        	
                hasTextIndex = true;
                hasMixedContent = true;        	
                for (int i = 0; i < length; i++) {
                    NodeProxy node = array[i];
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
        }

        /**
         * The method <code>setSelfAsContext</code>
         *
         * @param contextId an <code>int</code> value
         */
        void setSelfAsContext(int contextId) {
            for (int i = 0; i < length; i++) {
                array[i].addContextNode(contextId, array[i]);
            }
        }
    }

    /**
     * The class <code>ExtArrayIterator</code>
     *
     */
    private class ExtArrayIterator implements NodeSetIterator, SequenceIterator {

        Part currentPart = null;
        int partPos = 0;
        int pos = 0;
        NodeProxy next = null;

        /**
         * Creates a new <code>ExtArrayIterator</code> instance.
         *
         */
        ExtArrayIterator() {
            if (partPos < partCount) {
                currentPart = parts[partPos];
            }
            if (currentPart != null && currentPart.length > 0) {
                next = currentPart.get(0);
            }
        }

        /**
         * The method <code>setPosition</code>
         *
         * @param proxy a <code>NodeProxy</code> value
         */
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
                    int cmp = p.getNodeId().compareTo(proxy.getNodeId());
                    if (cmp == 0) {
                        pos = mid;
                        next = p;
                        return;
                    }
                    if (cmp > 0) {
                        high = mid - 1;
                    } else {
                        low = mid + 1;
                    }
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

        public NodeProxy peekNode() {
            return next;
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
    private class ExtDocIterator implements ByDocumentIterator {

    	Part currentPart = null;
        int pos = 0;
        NodeProxy next = null;

        /**
         * Creates a new <code>ExtDocIterator</code> instance.
         *
         */
        public ExtDocIterator() {
            if (partCount > 0) {
                currentPart = parts[0];
            }
            if (currentPart != null && currentPart.length > 0) {
                next = currentPart.get(0);
            }
        }

    	/**
         * The method <code>nextDocument</code>
         *
         * @param document a <code>DocumentImpl</code> value
         */
        public void nextDocument(DocumentImpl document) {
            currentPart = getPart(document, false, -1);
            pos = 0;
            if (currentPart != null && currentPart.length > 0) {
                next = currentPart.get(0);
            } else {
                next = null;
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
            if (++pos < currentPart.length) {
            	next = currentPart.get(pos);
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
            currentPart = getPart(node.getDocument(), false, -1);
            int low = 0;
            int high = currentPart.length - 1;
            int mid;
            NodeProxy p;
            while (low <= high) {
                mid = (low + high) / 2;
                p = currentPart.array[mid];
                int cmp = p.getNodeId().compareTo(node.getNodeId());
                if (cmp == 0) {
                    pos = mid;
                    next = p;
                    return;
                }
                if (cmp > 0) {
                    high = mid - 1;
                } else {
                    low = mid + 1;
                }
            }
            next = null;
        }
    }
}
