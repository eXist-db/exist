/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

import java.util.Comparator;

import org.exist.xpath.XPathException;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;
import org.w3c.dom.Node;

/**
 *  Placeholder class for any DOM-node. NodeProxy stores a node's unique id and
 *  the document a node belongs to. eXist will always try to use a NodeProxy
 *  instead of the actual node. Using a NodeProxy is much cheaper than loading
 *  the actual node from the database. All sets of type NodeSet operate on
 *  NodeProxys. NodeProxy implements Comparable, which is needed by all
 *  node-sets. To convert a NodeProxy to a real node, simply call getNode().
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    22. Juli 2002
 */
public final class NodeProxy implements Item, Comparable {

	public DocumentImpl doc = null;
	public long gid = 0;
	public short nodeType = -1;
	public Match match = null;
	private ContextItem context = null;
	private long internalAddress = -1;

	public NodeProxy() {
	}

	/**
	 *  Construct a node proxy with unique id gid and owned by document doc.
	 *
	 *@param  doc  Description of the Parameter
	 *@param  gid  Description of the Parameter
	 */
	public NodeProxy(DocumentImpl doc, long gid) {
		this.doc = doc;
		this.gid = gid;
	}

	/**
	 *  as above, but a hint is given about the node type of this proxy-object.
	 *
	 *@param  doc       Description of the Parameter
	 *@param  gid       Description of the Parameter
	 *@param  nodeType  Description of the Parameter
	 */
	public NodeProxy(DocumentImpl doc, long gid, short nodeType) {
		this.doc = doc;
		this.gid = gid;
		this.nodeType = nodeType;
	}

	public NodeProxy(
		DocumentImpl doc,
		long gid,
		short nodeType,
		long address) {
		this.doc = doc;
		this.gid = gid;
		this.nodeType = nodeType;
		this.internalAddress = address;
	}

	public NodeProxy(DocumentImpl doc, long gid, long address) {
		this.gid = gid;
		this.doc = doc;
		this.internalAddress = address;
	}

	public NodeProxy(NodeProxy p) {
		doc = p.doc;
		gid = p.gid;
		nodeType = p.nodeType;
		match = p.match;
		internalAddress = p.internalAddress;
	}

	public NodeProxy(NodeImpl node) {
		this((DocumentImpl) node.getOwnerDocument(), node.getGID());
		internalAddress = node.getInternalAddress();
	}

	public int compareTo(NodeProxy other) {
		final int diff = doc.docId - other.doc.docId;
		return diff == 0
			? (gid < other.gid ? -1 : (gid > other.gid ? 1 : 0))
			: diff;
	}

	public int compareTo(Object other) {
		final NodeProxy p = (NodeProxy) other;
		if (doc.docId == p.doc.docId) {
			if (gid == p.gid)
				return 0;
			else if (gid < p.gid)
				return -1;
			else
				return 1;
		} else if (doc.docId < p.doc.docId)
			return -1;
		else
			return 1;
	}

	public boolean equals(Object other) {
		if (!(other instanceof NodeProxy))
			throw new RuntimeException("cannot compare nodes from different implementations");
		NodeProxy node = (NodeProxy) other;
		if (node.doc.getDocId() == doc.getDocId() && node.gid == gid)
			return true;
		return false;
	}

	public DocumentImpl getDoc() {
		return doc;
	}

	public long getGID() {
		return gid;
	}

	public Node getNode() {
		return doc.getNode(this);
	}

	public short getNodeType() {
		return nodeType;
	}

	public String getNodeValue() {
		return doc.getBroker().getNodeValue(this);
	}

	public void setGID(long gid) {
		this.gid = gid;
	}

	public String toString() {
		return doc.getNode(gid).toString();
	}

	public static class NodeProxyComparator implements Comparator {

		public static NodeProxyComparator instance = new NodeProxyComparator();

