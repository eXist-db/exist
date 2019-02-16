package org.exist.util.serializer;

import org.exist.storage.DBBroker;
import org.exist.storage.serializers.EXistOutputKeys;
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

        // ALWAYS enforce XDM serialization rules
        outputProperties.setProperty(EXistOutputKeys.XDM_SERIALIZATION, "yes");
    }

    public void serialize(final Sequence sequence) throws SAXException, XPathException {
        serialize(sequence, 1, sequence.getItemCount(), false, false, 0, 0);
    }

    public void serialize(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        switch (method) {
            case "adaptive":
                serializeAdaptive(sequence);
                break;
            case "json":
                serializeJSON(sequence, compilationTime, executionTime);
                break;
            case "xml":
            default:
                serializeXML(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
                break;
        }
    }

    public boolean normalize() {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        return !("json".equals(method) || "adaptive".equals(method));
    }

    private void serializeXML(final Sequence sequence, final int start, final int howmany, final boolean wrap, final boolean typed, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        SAXSerializer sax = null;
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(
                    SAXSerializer.class);
            sax.setOutput(writer, outputProperties);
            serializer.setProperties(outputProperties);
            serializer.setSAXHandlers(sax, sax);
            serializer.toSAX(sequence, start, howmany, wrap, typed, compilationTime, executionTime);
        } catch (SAXNotSupportedException | SAXNotRecognizedException e) {
            throw new SAXException(e.getMessage(), e);
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
        }
    }

    private void serializeJSON(final Sequence sequence, final long compilationTime, final long executionTime) throws SAXException, XPathException {
        // backwards compatibility: if the sequence contains a single element, we assume
        // it should be transformed to JSON following the rules of the old JSON writer
        if (sequence.hasOne() && Type.subTypeOf(sequence.getItemType(), Type.ELEMENT)) {
            serializeXML(sequence, 1, sequence.getItemCount(), false, false, compilationTime, executionTime);
        } else {
            JSONSerializer serializer = new JSONSerializer(broker, outputProperties);
            serializer.serialize(sequence, writer);
        }
    }

    private void serializeAdaptive(final Sequence sequence) throws SAXException, XPathException {
        final AdaptiveSerializer serializer = new AdaptiveSerializer(broker);
        serializer.setOutput(writer, outputProperties);
        serializer.serialize(sequence);
    }
}
