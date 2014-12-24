/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2014 The eXist Project
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
package org.exist.dom.persistent;

import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.exist.stax.IEmbeddedXMLStreamReader;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.Signatures;
import org.exist.storage.dom.INodeIterator;
import org.exist.util.pool.NodePool;
import org.exist.xquery.Constants;
import org.w3c.dom.DOMException;
import org.w3c.dom.Node;

import java.io.IOException;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

/**
 * The base class for all persistent DOM nodes in the database.
 *
 * @author Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 */
public abstract class StoredNode<T extends StoredNode> extends NodeImpl<T> implements Visitable, NodeHandle, IStoredNode<T> {

    public static final int LENGTH_SIGNATURE_LENGTH = 1; //sizeof byte
    public static final long UNKNOWN_NODE_IMPL_ADDRESS = -1;

    protected NodeId nodeId = null;

    protected DocumentImpl ownerDocument = null;

    private long internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;

    protected final short nodeType;

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     */
    protected StoredNode(final short nodeType) {
        this.nodeType = nodeType;
    }

    /**
     * Creates a new <code>StoredNode</code> instance.
     *
     * @param nodeType a <code>short</code> value
     * @param nodeId   a <code>NodeId</code> value
     */
    protected StoredNode(final short nodeType, final NodeId nodeId) {
        this.nodeType = nodeType;
        this.nodeId = nodeId;
    }

    protected StoredNode(final short nodeType, final NodeId nodeId, final DocumentImpl ownerDocument, long internalAddress) {
        this(nodeType, nodeId);
        this.ownerDocument = ownerDocument;
        this.internalAddress = internalAddress;
    }

    protected StoredNode(final StoredNode other) {
        this.nodeType = other.nodeType;
        this.nodeId = other.nodeId;
        this.internalAddress = other.internalAddress;
        this.ownerDocument = other.ownerDocument;
    }

    /**
     * Extracts just the details of the StoredNode
     */
    public StoredNode extract() {
        return new StoredNode(this) {
        };
    }

    /**
     * Reset this object to its initial state. Required by the
     * parser to be able to reuse node objects.
     */
    public void clear() {
        this.nodeId = null;
        this.internalAddress = UNKNOWN_NODE_IMPL_ADDRESS;
    }

    @Override
    public byte[] serialize() {
        throw new DOMException(DOMException.INVALID_ACCESS_ERR, "Can't serialize " + getClass().getName());
    }

    /**
     * Read a node from the specified byte array.
     * <p/>
     * This checks the node type and calls the {@link #deserialize(byte[], int, int, DocumentImpl, boolean)}
     * method of the corresponding node class.
     *
     * @param data
     * @param start
     * @param len
     * @param doc
     */
    public static StoredNode deserialize(final byte[] data, final int start, final int len, final DocumentImpl doc) {
        return deserialize(data, start, len, doc, false);
    }

    /**
     * Read a node from the specified byte array.
     * <p/>
     * This checks the node type and calls the {@link #deserialize(byte[], int, int, DocumentImpl, boolean)}
     * method of the corresponding node class. The node will be allocated in the pool
     * and should be released once it is no longer needed.
     *
     * @param data
     * @param start
     * @param len
     * @param doc
     */
    public static StoredNode deserialize(final byte[] data, final int start, final int len, final DocumentImpl doc, boolean pooled) {
        final short type = Signatures.getType(data[start]);
        switch(type) {
            case Node.TEXT_NODE:
                return TextImpl.deserialize(data, start, len, doc, pooled);
            case Node.ELEMENT_NODE:
                return ElementImpl.deserialize(data, start, len, doc, pooled);
            case Node.ATTRIBUTE_NODE:
                return AttrImpl.deserialize(data, start, len, doc, pooled);
            case Node.PROCESSING_INSTRUCTION_NODE:
                return ProcessingInstructionImpl.deserialize(data, start, len, doc, pooled);
            case Node.COMMENT_NODE:
                return CommentImpl.deserialize(data, start, len, doc, pooled);
            case Node.CDATA_SECTION_NODE:
                return CDATASectionImpl.deserialize(data, start, len, doc, pooled);
            default:
                LOG.error("Unknown node type: " + type);
                Thread.dumpStack();
                return null;
        }
    }

    @Override
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

    @Override
    public void setQName(final QName qname) {
        //do nothing
    }

    @Override
    public boolean equals(final Object obj) {
        if(!(obj instanceof StoredNode)) {
            return false;
        }
        return ((StoredNode) obj).nodeId.equals(nodeId);
    }

