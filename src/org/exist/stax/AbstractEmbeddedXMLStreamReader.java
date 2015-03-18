/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist team
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.*;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.Signatures;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.IRawNodeIterator;
import org.exist.util.ByteConversion;
import org.exist.util.XMLString;
import org.exist.util.serializer.AttrList;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;
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
public abstract class AbstractEmbeddedXMLStreamReader<T extends IRawNodeIterator> implements IEmbeddedXMLStreamReader, ExtendedXMLStreamReader {

    private static final Logger LOG = LogManager.getLogger(AbstractEmbeddedXMLStreamReader.class);

    protected final T iterator;
    private Value current = null;
    private Value previous = null;

    private final Stack<ElementEvent> elementStack = new Stack<>();

    private int state = START_DOCUMENT;

    private boolean beforeRoot = false;

    private DocumentImpl document;
    protected NodeId nodeId;
    protected NodeHandle origin;
    private QName qname = null;

    private final XMLString text = new XMLString(256);

    private final List<String[]> namespaces = new ArrayList<>(6);
    private boolean nsRead = false;

    private AttrList attributes = null;
    private boolean reportAttribs = false;

    private DBBroker broker;

    /**
     * Construct an EmbeddedXMLStreamReader.
     *
     * @param doc              the document to which the start node belongs.
     * @param iterator         a RawNodeIterator positioned on the start node.
     * @param origin           an optional NodeHandle whose nodeId should match the first node in the stream
     *                         (or null if no need to check)
     * @param reportAttributes if set to true, attributes will be reported as top-level events.
     * @throws javax.xml.stream.XMLStreamException
     */
    public AbstractEmbeddedXMLStreamReader(final DBBroker broker, final DocumentImpl doc, final T iterator, final NodeHandle origin, final boolean reportAttributes)
        throws XMLStreamException {
        this.broker = broker;
        this.document = doc;
        this.iterator = iterator;
        this.reportAttribs = reportAttributes;
        this.origin = origin;
    }

    @Override
    public void filter(final StreamFilter filter) throws XMLStreamException {
        while(hasNext()) {
            next();
            if(!filter.accept(this)) {
                break;
            }
        }
    }

    @Override
    public void reposition(final DBBroker broker, final NodeHandle node, final boolean reportAttributes) throws IOException {
        this.broker = broker;
        // Seeking to a node with unknown address will reuse this reader, so do it before setting all
        // the fields otherwise they could get overwritten.
        iterator.seek(node);
        reset();
        this.current = null;
        this.previous = null;
        this.elementStack.clear();
        this.state = START_DOCUMENT;
        this.reportAttribs = reportAttributes;
        this.document = node.getOwnerDocument();
        this.origin = node;
    }

    @Override
    public short getNodeType() {
        return Signatures.getType(current.data()[current.start()]);
    }

