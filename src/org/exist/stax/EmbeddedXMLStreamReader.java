/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2007 The eXist team
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.stax;

import org.exist.dom.AttrImpl;
import org.exist.dom.CharacterDataImpl;
import org.exist.dom.DocumentImpl;
import org.exist.dom.ElementImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.StoredNode;
import org.exist.numbering.NodeId;
import org.exist.storage.Signatures;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.RawNodeIterator;
import org.exist.util.ByteConversion;
import org.exist.util.XMLString;
import org.exist.util.serializer.AttrList;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Stack;

/**
 * Lazy implementation of a StAX {@link javax.xml.stream.XMLStreamReader}, which directly reads
 * information from the persistent DOM. The class is optimized to support fast scanning of the DOM, where only
 * a few selected node properties are requested. Node properties are extracted on demand. For example, the QName of
 * an element will not be read unless {@link #getText()} is called.
 */
public class EmbeddedXMLStreamReader implements XMLStreamReader {

    public final static String PROPERTY_NODE_ID = "node-id";

    private RawNodeIterator iterator;

    private Value current = null;

    private Value previous = null;

    private Stack elementStack = new Stack();

    private int state = START_DOCUMENT;

    private DocumentImpl document;

    private NodeId nodeId;

    private QName qname = null;

    private XMLString text = new XMLString(256);

    private List namespaces = new ArrayList(6);
    private boolean nsRead = false;
    
    private AttrList attributes = null;

    private boolean reportAttribs = false;

    /**
     * Construct an EmbeddedXMLStreamReader.
     *
     * @param doc the document to which the start node belongs.
     * @param iterator a RawNodeIterator positioned on the start node.
     * @param reportAttributes if set to true, attributes will be reported as top-level events.
     * @throws XMLStreamException
     */
    public EmbeddedXMLStreamReader(DocumentImpl doc, RawNodeIterator iterator, boolean reportAttributes)
            throws XMLStreamException {
        this.document = doc;
        this.iterator = iterator;
        this.reportAttribs = reportAttributes;
    }

    /**
     * Reposition the stream reader to another start node, maybe in a different document.
     *
     * @param node the new start node.
     * @param reportAttributes if set to true, attributes will be reported as top-level events.
     * @throws IOException
     */
    public void reposition(StoredNode node, boolean reportAttributes) throws IOException {
        reset();
        this.current = null;
        this.previous = null;
        this.elementStack.clear();
        this.state = START_DOCUMENT;
        this.reportAttribs = reportAttributes;
        this.document = (DocumentImpl) node.getOwnerDocument();
        iterator.seek(node);
    }

    /**
     * Reposition the stream reader to another start node, maybe in a different document.
     *
     * @param proxy the new start node.
     * @param reportAttributes if set to true, attributes will be reported as top-level events.
     * @throws IOException
     */
    public void reposition(NodeProxy proxy, boolean reportAttributes) throws IOException {
        reset();
        this.current = null;
        this.previous = null;
        this.elementStack.clear();
        this.state = START_DOCUMENT;
        this.reportAttribs = reportAttributes;
        this.document = (DocumentImpl) proxy.getOwnerDocument();
        iterator.seek(proxy);
    }

    public short getNodeType() {
        return Signatures.getType(current.data()[current.start()]);
    }
    
    private void initNode() {
        final short type = Signatures.getType(current.data()[current.start()]);
        switch (type) {
            case Node.ELEMENT_NODE :
                state = START_ELEMENT;
                elementStack.push(new ElementEvent(current));
                break;
            case Node.ATTRIBUTE_NODE :
                state = ATTRIBUTE;
                break;
            case Node.TEXT_NODE :
                state = CHARACTERS;
                break;
            case Node.COMMENT_NODE:
                state = COMMENT;
                break;
            case Node.CDATA_SECTION_NODE:
                state = CDATA;
                break;
            case Node.PROCESSING_INSTRUCTION_NODE:
                state = PROCESSING_INSTRUCTION;
                break;
        }
        reset();
        readNodeId();
    }

    private void skipAttributes() throws XMLStreamException {
        if (attributes == null) {
            // attributes were not yet read. skip them...
            final ElementEvent parent = (ElementEvent) elementStack.peek();
            final int attrs = getAttributeCount();
            for (int i = 0; i < attrs; i++) {
                iterator.next();
                parent.incrementChild();
            }
        }
    }

