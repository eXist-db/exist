package org.exist.util.serializer.json;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import org.exist.storage.DBBroker;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

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

    public JSONSerializer(DBBroker broker) {
        super();
        this.broker = broker;
    }

    public void serialize(Sequence sequence, Writer writer, Properties outputProperties) throws SAXException {
        JsonFactory factory = new JsonFactory();
        try {
            JsonGenerator generator = factory.createGenerator(writer);
            if ("yes".equals(outputProperties.getProperty(OutputKeys.INDENT, "no"))) {
                generator.useDefaultPrettyPrinter();
            }
            serializeSequence(sequence, generator);
            generator.close();
        } catch (IOException | XPathException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void serializeSequence(Sequence sequence, JsonGenerator generator) throws IOException, XPathException {
        if (sequence.isEmpty()) {
            generator.writeNull();
        } else if (sequence.hasOne()) {
            serializeItem(sequence.itemAt(0), generator);
        } else {
            generator.writeStartArray();
            for (SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
                serializeItem(i.nextItem(), generator);
            }
            generator.writeEndArray();
        }
    }

    private void serializeItem(Item item, JsonGenerator generator) throws IOException, XPathException {
        if (Type.subTypeOf(item.getType(), Type.ATOMIC)) {
            if (Type.subTypeOf(item.getType(), Type.NUMBER)) {
                generator.writeNumber(item.getStringValue());
            } else if (Type.subTypeOf(item.getType(), Type.STRING)) {
                generator.writeString(item.getStringValue());
            } else {
                switch (item.getType()) {
                    case Type.ARRAY:
                        serializeArray((ArrayType) item, generator);
                        break;
                    case Type.MAP:
                        serializeMap((MapType) item, generator);
                        break;
                    case Type.BOOLEAN:
                        generator.writeBoolean(((AtomicValue)item).effectiveBooleanValue());
                        break;
                    default:
                        throw new XPathException(ErrorCodes.SERE0021, "Invalid type: " + Type.getTypeName(item.getType()));
                }
            }
        }
    }

    private void serializeArray(ArrayType array, JsonGenerator generator) throws IOException, XPathException {
        generator.writeStartArray();
        for (int i = 0; i < array.getSize(); i++) {
            final Sequence member = array.get(i);
            serializeSequence(member, generator);
        }
        generator.writeEndArray();
    }

    private void serializeMap(MapType map, JsonGenerator generator) throws IOException, XPathException {
        generator.writeStartObject();
        for (Map.Entry<AtomicValue, Sequence> entry: map) {
            generator.writeFieldName(entry.getKey().getStringValue());
            serializeSequence(entry.getValue(), generator);
        }
        generator.writeEndObject();
    }
}