    private void initNode() {
        final short type = Signatures.getType(current.data()[current.start()]);    // TODO: remove potential NPE
        if(state == START_DOCUMENT && type != Node.ELEMENT_NODE) {
            beforeRoot = true;
        }
        switch(type) {
            case Node.ELEMENT_NODE:
                state = START_ELEMENT;
                elementStack.push(new ElementEvent(current));
                beforeRoot = false;
                break;
            case Node.ATTRIBUTE_NODE:
                state = ATTRIBUTE;
                break;
            case Node.TEXT_NODE:
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
        if(attributes == null) {
            // attributes were not yet read. skip them...
            final ElementEvent parent = elementStack.peek();
            final int attrs = getAttributeCount();
            for(int i = 0; i < attrs; i++) {
                iterator.next();
                parent.incrementChild();
            }
        }
    }

    private void readAttributes() {
        if(attributes == null) {
            final ElementEvent parent = elementStack.peek();
            final int count = getAttributeCount();
            attributes = new AttrList();
            for(int i = 0; i < count; i++) {
                final Value v = iterator.next();
                AttrImpl.addToList(broker, v.data(), v.start(), v.getLength(), attributes);
                parent.incrementChild();
            }
        }
    }

    private void readNodeId() {
        int offset = current.start() + StoredNode.LENGTH_SIGNATURE_LENGTH;
        if(state == START_ELEMENT || state == END_ELEMENT) {
            offset += ElementImpl.LENGTH_ELEMENT_CHILD_COUNT;
        }
        final int dlnLen = ByteConversion.byteToShort(current.data(), offset);
        offset += NodeId.LENGTH_NODE_ID_UNITS;
        nodeId = broker.getBrokerPool().getNodeFactory().createFromData(dlnLen, current.data(), offset);
    }

    @Override
    public int next() throws XMLStreamException {
        if(state != END_ELEMENT) {
            previous = current;
        }
        if(state == START_ELEMENT && !reportAttribs) {
            skipAttributes();
        }
        if(!elementStack.isEmpty()) {
            final ElementEvent parent = elementStack.peek();
            if(parent.getChildCount() == parent.getCurrentChild()) {
                elementStack.pop();
                state = END_ELEMENT;
                current = parent.data;
                reset();
                return state;
            } else {
                parent.incrementChild();
            }
        } else if(state != START_DOCUMENT && !beforeRoot) {
            throw new NoSuchElementException();
        }
        final boolean first = state == START_DOCUMENT;
        current = iterator.next();
        initNode();
        if(first && origin != null) {
            verifyOriginNodeId();
            origin = null;
        }
        return state;
    }

    protected void verifyOriginNodeId() throws XMLStreamException {
        if(!nodeId.equals(origin.getNodeId())) {
            // Node got moved, we had the wrong address.  Resync iterator by nodeid.
            LOG.warn("expected node id " + origin.getNodeId() + ", got " + nodeId + "; resyncing address");
            origin.setInternalAddress(StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
            final boolean reportAttribsBackup = reportAttribs;
            DocumentImpl documentBackup = document;
            try {
                iterator.seek(origin);
            } catch(final IOException e) {
                throw new XMLStreamException(e);
            }
            // Seeking the iterator might've reused this reader, so reset all fields.
            reset();
            previous = null;
            elementStack.clear();
            reportAttribs = reportAttribsBackup;
            document = documentBackup;
            current = iterator.next();
            initNode();
        }
    }

    private void reset() {
        nodeId = null;
        qname = null;
        attributes = null;
        text.reuse();
        if(state != END_ELEMENT) {
            namespaces.clear();
            nsRead = false;
        }
    }

    @Override
    public void require(final int type, final String namespaceURI, final String localName) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if(getEventType() != START_ELEMENT) {
            throw new XMLStreamException(
                "parser must be on START_ELEMENT to read next text");
        }
        int eventType = next();
        final StringBuilder content = new StringBuilder();
        while(eventType != END_ELEMENT) {
            if(eventType == CHARACTERS
                || eventType == CDATA
                || eventType == SPACE
                || eventType == ENTITY_REFERENCE) {
                content.append(getText());
            } else if(eventType == PROCESSING_INSTRUCTION
                || eventType == COMMENT) {
                // skipping
            } else if(eventType == END_DOCUMENT) {
                throw new XMLStreamException("unexpected end of document when reading element text content");
            } else if(eventType == START_ELEMENT) {
                throw new XMLStreamException(
                    "element text content may not contain START_ELEMENT");
            } else {
                throw new XMLStreamException(
                    "Unexpected event type " + eventType);
            }
            eventType = next();
        }
        return content.toString();
    }

    @Override
    public Object getProperty(final String string) throws IllegalArgumentException {
        if(string.equals(PROPERTY_NODE_ID)) {
            if(nodeId == null) {
                readNodeId();
            }
            return nodeId;
        }
        return null;
    }

    @Override
    public int nextTag() throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        return state == START_DOCUMENT || beforeRoot || !elementStack.isEmpty();
    }

    @Override
    public void close() throws XMLStreamException {
        iterator.close();
    }

    @Override
    public boolean isStartElement() {
        return state == START_ELEMENT;
    }

    @Override
    public boolean isEndElement() {
        return state == END_ELEMENT;
    }

    @Override
    public boolean isCharacters() {
        return state == CHARACTERS;
    }

    @Override
    public boolean isWhiteSpace() {
        return false;
    }

    @Override
    public String getAttributeValue(final String namespaceURI, final String localName) {
        readAttributes();
        for(int i = 0; i < attributes.getLength(); i++) {
            final org.exist.dom.QName qn = attributes.getQName(i);
            if(qn.getNamespaceURI().equals(namespaceURI) && qn.getLocalPart().equals(localName)) {
                return attributes.getValue(i);
            }
        }
        return null;
    }

    @Override
    public int getAttributeCount() {
        final int offset = current.start() + StoredNode.LENGTH_SIGNATURE_LENGTH + ElementImpl.LENGTH_ELEMENT_CHILD_COUNT + NodeId.LENGTH_NODE_ID_UNITS + nodeId.size();
        return ByteConversion.byteToShort(current.data(), offset);
    }

