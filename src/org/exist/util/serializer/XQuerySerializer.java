package org.exist.util.serializer;

import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.json.JSONSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import java.io.Writer;
import java.util.Properties;

/**
 * Utility class for writing out XQuery results. It is an abstraction around
 * eXist's internal serializers specialized on writing XQuery sequences.
 *
 * @author Wolf
 */
public class XQuerySerializer {

    private final Properties outputProperties;
    private final DBBroker broker;
    private final Writer writer;

    public XQuerySerializer(DBBroker broker, Properties outputProperties, Writer writer) {
        super();
        this.broker = broker;
        this.outputProperties = outputProperties;
        this.writer = writer;
    }

    public void serialize(Sequence sequence) throws SAXException, XPathException {
        serialize(sequence, 1, sequence.getItemCount(), false, false);
    }

    public void serialize(Sequence sequence, int start, int howmany, boolean wrap, boolean typed) throws SAXException, XPathException {
        if (isJSON()) {
            serializeJSON(sequence);
        } else {
            serializeXML(sequence, start, howmany, wrap, typed);
        }
    }

    public boolean isJSON() {
        return "json".equals(outputProperties.getProperty(OutputKeys.METHOD, "xml"));
    }

    private void serializeXML(Sequence sequence, int start, int howmany, boolean wrap, boolean typed) throws SAXException, XPathException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            sax.setOutput(writer, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
            serializer.toSAX(sequence, start, howmany, wrap, typed);
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
        }
    }

    private void serializeJSON(Sequence sequence) throws SAXException, XPathException {
        // backwards compatibility: if the sequence contains a single element, we assume
        // it should be transformed to JSON following the rules of the old JSON writer
        if (sequence.hasOne() && Type.subTypeOf(sequence.getItemType(), Type.ELEMENT)) {
            serializeXML(sequence, 1, sequence.getItemCount(), false, false);
        } else {
            JSONSerializer serializer = new JSONSerializer(broker, outputProperties);
            serializer.serialize(sequence, writer);
        }
    }
}
