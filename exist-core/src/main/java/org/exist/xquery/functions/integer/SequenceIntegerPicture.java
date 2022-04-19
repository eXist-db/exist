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

class SequenceIntegerPicture extends IntegerPicture {

    private final static BigInteger RADIX = BigInteger.valueOf(26L);

    private final int codePoint;

    SequenceIntegerPicture(final int codePoint) {
        this.codePoint = codePoint;
    }

    @Override
    public String formatInteger(BigInteger bigInteger, String language) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0) {
            return DEFAULT.formatInteger(bigInteger, language);
        }

        StringBuilder sb = new StringBuilder();
        do {
            bigInteger = bigInteger.subtract(BigInteger.ONE);
            BigInteger[] divideAndRemainder = bigInteger.divideAndRemainder(RADIX);
            sb.append(FromCodePoint(codePoint + divideAndRemainder[1].intValue()));
            bigInteger = divideAndRemainder[0];
        }
        while (bigInteger.compareTo(BigInteger.ZERO) > 0);

        return sb.reverse().toString();
    }
}
