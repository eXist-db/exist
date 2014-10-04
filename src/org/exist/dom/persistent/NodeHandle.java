package org.exist.dom.persistent;

import org.exist.dom.INodeHandle;
import org.exist.numbering.NodeId;

public interface NodeHandle extends INodeHandle<DocumentImpl> {

    public void setNodeId(NodeId dln);
    
    public long getInternalAddress();
    public void setInternalAddress(long internalAddress);
}
