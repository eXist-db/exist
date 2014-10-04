package org.exist.dom.persistent;

import org.exist.dom.INode;
import org.exist.storage.NodePath;


//TODO do we really need to extend Visitable any more?
public interface IStoredNode extends INode<DocumentImpl>, NodeHandle, Visitable { 
    
    //<editor-fold desc="serialization">
    /**
     * Serialize the state of this node
     * into a byte array.
     * 
     * @return A byte array containing the
     * serialization of the node
     */
    public byte[] serialize();
    
    //public static StoredNode deserialize(byte[] data, int start, int len);
    //IStoredNode deserialize(); //TODO perhaps use package protected method?
    
    //</editor-fold>
    
    /**
     * Set the Document that this node belongs to
     * 
     * Counterpart to @see org.exist.dom.INode#getOwnerDocument()
     * 
     * @param doc The document that this node belongs to
     */
    public void setOwnerDocument(DocumentImpl doc);
    
    
    //<editor-fold desc="temp">
    
    //TODO see StoredNode.getParentStoredNode and StoredNode.getParentNode, should be able to remove in favour of getParentNode() in future.
    public IStoredNode getParentStoredNode();
    //</editor-fold>
    
    
    /**
     * Returns a count of the number of children
     * 
     */
    public int getChildCount(); //TODO also available in memtree.ElementImpl - consider moving to org.exist.dom.INode (also this is only really used for ElementImpl and DocumentImpl)

    /**
     * Returns true if the node was modified recently and nodes
     * were inserted at the start or in the middle of its children.
     *
     * @return true when node the node is 'dirty'
     */
    //public boolean isDirty();
    
    //TODO can we not detect this?
    /**
     * Set the node to dirty to indicated
     * that nodes were inserted at the start
     * or in the middle of its children.
     */
    public void setDirty(boolean dirty);
            
    
    public NodePath getPath();
    public NodePath getPath(NodePath parentPath); //TODO seems to be ElementImpl specific see StoredNode
    
    //TODO clean this up
    /**
     * @see org.exist.dom.persistent.StoredNode#reset()
     * this seems to do two things
     * clear the state, and then return the object
     * to NodePool - all a bit of a mess really!
     * 
     * org.exist.Indexer seems to borrow and return to the pool
     * org.exist.memtree.DOMIndexer only seems to borrow nodes
     * org.exist.serializers.NativeSerializer only seems to return nodes
     * org.exist.dom.persistent.*Impl#deserialize(...) seem to have support for pooling
     *   yet this is set to false in the invoking code!
     */
    public void release();
}
