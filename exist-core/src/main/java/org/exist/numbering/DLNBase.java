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
import java.util.Arrays;
import org.exist.storage.io.VariableByteInput;

/**
 * Base class representing a node id in the form of a dynamic level number (DLN).
 * See {@link DLN}. DLNBase handles the efficient binary encoding of node ids.
 *
 * Level values are stored consecutively, using a fixed prefix free encoding. The number of
 * units to be used for encoding a single level value is dynamically adjusted. We start with
 * one unit and use its n - 1 lower bits. If the number exceeds the lower bits, we add another
 * unit and set the highest bit to 1. This process is repeated for larger numbers. As a result,
 * the first 1 bits of a level id indicate the number of fixed-size units used for encoding a level id.
 * We thus don't need separator bits between the units themselves.
 *
 * @author wolf
 *
 */
public class DLNBase {

    /**
     * The default number of bits used per fixed
     * size unit.
     */
    public final static int BITS_PER_UNIT = 4;

    public final static int[] BIT_MASK = new int[8];

    static {
        BIT_MASK[0] = 0x80;
        for (int i = 1; i < 8; i++) {
            final int mask = 1 << (7 - i);
            BIT_MASK[i] = mask + BIT_MASK[i - 1];
        }
    }

    /**
     * Lists the maximum number that can be encoded
     * by a given number of units. PER_COMPONENT_SIZE[0]
     * corresponds to 1 unit used, PER_COMPONENT_SIZE[1]
     * to 2 units, and so on. With BITS_PER_UNIT = 4, the largest 
     * number to be encoded by 1 unit is 7, for 2 units it's 71, for
     * 3 units 583 ...
     */
    protected final static int[] PER_COMPONENT_SIZE = initComponents();

    private static int[] initComponents() {
        final int size[] = new int[10];
        size[0] = 7;  // = Math.pow(2, 3) - 1;
        int components;
        int numBits;
        for (int i = 1; i < size.length; i++) {
            components = i + 1;
            numBits = components * BITS_PER_UNIT - components;
            size[i] = (int)(Math.pow(2, numBits)) + size[i - 1];
        }
        return size;
    }

    protected final static int UNIT_SHIFT = 3;

    /** A 0-bit is used to mark the start of a new level */
    protected final static int LEVEL_SEPARATOR = 0;

    /** 
     * A 1-bit marks the start of a sub level, which is logically a part
     * of the current level.
     */
    protected final static int SUBLEVEL_SEPARATOR = 1;
    
    // the bits are stored in a byte[] 
    protected byte[] bits;

    // the current index into the byte[] used
    // for appending new bits
    protected int bitIndex = -1;

    public DLNBase() {
        bits = new byte[1];
    }

    public DLNBase(final DLNBase dln) {
        this.bits = new byte[dln.bits.length];
        System.arraycopy(dln.bits, 0, this.bits, 0, dln.bits.length);
        this.bitIndex = dln.bitIndex;
    }

    public DLNBase(final int units, final byte[] data, final int startOffset) {
    	if (units < 0) {
            throw new IllegalArgumentException("Negative size for DLN: " + units);
        }
        int blen = units / 8;
        if (units % 8 > 0) {
            ++blen;
        }
        bits = new byte[blen];
        System.arraycopy(data, startOffset, bits, 0, blen);
        bitIndex = units - 1;
    }

    protected DLNBase(final byte[] data, final int nbits) {
        final int remainder = nbits % 8;
        final int len = nbits / 8;
        bits = new byte[len + (remainder > 0 ? 1 : 0)];
        if (len > 0) {
            System.arraycopy(data, 0, bits, 0, len);
        }
        if (remainder > 0) {
            byte b = 0;
            for (int i = 0; i < remainder; i++) {
                if ((data[len] & (1 << ((7 - i) & 7))) != 0) {
                    b |= 1 << (7 - i);
                }
            }
            bits[len] = b;
        }
        bitIndex = nbits - 1;
    }

    public DLNBase(final short bitCnt, final VariableByteInput is) throws IOException {
        int blen = bitCnt / 8;
        if (bitCnt % 8 > 0) {
            ++blen;
        }
        bits = new byte[blen];
        is.read(bits);
        bitIndex = bitCnt - 1;
    }

