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

import org.apache.commons.codec.binary.Hex;
import java.io.InputStream;
import org.apache.commons.codec.binary.Base64;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.XPathException;
import java.io.IOException;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class BinaryValueFromBinaryStringTest {

    @Test
    public void getInputStream() throws XPathException, IOException {

        final String testData = "test data";
        final String base64TestData = Base64.encodeBase64String(testData.getBytes()).trim();

        BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), base64TestData);


        try (final InputStream is = binaryValue.getInputStream();
             final FastByteArrayOutputStream baos = new FastByteArrayOutputStream()) {
            baos.write(is);
            assertArrayEquals(testData.getBytes(), baos.toByteArray());
        }
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