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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

/**
 * Functions related to JSON parsing.
 *
 * @author Wolf
 */
public class JSON extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_JSON_TEXT = optParam("json-text", Type.STRING, "JSON text as defined in [RFC 7159]. The function parses this string to return an XDM value");
    private static final FunctionParameterSequenceType FS_PARAM_HREF = optParam("href", Type.STRING,"URL pointing to a JSON resource");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = param("options", Type.MAP_ITEM, "Parsing options");

    private static final String FS_PARSE_JSON_NAME = "parse-json";
    static final FunctionSignature[] FS_PARSE_JSON = functionSignatures(
            FS_PARSE_JSON_NAME,
            "Parses a string supplied in the form of a JSON text, returning the results typically in the form of a map or array.",
            returnsOpt(Type.ITEM, "The parsed data, typically a map, array or atomic value"),
            arities(
                arity(
                        FS_PARAM_JSON_TEXT
                ),
                arity(
                        FS_PARAM_JSON_TEXT,
                        param("options", Type.MAP_ITEM, "Parsing options")
                )
            )
    );

    private static final String FS_JSON_DOC_NAME = "json-doc";
    static final FunctionSignature[] FS_JSON_DOC = functionSignatures(
            FS_JSON_DOC_NAME,
            "Reads an external (or database) resource containing JSON, and returns the results of parsing the resource as JSON. An URL parameter " +
            "without scheme or scheme 'xmldb:' is considered to point to a database resource.",
            returnsOpt(Type.ITEM, "The parsed data, typically a map, array or atomic value"),
            arities(
                    arity(
                            FS_PARAM_HREF
                    ),
                    arity(
                            FS_PARAM_HREF,
                            FS_PARAM_OPTIONS
                    )
            )
    );

    private static final String FS_JSON_TO_XML_NAME = "json-to-xml";
    static final FunctionSignature[] FS_JSON_TO_XML = functionSignatures(
            FS_JSON_TO_XML_NAME,
            "Parses a string supplied in the form of a JSON text, returning the results in the form of an XML document node.",
            returnsOpt(Type.DOCUMENT, "The parsed data as XML"),
            arities(
                    arity(
                            FS_PARAM_JSON_TEXT
                    ),
                    arity(
                            FS_PARAM_JSON_TEXT,
                            FS_PARAM_OPTIONS
                    )
            )
    );

    public static final String OPTION_DUPLICATES = "duplicates";
    public static final String OPTION_DUPLICATES_REJECT = "reject";
    public static final String OPTION_DUPLICATES_USE_FIRST = "use-first";
    public static final String OPTION_DUPLICATES_USE_LAST = "use-last";
    public static final String OPTION_LIBERAL = "liberal";
    public static final String OPTION_UNESCAPE = "unescape";
    public static final QName KEY = new QName("key",null);

    public JSON(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getXQueryVersion() < 31) {
            throw new XPathException(this, ErrorCodes.EXXQDY0004, "json functions only available in XQuery 3.1, but version declaration states " +
                    context.getXQueryVersion());
        }
        // process options if present
        // TODO: jackson does not allow access to raw string, so option "unescape" is not supported
        boolean liberal = false;
        String handleDuplicates = OPTION_DUPLICATES_USE_LAST;
        if (getArgumentCount() == 2) {
            final MapType options = (MapType)args[1].itemAt(0);
            final Sequence liberalOpt = options.get(new StringValue(OPTION_LIBERAL));
            if (liberalOpt.hasOne()) {
                liberal = liberalOpt.itemAt(0).convertTo(Type.BOOLEAN).effectiveBooleanValue();
            }
            final Sequence duplicateOpt = options.get(new StringValue(OPTION_DUPLICATES));
            if (duplicateOpt.hasOne()) {
                handleDuplicates = duplicateOpt.itemAt(0).getStringValue();
            }
        }

        JsonFactory factory = createJsonFactory(liberal);

        if (isCalledAs(FS_PARSE_JSON_NAME)) {
            return parse(args[0], handleDuplicates, factory);
        }  else if (isCalledAs(FS_JSON_TO_XML_NAME)) {
            return toxml(args[0], handleDuplicates, factory);
        } else {
            return parseResource(args[0], handleDuplicates, factory);
        }
    }

    /**
     *  Create and initialize JSON factory.
     *
     * @param liberal Set TRUE to allow non standard JSON features.
     *
     * @return JSON factory
     */
    public static JsonFactory createJsonFactory(boolean liberal) {
        JsonFactory factory = new JsonFactory();
        factory.configure(JsonParser.Feature.ALLOW_NON_NUMERIC_NUMBERS, true);

        // duplicates are handled in readValue
        factory.configure(JsonParser.Feature.STRICT_DUPLICATE_DETECTION, false);
        if (liberal) {
            factory.configure(JsonParser.Feature.ALLOW_COMMENTS, true);
            factory.configure(JsonParser.Feature.ALLOW_SINGLE_QUOTES, true);
            factory.configure(JsonParser.Feature.ALLOW_NUMERIC_LEADING_ZEROS, true);
            factory.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
            factory.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, true);
        }
        return factory;
    }

    private Sequence parse(Sequence json, String handleDuplicates, JsonFactory factory) throws XPathException {
        if (json.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        try (final JsonParser parser = factory.createParser(json.itemAt(0).getStringValue())) {
            final Item result = readValue(context, parser, handleDuplicates);
            return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        }
    }
    private Sequence toxml(Sequence json, String handleDuplicates, JsonFactory factory) throws XPathException {
        if (json.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        try (final JsonParser parser = factory.createParser(json.itemAt(0).getStringValue())) {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            factory.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, false);
            jsonToXml(builder, parser);
            return builder.getDocument() == null ? Sequence.EMPTY_SEQUENCE : builder.getDocument();
        }  catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        } finally {
            context.popDocumentContext();
        }
    }


    private Sequence parseResource(Sequence href, String handleDuplicates, JsonFactory factory) throws XPathException {
        if (href.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        try {
            String url = href.getStringValue();
            if (url.indexOf(':') == Constants.STRING_NOT_FOUND) {
                url = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + url;
            }
            final Source source = SourceFactory.getSource(context.getBroker(), "", url, false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "failed to load json doc from URI " + url);
            }
            try (final InputStream is = source.getInputStream();
                 final JsonParser parser = factory.createParser(is)) {

                final Item result = readValue(context, parser, handleDuplicates);
                return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
            }
        } catch (IOException | PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    /**
     * Generate an XDM from the tokens delivered by the JSON parser.
     *
     * @param context the XQueryContext
     * @param parser parser to use
     * @param handleDuplicates string indicating how to handle duplicate property names
     * @return the top item read
     * @throws IOException in case of an error reading the JSON
     * @throws XPathException in case of dynamic error
     */
    public static Item readValue(XQueryContext context, JsonParser parser, String handleDuplicates) throws IOException, XPathException {
        return readValue(context, parser, null, handleDuplicates);
    }

    private static Item readValue(XQueryContext context, JsonParser parser, Item parent, String handleDuplicates) throws IOException, XPathException {
        JsonToken token;
        Item next = null;
        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                return parent;
            }
            switch (token) {
                case START_OBJECT:
                    next = new MapType(null, context, null);
                    readValue(context, parser, next, handleDuplicates);
                    break;
                case START_ARRAY:
                    next = new ArrayType(null, context, Sequence.EMPTY_SEQUENCE);
                    readValue(context, parser, next, handleDuplicates);
                    break;
                case VALUE_FALSE:
                    next = BooleanValue.FALSE;
                    break;
                case VALUE_TRUE:
                    next = BooleanValue.TRUE;
                    break;
                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:
                    // according to spec, all numbers are converted to double
                    next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                    break;
                case VALUE_NULL:
                    next = null;
                    break;
                default:
                    next = new StringValue(parser.getText());
                    break;
            }
            if (parent != null) {
                switch (parent.getType()) {
                    case Type.ARRAY_ITEM:
                        ((ArrayType)parent).add(next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                        break;
                    case Type.MAP_ITEM:
                        final String currentName = parser.getCurrentName();
                        if (currentName == null) {
                            throw new XPathException(next, ErrorCodes.FOJS0001, "Invalid JSON object");
                        }
                        final StringValue name = new StringValue(currentName);
                        final MapType map = (MapType) parent;
                        if (map.contains(name)) {
                            // handle duplicate keys
                            if (handleDuplicates.equals(OPTION_DUPLICATES_REJECT)) {
                                throw new XPathException(map.getExpression(), ErrorCodes.FOJS0003, "Duplicate key: " + currentName);
                            }
                            if (handleDuplicates.equals(OPTION_DUPLICATES_USE_LAST)) {
                                map.add(name, next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                            }
                        } else {
                            map.add(name, next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence());
                        }
                        break;
                }
            }
        }
        return next;
    }

    /**
     * Generate an XML from the tokens delivered by the JSON parser.
     *
     * @param builder the memtree builder
     * @param parser parser to use
     *
     * @throws IOException if an I/O error occurs
     */
    public static void jsonToXml(MemTreeBuilder builder, JsonParser parser) throws IOException {
        JsonToken token;

        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                builder.endElement();
            }
            switch (token) {
                case START_OBJECT:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"map","map",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    break;
                case START_ARRAY:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"array","array",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    break;
                case VALUE_FALSE:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"boolean","boolean",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    builder.characters(Boolean.toString(false));
                    builder.endElement();
                    break;
                case VALUE_TRUE:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"boolean","boolean",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    builder.characters(Boolean.toString(true));
                    builder.endElement();
                    break;
                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"number","number",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    builder.characters(parser.getText());
                    builder.endElement();

                    break;
                case VALUE_NULL:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"null","null",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    builder.endElement();

                    break;
                case VALUE_STRING:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS,"string","string",null );
                    if(parser.getCurrentName() != null){
                        builder.addAttribute(KEY, parser.getCurrentName());
                    }
                    builder.characters(parser.getText());
                    builder.endElement();

                    break;
                default:
                    break;
            }
        }
    }
}
