/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 *  $Id:
 */
package org.exist.dom;

import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import java.util.Iterator;
//import java.util.Arrays;
import org.apache.log4j.Category;
import org.exist.xpath.Value;

/**
 *  Description of the Class
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22. Juli 2002
 */
public class ArraySet extends NodeSet {

	public final static int ANCESTOR = 0;
	public final static int DESCENDANT = 1;

	protected static Category LOG =
		Category.getInstance(ArraySet.class.getName());
	protected int counter = 0;
	protected int length;

	protected NodeProxy nodes[];
	protected boolean sorted = false;

	/**
	 *  Constructor for the ArraySet object
	 *
	 *@param  initialCapacity  Description of the Parameter
	 */
	public ArraySet(int initialCapacity) {
		nodes = new NodeProxy[initialCapacity];
		length = initialCapacity;
	}

	private final static boolean getParentSet(NodeProxy[] nl) {
		int level;
		long pid;
		boolean foundValid = false;
		for (int i = 0; i < nl.length; i++) {
			// skip invalid nodes
			if (nl[i] == null)
				continue;
			if (nl[i].gid < 0) {
				nl[i] = null;
				continue;
			}
			level = nl[i].doc.getTreeLevel(nl[i].gid);
			// calculate parent's gid
			pid =
				(nl[i].gid - nl[i].doc.getLevelStartPoint(level))
					/ nl[i].doc.getTreeLevelOrder(level)
					+ nl[i].doc.getLevelStartPoint(level - 1);
			//System.out.println(nl[i].doc.getDocId() + ":" + nl[i].gid + "->" + pid);
			nl[i].gid = pid;
			if (pid > 0)
				foundValid = true;
		}
		return foundValid;
	}

