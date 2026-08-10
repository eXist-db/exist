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
package org.exist.xquery.util;

import net.sf.saxon.expr.number.Alphanumeric;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.text.DateFormatSymbols;
import java.time.DayOfWeek;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Formatter for numbers and dates. Concrete implementations are language-dependant.
 *
 * @author Wolfgang
 */
public abstract class NumberFormatter {

    private static char OPTIONAL_DIGIT_SIGN = '#';

    private final Locale locale;

    public NumberFormatter(final Locale locale) {
        this.locale = locale;
    }

    public String getMonth(int month) {
        return Month.of(month).getDisplayName(TextStyle.FULL, locale);
    }

    /** Locale's standard short month name (e.g. English January → "Jan"). */
    public String getMonthShort(int month) {
        return Month.of(month).getDisplayName(TextStyle.SHORT, locale);
    }

    public String getDay(int day) {
        return DayOfWeek.of(day).getDisplayName(TextStyle.FULL, locale);
    }

    /** Locale's standard short day-of-week name (e.g. English Monday → "Mon"). */
    public String getDayShort(int day) {
        return DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, locale);
    }

    public String getAmPm(int hour) {
        final DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        final String[] amPm = symbols.getAmPmStrings();
        if (hour >= 12) {
            return amPm[1];
        }
        return amPm[0];
    }

    public abstract String getOrdinalSuffix(long number);

    /**
     * Cardinal word form of a number, e.g. English 1 → "one", 21 → "twenty-one".
     * Default returns the digit string; subclasses provide language-specific
     * spelling.
     */
    public String getCardinalWord(long number) {
        return Long.toString(number);
    }

    /**
     * Ordinal word form of a number, e.g. English 1 → "first", 21 → "twenty-first".
     * Default returns the digit string with the ordinal suffix; subclasses provide
     * full language-specific spelling.
     */
    public String getOrdinalWord(long number) {
        return Long.toString(number) + getOrdinalSuffix(number);
    }

    /**
     * Convert a phrase to title case: capitalize the first letter of each word
     * (where word boundaries are spaces and hyphens). The default implementation
     * uppercases the first letter of each space- or hyphen-separated token.
     */
    public String toTitleCase(final String name) {
        if (name == null || name.isEmpty()) return name;
        final StringBuilder sb = new StringBuilder(name.length());
        boolean atWordStart = true;
        for (int i = 0; i < name.length(); i++) {
            final char ch = name.charAt(i);
            if (atWordStart && Character.isLetter(ch)) {
                sb.append(Character.toUpperCase(ch));
                atWordStart = false;
            } else {
                sb.append(Character.toLowerCase(ch));
                if (ch == ' ' || ch == '-') {
                    atWordStart = true;
                }
            }
        }
        return sb.toString();
    }

    public String formatNumber(final long number, final String picture) throws XPathException {
        final int min = getMinDigits(picture);
        final int max = getMaxDigits(picture);
        return formatNumber(number, picture, min, max);
    }

    public String formatNumber(final long number, String picture, final int min, final int max) throws XPathException {
        if (picture == null)
            {return "" + number;}

        boolean ordinal = false;
        if (picture.endsWith("o")) {
            ordinal = true;
            picture = picture.substring(0, picture.length() - 1);
        } else if (picture.endsWith("c")) {
            picture = picture.substring(0, picture.length() - 1);
        }

        final StringBuilder sb = new StringBuilder();
        final int digitSign = getFirstDigit(picture);
        final int zero = Alphanumeric.getDigitFamily(digitSign);

        int count = 0;
        long n = number;
        while (n > 0) {
            final int digit = zero + (int)n % 10;
            sb.insert(0, (char)digit);
            count++;
            if (count == max)
                {break;}
            n = n / 10;
        }
        if (sb.length() < min) {
            for (int i = sb.length(); i < min; i++) {
                sb.insert(0, (char) zero);
            }
        }

        if (ordinal)
            {sb.append(getOrdinalSuffix(number));}
        return sb.toString();
    }

    private int getFirstDigit(String picture) throws XPathException {
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if (ch != OPTIONAL_DIGIT_SIGN)
                {return ch;}
        }
        throw new XPathException((Expression) null, "There should be at least one digit sign in the picture string: " + picture);
    }

    public static int getMinDigits(String picture) {
        int count = 0;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1)
                {break;}
            if (ch != OPTIONAL_DIGIT_SIGN)
                {count++;}
        }
        return count;
    }

    public static int getMaxDigits(String picture) {
        int count = 0;
        for (int i = 0; i < picture.length(); i++) {
            final char ch = picture.charAt(i);
            if ((ch == 'o' || ch == 'c') && i == picture.length() - 1)
                {break;}
            count++;
        }
        return count;
    }

    public static NumberFormatter getInstance(final String language) {
        return switch (language) {
            case "de" -> new NumberFormatter_de(Locale.of("de"));
            case "fr" -> new NumberFormatter_fr(Locale.of("fr"));
            case "nl" -> new NumberFormatter_nl(Locale.of("nl"));
            case "ru" -> new NumberFormatter_ru(Locale.of("ru"));
            case "sv" -> new NumberFormatter_sv(Locale.of("sv"));
            // Unsupported languages fall back to English; using Locale.ENGLISH ensures
            // Month/DayOfWeek display names come back in full English form rather
            // than the JDK ROOT-locale abbreviated form.
            default -> new NumberFormatter_en(Locale.ENGLISH);
        };
    }
}
