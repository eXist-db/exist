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

import org.exist.xquery.XPathException;
import org.exist.xquery.functions.integer.IntegerPicture;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class IntegerPictureTest {

    @Test(expected = XPathException.class) public void pictureEmpty() throws XPathException {
        IntegerPicture.fromString("");
    }

    @Test public void picture() throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString("123,2345,34567,6789;00;more");
        assertEquals("primary=123,2345,34567,6789;00::modifier=more::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,5,,)::group=Group(0,4,;)::group=Group(0,2,)", picture.toString());
        picture = IntegerPicture.fromString("#23,345,567,789;more");
        assertEquals("primary=#23,345,567,789::modifier=more::regular=true::group=Group(0,3,,)", picture.toString());
        picture = IntegerPicture.fromString("123;345,567,789;more");
        assertEquals("primary=123;345,567,789::modifier=more::regular=false::group=Group(0,3,;)::group=Group(0,3,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("123,2345,567,789;more");
        assertEquals("primary=123,2345,567,789::modifier=more::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("#89;more");
        assertEquals("primary=#89::modifier=more::regular=true::group=Group(1,2,)", picture.toString());
        picture = IntegerPicture.fromString("#89|123;more");
        assertEquals("primary=#89|123::modifier=more::regular=true::group=Group(0,3,|)", picture.toString());
    }

    private String fmt(String pictureString, long value) throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString(pictureString);
        return picture.formatInteger(BigInteger.valueOf(value), "en");
    }

    @Test public void format() throws XPathException {
        assertEquals("0", fmt("1", 0L));
        assertEquals("00", fmt("12", 0L));
        assertEquals("000", fmt("123", 0L));
        assertEquals("0,0", fmt("1,1", 0L));
        assertEquals("0,1", fmt("1,1", 1L));
    }

    @Test public void formatNegative() throws XPathException {
        assertEquals("-1", fmt("1", -1L));
        assertEquals("-01", fmt("12", -1L));
        assertEquals("-001", fmt("123", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
    }

    @Test public void formatRegular() throws XPathException {
        assertEquals("1,23,45,67,89", fmt("12,34", 123456789L));
        assertEquals("1,23,45,67,89", fmt("12,34,56", 123456789L));
        assertEquals("12345,67?89", fmt("12,34?56", 123456789L));
        assertEquals("123456,789", fmt("12,345", 123456789L));
        assertEquals("00,089", fmt("12,345", 89L));
    }

    @Test public void formatOptional() throws XPathException {
        assertEquals("009", fmt("#234", 9L));
        assertEquals("123456789", fmt("#234", 123456789L));
        assertEquals("000,0009", fmt("#234,1234", 9L));
        assertEquals("0009", fmt("####,1234", 9L));
        assertEquals("0,0009", fmt("###4,1234", 9L));
    }

    @Test public void formatDefaultFamily() throws XPathException {
        StringBuilder sb = new StringBuilder();
        sb.append("#234567");
        assertEquals(7, sb.length());
        assertEquals("000009", fmt(sb.toString(), 9L));
    }

    @Test public void formatNonDefaultDigitFamilies() throws XPathException {
        for (int family : new int[]{0x104a0,0x660,0x30,0x1e950,0x1e2f0}) {
            StringBuilder sb = new StringBuilder();
            sb.append("#");
            for (int i = 2; i < 5; i++) {
                char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            sb.append("|");
            for (int i = 5; i < 8; i++) {
                char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            String formatted = fmt(sb.toString(), 149L);
            System.out.println("Formatted:" + formatted);
            assertEquals(6 * Character.charCount(family) + 1, formatted.length());
            int pos = 0;
            int codePoint;
            for (int offset : new int[]{0, 0, 0}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
            codePoint = Character.codePointAt(formatted, pos);
            assertEquals(Character.codePointAt("|",0), codePoint);
            pos += 1;
            for (int offset : new int[]{1, 4, 9}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
        }
    }

    @Test public void conflictingDigitFamilies() throws XPathException {
        StringBuilder sb = new StringBuilder();
        for (int family : new int[]{0x104a0,0x30}) {
            char[] chars = Character.toChars(family + 3);
            sb.append(chars);
            sb.append(",");
        }
        try {
            String formatted = fmt(sb.toString(), 9L);
            fail("Conflicting digit families should throw an exception");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("multiple digit families"));
        }
    }

    @Test public void optionalSignsAfterMandatorySigns() throws XPathException {
        assertEquals("0|005", fmt("##|#3|456", 5L));
        assertEquals("0|05", fmt("##|#3|45", 5L));
        assertEquals("5|67|89", fmt("##|#3|45", 56789L));

        try {
            fmt("12,#45", 0L);
            fail("The picture " + "12,#45" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("optional digit after mandatory"));
        }

        try {
            fmt("##|3#|45", 0L);
            fail("The picture " + "##|3#|45" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }

        try {
            fmt("1#", 0L);
            fail("The picture " + "1#" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }

    }

    @Test public void separatorAtEndIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("1+", 0L);
            fail("The picture " + "1+" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }
    }

    @Test public void separatorAtStartIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("|1", 0L);
            fail("The picture " + "+1" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }
    }

    @Test public void multiSeparator() throws XPathException {
        try {
            assertEquals("0|005", fmt("#3||456", 5L));
            fail("The picture " + "#3||456" + " should not be valid.");
        } catch (XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern at 3"));
        }
    }

    @Test public void nonDigitFormat() throws XPathException {
        assertEquals("0|005", fmt("A", 5L));
    }

}
