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
import java.math.BigDecimal;
import java.util.ArrayList;

import static org.exist.xquery.FunctionDSL.*;

/**
 * @author <a href="mailto:from-github-existdb@agh2342.de">Adrian Hamm</a>
 */
public class FunXmlToJson extends BasicFunction {

    private static final Logger logger = LogManager.getLogger();

    private static final String FS_XML_TO_JSON_NAME = "xml-to-json";
    private static final FunctionParameterSequenceType FS_XML_TO_JSON_OPT_PARAM_NODE = optParam("node", Type.NODE, "The input node");
    private static final FunctionParameterSequenceType FS_XML_TO_JSON_OPT_PARAM_OPTIONS = param("options", Type.MAP_ITEM, "The options map");
    static final FunctionSignature[] FS_XML_TO_JSON = functionSignatures(
            new QName(FS_XML_TO_JSON_NAME, Function.BUILTIN_FUNCTION_NS),
            "Converts an XML tree (in w3c 'XML Representation of JSON' format) into a string conforming to the JSON grammar. Basic string (un)escaping.",
            returnsOpt(Type.STRING, "The JSON representation of the input node"),
            arities(
                    arity(FS_XML_TO_JSON_OPT_PARAM_NODE),
                    arity(FS_XML_TO_JSON_OPT_PARAM_NODE, FS_XML_TO_JSON_OPT_PARAM_OPTIONS)
            )
    );

    public FunXmlToJson(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence result;
        final Sequence seq = (getArgumentCount() > 0) ? args[0] : Sequence.EMPTY_SEQUENCE;
        //TODO: implement handling of options
        final MapType options = (getArgumentCount() == 2) ? (MapType) args[1].itemAt(0) : new MapType(this, context);

        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE;
        } else {
            result = new ValueSequence();
            final Item item = seq.itemAt(0);
            if (item.getType() != Type.DOCUMENT && item.getType() != Type.ELEMENT) {
                throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON.");
            }
            final NodeValue nodeValue = (NodeValue) item;
            final StringWriter stringWriter = new StringWriter();
            nodeValueToJson(nodeValue, stringWriter);
            final String jsonString = stringWriter.toString();
            result.add(new StringValue(this, jsonString));
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
        final StringBuilder tempStringBuilder = new StringBuilder();
        final JsonFactory jsonFactory = new JsonFactory();
        final Integer stackSeparator = 0;
        //use ArrayList<Object> to store String type keys and non-string type separators
        final ArrayList<Object> mapkeyArrayList = new ArrayList<>();
        boolean elementKeyIsEscaped = false;
        boolean elementValueIsEscaped = false;
        XMLStreamReader reader = null;
        try (
                final JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer)
        ) {
            reader = context.getXMLStreamReader(nodeValue);
            int previous = XMLStreamReader.START_DOCUMENT;
            int status = XMLStreamReader.START_DOCUMENT;
            while (reader.hasNext()) {
                previous = status;
                status = reader.next();
                switch (status) {
                    case XMLStreamReader.START_ELEMENT:
                        tempStringBuilder.setLength(0);
                        final String elementAttributeEscapedValue = reader.getAttributeValue(null, "escaped");
                        elementValueIsEscaped = "true".equals(elementAttributeEscapedValue);
                        final String elementAttributeEscapedKeyValue = reader.getAttributeValue(null, "escaped-key");
                        elementKeyIsEscaped = "true".equals(elementAttributeEscapedKeyValue);
                        final String elementKeyValue;
                        if (elementKeyIsEscaped) {
                            elementKeyValue = unescapeEscapedJsonString(reader.getAttributeValue(null, "key"));
                        } else {
                            elementKeyValue = reader.getAttributeValue(null, "key");
                        }
                        if (elementKeyValue != null && previous != XMLStreamReader.START_DOCUMENT) {
                            if (mapkeyArrayList.lastIndexOf(elementKeyValue) == -1 || (mapkeyArrayList.lastIndexOf(elementKeyValue) < mapkeyArrayList.lastIndexOf(stackSeparator))) {
                                //key not found or found beyond separator, add key, continue
                                mapkeyArrayList.add(elementKeyValue);
                                jsonGenerator.writeFieldName(elementKeyValue);
                            } else if (mapkeyArrayList.lastIndexOf(elementKeyValue) > mapkeyArrayList.lastIndexOf(stackSeparator)) {
                                //key found, before separator, error double key use in same map
                                logger.error("fn:xml-to-json(): FOJS0006: Invalid XML representation of JSON. Found map with double key use. Offending key in double quotes: \"{}\"", elementKeyValue);
                                throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found map with double key use. Offending key in error logs.");
                            }
                        }
                        switch (reader.getLocalName()) {
                            case "array":
                                jsonGenerator.writeStartArray();
                                break;
                            case "map":
                                mapkeyArrayList.add(stackSeparator);
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
                        final String tempString = tempStringBuilder.toString();
                        switch (reader.getLocalName()) {
                            case "array":
                                jsonGenerator.writeEndArray();
                                break;
                            case "boolean":
                                final boolean tempBoolean = !(tempString.isEmpty() || "0".equals(tempString) || "false".equals(tempString));
                                jsonGenerator.writeBoolean(tempBoolean);
                                break;
                            case "map":
                                while (!mapkeyArrayList.isEmpty() && mapkeyArrayList.removeLast() != stackSeparator) {
                                }
                                jsonGenerator.writeEndObject();
                                break;
                            case "null":
                                if (!tempStringBuilder.isEmpty()) {
                                    throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found non-empty XML null element.");
                                }
                                jsonGenerator.writeNull();
                                break;
                            case "number":
                                try{
                                    final BigDecimal tempDouble = new BigDecimal(tempString);
                                    jsonGenerator.writeNumber(tempDouble);
                                } catch (NumberFormatException ex){
                                    throw new XPathException(this, ErrorCodes.FOJS0006, "Cannot convert '" + tempString + "' to a number.");
                                }
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
                                throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Found XML element which is not one of [map, array, null, boolean, number, string].");
                        }
                    default:
                        break;
                }
            }
        } catch (JsonGenerationException e) {
            throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON.");
        } catch (XMLStreamException | IOException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    throw new XPathException(this, ErrorCodes.FOER0000, "XMLStreamException", e);
                }
            }
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
        final JsonFactory jsonFactory = new JsonFactory();
        final StringBuilder unescapedJsonStringBuilder = new StringBuilder();
        final String unescapedJsonString;
        try {
            final JsonParser jsonParser = jsonFactory.createParser("\"" + escapedJsonString + "\"");
            while (!jsonParser.isClosed()) {
                jsonParser.nextToken();
                if (jsonParser.hasTextCharacters()) {
                    unescapedJsonStringBuilder.append(jsonParser.getValueAsString());
                }
            }
        } catch (JsonParseException e) {
            logger.error("fn:xml-to-json(): FOJS0007: Bad JSON escape sequence. XML claims string is escaped. String does not parse as valid JSON string. Offending string in double quotes : \"{}\"", escapedJsonString);
            throw new XPathException(this, ErrorCodes.FOJS0007, "Bad JSON escape sequence. XML claims string is escaped. String does not parse as valid JSON string. Offending string in error logs.");
        }
        unescapedJsonString = unescapedJsonStringBuilder.toString();
        return unescapedJsonString;
    }
}
