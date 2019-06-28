package org.exist.xquery.value;

import java.io.FilterOutputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class BinaryValueTypeTest {

    @Test
    public void verifyAndFormat_does_trim() throws XPathException {
        final String testValue = " HELLO \r\n";

        BinaryValueType binaryValueType = new TestableBinaryValueType(Type.BASE64_BINARY, Base64OutputStream.class);
        final String result = binaryValueType.verifyAndFormatString(testValue);

        assertEquals(testValue.trim(), result);
    }

    @Test
    public void verifyAndFormat_replaces_whiteSpace() throws XPathException {
        final String testValue = "HELLO WO RLD";

        BinaryValueType binaryValueType = new TestableBinaryValueType(Type.BASE64_BINARY, Base64OutputStream.class);
        final String result = binaryValueType.verifyAndFormatString(testValue);

        assertEquals(testValue.replaceAll("\\s", ""), result);
    }

    public class TestableBinaryValueType<T extends FilterOutputStream> extends BinaryValueType<T> {

        public TestableBinaryValueType(int xqueryType, Class<T> coder) {
            super(xqueryType, coder);
        }

        @Override
        public void verifyString(String str) throws XPathException {
        }

        @Override
        protected String formatString(String str) {
            return str;
        }
    }
}