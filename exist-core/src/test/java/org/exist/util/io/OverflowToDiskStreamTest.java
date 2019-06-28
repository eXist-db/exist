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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;

/**
 * @author <a href="mailto:patrick@reini.net">Patrick Reinhart</a>
 */
public class OverflowToDiskStreamTest {
    private MemoryContents memoryContents;
    private OutputStreamSupplier overflowStreamSupplier;
    private OverflowToDiskStream overflowToDiskStream;

    @Before
    public void setUp() {
        memoryContents = createMock(MemoryContents.class);
        overflowStreamSupplier = createMock(OutputStreamSupplier.class);
        overflowToDiskStream = new OverflowToDiskStream(5, memoryContents, overflowStreamSupplier);
    }

    @Test
    public void writeSingleByte() throws IOException {
        TestOutputStream testOutput = new TestOutputStream();

        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'1'}), eq(0), eq(1))).andReturn(1);
        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'2'}), eq(0), eq(1))).andReturn(1);
        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'3'}), eq(0), eq(1))).andReturn(1);
        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'4'}), eq(0), eq(1))).andReturn(1);
        expect(memoryContents.writeAtEnd(aryEq(new byte[]{'5'}), eq(0), eq(1))).andReturn(1);
        expect(overflowStreamSupplier.get()).andReturn(testOutput);
        expect(memoryContents.transferTo(testOutput, 0L)).andReturn(5L);
        memoryContents.reset();

        replay(memoryContents, overflowStreamSupplier);

        overflowToDiskStream.write('1');
        overflowToDiskStream.write('2');
        overflowToDiskStream.write('3');
        overflowToDiskStream.write('4');
        overflowToDiskStream.write('5');
        overflowToDiskStream.write('6');
        overflowToDiskStream.close();

        verify(memoryContents, overflowStreamSupplier);

        testOutput.assertClosedContent(new byte[]{'6'}, 0);
    }

    @Test
    public void close() throws IOException {
        replay(memoryContents, overflowStreamSupplier);

        overflowToDiskStream.close();
    }

    @Test
    public void flush() throws IOException {
        replay(memoryContents, overflowStreamSupplier);

        overflowToDiskStream.flush();
    }

    @Test
    public void writeByteArray() throws IOException {
        byte[] buf = new byte[]{'1', '2', '3', '4', '5', '6', '7', '8', '9'};
        TestOutputStream testOutput = new TestOutputStream();

        expect(memoryContents.writeAtEnd(buf, 0, 3)).andReturn(3);
        expect(overflowStreamSupplier.get()).andReturn(testOutput);
        expect(memoryContents.transferTo(testOutput, 0L)).andReturn(5L);
        memoryContents.reset();

        replay(memoryContents, overflowStreamSupplier);

        overflowToDiskStream.write(buf, 0, 3);
        overflowToDiskStream.flush(); // should not trigger as still writing to memory

        overflowToDiskStream.write(buf, 3, 3);
        overflowToDiskStream.flush();
        overflowToDiskStream.close();

        verify(memoryContents, overflowStreamSupplier);

        testOutput.assertClosedContent(new byte[]{'4', '5', '6'}, 1);
    }


    static class TestOutputStream extends ByteArrayOutputStream {
        private boolean closed;
        private int flushCount;

        public void assertClosedContent(byte[] expected, int expectedFlushes) {
            assertTrue("Stream not closed", closed);
            assertEquals(expectedFlushes, flushCount);
            assertArrayEquals(expected, toByteArray());
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }

        @Override
        public void flush() throws IOException {
            flushCount++;
            super.flush();
        }
    }
}
