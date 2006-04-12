/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 The eXist Project
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
 *
 *  $Id$
 */
package org.exist.numbering;

import java.io.IOException;

import org.exist.storage.io.VariableByteOutputStream;

/**
 * Represents the internal id of a node within eXist. Basically, all
 * stored nodes in eXist need to have an id that implements this
 * interface. The id will be assigned according to used numbering
 * scheme. From a given id, we can determine the relationship
 * of the node it represents to any other node in the same document.
 */
public interface NodeId extends Comparable {

    /**
     * Static field representing the document node.
     */
    public final static NodeId DOCUMENT_NODE = new DLN(0);

    /**
     * Returns a new NodeId representing the first child
     * node of this node. The returned id can be used
     * for creating new nodes. The actual id of the first
     * node might be different, depending on the
     * implementation.
     *
     * @return new child node id
     */
    NodeId newChild();

    /**
     * Returns a new NodeId representing the next following
     * sibling of this node. The returned id can be used
     * to create new sibling nodes. The actual id of the
     * next sibling might be different, depending on the
     * implementation.
     *
     * @return new sibling node id.
     */
    NodeId nextSibling();

    /**
     * Returns a new NodeId representing the parent
     * of the current node. If the parent is the document,
     * the constant {@link #DOCUMENT_NODE} will be returned.
     * For the document itself, the parent id will be null.
     *
     * @return the id of the parent node or null if the current node
     * is the document node.
     */
    NodeId getParentId();

    /**
     * Is the current node id a descendant of the specified node?
     *
     * @param ancestor node id of the potential ancestor
     * @return true if the node id is a descendant of the given node, false otherwise
     */
    boolean isDescendantOf(NodeId ancestor);

    boolean isDescendantOrSelfOf(NodeId ancestor);

    /**
     * Is the current node a child node of the specified parent?
     * 
     * @param parent the parent node
     * @return
     */
    boolean isChildOf(NodeId parent);

    public int isSiblingOf(NodeId sibling);
    
    /**
     * Returns the level within the document tree at which
     * this node occurs.
     *
     * @return
     */
    int getTreeLevel();

    int compareTo(NodeId other);

    boolean equals(NodeId other);
    
    /**
     * Returns the size (in bytes) of this node id. Depends on
     * the concrete implementation.
     *
     * @return
     */
    int size();

    int units();

    /**
     * Serializes the node id to an array of bytes. The first byte is
     * written at offset.
     *
     * @param data the byte array to be filled
     * @param offset offset into the array
     */
    void serialize(byte[] data, int offset);
    
    /**
     * Write the node id to a {@link org.exist.storage.io.VariableByteOutputStream}.
     *
     * @param os
     * @throws java.io.IOException
     */
    void write(VariableByteOutputStream os) throws IOException;
}
