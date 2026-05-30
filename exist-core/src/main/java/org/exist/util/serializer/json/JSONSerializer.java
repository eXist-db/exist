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
import io.lacuna.bifurcan.IEntry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
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
    @Nullable private final Int2ObjectMap<String> characterMap;

    public JSONSerializer(DBBroker broker, Properties outputProperties) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        this.characterMap = SerializerUtils.getCharacterMap(outputProperties);
    }

    public void serialize(Sequence sequence, Writer writer) throws SAXException {
        final JsonFactory factory = JsonFactory.builder().build();
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
            serializeSequence(sequence, generator);
            if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.INSERT_FINAL_NEWLINE, "no"))) {
                generator.writeRaw('\n');
            }
            generator.close();
        } catch (IOException | XPathException e) {
            throw new SAXException(e.getMessage(), e);
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
            // W3C Serialization 3.1: INF, -INF, and NaN MUST raise SERE0020
            if ("NaN".equals(stringValue) || "INF".equals(stringValue) || "-INF".equals(stringValue)) {
                throw new SAXException("err:SERE0020 Numeric value " + stringValue
                        + " cannot be serialized as JSON");
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
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final String key = entry.key().getStringValue();
            generator.writeFieldName(key);
            serializeSequence(entry.value(), generator, false);
        }
        generator.writeEndObject();
    }
}
