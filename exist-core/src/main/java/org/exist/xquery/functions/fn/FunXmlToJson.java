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
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.InMemoryXMLStreamReader;
import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Set;

import static org.exist.xquery.FunctionDSL.*;

/**
 * @author <a href="mailto:from-github-existdb@agh2342.de">Adrian Hamm</a>
 */
public class FunXmlToJson extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(FunXmlToJson.class);
    private static final Set<String> JSON_ELEMENT_NAMES = Set.of("map", "array", "null", "boolean", "number", "string");

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
        //TODO: implement handling of options. When wired up, parse args[1] into
        // a MapType here and pass through to nodeValueToJson / writeJsonElement.

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
        // If the input is an element node (not a document), use DOM-based conversion
        // to avoid XMLStreamReader traversing the entire owner document
        if (nodeValue.getType() == Type.ELEMENT) {
            elementToJson(nodeValue, writer);
            return;
        }

        documentToJson(nodeValue, writer);
    }

    private void documentToJson(final NodeValue nodeValue, final Writer writer) throws XPathException {
        // For document nodes, find the first child element and convert it
        final org.w3c.dom.Node docNode = nodeValue.getNode();
        org.w3c.dom.Node child = docNode.getFirstChild();
        while (child != null && child.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            child = child.getNextSibling();
        }
        if (child == null) {
            throw new XPathException(this, ErrorCodes.FOJS0006, "Invalid XML representation of JSON. Document has no element child.");
        }
        elementToJson((NodeValue) child, writer);
    }

    private void elementToJson(final NodeValue nodeValue, final Writer writer) throws XPathException {
        final org.w3c.dom.Element element = (org.w3c.dom.Element) nodeValue.getNode();
        final JsonFactory jsonFactory = new JsonFactory();
        try (final JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer)) {
            writeJsonElement(element, jsonGenerator);
        } catch (final IOException e) {
            throw new XPathException(this, ErrorCodes.FOER0000, e.getMessage(), e);
        }
    }

    private void writeJsonElement(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        final String localName = element.getLocalName() != null ? element.getLocalName() : element.getTagName();
        final String nsUri = element.getNamespaceURI();

        if (!Namespaces.XPATH_FUNCTIONS_NS.equals(nsUri)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Element '" + localName
                    + "' is not in the required namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'.");
        }

        if (!JSON_ELEMENT_NAMES.contains(localName)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Found XML element which is not one of [map, array, null, boolean, number, string].");
        }

        validateDomAttributes(element, localName);

        switch (localName) {
            case "map" -> writeJsonMap(element, gen);
            case "array" -> writeJsonArray(element, gen);
            case "string" -> writeJsonString(element, gen);
            case "number" -> writeJsonNumber(element, gen);
            case "boolean" -> writeJsonBoolean(element, gen);
            case "null" -> writeJsonNull(element, gen);
            default -> throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Found XML element which is not one of [map, array, null, boolean, number, string].");
        }
    }

    /**
     * Validate that the attributes on a JSON-representation element conform to
     * F&O 3.1 §17.4.2 (the schema for JSON, Appendix C.2). The only allowed
     * no-namespace attributes are {@code key} / {@code escaped-key} on any of
     * the six elements (meaningful only when child of {@code map}) and
     * {@code escaped} on any element (meaningful only on {@code string}, but
     * per W3C bug 29917 tolerated as a no-op elsewhere). Attributes in the
     * XPath-functions namespace are disallowed ({@code anyAttribute namespace="##other"}).
     * Other-namespace attributes are ignored. The {@code escaped} /
     * {@code escaped-key} values must be valid xs:boolean.
     */
    private void validateDomAttributes(final org.w3c.dom.Element element, final String localName) throws XPathException {
        final org.w3c.dom.NamedNodeMap attrs = element.getAttributes();
        if (attrs == null) {
            return;
        }
        for (int i = 0; i < attrs.getLength(); i++) {
            validateOneAttribute((org.w3c.dom.Attr) attrs.item(i), localName);
        }
    }

    /**
     * Validate a single attribute per F&O 3.1 §17.4.2 / Appendix C.2 schema.
     * Extracted from {@link #validateDomAttributes} so the per-attribute
     * branching does not multiply against the loop count in the parent's
     * PMD NPath complexity score.
     */
    private void validateOneAttribute(final org.w3c.dom.Attr attr, final String localName) throws XPathException {
        // Skip xmlns declarations — they live in the standard XML namespace.
        final String fullName = attr.getName();
        if (fullName != null && (fullName.equals("xmlns") || fullName.startsWith("xmlns:"))) {
            return;
        }
        final String attrName = attr.getLocalName() != null ? attr.getLocalName() : fullName;
        final String attrNs = attr.getNamespaceURI();
        if (Namespaces.XPATH_FUNCTIONS_NS.equals(attrNs)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Attribute '" + attrName
                    + "' must not be in the namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'.");
        }
        if (attrNs != null && !attrNs.isEmpty()) {
            // Other-namespace attributes are ignored per schema's anyAttribute namespace="##other".
            return;
        }
        validateNoNamespaceAttribute(attr, attrName, localName);
    }

    /**
     * Dispatch the no-namespace attribute name to its per-name validation.
     * The only allowed no-namespace attributes are {@code key}, {@code escaped-key},
     * and {@code escaped}; everything else is FOJS0006.
     */
    private void validateNoNamespaceAttribute(final org.w3c.dom.Attr attr, final String attrName,
                                              final String localName) throws XPathException {
        switch (attrName) {
            case "key" -> { /* always allowed; lexical form is xs:string */ }
            case "escaped-key", "escaped" -> requireValidXsBoolean(attr, attrName);
            default -> throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Attribute '" + attrName
                    + "' is not allowed on element '" + localName + "'.");
        }
    }

    private void requireValidXsBoolean(final org.w3c.dom.Attr attr, final String attrName) throws XPathException {
        if (!isValidXsBoolean(attr.getValue())) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Attribute '" + attrName
                    + "' must have a valid xs:boolean value, but got '" + attr.getValue() + "'.");
        }
    }

    /**
     * Reject non-whitespace text children of {@code map} and {@code array} per
     * F&O 3.1 §17.4.2 — only element children (and whitespace) are permitted
     * inside container elements.
     */
    private void validateContainerChildren(final org.w3c.dom.Element element, final String localName) throws XPathException {
        final org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final org.w3c.dom.Node child = children.item(i);
            final short kind = child.getNodeType();
            if (kind == org.w3c.dom.Node.TEXT_NODE || kind == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                final String text = child.getNodeValue();
                if (text != null && !isXmlWhitespace(text)) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Invalid XML representation of JSON. Element '" + localName
                            + "' must not have non-whitespace text content.");
                }
            }
        }
    }

    /**
     * Reject element children of leaf JSON elements ({@code string}, {@code number},
     * {@code boolean}, {@code null}) per F&O 3.1 §17.4.2.
     */
    private void validateNoElementChildren(final org.w3c.dom.Element element, final String localName) throws XPathException {
        final org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Invalid XML representation of JSON. Element '" + localName
                        + "' must not have element children.");
            }
        }
    }

    private void writeJsonMap(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateContainerChildren(element, "map");
        gen.writeStartObject();
        final org.w3c.dom.NodeList mapChildren = element.getChildNodes();
        final Set<String> seenKeys = new java.util.HashSet<>();
        for (int i = 0; i < mapChildren.getLength(); i++) {
            final org.w3c.dom.Node child = mapChildren.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                final org.w3c.dom.Element childElem = (org.w3c.dom.Element) child;
                final String keyValue = getKeyAttribute(childElem);
                if (keyValue == null) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Invalid XML representation of JSON. Map entry missing 'key' attribute.");
                }
                if (!seenKeys.add(keyValue)) {
                    throw new XPathException(this, ErrorCodes.FOJS0006,
                            "Invalid XML representation of JSON. Duplicate key '" + keyValue + "' in map.");
                }
                gen.writeFieldName(keyValue);
                writeJsonElement(childElem, gen);
            }
        }
        gen.writeEndObject();
    }

    private void writeJsonArray(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateContainerChildren(element, "array");
        gen.writeStartArray();
        final org.w3c.dom.NodeList arrayChildren = element.getChildNodes();
        for (int i = 0; i < arrayChildren.getLength(); i++) {
            final org.w3c.dom.Node child = arrayChildren.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                writeJsonElement((org.w3c.dom.Element) child, gen);
            }
        }
        gen.writeEndArray();
    }

    private void writeJsonString(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateNoElementChildren(element, "string");
        final String strContent = getTextContent(element);
        final boolean escaped = "true".equals(element.getAttribute("escaped"));
        if (escaped) {
            try {
                gen.writeString(unescapeEscapedJsonString(strContent));
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOJS0007, "Bad JSON escape sequence.");
            }
        } else {
            gen.writeString(strContent);
        }
    }

    private void writeJsonNumber(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateNoElementChildren(element, "number");
        final String numStr = getTextContent(element);
        try {
            gen.writeNumber(new BigDecimal(numStr));
        } catch (final NumberFormatException e) {
            throw new XPathException(this, ErrorCodes.FOJS0006, "Cannot convert '" + numStr + "' to a number.");
        }
    }

    private void writeJsonBoolean(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateNoElementChildren(element, "boolean");
        final String boolStr = getTextContent(element);
        final boolean boolVal = !("0".equals(boolStr) || "false".equals(boolStr) || boolStr.isEmpty());
        gen.writeBoolean(boolVal);
    }

    private void writeJsonNull(final org.w3c.dom.Element element, final JsonGenerator gen) throws XPathException, IOException {
        validateNoElementChildren(element, "null");
        final String nullContent = getTextContent(element);
        if (!nullContent.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Found non-empty XML null element.");
        }
        gen.writeNull();
    }

    private String getKeyAttribute(final org.w3c.dom.Element element) throws XPathException {
        final String escapedKey = element.getAttribute("escaped-key");
        // getAttribute returns "" for missing attributes, so check hasAttribute
        if (!element.hasAttribute("key")) {
            return null;
        }
        final String key = element.getAttribute("key");
        if ("true".equals(escapedKey)) {
            try {
                return unescapeEscapedJsonString(key);
            } catch (final IOException e) {
                throw new XPathException(this, ErrorCodes.FOJS0007, "Bad JSON escape sequence in key.");
            }
        }
        return key;
    }

    private String getTextContent(final org.w3c.dom.Element element) {
        final StringBuilder sb = new StringBuilder();
        final org.w3c.dom.NodeList children = element.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final org.w3c.dom.Node child = children.item(i);
            if (child.getNodeType() == org.w3c.dom.Node.TEXT_NODE
                    || child.getNodeType() == org.w3c.dom.Node.CDATA_SECTION_NODE) {
                sb.append(child.getTextContent());
            }
        }
        return sb.toString();
    }

    // Keep the old XMLStreamReader-based method for reference but it's no longer called
    @SuppressWarnings("unused")
    private void nodeValueToJsonViaStream(final NodeValue nodeValue, final Writer writer) throws XPathException {
        final StringBuilder tempStringBuilder = new StringBuilder();
        final JsonFactory jsonFactory = new JsonFactory();
        final Integer stackSeparator = 0;
        //use ArrayList<Object> to store String type keys and non-string type separators
        final ArrayList<Object> mapkeyArrayList = new ArrayList<>();
        //track parent element local names so we can validate child structure (F&O 3.1 §17.4.2 / §17.5.4)
        final Deque<String> elementStack = new ArrayDeque<>();
        boolean elementKeyIsEscaped = false;
        boolean elementValueIsEscaped = false;
        XMLStreamReader reader = null;
        try (
                final JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer)
        ) {
            reader = streamReaderFor(nodeValue);
            int previous = XMLStreamReader.START_DOCUMENT;
            int status = XMLStreamReader.START_DOCUMENT;
            while (reader.hasNext()) {
                previous = status;
                status = reader.next();
                switch (status) {
                    case XMLStreamReader.START_ELEMENT:
                        tempStringBuilder.setLength(0);
                        final String elementNamespaceURI = reader.getNamespaceURI();
                        if (!Namespaces.XPATH_FUNCTIONS_NS.equals(elementNamespaceURI)) {
                            throw new XPathException(this, ErrorCodes.FOJS0006,
                                    "Invalid XML representation of JSON. Element '" + reader.getLocalName()
                                    + "' is not in the required namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'.");
                        }
                        validateStartElement(reader, elementStack);
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
                            case "array" -> jsonGenerator.writeStartArray();
                            case "map" -> {
                                mapkeyArrayList.add(stackSeparator);
                                jsonGenerator.writeStartObject();
                            }
                            default -> { /* other valid JSON element kinds emit only at END_ELEMENT */ }
                        }
                        break;
                    case XMLStreamReader.CHARACTERS:
                    case XMLStreamReader.CDATA:
                        final String charText = reader.getText();
                        validateTextInContext(charText, elementStack.peek());
                        tempStringBuilder.append(charText);
                        break;
                    case XMLStreamReader.END_ELEMENT:
                        final String tempString = tempStringBuilder.toString();
                        if (!elementStack.isEmpty()) {
                            elementStack.pop();
                        }
                        switch (reader.getLocalName()) {
                            case "array":
                                jsonGenerator.writeEndArray();
                                break;
                            case "boolean":
                                final boolean tempBoolean = !(tempString.isEmpty() || "0".equals(tempString) || "false".equals(tempString));
                                jsonGenerator.writeBoolean(tempBoolean);
                                break;
                            case "map":
                                while (!mapkeyArrayList.isEmpty() && !stackSeparator.equals(mapkeyArrayList.removeLast())) {
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
                                if (elementValueIsEscaped) {
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
     * Construct a stream reader scoped to the input node's subtree.
     * For in-memory element nodes, XQueryContext.getXMLStreamReader walks from the
     * owner-document root, so when the input is an element nested inside a larger
     * document (e.g. selected by XPath from a host XML file), traversal visits
     * ancestor elements and the namespace check fires on them rather than on the
     * actual JSON wrapper element. Scoping the reader to the input element fixes that.
     */
    private XMLStreamReader streamReaderFor(final NodeValue nodeValue) throws IOException, XMLStreamException {
        if (nodeValue.getImplementationType() == NodeValue.IN_MEMORY_NODE
                && nodeValue.getType() == Type.ELEMENT) {
            final NodeImpl node = (NodeImpl) nodeValue;
            final DocumentImpl ownerDoc = node.getOwnerDocument();
            return new InMemoryXMLStreamReader(ownerDoc, node);
        }
        return context.getXMLStreamReader(nodeValue);
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

    /**
     * Validate the current START_ELEMENT against the F&O 3.1 §17.4.2 / §17.5.4 structural rules
     * and, on success, push the element's local name onto the parent-tracking stack.
     */
    private void validateStartElement(final XMLStreamReader reader, final Deque<String> elementStack) throws XPathException {
        final String localName = reader.getLocalName();
        if (!isJsonElementName(localName)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Element '" + localName
                    + "' is not one of [map, array, null, boolean, number, string].");
        }
        final String parentLocalName = elementStack.peek();
        if (parentLocalName != null && isLeafElementName(parentLocalName)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Element '" + parentLocalName
                    + "' must not have element children.");
        }
        validateAttributes(reader, localName);
        elementStack.push(localName);
    }

    /**
     * Reject non-whitespace text node children of {@code map} and {@code array} per F&O 3.1 §17.4.2.
     */
    private void validateTextInContext(final String text, final String parentLocalName) throws XPathException {
        if (parentLocalName == null) {
            return;
        }
        if (!"map".equals(parentLocalName) && !"array".equals(parentLocalName)) {
            return;
        }
        if (!isXmlWhitespace(text)) {
            throw new XPathException(this, ErrorCodes.FOJS0006,
                    "Invalid XML representation of JSON. Element '" + parentLocalName
                    + "' must not have non-whitespace text content.");
        }
    }

    private static boolean isJsonElementName(final String name) {
        return switch (name) {
            case "map", "array", "string", "number", "boolean", "null" -> true;
            default -> false;
        };
    }

    private static boolean isLeafElementName(final String name) {
        return switch (name) {
            case "string", "number", "boolean", "null" -> true;
            default -> false;
        };
    }

    private static boolean isXmlWhitespace(final String text) {
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return false;
            }
        }
        return true;
    }

    /**
     * Validate that the attributes on the current element conform to F&O 3.1 §17.4.2 (the schema for JSON).
     * <p>
     * Per the schema (Appendix C.2), the only allowed no-namespace attributes are:
     * <ul>
     *   <li>{@code key} and {@code escaped-key} on any of the six elements (when child of map; allowed at top-level too)</li>
     *   <li>{@code escaped} on {@code string} only</li>
     * </ul>
     * Attributes in the {@code http://www.w3.org/2005/xpath-functions} namespace are disallowed
     * ({@code anyAttribute namespace="##other"}); attributes in any other namespace are ignored.
     * The {@code escaped} and {@code escaped-key} attributes must hold a valid {@code xs:boolean} value.
     */
    private void validateAttributes(final XMLStreamReader reader, final String localName) throws XPathException {
        for (int i = 0; i < reader.getAttributeCount(); i++) {
            final String attrNs = reader.getAttributeNamespace(i);
            final String attrName = reader.getAttributeLocalName(i);
            if (Namespaces.XPATH_FUNCTIONS_NS.equals(attrNs)) {
                throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Invalid XML representation of JSON. Attribute '" + attrName
                        + "' must not be in the namespace '" + Namespaces.XPATH_FUNCTIONS_NS + "'.");
            }
            if (attrNs != null && !attrNs.isEmpty()) {
                continue;
            }
            switch (attrName) {
                case "key", "escaped-key" -> {
                    if ("escaped-key".equals(attrName) && !isValidXsBoolean(reader.getAttributeValue(i))) {
                        throw new XPathException(this, ErrorCodes.FOJS0006,
                                "Invalid XML representation of JSON. Attribute 'escaped-key' must have a valid xs:boolean value, but got '"
                                + reader.getAttributeValue(i) + "'.");
                    }
                }
                case "escaped" -> {
                    // Per W3C bug 29917 / qt3tests xml-to-json-065, 'escaped' is tolerated on
                    // non-string elements as a no-op; only the lexical value is enforced.
                    if (!isValidXsBoolean(reader.getAttributeValue(i))) {
                        throw new XPathException(this, ErrorCodes.FOJS0006,
                                "Invalid XML representation of JSON. Attribute 'escaped' must have a valid xs:boolean value, but got '"
                                + reader.getAttributeValue(i) + "'.");
                    }
                }
                default -> throw new XPathException(this, ErrorCodes.FOJS0006,
                        "Invalid XML representation of JSON. Attribute '" + attrName
                        + "' is not allowed on element '" + localName + "'.");
            }
        }
    }

    private static boolean isValidXsBoolean(final String value) {
        if (value == null) {
            return false;
        }
        final String trimmed = value.trim();
        return "true".equals(trimmed) || "false".equals(trimmed) || "1".equals(trimmed) || "0".equals(trimmed);
    }
}
