
/* eXist Open Source Native XML Database
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * You should have received a copy of the GNU Library General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id:
 * 
 */
package org.exist.dom;

import org.exist.xpath.*;
import org.exist.storage.*;
import org.exist.util.XMLUtil;
import org.dbxml.core.data.Value;
import java.util.Iterator;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * This node set is called virtual because it does not really contain all
 * the relevant nodes. If the user searches for descendant::*,
 * descendant-or-self::node() etc. it would be totally unefficient to actually
 * retrieve all the descendants. Instead this class is used for such
 * cases. It basically provides method getFirstParent to retrieve the first
 * matching descendant of its context according to the primary type axis.
 *
 * Class LocationStep will always return an instance of VirtualNodeSet
 * if it finds something like descendant::* etc..
 */
public class VirtualNodeSet extends NodeSet {

	protected int axis = -1;
	protected TypeTest test;
	protected NodeSet context;
	protected NodeSet realSet = null;

	public VirtualNodeSet(int axis, TypeTest test, NodeSet context) {
		this.axis = axis;
		this.test = test;
		this.context = context;
	}

	public boolean contains(DocumentImpl doc, long nodeId) {
		NodeProxy first =
			getFirstParent(new NodeProxy(doc, nodeId), null, false, 0);
		return (first != null);
	}

	public boolean contains(NodeProxy p) {
		NodeProxy first = getFirstParent(p, null, false, 0);
		return (first != null);
	}

	protected NodeProxy getFirstParent(
		NodeProxy node,
		long gid,
		boolean includeSelf) {
		return getFirstParent(node, null, includeSelf, 0);
	}

	protected NodeProxy getFirstParent(NodeProxy node, NodeProxy first,
		boolean includeSelf, int recursions) {
		// if includeSelf is true check node during first recursion
		if (recursions == 0 && includeSelf && 
			isOfType(node.doc, node.gid, node.nodeType, test))
			return node;
		long pid = XMLUtil.getParentId(node.doc, node.gid);
		if(first == null)
			first = new NodeProxy(node.doc, pid, Node.ELEMENT_NODE);
		// is pid member of the context set?
		/*// commented out by Timo Boehme: next is wrong because we have
		 * to test for current node set */
		//NodeProxy parent = context.get(node.doc, pid);
		// -- inserted by Timo Boehme --
	 	// is pid member of the virutal set?
	 	NodeProxy parent = get(node.doc, pid);
		if (parent != null)
			return first == null ? parent : first;
		else if (pid < 0)
			return null;
		else if (axis == Constants.CHILD_AXIS && recursions == 1)
			return null;
		else {
			parent = new NodeProxy(node.doc, pid, Node.ELEMENT_NODE);
			return getFirstParent(
				parent,
				first,
				false,
				recursions + 1);
		}
	}

	protected final static boolean isOfType(
		DocumentImpl doc,
		long gid,
		short type,
		TypeTest test) {
		if (test.getNodeType() == Constants.NODE_TYPE)
			return true;
		if (type == Constants.TYPE_UNKNOWN) {
			Node node = doc.getNode(gid);
			if (node == null)
				return false;
			type = node.getNodeType();
		}
		return isOfType(type, test);
	}

	protected final static boolean isOfType(short type, TypeTest test) {
		int domType;
		switch (test.getNodeType()) {
			case Constants.ELEMENT_NODE :
				domType = Node.ELEMENT_NODE;
				break;
			case Constants.TEXT_NODE :
				domType = Node.TEXT_NODE;
				break;
			case Constants.ATTRIBUTE_NODE :
				domType = Node.ATTRIBUTE_NODE;
				break;
			case Constants.NODE_TYPE :
			default :
				return true;
		}
		return (type == domType);
	}

	public boolean nodeHasParent(DocumentImpl doc, long gid,
		boolean directParent, boolean includeSelf) {
		final NodeProxy p =
			getFirstParent(new NodeProxy(doc, gid), null, includeSelf, 0);
		if(p != null)
			addInternal(p);
		return p != null;
	}
	
	public boolean nodeHasParent(NodeProxy parent, boolean directParent,
		boolean includeSelf) {
		final NodeProxy p = getFirstParent(parent, null, includeSelf, 0);
		if(p != null)
			addInternal(p);
		return p != null;
	}

