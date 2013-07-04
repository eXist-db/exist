package org.exist.xquery.value;

import java.io.FilterInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromInputStreamTest {

    @Test
    public void getInputStream() throws XPathException, IOException {

        BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try{
            final byte[] testData = "test data".getBytes();

            InputStream bais = new FilterInputStream(new ByteArrayInputStream(testData)) {
                @Override
                public boolean markSupported() {
                    return false;
                }
            };

            BinaryValue binaryValue = BinaryValueFromInputStream.getInstance(binaryValueManager, new Base64BinaryValueType(), bais);

            InputStream is = binaryValue.getInputStream();

            int read = -1;
            byte buf[] = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while((read = is.read(buf)) > -1) {
                baos.write(buf, 0, read);
            }

            assertArrayEquals(testData, baos.toByteArray());
        } finally {
            binaryValueManager.runCleanupTasks();
        }
    }
}