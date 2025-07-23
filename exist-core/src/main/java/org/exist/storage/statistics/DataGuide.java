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
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects statistics about the distribution of elements in a document or
 * even the entire database. The class creates a graph structure which describes
 * all possible element paths and their frequency. For example, for a TEI document, a typical
 * path could be:
 *
 * <pre>TEI[44,63330] -&gt; text[44,62757] -gt; body[44,44206] -gt; div[300,5584] -gt; p[5336,820]</pre>
 *
 * which means there are 44 TEI, text and body elements in the db with 300 div children and
 * 5336 paragraphs below them. The second number indicates the size of the largest element,
 * expressed as the number of descendant elements below the node. The largest p node in this
 * distribution has 820 elements below it.
 */
public class DataGuide {

    private final static int BYTES_PER_NODE = 16;

    // the (virtual) root of the tree whose name will always be null.
    private NodeStats root = new NodeStatsRoot();

    public int getSize() {
        return root.getSize();
    }
    
    /**
     * Add the given node path (a path like /root/childA/childB) to the data guide.
     * The frequency for the target element (i.e. the last component in the path)
     * is incremented by one.
     *
     * @param path the node path
     *
     * @return the node statistics
     */
    public NodeStats add(NodePath path) {
        return add(path, null);
    }

    /**
     * Add the given node path using the frequency and size information
     * given in the second argument. Used to merge two DataGuides.
     *
     * @param path the node path
     * @param mergeWith the existing node statistics
     *
     * @return the node statistics
     */
    protected NodeStats add(NodePath path, NodeStats mergeWith) {
        NodeStats current = root;
        for (int i = 0; i < path.length(); i++) {
            final QName qn = path.getComponent(i);
            if (qn.getNameType() != ElementValue.ELEMENT) {
                return null;
            }
            current = current.addChild(qn);
        }
        if (mergeWith != null) {
            current.mergeStats(mergeWith);
        } else
            {current.addOccurrence();}
        return current;
    }

    /**
     * Merge paths and statistics from this instance into the
     * other instance.
     *
     * @param other the other data guide
     * @return the other instance containing the merged graphs
     */
    public DataGuide mergeInto(DataGuide other) {
        root.mergeInto(other, new NodePath());
        return other;
    }

    public int getMaxParentDepth(QName qname) {
        final NodeStats temp = new NodeStats(qname);
        root.getMaxParentDepth(qname, temp);
        return temp.getMaxDepth();
    }

    public String toString() {
        final List<StringBuilder> paths = new ArrayList<>();
        root.dump(new StringBuilder(), paths);

        final StringBuilder buf = new StringBuilder();
        for (StringBuilder path : paths) {
            buf.append(path);
            buf.append('\n');
        }
        return buf.toString();
    }

    public void toSAX(ContentHandler handler) throws SAXException {
        root.toSAX(handler);
    }

    public void write(SeekableByteChannel chan, SymbolTable symbols) throws IOException {
        final int nodeCount = root.getSize();
        final ByteBuffer buffer = ByteBuffer.allocate(nodeCount * BYTES_PER_NODE + 4);
        root.write(buffer, symbols);
        ((java.nio.Buffer) buffer).flip();
        chan.write(buffer);
    }

    public void read(SeekableByteChannel chan, SymbolTable symbols) throws IOException {
        final ByteBuffer buffer = ByteBuffer.allocate((int) chan.size());
        chan.read(buffer);
        ((java.nio.Buffer) buffer).flip();
        root.read(buffer, symbols);
    }

    private static class NodeStatsRoot extends NodeStats {

        private NodeStatsRoot() {
            super(null);
        }

        protected void write(ByteBuffer buffer, SymbolTable symbols) {
            if (children == null)
                {buffer.putInt(0);}
            else {
                buffer.putInt(children.length);
                for (NodeStats child : children) {
                    child.write(buffer, symbols);
                }
            }
        }

        protected void read(ByteBuffer buffer, SymbolTable symbols) {
            final int childCount = buffer.getInt();
            if (childCount > 0) {
                children = new NodeStats[childCount];
                for (int i = 0; i < childCount; i++) {
                    children[i] = new NodeStats(null);
                    children[i].read(buffer, symbols);
                }
            }
        }

        public void toSAX(ContentHandler handler) throws SAXException {
            handler.startElement(Namespaces.EXIST_NS, "distribution", "distribution", new AttributesImpl());
            if (children != null) {
                for (NodeStats child : children) {
                    child.toSAX(handler);
                }
            }
            handler.endElement(Namespaces.EXIST_NS, "distribution", "distribution");
        }
    }
}
