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

import org.exist.xquery.XPathException;

import java.math.BigInteger;
import java.util.Locale;

/**
 * Format as sequence according to rule 2/3 (alphabetic digits)
 * {@see https://www.w3.org/TR/xpath-functions-31/#formatting-integers}
 */
class SequenceIntegerPicture extends IntegerPicture {

    private static final BigInteger RADIX = BigInteger.valueOf(26L);

    private final int codePoint;

    SequenceIntegerPicture(final int codePoint) {
        this.codePoint = codePoint;
    }

    /**
     * Format with a sequence as digits
     *
     * @param bigInteger the integer to format
     * @param locale     of the language to use in formatting
     * @return the formatted string
     * @throws XPathException if something went wrong
     */
    @Override
    public String formatInteger(final BigInteger bigInteger, final Locale locale) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0) {
            return IntegerPicture.defaultPictureWithModifier(new FormatModifier("")).formatInteger(bigInteger, locale);
        }

        final StringBuilder sb = new StringBuilder();
        BigInteger acc = bigInteger;
        do {
            final BigInteger[] divideAndRemainder = acc.subtract(BigInteger.ONE).divideAndRemainder(SequenceIntegerPicture.RADIX);
            sb.append(IntegerPicture.fromCodePoint(codePoint + divideAndRemainder[1].intValue()));
            acc = divideAndRemainder[0];
        } while (acc.compareTo(BigInteger.ZERO) > 0);

        return sb.reverse().toString();
    }
}
