/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import it.unimi.dsi.fastutil.Int2ObjectAVLTreeMap;

import java.util.Iterator;

import org.exist.util.FastQSort;
import org.exist.util.Range;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.SequenceIterator;
import org.w3c.dom.Node;

/**
 * A fast node set implementation, based on arrays to store nodes and an AVL tree to 
 * organize documents. 
 * 
 * The class uses arrays to store all nodes belonging to one document.
 * The AVL tree maps the document id to the array containing the nodes for the document.
 * Nodes are just appended to the array. No order is guaranteed and calls to get/contains may fail
 * although a node is present in the array (get/contains do a binary search and thus assume that
 * the set is sorted). Also, duplicates are allowed. If you have to ensure that calls to get/contains 
 * return valid results at any time and no duplicates occur, use class 
 * {@link org.exist.dom.AVLTreeNodeSet}. 
 * 
 * Use this class, if you can either ensure that items are added in order, or no calls to 
 * contains/get are required during the creation phase. Only after 
 * a call to one of the iterator methods,  the set will get sorted and duplicates removed.
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 * @since 0.9.3
 */
public class ExtArrayNodeSet extends AbstractNodeSet {

	private Int2ObjectAVLTreeMap map = new Int2ObjectAVLTreeMap();
	private int initalSize = 128;
	private int size = 0;
	private boolean isSorted = false;

	private int lastDoc = -1;
	private Part lastPart = null;

	public ExtArrayNodeSet() {
	}

	/**
	 * Constructor for ExtArrayNodeSet. The int argument specifies the
	 * default array size, which is used whenever a new array has to be
	 * allocated for nodes. The default array size can be overwritten by the
	 * sizeHint argument passed to {@link #add(NodeProxy, int).
	 * 
	 * @param initialArraySize
	 */
	public ExtArrayNodeSet(int initialArraySize) {
		this.initalSize = initialArraySize;
	}

	public void add(NodeProxy proxy) {
		add(proxy, initalSize);
	}

	/**
	 * Add a new node to the set. If a new array of nodes has to be
	 * allocated for the document, use the sizeHint parameter to
	 * determine the size of the newly allocated array. This will overwrite
	 * the default array size.
	 * 
	 * If the size hint is correct, no further reallocations will be required.
	 */
	public void add(NodeProxy proxy, int sizeHint) {
		Part part =
			getPart(
				proxy.doc.docId,
				true,
				sizeHint > 0 ? sizeHint : initalSize);
		part.add(proxy);
		++size;
		isSorted = false;
	}

	public int getSizeHint(DocumentImpl doc) {
		Part part = getPart(doc.docId, false, 0);
		return part == null ? -1 : part.length;
	}

