/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.memtree;

import org.exist.dom.NodeSet;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.DOMStreamerPool;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.NodeValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class NodeImpl implements Node, NodeValue, Sequence {

	protected int nodeNumber;
	protected DocumentImpl document;

	public NodeImpl(DocumentImpl doc, int nodeNumber) {
		this.document = doc;
		this.nodeNumber = nodeNumber;
	}

	public int getNodeNumber() {
		return nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NodeValue#getImplementation()
	 */
	public int getImplementationType() {
		return NodeValue.IN_MEMORY_NODE;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		switch (getNodeType()) {
			case Node.DOCUMENT_NODE :
				return "#document";
			case Node.ATTRIBUTE_NODE :
			case Node.ELEMENT_NODE :
			case Node.PROCESSING_INSTRUCTION_NODE :
				return document.nodeName[nodeNumber].toString();
			case Node.TEXT_NODE :
				return "#text";
			default :
				return "#unknown";
		}
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	public void setNodeValue(String arg0) throws DOMException {
		// TODO Auto-generated method stub
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		return document.nodeKind[nodeNumber];
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		int next = document.next[nodeNumber];
		while (next > nodeNumber) {
			next = document.next[next];
			System.out.println(next);
		}
		if (next < 0)
			return document;
		return document.getNode(next);
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		return nodeNumber == ((NodeImpl) obj).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NodeValue#equals(org.exist.xpath.value.NodeValue)
	 */
	public boolean equals(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			throw new XPathException("annot compare persistent node with in-memory node");
		return nodeNumber == ((NodeImpl) other).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NodeValue#after(org.exist.xpath.value.NodeValue)
	 */
	public boolean after(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			throw new XPathException("annot compare persistent node with in-memory node");
		return nodeNumber < ((NodeImpl) other).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NodeValue#before(org.exist.xpath.value.NodeValue)
	 */
	public boolean before(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			throw new XPathException("annot compare persistent node with in-memory node");
		return nodeNumber > ((NodeImpl) other).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	public NodeList getChildNodes() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNextSibling()
	 */
	public Node getNextSibling() {
		int nextNr = document.next[nodeNumber];
		return nextNr < nodeNumber ? null : document.getNode(nextNr);
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getAttributes()
	 */
	public NamedNodeMap getAttributes() {
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return document;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node insertBefore(Node arg0, Node arg1) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node arg0, Node arg1) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild(Node arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#appendChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node arg0) throws DOMException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
		return false;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported(String arg0, String arg1) {
		// TODO Auto-generated method stub
		return false;
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setPrefix(java.lang.String)
	 */
	public void setPrefix(String arg0) throws DOMException {

	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
		return false;
	}

	/*
	 * Methods of interface Item
	 */

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getType()
	 */
	public int getType() {
		int type = getNodeType();
		switch (type) {
			case Node.DOCUMENT_NODE :
				return Type.DOCUMENT;
			case Node.COMMENT_NODE :
				return Type.COMMENT;
			case Node.PROCESSING_INSTRUCTION_NODE :
				return Type.PROCESSING_INSTRUCTION;
			case Node.ELEMENT_NODE :
				return Type.ELEMENT;
			case Node.ATTRIBUTE_NODE :
				return Type.ATTRIBUTE;
			case Node.TEXT_NODE :
				return Type.TEXT;
			default :
				return Type.NODE;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
		int level = document.treeLevel[nodeNumber];
		StringBuffer buf = null;
		int next = nodeNumber + 1;
		while (next < document.size && document.treeLevel[next] > level) {
			if (document.nodeKind[next] == Node.TEXT_NODE) {
				if (buf == null)
					buf = new StringBuffer();
				buf.append(
					document.characters,
					document.alpha[next],
					document.alphaLen[next]);
			}
			++next;
		}
		return buf == null ? "" : buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		return new StringValue(getStringValue()).convertTo(requiredType);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return new StringValue(getStringValue());
	}

	/*
	 * Methods of interface Sequence
	 */

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#add(org.exist.xpath.value.Item)
	 */
	public void add(Item item) throws XPathException {
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#addAll(org.exist.xpath.value.Sequence)
	 */
	public void addAll(Sequence other) throws XPathException {
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getLength()
	 */
	public int getLength() {
		return 1;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
		return null;
	}

	private final static class SingleNodeIterator implements SequenceIterator {

		NodeImpl node;

		public SingleNodeIterator(NodeImpl node) {
			this.node = node;
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return node != null;
		}

		/* (non-Javadoc)
		 * @see org.exist.xpath.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			NodeImpl next = node;
			node = null;
			return next;
		}

	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler) throws SAXException {
		try {
			DOMStreamer streamer = DOMStreamerPool.getInstance().borrowDOMStreamer();
			streamer.setContentHandler(handler);
			streamer.serialize(this, false);
			DOMStreamerPool.getInstance().returnDOMStreamer(streamer);
		} catch (Exception e) {
			throw new SAXException(e);
		}
	}
	
	public void copyTo(DBBroker broker, Receiver receiver) throws SAXException {
		toSAX(broker, receiver);
	}
}
