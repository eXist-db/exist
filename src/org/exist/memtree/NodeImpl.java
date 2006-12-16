/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.dom.*;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.DOMStreamer;
import org.exist.util.serializer.Receiver;
import org.exist.util.serializer.SerializerPool;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;
import org.exist.xquery.value.ValueSequence;
import org.exist.numbering.NodeId;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.UserDataHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.util.Iterator;

public class NodeImpl implements Node, NodeValue, QNameable, Comparable {

    public final static short REFERENCE_NODE = 100;
    public final static short NAMESPACE_NODE = 101;
    
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
	 * @see org.exist.xquery.value.NodeValue#getImplementation()
	 */
	public int getImplementationType() {
		return NodeValue.IN_MEMORY_NODE;
	}
	
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return DocumentSet.EMPTY_DOCUMENT_SET;
    }

    public Iterator getCollectionIterator() {
        return EmptyNodeSet.EMPTY_ITERATOR;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#getNode()
	 */
	public Node getNode() {
		return this;
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeName()
	 */
	public String getNodeName() {
		switch (getType()) {
			case Type.DOCUMENT :
				return "#document";
			case Type.ELEMENT :
			case Type.PROCESSING_INSTRUCTION :
				QName qn = (QName)
					document.namePool.get(document.nodeName[nodeNumber]);
				//TODO : check !
				return qn.getStringValue();
			case Type.ATTRIBUTE:
				return ((QName)document.namePool.get(document.attrName[nodeNumber])).getStringValue();
			case Type.NAMESPACE:
				return ((QName)document.namePool.get(document.namespaceCode[nodeNumber])).getStringValue();
			case Type.TEXT :
				return "#text";
			case Type.COMMENT :
				return "#comment";
			case Type.CDATA_SECTION :
				return "#cdata-section";
			default :
				return "#unknown";
		}
	}

	//TODO : what are the semantics ? IMHO, QName.EMPTY_QNAME shouldn't be QNameable ! -pb
	public QName getQName() {
	    switch (getNodeType()) {			
			case Node.ATTRIBUTE_NODE :
			case Node.ELEMENT_NODE :
			case Node.PROCESSING_INSTRUCTION_NODE :
				QName qn = (QName)
					document.namePool.get(document.nodeName[nodeNumber]);
				return qn;
			case Node.DOCUMENT_NODE :
				return QName.EMPTY_QNAME;		
			case Node.COMMENT_NODE:
			    return QName.EMPTY_QNAME;		
			case Node.TEXT_NODE :
				return QName.EMPTY_QNAME;		
			case Node.CDATA_SECTION_NODE :
				return QName.EMPTY_QNAME;		
			default :
				return null;
		}
	}
	
    public void expand() throws DOMException {
        document.expand();
    }
    
    public void deepCopy() throws DOMException {
    	DocumentImpl newDoc = document.expandRefs(this);
    	if (newDoc != document) {
    		// we received a new document
	    	this.nodeNumber = 1;
	    	this.document = newDoc;
    	}
    }
    
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeValue()
	 */
	public String getNodeValue() throws DOMException {
		throw new RuntimeException(getClass().getName() + ": can not call getNodeValue() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setNodeValue(java.lang.String)
	 */
	public void setNodeValue(String arg0) throws DOMException {
		throw new RuntimeException("Can not call setNodeValue() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNodeType()
	 */
	public short getNodeType() {
		//Workaround for fn:string-length(fn:node-name(document {""}))
		if (this.document == null)
			return Node.DOCUMENT_NODE;
		return document.nodeKind[nodeNumber];
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getParentNode()
	 */
	public Node getParentNode() {
		int next = document.next[nodeNumber];
		while (next > nodeNumber) {
			next = document.next[next];
		}
		if (next < 0)
			return document;
		return document.getNode(next);
	}

    public void addContextNode(int contextId, NodeValue node) {
    	throw new RuntimeException("Can not call addContextNode() on node type " + this.getNodeType());
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if(!(obj instanceof NodeImpl))
			return false;
		return nodeNumber == ((NodeImpl) obj).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#equals(org.exist.xquery.value.NodeValue)
	 */
	public boolean equals(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			return false;
		return nodeNumber == ((NodeImpl) other).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#after(org.exist.xquery.value.NodeValue)
	 */
	public boolean after(NodeValue other, boolean isFollowing) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			throw new XPathException("annot compare persistent node with in-memory node"); 
		return nodeNumber < ((NodeImpl) other).nodeNumber;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#before(org.exist.xquery.value.NodeValue)
	 */
	public boolean before(NodeValue other, boolean isPreceding) throws XPathException {
		if (other.getImplementationType() != NodeValue.IN_MEMORY_NODE)
			throw new XPathException("annot compare persistent node with in-memory node");
		return nodeNumber > ((NodeImpl) other).nodeNumber;
	}

	public int compareTo(Object other) {
		if(!(other instanceof NodeImpl))
			return Constants.INFERIOR;
		NodeImpl n = (NodeImpl)other;
		if(n.document == document) {
			if (nodeNumber == n.nodeNumber)
				return Constants.EQUAL;
			else if (nodeNumber < n.nodeNumber)
				return Constants.INFERIOR;
			else
				return Constants.SUPERIOR;
		} else
			return Constants.INFERIOR;
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getChildNodes()
	 */
	public NodeList getChildNodes() {
		throw new RuntimeException("Can not call getChildNodes() on node type " + this.getNodeType());
		//return new NodeListImpl();
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getFirstChild()
	 */
	public Node getFirstChild() {
		throw new RuntimeException("Can not call getFirstChild() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLastChild()
	 */
	public Node getLastChild() {
		throw new RuntimeException("Can not call getLastChild() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPreviousSibling()
	 */
	public Node getPreviousSibling() {
		//TODO : we have a getNextSibling() method !
		throw new RuntimeException("Can not call getPreviousSibling() on node type " + this.getNodeType());
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
        throw new RuntimeException("Can not call getAttributes() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getOwnerDocument()
	 */
	public Document getOwnerDocument() {
		return document;
	}

	public DocumentImpl getDocument() {
	    return document;
	}
	
	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#insertBefore(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node insertBefore(Node arg0, Node arg1) throws DOMException {
		throw new RuntimeException("Can not call insertBefore() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#replaceChild(org.w3c.dom.Node, org.w3c.dom.Node)
	 */
	public Node replaceChild(Node arg0, Node arg1) throws DOMException {
        throw new RuntimeException("Can not call replaceChild() on node type " + this.getNodeType());
    }

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#removeChild(org.w3c.dom.Node)
	 */
	public Node removeChild(Node arg0) throws DOMException {
        throw new RuntimeException("Can not call removeChild() on node type " + this.getNodeType());	
    }

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#newChild(org.w3c.dom.Node)
	 */
	public Node appendChild(Node arg0) throws DOMException {
        throw new RuntimeException("Can not call appendChild() on node type " + this.getNodeType());
    }

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasChildNodes()
	 */
	public boolean hasChildNodes() {
        throw new RuntimeException("Can not call hasChildNodes() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#cloneNode(boolean)
	 */
	public Node cloneNode(boolean arg0) {
        throw new RuntimeException("Can not call cloneNode() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#normalize()
	 */
	public void normalize() {
        throw new RuntimeException("Can not call normalize() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#isSupported(java.lang.String, java.lang.String)
	 */
	public boolean isSupported(String arg0, String arg1) {
		throw new RuntimeException("Can not call isSupported() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getNamespaceURI()
	 */
	public String getNamespaceURI() {
        throw new RuntimeException("Can not call getNamespaceURI() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getPrefix()
	 */
	public String getPrefix() {
        throw new RuntimeException("Can not call getPrefix() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#setPrefix(java.lang.String)
	 */
	public void setPrefix(String arg0) throws DOMException {
        throw new RuntimeException("Can not call setPrefix() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#getLocalName()
	 */
	public String getLocalName() {
        throw new RuntimeException("Can not call getLocalName() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.w3c.dom.Node#hasAttributes()
	 */
	public boolean hasAttributes() {
        throw new RuntimeException("Can not call hasAttributes() on node type " + this.getNodeType());
	}

	/*
	 * Methods of interface Item
	 */

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getType()
	 */
	public int getType() {
		//Workaround for fn:string-length(fn:node-name(document {""}))
		if (this.document == null)
			return Type.DOCUMENT;		
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
			case Node.CDATA_SECTION_NODE :
				return Type.CDATA_SECTION;
			default :
				return Type.NODE;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
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
			} else if (document.nodeKind[next] == REFERENCE_NODE) {
				if (buf == null)
					buf = new StringBuffer();
				buf.append(document.references[document.alpha[next]].getStringValue());
			}
			++next;
		}
		return buf == null ? "" : buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		return new StringValue(getStringValue()).convertTo(requiredType);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return new UntypedAtomicValue(getStringValue());
	}

	/*
	 * Methods of interface Sequence
	 */

	public boolean isEmpty() {
		return false;
	}
	
	public boolean hasOne() {
		return true;
	}
	
	public boolean hasMany() {
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
	 */
	public void add(Item item) throws XPathException {
		throw new RuntimeException("Can not call add() on node type " + this.getNodeType());
	}	

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
	 */
	public void addAll(Sequence other) throws XPathException {
		throw new RuntimeException("Can not call addAll() on node type " + this.getNodeType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getItemType()
	 */
	public int getItemType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#iterate()
	 */
	public SequenceIterator iterate() throws XPathException {
		return new SingleNodeIterator(this);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getLength()
	 */
	public int getLength() {
		return 1;
	}

	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		//A node evaluates to true()
		return true;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#toNodeSet()
	 */
	public NodeSet toNodeSet() throws XPathException {
//		throw new XPathException("Querying constructed nodes is not yet implemented");
        ValueSequence seq = new ValueSequence();
        seq.add(this);
        return seq.toNodeSet();
	}

	private final static class SingleNodeIterator implements SequenceIterator {

		NodeImpl node;

		public SingleNodeIterator(NodeImpl node) {
			this.node = node;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#hasNext()
		 */
		public boolean hasNext() {
			return node != null;
		}

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			NodeImpl next = node;
			node = null;
			return next;
		}

	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler) throws SAXException {
	    DOMStreamer streamer = null;
		try {
		    Serializer serializer = broker.getSerializer();
		    serializer.reset();
			serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
			serializer.setSAXHandlers(handler, null);
            streamer = SerializerPool.getInstance().borrowDOMStreamer(serializer);
			streamer.setContentHandler(handler);
			streamer.serialize(this, false);
		} catch (Exception e) {
		    SerializerPool.getInstance().returnObject(streamer);
		    e.printStackTrace();
			throw new SAXException(e);
		}
	}

	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
		//Null test for document nodes
		if (document != null)
			document.copyTo(this, receiver);
	}

	public void streamTo(Serializer serializer, Receiver receiver) throws SAXException {
		//Null test for document nodes
		if (document != null)
			document.streamTo(serializer, this, receiver);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(NodeImpl.class))
			return 0;
		if (javaClass.isAssignableFrom(Node.class))
			return 1;
		if (javaClass == String.class || javaClass == CharSequence.class)
			return 2;
		if (javaClass == Character.class || javaClass == char.class)
			return 2;
		if (javaClass == Double.class || javaClass == double.class)
			return 10;
		if (javaClass == Float.class || javaClass == float.class)
			return 11;
		if (javaClass == Long.class || javaClass == long.class)
			return 12;
		if (javaClass == Integer.class || javaClass == int.class)
			return 13;
		if (javaClass == Short.class || javaClass == short.class)
			return 14;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 15;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 16;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(NodeImpl.class))
			return this;
		else if (target.isAssignableFrom(Node.class))
			return this;
		else if (target == Object.class)
			return this;
		else {
			StringValue v = new StringValue(getStringValue());
			return v.toJavaObject(target);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setSelfAsContext(int)
	 */
	public void setSelfAsContext(int contextId) {
		throw new RuntimeException("Can not call setSelfAsContext() on node type " + this.getNodeType());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#isCached()
	 */
	public boolean isCached() {
		// always return false
		return false;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
	 */
	public void setIsCached(boolean cached) {
		// ignore
		throw new RuntimeException("Can not call setIsCached() on node type " + this.getNodeType());
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // do nothing: this is a single node
    }

	/** ? @see org.w3c.dom.Node#getBaseURI()
	 */
	public String getBaseURI() {
        return null;
	}

	/** ? @see org.w3c.dom.Node#compareDocumentPosition(org.w3c.dom.Node)
	 */
	public short compareDocumentPosition(Node other) throws DOMException {
		throw new RuntimeException("Can not call compareDocumentPosition() on node type " + this.getNodeType());
		//return 0;
	}

	/** ? @see org.w3c.dom.Node#getTextContent()
	 */
	public String getTextContent() throws DOMException {
		throw new RuntimeException("Can not call getTextContent() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#setTextContent(java.lang.String)
	 */
	public void setTextContent(String textContent) throws DOMException {
		throw new RuntimeException("Can not call setTextContent() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#isSameNode(org.w3c.dom.Node)
	 */
	public boolean isSameNode(Node other) {
		throw new RuntimeException("Can not call isSameNode() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#lookupPrefix(java.lang.String)
	 */
	public String lookupPrefix(String namespaceURI) {
		throw new RuntimeException("Can not call lookupPrefix() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#isDefaultNamespace(java.lang.String)
	 */
	public boolean isDefaultNamespace(String namespaceURI) {
		throw new RuntimeException("Can not call isDefaultNamespace() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#lookupNamespaceURI(java.lang.String)
	 */
	public String lookupNamespaceURI(String prefix) {
		throw new RuntimeException("Can not call lookupNamespaceURI() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#isEqualNode(org.w3c.dom.Node)
	 */
	public boolean isEqualNode(Node arg) {
		throw new RuntimeException("Can not call isEqualNode() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#getFeature(java.lang.String, java.lang.String)
	 */
	public Object getFeature(String feature, String version) {
		throw new RuntimeException("Can not call getFeature() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#setUserData(java.lang.String, java.lang.Object, org.w3c.dom.UserDataHandler)
	 */
	public Object setUserData(String key, Object data, UserDataHandler handler) {
		throw new RuntimeException("Can not call setUserData() on node type " + this.getNodeType());
	}

	/** ? @see org.w3c.dom.Node#getUserData(java.lang.String)
	 */
	public Object getUserData(String key) {
		throw new RuntimeException("Can not call getUserData() on node type " + this.getNodeType());
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
    	//See package's name ;-)
        return false;
    }

    public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
        // can not be applied to in-memory nodes
    }

    public void clearContext(int contextId) {
		throw new RuntimeException("Can not call clearContext() on node type " + this.getNodeType());
	}
}
