package org.exist.storage.statistics;

import org.exist.dom.QName;
import org.exist.dom.SymbolTable;
import org.exist.storage.ElementValue;
import org.exist.storage.NodePath;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects statistics about the distribution of elements in a document or
 * even the entire database. The class creates a graph structure which describes
 * all possible element paths and their frequency. For example, for a TEI document, a typical
 * path could be:
 *
 * <pre>TEI[44,63330] -> text[44,62757] -> body[44,44206] -> div[300,5584] -> p[5336,820]</pre>
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

    public DataGuide() {
    }

    public int getSize() {
        return root.getSize();
    }
    
    /**
     * Add the given node path (a path like /root/childA/childB) to the data guide.
     * The frequency for the target element (i.e. the last component in the path)
     * is incremented by one.
     * 
     * @param path
     * @return
     */
    public NodeStats add(NodePath path) {
        return add(path, null);
    }

    /**
     * Add the given node path using the frequency and size information
     * given in the second argument. Used to merge two DataGuides.
     *
     * @param path
     * @param mergeWith
     * @return
     */
    protected NodeStats add(NodePath path, NodeStats mergeWith) {
        NodeStats current = root;
        for (int i = 0; i < path.length(); i++) {
            QName qn = path.getComponent(i);
            if (qn.getNameType() != ElementValue.ELEMENT) {
                return null;
            }
            current = current.addChild(qn);
        }
        if (mergeWith != null) {
            current.mergeStats(mergeWith);
        } else
            current.addOccurrence();
        return current;
    }

    /**
     * Merge paths and statistics from this instance into the
     * other instance.
     *
     * @param other
     * @return the other instance containing the merged graphs
     */
    public DataGuide mergeInto(DataGuide other) {
        root.mergeInto(other, new NodePath());
        return other;
    }

    public String toString() {
        List paths = new ArrayList();
        root.dump(new StringBuffer(), paths);

        StringBuffer buf = new StringBuffer();
        for (int i = 0; i < paths.size(); i++) {
            buf.append(paths.get(i));
            buf.append('\n');
        }
        return buf.toString();
    }

    public void write(FileChannel fc, SymbolTable symbols) throws IOException {
        int nodeCount = root.getSize();
        System.out.println("childCount = " + nodeCount);
        ByteBuffer buffer = ByteBuffer.allocate(nodeCount * BYTES_PER_NODE + 4);
        root.write(buffer, symbols);
        buffer.flip();
        fc.write(buffer);
    }

    public void read(FileChannel fc, SymbolTable symbols) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate((int) fc.size());
        fc.read(buffer);
        buffer.flip();
        root.read(buffer, symbols);
    }

    private static class NodeStatsRoot extends NodeStats {

        private NodeStatsRoot() {
            super(null);
        }

        protected void write(ByteBuffer buffer, SymbolTable symbols) {
            if (children == null)
                buffer.putInt(0);
            else {
                buffer.putInt(children.length);
                for (int i = 0; i < children.length; i++) {
                    children[i].write(buffer, symbols);
                }
            }
        }

        protected void read(ByteBuffer buffer, SymbolTable symbols) {
            int childCount = buffer.getInt();
            if (childCount > 0) {
                children = new NodeStats[childCount];
                for (int i = 0; i < childCount; i++) {
                    children[i] = new NodeStats(null);
                    children[i].read(buffer, symbols);
                }
            }
        }
    }

    private static NodePath createPath(String[] tags) {
        NodePath p = new NodePath();
        for (int i = 0; i < tags.length; i++) {
            String tag = tags[i];
            p.addComponent(new QName(tag, "", ""));
        }
        return p;
    }

    public static void main(String[] args) {
        DataGuide guide = new DataGuide();
        guide.add(createPath(new String[] { "root", "body", "head" }));
        guide.add(createPath(new String[] { "root", "body", "section" }));
        guide.add(createPath(new String[] { "root", "body", "section", "head" }));
        guide.add(createPath(new String[] { "root", "body", "section", "p" }));
        guide.add(createPath(new String[] { "root", "body", "section", "p" }));
        guide.add(createPath(new String[] { "root", "body", "section", "hi" }));
        guide.add(createPath(new String[] { "root", "body", "section", "hi", "hi" }));
        guide.add(createPath(new String[] { "root", "body", "backmatter" }));

        System.out.println(guide.toString());

        DataGuide guide2 = new DataGuide();
        guide2.add(createPath(new String[] { "root", "body", "head", "hi" }));
        guide2.add(createPath(new String[] { "root", "body", "section", "p" }));

        guide.mergeInto(guide2);
        System.out.println(guide2.toString());
    }
}