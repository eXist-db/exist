/*
 * SingleNodeSet.java - Jul 31, 2003
 * 
 * @author wolf
 */
package org.exist.dom;

import java.util.Iterator;

import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SingleItemIterator;
import org.w3c.dom.Node;

/**
 * A special node set containing just one, single node.
 */
public class SingleNodeSet extends ArraySet {

	public SingleNodeSet() {
		super(1);
		sorted = true;
	}
	
	public SingleNodeSet(NodeProxy node) {
		super(1);
		nodes[0] = node;
		counter = 1;
		sorted = true;
	}
 
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public Iterator iterator() {
		return new SingleNodeSetIterator();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleItemIterator(nodes[0]);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		return nodes[0].getDocument().getDocId() == doc.getDocId() && nodes[0].gid == nodeId;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
		return nodes[0].getDocument().getDocId() == proxy.getDocument().getDocId() && nodes[0].gid == proxy.gid;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
	 */
	public void addAll(NodeSet other) {
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
	 */
	public void add(NodeProxy proxy) {
		nodes[0] = proxy;
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
		return pos > 0 ? null : nodes[0].getNode();
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		return pos > 0 ? null : nodes[0];
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		return contains(p) ? nodes[0] : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl doc, long nodeId) {
		return contains(doc, nodeId) ? nodes[0] : null;
	}

	public void sort() {
	}
	
	private class SingleNodeSetIterator implements Iterator {

		private boolean hasNext = true;

		public boolean hasNext() {
			return hasNext;
		}

		public Object next() {
			if (hasNext) {
				hasNext = false;
				return nodes[0];
			} else
				return null;
		}

		public void remove() {
			throw new RuntimeException("not supported");
		}

	}

}
