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

import io.lacuna.bifurcan.IEntry;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.Writer;
import java.util.*;

/**
 * Serializes XDM sequences as RFC 4180 CSV output.
 *
 * Accepts three input formats:
 * <ul>
 *   <li>Array of arrays: each inner array is a row</li>
 *   <li>Sequence of maps: keys become header, values become rows</li>
 *   <li>XML table: &lt;csv&gt;&lt;record&gt;&lt;field&gt;...&lt;/field&gt;&lt;/record&gt;&lt;/csv&gt;</li>
 * </ul>
 */
public class CSVSerializer {

    private final String fieldDelimiter;
    private final String rowDelimiter;
    private final char quoteChar;
    private final boolean alwaysQuote;
    private final boolean includeHeader;

    public CSVSerializer(final Properties outputProperties) {
        this.fieldDelimiter = outputProperties.getProperty(EXistOutputKeys.CSV_FIELD_DELIMITER, ",");
        this.rowDelimiter = outputProperties.getProperty(EXistOutputKeys.CSV_ROW_DELIMITER, "\n");
        final String qc = outputProperties.getProperty(EXistOutputKeys.CSV_QUOTE_CHARACTER, "\"");
        this.quoteChar = qc.isEmpty() ? '"' : qc.charAt(0);
        this.alwaysQuote = !"no".equals(outputProperties.getProperty(EXistOutputKeys.CSV_QUOTES, "yes"));
        this.includeHeader = "yes".equals(outputProperties.getProperty(EXistOutputKeys.CSV_HEADER, "no"));
    }

    public void serialize(final Sequence sequence, final Writer writer) throws SAXException {
        try {
            if (sequence.isEmpty()) {
                return;
            }

            final Item first = sequence.itemAt(0);

            if (first.getType() == Type.ARRAY_ITEM) {
                if (sequence.hasOne()) {
                    // Single array: treat as array-of-arrays
                    serializeArrayOfArrays((ArrayType) first, writer);
                } else {
                    // Sequence of arrays: each array is a row
                    serializeSequenceOfArrays(sequence, writer);
                }
            } else if (first.getType() == Type.MAP_ITEM) {
                serializeSequenceOfMaps(sequence, writer);
            } else if (Type.subTypeOf(first.getType(), Type.NODE)) {
                serializeXmlTable(sequence, writer);
            } else {
                // Single atomic or sequence of atomics — one row
                serializeAtomicSequence(sequence, writer);
            }
        } catch (final IOException | XPathException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void serializeArrayOfArrays(final ArrayType outerArray, final Writer writer) throws IOException, XPathException {
        for (int i = 0; i < outerArray.getSize(); i++) {
            final Sequence member = outerArray.get(i);
            if (member.getItemCount() == 1 && member.itemAt(0).getType() == Type.ARRAY_ITEM) {
                writeRow((ArrayType) member.itemAt(0), writer);
            } else {
                writeSequenceRow(member, writer);
            }
            writer.write(rowDelimiter);
        }
    }

    private void serializeSequenceOfArrays(final Sequence sequence, final Writer writer) throws IOException, XPathException {
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (item.getType() == Type.ARRAY_ITEM) {
                writeRow((ArrayType) item, writer);
            } else {
                writer.write(quoteField(item.getStringValue()));
            }
            writer.write(rowDelimiter);
        }
    }

    private void serializeSequenceOfMaps(final Sequence sequence, final Writer writer) throws IOException, XPathException {
        // Collect all keys from first map for header
        final AbstractMapType firstMap = (AbstractMapType) sequence.itemAt(0);
        final List<String> keys = new ArrayList<>();
        for (final IEntry<AtomicValue, Sequence> entry : firstMap) {
            keys.add(entry.key().getStringValue());
        }
        Collections.sort(keys);

        // Write header
        if (includeHeader) {
            writeFields(keys, writer);
            writer.write(rowDelimiter);
        }

        // Write rows
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (item.getType() == Type.MAP_ITEM) {
                final AbstractMapType map = (AbstractMapType) item;
                boolean first = true;
                for (final String key : keys) {
                    if (!first) {
                        writer.write(fieldDelimiter);
                    }
                    final Sequence value = map.get(new StringValue(key));
                    writer.write(quoteField(value.isEmpty() ? "" : value.getStringValue()));
                    first = false;
                }
            }
            writer.write(rowDelimiter);
        }
    }

