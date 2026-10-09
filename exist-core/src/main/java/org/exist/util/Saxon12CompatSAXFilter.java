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
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;

/**
 * A SAX ContentHandler filter that adapts eXist's serializer output to Saxon 12's
 * stricter SAX expectations. Saxon 12's {@code LinkedTreeBuilder} rejects patterns
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
 *   <li>An {@code xml:}-namespaced attribute reported with some other, spurious
 *       prefix (see #3417 / #6781) — Saxon 12 rejects this outright rather than
 *       tolerating it.</li>
 * </ol>
 *
 * This filter wraps a delegate {@link ContentHandler} (via {@link XMLFilterImpl},
 * which forwards every SAX event this class doesn't itself override) and silently
 * drops or rewrites the offending events while passing everything else through
 * unchanged.
 *
 * <p>It also implements {@link LexicalHandler}, forwarding to the delegate when the
 * delegate implements one, but this only benefits a caller that uses an instance of
 * this filter itself as the content handler passed to something that does its own
 * {@code instanceof LexicalHandler} detection — e.g. {@link
 * org.exist.util.serializer.ReceiverToSAX}'s constructor, which is how {@link
 * org.exist.http.servlets.XSLTServlet} uses this filter. A caller that instead goes
 * through {@link org.exist.storage.serializers.Serializer#setSAXHandlers(ContentHandler,
 * LexicalHandler)} with an explicit {@code null} lexical handler — as {@code
 * StylesheetResolverAndCompiler} and {@code EXistDbXMLReader} both do — gets no benefit
 * from this, since {@code setSAXHandlers} overwrites whatever {@code ReceiverToSAX}
 * would otherwise have auto-detected.</p>
 */
public class Saxon12CompatSAXFilter extends XMLFilterImpl implements LexicalHandler {

    private static final Logger LOG = LogManager.getLogger(Saxon12CompatSAXFilter.class);

    private final LexicalHandler lexicalDelegate;
    private boolean documentStarted = false;

    /** Prefixes whose {@link #startPrefixMapping(String, String)} call was suppressed,
     *  with a count to correctly pair nested/repeated declarations of the same prefix
     *  with their matching {@link #endPrefixMapping(String)} calls, which must be
     *  suppressed too — otherwise the delegate would see an endPrefixMapping with no
     *  matching start. */
    private final Map<String, Integer> suppressedPrefixMappings = new HashMap<>();

    public Saxon12CompatSAXFilter(final ContentHandler delegate) {
        setContentHandler(delegate);
        this.lexicalDelegate = delegate instanceof LexicalHandler lh ? lh : null;
    }

    @Override
    public void startDocument() throws SAXException {
        if (!documentStarted) {
            documentStarted = true;
            super.startDocument();
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
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        // Saxon 12 rejects any namespace declaration involving the XML namespace URI
        // (http://www.w3.org/XML/1998/namespace) — the xml prefix is always implicitly bound
        if ("xml".equals(prefix) || XMLConstants.XML_NS_URI.equals(uri)) {
            suppressedPrefixMappings.merge(prefix, 1, Integer::sum);
            return;
        }
        super.startPrefixMapping(prefix, uri);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        final Integer suppressedCount = suppressedPrefixMappings.get(prefix);
        if (suppressedCount != null) {
            if (suppressedCount == 1) {
                suppressedPrefixMappings.remove(prefix);
            } else {
                suppressedPrefixMappings.put(prefix, suppressedCount - 1);
            }
            return;
        }
        super.endPrefixMapping(prefix);
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts) throws SAXException {
        super.startElement(uri, localName, qName, sanitizeXmlNamespaceAttributes(atts));
    }

    /**
     * The {@code xml} prefix is fixed by the XML namespaces spec to always be bound to
     * {@code http://www.w3.org/XML/1998/namespace}, and no other prefix is ever legally
     * used for that URI. eXist's persistent node layer has been observed to occasionally
     * hand back an attribute qName with a different, spurious prefix for that URI (see
     * #3417, root cause still tracked under #6781) even though the attribute's own
     * uri/localName are correct; Saxon 12 rejects such a mismatch outright with "Invalid
     * prefix for XML namespace" instead of ignoring it. Rather than trust whatever prefix
     * eXist supplied, this always rewrites an xml-namespaced attribute's qName to the
     * canonical {@code xml:}-prefixed form.
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
