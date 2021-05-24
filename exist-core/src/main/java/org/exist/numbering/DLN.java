/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.numbering;

import java.io.IOException;
import org.exist.security.MessageDigester;
import org.exist.storage.io.VariableByteInput;
import org.exist.storage.io.VariableByteOutputStream;

/**
 * Represents a node id in the form of a dynamic level number (DLN). DLN's are
 * hierarchical ids, which borrow from Dewey's decimal classification. Examples for
 * node ids: 1, 1.1, 1.2, 1.2.1, 1.2.2, 1.3. In this case, 1 represents the root node, 1.1 is
 * the first node on the second level, 1.2 the second, and so on.
 * 
 * To support efficient insertion of new nodes between existing nodes, we use the
 * concept of sublevel ids. Between two nodes 1.1 and 1.2, a new node can be inserted
 * as 1.1/1, where the / is the sublevel separator. The / does not start a new level. 1.1 and 
 * 1.1/1 are thus on the same level of the tree.
 * 
 * In the binary encoding, the '.' is represented by a 0-bit while '/' is written as a 1-bit.
 */
public class DLN extends DLNBase implements NodeId {

    /**
     * Constructs a new DLN with a single id with value 1.
     *
     */
    public DLN() {
        this(1);
    }

    /**
     * Constructs a new DLN by parsing the string argument.
     * In the string, levels are separated by a '.', sublevels by
     * a '/'. For example, '1.2/1' or '1.2/1.2' are valid ids.
     * 
     * @param s string represenation of a DLN
     */
    public DLN(final String s) {
        bits = new byte[1];
        final StringBuilder buf = new StringBuilder(16);
        boolean subValue = false;
        for(int p = 0; p < s.length(); p++) {
            final char ch = s.charAt(p);
            if(ch == '.' || ch == '/') {
                addLevelId(Integer.parseInt(buf.toString()), subValue);
                subValue = ch == '/';
                buf.setLength(0);
            } else {
                buf.append(ch);
            }
        }
        if(buf.length() > 0) {
            addLevelId(Integer.parseInt(buf.toString()), subValue);
        }
    }

    /**
     * Constructs a new DLN, using the passed id as its
     * single level value.
     * 
     * @param id the value for the initial first level of this DLN
     */
    public DLN(final int id) {
        bits = new byte[1];
        addLevelId(id, false);
    }

    /**
     * Constructs a new DLN by copying the data of the
     * passed DLN.
     * 
     * @param other the DLN to copy data from
     */
    public DLN(final DLN other) {
        super(other);
    }

    /**
     * Reads a DLN from the given byte[].
     * 
     * @param units number of bits to read
     * @param data the byte[] to read from
     * @param startOffset the start offset to start reading at
     */
    public DLN(final int units, final byte[] data, final int startOffset) {
        super(units, data, startOffset);
    }

    /**
     * Reads a DLN from the given {@link VariableByteInput} stream.
     * 
     * @see #write(VariableByteOutputStream)
     * @param bitCnt total number of bits to read
     * @param is the input stream to read from
     * @throws IOException in case of an error reading the DLN
     */
    public DLN(final short bitCnt, final VariableByteInput is) throws IOException {
        super(bitCnt, is);
    }

    public DLN(final byte prefixLen, final DLN previous, final short bitCnt, final VariableByteInput is) throws IOException {
        super(prefixLen, previous, bitCnt, is);
    }

    /**
     * Create a new DLN by copying nbits bits from the given 
     * byte[].
     * 
     * @param data the byte[] to read bits from
     * @param nbits number of bits to read
     */
    protected DLN(final byte[] data, final int nbits) {
        super(data, nbits);
    }

    /**
     * Returns a new DLN representing the first child
     * node of this node.
     *
     * @return new child node id
     */
    @Override
    public NodeId newChild() {
        final DLN child = new DLN(this);
        child.addLevelId(1, false);
        return child;
    }

    /**
     * Returns a new DLN representing the next following
     * sibling of this node.
     *
     * @return new sibling node id.
     */
    @Override
    public NodeId nextSibling() {
        final DLN sibling = new DLN(this);
        sibling.incrementLevelId();
        return sibling;
    }

    @Override
    public NodeId precedingSibling() {
        final DLN sibling = new DLN(this);
        sibling.decrementLevelId();
        return sibling;
    }

    @Override
    public NodeId getChild(final int child) {
        final DLN nodeId = new DLN(this);
        nodeId.addLevelId(child, false);
        return nodeId;
    }

    @Override
    public NodeId insertNode(final NodeId right) {
        final DLN rightNode = (DLN) right;
        if (right == null) {
            return nextSibling();
        }
        final int lastLeft = lastLevelOffset();
        final int lastRight = rightNode.lastLevelOffset();
        final int lenLeft = getSubLevelCount(lastLeft);
        final int lenRight = rightNode.getSubLevelCount(lastRight);
        final DLN newNode;
        if(lenLeft > lenRight) {
            newNode = new DLN(this);
            newNode.incrementLevelId();
        } else if(lenLeft < lenRight) {
            newNode = (DLN) rightNode.insertBefore(); 
        } else {
            newNode = new DLN(this);
            newNode.addLevelId(1, true);
        }
        return newNode;
    }

