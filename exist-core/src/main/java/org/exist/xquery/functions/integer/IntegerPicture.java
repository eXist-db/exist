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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Format numbers according to the rules for
 * <a href="https://www.w3.org/TR/xpath-functions-31/#formatting-integers">format-integer</a>
 */
public abstract class IntegerPicture {

    static final BigInteger TEN = BigInteger.valueOf(10L);

    //This contains \\v (vertical whitespace characters) so anything with vertical white space isn't a pattern
    //It also disallows 0 instances of the pattern
    //When decimal digit pattern doesn't match, we end up falling into a standard default.
    static final Pattern decimalDigitPattern = Pattern.compile("^((\\p{Nd}|#|[^\\p{N}\\p{L}\\v])+)$", Pattern.UNICODE_CHARACTER_CLASS);
    static final Pattern invalidDigitPattern = Pattern.compile("(\\p{Nd})");

    /**
     * The value of $picture consists of a primary format token,
     * optionally followed by a format modifier.
     *
     * @param pictureFormat the format to use - choose which sub picture is needed
     * @return the right picture to handle this format
     * @throws XPathException if the format is not a known/valid form of picture format
     */
    public static IntegerPicture fromString(final String pictureFormat) throws XPathException {

        final String primaryFormatToken;
        final FormatModifier formatModifier;

        final int splitPosition = pictureFormat.lastIndexOf(';');
        if (splitPosition < 0) {
            primaryFormatToken = pictureFormat;
            formatModifier = new FormatModifier("");
        } else {
            primaryFormatToken = pictureFormat.substring(0, splitPosition);
            formatModifier = new FormatModifier(pictureFormat.substring(splitPosition + 1));
        }
        if (primaryFormatToken.isEmpty()) {
            throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Invalid (empty) primary format token in integer format token: " + primaryFormatToken);
        }

        // type 1 matcher (some digits)
        final Matcher decimalDigitMatcher = IntegerPicture.decimalDigitPattern.matcher(primaryFormatToken);
        if (decimalDigitMatcher.matches()) {
            return new DigitsIntegerPicture(primaryFormatToken, formatModifier);
        }

        // incorrect type 1 matcher (and not anything else)
        final Matcher invalidDigitMatcher = IntegerPicture.invalidDigitPattern.matcher(primaryFormatToken);
        if (invalidDigitMatcher.find()) {
            throw new XPathException((Expression) null, ErrorCodes.FODF1310, "Invalid primary format token is not a valid decimal digital pattern: " + primaryFormatToken);
        }

        // specifically defined format token rules 2-8
        // <a href="https://www.w3.org/TR/xpath-functions-31/#formatting-integers"/>
        switch (primaryFormatToken) {
            case "A":
                return new SequenceIntegerPicture('A');
            case "a":
                return new SequenceIntegerPicture('a');
            case "i":
                return new RomanIntegerPicture(false/*isUpper*/);
            case "I":
                return new RomanIntegerPicture(true/*isUpper*/);
            case "W":
                return new WordPicture(WordPicture.CaseAndCaps.UPPER, formatModifier);
            case "w":
                return new WordPicture(WordPicture.CaseAndCaps.LOWER, formatModifier);
            case "Ww":
                return new WordPicture(WordPicture.CaseAndCaps.CAPITALIZED, formatModifier);
            default:
                break;
        }

        // Rule 9 - sequences
        // <a href="https://www.w3.org/TR/xpath-functions-31/#formatting-integers"/>
        final List<Integer> codePoints = IntegerPicture.codePoints(primaryFormatToken);
        final Optional<IntegerPicture> numberingPicture = NumberingPicture.fromIndexCodePoint(codePoints.getFirst(), formatModifier);

        return numberingPicture.orElse(IntegerPicture.defaultPictureWithModifier(formatModifier));
    }

    static IntegerPicture defaultPictureWithModifier(final FormatModifier formatModifier) throws XPathException {
        return new DigitsIntegerPicture("1", formatModifier);
    }

    /**
     * Format an integer according to the picture and language with which this was constructed
     *
     * @param bigInteger the integer to format
     * @param locale of the language to use in formatting
     * @return a string containing the formatted integer
     * @throws XPathException if the locale is ill-formed
     */
    protected abstract String formatInteger(BigInteger bigInteger, Locale locale) throws XPathException;

    private static Locale getLocaleFromLanguages(final List<String> languages) throws XPathException {

        IllformedLocaleException languageILE = null;
        for (final String language : languages) {
            final Locale.Builder localeBuilder = new Locale.Builder();
            try {
                localeBuilder.setLanguage(language);
                return localeBuilder.build();
            } catch (final IllformedLocaleException ile) {
                languageILE = ile;
            }
        }
        assert languageILE != null;
        throw new XPathException((Expression) null, ErrorCodes.FODF1310, languageILE.getMessage());
    }

    public final String formatInteger(final BigInteger bigInteger, final List<String> languages) throws XPathException {

        final Locale locale = IntegerPicture.getLocaleFromLanguages(languages);
        return formatInteger(bigInteger, locale);
    }

    /**
     * Convert a string into a list of unicode code points
     *
     * @param s the input string
     * @return a list of the codepoints forming the string
     */
    protected static List<Integer> codePoints(final String s) {
        final List<Integer> codePointList = new ArrayList<>(s.length());
        int i = 0;
        while (i < s.length()) {
            final int codePoint = Character.codePointAt(s, i);
            i += Character.charCount(codePoint);
            codePointList.add(codePoint);
        }
        return codePointList;
    }

    protected static String fromCodePoint(final int codePoint) {
        final StringBuilder sb = new StringBuilder();
        for (final char c : Character.toChars(codePoint)) {
            sb.append(c);
        }
        return sb.toString();
    }

    protected static String ordinalSuffix(final int value, final Locale locale) {
        final RuleBasedNumberFormat ruleBasedNumberFormat = new RuleBasedNumberFormat(locale, RuleBasedNumberFormat.ORDINAL);
        final StringBuilder sb = new StringBuilder(ruleBasedNumberFormat.format(value)).reverse();
        int i = 0;
        //noinspection StatementWithEmptyBody
        for (; !sb.isEmpty() && Character.isAlphabetic(sb.charAt(i)); i++) ;
        return sb.delete(i, sb.length()).reverse().toString();
    }
}
