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
package org.exist.util.io;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Random;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.Test;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class ByteBufferInputStreamTest {

    @Test
    public void available() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        assertEquals(testData.length, is.available());
    }

    @Test
    public void availableIsZeroAfterClose() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        is.close();

        assertEquals(0, is.available());
    }

    @Test
    public void availableAfterRead() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        //read first 2 bytes
        is.read();
        is.read();

        assertEquals(testData.length -2 , is.available());
    }

    @Test
    public void readByteByByteCorrectAndThenReturnMinus1AtEndOfStream() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        for (byte testDatum : testData) {
            assertEquals(testDatum, is.read());
        }

        //ensure reading past the end of the stream returns -1
        assertEquals(-1, is.read());
    }

    @Test
    public void readMultipleBytesCorrectAndThenReturnMinus1AtEndOfStream() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        byte readData[] = new byte[testData.length];
        int read = is.read(readData);

        assertEquals(testData.length, read);
        assertArrayEquals(testData, readData);

        //ensure reading past the end of the stream returns -1
        assertEquals(-1, is.read(readData));
    }

    @Test
    public void readMultipleBytesPastAvailable() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        byte readData[] = new byte[testData.length + 2];
        int read = is.read(readData);

        assertEquals(testData.length, read);
        assertArrayEquals(testData, subArray(readData, testData.length));

        //bytes past the available should still be 0
        assertArrayEquals(new byte[]{0,0}, subArray(readData, testData.length, 2));
    }
    
    @Test
    public void readMultipleBytesSpecificCorrectAndThenReturnMinus1AtEndOfStream() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        byte readData[] = new byte[testData.length];
        is.read(readData, 0, testData.length);

        assertArrayEquals(testData, readData);

        //ensure reading past the end of the stream returns -1
        assertEquals(-1, is.read(readData, 0, testData.length));
    }

    @Test
    public void readMultipleBytesSpecificPastAvailable() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        byte readData[] = new byte[testData.length + 2];
    	int read = is.read(readData, 0, readData.length);

    	assertEquals(testData.length, read);
        assertArrayEquals(testData, subArray(readData, testData.length));

        //bytes past the available should still be 0
        assertArrayEquals(new byte[]{0,0}, subArray(readData, testData.length, 2));
    }

    @Test(expected=IOException.class)
    public void readSingleByteAfterCloseThrowsException() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        try {
            is.close();
        } catch(IOException ioe) {
            fail(ioe.getMessage());
        }

        //should throw IOException
        is.read();
    }

    @Test(expected=IOException.class)
    public void readMultipleBytesAfterCloseThrowsException() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        try {
            is.close();
        } catch(IOException ioe) {
            fail(ioe.getMessage());
        }

        byte readBuf[] = new byte[2];
        //should throw IOException
        is.read(readBuf);
    }

    @Test(expected=IOException.class)
    public void readMultipleBytesSpecificAfterCloseThrowsException() throws IOException {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);

        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        try {
            is.close();
        } catch(IOException ioe) {
            fail(ioe.getMessage());
        }

        byte readBuf[] = new byte[2];

        //should throw IOException
        is.read(readBuf, 0, 2);
    }

    @Test
    public void readMultipleBytesInLoop() throws IOException {

        //generate 1KB of test data
        Random random = new Random();
        byte testData[] = new byte[1024];
        random.nextBytes(testData);

        final ByteBuffer buf = ByteBuffer.wrap(testData);
        try(final InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf))) {
            final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream(testData.length);
            byte readBuf[] = new byte[56];
            int read = -1;

            while ((read = is.read(readBuf)) > -1) {
                assertLessThanOrEqual(readBuf.length, read);
                baos.write(readBuf, 0, read);
            }

            assertArrayEquals(testData, baos.toByteArray());
        }
    }

    @Test
    public void readMultipleBytesSpecificInLoop() throws IOException {

        //generate 1KB of test data
        Random random = new Random();
        byte testData[] = new byte[1024];
        random.nextBytes(testData);

        final ByteBuffer buf = ByteBuffer.wrap(testData);
        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream(testData.length)) {
            byte readBuf[] = new byte[56];
            int read = -1;

            while ((read = is.read(readBuf, 0, readBuf.length)) > -1) {

                assertLessThanOrEqual(readBuf.length, read);

                baos.write(readBuf, 0, read);
            }
            assertArrayEquals(testData, baos.toByteArray());
        }
    }

    @Test
    public void markReturnsTrue() {
        final byte testData[] = "test data".getBytes();
        final ByteBuffer buf = ByteBuffer.wrap(testData);
        InputStream is = new ByteBufferInputStream(new TestableByteBufferAccessor(buf));

        assertTrue(is.markSupported());
    }

    public class TestableByteBufferAccessor implements ByteBufferAccessor {
        private final ByteBuffer buf;

        public TestableByteBufferAccessor(ByteBuffer buf) {
            this.buf = buf;
        }

        @Override
        public ByteBuffer getBuffer() {
            return buf;
        }
    }

    private byte[] subArray(byte data[], int len) {
        byte newData[] = new byte[len];
        System.arraycopy(data, 0, newData, 0, len);
        return newData;
    }

     private byte[] subArray(byte data[], int offset, int len) {
        byte newData[] = new byte[len];
        System.arraycopy(data, offset, newData, 0, len);
        return newData;
    }

    private static void assertLessThanOrEqual(int expectedMax, int actual) {
        if(actual > expectedMax) {
            fail("Expected actual value" + actual + " to be less than or equal to " + expectedMax);
        }
    }
}