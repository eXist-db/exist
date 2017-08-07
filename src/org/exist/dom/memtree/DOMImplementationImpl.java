/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.dom.memtree;

import org.w3c.dom.DOMException;
import org.w3c.dom.DOMImplementation;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentType;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class DOMImplementationImpl implements DOMImplementation {

    @Override
    public Document createDocument(final String namespaceURI,
            final String qualifiedName, final DocumentType doctype) throws DOMException {
        final DocumentImpl doc = new DocumentImpl(null, true);
        if(qualifiedName != null) {
            if(namespaceURI != null) {
                doc.createElementNS(namespaceURI, qualifiedName);
            } else {
                doc.createElement(qualifiedName);
            }
        }
        //TODO(AR) will need to handle the doc type eventually
        return doc;
    }

    @Override
    public DocumentType createDocumentType(final String qualifiedName,
            final String publicId, final String systemId) throws DOMException {
        return null;
    }

    @Override
    public Object getFeature(final String feature, final String version) {
        return null;
    }

    @Override
    public boolean hasFeature(final String feature, final String version) {
        return ("Core".equalsIgnoreCase(feature) || "XML".equalsIgnoreCase(feature)) &&
                (version == null || "".equals(version) || "1.0".equals(version) || "2.0".equals(version) || "3.0".equals(version));
    }
}
