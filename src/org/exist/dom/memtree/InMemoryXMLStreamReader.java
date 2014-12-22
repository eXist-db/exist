/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.dom.memtree;

import org.exist.dom.QName;
import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.w3c.dom.Node;

import javax.xml.namespace.NamespaceContext;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;


/**
 * Implementation of a StAX {@link javax.xml.stream.XMLStreamReader}, which wraps around eXist's in-memory DOM.
 * This class complements {@link org.exist.stax.EmbeddedXMLStreamReader} which reads persistent documents.
 */
public class InMemoryXMLStreamReader implements ExtendedXMLStreamReader {
    private final DocumentImpl doc;
    private final NodeImpl rootNode;
    private int currentNode;

    private int state = XMLStreamReader.START_DOCUMENT;

    public InMemoryXMLStreamReader(final DocumentImpl doc, final NodeImpl node) {
        this.doc = doc;
        this.rootNode = node;
        this.currentNode = -1;
    }

    @Override
    public Object getProperty(final String name) throws IllegalArgumentException {
        if(name.equals(PROPERTY_NODE_ID)) {

            if(currentNode < 0 || currentNode >= doc.size) {
                return null;
            }
            doc.expand();
            return doc.nodeId[currentNode];
        }
        return null;
    }

    @Override
    public int next() throws XMLStreamException {
        if(currentNode > -1) {
            int next = -1;

            if(state == XMLStreamReader.START_ELEMENT || state == XMLStreamReader.START_DOCUMENT) {
                next = doc.getFirstChildFor(currentNode);

                if(next < 0) { // no child nodes
                    state = XMLStreamReader.END_ELEMENT;
                    return state;
                }
            }

            if(next < 0) {
                next = doc.next[currentNode];

                if(next < currentNode) {

                    if(next == 0) {
                        state = XMLStreamReader.END_DOCUMENT;
                    } else {
                        state = XMLStreamReader.END_ELEMENT;
                    }
                    currentNode = next;
                    return state;
                }
            }
            currentNode = next;
        } else {
            currentNode = rootNode.getNodeNumber();
        }

        switch(doc.nodeKind[currentNode]) {

            case Node.TEXT_NODE: {
                state = XMLStreamReader.CHARACTERS;
                break;
            }

            case Node.CDATA_SECTION_NODE: {
                state = XMLStreamReader.CDATA;
                break;
            }

            case Node.COMMENT_NODE: {
                state = XMLStreamReader.COMMENT;
                break;
            }

            case Node.PROCESSING_INSTRUCTION_NODE: {
                state = XMLStreamReader.PROCESSING_INSTRUCTION;
                break;
            }

            case Node.ELEMENT_NODE: {
                state = XMLStreamReader.START_ELEMENT;
                break;
            }
        }
        return state;
    }

    @Override
    public void require(final int type, final String namespaceURI, final String localName) throws XMLStreamException {
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if(getEventType() != START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text");
        }
        int eventType = next();
        final StringBuilder content = new StringBuilder();

        while(eventType != END_ELEMENT) {

            if(eventType == CHARACTERS || eventType == CDATA || eventType == SPACE || eventType == ENTITY_REFERENCE) {
                content.append(getText());
            } else if(eventType == PROCESSING_INSTRUCTION || eventType == COMMENT) {
                // skipping
            } else if(eventType == END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content");
            } else if(eventType == START_ELEMENT) {
                throw new XMLStreamException("element text content may not contain START_ELEMENT");
            } else {
                throw new XMLStreamException("Unexpected event type " + eventType);
            }
            eventType = next();
        }
        return content.toString();
    }

