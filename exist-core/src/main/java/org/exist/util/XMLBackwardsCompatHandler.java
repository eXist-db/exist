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
package org.exist.util;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * A SAX ContentHandler wrapper that suppresses duplicate startDocument/endDocument calls.
 * Saxon 12's LinkedTreeBuilder does not tolerate receiving startDocument more than once,
 * which can happen when eXist's Serializer sends document events that overlap with
 * explicitly-called startDocument/endDocument in the XSLT compilation pipeline.
 */
public class XMLBackwardsCompatHandler implements ContentHandler {

    private final ContentHandler delegate;
    private boolean documentStarted = false;

    public XMLBackwardsCompatHandler(final ContentHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void startDocument() throws SAXException {
        if (!documentStarted) {
            documentStarted = true;
            delegate.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        // Suppress — the caller will call endDocument on the delegate directly
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        delegate.setDocumentLocator(locator);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        // Saxon 12 rejects any namespace declaration involving the XML namespace URI
        // (http://www.w3.org/XML/1998/namespace) — the xml prefix is always implicitly bound
        if ("xml".equals(prefix) || javax.xml.XMLConstants.XML_NS_URI.equals(uri)) {
            return;
        }
        delegate.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        delegate.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        delegate.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        delegate.endElement(uri, localName, qName);
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        delegate.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        delegate.ignorableWhitespace(ch, start, length);
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        delegate.processingInstruction(target, data);
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        delegate.skippedEntity(name);
    }
}
