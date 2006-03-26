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

import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

import java.io.IOException;

/**
 * Represents a node id in the form of a dynamic level number (DLN). DLN's are
 * hierarchical ids, which borrow from Dewey's decimal classification. Examples for
 * node ids: 1, 1.1, 1.2, 1.2.1, 1.2.2, 1.3. In this case, 1 represents the root node, 1.1 is
 * the first node on the second level, 1.2 the second, and so on. 
 */
public class DLN extends DLNBase implements NodeId {

    public DLN() {
        this(1);
    }

    public DLN(int[] id) {
        this(id[0]);
        for (int i = 1; i < id.length; i++)
            addLevelId(id[i]);
    }

    public DLN(int id) {
        bits = new byte[1];
        addLevelId(id);
    }

    public DLN(DLN other) {
        super(other);
    }

    public DLN(int units, byte[] data, int startOffset) {
        super(units, data, startOffset);
    }

    public DLN(VariableByteInput is) throws IOException {
        super(is);
    }

    protected DLN(byte[] data, int nbits) {
        super(data, nbits);
    }

    /**
     * Returns a new DLN representing the first child
     * node of this node.
     *
     * @return new child node id
     */
    public NodeId newChild() {
        DLN child = new DLN(this);
        child.addLevelId(1);
        return child;
    }

    /**
     * Returns a new DLN representing the next following
     * sibling of this node.
     *
     * @return new sibling node id.
     */
    public NodeId nextSibling() {
        DLN sibling = new DLN(this);
        sibling.incrementLevelId();
        return sibling;
    }

    public NodeId getParentId() {
        if (this == DOCUMENT_NODE)
            return null;
        int last = lastLevelOffset();
        if (last == 0)
            return DOCUMENT_NODE;
        return new DLN(bits, last);
    }

    public boolean isDescendantOf(NodeId ancestor) {
    	DLN other = (DLN) ancestor;
    	return startsWith(other) && bitIndex > other.bitIndex;
    }

    public boolean isDescendantOrSelfOf(NodeId ancestor) {
        return startsWith((DLN) ancestor);
    }

    public boolean isChildOf(NodeId parent) {
    	DLN other = (DLN) parent;
    	if(!startsWith(other))
    		return false;
    	int levels = getLevelCount(other.bitIndex + 1);
    	return levels == 1;
    }
    
    /**
     * Returns the level within the document tree at which
     * this node occurs.
     *
     * @return
     */
    public int getTreeLevel() {
        return getLevelCount(0);
    }

    public boolean equals(NodeId other) {
        return super.equals((DLNBase) other);
    }

    public int compareTo(NodeId other) {
        return super.compareTo(other);
    }

    /**
     * Write the node id to a {@link VariableByteOutputStream}.
     *
     * @param os
     * @throws IOException
     */
    public void write(VariableByteOutputStream os) throws IOException {
        os.writeByte((byte) units());
        os.write(bits, 0, bits.length);
    }

    public static void main(String[] args) {
        DLN id = new DLN();
        id.setLevelId(0, 8);
        System.out.println("ID: " + id.toBitString() + " = " + id.getLevelId(0));
        id.setLevelId(0, 0);
        for (int i = 0; i < 100; i++) {
            id.incrementLevelId();
            System.out.println("ID: " + id.toBitString() + " = " + id.getLevelId(0));
        }
        id.addLevelId(0);
        System.out.println("ID: " + id.toBitString() + " = " + id.toString());

        id = new DLN(new int[] {5, 87, 453});
        System.out.println("ID: " + id.toString() + " = " + id.toBitString());

        while (id != null) {
            System.out.println(id.debug());
            id = (DLN) id.getParentId();
        }
    }
}
