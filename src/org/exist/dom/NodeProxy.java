/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.dom;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.numbering.DLN;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.MemoryNodeSet;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.UntypedAtomicValue;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;

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
public class NodeProxy implements NodeSet, NodeValue, NodeHandle, DocumentSet, Comparable<Object> {

    /*
     * Special values for nodes gid :
     * Chosen in order to facilitate fast arithmetic computations
     */
    public static final int DOCUMENT_NODE_GID = -1;
    public static final int UNKNOWN_NODE_GID = 0;
    public static final int DOCUMENT_ELEMENT_GID = 1;

    public static final short UNKNOWN_NODE_TYPE = -1;

    public static final int UNKNOWN_NODE_LEVEL = -1;

    /**
     * The owner document of this node.
     */
    private DocumentImpl doc = null;

    private NodeId nodeId;
    /**
     * The internal storage address of this node in the
     * dom.dbx file, if known.
     * @link #UNKNOWN_NODE_ADDRESS
     */
    private long internalAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;

    /**
     * The type of this node (as defined by DOM), if known.
     * @link #UNKNOWN_NODE_TYPE
     */
    private short nodeType = UNKNOWN_NODE_TYPE;

    /**
     * The first {@link Match} object associated with this node.
     * Match objects are used to track fulltext hits throughout query processing.
     *
     * Matches are stored as a linked list.
     */
    private Match match = null;

