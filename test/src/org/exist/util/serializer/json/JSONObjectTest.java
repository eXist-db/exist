package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.StringWriter;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author Adam Retter <adam@exist-db.org>
 */
public class JSONObjectTest {

    @Test
    public void simpleValue() throws IOException {

        JSONObject root = new JSONObject("root");
        JSONObject node = new JSONObject("hello");
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : \"adam\" }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void simpleLiteral() throws IOException {

        JSONObject root = new JSONObject("root");
        JSONObject node = new JSONObject("hello");
        JSONValue literalValue = new JSONValue("1");
        literalValue.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue);
        root.addObject(node);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : 1 }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void simpleArray() throws IOException {

        JSONObject root = new JSONObject("root");

        JSONObject node = new JSONObject("hello");
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        JSONObject node2 = new JSONObject("hello");
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"] }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void literalArray() throws IOException {

        JSONObject root = new JSONObject("root");

        JSONObject node = new JSONObject("hello");
        JSONValue literalValue1 = new JSONValue("1");
        literalValue1.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue1);
        root.addObject(node);

        JSONObject node2 = new JSONObject("hello");
        JSONValue literalValue2 = new JSONValue("2");
        literalValue2.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node2.addObject(literalValue2);
        root.addObject(node2);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [1, 2] }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void forcedArray() throws IOException {

        JSONObject root = new JSONObject("root");

        JSONObject node = new JSONObject("hello");
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [\"adam\"] }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void forcedSimpleArray() throws IOException {

        JSONObject root = new JSONObject("root");

        JSONObject node = new JSONObject("hello");
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        JSONObject node2 = new JSONObject("hello");
        node2.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"] }", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void literalInArrayOfOne() throws IOException {
        final JSONObject root = new JSONObject("root");

        final JSONObject node = new JSONObject("intarray");
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        final JSONValue value = new JSONValue("1");
        value.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(value);
        root.addObject(node);

        final StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"intarray\" : [1] }", writer.toString());
    }

    @Test
    public void literalInRawArrayOfOne() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        root.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        final JSONValue value = new JSONValue("1");
        value.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        root.addObject(value);

        final StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("[1]", writer.toString());
    }
}
