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

package org.exist.xquery.functions.integer;

import com.ibm.icu.text.RuleBasedNumberFormat;
import com.ibm.icu.text.UnicodeSet;
import org.exist.util.CodePointString;
import org.exist.xquery.XPathException;
import org.junit.Test;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class IntegerPictureTest {

    @Test(expected = XPathException.class)
    public void pictureEmpty() throws XPathException {
        IntegerPicture.fromString("");
    }

    @Test
    public void picture() throws XPathException {
        IntegerPicture picture = IntegerPicture.fromString("123,2345,34567,6789;00;c(variation)t");
        assertEquals("primary=123,2345,34567,6789;00::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,5,,)::group=Group(0,4,;)::group=Group(0,2,)", picture.toString());
        picture = IntegerPicture.fromString("#23,345,567,789;c(variation)t");
        assertEquals("primary=#23,345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(0,3,,)", picture.toString());
        picture = IntegerPicture.fromString("123;345,567,789;c(variation)t");
        assertEquals("primary=123;345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,;)::group=Group(0,3,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("123,2345,567,789;c(variation)t");
        assertEquals("primary=123,2345,567,789::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=false::group=Group(0,3,,)::group=Group(0,4,,)::group=Group(0,3,,)::group=Group(0,3,)", picture.toString());
        picture = IntegerPicture.fromString("#89;c(variation)t");
        assertEquals("primary=#89::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(1,2,)", picture.toString());
        picture = IntegerPicture.fromString("#89|123;c(variation)t");
        assertEquals("primary=#89|123::modifier=numbering=Cardinal::variation=variation::lettersequence=Traditional::regular=true::group=Group(0,3,|)", picture.toString());
    }

    private String fmt(final String pictureString, final long value) throws XPathException {
        return fmt(pictureString, value, "en");
    }

    private String fmt(final String pictureString, final long value, final String language) throws XPathException {
        final IntegerPicture picture = IntegerPicture.fromString(pictureString);
        final Locale locale = new Locale.Builder().setLanguage(language).build();
        return picture.formatInteger(BigInteger.valueOf(value), locale);
    }

    @Test
    public void format() throws XPathException {
        assertEquals("0", fmt("1", 0L));
        assertEquals("00", fmt("12", 0L));
        assertEquals("000", fmt("123", 0L));
        assertEquals("0,0", fmt("1,1", 0L));
        assertEquals("0,1", fmt("1,1", 1L));
    }

    @Test
    public void formatCardinalModifier() throws XPathException {
        //c for cardinal is the default, but we still need to recognize it
        assertEquals("1", fmt("1;c", 1L));
    }

    @Test
    public void formatNegative() throws XPathException {
        assertEquals("-1", fmt("1", -1L));
        assertEquals("-01", fmt("12", -1L));
        assertEquals("-001", fmt("123", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
        assertEquals("-0,1", fmt("1,1", -1L));
    }

    @Test
    public void formatRegular() throws XPathException {
        assertEquals("1,23,45,67,89", fmt("12,34", 123456789L));
        assertEquals("1,23,45,67,89", fmt("12,34,56", 123456789L));
        assertEquals("12345,67?89", fmt("12,34?56", 123456789L));
        assertEquals("123,456,789", fmt("12,345", 123456789L));
        assertEquals("00,089", fmt("12,345", 89L));
    }

    @Test
    public void formatOptional() throws XPathException {
        assertEquals("009", fmt("#234", 9L));
        assertEquals("123456789", fmt("#234", 123456789L));
        assertEquals("000,0009", fmt("#234,1234", 9L));
        assertEquals("0009", fmt("####,1234", 9L));
        assertEquals("0,0009", fmt("###4,1234", 9L));
    }

    @Test
    public void formatOptionalOrdinal() throws XPathException {
        assertEquals("001st", fmt("#234;o", 1L));
        assertEquals("009th", fmt("#234;o", 9L));
        assertEquals("123456789th", fmt("#234;o", 123456789L));
        assertEquals("000,0009th", fmt("#234,1234;o", 9L));
        assertEquals("0009th", fmt("####,1234;o", 9L));
        assertEquals("0,0009th", fmt("###4,1234;o", 9L));
    }

    @Test
    public void formatDefaultFamily() throws XPathException {
        final StringBuilder sb = new StringBuilder();
        sb.append("#234567");
        assertEquals(7, sb.length());
        assertEquals("000009", fmt(sb.toString(), 9L));
    }

    @Test
    public void formatNonDefaultDigitFamilies() throws XPathException {

        // All the code point families that exist in DigitsIntegerPicture
        final int[] range = new int[]{0x30, 0x660, 0x6f0, 0x7c0, 0x966, 0x9e6, 0xa66, 0xae6, 0xb66, 0xbe6, 0xc66, 0xce6, 0xd66, 0xde6, 0xe50, 0xed0, 0xf20, 0x1040, 0x1090, 0x17e0, 0x1810, 0x1946, 0x19d0, 0x1a80, 0x1a90, 0x1b50, 0x1bb0, 0x1c40, 0x1c50, 0xa620, 0xa8d0, 0xa900, 0xa9d0, 0xa9f0, 0xaa50, 0xabf0, 0xff10, 0x104a0, 0x11066, 0x110f0, 0x11136, 0x111d0, 0x112f0, 0x11450, 0x114d0, 0x11650, 0x116c0, 0x11730, 0x118e0, 0x11c50, 0x16a60, 0x16b50, 0x1d7ce, 0x1d7d8, 0x1d7e2, 0x1d7ec, 0x1d7f6, 0x1e950};
        for (final int family : range) {
            final StringBuilder sb = new StringBuilder();
            sb.append("#");
            for (int i = 2; i < 5; i++) {
                final char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            sb.append("|");
            for (int i = 5; i < 8; i++) {
                final char[] chars = Character.toChars(family + i);
                sb.append(chars);
            }
            final String formatted = fmt(sb.toString(), 149L);
            assertEquals(6 * Character.charCount(family) + 1, formatted.length());
            int pos = 0;
            int codePoint;
            for (final int offset : new int[]{0, 0, 0}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
            codePoint = Character.codePointAt(formatted, pos);
            assertEquals(Character.codePointAt("|", 0), codePoint);
            pos += 1;
            for (final int offset : new int[]{1, 4, 9}) {
                codePoint = Character.codePointAt(formatted, pos);
                assertEquals(family + offset, codePoint);
                pos += Character.toChars(family + offset).length;
            }
        }
    }

    @Test
    public void conflictingDigitFamilies() {
        final StringBuilder sb = new StringBuilder();
        for (final int family : new int[]{0x104a0, 0x30}) {
            final char[] chars = Character.toChars(family + 3);
            sb.append(chars);
            sb.append(",");
        }
        try {
            fmt(sb.toString(), 9L);
            fail("Conflicting digit families should throw an exception");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("multiple digit families"));
        }
    }

    @Test
    public void optionalSignsAfterMandatorySigns() throws XPathException {
        assertEquals("0|005", fmt("##|#3|456", 5L));
        assertEquals("0|05", fmt("##|#3|45", 5L));
        assertEquals("5|67|89", fmt("##|#3|45", 56789L));

        try {
            fmt("12,#45", 0L);
            fail("The picture " + "12,#45" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("optional digit after mandatory"));
        }

        try {
            fmt("##|3#|45", 0L);
            fail("The picture " + "##|3#|45" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }

        try {
            fmt("1#", 0L);
            fail("The picture " + "1#" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }

    }

    @Test
    public void separatorAtEndIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("1+", 0L);
            fail("The picture " + "1+" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("ends with a separator"));
        }
    }

    @Test
    public void separatorAtStartIsIllegal() throws XPathException {
        assertEquals("0+5", fmt("1+1", 5L));
        try {
            fmt("|1", 0L);
            fail("The picture " + "+1" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern"));
        }
    }

    @Test
    public void multiSeparator() {
        try {
            assertEquals("0|005", fmt("#3||456", 5L));
            fail("The picture " + "#3||456" + " should not be valid.");
        } catch (final XPathException xpe) {
            assertTrue(xpe.getDetailMessage().contains("expected a digit grouping pattern at 3"));
        }
    }

    @Test
    public void fromDefault() throws XPathException {
        assertEquals("1500000", fmt("#a", 1500000L));
        assertEquals("15th", fmt("#a;o", 15L));
    }

    @Test
    public void alphaUpperDigitFormat() throws XPathException {
        assertEquals("E", fmt("A", 5L));
        assertEquals("Y", fmt("A", 25L));
        assertEquals("Z", fmt("A", 26L));
        assertEquals("AA", fmt("A", 27L));
        assertEquals("AB", fmt("A", 28L));
        assertEquals("AZ", fmt("A", 52L));
        assertEquals("BA", fmt("A", 53L));
        assertEquals("CA", fmt("A", 79L));
        assertEquals("ZZ", fmt("A", 702L));
        assertEquals("AAA", fmt("A", 703L));
        assertEquals("AAZ", fmt("A", 728L));
        assertEquals("ZZZ", fmt("A", 18278));
        assertEquals("AAAA", fmt("A", 18279));

        //out of range, format is "1L"
        assertEquals("0", fmt("A", 0L));
        assertEquals("-35", fmt("A", -35L));
    }

    @Test
    public void alphaLowerDigitFormat() throws XPathException {
        assertEquals("e", fmt("a", 5L));
        assertEquals("y", fmt("a", 25L));
        assertEquals("z", fmt("a", 26L));
        assertEquals("aa", fmt("a", 27L));
        assertEquals("ab", fmt("a", 28L));
        assertEquals("az", fmt("a", 52L));
        assertEquals("ba", fmt("a", 53L));
        assertEquals("ca", fmt("a", 79L));
        assertEquals("zz", fmt("a", 702L));
        assertEquals("aaa", fmt("a", 703L));
        assertEquals("aaz", fmt("a", 728L));
        assertEquals("zzz", fmt("a", 18278));
        assertEquals("aaaa", fmt("a", 18279));

        //out of range, format is "1L"
        assertEquals("0", fmt("a", 0L));
        assertEquals("-35", fmt("a", -35L));
    }

    @Test
    public void romanLowerDigitFormat() throws XPathException {
        assertEquals("v", fmt("i", 5L));
    }

    @Test
    public void romanUpperDigitFormat() throws XPathException {
        assertEquals("V", fmt("I", 5L));
        assertEquals("MDCCCLXVIII", fmt("I", 1868L));
        assertEquals("MCMLXXXIV", fmt("I", 1984L));
        assertEquals("-1", fmt("I", -1L));
        assertEquals("0", fmt("I", 0L));
        assertEquals("5984", fmt("I", 5984L));
    }

    @Test
    public void wordLowerDigitFormat() throws XPathException {
        assertEquals("five", fmt("w", 5L));
        assertEquals("fifteen", fmt("w", 15L));
        assertEquals("fünfzehn", fmt("w", 15L, "de"));
        assertEquals("two thousand five hundred ninety-eight", fmt("w", 2598L, "en"));
        assertEquals("zwei\u00ADtausend\u00ADfünf\u00ADhundert\u00ADacht\u00ADund\u00ADneunzig", fmt("w", 2598L, "de"));
    }

    @Test
    public void wordModifiers() throws XPathException {
        assertEquals("Première", fmt("Ww;o", 1L, "fr"));
        assertEquals("Deuxième", fmt("Ww;o", 2L, "fr"));
        assertEquals("five", fmt("w", 5L));
        assertEquals("fifth", fmt("w;o", 5L));
        assertEquals("cinque", fmt("w;a", 5L, "it"));
        assertEquals("quinta", fmt("w;o", 5L, "it"));
        assertEquals("una", fmt("w;a", 1L, "it"));
        assertEquals("prima", fmt("w;o", 1L, "it"));
        assertEquals("eine", fmt("w;a", 1L, "de"));
        assertEquals("ein", fmt("w;c(neuter)a", 1L, "de"));
        assertEquals("eine", fmt("w;c", 1L, "de"));
        assertEquals("eine", fmt("w;c(feminine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(masculine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(neuter)", 1L, "de"));
        assertEquals("eine", fmt("w;c(vulcan)", 1L, "de"));
        assertEquals("erste", fmt("w;o", 1L, "de"));
        assertEquals("erstes", fmt("w;o(s)", 1L, "de"));
        assertEquals("erster", fmt("w;o(r)", 1L, "de"));
        assertEquals("ersten", fmt("w;o(n)", 1L, "de"));
        assertEquals("erste", fmt("w;o(z)", 1L, "de"));
        assertEquals("первая", fmt("w;o(z)", 1L, "ru"));
        assertEquals("одна", fmt("w;c(z)", 1L, "ru"));
        assertEquals("one", fmt("w;c(z)", 1L, "nonsense"));
        assertEquals("first", fmt("w;o(z)", 1L, "nonsense"));
        assertEquals("erster", fmt("w;o(%spellout-ordinal-r)", 1L, "de"));
        assertEquals("zweites", fmt("w;o(%spellout-ordinal-s)", 2L, "de"));
        assertEquals("zweiten", fmt("w;o(%spellout-ordinal-n)", 2L, "de"));
        assertEquals("zweiter", fmt("w;o(%spellout-ordinal-r)", 2L, "de"));
        assertEquals("eine", fmt("w;c(%spellout-cardinal-feminine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(%spellout-cardinal-masculine)", 1L, "de"));
        assertEquals("ein", fmt("w;c(%spellout-cardinal-neuter)", 1L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-feminine)", 2L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-masculine)", 2L, "de"));
        assertEquals("zwei", fmt("w;c(%spellout-cardinal-neuter)", 2L, "de"));
        assertEquals("two", fmt("w;c(%spellout-cardinal-neuter)", 2L, "zz"));
        assertEquals("دو", fmt("w;o", 2L, "fa"));
    }

    @Test
    public void wordUpperDigitFormat() throws XPathException {
        assertEquals("FIVE", fmt("W", 5L));
        assertEquals("FIFTEEN", fmt("W", 15L));
        assertEquals("FIFTEEN", fmt("W", 15L, "unknown"));
        assertEquals("FÜNFZEHN", fmt("W", 15L, "de"));
    }

    @Test
    public void wordTitleCaseDigitFormat() throws XPathException {
        assertEquals("Five", fmt("Ww", 5L));
        assertEquals("Fifteen", fmt("Ww", 15L));
        assertEquals("Two thousand five hundred ninety-eight", fmt("Ww", 2598L, "en"));
    }

    @Test
    public void modifier() throws XPathException {
        FormatModifier formatModifier = new FormatModifier("c(maschile)t");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        assertEquals("maschile", formatModifier.variation);
        formatModifier = new FormatModifier("ct");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("ca");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("ot");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("oa");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("c");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("o");
        assertEquals(FormatModifier.Numbering.Ordinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("a");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        formatModifier = new FormatModifier("t");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Traditional, formatModifier.letterSequence);
        formatModifier = new FormatModifier("c(hello)");
        assertEquals(FormatModifier.Numbering.Cardinal, formatModifier.numbering);
        assertEquals(FormatModifier.LetterSequence.Alphabetic, formatModifier.letterSequence);
        assertEquals("hello", formatModifier.variation);
    }

    private void modifierFail(final String modifier) {
        try {
            final FormatModifier formatModifier = new FormatModifier(modifier);
            fail("Format modifier " + modifier + " should throw a parse exception, not: " + formatModifier);
        } catch (final XPathException e) {
            assertTrue(e.getMessage().contains("modifier"));
        }
    }

    @Test
    public void modifierFailTest() {
        modifierFail("b");
        modifierFail("ba");
        modifierFail("bt");
        modifierFail("(hello)");
        modifierFail("c(t");
        modifierFail("cv");
        modifierFail("av");
        modifierFail("ev");
        modifierFail("tc");
        modifierFail("ctc");
        modifierFail("ctt");
        modifierFail("c()t");
        modifierFail("c()");
    }

    @Test
    public void separators() throws XPathException {
        assertEquals("1500000", fmt("#", 1500000L));
        assertEquals("12,500:000", fmt("0,000:000", 12500000L));
        assertEquals("12,500,000", fmt("0,000,000", 12500000L));
        assertEquals("1,500,000", fmt("0,000", 1500000L));
        assertEquals("12345,00,000", fmt("0,00,000", 1234500000L));
    }

    @Test
    public void falllback() throws XPathException {
        assertEquals("1234", fmt("&#xa;", 1234L));
    }

    @Test
    public void greek() throws XPathException {
        assertEquals("\u03b2", fmt("\u03b1", 2L));
    }

    @Test
    public void math() throws XPathException {
        char[] chars = Character.toChars(0x1D7D8);
        StringBuilder sb = new StringBuilder();
        for (final char c : chars) sb.append(c);
        assertEquals("\uD835\uDFDC", fmt(sb.toString(), 4L));
        assertEquals("\uD835\uDFD9", fmt(sb.toString(), 1L));
        assertEquals("\uD835\uDFD8", fmt(sb.toString(), 0L));
        assertEquals("\uD835\uDFDA", fmt(sb.toString(), 2L));
        assertEquals("\uD835\uDFE1", fmt(sb.toString(), 9L));
        assertEquals("\uD835\uDFD9\uD835\uDFD8", fmt(sb.toString(), 10L));

        chars = Character.toChars(0x1D7E2);
        sb = new StringBuilder();
        for (final char c : chars) sb.append(c);
        assertEquals("\uD835\uDFE2", fmt(sb.toString(), 0L));
    }

    @Test
    public void numberings() throws XPathException {
        assertEquals("①", fmt("①", 1L));
        assertEquals("⑮", fmt("①", 15L));
        assertEquals("⑳", fmt("①", 20L));
        assertEquals("21", fmt("①", 21L));
    }



    @Test public void deutsch() throws XPathException {

        assertEquals("Erster", fmt("Ww;o(-r)", 1L, "de"));
        assertEquals("Erster", fmt("Ww;o(-er)", 1L, "de"));
        assertEquals("Erstes", fmt("Ww;o(-s)", 1L, "de"));
        assertEquals("Erstes", fmt("Ww;o(-es)", 1L, "de"));
        assertEquals("Ersten", fmt("Ww;o(-n)", 1L, "de"));
        assertEquals("Ersten", fmt("Ww;o(-en)", 1L, "de"));
    }

    @Test public void italiano() throws XPathException {

        assertEquals("Quinto", fmt("Ww;o(-o)", 5L, "it"));
        assertEquals("Quinta", fmt("Ww;o(-a)", 5L, "it"));
    }

    @Test
    public void kanji() throws XPathException {
        System.out.println("\u4e00\u4e01\u4e02\u4e03\u4e04\u4e05\u4e06\u4e07\u4e08\u4e09\u4e0a");
        assertEquals("一", fmt("\u4e00", 1L));
    }

    //format-integer(11, 'Ww', '@*!+%')
    @Test
    public void badLanguage() throws XPathException {
        assertEquals("43", fmt("Ww", 11L, "@*!+%"));
    }


    @Test
    public void fallback() throws XPathException {
        final char[] hexChar = Character.toChars(0xa);
        assertEquals("1", fmt(String.valueOf(hexChar), 1L));
    }

    /**
     * Investigation of digit sets
     *
     * @Test
     */
    public void unicodeSets() {
        final UnicodeSet set = new UnicodeSet("[:Nd:]");
        set.freeze();
        System.err.println(set.size());
        int prev = -1;
        int count = 0;
        final StringBuilder sb = new StringBuilder();
        for (final String s : set) {
            final CodePointString cps = new CodePointString(s);
            final int codePoint = cps.codePointAt(0);

            if (prev + 1 < codePoint || count == 10) {
                if (sb.length() > 0) {
                    sb.append(',');
                }
                if (count > 0) {
                    System.err.println(count);
                }
                System.err.print("" + codePoint + "->");
                count = 1;
                sb.append("0x").append(Integer.toHexString(codePoint));
            } else {
                count++;
            }
            prev = codePoint;
        }
        System.err.println(count);
        System.err.println(sb);
    }

    /**
     * Investigation of what spellouts are available per-locale
     *
     * @Test
     */
    @Test public void localesAndSpellouts() {
        final List<String> iso639Alpha2Codes = Arrays.asList("aa", "ab", "ae", "af", "ak", "am", "an", "ar", "as", "av", "ay", "az",
                "ba", "be", "bg", "bi", "bm", "bn", "bo", "br", "bs",
                "ca", "ce", "ch", "co", "cr", "cs", "cu", "cv", "cy",
                "da", "de", "dv", "dz",
                "ee", "el", "en", "eo", "es", "et", "eu",
                "fa", "ff", "fi", "fj", "fo", "fr", "fy",
                "ga", "gd", "gl", "gn", "gu", "gv",
                "ha", "he", "hi", "ho", "hr", "ht", "hu", "hy", "hz",
                "ia", "id", "ie", "ig", "ii", "ik", "io", "is", "it", "iu",
                "ja", "jv",
                "ka", "kg", "ki", "kj", "kk", "kl", "km", "kn", "ko", "kr", "ks", "ku", "kv", "kw", "ky",
                "la", "lb", "lg", "li", "ln", "lo", "lt", "lu", "lv",
                "mg", "mh", "mi", "mk", "ml", "mn", "mr", "ms", "mt", "my",
                "na", "nb", "nd", "ne", "ng", "nl", "nn", "no", "nr", "nv", "ny",
                "oc", "oj", "om", "or", "os",
                "pa", "pi", "pl", "ps", "pt",
                "rm", "rn", "ro", "ru", "rw",
                "sa", "sc", "sd", "se", "sg", "si", "sk", "sl", "sm", "sn", "so", "sq", "sr", "ss", "st", "su", "sv", "sw",
                "ta", "te", "tg", "th", "ti", "tk", "tl", "tn", "to", "tr", "ts", "tt", "tw", "ty",
                "ug", "uk", "ur", "uz",
                "ve", "vi", "vo",
                "wa", "wo",
                "xh",
                "yi", "yo",
                "za", "zh", "zu");
        final Map<String,Set<String>> global = new HashMap<>();
        for (final String isoCode : iso639Alpha2Codes) {
            final Locale locale = (new Locale.Builder()).setLanguage(isoCode).build();
            final RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.SPELLOUT);
            final Set<String> names = new HashSet<>();
            for (final String ruleSetName : ruleBasedNumberFormat.getRuleSetNames()) {
                names.add(ruleSetName);
                if (!global.containsKey(ruleSetName)) {
                    global.put(ruleSetName, new HashSet<>());
                }
                global.get(ruleSetName).add(isoCode);
            }
            boolean displayMissing = !names.contains("%spellout-ordinal") && !names.contains("%spellout-ordinal-masculine") && !names.contains("%spellout-ordinal-feminine");
            if (!names.contains("%spellout-cardinal") && !names.contains("%spellout-cardinal-masculine") && !names.contains("%spellout-cardinal-feminine")) {
                displayMissing = true;
            }
            if (displayMissing) {
                System.err.println(isoCode + " missing some spellouts --->>> ");
                for (final String name : names) {
                    System.err.println(name);
                }
                System.err.println(isoCode + " <<<---");
            }
        }
        System.err.println("all spellouts --->>>");
        final List<String> globalList = Arrays.asList(global.keySet().toArray(new String[]{}));
        Collections.sort(globalList);
        for (final String name : globalList) {
            final StringBuilder sb = new StringBuilder();
            sb.append(name).append(" --->");
            for (final String isoCode : global.get(name)) {
                sb.append(' ').append(isoCode);
            }
            System.err.println(sb);
        }
        System.err.println("<<<--- all spellouts");

    }
}
