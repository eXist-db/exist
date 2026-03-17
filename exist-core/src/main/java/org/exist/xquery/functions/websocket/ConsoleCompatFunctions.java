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
package org.exist.xquery.functions.websocket;

import org.exist.dom.QName;
import org.exist.storage.serializers.Serializer;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.StringWriter;
import java.util.Properties;

/**
 * Backward-compatible console:log() and console:send() functions.
 * Delegates to {@link WebSocketModule} static methods.
 */
public class ConsoleCompatFunctions extends BasicFunction {

    private static final Properties SERIALIZATION_PROPERTIES = new Properties();
    static {
        SERIALIZATION_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
        SERIALIZATION_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }

    private static final Properties JSON_SERIALIZATION_PROPERTIES = new Properties();
    static {
        JSON_SERIALIZATION_PROPERTIES.setProperty(OutputKeys.INDENT, "yes");
        JSON_SERIALIZATION_PROPERTIES.setProperty(OutputKeys.METHOD, "json");
    }

    public static final FunctionSignature[] CONSOLE_LOG = {
        new FunctionSignature(
            new QName("log", ConsoleCompatModule.NAMESPACE_URI, ConsoleCompatModule.PREFIX),
            "Log items to the 'default' console channel.",
            new SequenceType[] {
                new FunctionParameterSequenceType("items", Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "Values to log.")
            },
            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "Empty")
        ),
        new FunctionSignature(
            new QName("log", ConsoleCompatModule.NAMESPACE_URI, ConsoleCompatModule.PREFIX),
            "Log items to a specific console channel.",
            new SequenceType[] {
                new FunctionParameterSequenceType("channel", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The channel to log to."),
                new FunctionParameterSequenceType("items", Type.ITEM, Cardinality.ZERO_OR_MORE,
                    "Values to log.")
            },
            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "Empty")
        )
    };

    public static final FunctionSignature CONSOLE_SEND = new FunctionSignature(
        new QName("send", ConsoleCompatModule.NAMESPACE_URI, ConsoleCompatModule.PREFIX),
        "Send a JSON message to a console channel.",
        new SequenceType[] {
            new FunctionParameterSequenceType("channel", Type.STRING, Cardinality.EXACTLY_ONE,
                "The channel to send to."),
            new FunctionParameterSequenceType("items", Type.ITEM, Cardinality.ZERO_OR_ONE,
                "Value to send as JSON.")
        },
        new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "Empty")
    );

    private Expression parent = null;

    public ConsoleCompatFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        super.analyze(contextInfo);
        parent = contextInfo.getParent();
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String channel = getArgumentCount() == 1 ? "default" : args[0].getStringValue();
        final Sequence params = getArgumentCount() == 1 ? args[0] : args[1];

        final Properties outputProperties = new Properties(SERIALIZATION_PROPERTIES);
        final boolean jsonFormat = isCalledAs("send");
        if (jsonFormat) {
            outputProperties.setProperty(OutputKeys.METHOD, "json");
        }

        final StringBuilder out = new StringBuilder();
        printItems(out, outputProperties, jsonFormat, params);
        final String msg = out.toString();

        if (isCalledAs("send")) {
            WebSocketModule.send(channel, msg);
        } else {
            if (parent == null) {
                WebSocketModule.log(channel, msg);
            } else {
                WebSocketModule.log(channel,
                        parent.getSource().pathOrContentOrShortIdentifier(),
                        parent.getLine(), parent.getColumn(), msg);
            }
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private void printItems(final StringBuilder out, final Properties outputProperties,
            final boolean jsonFormat, final Sequence sequence) throws XPathException {
        for (final SequenceIterator i = sequence.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                final Serializer serializer = context.getBroker().getSerializer();
                serializer.reset();
                try {
                    serializer.setProperties(outputProperties);
                    out.append(serializer.serialize((NodeValue) item));
                } catch (final SAXException e) {
                    out.append(e.getMessage());
                }
            } else if (item.getType() == Type.MAP_ITEM || item.getType() == Type.ARRAY_ITEM) {
                final StringWriter writer = new StringWriter();
                final XQuerySerializer xqSerializer =
                        new XQuerySerializer(context.getBroker(), JSON_SERIALIZATION_PROPERTIES, writer);
                try {
                    xqSerializer.serialize(item.toSequence());
                    out.append(writer.toString());
                } catch (final SAXException e) {
                    throw new XPathException(this, e.getMessage());
                }
            } else if (jsonFormat) {
                out.append('"').append(item.getStringValue()).append('"');
            } else {
                out.append(item.getStringValue());
            }
        }
    }
}
