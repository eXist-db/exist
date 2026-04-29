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

import java.util.Locale;

/**
 * English formatter for numbers and dates.
 *
 * @author Wolfgang
 */
public class NumberFormatter_en extends NumberFormatter {

    public NumberFormatter_en(final Locale locale) {
        super(locale);
    }

    @Override
    public String getOrdinalSuffix(long number) {
        // "th" for the 11-19 teen exception, including any number whose last
        // two digits are 11-19 (e.g. 2011 → "th", 1812 → "th").
        final long mod100 = Math.abs(number) % 100;
        if (mod100 >= 11 && mod100 <= 19) {
            return "th";
        }
        final long mod = Math.abs(number) % 10;
        if (mod == 1)
            {return "st";}
        else if (mod == 2)
            {return "nd";}
        else if (mod == 3)
            {return "rd";}
        else
            {return "th";}
    }

    private static final String[] CARDINALS_BELOW_20 = {
            "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine",
            "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen",
            "seventeen", "eighteen", "nineteen"
    };

    private static final String[] CARDINAL_TENS = {
            "", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety"
    };

    private static final String[] ORDINALS_BELOW_20 = {
            "zeroth", "first", "second", "third", "fourth", "fifth", "sixth", "seventh",
            "eighth", "ninth", "tenth", "eleventh", "twelfth", "thirteenth", "fourteenth",
            "fifteenth", "sixteenth", "seventeenth", "eighteenth", "nineteenth"
    };

    private static final String[] ORDINAL_TENS = {
            "", "", "twentieth", "thirtieth", "fortieth", "fiftieth", "sixtieth", "seventieth",
            "eightieth", "ninetieth"
    };

    @Override
    public String getCardinalWord(long number) {
        if (number < 0) {
            return "minus " + getCardinalWord(-number);
        }
        return cardinal(number);
    }

    @Override
    public String getOrdinalWord(long number) {
        if (number < 0) {
            return "minus " + getOrdinalWord(-number);
        }
        return ordinal(number);
    }

    private String cardinal(long n) {
        if (n < 20) {
            return CARDINALS_BELOW_20[(int) n];
        }
        if (n < 100) {
            final int tens = (int) (n / 10);
            final int units = (int) (n % 10);
            return units == 0
                    ? CARDINAL_TENS[tens]
                    : CARDINAL_TENS[tens] + "-" + CARDINALS_BELOW_20[units];
        }
        if (n < 1000) {
            final int hundreds = (int) (n / 100);
            final long rem = n % 100;
            return rem == 0
                    ? CARDINALS_BELOW_20[hundreds] + " hundred"
                    : CARDINALS_BELOW_20[hundreds] + " hundred and " + cardinal(rem);
        }
        if (n < 1_000_000L) {
            final long thousands = n / 1000;
            final long rem = n % 1000;
            return rem == 0
                    ? cardinal(thousands) + " thousand"
                    : cardinal(thousands) + " thousand " + cardinal(rem);
        }
        if (n < 1_000_000_000L) {
            final long millions = n / 1_000_000L;
            final long rem = n % 1_000_000L;
            return rem == 0
                    ? cardinal(millions) + " million"
                    : cardinal(millions) + " million " + cardinal(rem);
        }
        return Long.toString(n);
    }

    private String ordinal(long n) {
        if (n < 20) {
            return ORDINALS_BELOW_20[(int) n];
        }
        if (n < 100) {
            final int tens = (int) (n / 10);
            final int units = (int) (n % 10);
            return units == 0
                    ? ORDINAL_TENS[tens]
                    : CARDINAL_TENS[tens] + "-" + ORDINALS_BELOW_20[units];
        }
        if (n < 1000) {
            final int hundreds = (int) (n / 100);
            final long rem = n % 100;
            return rem == 0
                    ? CARDINALS_BELOW_20[hundreds] + " hundredth"
                    : CARDINALS_BELOW_20[hundreds] + " hundred and " + ordinal(rem);
        }
        if (n < 1_000_000L) {
            final long thousands = n / 1000;
            final long rem = n % 1000;
            return rem == 0
                    ? cardinal(thousands) + " thousandth"
                    : cardinal(thousands) + " thousand " + ordinal(rem);
        }
        if (n < 1_000_000_000L) {
            final long millions = n / 1_000_000L;
            final long rem = n % 1_000_000L;
            return rem == 0
                    ? cardinal(millions) + " millionth"
                    : cardinal(millions) + " million " + ordinal(rem);
        }
        return Long.toString(n) + getOrdinalSuffix(n);
    }
}
