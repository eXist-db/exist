/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.dom;

import java.util.Iterator;

import org.exist.util.FastQSort;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.SequenceIterator;
import org.w3c.dom.Node;

/** @deprecated : use ExtArrayNodeSet
  *
 */
public class ArraySet extends AbstractNodeSet {

	protected int counter = 0;
	protected int length;

	protected NodeProxy nodes[];
    protected boolean sortedNaturally = false;
    protected boolean sortedInDocumentOrder = false;

	private DocumentOrderComparator docOrderComparator = new DocumentOrderComparator();
	
	/**
	 *  Constructor for the ArraySet object
	 *
	 *@param  initialCapacity  Description of the Parameter
	 */
	public ArraySet(int initialCapacity) {
		nodes = new NodeProxy[initialCapacity];
		length = initialCapacity;
	}

	private final static boolean getParentSet(NodeProxy[] nl, int len) {		
		boolean foundParentNode = false;		
		for (int i = 0; i < len; i++) {            
			//Skip invalid nodes
			if (nl[i] == null)
				continue;
			if (nl[i].getGID() == NodeProxy.DOCUMENT_NODE_GID) {
				nl[i] = null;
				continue;
			}	          
            nl[i] = new NodeProxy(nl[i].getDocument(), NodeSetHelper.getParentId(nl[i]));			
            foundParentNode = true;
		}
		return foundParentNode;
	}

	/**
	 *  BinarySearch algorithm
	 *
	 *@param  items    Description of the Parameter
	 *@param  low      Description of the Parameter
	 *@param  high     Description of the Parameter
	 *@param  cmpItem  Description of the Parameter
	 *@return          Description of the Return Value
	 */
	private final static int search(NodeProxy[] items, int low,	int high, NodeProxy cmpItem) {
		int mid;
		int cmp;
		//Remember that items must be sorted !
		while (low <= high) {
			mid = (low + high) / 2;
			cmp = items[mid].compareTo(cmpItem);
			if (cmp == 0)
				return mid;
			if (cmp > 0)
				high = mid - 1;
			else
				low = mid + 1;

		}
		return -1;
	}

	/**
		 *  BinarySearch algorithm
		 *
		 *@param  items    Description of the Parameter
		 *@param  low      Description of the Parameter
		 *@param  high     Description of the Parameter
		 *@param  cmpItem  Description of the Parameter
		 *@return          Description of the Return Value
		 */
	private final static int search(NodeProxy[] items, int low,	int high, DocumentImpl cmpDoc, long gid) {
		int mid;
        NodeProxy cmp;
        //Remember that items must be sorted !
		while (low <= high) {
			mid = (low + high) / 2;
            cmp = items[mid];
			if (cmp.getDocument().getDocId() == cmpDoc.getDocId()) {
				if (cmp.getGID() == gid)
					return mid;
				else if (cmp.getGID() > gid)
					high = mid - 1;
				else
					low = mid + 1;
			} else if (cmp.getDocument().getDocId() > cmpDoc.getDocId())
				high = mid - 1;
			else
				low = mid + 1;
		}
		return -1;
	}

