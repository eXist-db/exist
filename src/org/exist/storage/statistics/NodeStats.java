package org.exist.storage.statistics;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.storage.NodePath;
import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.nio.ByteBuffer;
import java.util.List;

/**
 * Collects statistics for a single node in the data guide.
 */
class NodeStats {

    private QName qname;
    private int nodeCount = 0;
    private int maxDepth = 0;

    transient private int depth = 0;

    protected NodeStats parent = null;
    protected NodeStats[] children = null;

    protected NodeStats(QName qname) {
        this(null, qname);
    }

    protected NodeStats(NodeStats parent, QName qname) {
        this.parent = parent;
        this.qname = qname;
    }

    public void incDepth() {
        this.depth++;
    }

    public void updateMaxDepth() {
        if (depth > maxDepth)
            maxDepth = depth;
        depth = 0;
    }

    public int getMaxDepth() {
        return maxDepth;
    }
    
    protected void addOccurrence() {
        nodeCount++;
    }

    protected NodeStats addChild(QName qn) {
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                NodeStats child = children[i];
                if (child.qname.equalsSimple(qn)) {
                    return child;
                }
            }
        }
        if (children == null) {
            children = new NodeStats[1];
        } else {
            NodeStats[] tc = new NodeStats[children.length + 1];
            System.arraycopy(children, 0, tc, 0, children.length);
            children = tc;
        }
        children[children.length - 1] = new NodeStats(this, qn);
        return children[children.length - 1];
    }

    protected void mergeInto(DataGuide other, NodePath currentPath) {
        NodePath newPath;
        if (qname == null)
            newPath = currentPath;
        else {
            newPath = new NodePath(currentPath);
            newPath.addComponent(qname);
            other.add(newPath, this);
        }

        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                NodeStats child = children[i];
                child.mergeInto(other, newPath);
            }
        }
    }

    protected void mergeStats(NodeStats other) {
        nodeCount += other.nodeCount;
        if (other.maxDepth > maxDepth)
            maxDepth = other.maxDepth;
    }

    protected int getSize() {
        int s = qname == null ? 0 : 1;
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                s += children[i].getSize();
            }
        }
        return s;
    }

    protected void getMaxParentDepth(QName name, NodeStats max) {
        if (parent != null && qname != null && qname.equalsSimple(name)) {
            max.maxDepth = Math.max(parent.maxDepth, max.maxDepth);
        }
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                children[i].getMaxParentDepth(name, max);
            }
        }
    }

    protected void write(ByteBuffer buffer, SymbolTable symbols) {
        buffer.putShort(symbols.getNSSymbol(qname.getNamespaceURI()));
        buffer.putShort(symbols.getSymbol(qname.getLocalName()));
        buffer.putInt(nodeCount);
        buffer.putInt(maxDepth);

        buffer.putInt(children == null ? 0: children.length);
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                children[i].write(buffer, symbols);
            }
        }
    }

    protected void read(ByteBuffer buffer, SymbolTable symbols) {
        short nsid = buffer.getShort();
        short localid = buffer.getShort();
        String namespaceURI = symbols.getNamespace(nsid);
        String localName = symbols.getName(localid);
        qname = symbols.getQName(Node.ELEMENT_NODE, namespaceURI,
            localName, "");
        nodeCount = buffer.getInt();
        maxDepth = buffer.getInt();

        int childCount = buffer.getInt();
        if (childCount > 0) {
            children = new NodeStats[childCount];
            for (int i = 0; i < childCount; i++) {
                children[i] = new NodeStats(this, null);
                children[i].read(buffer, symbols);
            }
        }
    }

    protected void dump(StringBuilder currentPath, List paths) {
        StringBuilder newPath;
        if (qname == null)
            newPath = currentPath;
        else {
            newPath = new StringBuilder(currentPath);
            if (newPath.length() > 0)
                newPath.append(" -> ");
            newPath.append(qname);
            newPath.append('[').append(nodeCount).append(',');
            newPath.append(maxDepth).append(']');
        }
        paths.add(newPath);
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                NodeStats child = children[i];
                child.dump(newPath, paths);
            }
        }
    }

    public void toSAX(ContentHandler handler) throws SAXException {
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "name", "name", "CDATA", qname.getLocalName());
        attribs.addAttribute("", "namespace", "namespace", "CDATA", qname.getNamespaceURI());
        attribs.addAttribute("", "node-count", "node-count", "CDATA", Integer.toString(nodeCount));
        attribs.addAttribute("", "max-depth", "max-depth", "CDATA", Integer.toString(maxDepth));
        handler.startElement(Namespaces.EXIST_NS, "node", "node", attribs);
        if (children != null) {
            for (int i = 0; i < children.length; i++) {
                children[i].toSAX(handler);
            }
        }
        handler.endElement(Namespaces.EXIST_NS, "node", "node");
    }
}
