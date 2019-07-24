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

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

/**
 * A factory for creating node ids. To support different numbering
 * schemes, NodeId instances should always be created through
 * this interface.
 *
 * The NodeIdFactory for the current database instance can be
 * retrieved from {@link org.exist.storage.BrokerPool#getNodeFactory()}.
 */
public interface NodeIdFactory {

    /**
     * Create a new NodeId, initialized with a default
     * value.
     *
     * @return a new NodeId.
     */
    NodeId createInstance();

    /**
     * Create a new NodeId, initialized with the given
     * base id.
     * 
     * @param id the initial, first level value to initialize the NodeId with
     * @return nodeId a new NodeId instance
     */
    NodeId createInstance(int id);

    /**
     * Read a NodeId from the given input stream.
     *
     * @see NodeId#write(org.exist.storage.io.VariableByteOutputStream)
     *
     * @param is the input stream to read from
     * @return the NodeId read
     * @throws IOException if there's a problem with the underlying input stream
     */
    NodeId createFromStream(VariableByteInput is) throws IOException;

    /**
     * Read a NodeId from the given input stream. Assumes that the node id was
     * stored with prefix-compression, i.e. only the bytes differing from the previous
     * node were written out.
     *
     * @see NodeId#write(NodeId, org.exist.storage.io.VariableByteOutputStream)
     * 
     * @param previous the previous node id read or null if there is none
     * @param is the input stream to read from
     * @return the NodeId read
     * @throws IOException if there's a problem with the underlying input stream
     */
    NodeId createFromStream(NodeId previous, VariableByteInput is) throws IOException;

    /**
     * Read a NodeId from the given byte array. Start to read at
     * startOffset. sizeHint indicates the length of the id in an
     * implementation dependent manner. Some implementations
     * may require sizeHint to be specified, others not.
     *
     * @param sizeHint a hint about the expected length of the id
     * @param data the byte array to read from
     * @param startOffset offset into the byte array
     * @return the NodeId read
     */
    NodeId createFromData(int sizeHint, byte[] data, int startOffset);

    /**
     * Create a NodeId instance from its string representation.
     * 
     * @param string the string representation of the node id as returned
     * by {@link Object#toString()}
     * @return nodeId
     */
    NodeId createFromString(String string);

    /**
     * Returns the number of bytes occupied by the NodeId stored
     * in the byte array at the given startOffset. This method is
     * similar to {@link #createFromData(int, byte[], int)}, but it
     * just returns the number of bytes.
     *
     * @param units the number of units to be read
     * @param data the byte array containing the data
     * @param startOffset offset into the byte array to start reading from
     * @return number of bytes
     */
    int lengthInBytes(int units, byte[] data, int startOffset);

    /**
     * Returns a NodeId representing the document node of a document.
     * Usually, this will be a singleton object.
     *
     * @return the document node id.
     */
    NodeId documentNodeId();

    void writeEndOfDocument(VariableByteOutputStream os);

}