    private void readAttributes() {
        if (attributes == null) {
            final ElementEvent parent = (ElementEvent) elementStack.peek();
            final int count = getAttributeCount();
            attributes = new AttrList();
            for (int i = 0; i < count; i++) {
                Value v = iterator.next();
                AttrImpl.addToList(document.getBroker(), v.data(), v.start(), v.getLength(), attributes);
                parent.incrementChild();
            }
        }
    }
    
    private void readNodeId() {
        int offset = current.start() + StoredNode.LENGTH_SIGNATURE_LENGTH;
        if (state == START_ELEMENT || state == END_ELEMENT)
        	offset += ElementImpl.LENGTH_ELEMENT_CHILD_COUNT;
        int dlnLen = ByteConversion.byteToShort(current.data(), offset);
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId = document.getBroker().getBrokerPool().getNodeFactory().createFromData(dlnLen, current.data(), offset);
    }
    
    public int next() throws XMLStreamException {
        if (state != END_ELEMENT)
            previous = current;
        if (state == START_ELEMENT && !reportAttribs)
            skipAttributes();
        if (!elementStack.isEmpty()) {
            ElementEvent parent = (ElementEvent) elementStack.peek();
            if (parent.getChildCount() == parent.getCurrentChild()) {
                elementStack.pop();
                state = END_ELEMENT;
                current = parent.data;
                reset();
                return state;
            } else {
                parent.incrementChild();
            }
        } else if (state != START_DOCUMENT)
            throw new NoSuchElementException();
        current = iterator.next();
        initNode();
        return state;
    }

    private void reset() {
        nodeId = null;
        qname = null;
        attributes = null;
        text.reuse();
        if (state != END_ELEMENT) {
            namespaces.clear();
            nsRead = false;
        }
    }

    public void require(int i, String string, String string1) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public String getElementText() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public Object getProperty(String string) throws IllegalArgumentException {
        if (string.equals(PROPERTY_NODE_ID)) {
            if (nodeId == null)
                readNodeId();
            return nodeId;
        }
        return null;
    }

    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    public boolean hasNext() throws XMLStreamException {
        return state == START_DOCUMENT || !elementStack.isEmpty();
    }

    public void close() throws XMLStreamException {
        iterator.closeDocument();
    }

    public boolean isStartElement() {
        return state == START_ELEMENT;
    }

    public boolean isEndElement() {
        return state == END_ELEMENT;
    }

    public boolean isCharacters() {
        return state == CHARACTERS;
    }

    public boolean isWhiteSpace() {
        return false;
    }

    public String getAttributeValue(String string, String string1) {
        throw new UnsupportedOperationException();
    }

    public int getAttributeCount() {
        int offset = current.start() + StoredNode.LENGTH_SIGNATURE_LENGTH + ElementImpl.LENGTH_ELEMENT_CHILD_COUNT + NodeId.LENGTH_NODE_ID_UNITS + nodeId.size();
        return ByteConversion.byteToShort(current.data(), offset);
    }

    public QName getAttributeName(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        return attributes.getQName(i).toJavaQName();
    }

    public String getAttributeNamespace(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        return attributes.getQName(i).getNamespaceURI();
    }

    public String getAttributeLocalName(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        return attributes.getQName(i).getLocalName();
    }

    public String getAttributePrefix(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        return attributes.getQName(i).getPrefix();
    }

    public String getAttributeType(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        final int type = attributes.getType(i);
        if (type == AttrImpl.ID)
            return "ID";
        if (type == AttrImpl.IDREF)
            return "IDREF";
        if (type == AttrImpl.IDREFS)
            return "IDREFS";
        return "CDATA";
    }

    public String getAttributeValue(int i) {
        if (state != START_ELEMENT)
            throw new IllegalStateException("Cursor is not at an element");
        readAttributes();
        if (i > attributes.getLength())
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        return attributes.getValue(i);
    }

    public boolean isAttributeSpecified(int i) {
        return false;
    }

    public int getNamespaceCount() {
        readNamespaceDecls();
        return namespaces.size();
    }

    public String getNamespacePrefix(int i) {
        readNamespaceDecls();
        if (i < 0 || i > namespaces.size())
            return null;
        String[] decl = (String[]) namespaces.get(i);
        return decl[0];
    }