    @Override
    public NodeId insertBefore() {
        final int lastPos = lastFieldPosition();
        final int lastId = getLevelId(lastPos);
        final DLN newNode = new DLN(this);
        //System.out.println("insertBefore: " + newNode.toString() + " = " + newNode.bitIndex);
        if (lastId == 1) {
            newNode.setLevelId(lastPos, 0);
            newNode.addLevelId(35, true);
        } else {
            newNode.setLevelId(lastPos, lastId - 1);
            newNode.compact();
            //System.out.println("newNode: " + newNode.toString() + " = " + newNode.bitIndex + "; last = " + lastPos);
        }
        return newNode;
    }

    @Override
    public NodeId append(final NodeId otherId) {
        final DLN other = (DLN) otherId;
        final DLN newId = new DLN(this);
        int offset = 0;
        while(offset <= other.bitIndex) {
            boolean subLevel = false;
            if (offset > 0) {
                subLevel = ((other.bits[offset >> UNIT_SHIFT] & (1 << ((7 - offset++) & 7))) != 0);
            }
            final int id = other.getLevelId(offset);
            newId.addLevelId(id, subLevel);
            offset += DLNBase.getUnitsRequired(id) * BITS_PER_UNIT;
        }
        return newId;
    }

    /**
     * Returns a new DLN representing the parent of the
     * current node. If the current node is the root element
     * of the document, the method returns 
     * {@link NodeId#DOCUMENT_NODE}. If the current node
     * is the document node, null is returned.
     * 
     * @see NodeId#getParentId()
     */
    @Override
    public NodeId getParentId() {
        if(this == DOCUMENT_NODE) {
            return null;
        }
        
        final int last = lastLevelOffset();
        if (last == 0) {
            return DOCUMENT_NODE;
        }
        
        return new DLN(bits, last - 1);
    }

    @Override
    public boolean isDescendantOf(final NodeId ancestor) {
        final DLN other = (DLN) ancestor;
        return startsWith(other) && bitIndex > other.bitIndex
            && isLevelSeparator(other.bitIndex + 1);
    }

    @Override
    public boolean isDescendantOrSelfOf(final NodeId other) {
        final DLN ancestor = (DLN) other;
        return startsWith(ancestor) &&
            (bitIndex == ancestor.bitIndex || isLevelSeparator((ancestor).bitIndex + 1));
    }

    @Override
    public boolean isChildOf(final NodeId parent) {
        final DLN other = (DLN) parent;
        if(!startsWith(other)) {
            return false;
        }
        final int levels = getLevelCount(other.bitIndex + 2);
        return levels == 1;
    }

    @Override
    public int computeRelation(final NodeId ancestor) {
        final DLN other = (DLN) ancestor;
        if (other == NodeId.DOCUMENT_NODE) {
            return getLevelCount(0) == 1 ? IS_CHILD : IS_DESCENDANT;
        }
        
        if (startsWith(other)) {
            if (bitIndex == other.bitIndex) {
                return IS_SELF;
            }
            if (bitIndex > other.bitIndex && isLevelSeparator(other.bitIndex + 1)) {
                if (getLevelCount(other.bitIndex + 2) == 1) {
                    return IS_CHILD;
                }
                return IS_DESCENDANT;
            }
        }
        return -1;
    }

    @Override
    public boolean isSiblingOf(final NodeId sibling) {
        final NodeId parent = getParentId();
        return sibling.isChildOf(parent);
    }

    /**
     * Returns the level within the document tree at which
     * this node occurs.
     */
    @Override
    public int getTreeLevel() {
        return getLevelCount(0);
    }

    @Override
    public int compareTo(final NodeId otherId) {
        if(otherId == null) {
            return 1;
        }
        final DLN other = (DLN) otherId;
        final int a1len = bits.length;
        final int a2len = other.bits.length;
        final int limit = a1len <= a2len ? a1len : a2len;
        final byte[] obits = other.bits;
        
        for(int i = 0; i < limit; i++) {
            final byte b1 = bits[i];
            final byte b2 = obits[i];
            if(b1 != b2) {
                return (b1 & 0xFF) - (b2 & 0xFF);
            }
        }
        return (a1len - a2len);
    }

    @Override
    public boolean after(final NodeId other, final boolean isFollowing) {
        if (compareTo(other) > 0) {
            if (isFollowing) {
                return !isDescendantOf(other);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean before(final NodeId other, final boolean isPreceding) {
        if (compareTo(other) < 0) {
            if (isPreceding) {
                return !other.isDescendantOf(this);
            }
            return true;
        }
        return false;
    }

    /**
     * Write the node id to a {@link VariableByteOutputStream}.
     *
     * @param os the output stream to write to
     * @throws IOException in case of write error
     */
    @Override
    public void write(final VariableByteOutputStream os) throws IOException {
        os.writeShort((short) units());
        os.write(bits, 0, bits.length);
    }

    @Override
    public NodeId write(final NodeId prevId, final VariableByteOutputStream os) throws IOException {
        int i = 0;
        if(prevId != null) {
            final DLN previous = (DLN) prevId;
            final int len = Math.min(bits.length, previous.bits.length);
            for( ; i < len; i++) {
                final byte b = bits[i];
                if (b != previous.bits[i]) {
                    break;
                }
            }
        }
        os.writeByte((byte) i);
        os.writeShort((short) units());
        os.write(bits, i, bits.length - i);
        return this;
    }
}
