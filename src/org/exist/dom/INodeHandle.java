package org.exist.dom;

import org.exist.numbering.NodeId;

/**
 * Interface for handling Nodes in eXist
 * used for both persistent and
 * in-memory nodes.
 * 
 * @param <T> The type of the persistent
 * or in-memory document
 * 
 * @author Adam Retter <adam@exist-db.org>
 */
public interface INodeHandle<T extends org.w3c.dom.Document> {
    
    /**
     * Get the ID of the Node
     * 
     * @return The ID of the Node
     */
    public NodeId getNodeId();
    
    /**
     * Get the type of the node
     */
    public short getNodeType(); //TODO convert to enum? what about persistence of the enum id (if it is ever persisted?)?
    
    /**
     * @see org.w3c.dom.Node#getOwnerDocument()
     * 
     * @return The persistent Owner Document
     */
    public T getOwnerDocument(); //TODO consider extracting T into "org.exist.dom.IDocument extends org.w3c.com.Document" and returning an IDocument here
}