	/**
	 *  get all nodes contained in the set, which are greater or equal to lower
	 *  and less or equal to upper. This is basically needed by all functions
	 *  that determine the position of a node in the node set.
	 *
	 *@param  items  Description of the Parameter
	 *@param  low    Description of the Parameter
	 *@param  high   Description of the Parameter
	 *@param  lower  Description of the Parameter
	 *@param  upper  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	private final static void searchRange(NodeSet result, NodeProxy[] items, int low, int high,
	        NodeProxy lower, NodeProxy upper) {
		int mid = 0;
		int max = high;
		int cmp;
		//Remember that items must be sorted !
		while (low <= high) {
			mid = (low + high) / 2;
			cmp = items[mid].compareTo(lower);
			if (cmp == 0)
				break;
			if (cmp > 0)
				high = mid - 1;
			else
				low = mid + 1;
		}
        
		while (mid > 0 && items[mid].compareTo(lower) > 0)
			mid--;

		if (items[mid].compareTo(lower) < 0)
			mid++;

		while (mid <= max && items[mid].compareTo(upper) <= 0)
			result.add(items[mid++]);
	}
	
	public boolean isEmpty() {
		return (getLength() == 0);
	}
	
    public boolean hasOne() {
    	return (getLength() == 1);
    }	

	public void add(NodeProxy proxy) {
		if (proxy == null)
			return;
		if (counter < length)
			nodes[counter++] = proxy;
		else {
			int grow = (length < 10) ? 50 : length >> 1;
			NodeProxy temp[] = new NodeProxy[length + grow];
			System.arraycopy(nodes, 0, temp, 0, length);
			length = length + grow;
			nodes = temp;
			nodes[counter++] = proxy;
		}
        sortedNaturally = false;
        sortedInDocumentOrder = false;
	}

	public void addAll(NodeSet other) {
		for (Iterator i = other.iterator(); i.hasNext();) {
			add((NodeProxy) i.next());
		}
	}

	public boolean contains(DocumentImpl doc, long nodeId) {		
        NodeProxy p = new NodeProxy(doc, nodeId);
		return contains(p);
	}

	public boolean contains(NodeProxy proxy) {
		sort();
		return -1 < search(nodes, 0, counter - 1, proxy);
	}

	public NodeProxy get(DocumentImpl doc, long nodeId) {
        NodeProxy p = new NodeProxy(doc, nodeId);
		return get(p);
	}

	public NodeProxy get(NodeProxy p) {
		sort();
		int pos = search(nodes, 0, counter - 1, p);
		if (pos == -1)
			return null;		
		return nodes[pos];
	}

	public NodeProxy get(int pos) {
        if (pos == -1)
            return null;
        if (pos >= counter)
            return null;
		sort();
		return nodes[pos];
	}

	public NodeProxy getUnsorted(int pos) {
        //TODO : what if the Array has been sorted ?
        if (pos == -1)
            return null;
        if (pos >= counter)
            return null;
		return nodes[pos];
	}
	
	public Item itemAt(int pos) {
		return get(pos);
	}

	/**
	 *  For a given set of potential ancestor nodes, get the
	 * descendants in this node set
	 *
	 *@return       The descendants value
	 */
	public NodeSet selectAncestors(NodeSet other, boolean includeSelf, int contextId) {
		if (!(other instanceof ArraySet))
			return super.selectAncestors(other, includeSelf, contextId);
		ArraySet al = (ArraySet) other;
		if (al.counter == 0 || counter == 0)
			return new ArraySet(1);
		long start = System.currentTimeMillis();
		al.sort();
		sort();
		// the descendant set will be modified: copy if required 
		NodeProxy[] dl = copyNodeSet(al, this);

		//NodeSet result = new NodeIDSet();
		NodeSet result = new ArraySet(getLength());
		NodeProxy temp;
		int ax;
		int dx;
		int cmp;
		final int dlen = dl.length;
		boolean more = includeSelf ? true : getParentSet(dl, dlen);
		while (more) {
			ax = 0;
			dx = 0;
			//more = getParentSet(dl);
			while (dx < dlen) {
				if (dl[dx] == null) { // || dl[dx].gid < 1) {
					dx++;
					continue;
				}
				//System.out.println(dl[dx].gid + " == " + al.nodes[ax].gid);
				cmp = dl[dx].compareTo(al.nodes[ax]);
				if (cmp > 0) {
					if (ax < al.counter - 1)
						ax++;
					else
						break;
				} else if (cmp < 0)
					dx++;
				else {
					// found a matching node
					if ((temp = result.get(al.nodes[ax])) == null) {
						// remember the ancestor-node
						al.nodes[ax].addMatches(nodes[dx]);
						if (Expression.NO_CONTEXT_ID != contextId)
							al.nodes[ax].addContextNode(contextId, nodes[dx]);
						else
							al.nodes[ax].copyContext(nodes[dx]);
						result.add(al.nodes[ax]);
					} else if (Expression.NO_CONTEXT_ID != contextId)
						temp.addContextNode(contextId, nodes[dx]);
					dx++;
				}
			}
			// calculate parent id for each node in the
			// descendant set. Returns false if no more
			// valid nodes are found
			more = getParentSet(dl, dlen);
		}
        if (LOG.isDebugEnabled())
    		LOG.debug(
    			"getAncestors found "
    				+ result.getLength()
    				+ " in "
    				+ (System.currentTimeMillis() - start)
    				+ "ms.");
		return result;
	}

	public int getLength() {
		return counter;
	}

	public void getRange(NodeSet result, NodeProxy lower, NodeProxy upper) {
		sort();
		searchRange(result, nodes, 0, counter - 1, lower, upper);
	}

	public void getRange(NodeSet result, DocumentImpl doc, long lower, long upper) {
		getRange(result, new NodeProxy(doc, lower), new NodeProxy(doc, upper));
	}

	//protected boolean isSorted() {
	//	return sorted;
	//}

	public Node item(int pos) {		
		NodeProxy p = get(pos);
		return (p == null) ? null : p.getNode();
	}

	public NodeSetIterator iterator() {
		sort();
		return new ArraySetIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		sortInDocumentOrder();
		return new ArraySequenceIterator();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.AbstractNodeSet#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
        //TODO : explain this sort -pb
		sort();
		return new ArraySequenceIterator();
	}

	public int position(NodeProxy proxy) {
		sort();
		return search(nodes, 0, counter - 1, proxy);
	}

