/*
 * eXist-db Open Source Native XML Database
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
 *
 * The source code for this class is taken from the stackoverflow answer
 * https://stackoverflow.com/questions/12967896/converting-integers-to-roman-numerals-java
 * written by https://stackoverflow.com/users/1420681/ben-hur-langoni-junior
 * and is therefore used and made available in accordance with
 * https://creativecommons.org/licenses/by-sa/3.0
 *
 */

package org.exist.xquery.functions.integer;

import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

class NumberingPicture extends IntegerPicture {

    // Set up the code point ranges we accept as numberings.
    private final static Map<Integer, Integer> rangesForCodePoint = new HashMap<>();

    static {
        range(0x391, 0x3A9);
        range(0x3B1, 0x3C9);
        range('①', '⑳');
        range('⑴', '⒇');
        range('⒈', '⒛');
    }

    /**
     * Define a range using characters
     *
     * @param from first item in range (1)
     * @param to   last item in range (inclusive)
     */
    private static void range(final char from, final char to) {
        final char[] fromChars = {from};
        final char[] toChars = {to};
        rangesForCodePoint.put(Character.codePointAt(fromChars, 0), Character.codePointAt(toChars, 0));
    }

    /**
     * Define a range using code points
     *
     * @param from first item in range (1)
     * @param to   last item in range (inclusive)
     */
    private static void range(final int from, final int to) {
        rangesForCodePoint.put(from, to);
    }

    private final int indexCodePoint;
    private final int limitForRange;
    private final IntegerPicture defaultPicture;
    private final FormatModifier formatModifier;

    private NumberingPicture(final int indexCodePoint, final int limitForRange, final FormatModifier formatModifier) throws XPathException {
        this.indexCodePoint = indexCodePoint;
        this.limitForRange = limitForRange;
        this.defaultPicture = IntegerPicture.defaultPictureWithModifier(formatModifier);
        this.formatModifier = formatModifier;
    }

    public static Optional<IntegerPicture> fromIndexCodePoint(final int indexCodePoint, final FormatModifier formatModifier) throws XPathException {
        if (!rangesForCodePoint.containsKey(indexCodePoint)) {
            return Optional.empty();
        }
        final int limitForRange = rangesForCodePoint.get(indexCodePoint);
        return Optional.of(new NumberingPicture(indexCodePoint, limitForRange, formatModifier));
    }

    @Override
    public String formatInteger(final BigInteger bigInteger, final String language) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.valueOf(1)) < 0 ||
                bigInteger.compareTo(BigInteger.valueOf(limitForRange - indexCodePoint + 1)) > 0) {
            return defaultPicture.formatInteger(bigInteger, language);
        }

        final StringBuilder result = new StringBuilder();
        result.append(FromCodePoint(bigInteger.intValue() + indexCodePoint - 1));
        if (formatModifier.numbering == FormatModifier.Numbering.Ordinal &&
                bigInteger.compareTo(BigInteger.valueOf(Integer.MIN_VALUE)) >= 0 &&
                bigInteger.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            result.append(ordinalSuffix(bigInteger.intValue(), language));
        }
        return result.toString();
    }
}
