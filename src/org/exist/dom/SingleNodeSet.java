/*
 * SingleNodeSet.java - Jul 31, 2003
 * 
 * @author wolf
 */
package org.exist.dom;

import java.util.Iterator;

import org.w3c.dom.Node;

/**
 * A special node set containing just one, single node.
 */
public class SingleNodeSet extends NodeSet {

	private NodeProxy node;

	public SingleNodeSet(NodeProxy node) {
		this.node = node;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public Iterator iterator() {
		return new SingleNodeSetIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		return node.doc.getDocId() == doc.getDocId() && node.gid == nodeId;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
		return node.doc.getDocId() == proxy.doc.getDocId() && node.gid == proxy.gid;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
	 */
	public void addAll(NodeSet other) {
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.NodeList#getLength()
	 */
	public int getLength() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.NodeList#item(int)
	 */
	public Node item(int pos) {
		return pos > 0 ? null : node.getNode();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		return pos > 0 ? null : node;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		return contains(p) ? node : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl doc, long nodeId) {
		return contains(doc, nodeId) ? node : null;
	}

	private class SingleNodeSetIterator implements Iterator {

		private boolean hasNext = true;

		public boolean hasNext() {
			return hasNext;
		}

		public Object next() {
			if (hasNext) {
				hasNext = false;
				return node;
			} else
				return null;
		}

		public void remove() {
			throw new RuntimeException("not supported");
		}

	}
}
