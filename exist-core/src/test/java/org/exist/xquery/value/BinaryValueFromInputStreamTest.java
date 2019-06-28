package org.exist.xquery.value;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FastByteArrayInputStream;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.XPathException;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class BinaryValueFromInputStreamTest {

    @Test
    public void getInputStream() throws XPathException, IOException {
        final byte[] testData = "test data".getBytes();

        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();
        try(final InputStream bais = new UnmarkableByteArrayInputStream(testData)) {

            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);

            try(final InputStream is = binaryValue.getInputStream()) {
                final byte[] actual = readAll(is);
                assertArrayEquals(testData, actual);
            }
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test
    public void repeated_getInputStream_sameUnderlyingCache() throws XPathException, IOException {
        final byte[] testData = "test data".getBytes();

        BinaryValue binaryValue1 = null;
        BinaryValue binaryValue2 = null;

        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();
        try(final InputStream bais = new UnmarkableByteArrayInputStream(testData)) {

            binaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);  //binValue1.sharedRefCount==1

            try(final InputStream is = binaryValue1.getInputStream()) {   //binValue1.sharedRefCount==2
                final byte[] actual = readAll(is);
                assertArrayEquals(testData, actual);
            }  //binValue1.sharedRefCount==1

            assertFalse(binaryValue1.isClosed());

            // second binary from InputStream of first binary
            try(final InputStream is = binaryValue1.getInputStream()) {   //binValue1.sharedRefCount==2
                binaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), is);     //binValue1.sharedRefCount==3

                try(final InputStream is2 = binaryValue2.getInputStream()) {
                    final byte[] actual = readAll(is2);
                    assertArrayEquals(testData, actual);
                }

                assertFalse(binaryValue2.isClosed());
            }
//binValue1.sharedRefCount==2
            assertFalse(binaryValue1.isClosed());

        } finally {
            binaryValueManager.runCleanupTasks();

            assertTrue(binaryValue2.isClosed());
            assertTrue(binaryValue1.isClosed());
        }
    }

    @Test(expected = IOException.class)
    public void filter_withoutIncrementReferenceCountFails() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {
            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis = binaryValue.getInputStream();

            // create a filter over the first BinaryValue, with no reference count increment
            final InputStream fis = new BinaryValueFilteringInputStream(bvis, false);
            final BinaryValue filteredBinaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis);

            // we now destroy the filtered binary value, just as it would be if it went out of scope from popLocalVariables#popLocalVariables.
            // It should close the original binary value, as we have not incremented the reference count!
            filteredBinaryValue.close();
            assertTrue(filteredBinaryValue.isClosed());
            fis.close();
            bvis.close();
            assertTrue(binaryValue.isClosed());

            // we should not be able to read from the origin binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {

                // this should throw an IOException
                binaryValue.streamBinaryTo(baos);
            }

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test
    public void filter_withIncrementReferenceCount() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {
            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis = binaryValue.getInputStream();

            // create a filter over the first BinaryValue, and reference count increment
            final InputStream fis = new BinaryValueFilteringInputStream(bvis, true);
            final BinaryValue filteredBinaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis);

            // we now destroy the filtered binary value, just as it would if it went out of scope from popLocalVariables#popLocalVariables.
            // It should not close the original binary value, as BinaryValueFilteringInputStream increased the reference count.
            filteredBinaryValue.close();
            assertTrue(filteredBinaryValue.isClosed());
            assertFalse(binaryValue.isClosed());

            // we should still be able to read from the origin binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                binaryValue.streamBinaryTo(baos);

                assertArrayEquals(testData, baos.toByteArray());
            }

            // finally close the original binary value
            fis.close();
            bvis.close();
            binaryValue.close();
            assertTrue(binaryValue.isClosed());

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test(expected = IOException.class)
    public void multiFilter_withoutIncrementReferenceCountFails() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {
            final BinaryValue binaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis1 = binaryValue1.getInputStream();

            final BinaryValue binaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis2 = binaryValue2.getInputStream();

            // create a filter over both BinaryValues, with no reference count increment
            final InputStream fis = new MultiBinaryValueFilteringInputStream(new InputStream[]{bvis1, bvis2}, false);
            final BinaryValue filteredBinaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis);

            // we now destroy the filtered binary value, just as it would be if it went out of scope from popLocalVariables#popLocalVariables.
            // It should close the original binary values, as we have not incremented the reference counts!
            filteredBinaryValue.close();
            assertTrue(filteredBinaryValue.isClosed());
            fis.close();
            bvis2.close();
            assertTrue(binaryValue2.isClosed());
            bvis1.close();
            assertTrue(binaryValue1.isClosed());

            // we should not be able to read from the origin binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {

                // this should throw an IOException
                binaryValue1.streamBinaryTo(baos);
            }

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test
    public void multiFilter_withIncrementReferenceCount() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData1 = "test data".getBytes();
        final byte[] testData2 = "second test data".getBytes();

        try (final InputStream bais1 = new FastByteArrayInputStream(testData1);
                final InputStream bais2 = new FastByteArrayInputStream(testData2)) {

            final BinaryValue binaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais1);
            final InputStream bvis1 = binaryValue1.getInputStream();

            final BinaryValue binaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais2);
            final InputStream bvis2 = binaryValue2.getInputStream();

            // create a filter over both BinaryValues, and reference count increment
            final InputStream fis = new MultiBinaryValueFilteringInputStream(new InputStream[]{bvis1, bvis2}, true);
            final BinaryValue filteredBinaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis);

            // we now destroy the filtered binary value, just as it would be if it went out of scope from popLocalVariables#popLocalVariables.
            // It should not close the original binary values, as MultiBinaryValueFilteringInputStream increased the reference count.
            filteredBinaryValue.close();
            assertTrue(filteredBinaryValue.isClosed());
            assertFalse(binaryValue2.isClosed());
            assertFalse(binaryValue1.isClosed());

            // we should still be able to read from the origin binary value2!
            try (final FastByteArrayOutputStream baos2 = new FastByteArrayOutputStream()) {
                binaryValue2.streamBinaryTo(baos2);

                assertArrayEquals(testData2, baos2.toByteArray());
            }

            // we should still be able to read from the original binary value1!
            try (final FastByteArrayOutputStream baos1 = new FastByteArrayOutputStream()) {
                binaryValue1.streamBinaryTo(baos1);

                assertArrayEquals(testData1, baos1.toByteArray());
            }

            // finally close the original binary values
            fis.close();
            bvis2.close();
            binaryValue2.close();
            assertTrue(binaryValue2.isClosed());

            bvis1.close();
            binaryValue1.close();
            assertTrue(binaryValue1.isClosed());

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test(expected = IOException.class)
    public void filterFilter_withoutIncrementReferenceCountFails() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {
            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis = binaryValue.getInputStream();

            // create a filter over the first BinaryValue, with no reference count increment
            final InputStream fis1 = new BinaryValueFilteringInputStream(bvis, false);
            final BinaryValue filteredBinaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis1);

            // create a second filter over the first filter, with no reference count increment
            final InputStream fbvis = filteredBinaryValue1.getInputStream();
            final InputStream fis2 = new BinaryValueFilteringInputStream(fbvis, false);
            final BinaryValue filteredBinaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis2);

            // we now destroy the second filtered binary value, just as it would if it went out of scope from popLocalVariables#popLocalVariables.
            // It should close the first filtered binary value and original binary value, as we have not incremented the reference counts!
            filteredBinaryValue2.close();
            fis2.close();
            fbvis.close();
            assertTrue(filteredBinaryValue2.isClosed());
            assertTrue(filteredBinaryValue1.isClosed());

            fis1.close();
            bvis.close();
            assertTrue(binaryValue.isClosed());

            // we should not be able to read from the first filtered binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {

                // this should throw an IOException
                filteredBinaryValue1.streamBinaryTo(baos);
            }

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test
    public void filterFilter_withIncrementReferenceCount() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {
            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);
            final InputStream bvis = binaryValue.getInputStream();

            // create a filter over the first BinaryValue, and reference count increment
            final InputStream fis1 = new BinaryValueFilteringInputStream(bvis, true);
            final BinaryValue filteredBinaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis1);

            // create a second filter over the first filter, and reference count increment
            final InputStream fbvis = filteredBinaryValue1.getInputStream();
            final InputStream fis2 = new BinaryValueFilteringInputStream(fbvis, true);
            final BinaryValue filteredBinaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis2);

            // we now destroy the second filtered binary value, just as it would if it went out of scope from popLocalVariables#popLocalVariables.
            // It should not close the filtered binary value or original binary value, as BinaryValueFilteringInputStream increased the reference count.
            filteredBinaryValue2.close();
            assertTrue(filteredBinaryValue2.isClosed());
            assertFalse(filteredBinaryValue1.isClosed());
            assertFalse(binaryValue.isClosed());

            // we should still be able to read from the filtered binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                filteredBinaryValue1.streamBinaryTo(baos);

                assertArrayEquals(testData, baos.toByteArray());
            }

            // we now destroy the first filtered binary value, just as it would if it went out of scope from popLocalVariables#popLocalVariables.
            // It should not close the original binary value, as BinaryValueFilteringInputStream increased the reference count.
            fis2.close();
            filteredBinaryValue1.close();
            assertTrue(filteredBinaryValue1.isClosed());

            // we should still be able to read from the origin binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                binaryValue.streamBinaryTo(baos);

                assertArrayEquals(testData, baos.toByteArray());
            }

            // finally close the original binary value
            bvis.close();
            binaryValue.close();
            assertTrue(binaryValue.isClosed());

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    @Test
    public void multiFilterFilter_withIncrementReferenceCount() throws IOException, XPathException {
        final BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        final byte[] testData = "test data".getBytes();

        try (final InputStream bais = new FastByteArrayInputStream(testData)) {

            final BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);

            // create a first filter over the first BinaryValue, and reference count increment
            final InputStream fis1 = new BinaryValueFilteringInputStream(binaryValue.getInputStream(), true);
            final BinaryValue filteredBinaryValue1 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis1);

            // create a second filter over the first BinaryValue, and reference count increment
            final InputStream fis2 = new BinaryValueFilteringInputStream(binaryValue.getInputStream(), true);
            final BinaryValue filteredBinaryValue2 = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), fis2);


            // create a multi filter over both filters
            final InputStream mfis = new MultiBinaryValueFilteringInputStream(new InputStream[]{filteredBinaryValue1.getInputStream(), filteredBinaryValue2.getInputStream()}, true);
            final BinaryValue multiFilteredBinaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), mfis);

            // we now destroy the multi filtered binary value, just as it would be if it went out of scope from popLocalVariables#popLocalVariables.
            // It should not close the filtered or original binary values, as MultiBinaryValueFilteringInputStream increased the reference count.
            multiFilteredBinaryValue.close();
            assertTrue(multiFilteredBinaryValue.isClosed());
            assertFalse(filteredBinaryValue2.isClosed());
            assertFalse(filteredBinaryValue1.isClosed());
            assertFalse(binaryValue.isClosed());


            // we should still be able to read from the filtered binary value2!
            try (final FastByteArrayOutputStream baos2 = new FastByteArrayOutputStream()) {
                filteredBinaryValue2.streamBinaryTo(baos2);

                assertArrayEquals(testData, baos2.toByteArray());
            }

            mfis.close();
            filteredBinaryValue2.close();
            assertTrue(filteredBinaryValue2.isClosed());

            // we should still be able to read from the filtered binary value1!
            try (final FastByteArrayOutputStream baos1 = new FastByteArrayOutputStream()) {
                filteredBinaryValue1.streamBinaryTo(baos1);

                assertArrayEquals(testData, baos1.toByteArray());
            }

            filteredBinaryValue1.close();
            assertTrue(filteredBinaryValue1.isClosed());

            // we should still be able to read from the original binary value!
            try (final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
                binaryValue.streamBinaryTo(baos);

                assertArrayEquals(testData, baos.toByteArray());
            }

            // finally close the original binary value
            fis2.close();
            fis1.close();
            binaryValue.close();
            assertTrue(binaryValue.isClosed());

        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }

    private static byte[] readAll(final InputStream is) throws IOException {
        try(final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
            baos.write(is);
            return baos.toByteArray();
        }
    }

    private static class UnmarkableByteArrayInputStream extends FastByteArrayInputStream {
        public UnmarkableByteArrayInputStream(final byte[] buf) {
            super(buf);
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }

    private static class BinaryValueFilteringInputStream extends FilterInputStream {
        public BinaryValueFilteringInputStream(final InputStream inputStream, final boolean incrementReferenceCount) {
            super(inputStream);
            if(incrementReferenceCount && inputStream instanceof CachingFilterInputStream) {
                final CachingFilterInputStream cfis = ((CachingFilterInputStream)inputStream);

                // increment shared references by one, as this filter is sharing the underlying input stream of the cache
                cfis.incrementSharedReferences();
            }
        }
    }

    private static class MultiBinaryValueFilteringInputStream extends FilterInputStream {
        final InputStream[] inputStreams;

        public MultiBinaryValueFilteringInputStream(final InputStream[] inputStreams, final boolean incrementReferenceCount) {
            super(inputStreams[0]);
            this.inputStreams = inputStreams;
            if(incrementReferenceCount) {
                for(final InputStream inputStream : inputStreams) {
                    if (inputStream instanceof  CachingFilterInputStream) {
                        final CachingFilterInputStream cfis = ((CachingFilterInputStream)inputStream);

                        // increment shared references by one, as this filter is sharing the underlying input stream of the cache
                        cfis.incrementSharedReferences();
                    }
                }
            }
        }

        @Override
        public void close() throws IOException {
            IOException firstException = null;

            for(int i = inputStreams.length - 1; i > -1; i--) {
                try {
                    inputStreams[i].close();
                } catch(final IOException e) {
                    if(firstException == null) {
                        firstException = e;
                    }
                }
            }

            if(firstException != null) {
                throw new IOException("first exception on close", firstException);
            }
        }
    }
}