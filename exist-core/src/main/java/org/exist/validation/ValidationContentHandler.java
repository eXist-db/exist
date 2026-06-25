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

package org.exist.validation;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Simple contenthandler to determine the NamespaceUri of
 * the document root node.
 *
 * @author Dannes Wessels
 */
public class ValidationContentHandler extends DefaultHandler {

    private boolean isFirstElement = true;
    private String namespaceUri = null;
    private Attributes rootAttributes = null;

    /**
     * @see org.xml.sax.helpers.DefaultHandler#startElement(String, String, String, Attributes)
     */
    @Override
    public void startElement(final String uri, final String localName, final String qName,
                             final Attributes attributes) throws SAXException {

        if (isFirstElement) {
            namespaceUri = uri;
            // SAX may reuse/mutate the Attributes instance after this call returns, so take
            // a defensive copy for callers that want to inspect the root element's attributes later.
            rootAttributes = new AttributesImpl(attributes);
            isFirstElement = false;
        }
    }

    /**
     * Get namespace of root element. To be used for reporting.
     *
     * @return Namespace of root element.
     */
    public String getNamespaceUri() {
        return namespaceUri;
    }

    /**
     * Get the attributes of the root element, as seen during parsing.
     *
     * @return the root element's attributes, or {@code null} if no element has been seen yet.
     */
    public Attributes getRootAttributes() {
        return rootAttributes;
    }
}