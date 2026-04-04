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
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

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
    private final boolean canonical;
    @Nullable private final Int2ObjectMap<String> characterMap;

    public JSONSerializer(DBBroker broker, Properties outputProperties) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        final String canonicalProp = outputProperties.getProperty(EXistOutputKeys.CANONICAL);
        this.canonical = isBooleanTrue(canonicalProp);
        this.characterMap = SerializerUtils.getCharacterMap(outputProperties);
    }

    public void serialize(Sequence sequence, Writer writer) throws SAXException {
        // QT4: escape-solidus controls whether / is escaped as \/
        // Default is "no" for XQ 3.1 compatibility (parameter doesn't exist in 3.1 spec)
        // Canonical JSON (RFC 8785): solidus is NOT escaped
        final boolean escapeSolidus = !canonical && isBooleanTrue(
                outputProperties.getProperty(EXistOutputKeys.ESCAPE_SOLIDUS, "no"));
        final JsonFactory factory = JsonFactory.builder()
                .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, escapeSolidus)
                .build();
        try {
            JsonGenerator generator = factory.createGenerator(writer);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            if (isBooleanTrue(outputProperties.getProperty(OutputKeys.INDENT, "no"))) {
                final int indentSpaces = Integer.parseInt(
                        outputProperties.getProperty(EXistOutputKeys.INDENT_SPACES, "4"));
                final com.fasterxml.jackson.core.util.DefaultPrettyPrinter pp =
                        new com.fasterxml.jackson.core.util.DefaultPrettyPrinter();
                pp.indentArraysWith(
                        com.fasterxml.jackson.core.util.DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(
                                " ".repeat(indentSpaces)));
                pp.indentObjectsWith(
                        com.fasterxml.jackson.core.util.DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withIndent(
                                " ".repeat(indentSpaces)));
                generator.setPrettyPrinter(pp);
            }
            // allow-duplicate-names defaults to "no" per W3C spec, so we enable strict
            // duplicate detection by default and only disable it when explicitly set to "yes"
            if ("no".equals(outputProperties.getProperty(EXistOutputKeys.ALLOW_DUPLICATE_NAMES, "no"))) {
                generator.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
            }
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
        // Each line must be a separate root-level value. Jackson adds separator
        // whitespace between root values, so we serialize each item to a string
        // and concatenate with newlines.
        final boolean escapeSolidus = !isBooleanFalse(
                outputProperties.getProperty(EXistOutputKeys.ESCAPE_SOLIDUS, "yes"));
        boolean first = true;
        for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            if (!first) {
                generator.writeRaw('\n');
            }
            // Serialize this item to a standalone string
            final java.io.StringWriter lineWriter = new java.io.StringWriter();
            final JsonFactory lineFactory = JsonFactory.builder()
                    .configure(JsonWriteFeature.ESCAPE_FORWARD_SLASHES, escapeSolidus)
                    .build();
            final JsonGenerator lineGen = lineFactory.createGenerator(lineWriter);
            lineGen.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            serializeItem(i.nextItem(), lineGen);
            lineGen.close();
            // Write the line's JSON as raw content to avoid Jackson's root separator
            generator.writeRaw(lineWriter.toString());
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
            if (canonical) {
                // RFC 8785: cast to double, use shortest representation
                final double d = ((org.exist.xquery.value.NumericValue) item).getDouble();
                if (!Double.isFinite(d)) {
                    throw new SAXException("err:SERE0020 Numeric value " + item.getStringValue()
                            + " cannot be serialized in canonical JSON");
                }
                generator.writeRawValue(canonicalDoubleString(d));
                return;
            }
            final String stringValue = item.getStringValue();
            // W3C Serialization 3.1: INF, -INF, and NaN MUST raise SERE0020
            if ("NaN".equals(stringValue) || "INF".equals(stringValue) || "-INF".equals(stringValue)) {
                throw new SAXException("err:SERE0020 Numeric value " + stringValue
                        + " cannot be serialized as JSON");
            } else if ("-0".equals(stringValue)) {
                // Negative zero: write as 0 (QT4 allows either 0 or -0)
                generator.writeNumber(stringValue);
            } else {
                generator.writeNumber(stringValue);
            }
        } else if (item.getType() == Type.BOOLEAN) {
            generator.writeBoolean(((AtomicValue) item).effectiveBooleanValue());
        } else {
            writeStringWithCharMap(generator, item.getStringValue());
        }
    }

    /**
     * RFC 8785 canonical double formatting.
     * Uses ECMAScript shortest representation: minimum digits to uniquely
     * identify the double value. Plain notation for [1e-6, 1e21), exponential
     * notation otherwise with lowercase 'e'.
     */
    private static String canonicalDoubleString(final double value) {
        if (value == 0) return "0";
        if (value == Double.MIN_VALUE) return "5e-324";
        if (value == -Double.MIN_VALUE) return "-5e-324";

        final java.math.BigDecimal bd = java.math.BigDecimal.valueOf(value).stripTrailingZeros();
        final double abs = Math.abs(value);
        if (abs >= 1e-6 && abs < 1e21) {
            return bd.toPlainString();
        } else {
            return bd.toString().replace('E', 'e');
        }
    }

    /**
     * Apply use-character-maps substitutions to a string value.
     * Character map replacements are written raw (not escaped by JSON).
     */
    private String applyCharacterMap(final String value) {
        if (characterMap == null || characterMap.isEmpty()) {
            return value;
        }
        final StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); ) {
            final int cp = value.codePointAt(i);
            i += Character.charCount(cp);
            final String replacement = characterMap.get(cp);
            if (replacement != null) {
                sb.append(replacement);
            } else {
                sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /**
     * Write a string value to the JSON generator, applying character map
     * substitutions. The mapped string is passed through writeString so
     * Jackson handles JSON structural separators and escaping correctly.
     */
    private void writeStringWithCharMap(final JsonGenerator generator, final String value) throws IOException {
        if (characterMap == null || characterMap.isEmpty()) {
            generator.writeString(value);
        } else {
            generator.writeString(applyCharacterMap(value));
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
            writeStringWithCharMap(generator, serializer.serialize((NodeValue)item));
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
            // W3C Serialization 3.1: multi-item sequences within arrays raise SERE0023
            if (member.getItemCount() > 1) {
                throw new SAXException("err:SERE0023 Array member at position " + (i + 1)
                        + " is a sequence of " + member.getItemCount() + " items");
            }
            serializeSequence(member, generator, false);
        }
        generator.writeEndArray();
    }

    private void serializeMap(MapType map, JsonGenerator generator) throws IOException, XPathException, SAXException {
        generator.writeStartObject();

        // Canonical JSON (RFC 8785): sort keys by UTF-16 code unit order
        final Iterable<IEntry<AtomicValue, Sequence>> entries;
        if (canonical) {
            final List<IEntry<AtomicValue, Sequence>> sorted = new ArrayList<>();
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                sorted.add(entry);
            }
            sorted.sort((a, b) -> {
                try {
                    return a.key().getStringValue().compareTo(b.key().getStringValue());
                } catch (XPathException e) {
                    return 0;
                }
            });
            entries = sorted;
        } else {
            final List<IEntry<AtomicValue, Sequence>> list = new ArrayList<>();
            for (final IEntry<AtomicValue, Sequence> entry : map) {
                list.add(entry);
            }
            entries = list;
        }

        for (final IEntry<AtomicValue, Sequence> entry : entries) {
            final String key = entry.key().getStringValue();
            generator.writeFieldName(key);
            serializeSequence(entry.value(), generator, false);
        }
        generator.writeEndObject();
    }
}
