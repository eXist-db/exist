/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
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

import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class MemoryContentsImplTest {
    private byte[] buf;
    private MemoryContents contents;

    @Before
    public void setUp() {
        buf = new byte[]{'0', '1', '2', '3', '4', '5', '6', '7', '8', '9'};
        contents = MemoryContentsImpl.createWithInitialBlocks(2);
    }

    @Test
    public void testSize() {
        assertEquals(0, contents.size());
    }

    @Test
    public void writeAtEnd() throws IOException {
        assertEquals(2, contents.writeAtEnd(buf, 0, 2));
        assertEquals(2, contents.size());
        assertEquals(2, contents.writeAtEnd(buf, 2, 2));
        assertEquals(4, contents.size());
    }

    @Test
    public void write() throws IOException {
        assertEquals(8, contents.write(buf, 2L, 0, 8));
        assertEquals(10, contents.size());
    }

    @Test
    public void read() throws IOException {
        byte[] dst = new byte[10];
        assertEquals(10, contents.write(buf, 0L, 0, 10));
        assertEquals(5, contents.read(dst, 0L, 2, 5));

        byte[] expected = new byte[10];
        System.arraycopy(buf, 0, expected, 2, 5);
        assertArrayEquals(expected, dst);
    }

    @Test
    public void transferTo() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int length = buf.length;

        assertEquals(length, contents.writeAtEnd(buf, 0, length));
        assertEquals(length, contents.transferTo(out, 0L));

        assertArrayEquals(buf, out.toByteArray());
    }

    @Test
    public void bigWriteAndRead() throws IOException {
        // set up phase
        Random random = new Random();
        int length = 1024 * 1024 + 1024;
        buf = new byte[length];
        random.nextBytes(buf);

        // test phase
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertEquals(length, contents.writeAtEnd(buf, 0, length));
        assertEquals(length, contents.transferTo(out, 0L));

        assertArrayEquals(buf, out.toByteArray());
    }
}