	/**
	 *  QuickSort - sorting is needed once before we do binary search
	 *
	 *@param  list  Description of the Parameter
	 *@param  low   Description of the Parameter
	 *@param  high  Description of the Parameter
	 */
	public final static void quickSort(NodeProxy[] list, int low, int high) {
		if (low >= high)
			return;
		int left_index = low;
		int right_index = high;
		NodeProxy pivot = list[(low + high) / 2];
		NodeProxy temp;
		do {
			while (left_index <= high && list[left_index].compareTo(pivot) < 0)
				left_index++;

			while (right_index >= low
				&& list[right_index].compareTo(pivot) > 0)
				right_index--;

			if (left_index <= right_index) {
				if (list[left_index].compareTo(list[right_index]) == 0) {
					left_index++;
					right_index--;
				} else {
					temp = list[right_index];
					list[right_index--] = list[left_index];
					list[left_index++] = temp;
				}
			}
		}
		while (left_index <= right_index);
		quickSort(list, low, right_index);
		quickSort(list, left_index, high);
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
	private final static int search(
		NodeProxy[] items,
		int low,
		int high,
		NodeProxy cmpItem) {
		int mid;
		int cmp;
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
	private final static NodeSet searchRange(
		NodeProxy[] items,
		int low,
		int high,
		NodeProxy lower,
		NodeProxy upper) {
		int mid = 0;
		int max = high;
		int cmp;
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
		ArraySet result = new ArraySet(100);
		while (mid > 0 && items[mid].compareTo(lower) > 0)
			mid--;

		if (items[mid].compareTo(lower) < 0)
			mid++;

		while (mid <= max && items[mid].compareTo(upper) <= 0)
			result.add(items[mid++]);

		result.setIsSorted(true);
		return result;
	}

	public void add(NodeProxy proxy) {
		if (proxy == null)
			return;
		if (counter < length)
			nodes[counter++] = proxy;
		else {
			final int grow = (length < 10) ? 50 : length >> 1;
			NodeProxy temp[] = new NodeProxy[length + grow];
			System.arraycopy(nodes, 0, temp, 0, length);
			length = length + grow;
			nodes = temp;
			nodes[counter++] = proxy;
		}
	}

	public void add(DocumentImpl doc, long nodeId) {
		NodeProxy p = new NodeProxy(doc, nodeId);
		add(p);
	}

	public void add(DocumentImpl doc, long nodeId, Value value) {
		NodeProxy p = new NodeProxy(doc, nodeId);
		add(p);
	}

	public void add(Node node) {
		if (node == null)
			return;
		if (!(node instanceof NodeImpl))
			throw new RuntimeException("wrong implementation");
		NodeProxy p =
			new NodeProxy(
				((NodeImpl) node).ownerDocument,
				((NodeImpl) node).gid,
				((NodeImpl) node).internalAddress);
		add(p);
	}

	/**
	 *  Adds a feature to the All attribute of the ArraySet object
	 *
	 *@param  other  The feature to be added to the All attribute
	 */
	public void addAll(NodeSet other) {
		for (Iterator i = other.iterator(); i.hasNext();)
			add((NodeProxy) i.next());

	}

	/**
	 *  Adds a feature to the All attribute of the ArraySet object
	 *
	 *@param  other  The feature to be added to the All attribute
	 */
	public void addAll(NodeList other) {
		for (int i = 0; i < other.getLength(); i++)
			add(other.item(i));

	}

	/**  Description of the Method */
	protected void checkSorted() {
		if (counter > 1
			&& nodes[counter - 1].compareTo(nodes[counter - 2]) < 0)
			sorted = false;
		else
			sorted = true;

	}

	//    protected void finalize() throws Throwable {
	//        System.out.println( "releasing nodes ...");
	//        for( int i = 0; i < counter; i++ ) {
	//            NodeProxyFactory.release( nodes[i] );
	//            nodes[i] = null;
	//        }
	//    }

	/**  Description of the Method */
	public void clear() {
		counter = 0;
		sorted = false;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc     Description of the Parameter
	 *@param  nodeId  Description of the Parameter
	 *@return         Description of the Return Value
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		NodeProxy p = new NodeProxy(doc, nodeId);
		return contains(p);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public boolean contains(NodeProxy proxy) {
		sort();
		return -1 < search(nodes, 0, counter - 1, proxy);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc     Description of the Parameter
	 *@param  nodeId  Description of the Parameter
	 *@return         Description of the Return Value
	 */
	public NodeProxy get(DocumentImpl doc, long nodeId) {
		int pos = search(nodes, 0, counter - 1, new NodeProxy(doc, nodeId));
		if (pos < 0) {
			return null;
		}
		return nodes[pos];
	}

	public NodeProxy get(NodeProxy p) {
		int pos = search(nodes, 0, counter - 1, p);
		if (pos < 0)
			return null;
		return nodes[pos];
	}

	/**
	 *  Description of the Method
	 *
	 *@param  pos  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public NodeProxy get(int pos) {
		if (pos >= counter || pos < 0)
			return null;
		sort();
		return nodes[pos];
	}

	/**
	 *  Gets the childNodes attribute of the ArraySet object
	 *
	 *@param  parent  Description of the Parameter
	 *@return         The childNodes value
	 */
	public ArraySet getChildNodes(NodeProxy parent) {
		sort();
		ArraySet result = new ArraySet(5);
		int level = parent.doc.getTreeLevel(parent.gid);
		long first =
			(parent.gid - parent.doc.getLevelStartPoint(level))
				* parent.doc.getTreeLevelOrder(level + 1)
				+ parent.doc.getLevelStartPoint(level + 1);
		long last = first + parent.doc.getTreeLevelOrder(level + 1);
		return (ArraySet) getRange(
			new NodeProxy(parent.doc, first),
			new NodeProxy(parent.doc, last));
	}

	/**
	 *  Gets the children attribute of the ArraySet object
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 *@return      The children value
	 */
	public NodeSet getChildren(DocumentImpl doc, long gid) {
		int level = doc.getTreeLevel(gid);
		// get parents id
		long pid =
			(gid - doc.getLevelStartPoint(level))
				/ doc.getTreeLevelOrder(level)
				+ doc.getLevelStartPoint(level - 1);
		// get first childs id
		long f_gid =
			(pid - doc.getLevelStartPoint(level - 1))
				* doc.getTreeLevelOrder(level)
				+ doc.getLevelStartPoint(level);
		// get last childs id
		long e_gid = f_gid + doc.getTreeLevelOrder(level);
		// get all nodes between first and last childs id
		NodeSet set = getRange(doc, f_gid, e_gid);
		return set;
	}

	public ArraySet getChildren(NodeSet ancestors, int mode) {
		if (!(ancestors instanceof ArraySet))
			return super.getChildren(ancestors, mode);
		ArraySet al = (ArraySet) ancestors;
		if (al.counter == 0)
			return new ArraySet(1);
		long start = System.currentTimeMillis();
		sort();
		al.sort();
		// get a deep copy of array - will be modified
		NodeProxy[] dl = null;
		if (mode == DESCENDANT) {
			dl = new NodeProxy[counter];
			for (int i = 0; i < counter; i++)
				dl[i] = new NodeProxy(nodes[i]);
		} else
			dl = nodes;

		ArraySet result = new ArraySet(al.counter);
		int ax = 0;
		int dx = 0;
		int cmp;
		getParentSet(dl);
		while (dx < dl.length) {
			if (dl[dx] == null) { // || dl[dx].gid < 1) {
				dx++;
				continue;
			}
			//            System.out.println(dl[dx].doc.getDocId() + ":" + dl[dx].gid + 
			//                        " = " + al.nodes[ax].doc.getDocId() + ':' + 
			//                        al.nodes[ax].gid);
			cmp = dl[dx].compareTo(al.nodes[ax]);
			if (cmp > 0) {
				if (ax < al.counter - 1)
					ax++;
				else
					break;
			} else if (cmp < 0)
				dx++;
			else {
				switch (mode) {
					case ANCESTOR :
						dl[dx].addMatches(al.nodes[ax].matches);
						result.add(dl[dx]);
						break;
					case DESCENDANT :
						nodes[dx].addMatches(al.nodes[ax].matches);
						result.add(nodes[dx]);
						break;
				}
				dx++;
			}
		}
		LOG.debug(
			"getChildren took " + (System.currentTimeMillis() - start) + "ms.");
		return result;
	}

	/**
	 *  Gets the descendants attribute of the ArraySet object
	 *
	 *@param  ancestor  Description of the Parameter
	 *@return           The descendants value
	 */
	public ArraySet getDescendantNodes(NodeProxy ancestor) {
		sort();
		ArraySet result = new ArraySet(5);
		int mid = 0;
		int high = counter - 1;
		int low = 0;
		int cmp;
		while (low <= high) {
			mid = (low + high) / 2;
			if (nodes[mid].doc.getDocId() == 0)
				break;
			if (nodes[mid].doc.getDocId() > 0)
				high = mid - 1;
			else
				low = mid + 1;

		}
		for (int i = mid; i > 0; i--)
			if (ancestor.doc.getDocId() == nodes[i].doc.getDocId()) {
				if (hasAncestor(nodes[i].doc, ancestor.gid, nodes[i].gid))
					result.add(nodes[i]);
				else
					break;
			}
		for (int i = mid + 1; i < counter; i++)
			if (ancestor.doc.getDocId() == nodes[i].doc.getDocId()) {
				if (hasAncestor(nodes[i].doc, ancestor.gid, nodes[i].gid))
					result.add(nodes[i]);
				else
					break;
			}
		return result;
	}

	/**
	 *  For a given set of potential ancestor nodes, get the
	 * descendants in this node set
	 *
	 *@param  al    Description of the Parameter
	 *@param  mode  Description of the Parameter
	 *@return       The descendants value
	 */
	public ArraySet getDescendants(ArraySet al, int mode) {
		if (al.counter == 0 || counter == 0)
			return new ArraySet(1);
		long start = System.currentTimeMillis();
		al.sort();
		sort();
		// the descendant set will be modified: copy if required 
		NodeProxy[] dl = null;
		if (mode == DESCENDANT) {
			dl = new NodeProxy[counter];
				for (int i = 0; i < counter; i++)
					dl[i] = new NodeProxy(nodes[i]);
		} else
			dl = nodes;

		ArraySet result = new ArraySet(al.counter);
		int ax;
		int dx;
		int cmp;
		boolean more = false;
		do {
			// calculate parent id for each node in the
			// descendant set. Returns false if no more
			// valid nodes are found
			more = getParentSet(dl);
			ax = 0;
			dx = 0;
			while (dx < dl.length) {
				if (dl[dx] == null) { // || dl[dx].gid < 1) {
					dx++;
					continue;
				}
				//				System.out.println(dl[dx].gid + " = " + al.nodes[ax].gid);
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
					switch (mode) {
						case ANCESTOR :
							dl[dx].addMatches(al.nodes[ax].matches);
							result.add(dl[dx]);
							break;
						case DESCENDANT :
							nodes[dx].addMatches(al.nodes[ax].matches);
							result.add(nodes[dx]);
							break;
					}
					dx++;
				}
			}
		}
		while (more);
		LOG.debug(
			"getDescendants took "
				+ (System.currentTimeMillis() - start)
				+ "ms.");
		return result;
	}

	/**
	 *  Gets the last attribute of the ArraySet object
	 *
	 *@return    The last value
	 */
	public int getLast() {
		return counter;
	}

	/**
	 *  Gets the length attribute of the ArraySet object
	 *
	 *@return    The length value
	 */
	public int getLength() {
		return counter;
	}

	/**
	 *  Gets the range attribute of the ArraySet object
	 *
	 *@param  lower  Description of the Parameter
	 *@param  upper  Description of the Parameter
	 *@return        The range value
	 */
	public NodeSet getRange(NodeProxy lower, NodeProxy upper) {
		sort();
		return searchRange(nodes, 0, counter - 1, lower, upper);
	}

	/**
	 *  Gets the range attribute of the ArraySet object
	 *
	 *@param  doc    Description of the Parameter
	 *@param  lower  Description of the Parameter
	 *@param  upper  Description of the Parameter
	 *@return        The range value
	 */
	public NodeSet getRange(DocumentImpl doc, long lower, long upper) {
		return getRange(new NodeProxy(doc, lower), new NodeProxy(doc, upper));
	}

	private boolean hasAncestor(DocumentImpl doc, long ancestor, long node) {
		int level;
		long pid;
		while (node > ancestor) {
			level = doc.getTreeLevel(node);
			// calculate parent's gid
			pid =
				(node - doc.getLevelStartPoint(level))
					/ doc.getTreeLevelOrder(level)
					+ doc.getLevelStartPoint(level - 1);
			if (pid == ancestor)
				return true;
			node = pid;
		}
		return false;
	}

	private boolean hasParent(DocumentImpl doc, long parent, long node) {
		int level = doc.getTreeLevel(node);
		// calculate parent's gid
		long pid =
			(node - doc.getLevelStartPoint(level))
				/ doc.getTreeLevelOrder(level)
				+ doc.getLevelStartPoint(level - 1);
		return pid == parent;
	}

	/**
	 *  Gets the sorted attribute of the ArraySet object
	 *
	 *@return    The sorted value
	 */
	protected boolean isSorted() {
		return sorted;
	}

	/**
	 *  Description of the Method
	 *
	 *@param  pos  Description of the Parameter
	 *@return      Description of the Return Value
	 */
	public Node item(int pos) {
		if (pos >= counter || pos < 0)
			return null;
		sort();
		NodeProxy p = nodes[pos];
		return p.doc.getNode(p.gid);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public Iterator iterator() {
		sort();
		return new ArraySetIterator();
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc           Description of the Parameter
	 *@param  gid           Description of the Parameter
	 *@param  directParent  Description of the Parameter
	 *@param  includeSelf   Description of the Parameter
	 *@return               Description of the Return Value
	 */
	public boolean nodeHasParent(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		sort();
		return super.nodeHasParent(doc, gid, directParent, includeSelf);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc           Description of the Parameter
	 *@param  gid           Description of the Parameter
	 *@param  directParent  Description of the Parameter
	 *@return               Description of the Return Value
	 */
	public boolean nodeHasParent(
		DocumentImpl doc,
		long gid,
		boolean directParent) {
		sort();
		return super.nodeHasParent(doc, gid, directParent, false);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  doc           Description of the Parameter
	 *@param  gid           Description of the Parameter
	 *@param  directParent  Description of the Parameter
	 *@param  includeSelf   Description of the Parameter
	 *@return               Description of the Return Value
	 */
	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		sort();
		return super.parentWithChild(doc, gid, directParent, includeSelf);
	}

	public NodeProxy parentWithChild(
		NodeProxy proxy,
		boolean directParent,
		boolean includeSelf) {
		sort();
		return parentWithChild(
			proxy.doc,
			proxy.gid,
			directParent,
			includeSelf,
			-1);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  test  Description of the Parameter
	 *@return       Description of the Return Value
	 */
	public int position(NodeImpl test) {
		sort();
		NodeProxy p = new NodeProxy(test.ownerDocument, test.getGID());
		return search(nodes, 0, counter - 1, p);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  proxy  Description of the Parameter
	 *@return        Description of the Return Value
	 */
	public int position(NodeProxy proxy) {
		sort();
		return search(nodes, 0, counter - 1, proxy);
	}

	/**
	 *  Description of the Method
	 *
	 *@param  node  Description of the Parameter
	 */
	public void remove(NodeProxy node) {
		long start = System.currentTimeMillis();
		int pos = search(nodes, 0, counter - 1, node);
		if (pos < 0)
			return;
		NodeProxy[] temp = new NodeProxy[counter];
		System.arraycopy(nodes, 0, temp, 0, pos - 2);
		System.arraycopy(nodes, pos + 1, temp, pos + 1, temp.length - pos - 1);
		nodes = temp;
		counter--;
		LOG.debug(
			"removal of node took " + (System.currentTimeMillis() - start));
	}

	/**
	 *  Description of the Method
	 *
	 *@param  position  Description of the Parameter
	 *@param  doc       Description of the Parameter
	 *@param  nodeId    Description of the Parameter
	 */
	public void set(int position, DocumentImpl doc, long nodeId) {
		if (position >= counter)
			throw new ArrayIndexOutOfBoundsException("out of bounds");
		nodes[position].gid = nodeId;
		nodes[position].doc = doc;
	}

	/**
	 *  Sets the isSorted attribute of the ArraySet object
	 *
	 *@param  sorted  The new isSorted value
	 */
	public void setIsSorted(boolean sorted) {
		//this.sorted = sorted;
	}

	/**  Description of the Method */
	public void sort() {
		if (this.sorted || counter < 2)
			return;
		quickSort(nodes, 0, counter - 1);
		//Arrays.sort(nodes, 0, counter - 1);
		sorted = true;
	}

	/**
	 *  Description of the Class
	 *
	 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
	 *@created    22. Juli 2002
	 */
	public class ArraySetIterator implements Iterator {

		protected int pos = 0;

		/**
		 *  Description of the Method
		 *
		 *@return    Description of the Return Value
		 */
		public boolean hasNext() {
			return (pos < counter) ? true : false;
		}

		/**
		 *  Description of the Method
		 *
		 *@return    Description of the Return Value
		 */
		public Object next() {
			return hasNext() ? nodes[pos++] : null;
		}

		/**  Description of the Method */
		public void remove() {
		}
	}
}
