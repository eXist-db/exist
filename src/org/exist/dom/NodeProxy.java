/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

import java.util.Iterator;

import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Placeholder class for DOM nodes. 
 * 
 * NodeProxy is an internal proxy class, acting as a placeholder for all types of persistent XML nodes
 * during query processing. NodeProxy just stores the node's unique id and the document it belongs to. 
 * Query processing deals with these proxys most of the time. Using a NodeProxy is much cheaper 
 * than loading the actual node from the database. The real DOM node is only loaded,
 * if further information is required for the evaluation of an XPath expression. To obtain 
 * the real node for a proxy, simply call {@link #getNode()}. 
 * 
 * All sets of type NodeSet operate on NodeProxys. A node set is a special type of 
 * sequence, so NodeProxy does also implement {@link org.exist.xquery.value.Item} and
 * can thus be an item in a sequence. Since, according to XPath 2, a single node is also 
 * a sequence, NodeProxy does itself extend NodeSet. It thus represents a node set containing
 * just one, single node.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 */
public class NodeProxy implements NodeSet, NodeValue, Comparable {
	
	/**
	 * The owner document of this node.
	 */
	public DocumentImpl doc = null;

	/**
	 * The unique internal node id.
	 */
	public long gid = 0;

	/**
	 * The internal storage address of the node in the
	 * dom.dbx node store. This field is optional.
	 */
	private long internalAddress = -1;
	
	/**
	 * The type of this node (as defined by DOM), if known, -1 if
	 * unknown.
	 */
	public short nodeType = -1;

	/**
	 * The first {@link Match} object associated with this node.
	 * Match objects are used to track fulltext hits throughout query processing.
	 * 
	 * Matches are stored as a linked list.
	 */
	public Match match = null;

	private ContextItem context = null;
	
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

	public NodeProxy(DocumentImpl doc, long gid, short nodeType, long address) {
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

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NodeValue#getImplementation()
	 */
	public int getImplementationType() {
		return NodeValue.PERSISTENT_NODE;
	}

	public int compareTo(NodeProxy other) {
		final int diff = doc.docId - other.doc.docId;
		return diff == 0 ? (gid < other.gid ? -1 : (gid > other.gid ? 1 : 0)) : diff;
	}

	public int compareTo(Object other) {
		if(!(other instanceof NodeProxy))
			return 1;
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

	public boolean equals(NodeValue other) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");
		NodeProxy node = (NodeProxy) other;
		if (node.doc.getDocId() == doc.getDocId() && node.gid == gid)
			return true;
		return false;
	}

	public boolean before(NodeValue other) throws XPathException {
		return before(other, true);
	}
	
	protected boolean before(NodeValue other, boolean includeAncestors) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");
		NodeProxy node = (NodeProxy) other;
		if (doc.docId != node.doc.docId)
			return false;
		//		System.out.println(gid + " << " + node.gid);
		int la = doc.getTreeLevel(gid);
		int lb = doc.getTreeLevel(node.gid);
		long pa = gid, pb = node.gid;
		if (la > lb) {
			while (la > lb) {
				pa = XMLUtil.getParentId(doc, pa, la);
				--la;
			}
			if (pa == pb)
				// a is a descendant of b
				return false;
			else
				return pa < pb;
		} else if (lb > la) {
			while (lb > la) {
				pb = XMLUtil.getParentId(node.doc, pb, lb);
				--lb;
			}
			if (pb == pa)
				// a is an ancestor of b
				return includeAncestors ? true : false;
			else
				return pa < pb;
		} else
			return pa < pb;
	}

	public boolean after(NodeValue other) throws XPathException {
		return after(other, true);
	}
	
	protected boolean after(NodeValue other, boolean includeDescendants) throws XPathException {
		if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
			throw new XPathException("cannot compare persistent node with in-memory node");
		NodeProxy node = (NodeProxy) other;
		if (doc.docId != node.doc.docId)
			return false;
		//		System.out.println(gid + " >> " + node.gid);
		int la = doc.getTreeLevel(gid);
		int lb = doc.getTreeLevel(node.gid);
		long pa = gid, pb = node.gid;
		if (la > lb) {
			while (la > lb) {
				pa = XMLUtil.getParentId(doc, pa, la);
				--la;
			}
			// a is a descendant of b
			if (pa == pb)
				return includeDescendants ? true : false;
			else
				return pa > pb;
		} else if (lb > la) {
			while (lb > la) {
				pb = XMLUtil.getParentId(node.doc, pb, lb);
				--lb;
			}
			if (pb == pa)
				return false;
			else
				return pa > pb;
		} else
			return pa > pb;
	}

	public Document getOwnerDocument() {
		return doc;
	}
	
	public DocumentImpl getDocument()  {
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

	public String toString() {
		return doc.getNode(gid).toString();
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
			(hasIndex ? internalAddress | 0x10000L : internalAddress & (~0x10000L));
	}

	public boolean hasIndex() {
		return (internalAddress & 0x10000L) > 0;
	}

	public Match getMatches() {
		return match;
	}
	
	public void setMatches(Match match) {
		this.match = match;
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
		while (next != null) {
			cmp = next.compareTo(m);
			if (cmp == 0 && m.getNodeId() == next.getNodeId())
				return;
			else if (cmp < 0) {
				if (next.prevMatch != null)
					next.prevMatch.nextMatch = m;
				else
					match = m;
				m.prevMatch = next.prevMatch;
				next.prevMatch = m;
				m.nextMatch = next;
				return;
			} else if (next.nextMatch == null) {
				next.nextMatch = m;
				m.prevMatch = next;
				m.nextMatch = null;
				return;
			}
			next = next.nextMatch;
		}
	}

	public void addMatches(NodeProxy p) {
		if(p == this)
			return;
		Match m = p.getMatches();
		while (m != null) {
			addMatch(new Match(m));
			m = m.nextMatch;
		}
	}

	public void printMatches(Match m) {
		System.out.print(gid);
		System.out.print(": ");
		Match next = m;
		while (next != null) {
			System.out.print(next.getMatchingTerm() + " [" + next.getNodeId() + "] ");
			System.out.print("-> " + (next.nextMatch == null ? "null" : next.nextMatch.getMatchingTerm()));
			System.out.print(" ");
			next = next.nextMatch;
		}
		System.out.println();
	}

	/**
	 * Add a node to the list of context nodes for this node.
	 * 
	 * NodeProxy internally stores the context nodes of the XPath context, for which 
	 * this node has been selected during a previous processing step.
	 * 
	 * Since eXist tries to process many expressions in one, single processing step,
	 * the context information is required to resolve predicate expressions. For
	 * example, for an expression like //SCENE[SPEECH/SPEAKER='HAMLET'],
	 * we have to remember the SCENE nodes for which the equality expression
	 * in the predicate was true.  Thus, when evaluating the step SCENE[SPEECH], the
	 * SCENE nodes become context items of the SPEECH nodes and this context
	 * information is preserved through all following steps.
	 * 
	 * To process the predicate expression, {@link org.exist.xquery.Predicate} will take the
	 * context nodes returned by the filter expression and compare them to its context
	 * node set.
	 */
	public void addContextNode(NodeProxy node) {
		if (context == null) {
			context = new ContextItem(node);
			return;
		}
		ContextItem next = context;
		while (next != null) {
			if (next.getNode().gid == node.gid)
				break;
			if (next.getNextItem() == null) {
				next.setNextItem(new ContextItem(node));
				break;
			}
			next = next.getNextItem();
		}
	}

	public void printContext() {
		ContextItem next = context;
		System.out.print(gid + ": ");
		while (next != null) {
			System.out.print(next.getNode().gid);
			System.out.print(' ');
			next = next.getNextItem();
		}
		System.out.println();
	}
	
	public void copyContext(NodeProxy node) {
		context = node.getContext();
	}

	public void clearContext() {
		context = null;
	}
	
	public ContextItem getContext() {
		return context;
	}
	
	//	methods of interface Item

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getType()
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
			case Node.DOCUMENT_NODE:
			    return Type.DOCUMENT;
			default :
				return Type.NODE; // unknown type
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSequence()
	 */
	public Sequence toSequence() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return getNodeValue();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		return new StringValue(getNodeValue()).convertTo(requiredType);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#atomize()
	 */
	public AtomicValue atomize() throws XPathException {
		return new UntypedAtomicValue(getNodeValue());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toSAX(org.exist.storage.DBBroker, org.xml.sax.ContentHandler)
	 */
	public void toSAX(DBBroker broker, ContentHandler handler) throws SAXException {
		Serializer serializer = broker.getSerializer();
		serializer.reset();
		serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
		serializer.setSAXHandlers(handler, null);
		serializer.toSAX(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#copyTo(org.exist.storage.DBBroker, org.exist.memtree.DocumentBuilderReceiver)
	 */
	public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
	    if(nodeType == Node.ATTRIBUTE_NODE) {
	        AttrImpl attr = (AttrImpl) getNode();
	        receiver.attribute(attr.getQName(), attr.getValue());
	    } else
	        receiver.addReferenceNode(this);
//	    Serializer serializer = broker.getSerializer();
//	    serializer.toReceiver(this, receiver);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(NodeProxy.class))
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
		if (target.isAssignableFrom(NodeProxy.class))
			return this;
		else if (target.isAssignableFrom(Node.class))
			return getNode();
		else if (target == Object.class)
			return getNode();
		else {
			StringValue v = new StringValue(getStringValue());
			return v.toJavaObject(target);
		}
	}

	/*
	 * Methods of interface Sequence:
	 */
	
	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    public int getItemType() {
        return Type.NODE;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getCardinality()
     */
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isCached()
     */
    public boolean isCached() {
        return false;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setIsCached(boolean)
     */
    public void setIsCached(boolean cached) {
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#effectiveBooleanValue()
     */
    public boolean effectiveBooleanValue() throws XPathException {
        return true;
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#removeDuplicates()
     */
    public void removeDuplicates() {
        // single node: no duplicates
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#setSelfAsContext()
     */
    public void setSelfAsContext() {
        addContextNode(this);
    }
    
	/* -----------------------------------------------*
	 * Methods of class NodeSet
	 * -----------------------------------------------*/

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterator()
	 */
	public Iterator iterator() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#iterate()
	 */
	public SequenceIterator iterate() {
		return new SingleNodeIterator(this);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#unorderedIterator()
	 */
	public SequenceIterator unorderedIterator() {
		return new SingleNodeIterator(this);
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.DocumentImpl, long)
	 */
	public boolean contains(DocumentImpl doc, long nodeId) {
		return this.doc.getDocId() == doc.getDocId() && this.gid == nodeId;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
	 */
	public boolean contains(NodeProxy proxy) {
		return doc.getDocId() == proxy.doc.getDocId() && gid == proxy.gid;
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
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
     */
    public void add(Item item) throws XPathException {
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy, int)
     */
    public void add(NodeProxy proxy, int sizeHint) {
    }
    
    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
     */
    public void addAll(Sequence other) throws XPathException {
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
		return pos > 0 ? null : getNode();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#itemAt(int)
	 */
	public Item itemAt(int pos) {
		return pos > 0 ? null : this;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(int)
	 */
	public NodeProxy get(int pos) {
		return pos > 0 ? null : this;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
	 */
	public NodeProxy get(NodeProxy p) {
		return contains(p) ? this : null;
	}

	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#get(org.exist.dom.DocumentImpl, long)
	 */
	public NodeProxy get(DocumentImpl document, long nodeId) {
	    if(doc.getDocId() == document.getDocId() && nodeId == gid)
	        return this;
	    else
	        return null;
	}

	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.NodeProxy, boolean, boolean, int)
     */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
            boolean includeSelf, int level) {
        return parentWithChild(proxy.getDocument(), proxy.gid, directParent, includeSelf, level);
    }
	
	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.DocumentImpl, long, boolean, boolean)
     */
    public NodeProxy parentWithChild(DocumentImpl doc, long gid,
            boolean directParent, boolean includeSelf) {
        return parentWithChild(doc, gid, directParent, includeSelf, -1);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.DocumentImpl, long, boolean, boolean, int)
	 */
	public NodeProxy parentWithChild(
			DocumentImpl otherDoc,
			long otherId,
			boolean directParent,
			boolean includeSelf,
			int level) {
		if(otherDoc.getDocId() != doc.getDocId())
			return null;
		if(includeSelf && otherId == gid)
			return this;
		if (level < 0)
			level = doc.getTreeLevel(otherId);
		while (otherId > 0) {
			otherId = XMLUtil.getParentId(doc, otherId, level);
			if(otherId == gid)
				return this;
			else if (directParent)
				return null;
			else
				--level;
		}
		return null;
	}
	
	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getContextNodes(boolean)
     */
    public NodeSet getContextNodes(boolean rememberContext) {
		ExtArrayNodeSet result = new ExtArrayNodeSet();
		ContextItem contextNode = getContext();
		while (contextNode != null) {
		    NodeProxy p = contextNode.getNode();
		    p.addMatches(this);
		    if (!result.contains(p)) {
		        if (rememberContext)
		            p.addContextNode(p);
		        result.add(p);
		    }
		    contextNode = contextNode.getNextItem();
		}
		return result;
    }
    
    /* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#getRange(org.exist.dom.DocumentImpl, long, long)
	 */
	public NodeSet getRange(DocumentImpl document, long lower, long upper) {
		if(doc.getDocId() == document.getDocId()
				&& gid >= lower && gid <= upper)
			return this;
		return NodeSet.EMPTY_SET;
	}
	
    /* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#getState()
	 */
	public int getState() {
		return 1;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.dom.NodeSet#hasChanged(int)
	 */
	public boolean hasChanged(int previousState) {
		return false;
	}
	
	/* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getSizeHint(org.exist.dom.DocumentImpl)
     */
    public int getSizeHint(DocumentImpl document) {
        if(document.getDocId() == doc.getDocId())
            return 1;
        else
            return 0;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        DocumentSet docs = new DocumentSet(1);
        docs.add(doc);
        return docs;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#containsDoc(org.exist.dom.DocumentImpl)
     */
    public boolean containsDoc(DocumentImpl document) {
        return doc.getDocId() == document.getDocId();
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getParents(boolean)
     */
    public NodeSet getParents(boolean rememberContext) {
        long pid = XMLUtil.getParentId(doc, gid);
		if (pid > -1) {
			NodeProxy parent = new NodeProxy(doc, pid, Node.ELEMENT_NODE);
			if (rememberContext)
				parent.addContextNode(this);
			else
				parent.copyContext(this);
			return parent;
		}
		return NodeSet.EMPTY_SET;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#intersection(org.exist.dom.NodeSet)
     */
    public NodeSet intersection(NodeSet other) {
        if(other.contains(this))
            return this;
        else
            return NodeSet.EMPTY_SET;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#deepIntersection(org.exist.dom.NodeSet)
     */
    public NodeSet deepIntersection(NodeSet other) {
        NodeProxy p = other.parentWithChild(this, false, true, -1);
        if(p == null)
            return NodeSet.EMPTY_SET;
        if(p.gid != gid)
			p.addMatches(this);
        return p;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#union(org.exist.dom.NodeSet)
     */
    public NodeSet union(NodeSet other) {
        ExtArrayNodeSet result = new ExtArrayNodeSet();
        result.addAll(other);
        result.add(this);
        return result;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#except(org.exist.dom.NodeSet)
     */
    public NodeSet except(NodeSet other) {
        return other.contains(this) ? NodeSet.EMPTY_SET : this;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectParentChild(NodeSet al, int mode) {
		return selectParentChild(al, mode, false);
	}
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int, boolean)
     */
    public NodeSet selectParentChild(NodeSet al, int mode,
            boolean rememberContext) {
        NodeProxy p = al.parentWithChild(this, true, false, -1);
        if(p != null) {
            if(mode == DESCENDANT) {
				if (rememberContext)
					addContextNode(p);
				else
					copyContext(p);
				return this;
            } else {
                if (rememberContext)
					p.addContextNode(this);
				else
					p.copyContext(this);
                return p;
            }
        } else
            return NodeSet.EMPTY_SET;
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestors(org.exist.dom.NodeSet, boolean, boolean)
     */
    public NodeSet selectAncestors(NodeSet al, boolean includeSelf,
            boolean rememberContext) {
        return NodeSetHelper.selectAncestors(this, al, includeSelf, rememberContext);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectSiblings(NodeSet siblings, int mode) {
        return NodeSetHelper.selectSiblings(this, siblings, mode);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestorDescendant(org.exist.dom.NodeSet, int, boolean, boolean)
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode,
            boolean includeSelf, boolean rememberContext) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, rememberContext);
    }
    
    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectFollowing(org.exist.dom.NodeSet)
     */
    public NodeSet selectFollowing(NodeSet following) throws XPathException {
        return NodeSetHelper.selectFollowing(this, following);
    }
    
	private final static class SingleNodeIterator implements Iterator, SequenceIterator {

		private boolean hasNext = true;
		private NodeProxy node;

		public SingleNodeIterator(NodeProxy node) {
			this.node = node;
		}

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

		/* (non-Javadoc)
		 * @see org.exist.xquery.value.SequenceIterator#nextItem()
		 */
		public Item nextItem() {
			if (hasNext) {
				hasNext = false;
				return node;
			} else
				return null;
		}

	}
}
