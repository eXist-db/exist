package org.exist.xquery.functions.util;

import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.StringValue;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author aretter
 */
public class BinaryToStringTest {

    @Test
    public void roundtrip() throws XPathException {
        final String value = "hello world";
        final String encoding = "UTF-8";

        TestableBinaryToString testable = new TestableBinaryToString(new MockXQueryContext(), null);

        final BinaryValue binary = testable.stringToBinary(value, encoding);
        StringValue result = testable.binaryToString(binary, encoding);

        assertEquals(value, result.getStringValue());
    }

    public class TestableBinaryToString extends BinaryToString {
        public TestableBinaryToString(XQueryContext context, FunctionSignature signature) {
            super(context, signature);
        }

        @Override
        public StringValue binaryToString(BinaryValue binary, String encoding) throws XPathException {
            return super.binaryToString(binary, encoding);
        }

        @Override
        public BinaryValue stringToBinary(String str, String encoding) throws XPathException {
            return super.stringToBinary(str, encoding);
        }
    }

    public class MockXQueryContext extends XQueryContext {
        public MockXQueryContext() {
            super();
        }

        @Override
        public String getCacheClass() {
        	return "org.exist.util.io.FileFilterInputStreamCache";
	        //return "org.exist.util.io.MemoryMappedFileFilterInputStreamCache";
	        //return "org.exist.util.io.MemoryFilterInputStreamCache";
        }

    }
}
