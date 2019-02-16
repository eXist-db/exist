/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.util.io;

import org.junit.Test;

import java.io.ByteArrayInputStream;

import static org.junit.Assert.assertEquals;

public class FastByteArrayInputStreamTest {

    @Test
    public void construct_1() {
        final byte[] empty = new byte[0];
        final byte[] one = new byte[1];
        final byte[] some = new byte[25];

        FastByteArrayInputStream is = new FastByteArrayInputStream(empty);
        assertEquals(empty.length, is.available());

        is = new FastByteArrayInputStream(one);
        assertEquals(one.length, is.available());

        is = new FastByteArrayInputStream(some);
        assertEquals(some.length, is.available());
    }

    @Test
    public void construct_2() {
        final byte[] empty = new byte[0];
        final byte[] one = new byte[1];
        final byte[] some = new byte[25];

        FastByteArrayInputStream is = new FastByteArrayInputStream(empty, 0);
        assertEquals(empty.length, is.available());
        is = new FastByteArrayInputStream(empty, 1);
        assertEquals(0, is.available());

        is = new FastByteArrayInputStream(one, 0);
        assertEquals(one.length, is.available());
        is = new FastByteArrayInputStream(one, 1);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(one, 2);
        assertEquals(0, is.available());

        is = new FastByteArrayInputStream(some, 0);
        assertEquals(some.length, is.available());
        is = new FastByteArrayInputStream(some, 1);
        assertEquals(some.length - 1, is.available());
        is = new FastByteArrayInputStream(some, 10);
        assertEquals(some.length - 10, is.available());
        is = new FastByteArrayInputStream(some, some.length);
        assertEquals(0, is.available());
    }

    @Test
    public void construct_3() {
        final byte[] empty = new byte[0];
        final byte[] one = new byte[1];
        final byte[] some = new byte[25];

        FastByteArrayInputStream is = new FastByteArrayInputStream(empty, 0);
        assertEquals(empty.length, is.available());
        is = new FastByteArrayInputStream(empty, 1);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(empty, 0,1);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(empty, 1,1);
        assertEquals(0, is.available());

        is = new FastByteArrayInputStream(one, 0);
        assertEquals(one.length, is.available());
        is = new FastByteArrayInputStream(one, 1);
        assertEquals(one.length - 1, is.available());
        is = new FastByteArrayInputStream(one, 2);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(one, 0, 1);
        assertEquals(1, is.available());
        is = new FastByteArrayInputStream(one, 1, 1);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(one, 0, 2);
        assertEquals(1, is.available());
        is = new FastByteArrayInputStream(one, 2, 1);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(one, 2, 2);
        assertEquals(0, is.available());

        is = new FastByteArrayInputStream(some, 0);
        assertEquals(some.length, is.available());
        is = new FastByteArrayInputStream(some, 1);
        assertEquals(some.length - 1, is.available());
        is = new FastByteArrayInputStream(some, 10);
        assertEquals(some.length - 10, is.available());
        is = new FastByteArrayInputStream(some, some.length);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(some, some.length, some.length);
        assertEquals(0, is.available());
        is = new FastByteArrayInputStream(some, some.length - 1, some.length);
        assertEquals(1, is.available());
        is = new FastByteArrayInputStream(some, 0, 7);
        assertEquals(7, is.available());
        is = new FastByteArrayInputStream(some, 7, 7);
        assertEquals(7, is.available());
        is = new FastByteArrayInputStream(some, 0, some.length * 2);
        assertEquals(some.length, is.available());
        is = new FastByteArrayInputStream(some, some.length - 1, 7);
        assertEquals(1, is.available());
    }
}
