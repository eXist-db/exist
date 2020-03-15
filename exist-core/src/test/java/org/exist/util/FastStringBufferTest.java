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
package org.exist.util;

import org.junit.Test;

import static org.junit.Assert.*;

public class FastStringBufferTest {

    @Test
    public void insertCharAt_begin(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(0, '#');
        assertEquals("#12345", fsb.toString());
    }

    @Test
    public void insertCharAt_middle(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(3, '#');
        assertEquals("123#45", fsb.toString());
    }

    @Test
    public void insertCharAt_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(5, '#');
        assertEquals("12345#", fsb.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void insertCharAt_IOOB_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(-1, '#');
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void insertCharAt_IOOB_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.insertCharAt(6, '#');
    }

    @Test
    public void removeCharAt_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(0);
        assertEquals("2345", fsb.toString());
    }

    @Test
    public void removeCharAt_middle(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(2);
        assertEquals("1245", fsb.toString());
    }

    @Test
    public void removeCharAt_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(4);
        assertEquals("1234", fsb.toString());
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void removeCharAt_IOOB_front(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(-1);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void removeCharAt_IOOB_end(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append("12345");
        fsb.removeCharAt(5);
    }

    @Test
    public void insertCharAt_capacity(){
        FastStringBuffer fsb = new FastStringBuffer(5);
        assertEquals(0, fsb.length());
    }

    @Test
    public void getNormalizedString0(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append(" 12345 ");
        assertEquals("12345", fsb.getNormalizedString(0));
    }

    @Test
    public void getNormalizedStringBoth(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append(" 12345 ");
        assertEquals("12345", fsb.getNormalizedString(FastStringBuffer.SUPPRESS_BOTH));
    }

    @Test
    public void getNormalizedStringLeading(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append(" 12345 ");
        assertEquals("12345 ", fsb.getNormalizedString(FastStringBuffer.SUPPRESS_LEADING_WS));
    }

    @Test
    public void getNormalizedStringTrailing(){
        FastStringBuffer fsb = new FastStringBuffer(0);
        fsb.append(" 12345 ");
        assertEquals(" 12345", fsb.getNormalizedString(FastStringBuffer.SUPPRESS_TRAILING_WS));
    }

}