/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 */

package org.exist.dom;

import it.unimi.dsi.fastutil.ObjectAVLTreeSet;

import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Category;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NodeIDSet extends NodeSet {

	private static Category LOG =
		Category.getInstance(NodeIDSet.class.getName());
	protected ArrayList list = new ArrayList();
	protected ObjectAVLTreeSet set = 
		new ObjectAVLTreeSet(NodeProxy.NodeProxyComparator.instance);

	public NodeIDSet() {
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

	public void set(int position, DocumentImpl doc, long nodeId) {
		if (position >= list.size())
			throw new ArrayIndexOutOfBoundsException("out of bounds");
		NodeProxy old = (NodeProxy) list.get(position);
		old.doc = doc;
		old.gid = nodeId;
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
		return p == null ? null : p.doc.getNode(p.gid);
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
		for(Iterator i = set.iterator(); i.hasNext(); ) {
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

	public int position(NodeImpl node) {
		NodeProxy p = new NodeProxy(node.ownerDocument, node.getGID());
		return list.indexOf(p);
	}

	public int position(NodeProxy proxy) {
		return list.indexOf(proxy);
	}

	public int getLast() {
		return list.size();
	}
}
