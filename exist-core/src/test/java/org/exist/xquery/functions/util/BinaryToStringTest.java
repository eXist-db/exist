/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.util;

import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.StringValue;
import org.junit.Test;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;

/**
 *
 * @author aretter
 */
public class BinaryToStringTest {

    @Test
    public void roundTrip() throws XPathException {
        final String value = "hello world";
        final Charset encoding = StandardCharsets.UTF_8;

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
        public StringValue binaryToString(BinaryValue binary, Charset encoding) throws XPathException {
            return super.binaryToString(binary, encoding);
        }

        @Override
        public BinaryValue stringToBinary(String str, Charset encoding) throws XPathException {
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
