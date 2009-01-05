/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
package org.exist.memtree;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.exist.numbering.NodeId;
import org.exist.stax.ExtendedXMLStreamReader;
import org.w3c.dom.Node;

/**
 * Implementation of a StAX {@link javax.xml.stream.XMLStreamReader}, which wraps around
 * eXist's in-memory DOM. This class complements {@link org.exist.stax.EmbeddedXMLStreamReader}
 * which reads persistent documents.
 */
public class InMemoryXMLStreamReader implements ExtendedXMLStreamReader {

    protected DocumentImpl doc;
    protected int currentNode;
    protected int previous;
    protected NodeImpl rootNode;

    protected int state = XMLStreamReader.START_DOCUMENT;

    public InMemoryXMLStreamReader(DocumentImpl doc, NodeImpl node) {
        this.doc = doc;
        this.currentNode = -1;
        this.rootNode = node;
    }

    public Object getProperty(String name) throws IllegalArgumentException {
        if (name.equals(PROPERTY_NODE_ID)) {
            if (currentNode < 0 || currentNode >= doc.size)
                return null;
            doc.expand();
            return doc.nodeId[currentNode];
        }
        return null;
    }

    public int next() throws XMLStreamException {
        if (state != XMLStreamReader.END_ELEMENT) {
            previous = currentNode;
        }
        if (currentNode > -1) {
            int next = -1;
            if (state == XMLStreamReader.START_ELEMENT || state == XMLStreamReader.START_DOCUMENT) {
                next = doc.getFirstChildFor(currentNode);
                if (next < 0) { // no child nodes
                    state = XMLStreamReader.END_ELEMENT;
                    return state;
                }
            }
            if (next < 0) {
                next = doc.next[currentNode];
                if (next < currentNode) {
                    if (next == 0)
                        state = XMLStreamReader.END_DOCUMENT;
                    else
                        state = XMLStreamReader.END_ELEMENT;
                    currentNode = next;
                    return state;
                }
            }
            currentNode = next;
        } else {
            currentNode = rootNode.getNodeNumber();
        }
        switch (doc.nodeKind[currentNode]) {
            case Node.TEXT_NODE:
                state = XMLStreamReader.CHARACTERS;
                break;
            case Node.CDATA_SECTION_NODE:
                state = XMLStreamReader.CDATA;
                break;
            case Node.COMMENT_NODE:
                state = XMLStreamReader.COMMENT;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                state = XMLStreamReader.PROCESSING_INSTRUCTION;
                break;
            case Node.ELEMENT_NODE:
                state = XMLStreamReader.START_ELEMENT;
                break;
        }
        return state;
    }

    public void require(int type, String namespaceURI, String localName) throws XMLStreamException {
    }

    public String getElementText() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext() throws XMLStreamException {
        return currentNode != rootNode.getNodeNumber() || state == XMLStreamReader.START_DOCUMENT ||
                state == XMLStreamReader.START_ELEMENT;
    }

    public void close() throws XMLStreamException {
    }

    public String getNamespaceURI(String prefix) {
        return null;
    }

    public boolean isStartElement() {
        return state == XMLStreamReader.START_ELEMENT;
    }

    public boolean isEndElement() {
        return state == XMLStreamReader.END_ELEMENT;
    }

    public boolean isCharacters() {
        return state == XMLStreamReader.CHARACTERS;
    }

    public boolean isWhiteSpace() {
        return false;
    }

    public String getAttributeValue(String namespaceURI, String localName) {
        int attrCount = doc.getAttributesCountFor(currentNode);
        if (attrCount == 0)
            return null;
        int attrStart = doc.alpha[currentNode];
        for (int i = 0; i < attrCount; i++) {
            org.exist.dom.QName qname = (org.exist.dom.QName) doc.namePool.get(doc.attrName[attrStart + i]);
            if (namespaceURI.equals(qname.getNamespaceURI()) && localName.equals(qname.getLocalName()))
                return doc.attrValue[attrStart + i];
        }
        return null;
    }

    public int getAttributeCount() {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return doc.getAttributesCountFor(currentNode);
    }

    public org.exist.dom.QName getAttributeQName(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        if (index > getAttributeCount())
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        int attr = doc.alpha[currentNode];
        return (org.exist.dom.QName)doc.namePool.get(doc.attrName[attr + index]);
    }

    public QName getAttributeName(int index) {
        return getAttributeQName(index).toJavaQName();
    }

