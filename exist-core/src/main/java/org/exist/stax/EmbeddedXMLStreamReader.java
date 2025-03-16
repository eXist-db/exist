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

package org.exist.stax;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.*;
import org.exist.numbering.NodeId;
import org.exist.storage.DBBroker;
import org.exist.storage.Signatures;
import org.exist.storage.btree.Value;
import org.exist.storage.dom.RawNodeIterator;
import org.exist.util.ByteConversion;
import org.exist.util.XMLString;
import org.exist.util.serializer.AttrList;
import org.w3c.dom.Node;
import org.w3c.dom.ProcessingInstruction;

import javax.annotation.Nullable;
import javax.xml.namespace.NamespaceContext;
import javax.xml.namespace.QName;
import javax.xml.stream.Location;
import javax.xml.stream.StreamFilter;
import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public class EmbeddedXMLStreamReader implements IEmbeddedXMLStreamReader, ExtendedXMLStreamReader {

    private static final Logger LOG = LogManager.getLogger(EmbeddedXMLStreamReader.class);

    // states outside the range of events defined in {@link XMLStreamConstants}
    private static int BEFORE = -1;
    private static int AFTER = -2;

    // members which don't (generally) change!
    private DBBroker broker;
    private DocumentImpl document;
    private final RawNodeIterator iterator;
    private boolean reportAttributes;

    // mutable members which hold the current state of the stream
    private int state = BEFORE;  // initial state!
    private boolean consumedState = false;
    @Nullable private NodeHandle origin;
    @Nullable private Value previous = null;
    @Nullable private Value current = null;
    @Nullable private NodeId nodeId = null;
    private final Deque<ElementEvent> elementStack = new ArrayDeque<>();
    private boolean nsRead = false;
    private final List<String[]> namespaces = new ArrayList<>(6);
    @Nullable private QName qname = null;
    @Nullable private AttrList attributes = null;
    private final XMLString text = new XMLString(256);


    public EmbeddedXMLStreamReader(final DBBroker broker, final DocumentImpl document, final RawNodeIterator iterator, @Nullable final NodeHandle origin, final boolean reportAttributes)
            throws XMLStreamException {
        this.broker = broker;
        this.document = document;
        this.iterator = iterator;
        this.reportAttributes = reportAttributes;
        this.origin = origin;
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
        this.state = BEFORE;
        this.consumedState = false;
        this.reportAttributes = reportAttributes;
        this.document = node.getOwnerDocument();
        this.origin = node;
    }

    @Override
    public boolean hasNext() throws XMLStreamException {
        if (consumedState || state == BEFORE) {
            getNext();

            consumedState = false;  // mark that we have a new state available

            // NOTE: this intentionally prevents END_DOCUMENT event being returned from {@link #next()}.
            if (state == END_DOCUMENT) {
                state = AFTER;
            }
        }

        return state != AFTER;
    }

    @Override
    public int next() throws XMLStreamException {
        if (!hasNext()) {
            throw new IllegalStateException("hasNext()==false");
        }
        consumedState = true;  // mark that we have consumed the current state
        return state;
    }

    private void getNext() throws XMLStreamException {
        if(state != END_ELEMENT) {
            previous = current;
        }

        if(state == START_ELEMENT && !reportAttributes) {
            skipAttributes();
        }
        if(!elementStack.isEmpty()) {
            final ElementEvent parent = elementStack.peek();
            if(parent.getChildCount() == parent.getCurrentChild()) {
                elementStack.pop();
                state = END_ELEMENT;
                current = parent.data;
                reset();
                return;
            } else {
                parent.incrementChild();
            }
        }
        final boolean first = state == BEFORE;
        current = iterator.next();
        if (current == null) {
            state = END_DOCUMENT;
            reset();
            return;
        }
        initNode();
        if(first && origin != null) {
            verifyOriginNodeId();
            origin = null;
        }
        return;
    }

    @Override
    public void filter(final StreamFilter filter) throws XMLStreamException {
        while (hasNext()) {
            next();
            if (!filter.accept(this)) {
                break;
            }
        }
    }

    private void initNode() {
        final short type = Signatures.getType(current.data()[current.start()]);
        switch (type) {
            case Node.ELEMENT_NODE:
                state = START_ELEMENT;
                elementStack.push(new ElementEvent(current));
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

    private void verifyOriginNodeId() throws XMLStreamException {
        if(!nodeId.equals(origin.getNodeId())) {
            // Node got moved, we had the wrong address.  Resync iterator by nodeid.
            LOG.warn("Expected node id {}, got {}; resyncing address", origin.getNodeId(), nodeId);
            origin.setInternalAddress(StoredNode.UNKNOWN_NODE_IMPL_ADDRESS);
            final boolean reportAttributesBackup = reportAttributes;
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
            reportAttributes = reportAttributesBackup;
            document = documentBackup;
            current = iterator.next();
            initNode();

            origin.setInternalAddress(iterator.currentAddress());
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
    public short getNodeType() {
        return Signatures.getType(current.data()[current.start()]);
    }

    @Override
    public String getElementText() throws XMLStreamException {
        if (state != START_ELEMENT) {
            throw new XMLStreamException("parser must be on START_ELEMENT to read next text");
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
                throw new XMLStreamException("element text content may not contain START_ELEMENT");
            } else {
                throw new XMLStreamException("Unexpected event type " + eventType);
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
    public javax.xml.namespace.QName getAttributeName(final int index) {
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
        if (state != START_ELEMENT) {
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
            if(text.isEmpty()) {
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
        return getText().toCharArray();
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
            if(text.isEmpty()) {
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
    public javax.xml.namespace.QName getName() {
        if(qname != null) {
            return qname;
        }
        if (state == START_ELEMENT || state == END_ELEMENT) {
            if(nodeId == null) {
                readNodeId();
            }
            qname = ElementImpl.readQName(current, document, nodeId).toJavaQName();
        }
        return qname;
    }

    @Override
    public org.exist.dom.QName getQName() {
        if (state == START_ELEMENT || state == END_ELEMENT) {
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
        if (state == START_ELEMENT || state == END_ELEMENT) {
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
        return state == START_ELEMENT || state == END_ELEMENT;
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
            qname = new javax.xml.namespace.QName("", pi.getTarget(), "");
            text.append(pi.getData());
        }
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

    private static final class ElementEvent {
        private final Value data;
        private final int childCount;
        private int currentChild = 0;

        public ElementEvent(final Value data) {
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
