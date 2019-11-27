/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2019 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

/**
 * Data class for a Decimal Format.
 *
 * See https://www.w3.org/TR/xpath-31/#dt-static-decimal-formats
 *
 * NOTE: UTF-16 characters are stored as code-points!
 *
 * @author <a href="mailto: adam@evolvedbinary.com">Adam Retter</a>
 */
public class DecimalFormat {

    public static final DecimalFormat UNNAMED = new DecimalFormat(
            '.',
            'e',
            ',',
            '%',
            '\u2030',
            '0',
            '#',
            ';',
            "Infinity",
            "NaN",
            '-'
    );


    // used both in the picture string, and in the formatted number
    public final int decimalSeparator;
    public final int exponentSeparator;
    public final int groupingSeparator;
    public final int percent;
    public final int perMille;
    public final int zeroDigit;

    // used in the picture string
    public final int digit;
    public final int patternSeparator;

    //used in the result of formatting the number, but not in the picture string
    public final String infinity;
    public final String NaN;
    public final int minusSign;

    public DecimalFormat(final int decimalSeparator, final int exponentSeparator, final int groupingSeparator,
            final int percent, final int perMille, final int zeroDigit, final int digit,
            final int patternSeparator, final String infinity, final String NaN, final int minusSign) {
        this.decimalSeparator = decimalSeparator;
        this.exponentSeparator = exponentSeparator;
        this.groupingSeparator = groupingSeparator;
        this.percent = percent;
        this.perMille = perMille;
        this.zeroDigit = zeroDigit;
        this.digit = digit;
        this.patternSeparator = patternSeparator;
        this.infinity = infinity;
        this.NaN = NaN;
        this.minusSign = minusSign;
    }
}
