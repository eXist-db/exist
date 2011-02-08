package org.exist.util.serializer.json;

import java.io.StringWriter;
import javax.xml.transform.TransformerException;
import org.exist.dom.QName;
import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class JSONWriterTest {

    @Test
    public void nestedArrays() throws TransformerException {

        //expected result
        /*
            [ { label: "Foo", data: [ [1, 2], [3, 4] ] },
            { label: "Bar", data: [ [5, 6], [7, 8] ] } ]
        */

        QName qnJsonValue = new QName("value", JSONWriter.JASON_NS, "json");
        QName qnJsonArray = new QName("array", JSONWriter.JASON_NS, "json");
        QName qnLabel = new QName("label");
        QName qnData = new QName("data");

        int val = 1;

        StringWriter writer = new StringWriter();
        JSONWriter jsonWriter = new JSONWriter(writer);

        jsonWriter.startDocument();
        jsonWriter.startElement(qnJsonValue);
        //jsonWriter.attribute(qnJsonArray, "true");

        jsonWriter.startElement(qnJsonValue);

        //label : "Foo"
        jsonWriter.startElement(qnLabel);
        jsonWriter.characters(new String("Foo"));
        jsonWriter.endElement(qnLabel);

        //data : [["1","2"]]
        jsonWriter.startElement(qnData);
        jsonWriter.attribute(qnJsonArray, "true");
        jsonWriter.startElement(qnJsonValue);
        jsonWriter.characters(Integer.toString(val++));
        jsonWriter.endElement(qnJsonValue);
        jsonWriter.startElement(qnJsonValue);
        jsonWriter.characters(Integer.toString(val++));
        jsonWriter.endElement(qnJsonValue);
        jsonWriter.endElement(qnData);

        jsonWriter.endElement(qnJsonValue);

        jsonWriter.startElement(qnJsonValue);

        //label : "Bar"
        jsonWriter.startElement(qnLabel);
        jsonWriter.characters(new String("Bar"));
        jsonWriter.endElement(qnLabel);

        //data : [["3","4"]]
        jsonWriter.startElement(qnData);
        jsonWriter.attribute(qnJsonArray, "true");
        jsonWriter.startElement(qnJsonValue);
        jsonWriter.characters(Integer.toString(val++));
        jsonWriter.endElement(qnJsonValue);
        jsonWriter.startElement(qnJsonValue);
        jsonWriter.characters(Integer.toString(val++));
        jsonWriter.endElement(qnJsonValue);
        jsonWriter.endElement(qnData);

        jsonWriter.endElement(qnJsonValue);

        jsonWriter.endElement(qnJsonValue);
        jsonWriter.endDocument();

        assertEquals("[{ \"label\" : \"Foo\", \"data\" : [[\"1\", \"2\"]]}, { \"label\" : \"Bar\", \"data\" : [[\"3\", \"4\"]]} ]", writer.toString());
    }
}
