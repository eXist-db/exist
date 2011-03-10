package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class Base64BinaryValueTypeTest {

    @Test(expected=XPathException.class)
    public void verify_invalidBase64_fails() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("=aaabbcd");
    }

    @Test(expected=XPathException.class)
    public void verify_invalidBase64_fails_2() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("frfhforlksid745323==");
    }

    @Test
    public void verify_validBase64_passes() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("aaabbcd=");
    }

    @Test
    public void verify_validBase64_passes_2() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("dGVzdCBkYXRh");
    }

    public class TestableBase64BinaryValueType extends Base64BinaryValueType {
        @Override
        public void verifyString(String str) throws XPathException {
            super.verifyString(str);
        }

        @Override
        protected String formatString(String str) {
            return super.formatString(str);
        }
    }
}