package org.exist.storage.btree;

/*
 * dbXML License, Version 1.0
 *
 *
 * Copyright (c) 1999-2001 The dbXML Group, L.L.C.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * 3. The end-user documentation included with the redistribution,
 *    if any, must include the following acknowledgment:
 *       "This product includes software developed by
 *        The dbXML Group (http://www.dbxml.com/)."
 *    Alternately, this acknowledgment may appear in the software
 *    itself, if and wherever such third-party acknowledgments normally
 *    appear.
 *
 * 4. The names "dbXML" and "The dbXML Group" must not be used to
 *    endorse or promote products derived from this software without
 *    prior written permission. For written permission, please contact
 *    info@dbxml.com.
 *
 * 5. Products derived from this software may not be called "dbXML",
 *    nor may "dbXML" appear in their name, without prior written
 *    permission of The dbXML Group.
 *
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND ANY EXPRESSED OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE DBXML GROUP OR ITS CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY,
 * OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
 * OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * $Id$
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Value is the primary base class for all data storing objects.
 * The content window of Value objects are immutable, but the
 * underlying byte array is not.
 */

public class Value implements Comparable<Object> {

    private static Logger LOG = LogManager.getLogger(Value.class.getName());

    public final static Value EMPTY_VALUE = new Value(new byte[0]);

    protected long address = -1;
    protected byte[] data = null;
    protected int pos = 0;
    protected int len = -1;

    public Value() {
    }

    public Value(Value value) {
        this.data = value.data;
        this.pos = value.pos;
        this.len = value.len;
	}

    public Value(byte[] data) {
        this.data = data;
        this.len = data.length;
    }

    public Value(byte[] data, int pos, int len) {
        this.data = data;
        this.pos = pos;
        this.len = len;
    }

    public Value(String data) {
        this.data = data.getBytes(UTF_8);
        this.len = this.data.length;
    }

    public void setAddress( long addr ) {
        address = addr;
    }

    public long getAddress() {
        return address;
    }

    /**
     * getData retrieves the data being stored by the Value as a byte array.
     *
     * @return The Data
     */
    public byte[] getData() {
        if (pos > 0 || len < data.length) {
            final byte[] b = new byte[len];
            System.arraycopy(data, pos, b, 0, len);
            return b;
        } else
            {return data;}
    }

    public final byte[] data() {
        return data;
    }

    public final int start() {
        return pos;
    }

    /**
     * getLength retrieves the length of the data being stored by the Value.
     *
     * @return The Value length
     */
    public final int getLength() {
        return len;
    }

    public String toString() {
    	return dump();
    }

    public int hashCode() {
        return toString().hashCode();
    }

    public boolean equals(Value value) {
        return len == value.len ? compareTo(value) == 0 : false;
    }

    public boolean equals(Object obj) {
        if (this == obj)
            {return true;}
        if (obj instanceof Value)
            {return equals((Value) obj);}
        else
            {return equals(new Value(obj.toString()));}
    }

    public final int compareTo(Value value) {
        final int stop = len > value.len ? value.len : len;
        for (int i = 0; i < stop; i++) {
            final byte b1 = data[pos + i];
            final byte b2 = value.data[value.pos + i];
            if (b1 != b2){
                final short s1 = (short) (b1 & 0xFF);
                final short s2 = (short) (b2 & 0xFF);
                return s1 > s2 ? (i + 1) : - (i + 1);
            }
        }
        if (len == value.len)
            {return 0;}
        else
            {return len > value.len ? stop + 1 : - (stop + 1);}
    }

    public final int compareTo(Object obj) {
        if (obj instanceof Value)
            {return compareTo((Value) obj);}
        else
            {return compareTo(new Value(obj.toString()));}
    }

    public final int comparePrefix(Value value) {
        for (int i = 0; i < value.len; i++) {
            final byte b1 = data[pos + i];
            final byte b2 = value.data[value.pos + i];
            if (b1 != b2){
                final short s1 = (short) (b1 & 0xFF);
                final short s2 = (short) (b2 & 0xFF);
                return s1 > s2 ? (i + 1) : - (i + 1);
            }
        }
        return 0;
    }

    public final int comparePrefix(Value prefix, Value keyPrefix) {
        if (keyPrefix.getLength() >= prefix.getLength()) {
            final int cmp = keyPrefix.comparePrefix(prefix);
            if (cmp != 0 || keyPrefix.getLength() == prefix.getLength())
                {return cmp;}
            for (int i = prefix.getLength(); i < keyPrefix.getLength(); i++) {
                final byte b1 = data[pos + i];
                final byte b2 = keyPrefix.data[keyPrefix.pos + i];
                if (b1 != b2){
                    final short s1 = (short) (b1 & 0xFF);
                    final short s2 = (short) (b2 & 0xFF);
                    return s1 > s2 ? (i + 1) : - (i + 1);
                }
            }
            return 0;
        } else {
            return prefix.comparePrefix(keyPrefix);
        }
    }

    public final boolean startsWith(Value value) {
        if (len < value.len)
            {return false;}
        final byte[] vdata = value.data;
        final int vpos = value.pos;
        for (int i = 0; i < value.len; i++) {
            if (data[i + pos] != vdata[i + vpos])
                {return false;}
        }
        return true;
    }

    public final boolean endsWith(Value value) {
        if (len < value.len)
            {return false;}
        final byte[] vdata = value.data;
        final int vpos = value.pos;
        final int d = len - value.len;
        for (int i = 0; i < value.len; ++i) {
            if (data[d + i + pos] != vdata[i + vpos])
                {return false;}
        }
        return true;
    }

    /**
     * Returns the length of the common prefix this value
     * shares with the specified other value (if any).
     *
     * @param other the other value
     * @return length of the common prefix, 0 if there is none
     */
    public int commonPrefix(Value other) {
        final int l = Math.min(len, other.len);
        int i = 0;
        for ( ; i < l; i++) {
            final byte b = data[pos + i];
            if (b != other.data[other.pos + i])
                {break;}
        }
        return i;
    }

    public int checkPrefix(Value prefix) {
        final int l = Math.min(prefix.len, len);
        for (int i = 0; i < l; i++) {
            if (prefix.data[prefix.pos + i] != data[pos + i])
                {return i;}
        }
        return l;
    }

    public Value getSeparator(Value other) {
        final int offset = commonPrefix(other) + 1;
        final byte[] data = new byte[Math.abs(offset)];
        System.arraycopy(other.getData(), 0, data, 0, data.length);
        return new Value(data);
    }

    public String dump() {
        final StringBuilder buf = new StringBuilder();
        for (int i = 0; i < len; i++) {
            buf.append(Integer.toHexString(data[pos + i]));
        }
        return buf.toString();
    }
}