    public DLNBase(final byte prefixLen, final DLNBase previous, final short bitCnt, final VariableByteInput is) throws IOException {
        int blen = bitCnt / 8;
        if (bitCnt % 8 > 0) {
            ++blen;
        }
        bits = new byte[blen];
        if (previous.bits.length < prefixLen) {
            throw new IOException("Found wrong prefix len: " + prefixLen + ". Previous: " + previous);
        }
        System.arraycopy(previous.bits, 0, bits, 0, prefixLen);
        is.read(bits, prefixLen, blen - prefixLen);
        bitIndex = bitCnt - 1;
    }

    /**
     * Set the level id which starts at offset to the
     * given id value.
     *
     * @param offset offset in number of bits
     * @param levelId the level id to set
     */
    public void setLevelId(final int offset, final int levelId) {
        bitIndex = offset - 1;
        setCurrentLevelId(levelId);
    }

    /**
     * Adds a new level to the node id, using levelId
     * as initial value.
     *
     * @param levelId initial value
     * @param isSubLevel if the new level id is a sublevel
     */
    public void addLevelId(final int levelId, final boolean isSubLevel) {
        if (bitIndex > -1){
            setNextBit(isSubLevel);
        }
        setCurrentLevelId(levelId);
    }

    /**
     * Increments the last level id by one.
     */
    public void incrementLevelId() {
        final int last = lastFieldPosition();
        bitIndex = last - 1;
        setCurrentLevelId(getLevelId(last) + 1);
    }

    public void decrementLevelId() {
        final int last = lastFieldPosition();
        bitIndex = last - 1;
        int levelId = getLevelId(last) - 1;
        if (levelId < 1) {
            levelId = 0;
        }
        setCurrentLevelId(levelId);
        // after decrementing, the DLN may need less bytes
        // than before. Remove the unused bytes, otherwise binary
        // comparisons may get wrong.
        final int len = bitIndex + 1;
        int blen = len / 8;
        if (len % 8 > 0) {
            ++blen;
        }
        
        if (blen < bits.length) {
            final byte[] nbits = new byte[blen];
            System.arraycopy(bits, 0, nbits, 0, blen);
            bits = nbits;
        }
    }

    /**
     * Set the level id for the last level that has been written.
     * The data array will be resized automatically if the bit set is
     * too small to encode the id.
     *
     * @param levelId the level id
     */
    protected void setCurrentLevelId(int levelId) {
        final int units = getUnitsRequired(levelId);
        final int numBits = bitWidth(units);
        if (units > 1) {
            levelId -= PER_COMPONENT_SIZE[units - 2];
        }
        for (int i = 1; i < units; i++) {
            setNextBit(true);
        }
        setNextBit(false);
        for (int i = numBits - 1; i >= 0; i--) {
            setNextBit(((levelId >>> i) & 1) != 0);
        }
    }

    /**
     * Returns the id starting at offset.
     *
     * @param startBit the offset (in number of bits) to read the level id from
     * @return the level id
     */
    public int getLevelId(int startBit) {
        final int units = unitsUsed(startBit, bits);
        startBit += units;
        final int numBits = bitWidth(units);
        //System.err.println("startBit: " + startBit + "; bitIndex: " + bitIndex + 
        //"; units: " + units + ": numBits: " + numBits + " " + toBitString() +
        //"; bits: " + bits.length);
        int id = 0;
        for (int i = numBits - 1; i >= 0; i--) {
            if ((bits[startBit >> UNIT_SHIFT] & (1 << ((7 - startBit++) & 7))) != 0) {
                id |= 1 << i;
            }
        }
        if (units > 1) {
            id += PER_COMPONENT_SIZE[units - 2];
        }
        return id;
    }

    /**
     * Returns the number of units currently used
     * to encode the id. The size of a single unit is
     * given by {@link #BITS_PER_UNIT}.
     *
     * @return the number of units
     */
    public int units() {
    	return bitIndex + 1;
    }

    /**
     * Returns the size of this id by counting the bytes
     * used to encode it.
     *
     * @return the size in bytes
     */
    public int size() {
        return bits.length;
    }

    private static int unitsUsed(int startBit, final byte[] bits) {
        int units = 1;
        while ((bits[startBit >> UNIT_SHIFT] & (1 << ((7 - startBit++) & 7))) != 0) {
            ++units;
        }
        return units;
    }

