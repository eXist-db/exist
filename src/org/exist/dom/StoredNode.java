/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2007 The eXist Project
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

import java.io.IOException;
import java.util.Iterator;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

import org.exist.EXistException;
import org.exist.numbering.NodeId;
import org.exist.stax.EmbeddedXMLStreamReader;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Constants;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 *  The base class for all persistent DOM nodes in the database.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public class StoredNode extends NodeImpl implements Visitable, NodeHandle {

    public final static int LENGTH_SIGNATURE_LENGTH = 1; //sizeof byte
    public final static long UNKNOWN_NODE_IMPL_ADDRESS = -1;

    protected NodeId nodeId = null;

    protected DocumentImpl ownerDocument = null;

    private long internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;

    private short nodeType = NodeProxy.UNKNOWN_NODE_TYPE;

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     */
    public StoredNode(short nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     * @param nodeId a <code>NodeId</code> value
     */
    public StoredNode(short nodeType, NodeId nodeId) {
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    /**
     * Copy constructor: creates a copy of the other node.
     *
     * @param other a <code>StoredNode</code> value
     */
    public StoredNode(StoredNode other) {
        this.nodeType = other.nodeType;
        this.nodeId = other.nodeId;
        this.internalAddress = other.internalAddress;
        this.ownerDocument = other.ownerDocument;
    }

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param other a <code>NodeProxy</code> value
     */
    public StoredNode(NodeProxy other) {
        this.ownerDocument = other.getDocument();
        this.nodeType = other.getNodeType();
        this.nodeId = other.getNodeId();    	
        this.internalAddress = other.getInternalAddress();
    }

    /**
     * Reset this object to its initial state. Required by the
     * parser to be able to reuse node objects.
     */
    public void clear() {
        this.nodeId = null;
        this.internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
    } 

    public byte[] serialize() {
        throw new DOMException(DOMException.INVALID_ACCESS_ERR, "Can't serialize " + getClass().getName());
    }

    /**
     * Read a node from the specified byte array.
     * 
     * This checks the node type and calls the {@link #deserialize(byte[], int, int,DocumentImpl,boolean)}
     * method of the corresponding node class.
     * 
     * @param data
     * @param start
     * @param len
     * @param doc
     */
    public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc) {
        return deserialize(data, start, len, doc, false);
    }

    /**
     * Read a node from the specified byte array.
     * 
     * This checks the node type and calls the {@link #deserialize(byte[], int, int, DocumentImpl, boolean)}
     * method of the corresponding node class. The node will be allocated in the pool
     * and should be released once it is no longer needed.
     * 
     * @param data
     * @param start
     * @param len
     * @param doc
     */
    public static StoredNode deserialize(byte[] data, int start, int len, DocumentImpl doc, boolean pooled) {
            final short type = Signatures.getType(data[start]);
        switch (type) {
        case Node.TEXT_NODE :
            return TextImpl.deserialize(data, start, len, doc, pooled);
        case Node.ELEMENT_NODE :
            return ElementImpl.deserialize(data, start, len, doc, pooled);
        case Node.ATTRIBUTE_NODE :
            return AttrImpl.deserialize(data, start, len, doc, pooled);
        case Node.PROCESSING_INSTRUCTION_NODE :
            return ProcessingInstructionImpl.deserialize(data, start, len, doc, pooled);
        case Node.COMMENT_NODE :
            return CommentImpl.deserialize(data, start, len, doc, pooled);
        case Node.CDATA_SECTION_NODE :
            return CDATASectionImpl.deserialize(data, start, len, doc, pooled);
        default :
            LOG.error("Unknown node type: " + type);
            Thread.dumpStack();
            return null;
        }
    }

    public QName getQName() {
        switch(getNodeType()) {
        case Node.DOCUMENT_NODE:
            return QName.DOCUMENT_QNAME;
        case Node.TEXT_NODE:
            return QName.TEXT_QNAME;
        case Node.COMMENT_NODE:
            return QName.COMMENT_QNAME;
        case Node.DOCUMENT_TYPE_NODE:
            return QName.DOCTYPE_QNAME;
        default:
            LOG.error("Unknown node type: " + getNodeType()); 
            return null;
        }
    }

    /**
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof StoredNode))
            {return false;}
        return ((StoredNode)obj).nodeId.equals(nodeId);
    }

    /**
     * The method <code>setNodeId</code>
     *
     * @param dln a <code>NodeId</code> value
     */
    public void setNodeId(NodeId dln) {
        this.nodeId = dln;
    }

    /**
     * The method <code>getNodeId</code>
     *
     * @return a <code>NodeId</code> value
     */
    public NodeId getNodeId() {
        return nodeId;
    }

    /**
     *  Get the internal storage address of this node
     *
     *@return    The internalAddress value
     */
    public long getInternalAddress() {
        return internalAddress;
    }

    /**
     *  Set the internal storage address of this node.
     *
     *@param  internalAddress  The new internalAddress value
     */
    public void setInternalAddress(long internalAddress) {
        this.internalAddress = internalAddress;
    }

    /**
     * Returns true if the node was modified recently and nodes
     * were inserted at the start or in the middle of its children.
     *
     * @return TRUE when node is 'dirty'
     */
    public boolean isDirty() {
        return true;
    }

    public void setDirty(boolean dirty) {
        //Nothing to do
    }

    /**
     * @see org.w3c.dom.Node#getNodeType()
     */
    public short getNodeType() {
        return this.nodeType;
    }

    /**
     * @see org.w3c.dom.Node#getOwnerDocument()
     */
    public Document getOwnerDocument() {
        return ownerDocument;
    }

    public DocumentImpl getDocument() {
        return ownerDocument;
    }

    public DocumentAtExist getDocumentAtExist() {
        return ownerDocument;
    }

    /**
     *  Set the owner document.
     *
     *@param  ownerDocument  The new ownerDocument value
     */
    public void setOwnerDocument(DocumentImpl ownerDocument) {
        this.ownerDocument = ownerDocument;
    }

    public int getDocId() {
        return ownerDocument.getDocId();
    }

    /**
     * @see org.w3c.dom.Node#getParentNode()
     */
    public Node getParentNode() {
        final NodeId parentId = nodeId.getParentId();
        if (parentId == NodeId.DOCUMENT_NODE)
            {return ownerDocument;}
        // Filter out the temporary nodes wrapper element
        if (parentId.getTreeLevel() == 1 && ((DocumentImpl)getOwnerDocument()).getCollection().isTempCollection())
            {return ownerDocument;}
        return ownerDocument.getNode(parentId);
    }

    public StoredNode getParentStoredNode() {
        final Node parent = getParentNode();
        return parent instanceof StoredNode ? (StoredNode) parent : null;
    }

    /**
     * @see org.w3c.dom.Node#getPreviousSibling()
     */
    public Node getPreviousSibling() {
        final StoredNode parent = getParentStoredNode();
        if (parent == null) {return null;}
        if (parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                final int level = nodeId.getTreeLevel();
                StoredNode last = null;
                while (reader.hasNext()) {
                    final int status = reader.next();
                    final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    if (status != XMLStreamConstants.END_ELEMENT && currentId.getTreeLevel() == level) {
                        if (currentId.equals(nodeId))
                            {return last;}
                        last = reader.getNode();
                    }
                }
            } catch (final IOException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch (final XMLStreamException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
              //TODO : throw exception -pb
            } catch (final EXistException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
              //TODO : throw exception -pb
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
            return null;
        }
        final NodeId firstChild = parent.getNodeId().newChild();
        if (nodeId.equals(firstChild))
            {return null;}
        final NodeId siblingId = nodeId.precedingSibling();
        return ownerDocument.getNode(siblingId);
    }

    /**
     * @see org.w3c.dom.Node#getNextSibling()
     */
    public Node getNextSibling() {
        if (nodeId.getTreeLevel() == 2 && ((DocumentImpl)getOwnerDocument()).getCollection().isTempCollection())
            {return null;}
        final StoredNode parent = getParentStoredNode();
        if (parent == null) {return null;}
        if (parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                final int level = nodeId.getTreeLevel();
                while (reader.hasNext()) {
                    final int status = reader.next();
                    final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    if (status != XMLStreamConstants.END_ELEMENT && currentId.getTreeLevel() == level) {
                        if (currentId.compareTo(nodeId) > 0)
                            {return reader.getNode();}
                    }
                }
            } catch (final IOException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch (final XMLStreamException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
              //TODO : throw exception -pb
            } catch (final EXistException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
              //TODO : throw exception -pb
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
            return null;
        }
        final NodeId siblingId = nodeId.nextSibling();
        return ownerDocument.getNode(siblingId);
    }

    protected StoredNode getLastNode(StoredNode node) {
        if (!node.hasChildNodes())
            {return node;}
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final EmbeddedXMLStreamReader reader = broker.getXMLStreamReader(node, true);
            while (reader.hasNext()) {
                reader.next();
            }
            return reader.getPreviousNode();
        } catch (final IOException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
          //TODO : throw exception -pb
        } catch (final XMLStreamException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
          //TODO : throw exception -pb
        } catch (final EXistException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
          //TODO : throw exception -pb
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

    protected StoredNode getLastNode(Iterator<StoredNode> iterator, StoredNode node) {
        if (!node.hasChildNodes())
            {return node;}
        final int children = node.getChildCount();
        StoredNode next = null;
        for (int i = 0; i < children; i++) {
            next = iterator.next();
            //Recursivity helps taversing...
            next = getLastNode(iterator, next);
        }
        return next;
    }

    public NodePath getPath() {
        final NodePath path = new NodePath();
        if (getNodeType() == Node.ELEMENT_NODE)
            {path.addComponent(getQName());}
        NodeImpl parent = (NodeImpl)getParentNode();
        while (parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
            path.addComponentAtStart(parent.getQName());
            parent = (NodeImpl)parent.getParentNode();
        }
        return path;
    }

    public NodePath getPath(NodePath parentPath) {
        if (getNodeType() == Node.ELEMENT_NODE)
            {parentPath.addComponent(getQName());}
        return parentPath;
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        buf.append(nodeId.toString());
        buf.append('\t');
        buf.append(getQName());
        return buf.toString();
    }

    public String toString(boolean top) {
        return toString();
    }

    /**
     * Release all memory resources hold by this node. 
     */
    public void release() {
        ownerDocument = null;
        clear();
        NodePool.getInstance().returnNode(this);
    }

    public boolean accept(NodeVisitor visitor) {
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final Iterator<StoredNode> iterator = broker.getNodeIterator(this);
            iterator.next();
            return accept(iterator, visitor);
        } catch (final EXistException e) {
            LOG.error("Exception while reading node: " + e.getMessage(), e);
            //TODO : throw exception -pb
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return false;
    }

    public boolean accept(Iterator<StoredNode> iterator, NodeVisitor visitor) {
        return visitor.visit(this);
    }

    public int getNodeNumber() {
        return 0; //TODO: find a value for node number
    }

    @Deprecated
    private final static class PreviousSiblingVisitor implements NodeVisitor {

        private StoredNode current;
        private StoredNode last = null;
        
        public PreviousSiblingVisitor(StoredNode current) {
            this.current = current;
        }

        public boolean visit(StoredNode node) {
            if (node.nodeId.equals(current.nodeId))
                {return false;}
            if (node.nodeId.getTreeLevel() == current.nodeId.getTreeLevel())
                {last = node;}
            return true;
        }
    }

    @Override
    public int compareTo(Object other) {
        if( !(other instanceof StoredNode)) {
            return(Constants.INFERIOR);
        }
        final StoredNode n = (StoredNode)other;
        if(n.ownerDocument == ownerDocument) {
            return nodeId.compareTo(n.nodeId);
        } else if(ownerDocument.getDocId() < n.ownerDocument.getDocId()) {
            return(Constants.INFERIOR);
        } else {
            return(Constants.SUPERIOR);
        }
    }
}
