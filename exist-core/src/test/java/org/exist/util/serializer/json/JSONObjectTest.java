package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.StringWriter;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 */
public class JSONObjectTest {

    @Test
    public void simpleValue() throws IOException {
        final JSONObject root = new JSONObject("root");
        final JSONObject node = new JSONObject("hello");
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":\"adam\"}", writer.toString());
        }
    }

    @Test
    public void simpleValue_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);
        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : \"adam\" }", writer.toString());
        }
    }

    @Test
    public void simpleLiteral() throws IOException {
        final JSONObject root = new JSONObject("root");
        final JSONObject node = new JSONObject("hello");
        final JSONValue literalValue = new JSONValue("1");
        literalValue.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue);
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":1}", writer.toString());
        }
    }

    @Test
    public void simpleLiteral_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);
        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        final JSONValue literalValue = new JSONValue("1");
        literalValue.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue);
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : 1 }", writer.toString());
        }
    }

    @Test
    public void simpleArray() throws IOException {
        final JSONObject root = new JSONObject("root");

        final JSONObject node = new JSONObject("hello");
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":[\"adam\",\"wolfgang\"]}", writer.toString());
        }
    }

    @Test
    public void simpleArray_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);

        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        node2.setIndent(true);
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"] }", writer.toString());
        }
    }

    @Test
    public void literalArray() throws IOException {
        final JSONObject root = new JSONObject("root");

        final JSONObject node = new JSONObject("hello");
        final JSONValue literalValue1 = new JSONValue("1");
        literalValue1.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue1);
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        final JSONValue literalValue2 = new JSONValue("2");
        literalValue2.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node2.addObject(literalValue2);
        root.addObject(node2);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":[1,2]}", writer.toString());
        }
    }

    @Test
    public void literalArray_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);

        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        final JSONValue literalValue1 = new JSONValue("1");
        literalValue1.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(literalValue1);
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        node2.setIndent(true);
        final JSONValue literalValue2 = new JSONValue("2");
        literalValue2.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node2.addObject(literalValue2);
        root.addObject(node2);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : [1, 2] }", writer.toString());
        }
    }

    @Test
    public void forcedArray() throws IOException {
        final JSONObject root = new JSONObject("root");

        final JSONObject node = new JSONObject("hello");
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":[\"adam\"]}", writer.toString());
        }
    }

    @Test
    public void forcedArray_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);

        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : [\"adam\"] }", writer.toString());
        }
    }

    @Test
    public void forcedSimpleArray() throws IOException {
        final JSONObject root = new JSONObject("root");

        final JSONObject node = new JSONObject("hello");
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        node2.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        try (final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"hello\":[\"adam\",\"wolfgang\"]}", writer.toString());
        }
    }

    @Test
    public void forcedSimpleArray_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);

        final JSONObject node = new JSONObject("hello");
        node.setIndent(true);
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node.addObject(new JSONValue("adam"));
        root.addObject(node);

        final JSONObject node2 = new JSONObject("hello");
        node2.setIndent(true);
        node2.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        node2.addObject(new JSONValue("wolfgang"));
        root.addObject(node2);

        try (final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"] }", writer.toString());
        }
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

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{\"intarray\":[1]}", writer.toString());
        }
    }

    @Test
    public void literalInArrayOfOne_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);

        final JSONObject node = new JSONObject("intarray");
        node.setIndent(true);
        node.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        final JSONValue value = new JSONValue("1");
        value.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        node.addObject(value);
        root.addObject(node);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("{ \"intarray\" : [1] }", writer.toString());
        }
    }

    @Test
    public void literalInRawArrayOfOne() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        root.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        final JSONValue value = new JSONValue("1");
        value.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        root.addObject(value);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("[1]", writer.toString());
        }
    }

    @Test
    public void literalInRawArrayOfOne_indent() throws IOException {
        final JSONObject root = new JSONObject("root");
        root.setIndent(true);
        root.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
        root.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        final JSONValue value = new JSONValue("1");
        value.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
        root.addObject(value);

        try(final StringWriter writer = new StringWriter()) {
            root.serialize(writer, true);
            assertEquals("[1]", writer.toString());
        }
    }
}