    private ContextItem context = null;
    
    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     */
    public NodeProxy(DocumentImpl doc, NodeId nodeId) {
        this(doc, nodeId, UNKNOWN_NODE_TYPE, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param address a <code>long</code> value
     */
    public NodeProxy(DocumentImpl doc, NodeId nodeId, long address) {
        this(doc, nodeId, UNKNOWN_NODE_TYPE, address);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param nodeType a <code>short</code> value
     */
    public NodeProxy(DocumentImpl doc, NodeId nodeId, short nodeType) {
        this(doc, nodeId, nodeType, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @param nodeType a <code>short</code> value
     * @param address a <code>long</code> value
     */
    public NodeProxy(DocumentImpl doc, NodeId nodeId, short nodeType, long address) {
        this.doc = doc;
        this.nodeType = nodeType;
        this.internalAddress = address;
        this.nodeId = nodeId;
    }
    
    public NodeProxy(DocumentImpl doc, NodeId nodeId, short nodeType, long address, Match match, ContextItem context) {
        this.doc = doc;
        this.nodeType = nodeType;
        this.internalAddress = address;
        this.nodeId = nodeId;
        this.match = match;
        this.context = context;
    }

    public void update(ElementImpl element) {
        this.doc = element.getDocument();
        this.nodeType = UNKNOWN_NODE_TYPE;
        this.internalAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
        this.nodeId = element.getNodeId();
        match = null;
        context = null;
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param n a <code>StoredNode</code> value
     */
    public NodeProxy(NodeHandle n) {
        this(n.getDocument(), n.getNodeId(), n.getNodeType(), n.getInternalAddress());
        if (n instanceof NodeProxy) {
      	  this.match = ((NodeProxy) n).match;
           //TODO : what about node's context ?
        }
    }

    /**
     * create a proxy to a document node
     * @param doc a <code>DocumentImpl</code> value
     */
    public NodeProxy(DocumentImpl doc) {
        this(doc, NodeId.DOCUMENT_NODE, Node.DOCUMENT_NODE, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * The method <code>setNodeId</code>
     *
     * @param id a <code>NodeId</code> value
     */
    public void setNodeId(NodeId id) {
        this.nodeId = id;
    }

    /**
     * The method <code>getNodeId</code>
     *
     * @return a <code>NodeId</code> value
     */
    public NodeId getNodeId() {
        return nodeId;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NodeValue#getImplementation()
     */
    public int getImplementationType() {
        return NodeValue.PERSISTENT_NODE;
    }

    public NodeSet copy() {
        // return this, because there's no other node in the set
        return this;
    }

    @Override
    public Sequence tail() throws XPathException {
    	return Sequence.EMPTY_SEQUENCE;
    }
    
    /**
     * Ordering first according to document ID; then if equal
     * according to node gid.
     * @param other a <code>NodeProxy</code> value
     * @return an <code>int</code> value
     */
    public int compareTo(NodeProxy other) {
        final int diff = doc.getDocId() - other.doc.getDocId();
        if (diff != Constants.EQUAL)
            return diff;
        return nodeId.compareTo(other.nodeId);
    }

    /**
     * The method <code>compareTo</code>
     *
     * @param other an <code>Object</code> value
     * @return an <code>int</code> value
     */
    public int compareTo(Object other) {
        if(!(other instanceof NodeProxy))
            //Always superior...
            return Constants.SUPERIOR;
        return compareTo((NodeProxy) other);
    }

    /**
     * The method <code>equals</code>
     *
     * @param other an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    public boolean equals(Object other) {
        if (!(other instanceof NodeProxy))
            //Always different...
            return false;
        NodeProxy otherNode = (NodeProxy) other;
        if (otherNode.doc.getDocId() != doc.getDocId())
            return false;
        return otherNode.nodeId.equals(nodeId);
    }

    /**
     * The method <code>equals</code>
     *
     * @param other a <code>NodeValue</code> value
     * @return a <code>boolean</code> value
     * @exception XPathException if an error occurs
     */
    public boolean equals(NodeValue other) throws XPathException {
        if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
            throw new XPathException("Cannot compare persistent node with in-memory node");
        NodeProxy otherNode = (NodeProxy) other;
        if (otherNode.doc.getDocId() != doc.getDocId())
            return false;
        return otherNode.nodeId.equals(nodeId);
    }

    /**
     * The method <code>before</code>
     *
     * @param other a <code>NodeValue</code> value
     * @param isPreceding a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception XPathException if an error occurs
     */
    public boolean before(NodeValue other, boolean isPreceding) throws XPathException {
        if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
            throw new XPathException("Cannot compare persistent node with in-memory node");
        NodeProxy otherNode = (NodeProxy) other;
        if (doc.getDocId() != otherNode.doc.getDocId())
            //Totally arbitrary
            return doc.getDocId() < otherNode.doc.getDocId();
        return nodeId.before(otherNode.nodeId, isPreceding);
    }

    /**
     * The method <code>after</code>
     *
     * @param other a <code>NodeValue</code> value
     * @param isFollowing a <code>boolean</code> value
     * @return a <code>boolean</code> value
     * @exception XPathException if an error occurs
     */
    public boolean after(NodeValue other, boolean isFollowing) throws XPathException {
        if (other.getImplementationType() != NodeValue.PERSISTENT_NODE)
            throw new XPathException("Cannot compare persistent node with in-memory node");
        NodeProxy otherNode = (NodeProxy) other;
        if (doc.getDocId() != otherNode.doc.getDocId())
            //Totally arbitrary
            return doc.getDocId() > otherNode.doc.getDocId();
            return nodeId.after(otherNode.nodeId, isFollowing);
    }

    /**
     * The method <code>getOwnerDocument</code>
     *
     * @return a <code>Document</code> value
     */
    public Document getOwnerDocument() {
        return doc;
    }

    /**
     * The method <code>getDocument</code>
     *
     * @return a <code>DocumentImpl</code> value
     */
    public final DocumentImpl getDocument()  {
        return doc;
    }

    /**
     * The method <code>isDocument</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isDocument() {
        return nodeType == Node.DOCUMENT_NODE;
    }

    /* Gets the node from the broker, i.e. fom the underlying file system
     * Call this method <string>only</strong> when necessary
     * @see org.exist.xquery.value.NodeValue#getNode()
     */
    public Node getNode() {
        if (isDocument())
            return doc;
        else {
            NodeImpl realNode = (NodeImpl) doc.getNode(this);
            if (realNode != null) this.nodeType = realNode.getNodeType();
            return realNode;
        }
    }

    public short getNodeType() {
        return nodeType;
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

    public void setIndexType(int type) {
        this.internalAddress = StorageAddress.setIndexType(internalAddress, (short) type);
    }

    public int getIndexType() {
        if (internalAddress == -1)
            return Type.ITEM;
        return RangeIndexSpec.indexTypeToXPath(StorageAddress.indexTypeFromPointer(internalAddress));
    }

    @Override
    public void setTrackMatches(boolean track) {    	
    }

    @Override
    public boolean getTrackMatches() {
        return true;
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
            match.nextMatch = null;
            return;
        }
        Match next = match;
        while (next != null) {
            if (next.matchEquals(m)) {
                next.mergeOffsets(m);
                return;
            }
            if (next.nextMatch == null) {
                next.nextMatch = m;
                break;
            }
            next = next.nextMatch;
        }
    }

    public void addMatches(NodeProxy p) {
        if (p == this)
            return;
        Match m = p.getMatches();
        if (Match.matchListEquals(m, this.match))
        	return;
        while (m != null) {
            addMatch(m.newCopy());
            m = m.nextMatch;
        }
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
    public void addContextNode(int contextId, NodeValue node) {
        if (node.getImplementationType() != NodeValue.PERSISTENT_NODE)
            return;
        NodeProxy contextNode = (NodeProxy) node;
        if (context == null) {
            context = new ContextItem(contextId, contextNode);
            return;
        }
        ContextItem next = context;
        while (next != null) {
            if (contextId == next.getContextId() &&
                next.getNode().getNodeId().equals(contextNode.getNodeId())) {
                // Ignore duplicate context nodes
                break;
            }
            if (next.getNextDirect() == null) {
                if (next == context) {
                    // context items should not be shared between proxies,
                    // but for performance reason, if there's only a single
                    // context item, it will be shared. we thus have to create
                    // a copy before appending a new item.
                    next = new ContextItem(next.getContextId(), next.getNode());
                    context = next;
                }
                next.setNextContextItem(new ContextItem(contextId, contextNode));
                break;
            }
            next = next.getNextDirect();
        }
    }

    /**
     * Add all context nodes from the other NodeProxy to the
     * context of this NodeProxy.
     *
     * @param other
     */
    public void addContext(NodeProxy other) {
        ContextItem next = other.context;
        while (next != null) {
            addContextNode(next.getContextId(), next.getNode());
            next = next.getNextDirect();
        }
    }

    /**
     * The method <code>copyContext</code>
     *
     * @param node a <code>NodeProxy</code> value
     */
    public void copyContext(NodeProxy node) {
//        context = node.getContext();
        deepCopyContext(node);
    }

    /**
     * Copy the context items from the given node into this node.
     * Context items are used to keep track of context nodes inside predicates.
     *
     * @param node a <code>NodeProxy</code> value
     */
    public void deepCopyContext(NodeProxy node) {
        context = null;
        if (node.context == null)
            return;
        if (node.context.getNextDirect() == null) {
            // if there's a single context item, we just
            // copy a reference to it. addContextNode will take
            // care of this and create a copy before appending
            // a new item
            context = node.context;
        } else {
            ContextItem next = node.context;
            ContextItem newContext = new ContextItem(next.getContextId(), next.getNode());
            context = newContext;
            next = next.getNextDirect();
            while (next != null) {
                newContext.setNextContextItem(new ContextItem(next.getContextId(), next.getNode()));
                newContext = newContext.getNextDirect();
                next = next.getNextDirect();
            }
        }
    }

    /**
     * The method <code>deepCopyContext</code>
     *
     * @param node a <code>NodeProxy</code> value
     * @param addContextId an <code>int</code> value
     */
    public void deepCopyContext(NodeProxy node, int addContextId) {
        if (context == null)
            deepCopyContext(node);
        addContextNode(addContextId, node);
    }

    /**
     * The method <code>clearContext</code>
     *
     * @param contextId an <code>int</code> value
     */
    public void clearContext(int contextId) {
        if (contextId == Expression.IGNORE_CONTEXT) {
            context = null;
            return;
        }
        ContextItem newContext = null;
        ContextItem last = null;
        ContextItem next = context;
        while (next != null) {
            if (next.getContextId() != contextId) {
                if (newContext == null) {
                    newContext = next;
                } else {
                    last.setNextContextItem(next);
                }
                last = next;
                last.setNextContextItem(null);
            }
            next = next.getNextDirect();
        }
        this.context = newContext;
    }

    /**
     * The method <code>getContext</code>
     *
     * @return a <code>ContextItem</code> value
     */
    public ContextItem getContext() {
        return context;
    }

    /**
     * The method <code>debugContext</code>
     *
     * @return a <code>String</code> value
     */
    public String debugContext() {
        StringBuilder buf = new StringBuilder();
        buf.append("Context for " + nodeId + " [ " + toString() + "] : ");
        ContextItem next = context;
        while(next != null) {
            buf.append('[');
            buf.append(next.getNode().getNodeId());
            buf.append(':');
            buf.append(next.getContextId());
            buf.append("] ");
            next = next.getNextDirect();
        }
        return buf.toString();
    }

    //	methods of interface Item

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#getType()
     */
    public int getType() {
        switch (nodeType) {
        case Node.ELEMENT_NODE :
            //TODO : return Type.DOCUMENT for some in-memory nodes :
            //http://sourceforge.net/tracker/index.php?func=detail&aid=1730690&group_id=17691&atid=117691
            //Ideally compute this when proxy is constructed
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
        //(yet) unknown type : return generic
        default :
            return Type.NODE;
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#isPersistentSet()
     */
    public boolean isPersistentSet() {
        return true;
    }

    /**
     * The method <code>nodeMoved</code>
     *
     * @param oldNodeId a <code>NodeId</code> value
     * @param newNode a <code>StoredNode</code> value
     */
    public void nodeMoved(NodeId oldNodeId, StoredNode newNode) {
        if (nodeId.equals(oldNodeId)) {
            // update myself
            nodeId = newNode.getNodeId();
            internalAddress = newNode.getInternalAddress();
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toSequence()
     */
    public Sequence toSequence() {
        return this;
    }

    /**
     * The method <code>getNodeValue</code>
     *
     * @return a <code>String</code> value
     */
    public String getNodeValue() {
        DBBroker broker = null;
        try {
            broker = doc.getBrokerPool().get(null);
            if (isDocument()) {
                Element e = doc.getDocumentElement();
                if (e instanceof NodeProxy) {
                    return broker.getNodeValue(new StoredNode((NodeProxy)e), false);
                } else if (e != null) {
                    return broker.getNodeValue((ElementImpl)e, false);
                } else
                    // probably a binary resource
                    return "";
            } else {
                return broker.getNodeValue(new StoredNode(this), false);
            }
        } catch (EXistException e) {
            //TODO : raise an exception here ! -pb
        } finally {
            doc.getBrokerPool().release(broker);
        }
        return "";
    }

    /**
     * The method <code>getNodeValueSeparated</code>
     *
     * @return a <code>String</code> value
     */
    public String getNodeValueSeparated() {
        DBBroker broker = null;
        try {
            broker = doc.getBrokerPool().get(null);
            return broker.getNodeValue(new StoredNode(this), true);
        } catch (EXistException e) {
            //TODO : raise an exception here !
        } finally {
            doc.getBrokerPool().release(broker);
        }
        return "";
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
        return UntypedAtomicValue.convertTo(getNodeValue(), requiredType);
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
    public void toSAX(DBBroker broker, ContentHandler handler, Properties properties) throws SAXException {
        Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        if (properties != null)
            serializer.setProperties(properties);
        
        if (handler instanceof LexicalHandler) {
            serializer.setSAXHandlers(handler, (LexicalHandler) handler);
        } else {
            serializer.setSAXHandlers(handler, null);
        }
        serializer.toSAX(this);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#copyTo(org.exist.storage.DBBroker, org.exist.memtree.DocumentBuilderReceiver)
     */
    public void copyTo(DBBroker broker, DocumentBuilderReceiver receiver) throws SAXException {
        NodeImpl node = null;
        if (nodeType < 0) {
            node = (NodeImpl) getNode();
        }
        if(nodeType == Node.ATTRIBUTE_NODE) {
            AttrImpl attr = (node == null ? (AttrImpl)getNode() : (AttrImpl)node);
            receiver.attribute(attr.getQName(), attr.getValue());
        } else {
            receiver.addReferenceNode(this);
        }
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
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(NodeProxy.class)) {
            return (T)this;
        } else if (target.isAssignableFrom(Node.class)) {
            return (T)getNode();
        } else if (target == Object.class) {
            return (T)getNode();
        } else {
            final StringValue v = new StringValue(getStringValue());
            return (T)v.toJavaObject(target);
        }
    }

    /*
     * Methods of interface Sequence:
     */

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getItemType()
     */
    public int getItemType() {
        return getType();
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
        //TODO : return something useful ? -pb
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#toNodeSet()
     */
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }

    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return null;
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
    public void setSelfAsContext(int contextId) {
        addContextNode(contextId, this);
    }

    /* -----------------------------------------------*
     * Methods of class NodeSet
     * -----------------------------------------------*/

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterator()
     */
    public NodeSetIterator iterator() {
        return new SingleNodeIterator(this);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#iterate()
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
     * @see org.exist.dom.NodeSet#contains(org.exist.dom.NodeProxy)
     */
    public boolean contains(NodeProxy proxy) {
        if (doc.getDocId() != proxy.doc.getDocId())
            return false;
        if (!nodeId.equals(proxy.getNodeId()))
            return false;
        return true;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#addAll(org.exist.dom.NodeSet)
     */
    public void addAll(NodeSet other) {
        throw new RuntimeException("Method not supported");
    }

    /**
     * The method <code>isEmpty</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean isEmpty() {
        return false;
    }

    /**
     * The method <code>hasOne</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasOne() {
        return true;
    }

    /**
     * The method <code>hasMany</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean hasMany() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy)
     */
    public void add(NodeProxy proxy) {
        throw new RuntimeException("Method not supported");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#add(org.exist.xquery.value.Item)
     */
    public void add(Item item) throws XPathException {
        throw new RuntimeException("Method not supported");
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#add(org.exist.dom.NodeProxy, int)
     */
    public void add(NodeProxy proxy, int sizeHint) {
        throw new RuntimeException("Method not supported");
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#addAll(org.exist.xquery.value.Sequence)
     */
    public void addAll(Sequence other) throws XPathException {
        throw new RuntimeException("Method not supported");
    }

    /* (non-Javadoc)
     * @see org.w3c.dom.NodeList#getLength()
     */
    public int getLength() {
        //TODO : how to delegate to the real node implementation's getLength() ?
        return 1;
    }

    //TODO : evaluate both semantics
    public int getItemCount() {
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


    /**
     * The method <code>get</code>
     * (non-Javadoc)
     * @see org.exist.dom.NodeSet#get(org.exist.dom.NodeProxy)
     *
     *
     * @param p a <code>NodeProxy</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(NodeProxy p) {
        return contains(p) ? this : null;
    }

    /**
     * The method <code>get</code>
     *
     * @param document a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     * @return a <code>NodeProxy</code> value
     */
    public NodeProxy get(DocumentImpl document, NodeId nodeId) {
        if (!this.nodeId.equals(nodeId))
            return null;
        if(this.doc.getDocId() != document.getDocId())
            return null;
        return this;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#parentWithChild(org.exist.dom.NodeProxy, boolean, boolean, int)
     */
    public NodeProxy parentWithChild(NodeProxy proxy, boolean directParent,
            boolean includeSelf, int level) {
        return parentWithChild(proxy.getDocument(), proxy.getNodeId(), directParent, includeSelf);
    }

    public NodeProxy parentWithChild(DocumentImpl otherDoc, NodeId otherId,
            boolean directParent, boolean includeSelf) {
        if (otherDoc.getDocId() != doc.getDocId())
            return null;
        if (includeSelf && otherId.compareTo(nodeId) == 0)
            return this;
        otherId = otherId.getParentId();
        while (otherId != null) {
            if(otherId.compareTo(nodeId) == 0)
                return this;
            else if (directParent)
                return null;
            otherId = otherId.getParentId();
        }
        return null;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getContextNodes(boolean)
     */
    public NodeSet getContextNodes(int contextId) {
        NewArrayNodeSet result = new NewArrayNodeSet();
        ContextItem contextNode = getContext();
        while (contextNode != null) {
            NodeProxy p = contextNode.getNode();
            p.addMatches(this);
            if (!result.contains(p)) {
                //TODO : why isn't "this" involved here ? -pb
                if (contextId != Expression.NO_CONTEXT_ID)
                    p.addContextNode(contextId, p);
                result.add(p);
            }
            contextNode = contextNode.getNextDirect();
        }
        return result;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getState()
     */
    public int getState() {
        return 0;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#hasChanged(int)
     */
    public boolean hasChanged(int previousState) {
        return false;
    }

    @Override
    public void destroy(XQueryContext context, Sequence contextSequence) {
        // Nothing to do
    }

    public boolean isCacheable() {
        return true;
    }

    /* (non-Javadoc)
    * @see org.exist.dom.NodeSet#getSizeHint(org.exist.dom.DocumentImpl)
    */
    public int getSizeHint(DocumentImpl document) {
        if(document.getDocId() == doc.getDocId())
            return 1;
        else
            return Constants.NO_SIZE_HINT;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getDocumentSet()
     */
    public DocumentSet getDocumentSet() {
        return this;
    }

    /**
     * The method <code>getCollectionIterator</code>
     *
     * @return an <code>Iterator</code> value
     */
    public Iterator<Collection> getCollectionIterator() {
        return new Iterator<Collection>() {
            boolean hasNext = true;

            public boolean hasNext() {
                return hasNext;
            }

            public Collection next() {
                hasNext = false;
                return NodeProxy.this.getDocument().getCollection();
            }

            public void remove() {
                //Nothing to do
            }
        };
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
        NodeProxy p = other.parentWithChild(this, false, true, UNKNOWN_NODE_LEVEL);
        if (p == null)
            return NodeSet.EMPTY_SET;
        if (!nodeId.equals(p.nodeId))
	    p.addMatches(this);
        return p;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#union(org.exist.dom.NodeSet)
     */
    public NodeSet union(NodeSet other) {
        if (other.isEmpty())
            return this;
        NewArrayNodeSet result = new NewArrayNodeSet();
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

    /**
     * The method <code>filterDocuments</code>
     *
     * @param otherSet a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet filterDocuments(NodeSet otherSet) {
        DocumentSet docs = otherSet.getDocumentSet();
        if (docs.contains(doc.getDocId()))
            return this;
        return NodeSet.EMPTY_SET;
    }

    /**
     * The method <code>setProcessInReverseOrder</code>
     *
     * @param inReverseOrder a <code>boolean</code> value
     */
    public void setProcessInReverseOrder(boolean inReverseOrder) {
        //Nothing to do
    }

    /**
     * The method <code>getProcessInReverseOrder</code>
     *
     * @return a <code>boolean</code> value
     */
    public boolean getProcessInReverseOrder() {
        return false;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#getParents(boolean)
     */
    public NodeSet getParents(int contextId) {
        NodeId pid = nodeId.getParentId();
        if (pid == null || pid == NodeId.DOCUMENT_NODE)
            return NodeSet.EMPTY_SET;
        NodeProxy parent = new NodeProxy(doc, pid, Node.ELEMENT_NODE);
        if (contextId != Expression.NO_CONTEXT_ID)
            parent.addContextNode(contextId, this);
        else
            parent.copyContext(this);
        parent.addMatches(this);
        return parent;
    }

    /**
     * The method <code>getAncestors</code>
     *
     * @param contextId an <code>int</code> value
     * @param includeSelf a <code>boolean</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet getAncestors(int contextId, boolean includeSelf) {
        NodeSet ancestors = new NewArrayNodeSet();
        if (includeSelf)
            ancestors.add(this);
        NodeId parentID = nodeId.getParentId();
        while (parentID != null) {
            NodeProxy parent = new NodeProxy(getDocument(), parentID, Node.ELEMENT_NODE);
            if (contextId != Expression.NO_CONTEXT_ID)
                parent.addContextNode(contextId, this);
            else
                parent.copyContext(this);
            parent.addMatches(this);
            ancestors.add(parent);
            parentID = parentID.getParentId();
        }
        return ancestors;
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectParentChild(NodeSet al, int mode) {
        return selectParentChild(al, mode, Expression.NO_CONTEXT_ID);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectParentChild(org.exist.dom.NodeSet, int, boolean)
     */
    public NodeSet selectParentChild(NodeSet al, int mode, int contextId) {
        return NodeSetHelper.selectParentChild(this, al, mode, contextId);
    }

    public boolean matchParentChild(NodeSet al, int mode, int contextId) {
        return NodeSetHelper.matchParentChild(this, al, mode, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestors(org.exist.dom.NodeSet, boolean, int)
     */
    public NodeSet selectAncestors(NodeSet al, boolean includeSelf, int contextId) {
        return NodeSetHelper.selectAncestors(this, al, includeSelf, contextId);
    }

    public boolean matchAncestors(NodeSet al, boolean includeSelf, int contextId) {
        return NodeSetHelper.matchAncestors(this, al, includeSelf, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectPrecedingSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectPrecedingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectPrecedingSiblings(this, siblings, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectFollowingSiblings(org.exist.dom.NodeSet, int)
     */
    public NodeSet selectFollowingSiblings(NodeSet siblings, int contextId) {
        return NodeSetHelper.selectFollowingSiblings(this, siblings, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectAncestorDescendant(org.exist.dom.NodeSet, int, boolean, int)
     */
    public NodeSet selectAncestorDescendant(NodeSet al, int mode, boolean includeSelf, int contextId,
            boolean copyMatches) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    public boolean matchAncestorDescendant(NodeSet al, int mode, boolean includeSelf, int contextId,
            boolean copyMatches) {
        return NodeSetHelper.matchAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.NodeSet#selectFollowing(org.exist.dom.NodeSet)
     */
    public NodeSet selectPreceding(NodeSet preceding, int contextId) throws XPathException {
        return NodeSetHelper.selectPreceding(this, preceding);
    }

    public NodeSet selectPreceding(NodeSet preceding, int position, int contextId) throws XPathException,
            UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    /**
     * The method <code>selectFollowing</code>
     *
     * @param following a <code>NodeSet</code> value
     * @return a <code>NodeSet</code> value
     * @exception XPathException if an error occurs
     */
    public NodeSet selectFollowing(NodeSet following, int contextId) throws XPathException {
        return NodeSetHelper.selectFollowing(this, following);
    }

    public NodeSet selectFollowing(NodeSet following, int position, int contextId) throws XPathException {
        throw new UnsupportedOperationException();
    }

    /**
     * The method <code>directSelectAttribute</code>
     *
     * @param test a node test
     * @param contextId an <code>int</code> value
     * @return a <code>NodeSet</code> value
     */
    public NodeSet directSelectAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId) {
        if (nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE)
            return NodeSet.EMPTY_SET;
        try {
            NewArrayNodeSet result = null;
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true);
            int status = reader.next();
            if (status != XMLStreamReader.START_ELEMENT)
                return NodeSet.EMPTY_SET;
            int attrs = reader.getAttributeCount();
            for (int i = 0; i < attrs; i++) {
                status = reader.next();
                if (status != XMLStreamReader.ATTRIBUTE)
                    break;
                AttrImpl attr = (AttrImpl) reader.getNode();
                if (test.matches(attr)) {
                    NodeProxy child = new NodeProxy(attr);
                    if (Expression.NO_CONTEXT_ID != contextId)
                        child.addContextNode(contextId, this);
                    else
                        child.copyContext(this);
                    if (!test.isWildcardTest())
                        return child;
                    if (result == null)
                        result = new NewArrayNodeSet();
                    result.add(child);
                }
            }
            return result == null ? NodeSet.EMPTY_SET : result;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public NodeSet directSelectChild(QName qname, int contextId) {
        if (nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE)
            return NodeSet.EMPTY_SET;
        NodeImpl node = (NodeImpl) getNode();
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return NodeSet.EMPTY_SET;
        NodeList children = node.getChildNodes();
        if (children.getLength() == 0)
            return NodeSet.EMPTY_SET;
        NewArrayNodeSet result = new NewArrayNodeSet();
        StoredNode child;
        for (int i = 0; i < children.getLength(); i++) {
            child = (StoredNode) children.item(i);
            if (child.getQName().equals(qname)) {
                NodeProxy p = new NodeProxy(doc, child.getNodeId(), Node.ELEMENT_NODE, child.getInternalAddress());
                if (Expression.NO_CONTEXT_ID != contextId)
                    p.addContextNode(contextId, this);
                else
                    p.copyContext(this);
                p.addMatches(this);
                result.add(p);
            }
        }
        return result;
    }

    /**
     * The method <code>toString</code>
     *
     * @return a <code>String</code> value
     */
    public String toString() {
        if (nodeId == NodeId.DOCUMENT_NODE)
            return "Document node for " + doc.getDocId();
        else
            return doc.getNode(nodeId).getNodeName();
    }

    public String toStringWithDetails() {
        if (nodeId == NodeId.DOCUMENT_NODE)
            return "Document node for " + doc.getDocId();
        else
            return doc.getNode(nodeId).toString();
    }

    private final static class SingleNodeIterator implements NodeSetIterator, SequenceIterator {

        private boolean hasNext = true;
        private NodeProxy node;

        public SingleNodeIterator(NodeProxy node) {
            this.node = node;
        }

        public boolean hasNext() {
            return hasNext;
        }

        public NodeProxy next() {
            if (!hasNext)
                return null;
            hasNext = false;
            return node;
        }

        public NodeProxy peekNode() {
            return node;
        }

        public void remove() {
            throw new RuntimeException("Method not supported");
        }

        /* (non-Javadoc)
         * @see org.exist.xquery.value.SequenceIterator#nextItem()
         */
        public Item nextItem() {
            if (!hasNext)
                return null;
            hasNext = false;
            return node;
        }

        public void setPosition(NodeProxy proxy) {
            node = proxy;
            hasNext = true;
        }
    }

    /************************************************
     * Methods of MutableDocumentSet
     ************************************************/

    public Iterator<DocumentImpl> getDocumentIterator() {
        return new Iterator<DocumentImpl>() {

            private boolean hasMore = true;

            public boolean hasNext() {
                return hasMore;
            }

            public DocumentImpl next() {
                final DocumentImpl next = hasMore ? doc : null;
                hasMore = false;
                return next;
            }

            public void remove() {
                //Raise exception ? -pb
            }
        };
    }

    public int getDocumentCount() {
        return 1;
    }

    public DocumentImpl getDocumentAt(int pos) {
        if (pos < 0 || pos > 1)
            return null;
        return this.doc;
    }

    public DocumentImpl getDoc() {
        return doc;
    }
    
    public DocumentImpl getDoc(int docId) {
        if (docId == this.doc.getDocId())
            return this.doc;
        return null;
    }

    public XmldbURI[] getNames() {
        return new XmldbURI[] {
            this.doc.getURI()
        };
    }

    public DocumentSet intersection(DocumentSet other) {
        if (other.contains(doc.getDocId())) {
            DefaultDocumentSet r = new DefaultDocumentSet();
            r.add(doc);
            return r;
        }
        return DefaultDocumentSet.EMPTY_DOCUMENT_SET;
    }

    public boolean contains(DocumentSet other) {
        if (other.getDocumentCount() > 1)
            return false;
        if (other.getDocumentCount() == 0)
            return true;
        return other.contains(doc.getDocId());
    }

    public boolean contains(int docId) {
        return doc.getDocId() == docId;
    }

    public NodeSet docsToNodeSet() {
        return new NodeProxy(doc, NodeId.DOCUMENT_NODE);
    }

    public void lock(DBBroker broker, boolean exclusive, boolean checkExisting) throws LockException {
        Lock dlock = doc.getUpdateLock();
        if (exclusive)
            dlock.acquire(Lock.WRITE_LOCK);
        else
            dlock.acquire(Lock.READ_LOCK);
    }

    public void unlock(boolean exclusive) {
        Lock dlock = doc.getUpdateLock();
        if(exclusive)
            dlock.release(Lock.WRITE_LOCK);
        else if (dlock.isLockedForRead(Thread.currentThread()))
            dlock.release(Lock.READ_LOCK);
    }

    public boolean equalDocs(DocumentSet other) {
        if (this == other)
            // we are comparing the same objects
            return true;
        if (other.getDocumentCount() != 1)
            return false;
        return other.contains(doc.getDocId());
    }

    public boolean directMatchAttribute(DBBroker broker, org.exist.xquery.NodeTest test, int contextId) {
        if (nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE)
            return false;
        try {
            EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true);
            int status = reader.next();
            if (status != XMLStreamReader.START_ELEMENT)
                return false;
            int attrs = reader.getAttributeCount();
            for (int i = 0; i < attrs; i++) {
                status = reader.next();
                if (status != XMLStreamReader.ATTRIBUTE)
                    break;
                AttrImpl attr = (AttrImpl) reader.getNode();
                if (test.matches(attr)) {
                	return true;
                }
            }
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch (XMLStreamException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public boolean directMatchChild(QName qname, int contextId) {
        if (nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE)
            return false;
        NodeImpl node = (NodeImpl) getNode();
        if (node.getNodeType() != Node.ELEMENT_NODE)
            return false;
        NodeList children = node.getChildNodes();
        if (children.getLength() == 0)
            return false;
        StoredNode child;
        for (int i = 0; i < children.getLength(); i++) {
            child = (StoredNode) children.item(i);
            if (child.getQName().equals(qname)) {
            	return true;
            }
        }
        return false;
    }
}
