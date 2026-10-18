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

import org.exist.xquery.XPathException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 *
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
class HexBinaryValueTypeTest {

    @Test
    void verifyNotMultipleOf2CharsFails() {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        assertThrows(XPathException.class, () ->
            hexType.verifyString("010010101"));
    }

    @Test
    void verifyMultipleOfCharsPasses() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        assertDoesNotThrow(() -> hexType.verifyString("01001010"));
    }

    @Test
    void verifyNotValidCharsFails() {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        assertThrows(XPathException.class, () ->
            hexType.verifyString("true"));
    }

    @Test
    void verifyValidCharsPasses() throws XPathException {
        TestableHexBinaryValueType hexType = new TestableHexBinaryValueType();
        assertDoesNotThrow(() -> hexType.verifyString("0fb7"));
    }

    @Test
    void formatUpperCases() throws XPathException {
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
