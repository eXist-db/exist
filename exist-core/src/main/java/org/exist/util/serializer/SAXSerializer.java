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
    public void startPrefixMapping(final String prefix, final String namespaceURI) throws SAXException {
        if (namespaceURI.equals(Namespaces.XML_NS)) {
            return;
        }
        final String declaredPrefix = prefix == null ? XMLConstants.DEFAULT_NS_PREFIX : prefix;
        final String ns = nsSupport.getURI(declaredPrefix);
        final String declaredNamespaceURI = enforceXHTML && !Namespaces.XHTML_NS.equals(namespaceURI) ? Namespaces.XHTML_NS : namespaceURI;
        if(ns == null || (!ns.equals(declaredNamespaceURI))) {
            optionalNamespaceDecls.put(declaredPrefix, declaredNamespaceURI);
        }
    }

    @Override
    public void endPrefixMapping(final String prefix) throws SAXException {
        optionalNamespaceDecls.remove(prefix);
    }

    @Override
    public void startElement(final String namespaceURI, final String localName, final String qname, final Attributes attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            receiver.startElement(namespaceURI, localName, qname);
            final String elemPrefix = prefixOf(qname);
            final String elementNamespaceURI = elementNamespace(namespaceURI, elemPrefix);
            declareIfNew(elemPrefix, elementNamespaceURI);
            declareNamespacesOf(attribs);
            writeNamespaceDeclarations(false);
            cancelDefaultNamespace(elemPrefix, elementNamespaceURI);
            optionalNamespaceDecls.clear();
            writeAttributes(attribs);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    @Override
    public void startElement(final QName qname, final AttrList attribs) throws SAXException {
        try {
            namespaceDecls.clear();
            nsSupport.pushContext();
            final String prefix = qname.getPrefix() == null ? XMLConstants.DEFAULT_NS_PREFIX : qname.getPrefix();
            final String declaredNamespaceURI = qname.getNamespaceURI() == null ? XMLConstants.NULL_NS_URI : qname.getNamespaceURI();
            final boolean xhtml = enforcesXhtml(prefix, declaredNamespaceURI);
            final String namespaceURI = xhtml ? Namespaces.XHTML_NS : declaredNamespaceURI;
            receiver.startElement(xhtml ? new QName(qname.getLocalPart(), namespaceURI, qname.getPrefix()) : qname);
            declareIfNew(prefix, namespaceURI);
            declareNamespacesOf(attribs);
            writeNamespaceDeclarations(true);
            optionalNamespaceDecls.clear();
            cancelDefaultNamespace(prefix, namespaceURI);
            writeAttributes(attribs);
        } catch (final TransformerException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private static String prefixOf(final String qname) {
        final int colon = qname.indexOf(':');
        return colon > 0 ? qname.substring(0, colon) : XMLConstants.DEFAULT_NS_PREFIX;
    }

    private boolean enforcesXhtml(final String prefix, final String namespaceURI) {
        return enforceXHTML && prefix.isEmpty() && namespaceURI.isEmpty();
    }

    private String elementNamespace(@Nullable final String namespaceURI, final String prefix) {
        final String declared = namespaceURI == null ? XMLConstants.NULL_NS_URI : namespaceURI;
        return enforcesXhtml(prefix, declared) ? Namespaces.XHTML_NS : declared;
    }

    /** The namespace of a default namespace declaration: the XHTML namespace if the serializer enforces it. */
    private String defaultNamespace(final String declared) {
        return enforceXHTML && !Namespaces.XHTML_NS.equals(declared) ? Namespaces.XHTML_NS : declared;
    }

    /**
     * Declares a prefix on the element unless it is declared already: in scope, or by the element itself. The second
     * test is needed for the default namespace of an element without namespace, which the namespace support does not
     * remember: without it, an attribute {@code xmlns} would declare a namespace that the element is not in.
     */
    private void declareIfNew(final String prefix, final String namespaceURI) {
        if (nsSupport.getURI(prefix) == null && !namespaceDecls.containsKey(prefix)) {
            namespaceDecls.put(prefix, namespaceURI);
            nsSupport.declarePrefix(prefix, namespaceURI);
        }
    }

    /** Declares the namespaces that the attributes of an element need: its own declarations and the prefixes it uses. */
    private void declareNamespacesOf(@Nullable final Attributes attribs) {
        if (attribs == null) {
            return;
        }
        for (int i = 0; i < attribs.getLength(); i++) {
            final String attrName = attribs.getQName(i);
            final int colon = attrName.indexOf(':');
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrName)) {
                declareIfNew(XMLConstants.DEFAULT_NS_PREFIX, defaultNamespace(attribs.getValue(i)));
            } else if (attrName.startsWith(XMLConstants.XMLNS_ATTRIBUTE + ":")) {
                declareIfNew(attrName.substring(XMLConstants.XMLNS_ATTRIBUTE.length() + 1), attribs.getValue(i));
            } else if (colon > 0) {
                declareIfNew(attrName.substring(0, colon), attribs.getURI(i));
            }
        }
    }

    /** Declares the namespaces that the attributes of an element need: its own declarations and the prefixes it uses. */
    private void declareNamespacesOf(@Nullable final AttrList attribs) {
        if (attribs == null) {
            return;
        }
        for (int i = 0; i < attribs.getLength(); i++) {
            final QName attrQName = attribs.getQName(i);
            final String attrPrefix = attrQName.getPrefix();
            if (XMLConstants.XMLNS_ATTRIBUTE.equals(attrQName.getLocalPart())) {
                declareIfNew(XMLConstants.DEFAULT_NS_PREFIX, defaultNamespace(attribs.getValue(i)));
            } else if (attrPrefix != null && !attrPrefix.isEmpty()) {
                declareIfNew(attrPrefix, attrQName.getNamespaceURI());
            }
        }
    }

    /**
     * Writes the namespace declarations of the element: those the caller asked for with a prefix mapping, then the
     * others that were collected.
     *
     * @param skipXmlnsPrefix whether a collected declaration of the prefix {@code xmlns} is left out
     */
    private void writeNamespaceDeclarations(final boolean skipXmlnsPrefix) throws TransformerException {
        for (final Map.Entry<String, String> nsEntry : optionalNamespaceDecls.entrySet()) {
            receiver.namespace(nsEntry.getKey(), nsEntry.getValue());
            nsSupport.declarePrefix(nsEntry.getKey(), nsEntry.getValue());
        }
        for (final Map.Entry<String, String> nsEntry : namespaceDecls.entrySet()) {
            final String prefix = nsEntry.getKey();
            if (skipXmlnsPrefix && XMLConstants.XMLNS_ATTRIBUTE.equals(prefix)) {
                continue;
            }
            if (!optionalNamespaceDecls.containsKey(prefix)) {
                receiver.namespace(prefix, nsEntry.getValue());
            }
        }
    }

    /** Cancels the default namespace in force if the element has none of its own, and the one in force is another. */
    private void cancelDefaultNamespace(final String prefix, final String namespaceURI) throws TransformerException {
        if (XMLConstants.DEFAULT_NS_PREFIX.equals(prefix) && !namespaceURI.equals(receiver.getDefaultNamespace())) {
            receiver.namespace(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
            nsSupport.declarePrefix(XMLConstants.DEFAULT_NS_PREFIX, namespaceURI);
        }
    }

    private void writeAttributes(@Nullable final Attributes attribs) throws TransformerException {
        if (attribs == null) {
            return;
        }
        for (int i = 0; i < attribs.getLength(); i++) {
            if (!attribs.getQName(i).startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                receiver.attribute(attribs.getQName(i), attribs.getValue(i));
            }
        }
    }

    private void writeAttributes(@Nullable final AttrList attribs) throws TransformerException {
        if (attribs == null) {
            return;
        }
        for (int i = 0; i < attribs.getLength(); i++) {
            if (!attribs.getQName(i).getLocalPart().startsWith(XMLConstants.XMLNS_ATTRIBUTE)) {
                receiver.attribute(attribs.getQName(i), attribs.getValue(i));
            }
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
    public void documentType(final String name, final String publicId, final String systemId, final String internalSubset) throws SAXException {
        try {
            receiver.documentType(name, publicId, systemId, internalSubset);
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
