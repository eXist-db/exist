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