	private final Part getPart(int docId, boolean create, int sizeHint) {
		if (docId == lastDoc && lastPart != null)
			return lastPart;
		Part part = (Part) map.get(docId);
		if (part == null && create) {
			part = new Part(sizeHint);
			map.put(docId, part);
		}
		lastPart = part;
		lastDoc = docId;
		return part;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public Iterator iterator() {
		sort();
		return new ExtArrayIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		sort();
		return new ExtArrayIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#containsDoc(org.exist.dom.DocumentImpl)
	 */
	public boolean containsDoc(DocumentImpl doc) {
		return map.containsKey(doc.docId);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		final Part part = getPart(doc.docId, false, 0);
		return part == null ? false : part.contains(nodeId);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
		final Part part = getPart(proxy.doc.docId, false, 0);
		return part == null ? false : part.contains(proxy.gid);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
	 */
	public void addAll(NodeSet other) {
		for (Iterator i = other.iterator(); i.hasNext();) {
			add((NodeProxy) i.next());
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getLength()
	 */
	public int getLength() {
		return size;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.NodeList#item(int)
	 */
	public Node item(int pos) {
		NodeProxy p = get(pos);
		return p == null ? null : p.getNode();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		int count = 0;
		Part part;
		for (Iterator i = map.values().iterator(); i.hasNext();) {
			part = (Part) i.next();
			if (count + part.length > pos)
				return part.get(pos - count);
			count += part.length;
		}
		return null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		final Part part = getPart(p.doc.docId, false, 0);
		return part == null ? null : part.get(p.gid);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl doc, long nodeId) {
		final Part part = getPart(doc.docId, false, 0);
		return part == null ? null : part.get(nodeId);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return get(pos);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#remove(org.exist.dom.NodeProxy)
	 */
	public void remove(NodeProxy node) {
		final Part part = getPart(node.doc.getDocId(), false, 0);
		if(part == null)
			return;
		part.remove(node);
		if(part.length == 0)
			map.remove(node.doc.getDocId());
	}
	
	public NodeSet getRange(DocumentImpl doc, long lower, long upper) {
		final Part part = getPart(doc.docId, false, 0);
		return part.getRange(lower, upper);
	}

	public NodeSet hasChildrenInSet(
		NodeProxy parent,
		int mode,
		boolean rememberContext) {
		final Part part = getPart(parent.doc.docId, false, 0);
		if(part == null)
			return new ArraySet(1);
		return part.getChildrenInSet(parent, mode, rememberContext);
	}

	public void sort() {
		if (isSorted)
			return;
		Part part;
		size = 0;
		for (Iterator i = map.values().iterator(); i.hasNext();) {
			part = (Part) i.next();
			part.sort();
			size += part.removeDuplicates();
		}
		isSorted = true;
	}

	private final static class Part {

		NodeProxy array[];
		int length = 0;

		Part(int initialSize) {
			array = new NodeProxy[initialSize];
		}

		void add(NodeProxy p) {
			// just check if this node has already been added. We only
			// check the last entry, which should avoid most of the likely 
			// duplicates. The remaining duplicates are removed by
			// removeDuplicates().
			if (length > 0 && array[length - 1].gid == p.gid) {
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

		boolean contains(long gid) {
			return search(gid) != null;
		}

		NodeProxy get(int pos) {
			return array[pos];
		}

		NodeProxy get(long gid) {
			return search(gid);
		}

		void sort() {
			FastQSort.sortByNodeId(array, 0, length - 1);
		}

		final NodeProxy search(long gid) {
			int low = 0;
			int high = length - 1;
			int mid;
			NodeProxy p;
			while (low <= high) {
				mid = (low + high) / 2;
				p = array[mid];
				if (p.gid == gid)
					return p;
				if (p.gid > gid)
					high = mid - 1;
				else
					low = mid + 1;
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
		final NodeSet getChildrenInSet(
			NodeProxy parent,
			int mode,
			boolean rememberContext) {
			NodeSet result = new ExtArrayNodeSet();
			// get the range of node ids reserved for children of the parent node
			Range range = XMLUtil.getChildRange(parent.doc, parent.gid);
			int low = 0;
			int high = length - 1;
			int mid = 0;
			NodeProxy p;
			// do a binary search to pick some node in the range of valid child ids
			while (low <= high) {
				mid = (low + high) / 2;
				p = array[mid];
				if (range.inRange(p.gid))
					break; // found a node, break out
				if (p.gid > range.getStart())
					high = mid - 1;
				else
					low = mid + 1;
			}
			if (low > high)
				return result; // no node found

			// find the first child node in the range
			while (mid > 0 && array[mid - 1].gid >= range.getStart())
				--mid;

			// walk through the range of child nodes we found
			for (int i = mid;
				i < length && array[i].gid <= range.getEnd();
				i++) {
				switch (mode) {
					case NodeSet.DESCENDANT :
						if (rememberContext)
							array[i].addContextNode(parent);
						else
							array[i].copyContext(parent);
						result.add(array[i], range.getDistance());
						break;
					case NodeSet.ANCESTOR :
						if (rememberContext)
							parent.addContextNode(array[i]);
						else
							parent.copyContext(array[i]);
						result.add(parent, 1);
						break;
				}
			}
			return result;
		}

		final NodeSet getRange(long lower, long upper) {
			NodeSet result = new ExtArrayNodeSet((int) (upper - lower) + 1);
			int low = 0;
			int high = length - 1;
			int mid = 0;
			NodeProxy p;
			// do a binary search to pick some node in the range of valid node ids
			while (low <= high) {
				mid = (low + high) / 2;
				p = array[mid];
				if (p.gid >= lower && p.gid <= upper)
					break; // found a node, break out
				if (p.gid > lower)
					high = mid - 1;
				else
					low = mid + 1;
			}
			if (low > high)
				return result; // no node found

			// find the first child node in the range
			while (mid > 0 && array[mid - 1].gid >= lower)
				--mid;
			for (int i = mid; i < length && array[i].gid <= upper; i++) {
				result.add(array[i]);
			}
			return result;
		}

		final void remove(NodeProxy node) {
			int low = 0;
			int high = length - 1;
			int mid = -1;
			NodeProxy p;
			while (low <= high) {
				mid = (low + high) / 2;
				p = array[mid];
				if (p.gid == node.gid)
					break;
				if (p.gid > node.gid)
					high = mid - 1;
				else
					low = mid + 1;
			}
			if(low > high)
				return;	// not found
			if(mid < length - 1)
				System.arraycopy(array, mid + 1, array, mid, length - mid - 1);
			--length;
		}
		
		/**
		 * Remove all duplicate nodes from this part.
		 * 
		 * @return the new length of the part, after removing all
		 * duplicates
		 */
		final int removeDuplicates() {
			int j = 0;
			for (int i = 1; i < length; i++) {
				if (array[i].gid != array[j].gid) {
					if (i != ++j)
						array[j] = array[i];
				}
			}
			length = ++j;
			return length;
		}
	}

	private class ExtArrayIterator implements Iterator, SequenceIterator {

		Iterator docsIterator;
		Part currentPart = null;
		int pos = 0;
		NodeProxy next = null;

		ExtArrayIterator() {
			docsIterator = map.values().iterator();
			if (docsIterator.hasNext())
				currentPart = (Part) docsIterator.next();
			if (currentPart != null && currentPart.length > 0)
				next = currentPart.get(0);
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#hasNext()
		 */
		public boolean hasNext() {
			return next != null;
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#next()
		 */
		public Object next() {
			if (next == null)
				return null;
			NodeProxy n = next;
			next = null;
			if (++pos == currentPart.length) {
				if (docsIterator.hasNext()) {
					currentPart = (Part) docsIterator.next();
					if (currentPart != null && currentPart.length > 0) {
						next = currentPart.get(0);
						pos = 0;
					}
				}
			} else
				next = currentPart.get(pos);
			return n;
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			return (Item) next();
		}

		/* (non-Javadoc)
		 * @see java.util.Iterator#remove()
		 */
		public void remove() {
		}
	}
}
