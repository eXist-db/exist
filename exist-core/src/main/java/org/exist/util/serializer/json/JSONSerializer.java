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
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.Properties;

/**
 * Called by {@link org.exist.util.serializer.XQuerySerializer} to serialize an XQuery sequence
 * to JSON. The JSON serializer differs from other serialization methods because it maps XQuery
 * data items to JSON.
 *
 * @author Wolf
 */
public class JSONSerializer {

    private final DBBroker broker;
    private final Properties outputProperties;

    public JSONSerializer(DBBroker broker, Properties outputProperties) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
    }

    public void serialize(Sequence sequence, Writer writer) throws SAXException {
        JsonFactory factory = new JsonFactory();
        try {
            JsonGenerator generator = factory.createGenerator(writer);
            generator.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            if ("yes".equals(outputProperties.getProperty(OutputKeys.INDENT, "no"))) {
                generator.useDefaultPrettyPrinter();
            }
            if ("yes".equals(outputProperties.getProperty(EXistOutputKeys.ALLOW_DUPLICATE_NAMES, "yes"))) {
                generator.enable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
            } else {
                generator.disable(JsonGenerator.Feature.STRICT_DUPLICATE_DETECTION);
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
        if (sequence.isEmpty()) {
            generator.writeNull();
        } else if (sequence.hasOne() && "no".equals(outputProperties.getProperty(EXistOutputKeys.JSON_ARRAY_OUTPUT, "no"))) {
            serializeItem(sequence.itemAt(0), generator);
        } else {
            generator.writeStartArray();
            for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
                serializeItem(i.nextItem(), generator);
            }
            generator.writeEndArray();
        }
    }

    private void serializeItem(Item item, JsonGenerator generator) throws IOException, XPathException, SAXException {
        if (item.getType() == Type.ARRAY) {
            serializeArray((ArrayType) item, generator);
        } else if (item.getType() == Type.MAP) {
            serializeMap((MapType) item, generator);
        } else if (Type.subTypeOf(item.getType(), Type.ATOMIC)) {
            if (Type.subTypeOfUnion(item.getType(), Type.NUMBER)) {
                generator.writeNumber(item.getStringValue());
            } else {
                switch (item.getType()) {
                    case Type.BOOLEAN:
                        generator.writeBoolean(((AtomicValue)item).effectiveBooleanValue());
                        break;
                    default:
                        generator.writeString(item.getStringValue());
                        break;
                }
            }
        } else if (Type.subTypeOf(item.getType(), Type.NODE)) {
            serializeNode(item, generator);
        }
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
            serializeSequence(member, generator);
        }
        generator.writeEndArray();
    }

    private void serializeMap(MapType map, JsonGenerator generator) throws IOException, XPathException, SAXException {
        generator.writeStartObject();
        for (final IEntry<AtomicValue, Sequence> entry: map) {
            generator.writeFieldName(entry.key().getStringValue());
            serializeSequence(entry.value(), generator);
        }
        generator.writeEndObject();
    }
}
