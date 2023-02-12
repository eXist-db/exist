/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentBuilderReceiver;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.SerializerUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.StringWriter;
import java.util.Properties;

import static org.exist.Namespaces.XSLT_XQUERY_SERIALIZATION_NS;

public class FunSerialize extends BasicFunction {

    private final static String DEFAULT_ITEM_SEPARATOR = " ";

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
                        new FunctionParameterSequenceType("parameters", Type.ITEM, Cardinality.ZERO_OR_ONE, "The serialization parameters as either a output:serialization-parameters element or a map")
                },
                new FunctionParameterSequenceType("result", Type.STRING, Cardinality.EXACTLY_ONE, "the string containing the serialized node set.")
        )
    };

    public FunSerialize(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final Properties outputProperties;
        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            outputProperties = getSerializationProperties(this, args[1].itemAt(0));
        } else {
            outputProperties = new Properties();
        }

        try(final StringWriter writer = new StringWriter()) {
            final XQuerySerializer xqSerializer = new XQuerySerializer(context.getBroker(), outputProperties, writer);

            final Sequence input = args[0];
            if (xqSerializer.normalize()) {
                final String itemSeparator = outputProperties.getProperty(EXistOutputKeys.ITEM_SEPARATOR, DEFAULT_ITEM_SEPARATOR);
                final Sequence normalized = normalize(this, context, input, itemSeparator);
                xqSerializer.serialize(normalized);
            }
            else {
                xqSerializer.serialize(input);
            }

            return new StringValue(this, writer.toString());
        } catch (final IOException | SAXException e) {
            throw new XPathException(this, FnModule.SENR0001, e.getMessage());
        }
    }

    public static Properties getSerializationProperties(final Expression callingExpr, final Item parametersItem) throws XPathException {
        final Properties outputProperties;
        if (parametersItem.getType() == Type.MAP_ITEM) {
            outputProperties = SerializerUtils.getSerializationOptions(callingExpr, (AbstractMapType) parametersItem);
        } else if (isSerializationParametersElement(parametersItem)) {
            outputProperties = new Properties();
            SerializerUtils.getSerializationOptions(callingExpr, (NodeValue) parametersItem, outputProperties);
        } else {
            throw new XPathException(callingExpr, ErrorCodes.XPTY0004, "The parameters element must be either an " +
                    "output:serialization-parameters element or a map");
        }
        return outputProperties;
    }

    /**
     * Returns true if the item is an element named output:serialization-parameter
     *
     * @param item An item to test
     *
     * @return true if this is a serialization parameters element
     */
    private static boolean isSerializationParametersElement(final Item item) {
        if(item.getType() == Type.ELEMENT) {
            final Element element = (Element)item;
            return XSLT_XQUERY_SERIALIZATION_NS.equals(element.getNamespaceURI())
                    && "serialization-parameters".equals(element.getLocalName());
        } else {
            return false;
        }
    }

    /**
     * Sequence normalization as described in
     * <a href="http://www.w3.org/TR/xslt-xquery-serialization-30/#serdm">XSLT and XQuery Serialization 3.0 - Sequence Normalization</a>.
     *
     * @param callingExpr the expression from which the function is called.
     *                    Needed for error reporting
     * @param context current context.
     * @param input non-normalized sequence.
     * @param itemSeparator the item separator placed between each item.
     *
     * @return normalized sequence.
     *
     * @throws XPathException in case of dynamic error.
     */
    public static Sequence normalize(final Expression callingExpr, final XQueryContext context, final Sequence input, final String itemSeparator) throws XPathException {
        // "If the sequence that is input to serialization is empty, create a sequence S1 that consists of a zero-length string."
        if (input.isEmpty()) {
            return StringValue.EMPTY_STRING;
        }
        // flatten arrays
        final ValueSequence step1 = new ValueSequence();
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            if (next.getType() == Type.ARRAY_ITEM) {
                final Sequence sequence = ArrayType.flatten(next);
                for (final SequenceIterator si = sequence.iterate(); si.hasNext(); ) {
                    step1.add(si.nextItem());
                }
            } else {
                step1.add(next);
            }
        }

        final ValueSequence step2 = new ValueSequence(step1.getItemCount());
        for (final SequenceIterator i = step1.iterate(); i.hasNext(); ) {
            final Item next = i.nextItem();
            final int itemType = next.getType();
            if (Type.subTypeOf(itemType, Type.NODE)) {
                if (itemType == Type.ATTRIBUTE || itemType == Type.NAMESPACE || itemType == Type.FUNCTION) {
                    throw new XPathException(callingExpr, FnModule.SENR0001,
                        "It is an error if an item in the sequence to serialize is an attribute node or a namespace node.");
                }
                step2.add(next);
            } else {
                // atomic value
                // "For each item in S1, if the item is atomic, obtain the lexical representation of the item by
                // casting it to an xs:string and copy the string representation to the new sequence;"
                final StringValue stringRepresentation = new StringValue(callingExpr, next.getStringValue());
                step2.add(stringRepresentation);
            }
        }

        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            final DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(callingExpr, builder, true);
            final String safeItemSeparator = itemSeparator == null ? "" : itemSeparator;
            for (final SequenceIterator i = step2.iterate(); i.hasNext(); ) {
                final Item next = i.nextItem();
                if (Type.subTypeOf(next.getType(), Type.NODE)) {
                    next.copyTo(context.getBroker(), receiver);
                } else {
                    final String stringValue = next.getStringValue();
                    receiver.characters(stringValue);
                }
                // add itemSeparator if there is a next item
                if (i.hasNext()) {
                    receiver.characters(safeItemSeparator);
                }
            }
            return (DocumentImpl)receiver.getDocument();
        } catch (final SAXException e) {
            throw new XPathException(callingExpr, FnModule.SENR0001, e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }
}
