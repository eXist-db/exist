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
package org.exist.dom.memtree;

import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.exist.dom.persistent.NodeProxy;
import org.exist.util.serializer.AttrList;
import org.exist.util.serializer.Receiver;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;


/**
 * Builds an in-memory DOM tree from SAX {@link org.exist.util.serializer.Receiver} events.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang</a>
 */
public class DocumentBuilderReceiver implements ContentHandler, LexicalHandler, Receiver {

    private MemTreeBuilder builder = null;
    private final boolean explicitNSDecl;

    private Map<String, String> namespaces = null;
    private boolean checkNS = false;

    private boolean suppressWhitespace = true;

    private final Expression expression;

    public DocumentBuilderReceiver() {
        this((Expression) null);
    }

    public DocumentBuilderReceiver(final Expression expression) {
        this(expression, null);
    }

    public DocumentBuilderReceiver(final MemTreeBuilder builder) {
        this(null, builder);
    }

    public DocumentBuilderReceiver(final Expression expression, final MemTreeBuilder builder) {
        this(expression, builder, false);
    }

    public DocumentBuilderReceiver(final MemTreeBuilder builder, final boolean declareNamespaces) {
        this(null, builder, declareNamespaces);
    }

    public DocumentBuilderReceiver(final Expression expression, final MemTreeBuilder builder, final boolean declareNamespaces) {
        this.expression = expression;
        this.builder = builder;
        this.explicitNSDecl = declareNamespaces;
    }

    public Expression getExpression() {
        return expression;
    }

    public void setCheckNS(final boolean checkNS) {
        this.checkNS = checkNS;
    }

    public void setSuppressWhitespace(boolean flag) {
        this.suppressWhitespace = flag;
    }

    @Override
    public Document getDocument() {
        return builder.getDocument();
    }

    public XQueryContext getContext() {
        return builder.getContext();
    }

    @Override
    public void setDocumentLocator(Locator locator) {
    }

    @Override
    public void startDocument() throws SAXException {
        if(builder == null) {
            builder = new MemTreeBuilder(expression);
            builder.startDocument();
        }
    }

    @Override
    public void endDocument() throws SAXException {
        builder.endDocument();
    }

    @Override
    public void declaration(final String version, final String encoding, final String standalone) throws SAXException {
        // NOTE(AR) in-memory documents do not support XML Declaration
    }

    @Override
    public void startPrefixMapping(final String prefix, final String namespaceURI) throws SAXException {
        if(prefix == null || prefix.isEmpty()) {
            builder.setDefaultNamespace(namespaceURI);
        }
        if(!explicitNSDecl) {
            return;
        }
        if(namespaces == null) {
            namespaces = new HashMap<>();
        }
        namespaces.put(prefix, namespaceURI);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        if(prefix == null || prefix.isEmpty()) {
            builder.setDefaultNamespace(XMLConstants.NULL_NS_URI);
        }
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName,
                             final Attributes attrs) throws SAXException {
        builder.startElement(namespaceURI, localName, qName, attrs);
        declareNamespaces();
    }

