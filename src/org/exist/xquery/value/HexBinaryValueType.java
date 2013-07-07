/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.value;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.util.io.HexOutputStream;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class HexBinaryValueType extends BinaryValueType<HexOutputStream> {

    private final static Pattern hexPattern = Pattern.compile("[A-Fa-f0-9]*");
    private Matcher matcher;

    public HexBinaryValueType() {
        super(Type.HEX_BINARY, HexOutputStream.class);
    }

    private Matcher getMatcher(String toMatch) {
        if(matcher == null) {
            matcher = hexPattern.matcher(toMatch);
        } else {
            matcher = matcher.reset(toMatch);
        }
        return matcher;
    }

    @Override
    protected void verifyString(String str) throws XPathException {

        if((str.length() & 1) != 0) {
            throw new XPathException(ErrorCodes.FORG0001, "A hexBinary value must contain an even number of characters");
        }

        if(!getMatcher(str).matches()) {
            throw new XPathException(ErrorCodes.FORG0001, "Invalid hexadecimal digit");
        }
    }

    @Override
    protected String formatString(String str) {
        return str.toUpperCase(Locale.ENGLISH);
    }
}