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
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.XPathException;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class BinaryValueFromBinaryStringTest {

    @Test
    void getInputStream() throws XPathException, IOException {

        final String testData = "test data";
        final String base64TestData = Base64.encodeBase64String(testData.getBytes()).trim();


        try (final BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), base64TestData);
             final InputStream is = binaryValue.getInputStream();
             final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
            baos.write(is);
            assertArrayEquals(testData.getBytes(), baos.toByteArray());
        }
    }

    @Test
    void castBase64ToHexBinary() throws XPathException, IOException {

        final String testData = "testdata";
        final String expectedResult = Hex.encodeHexString(testData.getBytes()).trim();

        try (final BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), Base64.encodeBase64String(testData.getBytes()))) {

            final AtomicValue result = binaryValue.convertTo(new HexBinaryValueType());

            assertEquals(expectedResult, result.getStringValue());
        }
    }

    @Test
    void castHexBinaryToBase64() throws XPathException, IOException {
        final String testData = "testdata";
        final String expectedResult = Base64.encodeBase64String(testData.getBytes()).trim();

        try (final BinaryValue binaryValue = new BinaryValueFromBinaryString(new HexBinaryValueType(), Hex.encodeHexString(testData.getBytes()))) {

            final AtomicValue result = binaryValue.convertTo(new Base64BinaryValueType());

            assertEquals(expectedResult, result.getStringValue());
        }
    }

    @Test
    void base64StreamBinaryTo() throws XPathException, IOException {
        try (final BinaryValue binaryValue = new BinaryValueFromBinaryString(new Base64BinaryValueType(), "yv4=");
            final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {

            binaryValue.streamBinaryTo(baos);

            final byte[] data = baos.toByteArray();
            assertEquals(2, data.length);
            assertEquals(0xCA, data[0] & 0xFF);
            assertEquals(0xFE, data[1] & 0xFF);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAFE", "cafe"})
    void hexStreamBinaryTo(final String hexString) throws XPathException, IOException {
        try (final BinaryValue binaryValue = new BinaryValueFromBinaryString(new HexBinaryValueType(), hexString);
             final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {

            binaryValue.streamBinaryTo(baos);

            final byte[] data = baos.toByteArray();
            assertEquals(2, data.length);
            assertEquals(0xCA, data[0] & 0xFF);
            assertEquals(0xFE, data[1] & 0xFF);
        }
    }
}
