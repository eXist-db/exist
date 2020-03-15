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

import org.exist.util.io.Base64OutputStream;
import org.exist.xquery.XPathException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class Base64BinaryValueType extends BinaryValueType<Base64OutputStream> {

    //private final static Pattern base64Pattern = Pattern.compile("^(?:[A-Za-z0-9+/\\s]{4})*(?:[A-Za-z0-9+/\\s]{2}==|[A-Za-z0-9+/\\s]{3}=)?$");
    //private final static Pattern base64Pattern = Pattern.compile("^((?:(?:\\s*[A-Za-z0-9+/]){4})*(?:(?:\\s*[A-Za-z0-9+/]){2}\\s*=\\s*=|(?:\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");

    //private final static Pattern base64Pattern = Pattern.compile("^((?:(?:\\s*[A-Za-z0-9+/]){4})*(?:(?:\\s*[A-Za-z0-9+/]){1}(?:\\s*[AQgw]){1}\\s*=\\s*=|(?:\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");
    private final static Pattern base64Pattern = Pattern.compile("^((?>(?>\\s*[A-Za-z0-9+/]){4})*(?>(?>\\s*[A-Za-z0-9+/]){1}(?>\\s*[AQgw]){1}\\s*=\\s*=|(?>\\s*[A-Za-z0-9+/]){3}\\s*=)?)$");

    public Base64BinaryValueType() {
        super(Type.BASE64_BINARY, Base64OutputStream.class);
    }

    private Matcher getMatcher(final String toMatch) {
        return base64Pattern.matcher(toMatch);
    }

    @Override
    public void verifyString(String str) throws XPathException {
        if (!getMatcher(str).matches()) {
            throw new XPathException("FORG0001: Invalid base64 data");
        }
    }

    @Override
    protected String formatString(String str) {
        return str;
    }
}