package org.exist.indexing.range;

import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.storage.NodePath;

public class RangeIndexDoc {

    private NodeId nodeId;
    private QName qname;
    private NodePath path;
    private TextCollector collector;
    private RangeIndexConfigElement config;

    public RangeIndexDoc(NodeId nodeId, QName qname, NodePath path, TextCollector collector, RangeIndexConfigElement config) {
        this.nodeId = nodeId;
        this.qname = qname;
        this.path = path;
        this.collector = collector;
        this.config = config;
    }

    public NodeId getNodeId() {
        return nodeId;
    }

    public QName getQName() {
        return qname;
    }

    public NodePath getPath() {
        return path;
    }

    public TextCollector getCollector() {
        return collector;
    }

    public RangeIndexConfigElement getConfig() {
        return config;
    }
}