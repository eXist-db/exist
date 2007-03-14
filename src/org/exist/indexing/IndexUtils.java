package org.exist.indexing;

import java.util.Iterator;

import org.exist.dom.AttrImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.w3c.dom.Node;

/**
 * Various utility methods to be used by Index implementations.
 */
public class IndexUtils {

    public static void scanNode(Txn transaction, StoredNode node, StreamListener listener) {
        Iterator iterator = node.getDocument().getBroker().getNodeIterator(node);
        iterator.next();
        NodePath path = node.getPath();
        scanNode(transaction, iterator, node, listener, path);
    }

    private static void scanNode(Txn transaction, Iterator iterator, StoredNode node, StreamListener listener,
                                 NodePath currentPath) {
        switch (node.getNodeType()) {
            case Node.ELEMENT_NODE:
                if (listener != null) {
                    listener.startElement(transaction, (ElementImpl) node, currentPath);
                }
                if (node.hasChildNodes()) {
                    int childCount = node.getChildCount();
                    for (int i = 0; i < childCount; i++) {
                    	StoredNode child = (StoredNode) iterator.next();
                        if (child.getNodeType() == Node.ELEMENT_NODE)
                            currentPath.addComponent(child.getQName());
                        scanNode(transaction, iterator, child, listener, currentPath);
                        if (child.getNodeType() == Node.ELEMENT_NODE)
                            currentPath.removeLastComponent();
                    }
                }
                if (listener != null) {
                    listener.endElement(transaction, (ElementImpl) node, currentPath);
                }
                break;
            case Node.TEXT_NODE :
                if (listener != null) {
                    listener.characters(transaction, (TextImpl) node, currentPath);
                }
                break;
            case Node.ATTRIBUTE_NODE :
                if (listener != null) {
                    listener.attribute(transaction, (AttrImpl) node, currentPath);
                }
                break;
        }
    }
}
