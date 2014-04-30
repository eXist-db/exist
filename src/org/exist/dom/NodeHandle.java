package org.exist.dom;

import org.exist.numbering.NodeId;
import org.w3c.dom.Document;

public interface NodeHandle {

    public NodeId getNodeId();

    public void setNodeId(NodeId dln);

    public long getInternalAddress();

    public void setInternalAddress(long internalAddress);

    public short getNodeType();

    public Document getOwnerDocument();

    public DocumentImpl getDocument();

}