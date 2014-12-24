package org.exist.versioning;

import org.exist.numbering.NodeId;
import org.exist.dom.QName;

public class DiffNode {

    public final static int UNCHANGED = 0;
    public final static int INSERTED = 1;
    public final static int APPENDED = 2;
    public final static int DELETED = 3;

    protected int status = UNCHANGED;

    protected NodeId nodeId;
    protected int nodeType;
    protected String value = null;
    protected QName qname = null;
    
    public DiffNode(NodeId nodeId, int nodeType, String value) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.value = value;
    }

    public DiffNode(NodeId nodeId, int nodeType, QName qname) {
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.qname = qname;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public boolean equals(Object obj) {
        DiffNode other = (DiffNode) obj;
        if (nodeType != other.nodeType)
            return false;
        if (qname != null)
            return qname.equals(other.qname);
        else
            return value.equals(other.value);
    }

    public int hashCode() {
        if (qname == null)
            return (value.hashCode() << 1) + nodeType;
        else
            return (qname.hashCode() << 1) + nodeType;
    }

    public String toString() {
        if (qname == null)
            return nodeType + " " + nodeId.toString() + " " + value;
        else
            return nodeType + " " + nodeId.toString() + " " + qname;
    }
}