		public int compare(Object obj1, Object obj2) {
			if (obj1 == null || obj2 == null)
				throw new NullPointerException("cannot compare null values");
			if (!(obj1 instanceof NodeProxy && obj2 instanceof NodeProxy))
				throw new RuntimeException(
					"cannot compare nodes " + "from different implementations");
			NodeProxy p1 = (NodeProxy) obj1;
			NodeProxy p2 = (NodeProxy) obj2;
			if (p1.doc.docId == p2.doc.docId) {
				if (p1.gid == p2.gid)
					return 0;
				else if (p1.gid < p2.gid)
					return -1;
				else
					return 1;
			} else if (p1.doc.docId < p2.doc.docId)
				return -1;
			else
				return 1;
		}
	}

	/**
		 * Sets the doc this node belongs to.
		 * @param doc The doc to set
		 */
	public void setDoc(DocumentImpl doc) {
		this.doc = doc;
	}

	/**
		 * Sets the nodeType.
		 * @param nodeType The nodeType to set
		 */
	public void setNodeType(short nodeType) {
		this.nodeType = nodeType;
	}

	/**
		 * Returns the storage address of this node in dom.dbx.
		 * @return long
		 */
	public long getInternalAddress() {
		return internalAddress;
	}

	/**
	 * Sets the storage address of this node in dom.dbx.
	 * 
	 * @param internalAddress The internalAddress to set
	 */
	public void setInternalAddress(long internalAddress) {
		this.internalAddress = internalAddress;
	}

	public void setHasIndex(boolean hasIndex) {
		internalAddress =
			(hasIndex
				? internalAddress | 0x10000L
				: internalAddress & (~0x10000L));
	}

	public boolean hasIndex() {
		return (internalAddress & 0x10000L) > 0;
	}

	public boolean hasMatch(Match m) {
		if (m == null || match == null)
			return false;
		Match next = match;
		do {
			if (next.equals(m))
				return true;
		} while ((next = next.getNextMatch()) != null);
		return false;
	}

	public void addMatch(Match m) {
		if (match == null) {
			match = m;
			match.prevMatch = null;
			match.nextMatch = null;
			return;
		}
		Match next = match;
		int cmp;
		while (true) {
			cmp = m.compareTo(next);
			if (cmp < 0) {
				if (next.prevMatch != null)
					next.prevMatch.nextMatch = m;
				else
					match = m;
				m.prevMatch = next.prevMatch;
				next.prevMatch = m;
				m.nextMatch = next;
				break;
			} else if (cmp == 0 && m.getNodeId() == next.getNodeId())
				break;
			if (next.nextMatch == null) {
				next.nextMatch = m;
				m.prevMatch = next;
				m.nextMatch = null;
				break;
			}
			next = next.nextMatch;
		}
	}

	public void addMatches(Match m) {
		Match next;
		while (m != null) {
			next = m.nextMatch;

			addMatch(m);
			m = next;
		}
	}

	public void printMatches() {
		System.out.print(gid);
		System.out.print(": ");
		Match next = match;
		while (next != null) {
			System.out.print(next.getMatchingTerm());
			System.out.print(" ");
			next = next.nextMatch;
		}
		System.out.println();
	}

	public void addContextNode(NodeProxy node) {
		if (context == null) {
			context = new ContextItem(node);
			return;
		}
		ContextItem next = context;
		while (next != null) {
			if (next.getNextItem() == null) {
				next.setNextItem(new ContextItem(node));
				return;
			}
			next = next.getNextItem();
		}
		//		System.out.print(gid + " context: ");
		//		for(Iterator i = contextNodes.iterator(); i.hasNext(); ) {
		//			System.out.print(((NodeProxy)i.next()).gid + " ");
		//		}
		//		System.out.println();
	}

	public void copyContext(NodeProxy node) {
		context = node.getContext();
	}

	public ContextItem getContext() {
		return context;
	}

	//	methods of interface Item

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getType()
	 */
	public int getType() {
		switch (nodeType) {
			case Node.ELEMENT_NODE :
				return Type.ELEMENT;
			case Node.ATTRIBUTE_NODE :
				return Type.ATTRIBUTE;
			case Node.TEXT_NODE :
				return Type.TEXT;
			case Node.PROCESSING_INSTRUCTION_NODE :
				return Type.PROCESSING_INSTRUCTION;
			case Node.COMMENT_NODE :
				return Type.COMMENT;
			default :
				return Type.NODE; // unknown type
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return new SingleNodeSet(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		return new StringValue(getNodeValue()).convertTo(requiredType);
	}
}
