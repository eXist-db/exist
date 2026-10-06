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

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import java.util.Collection;
import java.util.Arrays;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Test cases for CachingFilterInputStream
 *
 * @version 1.0
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
public class CachingFilterInputStreamTest {

    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"MemoryFilterInputStreamCache", MemoryFilterInputStreamCache.class},
            {"MemoryMappedFileFilterInputStreamCache", MemoryMappedFileFilterInputStreamCache.class},
            {"FileFilterInputStreamCache", FileFilterInputStreamCache.class}
        });
    }
    public String cacheName;
    public Class<FilterInputStreamCache> cacheClass;

    public FilterInputStreamCache getNewCache(InputStream is) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Constructor ctor = cacheClass.getDeclaredConstructor(InputStream.class);
        ctor.setAccessible(true);
        return (FilterInputStreamCache) ctor.newInstance(is);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readByte(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readByteOnClosedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData[0], cfis.read());

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.read());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytePastEndOfStreamFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "he";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.mark(Integer.MAX_VALUE);

        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());

        cfis.reset();

        assertEquals(testData[0], cfis.read());
        assertEquals(testData[1], cfis.read());

        //read byte past end of cache
        int b = cfis.read();
        assertEquals(-1, b);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytePastEndOfStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readByteAllFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytes(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesOnClosedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        byte result[] = new byte[2];
        cfis.read(result);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.read(result));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesPastEndOfStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        byte result[] = new byte[testData.length];
        int read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);

        byte endOfStreamResult[] = new byte[testData.length];
        read = cfis.read(endOfStreamResult);
        assertEquals(-1, read);
        assertArrayEquals(new byte[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0}, endOfStreamResult);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesPastEndOfStreamFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.mark(Integer.MAX_VALUE);

        //read first two bytes from stream
        byte result[] = new byte[2];
        int read = cfis.read(result);
        assertEquals(2, read);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.reset();

        //read all bytes from cache and src, +1 past end of stream
        byte endOfStreamResult[] = new byte[testData.length + 1];
        read = cfis.read(endOfStreamResult);
        byte expectedResult[] = new byte[testData.length + 1];
        System.arraycopy(testData, 0, expectedResult, 0, testData.length);
        assertEquals(testData.length, read);
        assertArrayEquals(expectedResult, endOfStreamResult);

        //2nd attempt to read past end of stream
        read = cfis.read(result);
        assertEquals(-1, read);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesAllFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesPartFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesWithZeroOffsetAllFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesWithZeroOffsetPartFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void readBytesWithOffsetAndLengthAllFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void skip(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void skipPartFromCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void skipOnClosedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException, IOException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        //should cause IOException
        assertThrows(IOException.class, () -> cfis.skip(1));
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void skipNegativeBytes(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //should cause IOException
        long skipped = cfis.skip(-1);
        assertEquals(0, skipped);
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void skipCorrectlyAdjustsSrcOffsetOnSharedCache(String cacheName, Class<FilterInputStreamCache> cacheClass) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);
        final FilterInputStreamCache cache = getNewCache(is);

        final CachingFilterInputStream cfis1 = new CachingFilterInputStream(cache);
        final CachingFilterInputStream cfis2 = new CachingFilterInputStream(cache);

        assertEquals(0, cfis1.offset());
        final long skipped1 = cfis1.skip(5);
        assertEquals(5, skipped1);
        assertEquals(5, cfis1.offset());

        assertEquals(0, cfis2.offset());
        final long skipped2 = cfis2.skip(5);
        assertEquals(5, skipped2);
        assertEquals(5, cfis2.offset());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnClosedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnEmptyStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        InputStream is = new UnsynchronizedByteArrayInputStream(new byte[]{});

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnUnCachedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnPartiallyReadStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read first 2 bytes
        cfis.read();
        cfis.read();

        assertEquals(testData.length - 2, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnPartiallyCachedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        //read first 2 bytes
        cfis.read();
        cfis.read();

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnOffsetPartiallyCachedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnCachedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        for (int i = 0; i < testData.length; i++) {
            cfis.read();
        }

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length, cfis.available());
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void availableOnOffsetCachedStream(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

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

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void sharedReferences(String cacheName, Class<FilterInputStreamCache> cacheClass) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        // increment shared references (will now be 2)
        cfis.incrementSharedReferences();

        // close should not close as we just incremented the shared references
        cfis.close();

        //read first 2 bytes
        cfis.read();
        cfis.read();

        // close the second time, should actually close, as shared references will now be zero
        cfis.close();

        try {
            cfis.read();
            fail("Should not be able to read after shared references reach zero");
        } catch(final IOException ioe) {
            // no op, we expected the IOException
        }
    }

    @MethodSource("data") @ParameterizedTest(name = "{0}")
    public void tika116Like(String cacheName, Class<FilterInputStreamCache> cacheClass) throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        initCachingFilterInputStreamTest(cacheName, cacheClass);
        final byte testData[] = generateRandomBytes(2149);//Files.readAllBytes(Paths.get("/tmp/test2.pdf"));

        final InputStream is = new UnsynchronizedByteArrayInputStream(testData);

        final CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));
        cfis.mark(0);

        // Now do as Apache Tika 1.16 does...
        cfis.mark(8);
        int b = cfis.read();
        assertEquals(testData[0], (byte)b);

        cfis.reset();
        cfis.mark(1024);
        final byte[] buf1 = new byte[1024];
        int read = cfis.read(buf1);
        assertEquals(1024, read);
        assertArrayEquals(subArray(testData, 1024), buf1);

        cfis.reset();
        cfis.mark(4);
        b = cfis.read();
        assertEquals(testData[0], (byte)b);

        cfis.reset();
        cfis.mark(65536);
        final byte[] buf2 = new byte[65536];
        read = cfis.read(buf2);
        assertEquals(2149, read);
        assertArrayEquals(subArray(testData, 2149), subArray(buf2, 2149));

        cfis.reset();
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

    private byte[] generateRandomBytes(final int len) {
        final byte bytes[] = new byte[len];
        final Random random = new Random();
        random.nextBytes(bytes);
        return bytes;
    }

    public void initCachingFilterInputStreamTest(String cacheName, Class<FilterInputStreamCache> cacheClass) {
        this.cacheName = cacheName;
        this.cacheClass = cacheClass;
    }
}