    @Override
    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        return currentNode != rootNode.getNodeNumber() || state == XMLStreamReader.START_DOCUMENT || state == XMLStreamReader.START_ELEMENT;
    }

    @Override
    public void close() throws XMLStreamException {
    }

    @Override
    public String getNamespaceURI(final String prefix) {
        return null;
    }

    @Override
    public boolean isStartElement() {
        return state == XMLStreamReader.START_ELEMENT;
    }

    @Override
    public boolean isEndElement() {
        return state == XMLStreamReader.END_ELEMENT;
    }

    @Override
    public boolean isCharacters() {
        return state == XMLStreamReader.CHARACTERS;
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public String getAttributeValue(final String namespaceURI, final String localName) {
        final int attrCount = doc.getAttributesCountFor(currentNode);

        if(attrCount == 0) {
            return null;
        }
        final int attrStart = doc.alpha[currentNode];

        for(int i = 0; i < attrCount; i++) {
            final QName qname = doc.attrName[attrStart + i];

            if((namespaceURI == null || namespaceURI.equals(qname.getNamespaceURI())) && localName.equals(qname.getLocalPart())) {
                return doc.attrValue[attrStart + i];
            }
        }
        return null;
    }

    @Override
    public int getAttributeCount() {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return doc.getAttributesCountFor(currentNode);
    }

    @Override
    public QName getAttributeQName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        }
        final int attr = doc.alpha[currentNode];
        return doc.attrName[attr + index];
    }

    @Override
    public javax.xml.namespace.QName getAttributeName(final int index) {
        return getAttributeQName(index).toJavaQName();
    }

    @Override
    public String getAttributeNamespace(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return getAttributeQName(index).getNamespaceURI();
    }

    @Override
    public String getAttributeLocalName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return getAttributeQName(index).getLocalPart();
    }

    @Override
    public String getAttributePrefix(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return getAttributeQName(index).getPrefix();
    }

    @Override
    public NodeId getAttributeId(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        }
        doc.expand();
        final int attr = doc.alpha[currentNode];
        return doc.attrNodeId[attr + index];
    }

    @Override
    public String getAttributeType(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        }
        final int attr = doc.alpha[currentNode];
        final int type = doc.attrType[attr + index];

        switch(type) {

            case AttrImpl.ATTR_ID_TYPE: {
                return "ID";
            }

            case AttrImpl.ATTR_IDREF_TYPE: {
                return "IDREF";
            }

            case AttrImpl.ATTR_IDREFS_TYPE: {
                return "IDREFS";
            }

            default: {
                return "CDATA";
            }
        }
    }

    @Override
    public String getAttributeValue(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }

        if(index > getAttributeCount()) {
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        }
        final int attr = doc.alpha[currentNode];
        return doc.attrValue[attr + index];
    }

    @Override
    public boolean isAttributeSpecified(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return true;
    }

    @Override
    public int getNamespaceCount() {
        if(state != START_ELEMENT && state != END_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        return doc.getNamespacesCountFor(currentNode);
    }

    @Override
    public String getNamespacePrefix(final int index) {
        if(index > getNamespaceCount()) {
            throw new ArrayIndexOutOfBoundsException("bad namespace index");
        }
        final int ns = doc.alphaLen[currentNode];
        final QName nsQName = doc.namespaceCode[ns + index];
        return nsQName.getLocalPart();
    }

    @Override
    public String getNamespaceURI(final int index) {
        if(index > getNamespaceCount()) {
            throw new ArrayIndexOutOfBoundsException("bad namespace index");
        }
        final int ns = doc.alphaLen[currentNode];
        final QName nsQName = doc.namespaceCode[ns + index];
        return nsQName.getNamespaceURI();
    }

    @Override
    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getEventType() {
        return state;
    }

    @Override
    public String getText() {
        if(state == CHARACTERS || state == COMMENT || state == CDATA) {
            return new String(doc.characters, doc.alpha[currentNode], doc.alphaLen[currentNode]);
        }
        return "";
    }

    @Override
    public char[] getTextCharacters() {
        final char[] ch = new char[doc.alphaLen[currentNode]];
        System.arraycopy(doc.characters, doc.alpha[currentNode], ch, 0, ch.length);
        return ch;
    }

    @Override
    public int getTextCharacters(final int sourceStart, final char[] target, final int targetStart, final int length) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextStart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextLength() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getEncoding() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasText() {
        return state == CHARACTERS || state == COMMENT || state == CDATA;
    }

    @Override
    public Location getLocation() {
        throw new UnsupportedOperationException();
    }

    @Override
    public QName getQName() {
        if(state == START_ELEMENT || state == END_ELEMENT) {
            return doc.nodeName[currentNode];
        }
        throw new IllegalStateException("Cursor is not at an element");
    }

    @Override
    public javax.xml.namespace.QName getName() {
        return getQName().toJavaQName();
    }

    @Override
    public String getLocalName() {
        return getQName().getLocalPart();
    }

    @Override
    public boolean hasName() {
        return state == START_ELEMENT || state == END_ELEMENT;
    }

    @Override
    public String getNamespaceURI() {
        return getQName().getNamespaceURI();
    }

    @Override
    public String getPrefix() {
        return getQName().getPrefix();
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public boolean isStandalone() {
        return false;
    }

    @Override
    public boolean standaloneSet() {
        return false;
    }

    @Override
    public String getCharacterEncodingScheme() {
        return null;
    }

    @Override
    public String getPITarget() {
        final QName qn = doc.nodeName[currentNode];
        return qn != null ? qn.getLocalPart() : null;
    }

    @Override
    public String getPIData() {
        return new String(doc.characters, doc.alpha[currentNode], doc.alphaLen[currentNode]);
    }
}
