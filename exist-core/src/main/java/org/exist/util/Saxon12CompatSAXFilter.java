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
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.XMLConstants;

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
 * passing everything else through unchanged. It also implements {@link LexicalHandler},
 * forwarding to the delegate when the delegate implements it, so wrapping a handler in
 * this filter does not silently drop comment/CDATA/DTD events for callers (such as
 * {@link org.exist.util.serializer.ReceiverToSAX}) that detect lexical-handler support
 * via {@code instanceof}.
 */
public class Saxon12CompatSAXFilter implements ContentHandler, LexicalHandler {

    private static final Logger LOG = LogManager.getLogger(Saxon12CompatSAXFilter.class);

    private final ContentHandler delegate;
    private final LexicalHandler lexicalDelegate;
    private boolean documentStarted = false;

    public Saxon12CompatSAXFilter(final ContentHandler delegate) {
        this.delegate = delegate;
        this.lexicalDelegate = delegate instanceof LexicalHandler lh ? lh : null;
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
        // Always suppress — callers are expected to invoke the delegate's endDocument()
        // directly themselves once the SAX pipeline this filter guards has finished (see
        // StylesheetResolverAndCompiler, EXistDbXMLReader, XSLTServlet), so an endDocument
        // arriving here is always the duplicate that Saxon 12's LinkedTreeBuilder rejects
        // on a second call (it NPEs rather than ignoring it, unlike startDocument).
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
        if ("xml".equals(prefix) || XMLConstants.XML_NS_URI.equals(uri)) {
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
        delegate.startElement(uri, localName, qName, sanitizeXmlNamespaceAttributes(atts));
    }

    /**
     * The {@code xml} prefix is fixed by the XML namespaces spec to always be bound to
     * {@code http://www.w3.org/XML/1998/namespace}, and no other prefix is ever legally
     * used for that URI. eXist's persistent node layer has been observed to occasionally
     * hand back an attribute qName with a different, spurious prefix for that URI (see
     * #3417) even though the attribute's own uri/localName are correct; Saxon 12 rejects
     * such a mismatch outright with "Invalid prefix for XML namespace" instead of ignoring
     * it. Rather than trust whatever prefix eXist supplied, this always rewrites an
     * xml-namespaced attribute's qName to the canonical {@code xml:}-prefixed form.
     */
    private static Attributes sanitizeXmlNamespaceAttributes(final Attributes atts) {
        AttributesImpl sanitized = null;
        for (int i = 0; i < atts.getLength(); i++) {
            if (XMLConstants.XML_NS_URI.equals(atts.getURI(i))) {
                final String canonicalQName = XMLConstants.XML_NS_PREFIX + ':' + atts.getLocalName(i);
                if (!canonicalQName.equals(atts.getQName(i))) {
                    if (sanitized == null) {
                        sanitized = new AttributesImpl(atts);
                    }
                    sanitized.setQName(i, canonicalQName);
                }
            }
        }
        return sanitized == null ? atts : sanitized;
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

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.startDTD(name, publicId, systemId);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.endDTD();
        }
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.startEntity(name);
        }
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.endEntity(name);
        }
    }

    @Override
    public void startCDATA() throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.startCDATA();
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.endCDATA();
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        if (lexicalDelegate != null) {
            lexicalDelegate.comment(ch, start, length);
        }
    }
}