    public boolean isLevelSeparator(final int index) {
        return (bits[index >> UNIT_SHIFT] & (1 << ((7 - index) & 7))) == 0;
    }
    
    /**
     * Returns the number of level in this id, which corresponds
     * to the depth at which the node occurs within the node tree.
     *
     * @param startOffset the offset (in number of bits) to start counting
     * @return the number of levels in this id
     */
    public int getLevelCount(final int startOffset) {
        int bit = startOffset;
        int count = 0;
        while (bit > -1 && bit <= bitIndex) {
            final int units = unitsUsed(bit, bits);
            bit += units;
            bit += bitWidth(units);
            if (bit < bitIndex) {
                if ((bits[bit >> UNIT_SHIFT] & (1 << ((7 - bit++) & 7))) == LEVEL_SEPARATOR) {
                    ++count;
                }
            } else {
                ++count;
            }
        }
        return count;
    }

    /**
     * Returns the number of sub-levels in the id starting at
     * startOffset. This is required to determine where a node
     * can be inserted.
     * 
     * @param startOffset the start offset (in number of bits)
     * @return number of sub-levels
     */
    public int getSubLevelCount(final int startOffset) {
        int bit = startOffset;
        int count = 0;
        while (bit > -1 && bit <= bitIndex) {
            final int units = unitsUsed(bit, bits);
            bit += units;
            bit += bitWidth(units);
            if (bit < bitIndex) {
                ++count;
                if ((bits[bit >> UNIT_SHIFT] & (1 << ((7 - bit++) & 7))) == LEVEL_SEPARATOR) {
                    break;
                }
            } else {
                ++count;
            }
        }
        return count;
    }

    /**
     * Return all level ids converted to int.
     *
     * @return all level ids in this node id.
     */
    public int[] getLevelIds() {
        final int count = getLevelCount(0);
        final int[] ids = new int[count];
        int offset = 0;
        for (int i = 0; i < count; i++) {
            ids[i] = getLevelId(offset);
            offset += getUnitsRequired(ids[i]) * BITS_PER_UNIT;
        }
        return ids;
    }

    /**
     * Find the last level in the id and return its offset.
     *
     * @return start-offset of the last level id.
     */
    public int lastLevelOffset() {
        int bit = 0;
        int lastOffset = 0;
        while (bit <= bitIndex) {
            // check if the next bit starts a new level or just a sub-level component
            if (bit > 0) {
                if ((bits[bit >> UNIT_SHIFT] & (1 << ((7 - bit) & 7))) == LEVEL_SEPARATOR) {
                    lastOffset = bit + 1;
                }
                ++bit;
            }
            final int units = unitsUsed(bit, bits);
            bit += units;
            bit += bitWidth(units);
        }
        return lastOffset;
    }

    protected int lastFieldPosition() {
        int bit = 0;
        int lastOffset = 0;
        while (bit <= bitIndex) {
            if (bit > 0) {
                lastOffset = ++bit;
            }
            final int units = unitsUsed(bit, bits);
            bit += units;
            bit += bitWidth(units);
        }
        return lastOffset;
    }

    /**
     * Set (or unset) the next bit in the current sequence
     * of bits. The current position is moved forward and the
     * bit set is resized if necessary.
     *
     * @param value the value of the bit to set, i.e. 1 (true) or 0 (false)
     */
    private void setNextBit(final boolean value) {
        ++bitIndex;
        if ((bitIndex >> UNIT_SHIFT) >= bits.length) {
            final byte[] new_bits = new byte[bits.length + 1];
            System.arraycopy(bits, 0, new_bits, 0, bits.length);
            bits = new_bits;
        }
        if (value) {
            bits[bitIndex >> UNIT_SHIFT] |= 1 << ((7 - bitIndex) & 7);
        }
        else {
            bits[bitIndex >> UNIT_SHIFT] &= ~(1 << ((7 - bitIndex) & 7));
        }
    }

    /**
     * Calculates the number of bits available in a bit set
     * that uses the given number of units. These are the bits
     * that can be actually used for the id, not including the
     * trailing address bits.
     * 
     * @param units the number of units to use
     * @return number of bits available
     */
    protected static int bitWidth(final int units) {
        return (units * BITS_PER_UNIT) - units;
    }

