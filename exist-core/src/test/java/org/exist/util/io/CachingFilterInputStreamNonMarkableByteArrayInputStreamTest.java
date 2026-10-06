/*
 * Copyright (c) 2012, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of Adam Retter Consulting nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.util.io;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.apache.commons.io.input.MarkShieldInputStream;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Collection;
import java.util.Arrays;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Test cases for CachingFilterInputStream
 *
 * @version 1.0
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class CachingFilterInputStreamNonMarkableByteArrayInputStreamTest {

    private final static int _4KB = 4 * 1024;
    private final static int _6KB = 6 * 1024;
    private final static int _12KB = 12 * 1024;
    private final static int _32KB = 32 * 1024;
    private final static int _64KB = 64 * 1024;

    private Class<FilterInputStreamCache> cacheClass;

    public static Collection data() {
        Object[][] data = new Object[][]{
            {MemoryFilterInputStreamCache.class},
            {MemoryMappedFileFilterInputStreamCache.class},
            {FileFilterInputStreamCache.class}
        };
        return Arrays.asList(data);
    }

    public void initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(final Class<FilterInputStreamCache> cacheClass) {
        this.cacheClass = cacheClass;
    }

    private FilterInputStreamCache getNewCache(InputStream is) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Constructor ctor = cacheClass.getDeclaredConstructor(InputStream.class);
        ctor.setAccessible(true);
        return (FilterInputStreamCache) ctor.newInstance(is);
    }

    @MethodSource("data") @ParameterizedTest
    public void readByte(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read the first 3 bytes
        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());
        assertEquals(testData[2], cfis.read());

        //mark position
        cfis.mark(Integer.MAX_VALUE);

        //read the next 3 bytes
        assertEquals(testData[3], cfis.read());
        assertEquals(testData[4], cfis.read());
        assertEquals(testData[5], cfis.read());

        //reset position to the mark
        cfis.reset();

        //attempt to reread the last 3 bytes from the mark (from the cache)
        assertEquals(testData[3], cfis.read());
        assertEquals(testData[4], cfis.read());
        assertEquals(testData[5], cfis.read());

        //read the next 2 bytes past the reset mark (past the cache, e.g. from src)
        assertEquals(testData[6], cfis.read());
        assertEquals(testData[7], cfis.read());

        //reset position to the mark
        cfis.reset();

        //attempt to read the last 5 bytes (from the cache)
        assertEquals(testData[3], cfis.read());
        assertEquals(testData[4], cfis.read());
        assertEquals(testData[5], cfis.read());
        assertEquals(testData[6], cfis.read());
        assertEquals(testData[7], cfis.read());

        //mark position
        cfis.mark(-1);

        //read the next 2 bytes past the reset mark (past the cache, e.g. from src)
        assertEquals(testData[8], cfis.read());
        assertEquals(testData[9], cfis.read());

        //reset position to the mark
        cfis.reset();

        //attempt to reread the last 2 bytes from the mark (from the cache)
        assertEquals(testData[8], cfis.read());
        assertEquals(testData[9], cfis.read());
    }

    @MethodSource("data") @ParameterizedTest
    public void readByteOnClosedStream(final Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);
        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData[0], cfis.read());

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.read());
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytePastEndOfStreamFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "he";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.mark(Integer.MAX_VALUE);

        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());

        cfis.reset();

        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());

        //read byte past end of cache
        final int b = cfis.read();
        assertEquals(-1, b);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytePastEndOfStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read all the bytes upto end of stream
        int b = -1;
        int testDataOffset = 0;
        while ((b = cfis.read()) > -1) {
            assertEquals(testData[testDataOffset++], b);
        }

        //read byte past end of stream
        b = cfis.read();
        assertEquals(-1, b);
    }

    @MethodSource("data") @ParameterizedTest
    public void readByteAllFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the data
        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());
        assertEquals(testData[2], cfis.read());
        assertEquals(testData[3], cfis.read());
        assertEquals(testData[4], cfis.read());

        //reset position to the mark
        cfis.reset();

        //attempt to reread the data (from the cache)
        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());
        assertEquals(testData[2], cfis.read());
        assertEquals(testData[3], cfis.read());
        assertEquals(testData[4], cfis.read());
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytes(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read the first 3 bytes
        byte result[] = new byte[3];
        int read = cfis.read(result);
        assertEquals(3, read);
        assertArrayEquals(subArray(testData, 3), result);

        //mark position
        cfis.mark(Integer.MAX_VALUE);

        //read the next 3 bytes
        result = new byte[3];
        read = cfis.read(result);
        assertEquals(3, read);
        assertArrayEquals(subArray(testData, 3, 3), result);

        //reset position to the mark
        cfis.reset();

        //attempt to reread the last 3 bytes from the mark (from the cache)
        result = new byte[3];
        read = cfis.read(result);
        assertEquals(3, read);
        assertArrayEquals(subArray(testData, 3, 3), result);

        //read the next 2 bytes past the reset mark (past the cache, e.g. from src)
        result = new byte[2];
        read = cfis.read(result);
        assertEquals(2, read);
        assertArrayEquals(subArray(testData, 6, 2), result);

        //reset position to the mark
        cfis.reset();

        //attempt to read the last 5 bytes (from the cache)
        result = new byte[5];
        read = cfis.read(result);
        assertEquals(5, read);
        assertArrayEquals(subArray(testData, 3, 5), result);

        //mark position
        cfis.mark(-1);

        //read the next 2 bytes past the reset mark (past the cache, e.g. from src)
        result = new byte[2];
        read = cfis.read(result);
        assertEquals(2, read);
        assertArrayEquals(subArray(testData, 8, 2), result);

        //reset position to the mark
        cfis.reset();

        //attempt to reread the last 2 bytes from the mark (from the cache)
        result = new byte[2];
        read = cfis.read(result);
        assertEquals(2, read);
        assertArrayEquals(subArray(testData, 8, 2), result);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesOnClosedStream(final Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);
        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        final byte result[] = new byte[2];
        cfis.read(result);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.read(result));
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesPastEndOfStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        final byte result[] = new byte[testData.length];
        int read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);

        final byte endOfStreamResult[] = new byte[testData.length];
        read = cfis.read(endOfStreamResult);
        assertEquals(-1, read);
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, endOfStreamResult);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesPastEndOfStreamFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.mark(Integer.MAX_VALUE);

        //read first two bytes from stream
        final byte result[] = new byte[2];
        int read = cfis.read(result);
        assertEquals(2, read);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.reset();

        //read all bytes from cache and src, +1 past end of stream
        final byte endOfStreamResult[] = new byte[testData.length + 1];
        read = cfis.read(endOfStreamResult);
        final byte expectedResult[] = new byte[testData.length + 1];
        System.arraycopy(testData, 0, expectedResult, 0, testData.length);
        assertEquals(testData.length, read);
        assertArrayEquals(expectedResult, endOfStreamResult);

        //2nd attempt to read past end of stream
        read = cfis.read(result);
        assertEquals(-1, read);
        read = cfis.read(result);
        assertEquals(-1, read);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesAllFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the data
        byte result[] = new byte[testData.length];
        int read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);

        //reset position to the mark
        cfis.reset();

        //attempt to reread the data (from the cache)
        result = new byte[testData.length];
        read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesPartFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the first 5 byts data
        byte result[] = new byte[5];
        int read = cfis.read(result);
        assertEquals(5, read);
        assertArrayEquals(subArray(testData, 5), result);

        //reset position to the mark
        cfis.reset();

        //attempt to read all the data (first 5 bytes will be from the cache)
        result = new byte[testData.length];
        read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesWithZeroOffsetAllFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the data
        byte result[] = new byte[testData.length];
        int read = cfis.read(result, 0, testData.length);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);

        //reset position to the mark
        cfis.reset();

        //attempt to reread the data (from the cache)
        result = new byte[testData.length];
        read = cfis.read(result, 0, testData.length);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesWithZeroOffsetPartFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the first 5 byts data
        byte result[] = new byte[5];
        int read = cfis.read(result, 0, result.length);
        assertEquals(5, read);
        assertArrayEquals(subArray(testData, 5), result);

        //reset position to the mark
        cfis.reset();

        //attempt to read all the data (first 5 bytes will be from the cache)
        result = new byte[testData.length];
        read = cfis.read(result, 0, result.length);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);
    }

    @MethodSource("data") @ParameterizedTest
    public void readBytesWithOffsetAndLengthAllFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark the position
        cfis.mark(Integer.MAX_VALUE);

        //read the data
        byte result[] = new byte[4];
        int read = cfis.read(result, 1, 3);
        assertEquals(3, read);
        byte expected[] = new byte[4];
        expected[0] = 0;
        System.arraycopy(testData, 0, expected, 1, 3);
        assertArrayEquals(expected, result);

        //reset position to the mark
        cfis.reset();

        //attempt to reread the data (from the cache)
        result = new byte[4];
        read = cfis.read(result, 2, 2);
        expected = new byte[4];
        expected[0] = 0;
        expected[1] = 0;
        System.arraycopy(testData, 0, expected, 2, 2);
        assertEquals(2, read);
        assertArrayEquals(expected, result);
    }

    @MethodSource("data") @ParameterizedTest
    public void skip(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read the first 3 bytes
        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());
        assertEquals(testData[2], cfis.read());

        //skip 3 bytes
        cfis.skip(3);

        //read bytes 5 to 7 inclusive
        assertEquals(testData[6], cfis.read());
        assertEquals(testData[7], cfis.read());
        assertEquals(testData[8], cfis.read());
    }

    @MethodSource("data") @ParameterizedTest
    public void skipPartFromCache(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read the first 2 bytes
        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());

        //skip 2 bytes
        cfis.skip(2);

        cfis.mark(Integer.MAX_VALUE);

        //read byte 5
        assertEquals(testData[4], cfis.read());

        //skip 2 bytes
        cfis.skip(2);

        //read bytes 6 to 7 inclusive
        assertEquals(testData[7], cfis.read());
        assertEquals(testData[8], cfis.read());

        cfis.reset();

        //reread bytes 5 to 7 inclusive
        assertEquals(testData[4], cfis.read());
        assertEquals(testData[5], cfis.read());
        assertEquals(testData[6], cfis.read());
        assertEquals(testData[7], cfis.read());
        assertEquals(testData[8], cfis.read());

        //read final byte (outside cache)
        assertEquals(testData[9], cfis.read());

    }

    @MethodSource("data") @ParameterizedTest
    public void skipOnClosedStream(final Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.skip(1));
    }

    @MethodSource("data") @ParameterizedTest
    public void skipNegativeBytes(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //should cause IOException
        final long skipped = cfis.skip(-1);
        assertEquals(0, skipped);
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnClosedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnEmptyStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final InputStream is = new UnsynchronizedByteArrayInputStream(new byte[]{});

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnUnCachedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnPartiallyReadStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read first 2 bytes
        cfis.read();
        cfis.read();

        assertEquals(testData.length - 2, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnPartiallyCachedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        //read first 2 bytes
        cfis.read();
        cfis.read();

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnOffsetPartiallyCachedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read first 2 bytes
        cfis.read();
        cfis.read();

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        //read next 2 bytes
        cfis.read();
        cfis.read();

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length - 2, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnCachedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        for (int i = 0; i < testData.length; i++) {
            cfis.read();
        }

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void availableOnOffsetCachedStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read first 2 bytes
        cfis.read();
        cfis.read();

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        for (int i = 0; i < testData.length - 2; i++) {
            cfis.read();
        }

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length - 2, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest
    public void constructedFromCachingFilterInputStream(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final byte[] testData = generateRandomData(_12KB);
        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        //first CachingFilterInputStream
        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(getNewCache(is));

        //second CachingFilterInputStream wraps first CachingFilterInputStream
        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cfis1);

        assertArrayEquals(testData, consumeInputStream(cfis2));
    }

    @MethodSource("data") @ParameterizedTest
    public void constructedFromCachingFilterInputStreamConsumed(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final byte[] testData = generateRandomData(_12KB);
        final InputStream is = new MarkShieldInputStream(new UnsynchronizedByteArrayInputStream(testData));

        //first CachingFilterInputStream
        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(getNewCache(is));

        assertArrayEquals(testData, consumeInputStream(cfis1));

        //second CachingFilterInputStream wraps first CachingFilterInputStream
        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cfis1);

        assertArrayEquals(testData, consumeInputStream(cfis2));
    }

    @MethodSource("data") @ParameterizedTest
    public void constructedFromCachingFilterInputStreamPartiallyConsumed(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);

        final byte[] testData = generateRandomData(_12KB);
        final InputStream is = new MarkShieldInputStream(new UnsynchronizedByteArrayInputStream(testData));

        //first CachingFilterInputStream
        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(getNewCache(is));

        //read first 6KB
        final byte firstPart[] = new byte[_6KB];
        cfis1.read(firstPart);
        assertArrayEquals(subArray(testData, _6KB), firstPart); //ensure first 6KB was read!

        //second CachingFilterInputStream wraps first CachingFilterInputStream
        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cfis1);

        assertArrayEquals(testData, consumeInputStream(cfis2));
    }

    /**
     * When given an underlying InputSource and caching it twice with the same
     * cache we should be able to read the input twice assuming that the input
     * that we are interested in has not been read before a mark()
     */
    @MethodSource("data") @ParameterizedTest
    public void interleavedSourceReads(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final byte[] testData = generateRandomData(_64KB);
        final InputStream is = new MarkShieldInputStream(new UnsynchronizedByteArrayInputStream(testData));

        final FilterInputStreamCache cache1 = getNewCache(is);

        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(cache1);
        cfis1.mark(Integer.MAX_VALUE);

        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cache1);

        final byte result1[] = new byte[_12KB];
        cfis1.read(result1);
        assertArrayEquals(subArray(testData, _12KB), result1);

        final byte result2[] = new byte[_12KB];
        cfis2.read(result2);
        assertArrayEquals(subArray(testData, _12KB), result2);
    }

    @MethodSource("data") @ParameterizedTest
    public void sharedCacheWritesInOrder(final Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamNonMarkableByteArrayInputStreamTest(cacheClass);
        final byte[] testData = generateRandomData(_64KB);
        final InputStream is = new MarkShieldInputStream(new UnsynchronizedByteArrayInputStream(testData));

        //first CachingFilterInputStream
        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(getNewCache(is));

        //read first 6KB
        final byte cfis1Part1[] = new byte[_6KB];
        cfis1.read(cfis1Part1);
        assertArrayEquals(subArray(testData, _6KB), cfis1Part1); //ensure first 6KB was read!

        //second CachingFilterInputStream wraps first CachingFilterInputStream
        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cfis1);

        //read first 32KB from second InputStream
        final byte cfis2Part1[] = new byte[_32KB];
        cfis2.read(cfis2Part1);
        assertArrayEquals(subArray(testData, _32KB), cfis2Part1); //ensure next 32KB was read!

        //interleave by reading another 6KB from first InputStream
        final byte cfis1Part2[] = new byte[_6KB];
        cfis1.read(cfis1Part2);
        assertArrayEquals(subArray(testData, _6KB, _6KB), cfis1Part2); //ensure first 6KB was read!
    }

    private byte[] consumeInputStream(final CachingFilterInputStream is) throws IOException {
        try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            baos.write(is);
            return baos.toByteArray();
        }
    }

    private byte[] generateRandomData(final int bytes) {
        final byte[] data = new byte[bytes];
        new Random().nextBytes(data);
        return data;
    }

    private byte[] subArray(final byte data[], final int len) {
        final byte newData[] = new byte[len];
        System.arraycopy(data, 0, newData, 0, len);
        return newData;
    }

    private byte[] subArray(final byte data[], final int offset, final int len) {
        final byte newData[] = new byte[len];
        System.arraycopy(data, offset, newData, 0, len);
        return newData;
    }
}
