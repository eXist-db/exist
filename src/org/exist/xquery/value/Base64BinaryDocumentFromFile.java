/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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

import java.io.File;
import org.exist.xquery.XPathException;

/**
 * Wrapper around Base64Binary for Files
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class Base64BinaryDocumentFromFile extends BinaryValueFromFile {

    private String url = null;
    
    private Base64BinaryDocumentFromFile(final BinaryValueManager manager, final File file) throws XPathException {
        super(manager, new Base64BinaryValueType(), file);
    }

    public static Base64BinaryDocumentFromFile getInstance(final BinaryValueManager manager, final File f) throws XPathException {
        Base64BinaryDocumentFromFile b64BinaryDocument = new Base64BinaryDocumentFromFile(manager, f);
        manager.registerBinaryValueInstance(b64BinaryDocument);
        return b64BinaryDocument;
    }

    public void setUrl(final String url) {
        this.url = url;
    }
    
    public String getUrl() {
        return url;
    }
}