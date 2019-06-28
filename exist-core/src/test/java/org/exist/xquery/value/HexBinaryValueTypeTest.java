package org.exist.xquery.value;

import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class HexBinaryValueTypeTest {
    
    @Test(expected=XPathException.class)
    public void verify_notMultipleOf2Chars_fails() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        hexType.verifyString("010010101");
    }

    @Test
    public void verify_multipleOfChars_passes() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        hexType.verifyString("01001010");
    }

    @Test(expected=XPathException.class)
    public void verify_notValidChars_fails() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        hexType.verifyString("true");
    }

    @Test
    public void verify_validChars_passes() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        hexType.verifyString("0fb7");
    }

    @Test
    public void format_upperCases() throws XPathException {
        final String hexString = "0fb7";

        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        final String result = hexType.formatString(hexString);

        assertEquals(hexString.toUpperCase(), result);
    }

    public class TestableHexBinaryValueType extends HexBinaryValueType {
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
