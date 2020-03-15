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
