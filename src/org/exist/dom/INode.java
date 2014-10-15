package org.exist.dom;

/**
 * Interface for Nodes in eXist
 * used for both persistent and
 * in-memory nodes.
 * 
 * @param <T> The type of the persistent
 * or in-memory document
 * 
 * @author Adam Retter <adam@exist-db.org>
 */
public interface INode<D extends org.w3c.dom.Document, T extends INode> extends org.w3c.dom.Node,
    INodeHandle<D>, Comparable<T> {
    
    /**
     * Get the qualified name of the Node
     * 
     * @return The qualified name of the Node
     */
    public QName getQName();

    //TODO try and get rid of this eventually (AR)?
    public void setQName(QName qname);
}
