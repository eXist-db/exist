/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage.statistics;

import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.persistent.SymbolTable;
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
            {maxDepth = depth;}
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
            for (final NodeStats child : children) {
                if (child.qname.equals(qn)) {
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
            {newPath = currentPath;}
        else {
            newPath = new NodePath(currentPath);
            newPath.addComponent(qname);
            other.add(newPath, this);
        }

        if (children != null) {
            for (final NodeStats child : children) {
                child.mergeInto(other, newPath);
            }
        }
    }

    protected void mergeStats(NodeStats other) {
        nodeCount += other.nodeCount;
        if (other.maxDepth > maxDepth)
            {maxDepth = other.maxDepth;}
    }

    protected int getSize() {
        int s = qname == null ? 0 : 1;
        if (children != null) {
            for (NodeStats child : children) {
                s += child.getSize();
            }
        }
        return s;
    }

    protected void getMaxParentDepth(QName name, NodeStats max) {
        if (parent != null && qname != null && qname.equals(name)) {
            max.maxDepth = Math.max(parent.maxDepth, max.maxDepth);
        }
        if (children != null) {
            for (NodeStats child : children) {
                child.getMaxParentDepth(name, max);
            }
        }
    }

    protected void write(ByteBuffer buffer, SymbolTable symbols) {
        buffer.putShort(symbols.getNSSymbol(qname.getNamespaceURI()));
        buffer.putShort(symbols.getSymbol(qname.getLocalPart()));
        buffer.putInt(nodeCount);
        buffer.putInt(maxDepth);

        buffer.putInt(children == null ? 0: children.length);
        if (children != null) {
            for (NodeStats child : children) {
                child.write(buffer, symbols);
            }
        }
    }

    protected void read(ByteBuffer buffer, SymbolTable symbols) {
        final short nsid = buffer.getShort();
        final short localid = buffer.getShort();
        final String namespaceURI = symbols.getNamespace(nsid);
        final String localName = symbols.getName(localid);
        qname = symbols.getQName(Node.ELEMENT_NODE, namespaceURI,
            localName, "");
        nodeCount = buffer.getInt();
        maxDepth = buffer.getInt();

        final int childCount = buffer.getInt();
        if (childCount > 0) {
            children = new NodeStats[childCount];
            for (int i = 0; i < childCount; i++) {
                children[i] = new NodeStats(this, null);
                children[i].read(buffer, symbols);
            }
        }
    }

    protected void dump(StringBuilder currentPath, List<StringBuilder> paths) {
        StringBuilder newPath;
        if (qname == null)
            {newPath = currentPath;}
        else {
            newPath = new StringBuilder(currentPath);
            if (!newPath.isEmpty())
                {newPath.append(" -> ");}
            newPath.append(qname);
            newPath.append('[').append(nodeCount).append(',');
            newPath.append(maxDepth).append(']');
        }
        paths.add(newPath);
        if (children != null) {
            for (final NodeStats child : children) {
                child.dump(newPath, paths);
            }
        }
    }

    public void toSAX(ContentHandler handler) throws SAXException {
        final AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "name", "name", "CDATA", qname.getLocalPart());
        attribs.addAttribute("", "namespace", "namespace", "CDATA", qname.getNamespaceURI());
        attribs.addAttribute("", "node-count", "node-count", "CDATA", Integer.toString(nodeCount));
        attribs.addAttribute("", "max-depth", "max-depth", "CDATA", Integer.toString(maxDepth));
        handler.startElement(Namespaces.EXIST_NS, "node", "node", attribs);
        if (children != null) {
            for (NodeStats child : children) {
                child.toSAX(handler);
            }
        }
        handler.endElement(Namespaces.EXIST_NS, "node", "node");
    }
}
