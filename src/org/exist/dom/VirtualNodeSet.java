
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
 * This node set is called virtual because it is just a placeholder for
 * the set of relevant nodes. For XPath expressions like //* or //node(), 
 * it would be totally unefficient to actually retrieve all descendant nodes.
 * In many cases, the expression can be resolved at a later point in time
 * without retrieving the whole node set. 
 *
 * VirtualNodeSet basically provides method getFirstParent to retrieve the first
 * matching descendant of its context according to the primary type axis.
 *
 * Class LocationStep will always return an instance of VirtualNodeSet
 * if it finds something like descendant::* etc..
 *
 * @author Wolfgang Meier
 * @author Timo Boehme
 */
public class VirtualNodeSet extends NodeSet {

	protected int axis = -1;
	protected TypeTest test;
	protected NodeSet context;
	protected NodeSet realSet = null;
	protected boolean inPredicate = false;
	protected boolean useSelfAsContext = false;
	
	public VirtualNodeSet(int axis, TypeTest test, NodeSet context) {
		this.axis = axis;
		this.test = test;
		this.context = context;
	}

	public boolean contains(DocumentImpl doc, long nodeId) {
		NodeProxy first =
			getFirstParent(new NodeProxy(doc, nodeId), null, false, 0);
		// Timo Boehme: getFirstParent returns now only real parents
		//              therefore test if node is child of context
		//return (first != null);
		return ((first != null) ||
                        (context.get(doc, XMLUtil.getParentId(doc, nodeId)) != null));
	}

	public boolean contains(NodeProxy p) {
		NodeProxy first = getFirstParent(p, null, false, 0);
		// Timo Boehme: getFirstParent returns now only real parents
		//              therefore test if node is child of context
		//return (first != null);
		return ((first != null) ||
                        (context.get(p.doc, XMLUtil.getParentId(p.doc, p.gid)) != null));
	}

	public void setInPredicate(boolean predicate) {
		inPredicate = predicate;
	}
	
	protected NodeProxy getFirstParent(
		NodeProxy node,
		long gid,
		boolean includeSelf) {
		return getFirstParent(node, null, includeSelf, true, 0);
	}

	protected NodeProxy getFirstParent(NodeProxy node, NodeProxy first,
		boolean includeSelf, int recursions) {
	    return getFirstParent(node, first, includeSelf, true, recursions);
	}

	protected NodeProxy getFirstParent(NodeProxy node, NodeProxy first,
		boolean includeSelf, boolean directParent, int recursions) {
		long pid = XMLUtil.getParentId(node.doc, node.gid);
		NodeProxy parent;
		// check if the start-node should be included, e.g. to process an
		// expression like *[. = 'xxx'] 
		if (recursions == 0 && includeSelf && 
			test.isOfType(node, node.nodeType)) {
			if(axis == Constants.CHILD_AXIS) {
				// if we're on the child axis, test if
				// the node is a direct child of the context node
				if((parent = context.get(new NodeProxy(node.doc, pid))) != null) {
					if(useSelfAsContext && inPredicate)
						node.addContextNode(node);
					else if(inPredicate)
						node.addContextNode(parent);
					else
						node.copyContext(parent);
					return node;
				}
			} else
				// descendant axis: remember the node and continue 
				first = node;
		}
		// if this is the first call to this method, remember the first parent node
		// and re-evaluate the method
		if(first == null) {
			if(pid < 0)
				// given node was already document element -> no parent
				return null;
			first = new NodeProxy(node.doc, pid, Node.ELEMENT_NODE);
			// Timo Boehme: we need a real parent (child from context)
			return getFirstParent(first, first, false, directParent, recursions + 1);
		}

		// is pid member of the context set?
		parent = context.get(node.doc, pid);

		if (parent != null) {
			if(useSelfAsContext && inPredicate)
				node.addContextNode(node);
			else if(inPredicate)
				node.addContextNode(parent);
			else {
				node.copyContext(parent);
			}
		    // Timo Boehme: we return the ancestor which is child of context 
			return node;
		} else if (pid < 0)
			// no matching node has been found in the context
			return null;
		else if (directParent && axis == Constants.CHILD_AXIS && recursions == 1)
			// break here if the expression is like /*/n
			return null;
		else {
			// continue for expressions like //*/n or /*//n
			parent = new NodeProxy(node.doc, pid, Node.ELEMENT_NODE);
			return getFirstParent(
				parent,
				first,
				false,
				directParent,
				recursions + 1);
		}
	}

