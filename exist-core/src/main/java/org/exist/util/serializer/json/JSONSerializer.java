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
package org.exist.util.serializer.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import io.lacuna.bifurcan.IEntry;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * Called by {@link org.exist.util.serializer.XQuerySerializer} to serialize an XQuery sequence
 * to JSON. The JSON serializer differs from other serialization methods because it maps XQuery
 * data items to JSON.
 *
 * Per W3C XSLT and XQuery Serialization 3.1 Section 10 (JSON Output Method).
 *
 * @author Wolf
 */
public class JSONSerializer {

    private final DBBroker broker;
    private final Properties outputProperties;
    private final boolean allowDuplicateNames;

    public JSONSerializer(DBBroker broker, Properties outputProperties) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        this.allowDuplicateNames = "yes".equals(
                outputProperties.getProperty(EXistOutputKeys.ALLOW_DUPLICATE_NAMES, "yes"));
    }

    public void serialize(Sequence sequence, Writer writer) throws SAXException {
        // QT4: escape-solidus controls whether / is escaped as \/ (default: true)
        final boolean escapeSolidus = !isBooleanFalse(
                outputProperties.getProperty(EXistOutputKeys.ESCAPE_SOLIDUS, "yes"));
        final JsonFactory factory = JsonFactory.builder()
                .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, escapeSolidus)
                .build();
        try {
            JsonGenerator generator = factory.createGenerator(writer);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            if ("yes".equals(outputProperties.getProperty(OutputKeys.INDENT, "no"))) {
                generator.useDefaultPrettyPrinter();
            }
            // Duplicate detection is handled manually in serializeMap for proper SERE0022 errors
            generator.disable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
            final boolean jsonLines = isBooleanTrue(
                    outputProperties.getProperty(EXistOutputKeys.JSON_LINES, "no"));
            if (jsonLines) {
                serializeJsonLines(sequence, generator);
            } else {
                serializeSequence(sequence, generator);
            }
            if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.INSERT_FINAL_NEWLINE, "no"))) {
                generator.writeRaw('\n');
            }
            generator.close();
        } catch (IOException | XPathException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    /**
     * JSON Lines format (NDJSON): one JSON value per line, no array wrapper.
     * Per QT4 Serialization 4.0, when json-lines=true.
     */
    private void serializeJsonLines(Sequence sequence, JsonGenerator generator) throws IOException, XPathException, SAXException {
        if (sequence.isEmpty()) {
            return;
        }
        boolean first = true;
        for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            if (!first) {
                generator.writeRaw('\n');
            }
            serializeItem(i.nextItem(), generator);
            generator.flush();
            first = false;
        }
    }

    private void serializeSequence(Sequence sequence, JsonGenerator generator) throws IOException, XPathException, SAXException {
        serializeSequence(sequence, generator, false);
    }

    private void serializeSequence(Sequence sequence, JsonGenerator generator, boolean allowMultiItem) throws IOException, XPathException, SAXException {
        if (sequence.isEmpty()) {
            generator.writeNull();
        } else if (sequence.hasOne() && "no".equals(outputProperties.getProperty(EXistOutputKeys.JSON_ARRAY_OUTPUT, "no"))) {
            serializeItem(sequence.itemAt(0), generator);
        } else if (!allowMultiItem) {
            // SERE0023: JSON output method cannot serialize a sequence of more than one item
            // at the top level or as a map entry value
            throw new SAXException("err:SERE0023 Sequence of " + sequence.getItemCount()
                    + " items cannot be serialized using the JSON output method");
        } else {
            // Inside arrays, multi-item sequences become JSON arrays
            generator.writeStartArray();
            for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
                serializeItem(i.nextItem(), generator);
            }
            generator.writeEndArray();
        }
    }

    private void serializeItem(Item item, JsonGenerator generator) throws IOException, XPathException, SAXException {
        if (item.getType() == Type.ARRAY_ITEM) {
            serializeArray((ArrayType) item, generator);
        } else if (item.getType() == Type.MAP_ITEM) {
            serializeMap((MapType) item, generator);
        } else if (Type.subTypeOf(item.getType(), Type.ANY_ATOMIC_TYPE)) {
            serializeAtomicValue(item, generator);
        } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
            serializeNode(item, generator);
        } else if (Type.subTypeOf(item.getType(), Type.FUNCTION)) {
            throw new SAXException("err:SERE0021 Sequence contains a function item, which cannot be serialized as JSON");
        }
    }

    private void serializeAtomicValue(Item item, JsonGenerator generator) throws IOException, XPathException, SAXException {
        if (Type.subTypeOfUnion(item.getType(), Type.NUMERIC)) {
            final String stringValue = item.getStringValue();
            // Handle special float/double values per W3C Serialization
            if ("NaN".equals(stringValue)) {
                // QT4: NaN serializes as JSON null
                generator.writeNull();
            } else if ("INF".equals(stringValue)) {
                // QT4: +INF serializes as 1e9999
                generator.writeRawValue("1e9999");
            } else if ("-INF".equals(stringValue)) {
                // QT4: -INF serializes as -1e9999
                generator.writeRawValue("-1e9999");
            } else if ("-0".equals(stringValue)) {
                // Negative zero: write as 0 (QT4 allows either 0 or -0)
                generator.writeNumber(stringValue);
            } else {
                generator.writeNumber(stringValue);
            }
        } else if (item.getType() == Type.BOOLEAN) {
            generator.writeBoolean(((AtomicValue) item).effectiveBooleanValue());
        } else {
            generator.writeString(item.getStringValue());
        }
    }

    private static boolean isBooleanTrue(final String value) {
        if (value == null) return false;
        final String v = value.trim();
        return "yes".equals(v) || "true".equals(v) || "1".equals(v);
    }

    private static boolean isBooleanFalse(final String value) {
        if (value == null) return false;
        final String v = value.trim();
        return "no".equals(v) || "false".equals(v) || "0".equals(v);
    }

    private void serializeNode(Item item, JsonGenerator generator) throws SAXException {
        final Serializer serializer = broker.borrowSerializer();
        final Properties xmlOutput = new Properties();
        xmlOutput.setProperty(OutputKeys.METHOD, outputProperties.getProperty(EXistOutputKeys.JSON_NODE_OUTPUT_METHOD, "xml"));
        xmlOutput.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        xmlOutput.setProperty(OutputKeys.INDENT, outputProperties.getProperty(OutputKeys.INDENT, "no"));
        try {
            serializer.setProperties(xmlOutput);
            generator.writeString(serializer.serialize((NodeValue)item));
        } catch (IOException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            broker.returnSerializer(serializer);
        }
    }

    private void serializeArray(ArrayType array, JsonGenerator generator) throws IOException, XPathException, SAXException {
        generator.writeStartArray();
        for (int i = 0; i < array.getSize(); i++) {
            final Sequence member = array.get(i);
            // Array members can be multi-item sequences — each becomes a nested JSON array
            serializeSequence(member, generator, true);
        }
        generator.writeEndArray();
    }

    private void serializeMap(MapType map, JsonGenerator generator) throws IOException, XPathException, SAXException {
        generator.writeStartObject();
        final Set<String> seenKeys = allowDuplicateNames ? null : new HashSet<>();
        for (final IEntry<AtomicValue, Sequence> entry: map) {
            final String key = entry.key().getStringValue();
            if (seenKeys != null && !seenKeys.add(key)) {
                throw new SAXException("err:SERE0022 Duplicate key '" + key + "' in map and allow-duplicate-names is 'no'");
            }
            generator.writeFieldName(key);
            serializeSequence(entry.value(), generator, false);
        }
        generator.writeEndObject();
    }
}
