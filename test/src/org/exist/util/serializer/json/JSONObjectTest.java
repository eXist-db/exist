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

        assertEquals("{ \"hello\" : \"adam\"} ", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void simpleLiteral() throws IOException {

        JSONObject root = new JSONObject("root");
        JSONObject node = new JSONObject("hello");
        JSONValue literalValue = new JSONValue("1");
        literalValue.setSerializationType(JSONNode.SerializationType.AS_LITERAL);
        node.addObject(literalValue);
        root.addObject(node);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : 1} ", writer.toString());
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

        assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"]} ", writer.toString());
        //TODO remove trailing space
    }

    @Test
    public void literalArray() throws IOException {

        JSONObject root = new JSONObject("root");

        JSONObject node = new JSONObject("hello");
        JSONValue literalValue1 = new JSONValue("1");
        literalValue1.setSerializationType(JSONNode.SerializationType.AS_LITERAL);
        node.addObject(literalValue1);
        root.addObject(node);

        JSONObject node2 = new JSONObject("hello");
        JSONValue literalValue2 = new JSONValue("2");
        literalValue2.setSerializationType(JSONNode.SerializationType.AS_LITERAL);
        node2.addObject(literalValue2);
        root.addObject(node2);

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [1, 2]} ", writer.toString());
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

        assertEquals("{ \"hello\" : [\"adam\"]} ", writer.toString());
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

        assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"]} ", writer.toString());
        //TODO remove trailing space
    }


    @Test
    public void nestedArray() throws IOException {

        //expected result
        /*
            [ { label: "Foo", data: [ [1, 2], [3, 4] ] },
            { label: "Bar", data: [ [5, 6], [7, 8] ] } ]
        */

        JSONObject root = new JSONObject("root");
        root.setSerializationType(JSONNode.SerializationType.AS_ARRAY);

        JSONObject container = new JSONObject("value", true);
        JSONObject label = new JSONObject("label");
        label.addObject(new JSONValue("Foo"));
        container.addObject(label);
        root.addObject(container);

        JSONObject container2 = new JSONObject("value", true);
        label = new JSONObject("label");
        label.addObject(new JSONValue("Bar"));
        container2.addObject(label);
        root.addObject(container2);

        

        StringWriter writer = new StringWriter();
        root.serialize(writer, true);

        assertEquals("{ \"hello\" : [\"adam\", \"wolfgang\"]} ", writer.toString());
        //TODO remove trailing space
    }

}