    public String getAttributeNamespace(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return getAttributeQName(index).getNamespaceURI();
    }

    public String getAttributeLocalName(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return getAttributeQName(index).getLocalName();
    }

    public String getAttributePrefix(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return getAttributeQName(index).getPrefix();
    }

    public NodeId getAttributeId(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        if (index > getAttributeCount())
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        doc.expand();
        int attr = doc.alpha[currentNode];
        return doc.attrNodeId[attr + index];
    }

    public String getAttributeType(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        if (index > getAttributeCount())
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        int attr = doc.alpha[currentNode];
        int type = doc.attrType[attr + index];
        switch (type) {
            case AttributeImpl.ATTR_ID_TYPE:
                return "ID";
            case AttributeImpl.ATTR_IDREF_TYPE:
                return "IDREF";
            case AttributeImpl.ATTR_IDREFS_TYPE:
                return "IDREFS";
            default:
                return "CDATA";
        }
    }

    public String getAttributeValue(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        if (index > getAttributeCount())
            throw new ArrayIndexOutOfBoundsException("bad attribute index");
        int attr = doc.alpha[currentNode];
        return doc.attrValue[attr + index];
    }

    public boolean isAttributeSpecified(int index) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return true;
    }

    public int getNamespaceCount() {
        if (state != START_ELEMENT && state != END_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        return doc.getNamespacesCountFor(currentNode);
    }

    public String getNamespacePrefix(int index) {
        if (index > getNamespaceCount())
            throw new ArrayIndexOutOfBoundsException("bad namespace index");
        int ns = doc.alphaLen[currentNode];
        org.exist.dom.QName nsQName = (org.exist.dom.QName) doc.namePool.get(doc.namespaceCode[ns + index]);
        return nsQName.getLocalName();
    }

    public String getNamespaceURI(int index) {
        if (index > getNamespaceCount())
            throw new ArrayIndexOutOfBoundsException("bad namespace index");
        int ns = doc.alphaLen[currentNode];
        org.exist.dom.QName nsQName = (org.exist.dom.QName) doc.namePool.get(doc.namespaceCode[ns + index]);
        return nsQName.getNamespaceURI();
    }

    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException();
    }

    public int getEventType() {
        return state;
    }

    public String getText() {
        if (state == CHARACTERS || state == COMMENT || state == CDATA)
            return new String(doc.characters, doc.alpha[currentNode],
			doc.alphaLen[currentNode]);
        return "";
    }

    public char[] getTextCharacters() {
        char[] ch = new char[doc.alphaLen[currentNode]];
        System.arraycopy(doc.characters, doc.alpha[currentNode], ch, 0, ch.length);
        return ch;
    }

    public int getTextCharacters(int sourceStart, char[] target, int targetStart, int length) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public int getTextStart() {
        throw new UnsupportedOperationException();
    }

    public int getTextLength() {
        throw new UnsupportedOperationException();
    }

    public String getEncoding() {
        throw new UnsupportedOperationException();
    }

    public boolean hasText() {
        return state == CHARACTERS || state == COMMENT || state == CDATA;
    }

    public Location getLocation() {
        throw new UnsupportedOperationException();
    }

    public org.exist.dom.QName getQName() {
        if (state == START_ELEMENT || state == END_ELEMENT) {
            return (org.exist.dom.QName) doc.namePool.get(doc.nodeName[currentNode]);
        }
        throw new IllegalStateException("Cursor is not at an element");
    }

    public QName getName() {
        return getQName().toJavaQName();
    }

    public String getLocalName() {
        return getQName().getLocalName();
    }

    public boolean hasName() {
        return (state == START_ELEMENT || state == END_ELEMENT);
    }

    public String getNamespaceURI() {
        return getQName().getNamespaceURI();
    }

    public String getPrefix() {
        return getQName().getPrefix();
    }

    public String getVersion() {
        return "1.0";
    }

    public boolean isStandalone() {
        return false;
    }

    public boolean standaloneSet() {
        return false;
    }

    public String getCharacterEncodingScheme() {
        return null;
    }

    public String getPITarget() {
        org.exist.dom.QName qn = (org.exist.dom.QName)doc.namePool.get(doc.nodeName[currentNode]);
        return qn != null ? qn.getLocalName() : null;
    }

    public String getPIData() {
        return new String(doc.characters, doc.alpha[currentNode],
		    doc.alphaLen[currentNode]);
    }
}