	public NodeProxy nodeHasParent(DocumentImpl doc, long gid,
		boolean directParent, boolean includeSelf) {
		final NodeProxy p =
			getFirstParent(new NodeProxy(doc, gid), null, includeSelf, directParent, 0);
		if(p != null)
			addInternal(p);
		return p;
	}
	
	public NodeProxy nodeHasParent(NodeProxy node, boolean directParent,
		boolean includeSelf) {
		final NodeProxy p = getFirstParent(node, null, includeSelf, directParent, 0);
		if(p != null)
			addInternal(p);
		return p;
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
		NodeProxy p = parentWithChild(doc, gid, directParent, false);
		if(p != null)
			addInternal(p);
		return p;
	}

	public NodeProxy parentWithChild(
		DocumentImpl doc,
		long gid,
		boolean directParent,
		boolean includeSelf) {
		NodeProxy first =
			getFirstParent(new NodeProxy(doc, gid), null, includeSelf, directParent, 0);
		if(first != null)
			addInternal(first);
		return first;
	}

	public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
		boolean includeSelf) {
		NodeProxy first =
			getFirstParent(
				proxy, null,
				includeSelf, directParent, 0);
		if(first != null) {
			addInternal(first);
		}
		return first;
	}
	
	private final NodeSet getNodes() {
		ArraySet result = new ArraySet(100);
		Node p, c;
		NodeProxy proxy;
		NodeList cl;
		Iterator domIter;
		for (Iterator i = context.iterator(); i.hasNext();) {
			proxy = (NodeProxy) i.next();
			if (proxy.gid < 0) {
/* // commented out by Timo Boehme (document element is already part of virtual node set (not parent!))
				proxy.gid = proxy.doc.getDocumentElementId();
*/
				// -- inserted by Timo Boehme --
				NodeProxy docElemProxy = new NodeProxy(proxy.getDoc(), 1);
				result.add(docElemProxy);
				if (axis == Constants.DESCENDANT_AXIS || 
					axis == Constants.DESCENDANT_SELF_AXIS) {
					domIter = docElemProxy.doc.getBroker().getNodeIterator(docElemProxy);
					NodeImpl node = (NodeImpl)domIter.next();
					node.setOwnerDocument(docElemProxy.doc);
					node.setGID(docElemProxy.gid);
					addChildren(result, node, proxy, domIter, 0);
				}
				continue;
				// -- end of insertion --
			} else if (proxy.getBrokerType() == DBBroker.NATIVE) {
				domIter = proxy.doc.getBroker().getNodeIterator(proxy);
				NodeImpl node = (NodeImpl)domIter.next();
				node.setOwnerDocument(proxy.doc);
				node.setGID(proxy.gid);
				addChildren(result, node, proxy, domIter, 0);
			}
		}
		return result;
	}

	private final void addChildren(
		NodeSet result,
		NodeImpl node,
		NodeProxy proxy,
		Iterator iter,
		int recursions) {
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
				if (axis == Constants.CHILD_AXIS && recursions == 0 &&
					test.isOfType(child.getNodeType())) {
					result.add(p);
				} else if ((axis == Constants.DESCENDANT_AXIS ||
					axis == Constants.DESCENDANT_SELF_AXIS) &&
					test.isOfType(child.getNodeType())) {
					result.add(p);
				} else if (axis == Constants.ATTRIBUTE_AXIS)
					return;
				addChildren(result, child, p, iter, recursions + 1);
			}
		}
	}

	private final void realize() {
		if (realSet != null)
			return;
		realSet = getNodes();
	}

	public void setSelfIsContext() {
		useSelfAsContext = true;
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
