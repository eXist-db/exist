package org.exist.util.serializer.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.Writer;
import java.util.Map;
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
            if (Type.subTypeOf(item.getType(), Type.NUMBER)) {
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
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        final Properties xmlOutput = new Properties();
        xmlOutput.setProperty(OutputKeys.METHOD, outputProperties.getProperty(EXistOutputKeys.JSON_NODE_OUTPUT_METHOD, "xml"));
        xmlOutput.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        xmlOutput.setProperty(OutputKeys.INDENT, outputProperties.getProperty(OutputKeys.INDENT, "no"));
        try {
            serializer.setProperties(xmlOutput);
            generator.writeString(serializer.serialize((NodeValue)item));
        } catch (IOException e) {
            throw new SAXException(e.getMessage(), e);
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
        for (Map.Entry<AtomicValue, Sequence> entry: map) {
            generator.writeFieldName(entry.getKey().getStringValue());
            serializeSequence(entry.getValue(), generator);
        }
        generator.writeEndObject();
    }
}
