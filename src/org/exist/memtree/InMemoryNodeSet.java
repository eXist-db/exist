package org.exist.memtree;

import org.exist.xquery.NodeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 */
public class InMemoryNodeSet extends ValueSequence {

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

    public Sequence getChildren(NodeTest test) throws XPathException {
        InMemoryNodeSet nodes = new InMemoryNodeSet();
        for (int i = 0; i <= size; i++) {
            NodeImpl node = (NodeImpl) values[i];
            node.selectChildren(test, nodes);
        }
        return nodes;
    }
}
