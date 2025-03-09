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
package org.exist.util.serializer;

import org.exist.Namespaces;
import org.exist.dom.INodeHandle;
import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.XMLString;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.NamespaceSupport;

import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.transform.TransformerException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class SAXSerializer extends AbstractSerializer implements ContentHandler, LexicalHandler, Receiver {

    private final NamespaceSupport nsSupport = new NamespaceSupport();
    private final Map<String, String> namespaceDecls = new HashMap<>();
    private final Map<String, String> optionalNamespaceDecls = new HashMap<>();
    private boolean enforceXHTML = false;

    public SAXSerializer() {
        super();
    }

    public SAXSerializer(final Writer writer, final Properties outputProperties) {
        super();
        setOutput(writer, outputProperties);
    }

    @Override
    public final void setOutput(final Writer writer, final Properties properties) {
        super.setOutput(writer, properties);

        // if set, enforce XHTML namespace on elements with no namespace
        final String xhtml = outputProperties.getProperty(EXistOutputKeys.ENFORCE_XHTML, "no");
        enforceXHTML = "yes".equalsIgnoreCase(xhtml);
    }

    public Writer getWriter() {
        return receiver.getWriter();
    }

    public void setReceiver(final SerializerWriter receiver) {
        this.receiver = receiver;
    }

    @Override
    public void reset() {
        super.reset();
        nsSupport.reset();
        namespaceDecls.clear();
        optionalNamespaceDecls.clear();
    }

    @Override
    public void setDocumentLocator(final Locator locator) {
        //Nothing to do ?
    }

    @Override
    public void startDocument() throws SAXException {
        try {
            receiver.startDocument();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endDocument() throws SAXException {
        try {
            receiver.endDocument();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void declaration(@Nullable final String version, @Nullable final String encoding, @Nullable final String standalone) throws SAXException {
        try {
            receiver.declaration(version, encoding, standalone);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void startPrefixMapping(String prefix, String namespaceURI) throws SAXException {
        if (namespaceURI.equals(Namespaces.XML_NS)) {
            return;
        }
        if(prefix == null) {
            prefix = XMLConstants.DEFAULT_NS_PREFIX;
        }
        final String ns = nsSupport.getURI(prefix);
        if (enforceXHTML && !Namespaces.XHTML_NS.equals(namespaceURI)) {
            namespaceURI = Namespaces.XHTML_NS;
        }
        if(ns == null || (!ns.equals(namespaceURI))) {
            optionalNamespaceDecls.put(prefix, namespaceURI);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        optionalNamespaceDecls.remove(prefix);
    }

    @Override
    public void startElement(String namespaceURI, final String localName, final String qname, final Attributes attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            receiver.startElement(namespaceURI, localName, qname);
            String elemPrefix = XMLConstants.DEFAULT_NS_PREFIX;
            int p = qname.indexOf(':');
            if (p > 0) {
                elemPrefix = qname.substring(0, p);
            }
            if (namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            if (enforceXHTML && elemPrefix.isEmpty() && namespaceURI.isEmpty()) {
                namespaceURI = Namespaces.XHTML_NS;
            }
            if (nsSupport.getURI(elemPrefix) == null) {
                namespaceDecls.put(elemPrefix, namespaceURI);
                nsSupport.declarePrefix(elemPrefix, namespaceURI);
            }
            // check attributes for required namespace declarations
            String attrName;
            String uri;
            if(attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    attrName = attribs.getQName(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrName)) {
                        if (nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX) == null) {
                            uri = attribs.getValue(i);
                            if (enforceXHTML && !Namespaces.XHTML_NS.equals(uri)) {
                                uri = Namespaces.XHTML_NS;
                            }
                            namespaceDecls.put(XMLConstants.DEFAULT_NS_PREFIX, uri);
                            nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, uri);
                        }
                    } else if (attrName.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                        final String prefix = attrName.substring(6);
                        if (nsSupport.getURI(prefix) == null) {
                            uri = attribs.getValue(i);
                            namespaceDecls.put(prefix, uri);
                            nsSupport.declarePrefix(prefix, uri);
                        }
                    } else if ((p = attrName.indexOf(':')) > 0) {
                        final String prefix = attrName.substring(0, p);
                        uri = attribs.getURI(i);
                        if (nsSupport.getURI(prefix) == null) {
                            namespaceDecls.put(prefix, uri);
                            nsSupport.declarePrefix(prefix, uri);
                        }
                    }
                }
            }
            for (final Map.Entry<String, String> nsEntry : optionalNamespaceDecls.entrySet()) {
                final String prefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                receiver.namespace(prefix, uri);
                nsSupport.declarePrefix(prefix, uri); //nsSupport.declarePrefix(prefix, namespaceURI);
            }
            // output all namespace declarations
            for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
                final String prefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                if(!optionalNamespaceDecls.containsKey(prefix)) {
                    receiver.namespace(prefix, uri);
                }
            }
            //cancels current xmlns if relevant
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(elemPrefix) && !namespaceURI.equals(receiver.getDefaultNamespace())) {
                receiver.namespace(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
                nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
            }
            optionalNamespaceDecls.clear();
            // output attributes
            if(attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    if (!attribs.getQName(i).startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                        receiver.attribute(attribs.getQName(i), attribs.getValue(i));
                    }
                }
            }
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            String prefix = qname.getPrefix();
            String namespaceURI = qname.getNamespaceURI();
            if(prefix == null) {
                prefix = XMLConstants.DEFAULT_NS_PREFIX;
            }
            if(namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            if(enforceXHTML && prefix.isEmpty() && namespaceURI.isEmpty()) {
                namespaceURI = Namespaces.XHTML_NS;
                receiver.startElement(new QName(qname.getLocalPart(), namespaceURI, qname.getPrefix()));
            } else {
                receiver.startElement(qname);
            }
            if (nsSupport.getURI(prefix) == null) {
                namespaceDecls.put(prefix, namespaceURI);
                nsSupport.declarePrefix(prefix, namespaceURI);
            }
            // check attributes for required namespace declarations
            QName attrQName;
            String uri;
            if(attribs != null) {
                for (int i = 0; i < attribs.getLength(); i++) {
                    attrQName = attribs.getQName(i);
                    if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrQName.getLocalPart())) {
                        if (nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX) == null) {
                            uri = attribs.getValue(i);
                            if (enforceXHTML && !Namespaces.XHTML_NS.equals(uri)) {
                                uri = Namespaces.XHTML_NS;
                            }
                            namespaceDecls.put(XMLConstants.DEFAULT_NS_PREFIX, uri);
                            nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, uri);
                        }
                    } else if (attrQName.getPrefix() != null && !attrQName.getPrefix().isEmpty()) {
                        prefix = attrQName.getPrefix();
                        if (nsSupport.getURI(prefix) == null) {
                            uri = attrQName.getNamespaceURI();
                            namespaceDecls.put(prefix, uri);
                            nsSupport.declarePrefix(prefix, uri);
                        }
                    }
                }
            }
            String optPrefix;
            for (final Map.Entry<String, String> nsEntry : optionalNamespaceDecls.entrySet()) {
                optPrefix = nsEntry.getKey();
                uri = nsEntry.getValue(); 
                receiver.namespace(optPrefix, uri);
                nsSupport.declarePrefix(optPrefix, uri);
            }
            // output all namespace declarations
            for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
                optPrefix = nsEntry.getKey();
                if (XMLConstants.XMLNS_ATTRIBUTE.equals(optPrefix)) {
                    continue;
                }
                uri = nsEntry.getValue(); 
                if(!optionalNamespaceDecls.containsKey(optPrefix)) {
                    receiver.namespace(optPrefix, uri);
                }
            }
            optionalNamespaceDecls.clear();
            //cancels current xmlns if relevant
            if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix) && !namespaceURI.equals(receiver.getDefaultNamespace())) {
                receiver.namespace(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
                nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
            }
            if(attribs != null) {
                // output attributes
                for (int i = 0; i < attribs.getLength(); i++) {
                    if (!attribs.getQName(i).getLocalPart().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                        receiver.attribute(attribs.getQName(i), attribs.getValue(i));
                    }
                }
            }
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endElement(final String namespaceURI, final String localName, final String qname) throws SAXException {
        try {
            nsSupport.popContext();
            receiver.endElement(namespaceURI, localName, qname);
            receiver.setDefaultNamespace(nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endElement(final QName qname) throws SAXException {
        try {
            nsSupport.popContext();
            String prefix = qname.getPrefix();
            String namespaceURI = qname.getNamespaceURI();
            if(prefix == null) {
                prefix = XMLConstants.DEFAULT_NS_PREFIX;
            }
            
            if(namespaceURI == null) {
                namespaceURI = XMLConstants.NULL_NS_URI;
            }
            
            if(enforceXHTML && prefix.isEmpty() && namespaceURI.isEmpty()) {
                namespaceURI = Namespaces.XHTML_NS;
                receiver.endElement(new QName(qname.getLocalPart(), namespaceURI, qname.getPrefix()));
            } else {
                receiver.endElement(qname);
            }
            receiver.setDefaultNamespace(nsSupport.getURI(XMLConstants.DEFAULT_NS_PREFIX));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void attribute(final QName qname, final String value) throws SAXException {
        // ignore namespace declaration attributes
        if((qname.getPrefix() != null && XMLConstants.XMLNS_ATTRIBUTE.equals(qname.getPrefix())) || XMLConstants.XMLNS_ATTRIBUTE.equals(qname.getLocalPart())) {
            return;
        }
        
        try {
            receiver.attribute(qname, value);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void characters(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.characters(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void characters(final CharSequence seq) throws SAXException {
        try {
            receiver.characters(seq);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void ignorableWhitespace(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.characters(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void processingInstruction(final String target, final String data) throws SAXException {
        try {
            receiver.processingInstruction(target, data);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void cdataSection(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.cdataSection(ch, start, len);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void skippedEntity(final String name) throws SAXException {
        //Nothing to do
    }

    @Override
    public void startDTD(final String name, final String publicId, final String systemId) throws SAXException {
        try {
            receiver.startDocumentType(name, publicId, systemId);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endDTD() throws SAXException {
        try {
            receiver.endDocumentType();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void documentType(final String name, final String publicId, final String systemId) throws SAXException {
        try {
            receiver.documentType(name, publicId, systemId);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void highlightText(final CharSequence seq) {
        // not supported with this receiver
    }

    @Override
    public void startEntity(final String name) throws SAXException {
        //Nothing to do
    }

    @Override
    public void endEntity(final String name) throws SAXException {
        //Nothing to do
    }

    @Override
    public void startCDATA() throws SAXException {
        try {
            receiver.startCdataSection();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void endCDATA() throws SAXException {
        try {
            receiver.endCdataSection();
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void comment(final char[] ch, final int start, final int len) throws SAXException {
        try {
            receiver.comment(new XMLString(ch, start, len));
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void setCurrentNode(final INodeHandle node) {
        // just ignore.
    }

    @Override
    public Document getDocument() {
        //just ignore.
        return null;
    }
}
