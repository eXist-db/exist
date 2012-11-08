package org.exist.xquery.value;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64InputStream;
import org.exist.util.ConfigurationHelper;
import org.exist.xquery.XPathException;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;

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

    @Test
    public void verify_validBase64_passes_3() throws XPathException {
        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();
        base64Type.verifyString("aaa a");
    }

    @Test
    public void verify_validBase64_passes_large_string() throws XPathException, IOException {
        File home = ConfigurationHelper.getExistHome();
        File binaryFile = new File(home, "webapp/logo.jpg");

        InputStream is = null;
        ByteArrayOutputStream baos = null;
        String base64data = null;
        try {
            is = new Base64InputStream(new FileInputStream(binaryFile), true, -1, null);
            baos  = new ByteArrayOutputStream();
            byte buf[] = new byte[1024];
            int read = -1;
            while((read = is.read(buf)) > -1) {
                baos.write(buf, 0, read);
            }
            base64data = new String(baos.toByteArray());
        } finally {
            if(is != null) { is.close(); }
            if(baos != null) { baos.close(); }
        }

        assertNotNull(base64data);

        TestableBase64BinaryValueType base64Type = new TestableBase64BinaryValueType();

        base64Type.verifyString(base64data);
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