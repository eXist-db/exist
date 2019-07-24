/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.xquery.XPathException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;

/**
 * Represents a node value. May either be an in-memory node
 * or a persistent node.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface NodeValue extends Item, Sequence {

    /**
     * Node is a constructed in-memory node
     */
    int IN_MEMORY_NODE = 0;

    /**
     * Node is a persistent, i.e. stored in the database
     */
    int PERSISTENT_NODE = 1;

    /**
     * Returns true if this node has the same identity as another
     * node. Used to implement "is" and "isnot" comparisons.
     *
     * @param other the other node to compare with
     * @return true if the nodes have the same identity
     * @throws XPathException in case of a dynamic error
     */
    boolean equals(NodeValue other) throws XPathException;

    /**
     * Returns true if this node comes before another node in
     * document order.
     *
     * @param other the other node to compare with
     * @param isPreceding if true, return false for ancestors of the current node
     * @return true if this nodes precedes the other node
     * @throws XPathException in case of dynamic error
     */
    boolean before(NodeValue other, boolean isPreceding) throws XPathException;

    /**
     * Returns true if this node comes after another node in
     * document order.
     *
     * @param other the other node to compare with
     * @param isFollowing if true, return false for descendants of the current node
     * @return true if this nodes precedes the other node
     * @throws XPathException in case of dynamic error
     */
    boolean after(NodeValue other, boolean isFollowing) throws XPathException;

    /**
     * Returns the implementation-type of this node, i.e. either
     * {@link #IN_MEMORY_NODE} or {@link #PERSISTENT_NODE}.
     *
     * @return {@link #IN_MEMORY_NODE} or {@link #PERSISTENT_NODE}
     */
    int getImplementationType();

    void addContextNode(int contextId, NodeValue node);

    QName getQName();

    /**
     * Retrieve the actual node. This operation is <strong>expensive</strong>.
     *
     * @return The actual node.
     */
    Node getNode();

    Document getOwnerDocument();

    NodeId getNodeId();
}