	private void addInternal(NodeProxy p) {
		if(realSet == null)
			realSet = new ArraySet(100);

		if(!realSet.contains(p))
			realSet.add(p);
	}
	
	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent) {
		return parentWithChild(doc, gid, directParent, false);
	}

	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		//if(realSet != null)
		//	return super.parentWithChild(doc, gid, directParent, includeSelf);
		NodeProxy first =
			getFirstParent(new NodeProxy(doc, gid), null, includeSelf, 0);
		return first;
	}

	public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
		boolean includeSelf) {
		NodeProxy first =
			getFirstParent(
				proxy, null,
				includeSelf, 0);
		return first;
	}
	
	private final NodeSet getNodes(boolean recursive) {
		ArraySet result = new ArraySet(100);
		Node p, c;
		NodeProxy proxy;
		NodeList cl;
		Iterator domIter;
		for (Iterator i = context.iterator(); i.hasNext();) {
			proxy = (NodeProxy) i.next();
			if (proxy.gid < 0) {
				proxy.gid = proxy.doc.getDocumentElementId();
            // -- inserted by Timo Boehme --
            NodeProxy docElemProxy = new NodeProxy(proxy.getDoc(), proxy.doc.getDocumentElementId());
            result.add(docElemProxy);
            if (recursive)
            	addChildren(result, docElemProxy.getNode(), recursive);
            continue;
                // -- end of insertion --
            }
			if (proxy.getBrokerType() == DBBroker.NATIVE) {
				domIter = proxy.doc.getBroker().getNodeIterator(proxy);
				NodeImpl node = (NodeImpl)domIter.next();
				node.setOwnerDocument(proxy.doc);
				node.setGID(proxy.gid);
				addChildren(result, node, proxy, domIter, recursive);
			} else {
				p = proxy.getNode();
				if (p == null)
					continue;
				addChildren(result, p, recursive);
			}
		}
		return result;
	}

	private final void addChildren(
		NodeSet result,
		NodeImpl node,
		NodeProxy proxy,
		Iterator iter,
		boolean recursive) {
		if (node.hasChildNodes()) {
			NodeImpl child;
			Value value;
			NodeProxy p;
			for (int i = 0; i < node.getChildCount(); i++) {
				child = (NodeImpl)iter.next();
				child.setOwnerDocument(node.getOwnerDocument());
				child.setGID(node.firstChildID() + i);
				p = new NodeProxy(child.ownerDocument, child.gid, 
					child.getNodeType(), child.internalAddress);
				p.matches = proxy.matches;
				if (isOfType(child.getNodeType(), test)) {
					result.add(p);
				} else if (axis == Constants.ATTRIBUTE_AXIS)
					return;
				if (recursive)
					addChildren(result, child, p, iter, recursive);
			}
		}
	}

	private final void addChildren(NodeSet result, Node n, boolean recursive) {
		if (n.hasChildNodes()) {
			Node c;
			NodeList cl;
			cl = n.getChildNodes();
			for (int j = 0; j < cl.getLength(); j++) {
				c = cl.item(j);
				if (isOfType(c.getNodeType(), test)) {
					//					System.out.println("found " + c.getNodeName());
					result.add(c);
				} else if (axis == Constants.ATTRIBUTE_AXIS)
					return;
				if (recursive)
					addChildren(result, c, recursive);
			}
		}
	}

	private final void realize() {
		if (realSet != null)
			return;
		System.err.println("realizing nodes");
		switch (axis) {
			case Constants.ATTRIBUTE_AXIS :
			case Constants.CHILD_AXIS :
				realSet = getNodes(false);
				break;
			case Constants.DESCENDANT_AXIS :
				realSet = getNodes(true);
				break;
		}
	}

	/* the following methods are normally never called in this context,
	 * we just provide them because they are declared abstract
	 * in the super class
	 */

	public void add(DocumentImpl doc, long nodeId) {
	}

	public void add(Node node) {
	}

	public void add(NodeProxy proxy) {
	}

	public void addAll(NodeList other) {
	}

	public void addAll(NodeSet other) {
	}

	public void set(int position, DocumentImpl doc, long nodeId) {
	}

	public void remove(NodeProxy node) {
	}

	public int getLength() {
		realize();
		return realSet.getLength();
	}

	public Node item(int pos) {
		realize();
		return realSet.item(pos);
	}

	public NodeProxy get(int pos) {
		realize();
		return realSet.get(pos);
	}

	public NodeProxy get(DocumentImpl doc, long gid) {
		realize();
		return realSet.get(doc, gid);
	}
	
	public NodeProxy get(NodeProxy proxy) {
		realize();
		return realSet.get(proxy);
	}

	public Iterator iterator() {
		realize();
		return realSet.iterator();
	}

	public NodeSet intersection(NodeSet other) {
		realize();
		return realSet.intersection(other);
	}

	public NodeSet union(NodeSet other) {
		realize();
		return realSet.union(other);
	}

	public boolean hasValues() {
		return false;
	}

	public int getLast() {
		realize();
		return realSet.getLength();
	}
}
