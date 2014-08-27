/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.indexing;

import org.exist.dom.AttrImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.StoredNode;
import org.exist.dom.TextImpl;
import org.exist.storage.DBBroker;
import org.exist.storage.NodePath;
import org.exist.storage.txn.Txn;
import org.w3c.dom.Node;

import java.util.Iterator;

/**
 * Various utility methods to be used by Index implementations.
 */
public class IndexUtils {

    public static void scanNode(DBBroker broker, Txn transaction, StoredNode node, StreamListener listener) {
        final Iterator<StoredNode> iterator = broker.getNodeIterator(node);
        iterator.next();
        final NodePath path = node.getPath();
        scanNode(transaction, iterator, node, listener, path);
    }

    private static void scanNode(Txn transaction, Iterator<StoredNode> iterator,
            StoredNode node, StreamListener listener, NodePath currentPath) {
        switch (node.getNodeType()) {
        case Node.ELEMENT_NODE:
            if (listener != null) {
                listener.startElement(transaction, (ElementImpl) node, currentPath);
            }
            if (node.hasChildNodes()) {
                final int childCount = node.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final StoredNode child = iterator.next();
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
