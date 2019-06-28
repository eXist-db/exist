package org.exist.util.io;

import java.util.Collection;
import java.util.Arrays;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Random;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * Test cases for CachingFilterInputStream
 *
 * @version 1.0
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(value = ParallelParameterized.class)
public class CachingFilterInputStreamTest {

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
            {"MemoryFilterInputStreamCache", MemoryFilterInputStreamCache.class},
            {"MemoryMappedFileFilterInputStreamCache", MemoryMappedFileFilterInputStreamCache.class},
            {"FileFilterInputStreamCache", FileFilterInputStreamCache.class}
        });
    }

    @Parameter
    public String cacheName;

    @Parameter(value = 1)
    public Class<FilterInputStreamCache> cacheClass;

    public FilterInputStreamCache getNewCache(InputStream is) throws InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        Constructor ctor = cacheClass.getDeclaredConstructor(InputStream.class);
        ctor.setAccessible(true);
        return (FilterInputStreamCache) ctor.newInstance(is);
    }

    @Test
    public void readByte() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test(expected = IOException.class)
    public void readByte_onClosedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData[0], cfis.read());

        cfis.close();

        //should cause IOException
        cfis.read();
    }

    @Test
    public void readByte_pastEndOfStream_fromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "he";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readByte_pastEndOfStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readByte_allFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test(expected = IOException.class)
    public void readBytes_onClosedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        byte result[] = new byte[2];
        cfis.read(result);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.close();

        //should cause IOException
        cfis.read(result);
    }

    @Test
    public void readBytes_pastEndOfStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_pastEndOfStream_fromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_allFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_partFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_withZeroOffset_allFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_withZeroOffset_partFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void readBytes_withOffsetAndLength_allFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void skip() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void skip_partFromCache() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test(expected = IOException.class)
    public void skip_onClosedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        //should cause IOException
        cfis.skip(1);
    }

    @Test
    public void skip_negativeBytes() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //should cause IOException
        long skipped = cfis.skip(-1);
        assertEquals(0, skipped);
    }

    @Test
    public void skip_correctlyAdjustsSrcOffset_onSharedCache() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new FastByteArrayInputStream(testData);
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

    @Test
    public void available_onClosedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @Test
    public void available_onEmptyStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        InputStream is = new FastByteArrayInputStream(new byte[]{});

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @Test
    public void available_onUnCachedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        assertEquals(testData.length, cfis.available());
    }

    @Test
    public void available_onPartiallyReadStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(is));

        //read first 2 bytes
        cfis.read();
        cfis.read();

        assertEquals(testData.length - 2, cfis.available());
    }

    @Test
    public void available_onPartiallyCachedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void available_onOffsetPartiallyCachedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void available_onCachedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void available_onOffsetCachedStream() throws IOException, InstantiationException, IllegalAccessException, NoSuchMethodException, IllegalArgumentException, InvocationTargetException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void sharedReferences() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        final InputStream is = new FastByteArrayInputStream(testData);

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

    @Test
    public void tika116_like() throws IOException, InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final byte testData[] = generateRandomBytes(2149);//Files.readAllBytes(Paths.get("/tmp/test2.pdf"));

        final InputStream is = new FastByteArrayInputStream(testData);

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
}
