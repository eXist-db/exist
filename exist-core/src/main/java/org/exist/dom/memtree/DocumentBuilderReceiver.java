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
            builder.addAttribute(checkNS(false, qname), value);
        } catch(final DOMException e) {
            throw new SAXException(e.getMessage(), e);
        }
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