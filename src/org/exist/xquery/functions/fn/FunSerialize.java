package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.StringWriter;
import java.util.Properties;

public class FunSerialize extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("serialize", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
                "This function serializes the supplied input sequence $arg as described in XSLT and XQuery Serialization 3.0, returning the " +
                        "serialized representation of the sequence as a string.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("args", Type.ITEM, Cardinality.ZERO_OR_MORE, "The node set to serialize")
                },
                new FunctionParameterSequenceType("result", Type.STRING, Cardinality.EXACTLY_ONE, "the string containing the serialized node set.")
        ),
        new FunctionSignature(
                new QName("serialize", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
                "This function serializes the supplied input sequence $arg as described in XSLT and XQuery Serialization 3.0, returning the " +
                        "serialized representation of the sequence as a string.",
                new SequenceType[] {
                        new FunctionParameterSequenceType("args", Type.ITEM, Cardinality.ZERO_OR_MORE, "The node set to serialize"),
                        new FunctionParameterSequenceType("parameters", Type.NODE, Cardinality.ZERO_OR_ONE, "The serialization parameters")
                },
                new FunctionParameterSequenceType("result", Type.STRING, Cardinality.EXACTLY_ONE, "the string containing the serialized node set.")
        )
    };

    public FunSerialize(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final Properties outputProperties = new Properties();
        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
                SerializerUtils.getSerializationOptions(this, (NodeValue) args[1].itemAt(0), outputProperties);
        }

        final StringWriter writer = new StringWriter();
        XQuerySerializer xqSerializer = new XQuerySerializer(context.getBroker(), outputProperties, writer);

        Sequence seq = args[0];
        if (!xqSerializer.isJSON()) {
            seq = normalize(seq);
        }
        try {
            xqSerializer.serialize(seq);
            return new StringValue(writer.toString());
        } catch (final SAXException e) {
            throw new XPathException(this, FnModule.SENR0001, e.getMessage());
        }
    }

    /**
     * Sequence normalization as described in
     * http://www.w3.org/TR/xslt-xquery-serialization-30/#serdm
     *
     * @param input non-normalized sequence
     * @return normalized sequence
     * @throws XPathException
     */
    protected Sequence normalize(Sequence input) throws XPathException {
        if (input.isEmpty())
            // "If the sequence that is input to serialization is empty, create a sequence S1 that consists of a zero-length string."
            {return StringValue.EMPTY_STRING;}
        final ValueSequence temp = new ValueSequence(input.getItemCount());
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (Type.subTypeOf(next.getType(), Type.NODE)) {
                if (next.getType() == Type.ATTRIBUTE || next.getType() == Type.NAMESPACE || next.getType() == Type.FUNCTION_REFERENCE)
                    {throw new XPathException(this, FnModule.SENR0001,
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
            throw new XPathException(this, FnModule.SENR0001, e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }
}