    private void serializeXmlTable(final Sequence sequence, final Writer writer) throws IOException, XPathException {
        // Walk XML table: <csv><record><field>value</field></record></csv>
        // or <table><tr><td>value</td></tr></table>
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
                final org.w3c.dom.Element elem = (org.w3c.dom.Element) ((NodeValue) item).getNode();
                serializeXmlElement(elem, writer);
            }
        }
    }

    private void serializeXmlElement(final org.w3c.dom.Element element, final Writer writer) throws IOException {
        final org.w3c.dom.NodeList children = element.getChildNodes();
        boolean hasChildElements = false;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                hasChildElements = true;
                break;
            }
        }

        if (!hasChildElements) {
            // Leaf element — output as a field value
            writer.write(quoteField(element.getTextContent()));
            return;
        }

        // Check if children are "record" elements (containing field elements)
        // or direct field elements
        boolean firstRecord = true;
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                final org.w3c.dom.Element child = (org.w3c.dom.Element) children.item(i);
                final org.w3c.dom.NodeList grandchildren = child.getChildNodes();
                boolean hasGrandchildElements = false;
                for (int j = 0; j < grandchildren.getLength(); j++) {
                    if (grandchildren.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                        hasGrandchildElements = true;
                        break;
                    }
                }

                if (hasGrandchildElements) {
                    // This is a record element — its children are fields
                    if (!firstRecord) {
                        // row delimiter already written
                    }
                    boolean firstField = true;
                    for (int j = 0; j < grandchildren.getLength(); j++) {
                        if (grandchildren.item(j).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                            if (!firstField) {
                                writer.write(fieldDelimiter);
                            }
                            writer.write(quoteField(grandchildren.item(j).getTextContent()));
                            firstField = false;
                        }
                    }
                    writer.write(rowDelimiter);
                    firstRecord = false;
                } else {
                    // Direct field element — accumulate as part of a single row
                    if (!firstRecord) {
                        writer.write(fieldDelimiter);
                    }
                    writer.write(quoteField(child.getTextContent()));
                    firstRecord = false;
                }
            }
        }
    }

    private void serializeAtomicSequence(final Sequence sequence, final Writer writer) throws IOException, XPathException {
        boolean first = true;
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            if (!first) {
                writer.write(fieldDelimiter);
            }
            writer.write(quoteField(i.nextItem().getStringValue()));
            first = false;
        }
        writer.write(rowDelimiter);
    }

    private void writeRow(final ArrayType array, final Writer writer) throws IOException, XPathException {
        for (int i = 0; i < array.getSize(); i++) {
            if (i > 0) {
                writer.write(fieldDelimiter);
            }
            final Sequence member = array.get(i);
            writer.write(quoteField(member.isEmpty() ? "" : member.getStringValue()));
        }
    }

    private void writeSequenceRow(final Sequence sequence, final Writer writer) throws IOException, XPathException {
        boolean first = true;
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            if (!first) {
                writer.write(fieldDelimiter);
            }
            writer.write(quoteField(i.nextItem().getStringValue()));
            first = false;
        }
    }

    private void writeFields(final List<String> fields, final Writer writer) throws IOException {
        boolean first = true;
        for (final String field : fields) {
            if (!first) {
                writer.write(fieldDelimiter);
            }
            writer.write(quoteField(field));
            first = false;
        }
    }

    /**
     * Quote a field value per RFC 4180.
     * If alwaysQuote is true, all fields are quoted.
     * If false, only fields containing the delimiter, quote char, or newline are quoted.
     * Quote characters within the value are escaped by doubling.
     */
    private String quoteField(final String value) {
        final boolean needsQuoting = alwaysQuote
                || value.contains(fieldDelimiter)
                || value.indexOf(quoteChar) >= 0
                || value.contains("\n")
                || value.contains("\r");

        if (!needsQuoting) {
            return value;
        }

        final StringBuilder sb = new StringBuilder(value.length() + 2);
        sb.append(quoteChar);
        for (int i = 0; i < value.length(); i++) {
            final char c = value.charAt(i);
            if (c == quoteChar) {
                sb.append(quoteChar); // escape by doubling
            }
            sb.append(c);
        }
        sb.append(quoteChar);
        return sb.toString();
    }
}