    private void declareNamespaces() {
        if(explicitNSDecl && namespaces != null) {
            for(final Map.Entry<String, String> entry : namespaces.entrySet()) {
                builder.namespaceNode(entry.getKey(), entry.getValue());
            }
            namespaces.clear();
        }
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) {
        builder.startElement(checkNS(true, qname), null);
        declareNamespaces();
        if(attribs != null) {
            for(int i = 0; i < attribs.getLength(); i++) {
                builder.addAttribute(attribs.getQName(i), attribs.getValue(i));
            }
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        builder.endElement();
    }

    @Override
    public void endElement(final QName qname) throws SAXException {
        builder.endElement();
    }

    public void addReferenceNode(final NodeProxy proxy) throws SAXException {
        builder.addReferenceNode(proxy);
    }

    public void addNamespaceNode(final QName qname) throws SAXException {
        builder.namespaceNode(qname, checkNS);
    }

    @Override
    public void characters(final CharSequence seq) throws SAXException {
        builder.characters(seq);
    }

    @Override
    public void characters(final char[] ch, final int start, final int len) throws SAXException {
        builder.characters(ch, start, len);
    }

    @Override
    public void attribute(final QName qname, final String value) throws SAXException {
        try {
            // Attribute namespace handling must be independent of the checkNS
            // flag: when an attribute is copied into a new element constructor
            // (XQuery 3.1 §3.9.1.3, default copy-namespaces preserve), the
            // attribute's prefix MUST be rebound if it conflicts with an
            // in-scope binding on the new element, and its (prefix, URI)
            // mapping MUST be reflected as a namespace node on that element.
            final QName resolved = qname.hasNamespace()
                    ? resolveAttributeQName(qname)
                    : qname;
            builder.addAttribute(resolved, value);
        } catch(final DOMException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /**
     * Resolves a namespaced attribute QName against the current in-scope
     * namespaces, rebinding to a freshly generated prefix on prefix-to-URI
     * conflict, and emitting a namespace node on the parent element to make
     * the binding visible to the serializer.
     */
    private QName resolveAttributeQName(final QName qname) {
        final XQueryContext context = builder.getContext();
        final String uri = qname.getNamespaceURI();
        String prefix = qname.getPrefix();
        if (prefix == null || prefix.isEmpty()) {
            // Attribute with namespace but no prefix: pick an existing prefix
            // mapped to this URI, or generate a fresh one.
            final String existing = context == null ? null : context.getInScopePrefix(uri);
            if (existing != null && !existing.isEmpty()) {
                prefix = existing;
            } else {
                prefix = generatePrefix(context, null);
                if (context != null) {
                    context.declareInScopeNamespace(prefix, uri);
                }
                emitNamespaceNode(prefix, uri);
                return new QName(qname.getLocalPart(), uri, prefix);
            }
        } else if (context != null) {
            final String boundUri = context.getInScopeNamespace(prefix);
            if (boundUri == null) {
                // Prefix is not in scope -> declare it
                context.declareInScopeNamespace(prefix, uri);
            } else if (!boundUri.equals(uri)) {
                // Prefix is bound to a different URI -> generate a fresh prefix
                String reuse = context.getInScopePrefix(uri);
                if (reuse == null || reuse.isEmpty()) {
                    prefix = generatePrefix(context, null);
                    context.declareInScopeNamespace(prefix, uri);
                } else {
                    prefix = reuse;
                }
            }
        }
        emitNamespaceNode(prefix, uri);
        return new QName(qname.getLocalPart(), uri, prefix);
    }

    /**
     * Adds an xmlns:prefix=uri namespace node to the current element when not
     * already declared there. No-op for the {@code xml} prefix or when the
     * parent node is not an element.
     */
    private void emitNamespaceNode(final String prefix, final String uri) {
        if (prefix == null || prefix.isEmpty() || XMLConstants.XML_NS_PREFIX.equals(prefix)) {
            return;
        }
        final DocumentImpl doc = builder.getDocument();
        final int parent = doc.getLastNode();
        if (parent < 0 || doc.getNodeType(parent) != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }
        final QName parentName = doc.nodeName[parent];
        if (parentName != null && prefix.equals(parentName.getPrefix())
                && uri.equals(parentName.getNamespaceURI())) {
            return;
        }
        final int firstNs = doc.alphaLen[parent];
        if (firstNs >= 0) {
            for (int ns = firstNs;
                 ns < doc.nextNamespace && doc.namespaceParent[ns] == parent;
                 ns++) {
                final QName nsName = doc.namespaceCode[ns];
                if (nsName != null && prefix.equals(nsName.getLocalPart())) {
                    return;
                }
            }
        }
        builder.namespaceNode(prefix, uri);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int len) throws SAXException {
        if (!suppressWhitespace) {
            builder.characters(ch, start, len);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        builder.processingInstruction(target, data);
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws SAXException {
        builder.cdataSection(new String(ch, start, len));
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
    }

    @Override
    public void endCDATA() throws SAXException {
    }

    @Override
    public void endDTD() throws SAXException {
    }

    @Override
    public void startCDATA() throws SAXException {
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws SAXException {
        builder.documentType(name, publicId, systemId);
    }

    @Override
    public void comment(final char[] ch, final int start, final int length) throws SAXException {
        builder.comment(ch, start, length);
    }

    @Override
    public void endEntity(final String name) throws SAXException {
    }

    @Override
    public void startEntity(final String name) throws SAXException {
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
    }

    @Override
    public void highlightText(final CharSequence seq) {
        // not supported with this receiver
    }

    @Override
    public void setCurrentNode(final INodeHandle node) {
        // ignored
    }

    private QName checkNS(boolean isElement, final QName qname) {
        if(checkNS) {
            final XQueryContext context = builder.getContext();
            if(qname.getPrefix() == null) {
                if(!qname.hasNamespace()) {
                    return qname;
                } else if(isElement) {
                    return qname;
                } else {
                    final String prefix = generatePrefix(context, context.getInScopePrefix(qname.getNamespaceURI()));
                    context.declareInScopeNamespace(prefix, qname.getNamespaceURI());
                    return new QName(qname.getLocalPart(), qname.getNamespaceURI(), prefix);
                }
            }

            if(qname.getPrefix().isEmpty() && qname.getNamespaceURI() == null) {
                return qname;
            }

            final String inScopeNamespace = context.getInScopeNamespace(qname.getPrefix());
            if(inScopeNamespace == null) {
                context.declareInScopeNamespace(qname.getPrefix(), qname.getNamespaceURI());
            } else if(!inScopeNamespace.equals(qname.getNamespaceURI())) {
                final String prefix = generatePrefix(context, context.getInScopePrefix(qname.getNamespaceURI()));
                context.declareInScopeNamespace(prefix, qname.getNamespaceURI());
                return new QName(qname.getLocalPart(), qname.getNamespaceURI(), prefix);
            }
        }
        return qname;
    }

    private String generatePrefix(final XQueryContext context, String prefix) {
        int i = 0;
        while(prefix == null) {
            prefix = "XXX";
            if(i > 0) {
                prefix += String.valueOf(i);
            }
            if(context.getInScopeNamespace(prefix) != null) {
                prefix = null;
                i++;
            }
        }
        return prefix;
    }
}