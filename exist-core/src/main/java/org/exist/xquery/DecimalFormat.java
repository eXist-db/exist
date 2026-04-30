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
package org.exist.xquery;

/**
 * Data class for a Decimal Format.
 *
 * See https://www.w3.org/TR/xquery-31/#id-decimal-format-decl
 *
 * NOTE: UTF-16 characters are stored as code-points!
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DecimalFormat {

    /**
     * The default (unnamed) decimal format as defined by the XQuery 3.1 specification.
     *
     * @see <a href="https://www.w3.org/TR/xquery-31/#id-decimal-format-decl">XQuery 3.1 §4.10: Decimal Format Declaration</a>
     */
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
            "-"
    );


    // Markers: used in the picture string to identify active elements
    public final int decimalSeparator;
    public final int exponentSeparator;
    public final int groupingSeparator;
    public final int percent;
    public final int perMille;
    public final int zeroDigit;

    // used in the picture string only
    public final int digit;
    public final int patternSeparator;

    // used in the result of formatting the number, but not in the picture string.
    // Per XPath 4.0, minus-sign is a string (rendition) — multi-character allowed.
    public final String infinity;
    public final String NaN;
    public final String minusSign;

    // XQ4 renditions: output strings for properties that support char:rendition.
    // When marker != rendition, the marker is used for picture parsing and the
    // rendition string appears in the formatted output.
    public final String decimalSeparatorRendition;
    public final String exponentSeparatorRendition;
    public final String groupingSeparatorRendition;
    public final String percentRendition;
    public final String perMilleRendition;

    public DecimalFormat(final int decimalSeparator, final int exponentSeparator, final int groupingSeparator,
            final int percent, final int perMille, final int zeroDigit, final int digit,
            final int patternSeparator, final String infinity, final String NaN, final String minusSign) {
        this(decimalSeparator, exponentSeparator, groupingSeparator, percent, perMille,
                zeroDigit, digit, patternSeparator, infinity, NaN, minusSign,
                null, null, null, null, null);
    }

    public DecimalFormat(final int decimalSeparator, final int exponentSeparator, final int groupingSeparator,
            final int percent, final int perMille, final int zeroDigit, final int digit,
            final int patternSeparator, final String infinity, final String NaN, final String minusSign,
            final String decimalSeparatorRendition, final String exponentSeparatorRendition,
            final String groupingSeparatorRendition, final String percentRendition,
            final String perMilleRendition) {
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
        // Renditions default to the marker character as a string
        this.decimalSeparatorRendition = decimalSeparatorRendition != null ? decimalSeparatorRendition : new String(Character.toChars(decimalSeparator));
        this.exponentSeparatorRendition = exponentSeparatorRendition != null ? exponentSeparatorRendition : new String(Character.toChars(exponentSeparator));
        this.groupingSeparatorRendition = groupingSeparatorRendition != null ? groupingSeparatorRendition : new String(Character.toChars(groupingSeparator));
        this.percentRendition = percentRendition != null ? percentRendition : new String(Character.toChars(percent));
        this.perMilleRendition = perMilleRendition != null ? perMilleRendition : new String(Character.toChars(perMille));
    }
}
