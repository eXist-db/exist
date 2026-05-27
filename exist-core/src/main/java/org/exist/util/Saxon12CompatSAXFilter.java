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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * A SAX ContentHandler filter that adapts eXist's serializer output to Saxon 12's
 * stricter SAX expectations. Saxon 12's {@code LinkedTreeBuilder} rejects two patterns
 * that earlier Saxon versions tolerated:
 *
 * <ol>
 *   <li>Duplicate {@link #startDocument()} calls — emitted when eXist's Serializer
 *       sends its own document events on top of an explicit {@code startDocument}
 *       from the XSLT compilation pipeline.</li>
 *   <li>Namespace declarations for the implicit {@code xml} prefix
 *       (URI {@code http://www.w3.org/XML/1998/namespace}) — eXist's persistent DOM
 *       stores these in its namespace mappings, but Saxon 12 treats redeclaring the
 *       xml namespace as an error.</li>
 * </ol>
 *
 * This filter wraps a delegate handler and silently drops the offending events while
 * passing everything else through unchanged.
 */
public class Saxon12CompatSAXFilter implements ContentHandler {

    private static final Logger LOG = LogManager.getLogger(Saxon12CompatSAXFilter.class);

    private final ContentHandler delegate;
    private boolean documentStarted = false;

    public Saxon12CompatSAXFilter(final ContentHandler delegate) {
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
        // Suppress — the caller will call endDocument on the delegate directly.
        // If endDocument arrives before any startDocument, that's a spurious SAX event
        // (a downstream issue rather than the duplicate-startDocument case this guard
        // exists for) — log at debug level so it's visible during diagnosis without
        // adding noise to normal operation.
        if (!documentStarted && LOG.isDebugEnabled()) {
            LOG.debug("endDocument received without a preceding startDocument; suppressing");
        }
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
