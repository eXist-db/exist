/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2009 The eXist Project
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
 *
 * $Id$
 */
package org.exist.xquery.value;

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
        super(manager, new Base64BinaryValueType(), is);
    }

    public static Base64BinaryDocument getInstance(final BinaryValueManager manager, final InputStream is) throws XPathException {
        final Base64BinaryDocument b64BinaryDocument = new Base64BinaryDocument(manager, is);
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
