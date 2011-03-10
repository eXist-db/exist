package org.exist.xquery.value;

import org.apache.commons.codec.binary.Hex;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;
import org.exist.xquery.XPathException;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class BinaryValueFromBinaryStringTest {

    @Test
    public void getInputStream() throws XPathException, IOException {

        final String testData = "test data";
        final String base64TestData = Base64.encodeBase64String(testData.getBytes()).trim();

        BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), base64TestData);

        InputStream is = binaryValue.getInputStream();

        int read = -1;
        byte buf[] = new byte[1024];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        while((read = is.read(buf)) > -1) {
            baos.write(buf, 0, read);
        }

        assertArrayEquals(testData.getBytes(), baos.toByteArray());
    }

    @Test
    public void cast_base64_to_hexBinary() throws XPathException {

        final String testData = "testdata";
        final String expectedResult = Hex.encodeHexString(testData.getBytes()).trim();

        BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), Base64.encodeBase64String(testData.getBytes()));

        final AtomicValue result = binaryValue.convertTo(new HexBinaryValueType());

        assertEquals(expectedResult, result.getStringValue());
    }

    @Test
    public void cast_hexBinary_to_base64() throws XPathException {
        final String testData = "testdata";
        final String expectedResult = Base64.encodeBase64String(testData.getBytes()).trim();

        BinaryValue binaryValue = new BinaryValueFromBinaryString(new HexBinaryValueType(), Hex.encodeHexString(testData.getBytes()));

        final AtomicValue result = binaryValue.convertTo(new Base64BinaryValueType());

        assertEquals(expectedResult, result.getStringValue());
    }
}