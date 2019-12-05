/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.fn;

import com.fasterxml.jackson.core.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Stack;

/**
 * @author <a href="mailto:from-github-existdb@agh2342.de">Adrian Hamm</a>
 */
public class FunXmlToJson extends BasicFunction {

    private final static Logger logger = LogManager.getLogger();

    public final static FunctionSignature[] signature = {
            new FunctionSignature(
                    new QName("xml-to-json", Function.BUILTIN_FUNCTION_NS),
                    "Converts an XML tree (in w3c 'XML Representation of JSON' format) into a string conforming to the JSON grammar. Basic string (un)escaping.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node", Type.NODE,
                                    Cardinality.ZERO_OR_ONE, "The input node")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                            "The JSON representation of the input node")
            ),
            new FunctionSignature(
                    new QName("xml-to-json", Function.BUILTIN_FUNCTION_NS),
                    "Converts an XML tree (in w3c 'XML Representation of JSON' format) into a string conforming to the JSON grammar. Basic string (un)escaping. Options are ignored.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("node", Type.NODE,
                                    Cardinality.ZERO_OR_ONE, "The input node"),
                            new FunctionParameterSequenceType("options", Type.MAP,
                                    Cardinality.ONE, "The options map")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE,
                            "The JSON representation of the input node")
            )
    };

    public FunXmlToJson(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        final Sequence seq = (getArgumentCount() > 0) ? args[0] : Sequence.EMPTY_SEQUENCE;
        //TODO: implement handling of options
        final MapType options = (getArgumentCount() > 1) ? (MapType) args[1].itemAt(0) : new MapType(context);

        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            result = new ValueSequence();
            final Item item = seq.itemAt(0);
            if (item.getType() != Type.DOCUMENT && item.getType() != Type.ELEMENT) {
                throw new XPathException(ErrorCodes.FOJS0006, "Invalid XML representation of JSON.");
            }
            final NodeValue nodeValue = (NodeValue) item;
            final StringWriter stringWriter = new StringWriter();
            nodeValueToJson(nodeValue, stringWriter);
            final String jsonString = stringWriter.toString();
            result.add(new StringValue(jsonString));
        }
        return result;
    }

    /**
     * Generate a JSON representation of a NodeValue which conforms to
     * https://www.w3.org/TR/xpath-functions-31/schema-for-json.xsd
     * Traverse a NodeValue via XMLStreamReader and fill a Writer with its JSON representation
     * by calling com.fasterxml.jackson write functions according to input type.
     * <p>
     * Implements basic part of the specification. String (un)escaping is fully delegated to jackson
     * and NOT fully conforming to spec.
     *
     * @param nodeValue the NodeValue to be read
     * @param writer    the Writer to be used
     * @throws XPathException on error in XML JSON input according to specification
     */
    private void nodeValueToJson(final NodeValue nodeValue, final Writer writer) throws XPathException {
        StringBuilder tempStringBuilder = new StringBuilder();
        JsonFactory jsonFactory = new JsonFactory();
        //use Stack<Object> to store String type keys and non-string type separators
        final Integer stackSeperator = 0;
        Stack<Object> mapkeyStack = new Stack<Object>();
        boolean elementKeyIsEscaped = false;
        boolean elementValueIsEscaped = false;
        try (
                final JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer);
        ) {
            final XMLStreamReader reader = context.getXMLStreamReader(nodeValue);
            int previous = XMLStreamReader.START_DOCUMENT;
            int status = XMLStreamReader.START_DOCUMENT;
            while (reader.hasNext()) {
                previous = status;
                status = reader.next();
                switch (status) {
                    case XMLStreamReader.START_ELEMENT:
                        tempStringBuilder.setLength(0);
                        String elementAttributeEscapedValue = reader.getAttributeValue(null, "escaped");
                        elementValueIsEscaped = "true".equals(elementAttributeEscapedValue);
                        String elementAttributeEscapedKeyValue = reader.getAttributeValue(null, "escaped-key");
                        elementKeyIsEscaped = "true".equals(elementAttributeEscapedKeyValue);
                        String elementKeyValue = "";
                        if (elementKeyIsEscaped) {
                            elementKeyValue = unescapeEscapedJsonString(reader.getAttributeValue(null, "key"));
                        } else {
                            elementKeyValue = reader.getAttributeValue(null, "key");
                        }
                        if (elementKeyValue != null && previous != XMLStreamReader.START_DOCUMENT) {
                            if (mapkeyStack.search(elementKeyValue) == -1 || (mapkeyStack.search(elementKeyValue) > mapkeyStack.search(stackSeperator))) {
                                //key not found or found beyond separator, add key, continue
                                mapkeyStack.push(elementKeyValue);
                                jsonGenerator.writeFieldName(elementKeyValue);
                            } else if (mapkeyStack.search(elementKeyValue) < mapkeyStack.search(stackSeperator)) {
                                //key found, before separator, error double key use in same map
                                logger.error("fn:xml-to-json(): FOJS0006: Invalid XML representation of JSON. Found map with double key use. Offending key in double quotes: \"" + elementKeyValue + "\"");
                                throw new XPathException(ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found map with double key use. Offending key in error logs.");
                            }
                        }
                        switch (reader.getLocalName()) {
                            case "array":
                                jsonGenerator.writeStartArray();
                                break;
                            case "map":
                                mapkeyStack.push(stackSeperator);
                                jsonGenerator.writeStartObject();
                                break;
                            default:
                                break;
                        }
                        break;
                    case XMLStreamReader.CHARACTERS:
                    case XMLStreamReader.CDATA:
                        tempStringBuilder.append(reader.getText());
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        String tempString = tempStringBuilder.toString();
                        boolean tempBoolean;
                        switch (reader.getLocalName()) {
                            case "array":
                                jsonGenerator.writeEndArray();
                                break;
                            case "boolean":
                                tempBoolean = !("".equals(tempString) || "0".equals(tempString) || "false".equals(tempString));
                                jsonGenerator.writeBoolean(tempBoolean);
                                break;
                            case "map":
                                for (int i = 0; i <= mapkeyStack.search(stackSeperator); i++) {
                                    mapkeyStack.pop();
                                }
                                jsonGenerator.writeEndObject();
                                break;
                            case "null":
                                if (tempStringBuilder.length() != 0) {
                                    throw new XPathException(ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found non-empty XML null element.");
                                }
                                jsonGenerator.writeNull();
                                break;
                            case "number":
                                double tempDouble = Double.parseDouble(tempString);
                                jsonGenerator.writeNumber(tempDouble);
                                break;
                            case "string":
                                if (elementValueIsEscaped == true) {
                                    //TODO: any unescaped occurrence of quotation mark, backspace, form-feed, newline, carriage return, tab, or solidus is replaced by \", \b, \f, \n, \r, \t, or \/ respectively;
                                    //TODO: any other codepoint in the range 1-31 or 127-159 is replaced by an escape in the form <backslash>uHHHH where HHHH is the upper-case hexadecimal representation of the codepoint value.
                                    jsonGenerator.writeString(unescapeEscapedJsonString(tempString));
                                } else {
                                    //TODO: any other codepoint in the range 1-31 or 127-159 is replaced by an escape in the form <backslash>uHHHH where HHHH is the upper-case hexadecimal representation of the codepoint value.
                                    jsonGenerator.writeString(tempString);
                                }
                                break;
                            default:
                                throw new XPathException(ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found XML element which is not one of [map, array, null, boolean, number, string].");
                        }
                    default:
                        break;
                }
            }
            reader.close();
        } catch (JsonGenerationException e) {
            throw new XPathException(ErrorCodes.FOJS0006, "Invalid XML representation of JSON.");
        } catch (XMLStreamException e) {
            throw new XPathException(ErrorCodes.FOER0000, "XMLStreamException", e);
        } catch (IOException e) {
            throw new XPathException(ErrorCodes.FOER0000, "IOException", e);
        }
    }

    /**
     * Generate an unescaped JSON string by parsing an escaped JSON string.
     *
     * @param escapedJsonString the escaped JSON string
     * @return the unescaped JSON string
     * @throws IOException    in case of an unhandled error reading the JSON
     * @throws XPathException in case of dynamic error
     */
    private String unescapeEscapedJsonString(final String escapedJsonString) throws IOException, XPathException {
        JsonFactory jsonFactory = new JsonFactory();
        StringBuilder unescapedJsonStringBuilder = new StringBuilder();
        String unescapedJsonString;
        try {
            JsonParser jsonParser = jsonFactory.createParser("\"" + escapedJsonString + "\"");
            while (!jsonParser.isClosed()) {
                JsonToken jsonToken = jsonParser.nextToken();
                if (jsonParser.hasTextCharacters()) {
                    unescapedJsonStringBuilder.append(jsonParser.getValueAsString());
                }
            }
        } catch (JsonParseException e) {
            logger.error("fn:xml-to-json(): FOJS0007: Bad JSON escape sequence. XML claims string is escaped. String does not parse as valid JSON string. Offending string in double quotes : \"" + escapedJsonString + "\"");
            throw new XPathException(ErrorCodes.FOJS0007, "Bad JSON escape sequence. XML claims string is escaped. String does not parse as valid JSON string. Offending string in error logs.");
        }
        unescapedJsonString = unescapedJsonStringBuilder.toString();
        return unescapedJsonString;
    }
}
