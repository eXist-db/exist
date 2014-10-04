package org.exist.indexing;

import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.IStoredNode;
import org.exist.dom.persistent.TextImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.dom.INodeIterator;
import org.exist.storage.txn.Txn;
import org.w3c.dom.Node;

/**
 * Various utility methods to be used by Index implementations.
 */
public class IndexUtils {

    public static void scanNode(DBBroker broker, Txn transaction, IStoredNode node, StreamListener listener) {
        final INodeIterator iterator = broker.getNodeIterator(node);
        iterator.next();
        final NodePath path = node.getPath();
        scanNode(transaction, iterator, node, listener, path);
    }

    private static void scanNode(Txn transaction, INodeIterator iterator,
            IStoredNode node, StreamListener listener, NodePath currentPath) {
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
            if (listener != null) {
                listener.startElement(transaction, (ElementImpl) node, currentPath);
            }
            if (node.hasChildNodes()) {
                final int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final IStoredNode child = iterator.next();
                    if (child.getNodeType() == Node.ELEMENT_NODE)
                        {currentPath.addComponent(child.getQName());}
                    scanNode(transaction, iterator, child, listener, currentPath);
                    if (child.getNodeType() == Node.ELEMENT_NODE)
                        {currentPath.removeLastComponent();}
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
