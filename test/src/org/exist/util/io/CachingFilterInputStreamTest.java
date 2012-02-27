package org.exist.util.io;

import java.io.FileNotFoundException;
import org.junit.runners.Parameterized.Parameters;
import java.util.Collection;
import java.util.Arrays;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;

/**
 * Test cases for CachingFilterInputStream
 *
 * @version 1.0
 *
 * @author Adam Retter <adam.retter@googlemail.com>
 */
@RunWith(value = Parameterized.class)
public class CachingFilterInputStreamTest {

    @Parameters
    public static Collection data() throws FileNotFoundException, IOException {
        Object[][] data = new Object[][] {
            { MemoryFilterInputStreamCache.class },
            { MemoryMappedFileFilterInputStreamCache.class },
            { FileFilterInputStreamCache.class }
        };
        return Arrays.asList(data);
    }

    private final Class<FilterInputStreamCache> cacheClass;
    public CachingFilterInputStreamTest(Class<FilterInputStreamCache> cacheClass) {
        this.cacheClass = cacheClass;
    }

    public FilterInputStreamCache getNewCache() throws InstantiationException, IllegalAccessException {
        return cacheClass.newInstance();
    }

    @Test
    public void readByte() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readByte_onClosedStream() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        assertEquals(testData[0], cfis.read());

        cfis.close();

        //should cause IOException
        cfis.read();
    }

    @Test
    public void readByte_pastEndOfStream_fromCache() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "he";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readByte_pastEndOfStream() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        //read all the bytes upto end of stream
        int b = -1;
        int testDataOffset = 0;
        while((b = cfis.read()) > -1) {
            assertEquals(testData[testDataOffset++], b);
        }

        //read byte past end of stream
        b = cfis.read();
        assertEquals(-1, b);
    }

    @Test
    public void readByte_allFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_onClosedStream() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);
        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        byte result[] = new byte[2];
        cfis.read(result);
        assertArrayEquals(subArray(testData, 2), result);

        cfis.close();

        //should cause IOException
        cfis.read(result);
    }

    @Test
    public void readBytes_pastEndOfStream() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        byte result[] = new byte[testData.length];
        int read = cfis.read(result);
        assertEquals(testData.length, read);
        assertArrayEquals(testData, result);

        byte endOfStreamResult[] = new byte[testData.length];
        read = cfis.read(endOfStreamResult);
        assertEquals(-1, read);
        assertArrayEquals(new byte[] {0,0,0,0,0,0,0,0,0,0}, endOfStreamResult);
    }
    
    @Test
    public void readBytes_pastEndOfStream_fromCache() throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_allFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_partFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_withZeroOffset_allFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "hello";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_withZeroOffset_partFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void readBytes_withOffsetAndLength_allFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void skip() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void skip_partFromCache() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void skip_onClosedStream() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        cfis.close();

        //should cause IOException
        cfis.skip(1);
    }

    @Test
    public void skip_negativeBytes() throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        //should cause IOException
        long skipped = cfis.skip(-1);
        assertEquals(0, skipped);
    }

    @Test
    public void available_onClosedStream()  throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @Test
    public void available_onEmptyStream()  throws IOException, InstantiationException, IllegalAccessException {

        InputStream is = new ByteArrayInputStream(new byte[]{});

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        cfis.close();

        assertEquals(0, cfis.available());
    }

    @Test
    public void available_onUnCachedStream()  throws IOException, InstantiationException, IllegalAccessException {
        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        assertEquals(testData.length, cfis.available());
    }

    @Test
    public void available_onPartiallyReadStream()  throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        //read first 2 bytes
        cfis.read();
        cfis.read();

        assertEquals(testData.length - 2 , cfis.available());
    }

    @Test
    public void available_onPartiallyCachedStream()  throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void available_onOffsetPartiallyCachedStream()  throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

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
    public void available_onCachedStream()  throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        for(int i = 0; i < testData.length; i++) {
            cfis.read();
        }

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length, cfis.available());
    }

    @Test
    public void available_onOffsetCachedStream()  throws IOException, InstantiationException, IllegalAccessException {

        final String testString = "helloWorld";
        final byte testData[] = testString.getBytes();

        InputStream is = new ByteArrayInputStream(testData);

        CachingFilterInputStream cfis = new CachingFilterInputStream(getNewCache(), is);

        //read first 2 bytes
        cfis.read();
        cfis.read();

        //mark for later reset
        cfis.mark(Integer.MAX_VALUE);

        for(int i = 0; i < testData.length - 2; i++) {
            cfis.read();
        }

        //return to the start of the stream
        cfis.reset();

        assertEquals(testData.length - 2, cfis.available());
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
}