    @Override
    public void setNodeId(final NodeId dln) {
        this.nodeId = dln;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    @Override
    public long getInternalAddress() {
        return internalAddress;
    }

    @Override
    public void setInternalAddress(final long internalAddress) {
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

    @Override
    public void setDirty(final boolean dirty) {
        //Nothing to do
    }

    @Override
    public short getNodeType() {
        return this.nodeType;
    }

    @Override
    public DocumentImpl getOwnerDocument() {
        return ownerDocument;
    }

    @Override
    public void setOwnerDocument(final DocumentImpl ownerDocument) {
        this.ownerDocument = ownerDocument;
    }

    @Override
    public Node getParentNode() {
        final NodeId parentId = nodeId.getParentId();
        if(parentId == NodeId.DOCUMENT_NODE) {
            return ownerDocument;
        }
        // Filter out the temporary nodes wrapper element
        if(parentId.getTreeLevel() == 1 && getOwnerDocument().getCollection().isTempCollection()) {
            return ownerDocument;
        }
        return ownerDocument.getNode(parentId);
    }

    @Override
    public StoredNode getParentStoredNode() {
        final Node parent = getParentNode();
        return parent instanceof StoredNode ? (StoredNode) parent : null;
    }

    @Override
    public Node getPreviousSibling() {
        final StoredNode parent = getParentStoredNode();
        if(parent == null) {
            return null;
        }
        if(parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                final int level = nodeId.getTreeLevel();
                IStoredNode last = null;
                while(reader.hasNext()) {
                    final int status = reader.next();
                    final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    if(status != XMLStreamConstants.END_ELEMENT && currentId.getTreeLevel() == level) {
                        if(currentId.equals(nodeId)) {
                            return last;
                        }
                        last = reader.getNode();
                    }
                }
            } catch(final IOException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch(final XMLStreamException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch(final EXistException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } finally {
                ownerDocument.getBrokerPool().release(broker);
            }
            return null;
        }
        final NodeId firstChild = parent.getNodeId().newChild();
        if(nodeId.equals(firstChild)) {
            return null;
        }
        final NodeId siblingId = nodeId.precedingSibling();
        return ownerDocument.getNode(siblingId);
    }

    @Override
    public Node getNextSibling() {
        if(nodeId.getTreeLevel() == 2 && getOwnerDocument().getCollection().isTempCollection()) {
            return null;
        }
        final StoredNode parent = getParentStoredNode();
        if(parent == null) {
            return null;
        }
        if(parent.isDirty()) {
            DBBroker broker = null;
            try {
                broker = ownerDocument.getBrokerPool().get(null);
                final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(parent, true);
                final int level = nodeId.getTreeLevel();
                while(reader.hasNext()) {
                    final int status = reader.next();
                    final NodeId currentId = (NodeId) reader.getProperty(ExtendedXMLStreamReader.PROPERTY_NODE_ID);
                    if(status != XMLStreamConstants.END_ELEMENT
                        && currentId.getTreeLevel() == level
                        && currentId.compareTo(nodeId) > 0) {
                        return reader.getNode();
                    }
                }
            } catch(final IOException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch(final XMLStreamException e) {
                LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
                //TODO : throw exception -pb
            } catch(final EXistException e) {
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

    protected IStoredNode getLastNode(final IStoredNode node) {
        if(!node.hasChildNodes()) {
            return node;
        }
        DBBroker broker = null;
        try {
            broker = ownerDocument.getBrokerPool().get(null);
            final IEmbeddedXMLStreamReader reader = broker.getXMLStreamReader(node, true);
            while(reader.hasNext()) {
                reader.next();
            }
            return reader.getPreviousNode();
        } catch(final IOException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
            //TODO : throw exception -pb
        } catch(final XMLStreamException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
            //TODO : throw exception -pb
        } catch(final EXistException e) {
            LOG.error("Internal error while reading child nodes: " + e.getMessage(), e);
            //TODO : throw exception -pb
        } finally {
            ownerDocument.getBrokerPool().release(broker);
        }
        return null;
    }

//    protected StoredNode getLastNode(final Iterator<StoredNode> iterator, final StoredNode node) {
//        if(!node.hasChildNodes()) {
//            return node;
//        }
//        final int children = node.getChildCount();
//        StoredNode next = null;
//        for(int i = 0; i < children; i++) {
//            next = iterator.next();
//            //Recursivity helps taversing...
//            next = getLastNode(iterator, next);
//        }
//        return next;
//    }

    @Override
    public NodePath getPath() {
        final NodePath path = new NodePath();
        if(getNodeType() == Node.ELEMENT_NODE) {
            path.addComponent(getQName());
        }
        NodeImpl parent = (NodeImpl) getParentNode();
        while(parent != null && parent.getNodeType() != Node.DOCUMENT_NODE) {
            path.addComponentAtStart(parent.getQName());
            parent = (NodeImpl) parent.getParentNode();
        }
        return path;
    }

    @Override
    public NodePath getPath(final NodePath parentPath) {
        if(getNodeType() == Node.ELEMENT_NODE) {
            parentPath.addComponent(getQName());
        }
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

    public String toString(final boolean top) {
        return toString();
    }

    /**
     * Release all memory resources hold by this node.
     */
    @Override
    public void release() {
        ownerDocument = null;
        clear();
        NodePool.getInstance().returnNode(this);
    }

    public boolean accept(final NodeVisitor visitor) {
        try(final DBBroker broker = ownerDocument.getBrokerPool().get(null);
                final INodeIterator iterator = broker.getNodeIterator(this)) {
            iterator.next();
            return accept(iterator, visitor);
        } catch(final EXistException | IOException e) {
            LOG.error("Exception while reading node: " + e.getMessage(), e);
            //TODO : throw exception -pb
        }
        return false;
    }

    @Override
    public boolean accept(final INodeIterator iterator, final NodeVisitor visitor) {
        return visitor.visit(this); //TODO iterator is not used here?
    }

    @Override
    public int compareTo(final StoredNode other) {
        if(other.ownerDocument == ownerDocument) {
            return nodeId.compareTo(other.nodeId);
        } else if(ownerDocument.getDocId() < other.ownerDocument.getDocId()) {
            return Constants.INFERIOR;
        } else {
            return Constants.SUPERIOR;
        }
    }
}
