package org.exist.dom.persistent;

/**
 * Holds a mutable reference to a NodeImpl, used to pass a node by reference.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class NodeImplRef {	

    private IStoredNode node;
    
    public NodeImplRef() {
        this.node = null;
    }
    
    public NodeImplRef(IStoredNode node) {
        this.node = node;
    }
    
    public void setNode(IStoredNode node) {
        this.node = node;
    }    
    
    public IStoredNode getNode() {
        return this.node;
    }
}
