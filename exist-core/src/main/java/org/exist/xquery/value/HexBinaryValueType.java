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
package org.exist.xquery.value;

import org.exist.util.io.HexOutputStream;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class HexBinaryValueType extends BinaryValueType<HexOutputStream> {

    private final static Pattern hexPattern = Pattern.compile("[A-Fa-f0-9]*");

    public HexBinaryValueType() {
        super(Type.HEX_BINARY, (outputStream, doEncode) -> new HexOutputStream(outputStream, doEncode, false));
    }

    private Matcher getMatcher(final String toMatch) {
        return hexPattern.matcher(toMatch);
    }

    @Override
    protected void verifyString(String str) throws XPathException {

        if ((str.length() & 1) != 0) {
            throw new XPathException((Expression) null, ErrorCodes.FORG0001, "A hexBinary value must contain an even number of characters");
        }

        if (!getMatcher(str).matches()) {
            throw new XPathException((Expression) null, ErrorCodes.FORG0001, "Invalid hexadecimal digit");
        }
    }

    @Override
    protected String formatString(String str) {
        return str.toUpperCase(Locale.ENGLISH);
    }
}