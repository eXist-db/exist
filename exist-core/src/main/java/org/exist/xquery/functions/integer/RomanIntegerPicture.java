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
 * Format numbers according to rules 4/5 (any other numbering sequence)
 * {@see https://www.w3.org/TR/xpath-functions-31/#formatting-integers}
 */
class RomanIntegerPicture extends IntegerPicture {

    private final boolean isUpper;

    RomanIntegerPicture(final boolean isUpper) {
        this.isUpper = isUpper;
    }

    @Override
    public String formatInteger(final BigInteger bigInteger, final Locale locale) throws XPathException {
        //spec says out of range should be formatted by "1"
        if (bigInteger.compareTo(BigInteger.ZERO) <= 0 || bigInteger.compareTo(BigInteger.valueOf(4999L)) > 0) {
            return defaultPictureWithModifier(new FormatModifier("")).formatInteger(bigInteger, locale);
        }

        final String roman = RomanNumberHelper.toRoman(bigInteger.intValue());
        if (isUpper) {
            return roman.toUpperCase();
        } else {
            return roman.toLowerCase();
        }
    }
}
