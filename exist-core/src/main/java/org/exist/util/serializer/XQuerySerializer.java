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

import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.json.JSONSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

/**
 * Utility class for writing out XQuery results. It is an abstraction around
 * eXist's internal serializers specialized on writing XQuery sequences.
 *
 * @author Wolf
 */
public class XQuerySerializer {

    private final Properties outputProperties;
    private final DBBroker broker;
    private final Writer writer;

    public XQuerySerializer(DBBroker broker, Properties outputProperties, Writer writer) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        this.writer = writer;

        // ALWAYS enforce XDM serialization rules
        outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
    }

    public void serialize(final Sequence sequence) throws SAXException, XPathException {
        serialize(sequence, 1, sequence.getItemCount(), false, false, 0, 0);
    }

    public void serialize(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        switch (method) {
            case "adaptive":
                serializeAdaptive(sequence);
                break;
            case "json":
                serializeJSON(sequence, compilationTime, executionTime);
                break;
            case "csv":
                serializeCSV(sequence);
                break;
            case "xml":
            default:
                // For XML/text methods, flatten any arrays in the sequence before serialization
                // (arrays can't be serialized as SAX events directly)
                // Maps and function items cannot be serialized with XML/text methods (SENR0001)
                validateXmlSerializable(sequence);
                if (isCanonical()) {
                    validateCanonical(sequence);
                }
                final Sequence flattened = flattenArrays(sequence);
                if (flattened != sequence) {
                    // Flattening changed the sequence — reset start/howmany to cover all items.
                    // For text method, default item-separator is space if not explicitly set.
                    if ("text".equals(method) && outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR) == null) {
                        outputProperties.setProperty(EXistOutputKeys.ITEM_SEPARATOR, " ");
                    }
                    serializeXML(flattened, 1, flattened.getItemCount(), wrap, typed, compilationTime, executionTime);
                } else {
                    serializeXML(flattened, start, howmany, wrap, typed, compilationTime, executionTime);
                }
                break;
        }
    }

    /**
     * Validate that a sequence can be serialized with the XML/text method.
     * Maps and function items are not serializable as XML (SENR0001).
     */
    private static void validateXmlSerializable(final Sequence sequence) throws SAXException, XPathException {
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final int type = item.getType();
            if (type == Type.MAP_ITEM || type == Type.FUNCTION) {
                throw new SAXException("err:SENR0001 Cannot serialize a " +
                        Type.getTypeName(type) + " with the XML or text output method");
            }
        }
    }

    private boolean isCanonical() {
        final String v = outputProperties.getProperty(EXistOutputKeys.CANONICAL);
        return "yes".equals(v) || "true".equals(v) || "1".equals(v);
    }

    /**
     * Validate canonical XML constraints (SERE0024).
     * Checks for relative namespace URIs and multi-root documents.
     */
    private void validateCanonical(final Sequence sequence) throws SAXException, XPathException {
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                validateCanonicalNode((NodeValue) item);
            }
        }
    }

    private void validateCanonicalNode(final NodeValue node) throws SAXException, XPathException {
        if (node.getType() == Type.DOCUMENT) {
            // Check for multi-root: document must have exactly one element child
            int elementCount = 0;
            final org.w3c.dom.Node domNode = node.getNode();
            for (org.w3c.dom.Node child = domNode.getFirstChild(); child != null; child = child.getNextSibling()) {
                if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    elementCount++;
                }
            }
            if (elementCount != 1) {
                throw new SAXException("err:SERE0024 Canonical serialization requires a well-formed document with exactly one root element, found " + elementCount);
            }
            // Check namespace URIs on the document's elements
            validateCanonicalNamespaces(domNode);
        } else if (node.getType() == Type.ELEMENT) {
            validateCanonicalNamespaces(node.getNode());
        }
    }

    private void validateCanonicalNamespaces(final org.w3c.dom.Node node) throws SAXException {
        if (node.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
            final String nsUri = node.getNamespaceURI();
            if (nsUri != null && !nsUri.isEmpty() && isRelativeUri(nsUri)) {
                throw new SAXException("err:SERE0024 Canonical serialization does not allow relative namespace URIs: " + nsUri);
            }
            // Also check namespace URIs in attributes (including xmlns declarations)
            final org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
            if (attrs != null) {
                for (int i = 0; i < attrs.getLength(); i++) {
                    final org.w3c.dom.Attr attr = (org.w3c.dom.Attr) attrs.item(i);
                    final String attrName = attr.getName();
                    // Check xmlns and xmlns:prefix declarations
                    if ("xmlns".equals(attrName) || attrName.startsWith("xmlns:")) {
                        final String declUri = attr.getValue();
                        if (declUri != null && !declUri.isEmpty() && isRelativeUri(declUri)) {
                            throw new SAXException("err:SERE0024 Canonical serialization does not allow relative namespace URIs: " + declUri);
                        }
                    }
                }
            }
            // Check child elements recursively
            for (org.w3c.dom.Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
                validateCanonicalNamespaces(child);
            }
        }
    }

    private static boolean isRelativeUri(final String uri) {
        // Absolute URIs contain a scheme (e.g., "http://", "urn:", "file:")
        // A URI without ":" before the first "/" or "?" is relative
        for (int i = 0; i < uri.length(); i++) {
            final char c = uri.charAt(i);
            if (c == ':') return false;  // Found scheme separator — absolute
            if (c == '/' || c == '?' || c == '#') return true;  // Path/query before scheme — relative
        }
        return true;  // No scheme found — relative (e.g., "local.ns")
    }

    /**
     * Flatten arrays in a sequence — each array member becomes a top-level item.
     * This is needed because the SAX-based XML/text serializer can't handle ArrayType items.
     */
    private static Sequence flattenArrays(final Sequence sequence) throws XPathException {
        boolean hasArrays = false;
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            if (i.nextItem().getType() == Type.ARRAY_ITEM) {
                hasArrays = true;
                break;
            }
        }
        if (!hasArrays) {
            return sequence;
        }
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (item.getType() == Type.ARRAY_ITEM) {
                final Sequence flat = org.exist.xquery.functions.array.ArrayType.flatten(item);
                for (final SequenceIterator fi = flat.iterate(); fi.hasNext(); ) {
                    result.add(fi.nextItem());
                }
            } else {
                result.add(item);
            }
        }
        return result;
    }

    public boolean normalize() {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        return !("json".equals(method) || "adaptive".equals(method) || "csv".equals(method));
    }

    private void serializeXML(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final String itemSeparator = outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR);
        // If item-separator is set and sequence has multiple items, serialize items individually
        // with separator between them (the internal Serializer doesn't handle item-separator)
        if (itemSeparator != null && sequence.getItemCount() > 1 && !wrap) {
            serializeXMLWithItemSeparator(sequence, start, howmany, typed, itemSeparator);
        } else {
            serializeXMLDirect(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
        }
    }

    private void serializeXMLDirect(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final Serializer serializer = broker.borrowSerializer();
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            sax.setOutput(writer, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
            serializer.toSAX(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
            broker.returnSerializer(serializer);
        }
    }

    private void serializeXMLWithItemSeparator(final Sequence sequence, final int start, final int howmany, final boolean typed, final String itemSeparator) throws SAXException, XPathException {
        // Write XML declaration if not omitted (per W3C Serialization 3.1)
        if (!isBooleanTrue(outputProperties.getProperty(OutputKeys.OMIT_XML_DECLARATION, "no"))) {
            try {
                final String version = outputProperties.getProperty(OutputKeys.VERSION, "1.0");
                final String encoding = outputProperties.getProperty(OutputKeys.ENCODING, "UTF-8");
                writer.write("<?xml version=\"" + version + "\" encoding=\"" + encoding + "\"?>");
            } catch (IOException e) {
                throw new SAXException(e.getMessage(), e);
            }
        }

        final int actualStart = start - 1; // convert 1-based to 0-based
        final int end = Math.min(actualStart + howmany, sequence.getItemCount());
        for (int i = actualStart; i < end; i++) {
            if (i > actualStart) {
                try {
                    writer.write(itemSeparator);
                } catch (IOException e) {
                    throw new SAXException(e.getMessage(), e);
                }
            }
            final Item item = sequence.itemAt(i);
            if (item == null) {
                continue;
            }
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                // For nodes serialized with item-separator, omit the XML declaration
                // on each individual node (only one declaration for the whole output)
                final Properties nodeProps = new Properties(outputProperties);
                nodeProps.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
                final Serializer serializer = broker.borrowSerializer();
                SAXSerializer sax = null;
                try {
                    sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
                    sax.setOutput(writer, nodeProps);
                    serializer.setProperties(nodeProps);
                    serializer.setSAXHandlers(sax, sax);
                    final ValueSequence singleItem = new ValueSequence(1);
                    singleItem.add(item);
                    serializer.toSAX(singleItem, 1, 1, false, typed, 0, 0);
                } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
                    throw new SAXException(e.getMessage(), e);
                } finally {
                    if (sax != null) {
                        SerializerPool.getInstance().returnObject(sax);
                    }
                    broker.returnSerializer(serializer);
                }
            } else {
                try {
                    writer.write(item.getStringValue());
                } catch (IOException e) {
                    throw new SAXException(e.getMessage(), e);
                }
            }
        }
    }

    private static boolean isBooleanTrue(final String value) {
        if (value == null) return false;
        final String v = value.trim();
        return "yes".equals(v) || "true".equals(v) || "1".equals(v);
    }

    private void serializeJSON(final Sequence sequence, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        // The legacy XML-to-JSON conversion (where an element became a JSON object
        // graph and an empty element became `null`) violates W3C JSON Output Method
        // semantics, which require a node to be serialized as XML and the result
        // wrapped in a JSON string. Callers wanting the legacy behavior must opt
        // in via {@code exist:legacy-json-conversion=yes}.
        final String legacy = outputProperties.getProperty(EXistOutputKeys.LEGACY_JSON_CONVERSION, "no");
        if (("yes".equals(legacy) || "true".equals(legacy) || "1".equals(legacy))
                && sequence.hasOne()
                && (Type.subTypeOf(sequence.getItemType(), Type.DOCUMENT) || Type.subTypeOf(sequence.getItemType(), Type.ELEMENT))) {
            serializeXMLDirect(sequence, 1, 1, false, false, compilationTime, executionTime);
        } else {
            JSONSerializer serializer = new JSONSerializer(broker, outputProperties);
            serializer.serialize(sequence, writer);
        }
    }

    private void serializeCSV(final Sequence sequence) throws SAXException {
        final CSVSerializer serializer = new CSVSerializer(outputProperties);
        serializer.serialize(sequence, writer);
    }

    private void serializeAdaptive(final Sequence sequence) throws SAXException, XPathException {
        final AdaptiveSerializer serializer = new AdaptiveSerializer(broker);
        serializer.setOutput(writer, outputProperties);
        serializer.serialize(sequence);
    }
}
