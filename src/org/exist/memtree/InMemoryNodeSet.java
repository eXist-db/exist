package org.exist.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 */
public class InMemoryNodeSet extends ValueSequence {

    public final static InMemoryNodeSet EMPTY = new InMemoryNodeSet(0);
    
    public InMemoryNodeSet() {
        super();
    }

    public InMemoryNodeSet(int initialSize) {
        super(initialSize);
    }

    public InMemoryNodeSet(Sequence otherSequence) throws XPathException {
        super(otherSequence);
        Set docs = new HashSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            docs.add(node.getOwnerDocument());
        }
        for (Iterator i = docs.iterator(); i.hasNext(); ) {
            DocumentImpl doc = (DocumentImpl) i.next();
            doc.expand();
        }
    }

    public Sequence getAttributes(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectAttributes(test, nodes);
        }
        return nodes;
    }

    public Sequence getDescendantAttributes(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectDescendantAttributes(test, nodes);
        }
        return nodes;
    }

    public Sequence getChildren(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectChildren(test, nodes);
        }
        return nodes;
    }

    public Sequence getChildrenForParent(NodeImpl parent) {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            if (node.getNodeId().isChildOf(parent.getNodeId()))
                nodes.add(node);
        }
        return nodes;
    }

    public Sequence getDescendants(boolean includeSelf, NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectDescendants(includeSelf, test, nodes);
        }
        return nodes;
    }

    public Sequence getAncestors(boolean includeSelf, NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectAncestors(includeSelf, test, nodes);
        }
        return nodes;
    }

    public Sequence getParents(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            NodeImpl parent = (NodeImpl) node.selectParentNode();
            if (parent != null && test.matches(parent))
                nodes.add(parent);
        }
        return nodes;
    }

    public Sequence getSelf(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            if ((test.getType() == Type.NODE && node.getNodeType() == Node.ATTRIBUTE_NODE) ||
                test.matches(node))
                nodes.add(node);
        }
        return nodes;
    }

    public Sequence getPrecedingSiblings(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectPrecedingSiblings(test, nodes);
        }
        return nodes;
    }

    public Sequence getFollowingSiblings(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectFollowingSiblings(test, nodes);
        }
        return nodes;
    }
}
