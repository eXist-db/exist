package org.exist.xquery.value;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;
import org.exist.xquery.XPathException;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromBinaryStringTest {

    @Test
    public void getInputStream() throws XPathException, IOException {

        BinaryValueManager binaryValueManager = new MockBinaryValueManager();

        try{
            final String testData = "test data";

            BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), Base64.encodeBase64String(testData.getBytes()));

            InputStream is = binaryValue.getInputStream();

            int read = -1;
            byte buf[] = new byte[1024];
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            while((read = is.read(buf)) > -1) {
                baos.write(buf, 0, read);
            }

            assertArrayEquals(testData.getBytes(), baos.toByteArray());
        } finally {
            binaryValueManager.cleanupBinaryValueInstances();
        }
    }
}