    @Override
    public QName getAttributeName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getQName(index).toJavaQName();
    }

    @Override
    public org.exist.dom.QName getAttributeQName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getQName(index);
    }

    @Override
    public String getAttributeNamespace(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getQName(index).getNamespaceURI();
    }

    @Override
    public String getAttributeLocalName(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getQName(index).getLocalPart();
    }

    @Override
    public String getAttributePrefix(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getQName(index).getPrefix();
    }

    @Override
    public String getAttributeType(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        final int type = attributes.getType(index);
        return AttrImpl.getAttributeType(type);
    }

    @Override
    public String getAttributeValue(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getValue(index);
    }

    @Override
    public NodeId getAttributeId(final int index) {
        if(state != START_ELEMENT) {
            throw new IllegalStateException("Cursor is not at an element");
        }
        readAttributes();
        if(index > attributes.getLength()) {
            throw new ArrayIndexOutOfBoundsException("index should be < " + attributes.getLength());
        }
        return attributes.getNodeId(index);
    }

    @Override
    public boolean isAttributeSpecified(final int index) {
        return false;
    }

    @Override
    public int getNamespaceCount() {
        readNamespaceDecls();
        return namespaces.size();
    }

    @Override
    public String getNamespacePrefix(final int index) {
        readNamespaceDecls();
        if(index < 0 || index > namespaces.size()) {
            return null;
        }
        final String[] decl = namespaces.get(index);
        return decl[0];
    }

    @Override
    public String getNamespaceURI(int i) {
        readNamespaceDecls();
        if(i < 0 || i > namespaces.size()) {
            return null;
        }
        final String[] decl = namespaces.get(i);
        return decl[1];
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
    public XMLString getXMLText() {
        if(state == CHARACTERS || state == COMMENT || state == CDATA) {
            if(text.length() == 0) {
                AbstractCharacterData.readData(nodeId, current, text);
            }
            return text;
        }
        return new XMLString();
    }

    @Override
    public String getText() {
        return getXMLText().toString();
    }

    @Override
    public char[] getTextCharacters() {
        final String s = getText();
        final char[] dst = new char[s.length()];
        s.getChars(0, dst.length, dst, 0);
        return dst;
    }

    @Override
    public int getTextCharacters(final int sourceStart, final char[] chars, final int targetStart, final int length) throws XMLStreamException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextStart() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getTextLength() {
        if(state == CHARACTERS || state == COMMENT || state == CDATA) {
            if(text.length() == 0) {
                return AbstractCharacterData.getStringLength(nodeId, current);
            }
            return text.length();
        }
        return 0;
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
    public String getNamespaceURI(final String prefix) {
        return null;
    }

    @Override
    public QName getName() {
        if(qname != null) {
            return qname;
        }
        if(state == START_ELEMENT || state == END_ELEMENT) {
            if(nodeId == null) {
                readNodeId();
            }
            qname = ElementImpl.readQName(current, document, nodeId).toJavaQName();
        }
        return qname;
    }

    @Override
    public org.exist.dom.QName getQName() {
        if(state == START_ELEMENT || state == END_ELEMENT) {
            if(nodeId == null) {
                readNodeId();
            }
            return ElementImpl.readQName(current, document, nodeId);
        }
        return null;
    }

    /**
     * Read all namespace declarations defined on the current element.
     * Cache them in the namespaces map.
     */
    private void readNamespaceDecls() {
        if(nsRead) {
            return;
        }
        if(state == START_ELEMENT || state == END_ELEMENT) {
            if(nodeId == null) {
                readNodeId();
            }
            ElementImpl.readNamespaceDecls(namespaces, current, document, nodeId);
        }
        nsRead = true;
    }

    @Override
    public String getPrefix() {
        return getName().getPrefix();
    }

    @Override
    public String getLocalName() {
        return getName().getLocalPart();
    }

    @Override
    public String getNamespaceURI() {
        return getName().getNamespaceURI();
    }

    @Override
    public boolean hasName() {
        return (state == START_ELEMENT || state == END_ELEMENT);
    }

    @Override
    public IStoredNode getNode() {
        final IStoredNode node = StoredNode.deserialize(current.data(), current.start(), current.getLength(), document);
        node.setOwnerDocument(document);
        node.setInternalAddress(current.getAddress());
        return node;
    }

    @Override
    public IStoredNode getPreviousNode() {
        final StoredNode node = StoredNode.deserialize(previous.data(), previous.start(), previous.getLength(), document);
        node.setOwnerDocument(document);
        node.setInternalAddress(previous.getAddress());
        return node;
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
        readPI();
        return qname.getLocalPart();
    }

    @Override
    public String getPIData() {
        readPI();
        return text.toString();
    }

    private void readPI() {
        if(qname == null) {
            if(state != PROCESSING_INSTRUCTION) {
                throw new IllegalStateException("Cursor is not at a processing instruction");
            }
            final ProcessingInstruction pi = (ProcessingInstruction)
                StoredNode.deserialize(current.data(), current.start(), current.getLength(), document);
            qname = new QName("", pi.getTarget(), "");
            text.append(pi.getData());
        }
    }

    private static final class ElementEvent {

        private final Value data;
        private final int childCount;
        private int currentChild = 0;

        public ElementEvent(Value data) {
            this.data = data;
            this.childCount = ByteConversion.byteToInt(data.data(), data.start() + StoredNode.LENGTH_SIGNATURE_LENGTH);
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

        public void incrementChild() {
            currentChild++;
        }
    }
}