	public void remove(NodeProxy node) {		
		int pos = search(nodes, 0, counter - 1, node);
		if (pos == -1)
            return;		
		NodeProxy[] temp = new NodeProxy[counter];
		System.arraycopy(nodes, 0, temp, 0, pos - 2);
		System.arraycopy(nodes, pos + 1, temp, pos + 1, temp.length - pos - 1);
		nodes = temp;
		counter--;
	}

	
    /* (non-Javadoc)
     * @see org.exist.dom.AbstractNodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        DocumentSet docs = new DocumentSet();
        DocumentImpl lastDoc = null;
        for (int i = 0; i < counter; i++) {
            if (lastDoc == null || lastDoc.getDocId() != nodes[i].getDocument().getDocId()) {
                docs.add(nodes[i].getDocument(), false);
            }
            lastDoc = nodes[i].getDocument();
        }
        return docs;
    }
    
	//public void setIsSorted(boolean sorted) {
		//this.sorted = sorted;
	//}

	public void sort() {
        if (this.sortedNaturally || counter < 2)
			return;
		FastQSort.sort(nodes, 0, counter - 1);
		removeDuplicateNodes();
        sortedNaturally = true;
        sortedInDocumentOrder = false;
	}

	public void sortInDocumentOrder() {
        if (this.sortedInDocumentOrder || counter < 2)
			return;
		FastQSort.sort(nodes, docOrderComparator, 0, counter - 1);
		removeDuplicateNodes();
        sortedNaturally = false;
        sortedInDocumentOrder = true;
	}
	
	private final void removeDuplicateNodes() {
		int j = 0;
		for (int i = 1; i < counter; i++) {
			if (nodes[i].compareTo(nodes[j]) != 0) {
				if (i != ++j)
					nodes[j] = nodes[i];
			}
		}
		counter = ++j;
	}

	public void reset() {
		counter = 0;
        sortedNaturally = false;
        sortedInDocumentOrder = false;
	}
	
	private final static NodeProxy[] copyNodeSet(ArraySet al, ArraySet dl) {
		int ax = 0, dx = 0;
		int ad = al.nodes[ax].getDocument().getDocId(), dd = dl.nodes[dx].getDocument().getDocId();
		final int alen = al.counter - 1, dlen = dl.counter - 1;
		final NodeProxy[] ol = new NodeProxy[dl.counter];
		while (true) {
			if (ad < dd) {
				if (ax < alen) {
					++ax;
					ad = al.nodes[ax].getDocument().getDocId();
				} else
					break;
			} else if (ad > dd) {
				if (dx < dlen) {
					ol[dx] = null;
					++dx;
					dd = dl.nodes[dx].getDocument().getDocId();
				} else
					break;
			} else {
				ol[dx] = new NodeProxy(dl.nodes[dx]);
				if (dx < dlen) {
					++dx;
					dd = dl.nodes[dx].getDocument().getDocId();
				} else
					break;
			}
		}
		return ol;
	}

	private final static void trimNodeSet(ArraySet al, ArraySet dl) {
		int ax = 0, dx = 0;
		int ad = al.nodes[ax].getDocument().getDocId(), dd = dl.nodes[dx].getDocument().getDocId();
		int count = 0;
		final int alen = al.counter - 1, dlen = dl.counter - 1;
		while (true) {
			if (ad < dd) {
				if (ax < alen) {
					++ax;
					ad = al.nodes[ax].getDocument().getDocId();
				} else
					break;
			} else if (ad > dd) {
				if (dx < dlen) {
					++dx;
					dd = dl.nodes[dx].getDocument().getDocId();
				} else
					break;
			} else {
				if (dx < dlen) {
					++dx;
					count++;
					dd = dl.nodes[dx].getDocument().getDocId();
				} else
					break;
			}
		}
		System.out.println("dl = " + dlen + "; copy = " + count);
	}

	private class ArraySetIterator implements NodeSetIterator {

		private int pos = 0;

		public boolean hasNext() {
			return (pos < counter) ? true : false;
		}

		public Object next() {
			return hasNext() ? nodes[pos++] : null;
		}

        public void setPosition(NodeProxy proxy) {
            int pos = search(nodes, 0, counter - 1, proxy);
            if (pos == -1)
                pos = counter;
        }
        
		public void remove() {
		}
	}

	private class ArraySequenceIterator implements SequenceIterator {

		private int pos = 0;

		public boolean hasNext() {
			return (pos < counter) ? true : false;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			return (pos < counter) ? nodes[pos++] : null;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.util.Sortable#compare(int, int)
	 */
	public int compare(int a, int b) {
		NodeProxy anode = nodes[a], bnode = nodes[b];
		if (anode.getDocument().getDocId() == bnode.getDocument().getDocId()) {
			return anode.getGID() == bnode.getGID()
				? 0
				: (anode.getGID() < bnode.getGID() ? Constants.INFERIOR : Constants.SUPERIOR);
		}
		return anode.getDocument().getDocId() < bnode.getDocument().getDocId() ? Constants.INFERIOR : Constants.SUPERIOR;
	}

	/* (non-Javadoc)
	 * @see org.exist.util.Sortable#swap(int, int)
	 */
	public void swap(int a, int b) {
		NodeProxy t = nodes[a];
		nodes[a] = nodes[b];
		nodes[b] = t;
	}

	public Comparable[] array() {
		return nodes;
	}
    
    public String toString() {
        StringBuffer result = new StringBuffer();
        result.append("Array#").append(super.toString());
        return result.toString();
    }     
}