    /**
     * Calculates the minimum number of units that would be required
     * to properly encode the given integer.
     * 
     * @param levelId the integer to encode in the level id
     * @return number of units required
     */
    protected static int getUnitsRequired(final int levelId) {
        for(int i = 0; i < PER_COMPONENT_SIZE.length; i++) {
            if(levelId < PER_COMPONENT_SIZE[i]) {
                return i + 1;
            }
        }
        // can't happen
        throw new IllegalStateException("Number of nodes exceeds the internal limit");
    }

    protected void compact() {
        final int units = bitIndex + 1;
        int blen = units / 8;
        if (units % 8 > 0) {
            ++blen;
        }
        final byte[] nbits = new byte[blen];
        System.arraycopy(bits, 0, nbits, 0, blen);
        this.bits = nbits;
    }

    public void serialize(final byte[] data, final int offset) {
        System.arraycopy(bits, 0, data, offset, bits.length);
    }

    public static int getLengthInBytes(final int units, final byte[] data, final int startOffset) {
        return (int) Math.ceil(units / 8.0);
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof DLNBase other)) {
            return false;
        }

        if (bitIndex != other.bitIndex) {
            return false;
        }
        return Arrays.equals(bits, other.bits);
    }

//    public int compareTo(final DLNBase other) {
//        if (other == null)
//            return 1;
//        final int a1len = bits.length;
//        final int a2len = other.bits.length;
//
//        int limit = a1len <= a2len ? a1len : a2len;
//        byte[] obits = other.bits;
//        for (int i = 0; i < limit; i++) {
//            byte b1 = bits[i];
//            byte b2 = obits[i];
//            if (b1 != b2)
//                return (b1 & 0xFF) - (b2 & 0xFF);
//        }
//        return (a1len - a2len);
//    }
//
//    public int compareTo(Object obj) {
//        DLNBase other = (DLNBase) obj;
//        return compareTo(other);
//    }

    public int compareBits(final DLNBase other, final int bitCount) {
        final int bytes = bitCount / 8;
        final int remaining = bitCount % 8;
        for (int i = 0; i < bytes; i++) {
            if (bits[i] != other.bits[i]) {
                return (bits[i] & 0xFF) - (other.bits[i] & 0xFF);
            }
        }
        return (bits[bytes] & BIT_MASK[remaining]) - 
            (other.bits[bytes] & BIT_MASK[remaining]);
    }

    /**
     * Checks if the current DLN starts with the
     * same bit sequence as other. This is used
     * to test ancestor-descendant relationships.
     * 
     * @param other other DLN to compare with
     * @return true if this DLN starts with the same bit sequence as the other
     */
    public boolean startsWith(final DLNBase other) {
        if (other.bitIndex > bitIndex) {
            return false;
        }
        final int bytes = other.bitIndex / 8;
        final int remaining = other.bitIndex % 8;
        for (int i = 0; i < bytes; i++) {
            if (bits[i] != other.bits[i]) {
                return false;
            }
        }
        return (bits[bytes] & BIT_MASK[remaining]) == (other.bits[bytes] & BIT_MASK[remaining]);
    }

    public String debug() {
        return this + " = " + toBitString() + " [" +  (bitIndex + 1) + ']';
    }

    @Override
    public String toString() {
        final StringBuilder buf = new StringBuilder();
        int offset = 0;
        while (offset <= bitIndex) {
            if (offset > 0) { 
                if ((bits[offset >> UNIT_SHIFT] & (1 << ((7 - offset++) & 7))) == 0) {
                    buf.append('.');
                } else {
                    buf.append('/');
                }
            }
            final int id = getLevelId(offset);
            buf.append(id);
            offset += getUnitsRequired(id) * BITS_PER_UNIT;
        }
        return buf.toString();
    }

    public String toBitString() {
        final StringBuilder buf = new StringBuilder();
        final int len = bits.length;
        for (final byte bit : bits) {
            buf.append(toBitString(bit));
        }
        return buf.toString();
    }

    private final static char[] digits = { '0', '1' };

    /**
     * Returns a string showing the bit representation
     * of the given byte.
     * 
     * @param b the byte to display
     * @return string representation
     */
    public static String toBitString(byte b) {
        final char[] buf = new char[8];
        int charPos = 8;
        final int radix = 2;
        final int mask = radix - 1;
        for (int i = 0; i < 8; i++) {
            buf[--charPos] = digits[b & mask];
            b >>>= 1;
        }
        return new String(buf);
    }
}
