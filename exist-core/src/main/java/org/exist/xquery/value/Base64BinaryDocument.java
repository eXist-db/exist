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

import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.io.InputStream;

/**
 * Wrapper around Base64Binary.
 *
 * @author dizzzzz
 */
public class Base64BinaryDocument extends BinaryValueFromInputStream {

    private String url = null;

    private Base64BinaryDocument(final BinaryValueManager manager, final InputStream is) throws XPathException {
        this(null, manager, is);
    }

    private Base64BinaryDocument(final Expression expression, final BinaryValueManager manager, final InputStream is) throws XPathException {
        super(expression, manager, new Base64BinaryValueType(), is);
    }

    public static Base64BinaryDocument getInstance(final BinaryValueManager manager, final InputStream is) throws XPathException {
        return getInstance(manager, is, null);
    }

    public static Base64BinaryDocument getInstance(final BinaryValueManager manager, final InputStream is, final Expression expression) throws XPathException {
        final Base64BinaryDocument b64BinaryDocument = new Base64BinaryDocument(expression, manager, is);
        manager.registerBinaryValueInstance(b64BinaryDocument);
        return b64BinaryDocument;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
}
