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

import org.easymock.IArgumentMatcher;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reportMatcher;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class MemoryContentsInputStreamTest {
    private MemoryContents memoryContents;
    private MemoryContentsInputStream inputStream;

    @Before
    public void setUp() {
        memoryContents = createMock(MemoryContents.class);
        inputStream = new MemoryContentsInputStream(memoryContents);
    }

    @Test
    public void available() throws IOException {
        expect(memoryContents.size()).andReturn(1L + Integer.MAX_VALUE);
        expect(memoryContents.size()).andReturn(1235L);
        expect(memoryContents.size()).andReturn(1L);

        replay(memoryContents);

        assertEquals(Integer.MAX_VALUE, inputStream.available());
        assertEquals(1234L, inputStream.available());
        assertEquals(1L, inputStream.available());

        verify(memoryContents);
    }

    @Test
    public void readSingleByte() throws IOException {
        expect(memoryContents.read(write('a'), eq(0L), eq(0), eq(1))).andReturn(1);
        expect(memoryContents.read(write('b'), eq(1L), eq(0), eq(1))).andReturn(0);
        expect(memoryContents.read(write('c'), eq(1L), eq(0), eq(1))).andReturn(-1);

        replay(memoryContents);

        assertEquals('a', inputStream.read());
        assertEquals(0, inputStream.read());
        assertEquals(-1, inputStream.read());

        verify(memoryContents);
    }

    @Test
    public void readByteArray() throws IOException {
        byte[] buf = new byte[20];

        expect(memoryContents.read(aryEq(buf), eq(0L), eq(1), eq(10))).andReturn(9);
        expect(memoryContents.read(aryEq(buf), eq(9L), eq(2), eq(9))).andReturn(0);
        expect(memoryContents.read(aryEq(buf), eq(9L), eq(3), eq(8))).andReturn(-1);

        replay(memoryContents);

        assertEquals(9, inputStream.read(buf, 1, 10));
        assertEquals(0, inputStream.read(buf, 2, 9));
        assertEquals(-1, inputStream.read(buf, 3, 8));

        verify(memoryContents);
    }

    @Test
    public void skip() throws IOException {
        expect(memoryContents.size()).andReturn(10L);
        expect(memoryContents.size()).andReturn(50L);

        replay(memoryContents);

        assertEquals(10, inputStream.skip(20));
        assertEquals(20, inputStream.skip(20));

        verify(memoryContents);
    }

    @Test
    public void transferTo​() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        expect(memoryContents.size()).andReturn(123L);
        expect(memoryContents.transferTo(out, 0L)).andReturn(123L);

        replay(memoryContents);

        assertEquals(123L, inputStream.transferTo(out));

        verify(memoryContents);
    }

    static byte[] write(int ch) {
        reportMatcher(new IArgumentMatcher() {
            @Override
            public boolean matches(Object o) {
                if (o instanceof byte[]) {
                    byte[] data = (byte[]) o;
                    if (data.length == 1) {
                        data[0] = (byte) ch;
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void appendTo(StringBuffer stringBuffer) {
                stringBuffer.append("bye[1]");
            }
        });
        return null;
    }
}