    public String getNamespaceURI(int i) {
        readNamespaceDecls();
        if (i < 0 || i > namespaces.size())
            return null;
        String[] decl = (String[]) namespaces.get(i);
        return decl[1];
    }

    public NamespaceContext getNamespaceContext() {
        throw new UnsupportedOperationException();
    }

    public int getEventType() {
        return state;
    }

    public String getText() {
        if (state == CHARACTERS || state == COMMENT || state == CDATA) {
            if (text.length() == 0) {
                CharacterDataImpl.readData(nodeId, current, text);
            }
            return text.toString();
        }
        return "";
    }

    public char[] getTextCharacters() {
        String s = getText();
        char[] dst = new char[s.length()];
        s.getChars(0, dst.length, dst, 0);
        return dst;
    }

    public int getTextCharacters(int sourceStart, char[] chars, int targetStart, int length) throws XMLStreamException {
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

    public String getNamespaceURI(String string) {
        return null;
    }

    public QName getName() {
        if (qname != null)
            return qname;
        if (state == START_ELEMENT || state == END_ELEMENT) {
            if (nodeId == null)
                readNodeId();
            qname = ElementImpl.readQName(current, document, nodeId).toJavaQName();
        }
        return qname;
    }

    public org.exist.dom.QName getQName() {
        if (state == START_ELEMENT || state == END_ELEMENT) {
            if (nodeId == null)
                readNodeId();
            return ElementImpl.readQName(current, document, nodeId);
        }
        return null;
    }

    /**
     * Read all namespace declarations defined on the current element.
     * Cache them in the namespaces map.
     */
    private void readNamespaceDecls() {
        if (nsRead)
            return;
        if (state == START_ELEMENT || state == END_ELEMENT) {
            if (nodeId == null)
                readNodeId();
            ElementImpl.readNamespaceDecls(namespaces, current, document, nodeId);
        }
        nsRead = true;
    }
    
    public String getPrefix() {
        return getName().getPrefix();
    }

    public String getLocalName() {
        return getName().getLocalPart();
    }

    public String getNamespaceURI() {
        return getName().getNamespaceURI();
    }

    public boolean hasName() {
        return (state == START_ELEMENT || state == END_ELEMENT);
    }

    /**
     * Deserialize the node at the current position of the cursor and return
     * it as a {@link org.exist.dom.StoredNode}.
     *
     * @return the node at the current position.
     */
    public StoredNode getNode() {
        StoredNode node = StoredNode.deserialize(current.data(), current.start(), current.getLength(), document);
        node.setOwnerDocument(document);
        node.setInternalAddress(current.getAddress());
        return node;
    }

    /**
     * Returns the last node in document sequence that occurs before the
     * current node. Usually used to find the last child before an END_ELEMENT
     * event.
     *
     * @return the last node in document sequence before the current node
     */
    public StoredNode getPreviousNode() {
        StoredNode node = StoredNode.deserialize(previous.data(), previous.start(), previous.getLength(), document);
        node.setOwnerDocument(document);
        node.setInternalAddress(previous.getAddress());
        return node;
    }

    /**
     * Returns the (internal) address of the node at the cursor's current
     * position.
     * 
     * @return internal address of node
     */
    public long getCurrentPosition() {
        return iterator.currentAddress();
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
        readPI();
        return qname.getLocalPart();
    }

    public String getPIData() {
        readPI();
        return text.toString();
    }

    private void readPI() {
        if (qname == null) {
            if (state != PROCESSING_INSTRUCTION)
                throw new IllegalStateException("Cursor is not at a processing instruction");
            ProcessingInstruction pi = (ProcessingInstruction)
                    StoredNode.deserialize(current.data(), current.start(), current.getLength(), document);
            qname = new QName("", pi.getTarget(), "");
            text.append(pi.getData());
        }
    }

    private static class ElementEvent {

        private Value data;

        private int childCount = 0;

        private int currentChild = 0;

        public ElementEvent(Value data) {
            this.data = data;
            childCount = ByteConversion.byteToInt(data.data(), data.start() + StoredNode.LENGTH_SIGNATURE_LENGTH);
        }

        public Value getData() {
            return data;
        }

        public int getChildCount() {
            return childCount;
        }

        public int getCurrentChild() {
            return currentChild;
        }

        public void setCurrentChild(int currentChild) {
            this.currentChild = currentChild;
        }

        public void incrementChild() {
            currentChild++;
        }
    }
}