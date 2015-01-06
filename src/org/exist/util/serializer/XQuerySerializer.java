package org.exist.util.serializer;

import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.json.JSONSerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FnModule;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;

import javax.xml.transform.OutputKeys;
import java.io.IOException;
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
    private final XQueryContext context;

    public XQuerySerializer(XQueryContext context, Properties outputProperties) {
        super();
        this.context = context;
        this.outputProperties = outputProperties;
    }

    public void serialize(Sequence sequence, Writer writer) throws SAXException, XPathException {
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        if ("json".equals(method)) {
            serializeJSON(sequence, writer);
        } else {
            serializeXML(sequence, writer);
        }
    }

    private void serializeXML(Sequence sequence, Writer writer) throws SAXException, XPathException {
        final Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        try {
            serializer.setProperties(outputProperties);
            sequence = normalize(sequence);
            for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    final String val = serializer.serialize((NodeValue) next);
                    writer.append(val);
                }
            }
        } catch (SAXNotSupportedException | SAXNotRecognizedException | IOException e) {
            throw new SAXException(e.getMessage(), e);
        }
    }

    private void serializeJSON(Sequence sequence, Writer writer) throws SAXException, XPathException {
        // backwards compatibility: if the sequence contains a single element, we assume
        // it should be transformed to JSON following the rules of the old JSON writer
        if (sequence.hasOne() && Type.subTypeOf(sequence.getItemType(), Type.ELEMENT)) {
            serializeXML(sequence, writer);
        }
        JSONSerializer serializer = new JSONSerializer(context.getBroker());
        serializer.serialize(sequence, writer, outputProperties);
    }

    /**
     * Sequence normalization as described in
     * http://www.w3.org/TR/xslt-xquery-serialization-30/#serdm
     *
     * @param input non-normalized sequence
     * @return normalized sequence
     * @throws org.exist.xquery.XPathException
     */
    private Sequence normalize(Sequence input) throws XPathException {
        if (input.isEmpty())
        // "If the sequence that is input to serialization is empty, create a sequence S1 that consists of a zero-length string."
        {return StringValue.EMPTY_STRING;}
        final ValueSequence temp = new ValueSequence(input.getItemCount());
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (Type.subTypeOf(next.getType(), Type.NODE)) {
                if (next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE || next.getType() == Type.FUNCTION_REFERENCE)
                {throw new XPathException(FnModule.SENR0001,
                        "It is an error if an item in the sequence to serialize is an attribute node or a namespace node.");}
                temp.add(next);
            } else {
                // atomic value
                Item last = null;
                if (!temp.isEmpty())
                {last = temp.itemAt(temp.getItemCount() - 1);}
                if (last != null && last.getType() == Type.STRING)
                // "For each subsequence of adjacent strings in S2, copy a single string to the new sequence
                // equal to the values of the strings in the subsequence concatenated in order, each separated
                // by a single space."
                {((StringValue)last).append(" " + next.getStringValue());}
                else
                // "For each item in S1, if the item is atomic, obtain the lexical representation of the item by
                // casting it to an xs:string and copy the string representation to the new sequence;"
                {temp.add(new StringValue(next.getStringValue()));}
            }
        }

        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
            for (final SequenceIterator i = temp.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    next.copyTo(context.getBroker(), receiver);
                } else {
                    receiver.characters(next.getStringValue());
                }
            }
            return (DocumentImpl)receiver.getDocument();
        } catch (final SAXException e) {
            throw new XPathException(FnModule.SENR0001, e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }
}
