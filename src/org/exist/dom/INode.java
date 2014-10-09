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
public interface INode<T extends org.w3c.dom.Document> extends org.w3c.dom.Node, INodeHandle<T> {
    
    /**
     * Get the qualified name of the Node
     * 
     * @return The qualified name of the Node
     */
    public QName getQName();

    //TODO try and get rid of this eventually (AR)?
    public void setQName(QName qname);
}
