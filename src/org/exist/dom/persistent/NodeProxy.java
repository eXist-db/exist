/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.numbering.NodeId;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.RangeIndexSpec;
import org.exist.storage.StorageAddress;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
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
import java.util.NoSuchElementException;
import java.util.Properties;

/**
 * Placeholder class for DOM nodes.
 * <p/>
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
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class NodeProxy implements NodeSet, NodeValue, NodeHandle, DocumentSet, Comparable<Object> {

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
     *
     * @link #UNKNOWN_NODE_ADDRESS
     */
    private long internalAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;

    /**
     * The type of this node (as defined by DOM), if known.
     *
     * @link #UNKNOWN_NODE_TYPE
     */
    private short nodeType = UNKNOWN_NODE_TYPE;

    /**
     * The first {@link Match} object associated with this node.
     * Match objects are used to track hits throughout query processing.
     * <p/>
     * Matches are stored as a linked list.
     */
    private Match match = null;

    private ContextItem context = null;

    private QName qname = null;

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc    a <code>DocumentImpl</code> value
     * @param nodeId a <code>NodeId</code> value
     */
    public NodeProxy(final DocumentImpl doc, final NodeId nodeId) {
        this(doc, nodeId, UNKNOWN_NODE_TYPE, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc     a <code>DocumentImpl</code> value
     * @param nodeId  a <code>NodeId</code> value
     * @param address a <code>long</code> value
     */
    public NodeProxy(final DocumentImpl doc, final NodeId nodeId, final long address) {
        this(doc, nodeId, UNKNOWN_NODE_TYPE, address);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc      a <code>DocumentImpl</code> value
     * @param nodeId   a <code>NodeId</code> value
     * @param nodeType a <code>short</code> value
     */
    public NodeProxy(final DocumentImpl doc, final NodeId nodeId, final short nodeType) {
        this(doc, nodeId, nodeType, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param doc      a <code>DocumentImpl</code> value
     * @param nodeId   a <code>NodeId</code> value
     * @param nodeType a <code>short</code> value
     * @param address  a <code>long</code> value
     */
    public NodeProxy(final DocumentImpl doc, final NodeId nodeId, final short nodeType, final long address) {
        this.doc = doc;
        this.nodeType = nodeType;
        this.internalAddress = address;
        this.nodeId = nodeId;
    }

    public void update(final ElementImpl element) {
        this.doc = element.getOwnerDocument();
        this.nodeType = UNKNOWN_NODE_TYPE;
        this.internalAddress = StoredNode.UNKNOWN_NODE_IMPL_ADDRESS;
        this.nodeId = element.getNodeId();
        match = null;
        context = null;
    }

    /**
     * Creates a new <code>NodeProxy</code> instance.
     *
     * @param n a <code>NodeHandle</code> value
     */
    public NodeProxy(final NodeHandle n) {
        this(n.getOwnerDocument(), n.getNodeId(), n.getNodeType(), n.getInternalAddress());
        if(n instanceof NodeProxy) {
            this.match = ((NodeProxy) n).match;
            this.context = ((NodeProxy) n).context;
        }
    }

    /**
     * create a proxy to a document node
     *
     * @param doc a <code>DocumentImpl</code> value
     */
    public NodeProxy(final DocumentImpl doc) {
        this(doc, NodeId.DOCUMENT_NODE, Node.DOCUMENT_NODE, StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
    }

    @Override
    public void setNodeId(final NodeId id) {
        this.nodeId = id;
    }

    @Override
    public NodeId getNodeId() {
        return nodeId;
    }

    @Override
    public QName getQName() {
        if (qname == null) {
            getNode();
        }
        return qname;
    }

    public void setQName(QName qname) {
        this.qname = qname;
    }

    @Override
    public int getImplementationType() {
        return NodeValue.PERSISTENT_NODE;
    }

    @Override
    public NodeSet copy() {
        // return this, because there's no other node in the set
        return this;
    }

    @Override
    public Sequence tail() throws XPathException {
        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * The method <code>compareTo</code>
     *
     * @param other an <code>Object</code> value
     * @return an <code>int</code> value
     */
    @Override
    public int compareTo(final Object other) {
        if(!(other instanceof NodeProxy)) {
            //We are always superior...
            return Constants.SUPERIOR;
        } else {
            return compareTo((NodeProxy) other);
        }
    }

    /**
     * Ordering first according to document ID; then if equal
     * according to node gid.
     *
     * @param other a <code>NodeProxy</code> value
     * @return an <code>int</code> value
     */
    public int compareTo(final NodeProxy other) {
        final int diff = doc.getDocId() - other.doc.getDocId();
        if(diff != Constants.EQUAL) {
            return diff;
        } else {
            return nodeId.compareTo(other.nodeId);
        }
    }

    /**
     * The method <code>equals</code>
     *
     * @param other an <code>Object</code> value
     * @return a <code>boolean</code> value
     */
    @Override
    public boolean equals(final Object other) {
        if(!(other instanceof NodeProxy)) {
            return false;
        }

        final NodeProxy otherNode = (NodeProxy) other;
        if(otherNode.doc.getDocId() != doc.getDocId()) {
            return false;
        }
        return otherNode.nodeId.equals(nodeId);
    }

    @Override
    public boolean equals(final NodeValue other) throws XPathException {
        if(other.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            throw new XPathException("Cannot compare persistent node with in-memory node");
        }
        final NodeProxy otherNode = (NodeProxy) other;
        if(otherNode.doc.getDocId() != doc.getDocId()) {
            return false;
        }
        return otherNode.nodeId.equals(nodeId);
    }

    @Override
    public boolean before(final NodeValue other, final boolean isPreceding) throws XPathException {
        if(other.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            throw new XPathException("Cannot compare persistent node with in-memory node");
        }
        final NodeProxy otherNode = (NodeProxy) other;
        if(doc.getDocId() != otherNode.doc.getDocId()) {
            //Totally arbitrary
            return doc.getDocId() < otherNode.doc.getDocId();
        }
        return nodeId.before(otherNode.nodeId, isPreceding);
    }

    @Override
    public boolean after(final NodeValue other, final boolean isFollowing) throws XPathException {
        if(other.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            throw new XPathException("Cannot compare persistent node with in-memory node");
        }
        final NodeProxy otherNode = (NodeProxy) other;
        if(doc.getDocId() != otherNode.doc.getDocId()) {
            //Totally arbitrary
            return doc.getDocId() > otherNode.doc.getDocId();
        }
        return nodeId.after(otherNode.nodeId, isFollowing);
    }

    @Override
    public DocumentImpl getOwnerDocument() {
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

    /**
     * Gets the node from the broker, i.e. fom the underlying file system
     * Call this method <string>only</strong> when necessary
     * @see org.exist.xquery.value.NodeValue#getNode()
     */
    @Override
    public Node getNode() {
        if(isDocument()) {
            return doc;
        } else {
            final NodeImpl realNode = (NodeImpl) doc.getNode(this);
            if(realNode != null) {
                this.nodeType = realNode.getNodeType();
                this.qname = realNode.getQName();
            }
            return realNode;
        }
    }

    @Override
    public short getNodeType() {
        return nodeType;
    }

    /**
     * Sets the nodeType.
     *
     * @param nodeType The nodeType to set
     */
    public void setNodeType(final short nodeType) {
        this.nodeType = nodeType;
    }

    @Override
    public long getInternalAddress() {
        return internalAddress;
    }

    @Override
    public void setInternalAddress(final long internalAddress) {
        this.internalAddress = internalAddress;
    }

    public void setIndexType(int type) {
        this.internalAddress = StorageAddress.setIndexType(internalAddress, (short) type);
    }

    @Override
    public int getIndexType() {
        if(internalAddress == -1) {
            return Type.ITEM;
        }
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

    public void setMatches(final Match match) {
        this.match = match;
    }

    public void addMatch(final Match m) {
        if(match == null) {
            match = m;
            match.nextMatch = null;
            return;
        }
        Match next = match;
        while(next != null) {
            if(next.matchEquals(m)) {
                next.mergeOffsets(m);
                return;
            }
            if(next.nextMatch == null) {
                next.nextMatch = m;
                break;
            }
            next = next.nextMatch;
        }
    }

    public void addMatches(final NodeProxy p) {
        if(p == this) {
            return;
        }
        Match m = p.getMatches();
        if(Match.matchListEquals(m, this.match)) {
            return;
        }
        while(m != null) {
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
    @Override
    public void addContextNode(final int contextId, final NodeValue node) {
        if(node.getImplementationType() != NodeValue.PERSISTENT_NODE) {
            return;
        }
        final NodeProxy contextNode = (NodeProxy) node;
        if(context == null) {
            context = new ContextItem(contextId, contextNode);
            return;
        }
        ContextItem next = context;
        while(next != null) {
            if(contextId == next.getContextId() &&
                next.getNode().getNodeId().equals(contextNode.getNodeId())) {
                // Ignore duplicate context nodes
                break;
            }
            if(next.getNextDirect() == null) {
                if(next == context) {
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
    public void addContext(final NodeProxy other) {
        ContextItem next = other.context;
        while(next != null) {
            addContextNode(next.getContextId(), next.getNode());
            next = next.getNextDirect();
        }
    }

    public void copyContext(final NodeProxy node) {
        deepCopyContext(node);
    }

    /**
     * Copy the context items from the given node into this node.
     * Context items are used to keep track of context nodes inside predicates.
     *
     * @param node a <code>NodeProxy</code> value
     */
    public void deepCopyContext(final NodeProxy node) {
        context = null;
        if(node.context == null) {
            return;
        }
        if(node.context.getNextDirect() == null) {
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
            while(next != null) {
                newContext.setNextContextItem(new ContextItem(next.getContextId(), next.getNode()));
                newContext = newContext.getNextDirect();
                next = next.getNextDirect();
            }
        }
    }

    public void deepCopyContext(final NodeProxy node, final int addContextId) {
        if(context == null) {
            deepCopyContext(node);
        }
        addContextNode(addContextId, node);
    }

    /**
     * The method <code>clearContext</code>
     *
     * @param contextId an <code>int</code> value
     */
    @Override
    public void clearContext(final int contextId) {
        if(contextId == Expression.IGNORE_CONTEXT) {
            context = null;
            return;
        }
        ContextItem newContext = null;
        ContextItem last = null;
        ContextItem next = context;
        while(next != null) {
            if(next.getContextId() != contextId) {
                if(newContext == null) {
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

    public ContextItem getContext() {
        return context;
    }

    public String debugContext() {
        final StringBuilder buf = new StringBuilder();
        buf.append("Context for ").append(nodeId).append(" [ ").append(toString()).append("] : ");
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
    @Override
    public int getType() {
        return nodeType2XQuery(nodeType);
    }

    public static int nodeType2XQuery(final short nodeType) {
        switch(nodeType) {
            case Node.ELEMENT_NODE:
                //TODO : return Type.DOCUMENT for some in-memory nodes :
                //http://sourceforge.net/tracker/index.php?func=detail&aid=1730690&group_id=17691&atid=117691
                //Ideally compute this when proxy is constructed
                return Type.ELEMENT;
            case Node.ATTRIBUTE_NODE:
                return Type.ATTRIBUTE;
            case Node.TEXT_NODE:
                return Type.TEXT;
            case Node.PROCESSING_INSTRUCTION_NODE:
                return Type.PROCESSING_INSTRUCTION;
            case Node.COMMENT_NODE:
                return Type.COMMENT;
            case Node.DOCUMENT_NODE:
                return Type.DOCUMENT;
            //(yet) unknown type : return generic
            default:
                return Type.NODE;
        }
    }

    @Override
    public boolean isPersistentSet() {
        return true;
    }

    @Override
    public void nodeMoved(final NodeId oldNodeId, final NodeHandle newNode) {
        if(nodeId.equals(oldNodeId)) {
            // update myself
            nodeId = newNode.getNodeId();
            internalAddress = newNode.getInternalAddress();
        }
    }

    @Override
    public Sequence toSequence() {
        return this;
    }

    public String getNodeValue() {
        try(final DBBroker broker = doc.getBrokerPool().getBroker()) {
            if(isDocument()) {
                final Element e = doc.getDocumentElement();
                if(e instanceof NodeProxy) {
                    return broker.getNodeValue(((StoredNode) e).extract(), false);
                } else if(e != null) {
                    return broker.getNodeValue((ElementImpl) e, false);
                } else
                // probably a binary resource
                {
                    return "";
                }
            } else {
                return broker.getNodeValue(this.asStoredNode(), false);
            }
        } catch(final EXistException e) {
            //TODO : raise an exception here ! -pb
        }
        return "";
    }

    public String getNodeValueSeparated() {
        try(final DBBroker broker = doc.getBrokerPool().getBroker()) {
            return broker.getNodeValue(asStoredNode(), true);
        } catch(final EXistException e) {
            //TODO : raise an exception here !
        }
        return "";
    }

    //TODO this should be improved. Consider an interface that contains just the
    // getters from INodeHandle and persistent.NodeHandle
    public StoredNode asStoredNode() {
        return new StoredNode(
            this.getNodeType(),
            this.getNodeId(),
            this.getOwnerDocument(),
            this.getInternalAddress()) {
        };
    }

    @Override
    public String getStringValue() {
        return getNodeValue();
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        return UntypedAtomicValue.convertTo(getNodeValue(), requiredType);
    }

    @Override
    public AtomicValue atomize() throws XPathException {
        return new UntypedAtomicValue(getNodeValue());
    }

    @Override
    public void toSAX(final DBBroker broker, final ContentHandler handler, final Properties properties) throws SAXException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        serializer.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        if(properties != null) {
            serializer.setProperties(properties);
        }

        if(handler instanceof LexicalHandler) {
            serializer.setSAXHandlers(handler, (LexicalHandler) handler);
        } else {
            serializer.setSAXHandlers(handler, null);
        }
        serializer.toSAX(this);
    }

    @Override
    public void copyTo(final DBBroker broker, final DocumentBuilderReceiver receiver) throws SAXException {
        NodeImpl node = null;
        if(nodeType < 0) {
            node = (NodeImpl) getNode();
        }
        if(nodeType == Node.ATTRIBUTE_NODE) {
            final AttrImpl attr = (node == null ? (AttrImpl) getNode() : (AttrImpl) node);
            receiver.attribute(attr.getQName(), attr.getValue());
        } else {
            receiver.addReferenceNode(this);
        }
    }

    @Override
    public int conversionPreference(final Class javaClass) {
        if(javaClass.isAssignableFrom(NodeProxy.class)) {
            return 0;
        } else if(javaClass.isAssignableFrom(Node.class)) {
            return 1;
        } else if(javaClass == String.class || javaClass == CharSequence.class) {
            return 2;
        } else if(javaClass == Character.class || javaClass == char.class) {
            return 2;
        } else if(javaClass == Double.class || javaClass == double.class) {
            return 10;
        } else if(javaClass == Float.class || javaClass == float.class) {
            return 11;
        } else if(javaClass == Long.class || javaClass == long.class) {
            return 12;
        } else if(javaClass == Integer.class || javaClass == int.class) {
            return 13;
        } else if(javaClass == Short.class || javaClass == short.class) {
            return 14;
        } else if(javaClass == Byte.class || javaClass == byte.class) {
            return 15;
        } else if(javaClass == Boolean.class || javaClass == boolean.class) {
            return 16;
        } else if(javaClass == Object.class) {
            return 20;
        } else {
            return Integer.MAX_VALUE;
        }
    }

    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if(target.isAssignableFrom(NodeProxy.class)) {
            return (T) this;
        } else if(target.isAssignableFrom(Node.class) || target == Object.class) {
            return (T) getNode();
        } else {
            final StringValue v = new StringValue(getStringValue());
            return v.toJavaObject(target);
        }
    }

    /*
     * Methods of interface Sequence:
     */

    @Override
    public int getItemType() {
        return getType();
    }

    @Override
    public int getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public boolean isCached() {
        return false;
    }

    @Override
    public void setIsCached(final boolean cached) {
        //TODO : return something useful ? -pb
    }

    @Override
    public NodeSet toNodeSet() throws XPathException {
        return this;
    }

    @Override
    public MemoryNodeSet toMemNodeSet() throws XPathException {
        return null;
    }

    @Override
    public boolean effectiveBooleanValue() throws XPathException {
        return true;
    }

    @Override
    public void removeDuplicates() {
        // single node: no duplicates
    }

    @Override
    public void setSelfAsContext(final int contextId) {
        addContextNode(contextId, this);
    }

    /* -----------------------------------------------*
     * Methods of class NodeSet
     * -----------------------------------------------*/

    @Override
    public NodeSetIterator iterator() {
        return new SingleNodeIterator(this);
    }

    @Override
    public SequenceIterator iterate() throws XPathException {
        return new SingleNodeIterator(this);
    }

    @Override
    public SequenceIterator unorderedIterator() {
        return new SingleNodeIterator(this);
    }

    @Override
    public boolean contains(final NodeProxy proxy) {
        if(doc.getDocId() != proxy.doc.getDocId()) {
            return false;
        } else {
            return nodeId.equals(proxy.getNodeId());
        }
    }

    @Override
    public void addAll(final NodeSet other) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean hasOne() {
        return true;
    }

    @Override
    public boolean hasMany() {
        return false;
    }

    @Override
    public void add(final NodeProxy proxy) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(final Item item) throws XPathException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(final NodeProxy proxy, final int sizeHint) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addAll(final Sequence other) throws XPathException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getLength() {
        //TODO : how to delegate to the real node implementation's getLength() ?
        return 1;
    }

    //TODO : evaluate both semantics
    @Override
    public int getItemCount() {
        return 1;
    }

    @Override
    public Node item(final int pos) {
        return pos > 0 ? null : getNode();
    }

    @Override
    public Item itemAt(final int pos) {
        return pos > 0 ? null : this;
    }

    @Override
    public NodeProxy get(final int pos) {
        return pos > 0 ? null : this;
    }

    @Override
    public NodeProxy get(final NodeProxy p) {
        return contains(p) ? this : null;
    }

    @Override
    public NodeProxy get(final DocumentImpl document, final NodeId nodeId) {
        if(!this.nodeId.equals(nodeId)) {
            return null;
        } else if(this.doc.getDocId() != document.getDocId()) {
            return null;
        } else {
            return this;
        }
    }

    @Override
    public NodeProxy parentWithChild(final NodeProxy proxy, final boolean directParent,
            final boolean includeSelf, final int level) {
        return parentWithChild(proxy.getOwnerDocument(), proxy.getNodeId(), directParent, includeSelf);
    }

    @Override
    public NodeProxy parentWithChild(final DocumentImpl otherDoc, final NodeId otherId,
            final boolean directParent, final boolean includeSelf) {
        if(otherDoc.getDocId() != doc.getDocId()) {
            return null;
        } else if(includeSelf && otherId.compareTo(nodeId) == 0) {
            return this;
        } else {
            NodeId parentId = otherId.getParentId();
            while(parentId != null) {
                if(parentId.compareTo(nodeId) == 0) {
                    return this;
                } else if(directParent) {
                    return null;
                }
                parentId = parentId.getParentId();
            }
            return null;
        }
    }

    @Override
    public NodeSet getContextNodes(final int contextId) {
        final NewArrayNodeSet result = new NewArrayNodeSet();
        ContextItem contextNode = getContext();
        while(contextNode != null) {
            final NodeProxy p = contextNode.getNode();
            p.addMatches(this);
            if(!result.contains(p)) {
                //TODO : why isn't "this" involved here ? -pb
                if(contextId != Expression.NO_CONTEXT_ID) {
                    p.addContextNode(contextId, p);
                }
                result.add(p);
            }
            contextNode = contextNode.getNextDirect();
        }
        return result;
    }

    @Override
    public int getState() {
        return 0;
    }

    @Override
    public boolean hasChanged(final int previousState) {
        return false;
    }

    @Override
    public void destroy(final XQueryContext context, final Sequence contextSequence) {
        // Nothing to do
    }

    @Override
    public boolean isCacheable() {
        return true;
    }

    @Override
    public int getSizeHint(final DocumentImpl document) {
        if(document.getDocId() == doc.getDocId()) {
            return 1;
        } else {
            return Constants.NO_SIZE_HINT;
        }
    }

    @Override
    public DocumentSet getDocumentSet() {
        return this;
    }

    @Override
    public Iterator<Collection> getCollectionIterator() {
        return new Iterator<Collection>() {
            boolean hasNext = true;

            @Override
            public final boolean hasNext() {
                return hasNext;
            }

            @Override
            public final Collection next() {
                hasNext = false;
                return NodeProxy.this.getOwnerDocument().getCollection();
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException("Remove is not implemented for NodeProxt#getCollectionIterator");
            }
        };
    }

    @Override
    public NodeSet intersection(final NodeSet other) {
        if(other.contains(this)) {
            return this;
        } else {
            return NodeSet.EMPTY_SET;
        }
    }

    @Override
    public NodeSet deepIntersection(final NodeSet other) {
        final NodeProxy p = other.parentWithChild(this, false, true, UNKNOWN_NODE_LEVEL);
        if(p == null) {
            return NodeSet.EMPTY_SET;
        } else if(!nodeId.equals(p.nodeId)) {
            p.addMatches(this);
        }
        return p;
    }

    @Override
    public NodeSet union(final NodeSet other) {
        if(other.isEmpty()) {
            return this;
        }
        final NewArrayNodeSet result = new NewArrayNodeSet();
        result.addAll(other);
        result.add(this);
        return result;
    }

    @Override
    public NodeSet except(final NodeSet other) {
        return other.contains(this) ? NodeSet.EMPTY_SET : this;
    }

    @Override
    public NodeSet filterDocuments(final NodeSet otherSet) {
        final DocumentSet docs = otherSet.getDocumentSet();
        if(docs.contains(doc.getDocId())) {
            return this;
        }
        return NodeSet.EMPTY_SET;
    }

    @Override
    public void setProcessInReverseOrder(final boolean inReverseOrder) {
        //Nothing to do
    }

    @Override
    public boolean getProcessInReverseOrder() {
        return false;
    }

    @Override
    public NodeSet getParents(final int contextId) {
        final NodeId pid = nodeId.getParentId();
        if(pid == null || pid == NodeId.DOCUMENT_NODE) {
            return NodeSet.EMPTY_SET;
        }

        final NodeProxy parent = new NodeProxy(doc, pid, Node.ELEMENT_NODE);
        if(contextId != Expression.NO_CONTEXT_ID) {
            parent.addContextNode(contextId, this);
        } else {
            parent.copyContext(this);
        }
        parent.addMatches(this);
        return parent;
    }

    @Override
    public NodeSet getAncestors(final int contextId, final boolean includeSelf) {
        final NodeSet ancestors = new NewArrayNodeSet();
        if(includeSelf) {
            ancestors.add(this);
        }

        NodeId parentID = nodeId.getParentId();
        while(parentID != null) {
            final NodeProxy parent = new NodeProxy(getOwnerDocument(), parentID, Node.ELEMENT_NODE);
            if(contextId != Expression.NO_CONTEXT_ID) {
                parent.addContextNode(contextId, this);
            } else {
                parent.copyContext(this);
            }
            parent.addMatches(this);
            ancestors.add(parent);
            parentID = parentID.getParentId();
        }
        return ancestors;
    }

    @Override
    public NodeSet selectParentChild(final NodeSet al, final int mode) {
        return selectParentChild(al, mode, Expression.NO_CONTEXT_ID);
    }

    @Override
    public NodeSet selectParentChild(final NodeSet al, final int mode, final int contextId) {
        return NodeSetHelper.selectParentChild(this, al, mode, contextId);
    }

    @Override
    public boolean matchParentChild(final NodeSet al, final int mode, final int contextId) {
        return NodeSetHelper.matchParentChild(this, al, mode, contextId);
    }

    /* (non-Javadoc)
     * @see org.exist.dom.persistent.NodeSet#selectAncestors(org.exist.dom.persistent.NodeSet, boolean, int)
     */
    @Override
    public NodeSet selectAncestors(final NodeSet al, final boolean includeSelf, final int contextId) {
        return NodeSetHelper.selectAncestors(this, al, includeSelf, contextId);
    }

    public boolean matchAncestors(final NodeSet al, final boolean includeSelf, final int contextId) {
        return NodeSetHelper.matchAncestors(this, al, includeSelf, contextId);
    }

    @Override
    public NodeSet selectPrecedingSiblings(final NodeSet siblings, final int contextId) {
        return NodeSetHelper.selectPrecedingSiblings(this, siblings, contextId);
    }

    @Override
    public NodeSet selectFollowingSiblings(final NodeSet siblings, final int contextId) {
        return NodeSetHelper.selectFollowingSiblings(this, siblings, contextId);
    }

    @Override
    public NodeSet selectAncestorDescendant(final NodeSet al, final int mode,
            final boolean includeSelf, final int contextId, final boolean copyMatches) {
        return NodeSetHelper.selectAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    @Override
    public boolean matchAncestorDescendant(final NodeSet al, final int mode,
            final boolean includeSelf, final int contextId, final boolean copyMatches) {
        return NodeSetHelper.matchAncestorDescendant(this, al, mode, includeSelf, contextId);
    }

    @Override
    public NodeSet selectPreceding(final NodeSet preceding, final int contextId) throws XPathException {
        return NodeSetHelper.selectPreceding(this, preceding);
    }

    @Override
    public NodeSet selectPreceding(final NodeSet preceding, final int position,
            final int contextId) throws XPathException, UnsupportedOperationException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeSet selectFollowing(final NodeSet following, final int contextId) throws XPathException {
        return NodeSetHelper.selectFollowing(this, following);
    }

    @Override
    public NodeSet selectFollowing(final NodeSet following, final int position, final int contextId) throws XPathException {
        throw new UnsupportedOperationException();
    }

    @Override
    public NodeSet directSelectAttribute(final DBBroker broker, final NodeTest test, final int contextId) {
        if(nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE) {
            return NodeSet.EMPTY_SET;
        }
        try {
            NewArrayNodeSet result = null;
            final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true);
            int status = reader.next();
            if(status != XMLStreamReader.START_ELEMENT) {
                return NodeSet.EMPTY_SET;
            }
            final int attrs = reader.getAttributeCount();
            for(int i = 0; i < attrs; i++) {
                status = reader.next();
                if(status != XMLStreamReader.ATTRIBUTE) {
                    break;
                }
                final AttrImpl attr = (AttrImpl) reader.getNode();
                if(test.matches(attr)) {
                    final NodeProxy child = new NodeProxy(attr);
                    if(Expression.NO_CONTEXT_ID != contextId) {
                        child.addContextNode(contextId, this);
                    } else {
                        child.copyContext(this);
                    }
                    if(!test.isWildcardTest()) {
                        return child;
                    }
                    if(result == null) {
                        result = new NewArrayNodeSet();
                    }
                    result.add(child);
                }
            }
            return result == null ? NodeSet.EMPTY_SET : result;
        } catch(final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch(final XMLStreamException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public NodeSet directSelectChild(final QName qname, final int contextId) {
        if(nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE) {
            return NodeSet.EMPTY_SET;
        }
        final NodeImpl node = (NodeImpl) getNode();
        if(node.getNodeType() != Node.ELEMENT_NODE) {
            return NodeSet.EMPTY_SET;
        }
        final NodeList children = node.getChildNodes();
        if(children.getLength() == 0) {
            return NodeSet.EMPTY_SET;
        }
        final NewArrayNodeSet result = new NewArrayNodeSet();
        IStoredNode<?> child;
        for(int i = 0; i < children.getLength(); i++) {
            child = (IStoredNode<?>) children.item(i);
            if(child.getQName().equals(qname)) {
                final NodeProxy p = new NodeProxy(doc, child.getNodeId(), Node.ELEMENT_NODE, child.getInternalAddress());
                if(Expression.NO_CONTEXT_ID != contextId) {
                    p.addContextNode(contextId, this);
                } else {
                    p.copyContext(this);
                }
                p.addMatches(this);
                result.add(p);
            }
        }
        return result;
    }

    @Override
    public String toString() {
        if(nodeId == NodeId.DOCUMENT_NODE) {
            return "Document node for " + doc.getDocId();
        } else {
            return doc.getNode(nodeId).getNodeName();
        }
    }

    private static final class SingleNodeIterator implements NodeSetIterator, SequenceIterator {

        private boolean hasNext = true;
        private NodeProxy node;

        public SingleNodeIterator(final NodeProxy node) {
            this.node = node;
        }

        @Override
        public final boolean hasNext() {
            return hasNext;
        }

        @Override
        public final NodeProxy next() {
            if(!hasNext) {
                throw new NoSuchElementException();
            } else {
                hasNext = false;
                return node;
            }
        }

        @Override
        public final NodeProxy peekNode() {
            return node;
        }

        @Override
        public final void remove() {
            throw new UnsupportedOperationException("remove is not implemented for SingleNodeIterator");
        }

        @Override
        public final Item nextItem() {
            if(!hasNext) {
                return null;
            } else {
                hasNext = false;
                return node;
            }
        }

        @Override
        public final void setPosition(final NodeProxy proxy) {
            node = proxy;
            hasNext = true;
        }
    }

    /**
     * *********************************************
     * Methods of MutableDocumentSet
     * **********************************************
     */

    @Override
    public Iterator<DocumentImpl> getDocumentIterator() {
        return new Iterator<DocumentImpl>() {

            private boolean hasMore = true;

            @Override
            public final boolean hasNext() {
                return hasMore;
            }

            @Override
            public final DocumentImpl next() {
                if(!hasMore) {
                    throw new NoSuchElementException();
                } else {
                    hasMore = false;
                    return doc;
                }
            }

            @Override
            public final void remove() {
                throw new UnsupportedOperationException("remove is not implemented for NodeProxy#getDocumentIterator");
            }
        };
    }

    @Override
    public int getDocumentCount() {
        return 1;
    }

    public DocumentImpl getDoc() {
        return doc;
    }

    @Override
    public DocumentImpl getDoc(final int docId) {
        if(docId == this.doc.getDocId()) {
            return this.doc;
        }
        return null;
    }

    @Override
    public XmldbURI[] getNames() {
        return new XmldbURI[]{
            this.doc.getURI()
        };
    }

    @Override
    public DocumentSet intersection(final DocumentSet other) {
        if(other.contains(doc.getDocId())) {
            final DefaultDocumentSet r = new DefaultDocumentSet();
            r.add(doc);
            return r;
        } else {
            return DefaultDocumentSet.EMPTY_DOCUMENT_SET;
        }
    }

    @Override
    public boolean contains(final DocumentSet other) {
        if(other.getDocumentCount() > 1) {
            return false;
        } else if(other.getDocumentCount() == 0) {
            return true;
        } else {
            return other.contains(doc.getDocId());
        }
    }

    @Override
    public boolean contains(final int docId) {
        return doc.getDocId() == docId;
    }

    @Override
    public NodeSet docsToNodeSet() {
        return new NodeProxy(doc, NodeId.DOCUMENT_NODE);
    }

    @Override
    public void lock(final DBBroker broker, final boolean exclusive, final boolean checkExisting) throws LockException {
        final Lock docLock = doc.getUpdateLock();
        docLock.acquire(exclusive ? Lock.WRITE_LOCK : Lock.READ_LOCK);
    }

    @Override
    public void unlock(final boolean exclusive) {
        final Lock docLock = doc.getUpdateLock();
        if(exclusive) {
            docLock.release(Lock.WRITE_LOCK);
        } else if(docLock.isLockedForRead(Thread.currentThread())) {
            docLock.release(Lock.READ_LOCK);
        }
    }

    @Override
    public boolean equalDocs(final DocumentSet other) {
        if(this == other) {
            // we are comparing the same objects
            return true;
        } else if(other.getDocumentCount() != 1) {
            return false;
        } else {
            return other.contains(doc.getDocId());
        }
    }

    @Override
    public boolean directMatchAttribute(final DBBroker broker, final NodeTest test, final int contextId) {
        if(nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE) {
            return false;
        }
        try {
            final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(this, true);
            int status = reader.next();
            if(status != XMLStreamReader.START_ELEMENT) {
                return false;
            }
            final int attrs = reader.getAttributeCount();
            for(int i = 0; i < attrs; i++) {
                status = reader.next();
                if(status != XMLStreamReader.ATTRIBUTE) {
                    break;
                }
                final AttrImpl attr = (AttrImpl) reader.getNode();
                if(test.matches(attr)) {
                    return true;
                }
            }
            return false;
        } catch(final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        } catch(final XMLStreamException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public boolean directMatchChild(final QName qname, final int contextId) {
        if(nodeType != UNKNOWN_NODE_TYPE && nodeType != Node.ELEMENT_NODE) {
            return false;
        }
        final NodeImpl node = (NodeImpl) getNode();
        if(node.getNodeType() != Node.ELEMENT_NODE) {
            return false;
        }
        final NodeList children = node.getChildNodes();
        if(children.getLength() == 0) {
            return false;
        }
        IStoredNode<?> child;
        for(int i = 0; i < children.getLength(); i++) {
            child = (IStoredNode<?>) children.item(i);
            if(child.getQName().equals(qname)) {
                return true;
            }
        }
        return false;
    }
}
