/* eXist Native XML Database
 * 
 * Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.dom;

import it.unimi.dsi.fastutil.ObjectAVLTreeSet;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.SequenceIterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class TreeNodeSet extends NodeSet {

	private static Category LOG =
		Category.getInstance(TreeNodeSet.class.getName());
	protected ArrayList list = new ArrayList();
	protected ObjectAVLTreeSet set = 
		new ObjectAVLTreeSet(NodeProxy.NodeProxyComparator.instance);

	public TreeNodeSet() {
		super();
	}

	public void add(NodeProxy node) {
		if (node == null)
			return;
		set.add(node);
		list.add(node);
	}

	public void add(DocumentImpl doc, long nodeId) {
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
				((NodeImpl) node).gid);
		add(p);
	}

	public void addAll(NodeList other) {
		for (int i = 0; i < other.getLength(); i++)
			add((Node) other.item(i));
	}

	public void addAll(NodeSet other) {
		NodeProxy p;
		for (Iterator i = other.iterator(); i.hasNext();) {
			p = (NodeProxy) i.next();
			add(p);
		}
	}

	public void remove(NodeProxy node) {
		list.remove(node);
		set.remove(node);
	}

	public int getLength() {
		return list.size();
	}

	public Node item(int pos) {
		NodeProxy p = (NodeProxy) list.get(pos);
		return p == null ? null : p.doc.getNode(p);
	}

	public NodeProxy get(DocumentImpl doc, long nodeId) {
		NodeProxy p = new NodeProxy(doc, nodeId);
		return set.contains(p) ? p : null;
	}

	public NodeProxy get(int pos) {
		return (NodeProxy) list.get(pos);
	}

	public NodeProxy get(NodeProxy p) {
		NodeProxy n;
		for(Iterator i = list.iterator(); i.hasNext(); ) {
			n = (NodeProxy)i.next();
			if(n.compareTo(p) == 0)
				return n;
		}
		return null;
	}
	
	public boolean contains(NodeProxy proxy) {
		return set.contains(proxy);
	}

	public boolean contains(DocumentImpl doc, long nodeId) {
		NodeProxy p = new NodeProxy(doc, nodeId);
		return set.contains(p);
	}

	public Iterator iterator() {
		return set.iterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return new NodeIDSequenceIterator(set.iterator());
	}
	
	public int getLast() {
		return list.size();
	}
	
	private class NodeIDSequenceIterator implements SequenceIterator {
		
		private Iterator iter;
		
		public NodeIDSequenceIterator(Iterator iterator) {
			iter = iterator;
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return iter.hasNext();
		}
		
		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			return (Item)iter.next();
		}
	}
}
