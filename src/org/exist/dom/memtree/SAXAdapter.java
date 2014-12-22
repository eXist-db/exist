/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.dom.memtree;

import org.w3c.dom.Document;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;

import org.exist.xquery.XQueryContext;

import javax.xml.XMLConstants;
import java.util.HashMap;
import java.util.Map;


/**
 * Adapter class to build an internal, in-memory DOM from a SAX stream.
 *
 * @author wolf
 */
public class SAXAdapter implements ContentHandler, LexicalHandler {
    private MemTreeBuilder builder;
    private HashMap<String, String> namespaces = null;
    private boolean replaceAttributeFlag;

    public SAXAdapter() {
        setBuilder(new MemTreeBuilder());
    }

    public SAXAdapter(final XQueryContext context) {
        setBuilder(new MemTreeBuilder(context));
    }

    protected final void setBuilder(final MemTreeBuilder builder) {
        this.builder = builder;
    }

    public DocumentImpl getDocument() {
        return builder.getDocument();
    }

    @Override
    public void endDocument() throws SAXException {
        builder.endDocument();
    }

    @Override
    public void startDocument() throws SAXException {
        builder.startDocument();
        if(replaceAttributeFlag) {
            builder.setReplaceAttributeFlag(replaceAttributeFlag);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int length) throws SAXException {
        builder.characters(ch, start, length);
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
        builder.characters(ch, start, length);
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        builder.processingInstruction(target, data);
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        if(namespaces == null) {
            namespaces = new HashMap<>();
        }
        namespaces.put(prefix, uri);
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qName) throws SAXException {
        builder.endElement();
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qName, final Attributes atts) throws SAXException {
        builder.startElement(namespaceURI, localName, qName, atts);

        if(namespaces != null) {
            for(final Map.Entry<String, String> entry : namespaces.entrySet()) {
                builder.namespaceNode(entry.getKey().toString(), entry.getValue().toString());
            }
        }

        for(int i = 0; i < atts.getLength(); i++) {
            if(atts.getQName(i).startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                final String prefix = atts.getLocalName(i);
                final String uri = atts.getValue(i);
                if(namespaces == null || !namespaces.containsKey(prefix)) {
                    builder.namespaceNode(prefix, uri);
                }
            }
        }
        namespaces = null;
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

    public void setReplaceAttributeFlag(final boolean replaceAttributeFlag) {
        this.replaceAttributeFlag = replaceAttributeFlag;
    }
}
