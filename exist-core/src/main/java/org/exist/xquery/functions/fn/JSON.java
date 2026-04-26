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
import org.exist.xquery.value.BooleanValue;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.newInputStream;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.fn.FnModule.functionSignatures;

/**
 * Functions related to JSON parsing: fn:parse-json, fn:json-doc, fn:json-to-xml.
 *
 * @author Wolf
 */
public class JSON extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_JSON_TEXT = optParam("value", Type.STRING, "JSON text as defined in [RFC 7159]. The function parses this string to return an XDM value");
    private static final FunctionParameterSequenceType FS_PARAM_HREF = optParam("source", Type.STRING,"URL pointing to a JSON resource");
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
                            optParam("options", Type.MAP_ITEM, "Parsing options")
                    )
            )
    );

    public static final String OPTION_DUPLICATES = "duplicates";
    public static final String OPTION_DUPLICATES_REJECT = "reject";
    public static final String OPTION_DUPLICATES_USE_FIRST = "use-first";
    public static final String OPTION_DUPLICATES_USE_LAST = "use-last";
    public static final String OPTION_DUPLICATES_RETAIN = "retain";
    public static final String OPTION_LIBERAL = "liberal";
    public static final String OPTION_ESCAPE = "escape";
    public static final String OPTION_FALLBACK = "fallback";
    public static final String OPTION_NUMBER_PARSER = "number-parser";
    public static final String OPTION_VALIDATE = "validate";
    public static final String OPTION_UNESCAPE = "unescape";
    public static final QName KEY = new QName("key", null);
    public static final QName ESCAPED = new QName("escaped", null);
    public static final QName ESCAPED_KEY = new QName("escaped-key", null);

    public JSON(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getXQueryVersion() < 31) {
            throw new XPathException(this, ErrorCodes.EXXQDY0004, "json functions only available in XQuery 3.1, but version declaration states " +
                    context.getXQueryVersion());
        }

        final boolean isJsonToXml = isCalledAs(FS_JSON_TO_XML_NAME);

        // process options if present
        // TODO: jackson does not allow access to raw string, so option "unescape" is not supported
        boolean liberal = false;
        String handleDuplicates = isJsonToXml ? OPTION_DUPLICATES_RETAIN : OPTION_DUPLICATES_USE_LAST;
        boolean escape = false;
        FunctionReference fallbackFn = null;
        FunctionReference numberParserFn = null;

        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            final Item optItem = args[1].itemAt(0);
            if (optItem.getType() != Type.MAP_ITEM) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Expected map for options parameter, got " + Type.getTypeName(optItem.getType()));
            }
            final MapType options = (MapType) optItem;

            // --- liberal option (xs:boolean) ---
            liberal = getBooleanOption(options, OPTION_LIBERAL, false);

            // --- escape option (xs:boolean) ---
            escape = getBooleanOption(options, OPTION_ESCAPE, false);

            // --- validate option (xs:boolean) ---
            // Accepted but not enforced (eXist has no schema validation for json-to-xml output)
            getBooleanOption(options, OPTION_VALIDATE, false);

            // --- duplicates option (xs:string) ---
            if (options.contains(new StringValue(OPTION_DUPLICATES))) {
                final Sequence dupOpt = options.get(new StringValue(OPTION_DUPLICATES));
                if (!dupOpt.hasOne()) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'duplicates' must be a single string value");
                }
                final Item dupItem = dupOpt.itemAt(0);
                if (!Type.subTypeOf(dupItem.getType(), Type.STRING)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'duplicates' must be a string, got " + Type.getTypeName(dupItem.getType()));
                }
                handleDuplicates = dupItem.getStringValue();
                if (!OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_USE_LAST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_REJECT.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_RETAIN.equals(handleDuplicates)) {
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "Invalid value for 'duplicates' option: " + handleDuplicates);
                }
                // use-last is not valid for json-to-xml (spec: "use-last" is not permitted)
                if (isJsonToXml && OPTION_DUPLICATES_USE_LAST.equals(handleDuplicates)) {
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "Option duplicates='use-last' is not permitted for fn:json-to-xml");
                }
            }

            // --- fallback option (function($s as xs:string) as xs:string) ---
            if (options.contains(new StringValue(OPTION_FALLBACK))) {
                final Sequence fbOpt = options.get(new StringValue(OPTION_FALLBACK));
                if (!fbOpt.hasOne()) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' must be a single function value");
                }
                final Item fbItem = fbOpt.itemAt(0);
                if (!(fbItem instanceof FunctionReference)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' must be a function, got " + Type.getTypeName(fbItem.getType()));
                }
                fallbackFn = (FunctionReference) fbItem;
                if (fallbackFn.getSignature().getArgumentCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' function must have arity 1, got " + fallbackFn.getSignature().getArgumentCount());
                }
            }

            // --- number-parser option (function($s as xs:string) as xs:anyAtomicType?) ---
            if (options.contains(new StringValue(OPTION_NUMBER_PARSER))) {
                final Sequence npOpt = options.get(new StringValue(OPTION_NUMBER_PARSER));
                if (!npOpt.hasOne()) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a single function value");
                }
                final Item npItem = npOpt.itemAt(0);
                if (!(npItem instanceof FunctionReference)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a function, got " + Type.getTypeName(npItem.getType()));
                }
                numberParserFn = (FunctionReference) npItem;
            }

            // XQuery 4.0: 'spec' option controls JSON spec version -- accepted but not enforced

            // escape=true combined with fallback is invalid (spec 22.3.2)
            if (escape && fallbackFn != null) {
                throw new XPathException(this, ErrorCodes.FOJS0005,
                        "Options 'escape' and 'fallback' cannot both be specified");
            }
        }

        JsonFactory factory = createJsonFactory(liberal);

        if (isCalledAs(FS_PARSE_JSON_NAME)) {
            return parse(args[0], handleDuplicates, factory, numberParserFn, fallbackFn);
        } else if (isJsonToXml) {
            return toxml(args[0], handleDuplicates, escape, fallbackFn, numberParserFn, factory);
        } else {
            return parseResource(args[0], handleDuplicates, factory);
        }
    }

    /**
     * Validate and extract a boolean option from the options map.
     * Throws XPTY0004 if the key is present but the value is not a single xs:boolean.
     */
    private boolean getBooleanOption(MapType options, String optionName, boolean defaultValue) throws XPathException {
        final StringValue optKey = new StringValue(optionName);
        if (!options.contains(optKey)) {
            return defaultValue;
        }
        final Sequence optVal = options.get(optKey);
        if (!optVal.hasOne()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + optionName + "' must be a single boolean value");
        }
        final Item item = optVal.itemAt(0);
        if (item.getType() != Type.BOOLEAN) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + optionName + "' must be a boolean, got " + Type.getTypeName(item.getType()));
        }
        return ((BooleanValue) item).effectiveBooleanValue();
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

    private Sequence parse(Sequence json, String handleDuplicates, JsonFactory factory,
                           FunctionReference numberParserFn, FunctionReference fallbackFn) throws XPathException {
        if (json.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String jsonStr = stripBOM(json.itemAt(0).getStringValue());
        if (jsonStr.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001, "JSON syntax error: empty string");
        }
        try (final JsonParser parser = factory.createParser(jsonStr)) {
            final Item result = readValue(context, parser, handleDuplicates, numberParserFn, fallbackFn);
            return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
        } catch (IOException e) {
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
        } catch (XPathException e) {
            e.setLocation(getLine(), getColumn(), getSource());
            throw e;
        }
    }

    private Sequence toxml(Sequence json, String handleDuplicates, boolean escape,
                           FunctionReference fallbackFn, FunctionReference numberParserFn,
                           JsonFactory factory) throws XPathException {
        if (json.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String jsonStr = stripBOM(json.itemAt(0).getStringValue());
        if (jsonStr.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001, "JSON syntax error: empty string");
        }
        try (final JsonParser parser = factory.createParser(jsonStr)) {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            jsonToXml(builder, parser, escape, handleDuplicates, fallbackFn, numberParserFn, context);
            return builder.getDocument() == null ? Sequence.EMPTY_SEQUENCE : builder.getDocument();
        } catch (IOException e) {
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
            boolean resolvedFromBaseUri = false;
            if (url.indexOf(':') == Constants.STRING_NOT_FOUND) {
                // Relative URI: resolve against static base URI
                final String resolved = DocUtils.resolveAgainstBaseUri(context, url);
                if (resolved != null && resolved.startsWith("file:")) {
                    url = resolved;
                    resolvedFromBaseUri = true;
                } else {
                    url = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + url;
                }
            }
            // Only use direct file: access for URIs resolved from a relative path.
            // Absolute file: URIs go through SourceFactory for security.
            if (resolvedFromBaseUri && url.startsWith("file:")) {
                // Extract path from file: URI: file:/path, file://host/path, file:///path
                final String filePath = url.replaceFirst("^file:(?://[^/]*)?", "");
                final Path path = Path.of(filePath);
                if (isReadable(path)) {
                    try (final InputStream is = newInputStream(path)) {
                        try (final JsonParser parser = factory.createParser(is)) {
                            final Item result = readValue(context, parser, handleDuplicates);
                            return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
                        } catch (final IOException jsonErr) {
                            // JSON parsing error, not file I/O
                            throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                        }
                    }
                }
                throw new XPathException(this, ErrorCodes.FOUT1170, "failed to load json doc from file: " + filePath);
            }

            // Check dynamically available text resources first (e.g., XQTS test resources)
            try (final Reader dynamicTextResource = context.getDynamicallyAvailableTextResource(url, StandardCharsets.UTF_8)) {
                if (dynamicTextResource != null) {
                    final StringBuilder sb = new StringBuilder();
                    final char[] buf = new char[4096];
                    int read;
                    while ((read = dynamicTextResource.read(buf)) != -1) {
                        sb.append(buf, 0, read);
                    }
                    try (final JsonParser parser = factory.createParser(sb.toString())) {
                        final Item result = readValue(context, parser, handleDuplicates);
                        return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
                    } catch (final IOException jsonErr) {
                        throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                    }
                }
            }

            final Source source = SourceFactory.getSource(context.getBroker(), "", url, false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FOUT1170, "failed to load json doc from URI " + url);
            }
            try (final InputStream is = source.getInputStream()) {
                try (final JsonParser parser = factory.createParser(is)) {
                    final Item result = readValue(context, parser, handleDuplicates);
                    return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
                } catch (final IOException jsonErr) {
                    throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                }
            }
        } catch (IOException | PermissionDeniedException e) {
            throw new XPathException(this, ErrorCodes.FOUT1170, e.getMessage());
        }
    }

    private String resolveAgainstBaseUri(final String relativePath) {
        try {
            final AnyURIValue baseXdmUri = context.getBaseURI();
            if (baseXdmUri != null && !baseXdmUri.equals(AnyURIValue.EMPTY_URI)) {
                String baseStr = baseXdmUri.toURI().toString();
                // Strip filename to get directory URI
                final int lastSlash = baseStr.lastIndexOf('/');
                if (lastSlash >= 0) {
                    baseStr = baseStr.substring(0, lastSlash + 1);
                }
                final java.net.URI baseUri = new java.net.URI(baseStr);
                return baseUri.resolve(relativePath).toString();
            }
        } catch (final java.net.URISyntaxException | XPathException e) {
            // fall through
        }
        return null;
    }

    // =================================================================
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
        return readValue(context, parser, null, handleDuplicates, null, null);
    }

    public static Item readValue(XQueryContext context, JsonParser parser, String handleDuplicates,
                                  FunctionReference numberParserFn, FunctionReference fallbackFn) throws IOException, XPathException {
        return readValue(context, parser, null, handleDuplicates, numberParserFn, fallbackFn);
    }

    private static Item readValue(XQueryContext context, JsonParser parser, Item parent, String handleDuplicates,
                                   FunctionReference numberParserFn, FunctionReference fallbackFn) throws IOException, XPathException {
        JsonToken token;
        Item next = null;
        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                return parent;
            }
            switch (token) {
                case START_OBJECT:
                    next = new MapType(null, context, null);
                    readValue(context, parser, next, handleDuplicates, numberParserFn, fallbackFn);
                    break;
                case START_ARRAY:
                    next = new ArrayType(null, context, Sequence.EMPTY_SEQUENCE);
                    readValue(context, parser, next, handleDuplicates, numberParserFn, fallbackFn);
                    break;
                case VALUE_FALSE:
                    next = BooleanValue.FALSE;
                    break;
                case VALUE_TRUE:
                    next = BooleanValue.TRUE;
                    break;
                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:
                    if (numberParserFn != null) {
                        final Sequence numResult = numberParserFn.evalFunction(null, null,
                                new Sequence[]{new StringValue(parser.getText())});
                        if (numResult.isEmpty()) {
                            next = null;
                        } else if (numResult.getItemCount() > 1) {
                            throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                                    "number-parser function must return zero or one item, got " + numResult.getItemCount());
                        } else {
                            next = numResult.itemAt(0);
                        }
                    } else if (token == JsonToken.VALUE_NUMBER_INT) {
                        // XQuery 4.0: JSON integers -> xs:integer (was xs:double in 3.1)
                        try {
                            next = new IntegerValue(parser.getLongValue());
                        } catch (final Exception e) {
                            // Fallback to double for very large integers
                            next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                        }
                    } else {
                        // JSON fractional numbers -> xs:double
                        next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                    }
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

    // ========================================================================
    // fn:json-to-xml -- recursive XML builder with full options support
    // ========================================================================

    /**
     * Generate XML from JSON tokens with full options support.
     */
    public static void jsonToXml(MemTreeBuilder builder, JsonParser parser,
                                  boolean escape, String duplicates,
                                  FunctionReference fallbackFn, FunctionReference numberParserFn,
                                  XQueryContext context) throws IOException, XPathException {
        final JsonToken token = parser.nextValue();
        if (token == null) {
            return;
        }
        writeJsonValueAsXml(builder, parser, token, null, escape, duplicates, fallbackFn, numberParserFn, context);
    }

    /**
     * Legacy overload for backward compatibility.
     */
    public static void jsonToXml(MemTreeBuilder builder, JsonParser parser) throws IOException {
        try {
            jsonToXml(builder, parser, false, OPTION_DUPLICATES_RETAIN, null, null, null);
        } catch (XPathException e) {
            throw new IOException("XPath error during JSON-to-XML conversion", e);
        }
    }

    private static void writeJsonValueAsXml(MemTreeBuilder builder, JsonParser parser,
                                             JsonToken token, String key,
                                             boolean escape, String duplicates,
                                             FunctionReference fallbackFn, FunctionReference numberParserFn,
                                             XQueryContext context) throws IOException, XPathException {
        switch (token) {
            case START_OBJECT:
                writeMapAsXml(builder, parser, key, escape, duplicates, fallbackFn, numberParserFn, context);
                break;
            case START_ARRAY:
                writeArrayAsXml(builder, parser, key, escape, duplicates, fallbackFn, numberParserFn, context);
                break;
            case VALUE_STRING:
                writeStringAsXml(builder, parser, key, escape, fallbackFn, context);
                break;
            case VALUE_NUMBER_FLOAT:
            case VALUE_NUMBER_INT:
                writeNumberAsXml(builder, parser, key, escape, fallbackFn, numberParserFn, context);
                break;
            case VALUE_TRUE:
                writeBooleanAsXml(builder, key, "true", escape, fallbackFn, context);
                break;
            case VALUE_FALSE:
                writeBooleanAsXml(builder, key, "false", escape, fallbackFn, context);
                break;
            case VALUE_NULL:
                writeNullAsXml(builder, key, escape, fallbackFn, context);
                break;
            default:
                break;
        }
    }

    private static void writeMapAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                       boolean escape, String duplicates,
                                       FunctionReference fallbackFn, FunctionReference numberParserFn,
                                       XQueryContext context) throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "map", "map", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);

        final Set<String> seenKeys = OPTION_DUPLICATES_RETAIN.equals(duplicates) ? null : new HashSet<>();
        JsonToken token;
        while ((token = parser.nextValue()) != JsonToken.END_OBJECT) {
            if (token == null) {
                break;
            }
            final String childKey = parser.getCurrentName();

            if (seenKeys != null && childKey != null) {
                final boolean isDuplicate = !seenKeys.add(childKey);
                if (isDuplicate) {
                    if (OPTION_DUPLICATES_REJECT.equals(duplicates)) {
                        throw new XPathException((Expression) null, ErrorCodes.FOJS0003, "Duplicate key: " + childKey);
                    } else if (OPTION_DUPLICATES_USE_FIRST.equals(duplicates)) {
                        skipJsonValue(parser, token);
                        continue;
                    }
                }
            }

            writeJsonValueAsXml(builder, parser, token, childKey, escape, duplicates, fallbackFn, numberParserFn, context);
        }
        builder.endElement();
    }

    private static void writeArrayAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                         boolean escape, String duplicates,
                                         FunctionReference fallbackFn, FunctionReference numberParserFn,
                                         XQueryContext context) throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "array", "array", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);

        JsonToken token;
        while ((token = parser.nextValue()) != JsonToken.END_ARRAY) {
            if (token == null) {
                break;
            }
            writeJsonValueAsXml(builder, parser, token, null, escape, duplicates, fallbackFn, numberParserFn, context);
        }
        builder.endElement();
    }

    private static void writeStringAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                          boolean escape, FunctionReference fallbackFn,
                                          XQueryContext context) throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "string", "string", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);

        final String text = parser.getText();
        if (escape) {
            final boolean[] hasEscapes = {false};
            final String escaped = escapeJsonStringForXml(text, hasEscapes);
            if (hasEscapes[0]) {
                builder.addAttribute(ESCAPED, "true");
            }
            builder.characters(escaped);
        } else {
            final String processed = replaceNonXmlChars(text, fallbackFn, context);
            builder.characters(processed);
        }
        builder.endElement();
    }

    private static void writeNumberAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                          boolean escape, FunctionReference fallbackFn,
                                          FunctionReference numberParserFn,
                                          XQueryContext context) throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "number", "number", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);

        if (numberParserFn != null) {
            final Sequence numResult = numberParserFn.evalFunction(null, null,
                    new Sequence[]{new StringValue(parser.getText())});
            if (numResult.isEmpty()) {
                // empty number element
            } else if (numResult.getItemCount() > 1) {
                throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                        "number-parser function must return zero or one item, got " + numResult.getItemCount());
            } else {
                final Item resultItem = numResult.itemAt(0);
                // getStringValue will throw FOTY0013 for function items
                final String resultStr = resultItem.getStringValue();
                builder.characters(resultStr);
            }
        } else {
            builder.characters(parser.getText());
        }
        builder.endElement();
    }

    private static void writeBooleanAsXml(MemTreeBuilder builder, String key, String value,
                                           boolean escape, FunctionReference fallbackFn,
                                           XQueryContext context) throws XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "boolean", "boolean", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);
        builder.characters(value);
        builder.endElement();
    }

    private static void writeNullAsXml(MemTreeBuilder builder, String key,
                                        boolean escape, FunctionReference fallbackFn,
                                        XQueryContext context) throws XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "null", "null", null);
        writeKeyAttribute(builder, key, escape, fallbackFn, context);
        builder.endElement();
    }

    /**
     * Write the key attribute for a JSON value element.
     * When escape=true, also handles escaped-key attribute for keys containing
     * characters that require JSON escaping.
     */
    private static void writeKeyAttribute(MemTreeBuilder builder, String key,
                                           boolean escape, FunctionReference fallbackFn,
                                           XQueryContext context) throws XPathException {
        if (key == null) {
            return;
        }
        if (escape) {
            final boolean[] hasEscapes = {false};
            final String escapedKey = escapeJsonStringForXml(key, hasEscapes);
            builder.addAttribute(KEY, escapedKey);
            if (hasEscapes[0]) {
                builder.addAttribute(ESCAPED_KEY, "true");
            }
        } else {
            final String processedKey = replaceNonXmlChars(key, fallbackFn, context);
            builder.addAttribute(KEY, processedKey);
        }
    }

    /**
     * Skip a JSON value (including nested structures) during parsing.
     * Used for duplicates=use-first to discard subsequent occurrences.
     */
    private static void skipJsonValue(JsonParser parser, JsonToken token) throws IOException {
        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
            int depth = 1;
            while (depth > 0) {
                final JsonToken t = parser.nextValue();
                if (t == null) {
                    break;
                }
                if (t == JsonToken.START_OBJECT || t == JsonToken.START_ARRAY) {
                    depth++;
                } else if (t == JsonToken.END_OBJECT || t == JsonToken.END_ARRAY) {
                    depth--;
                }
            }
        }
        // scalar values are already consumed by the caller's nextValue()
    }

    // ========================================================================
    // Escape handling helpers
    // ========================================================================

    /**
     * Re-encode a decoded JSON string for XML output in escape=true mode.
     * Per spec section 22.3.2: JSON escape sequences are preserved in their escaped form.
     * Characters that must be escaped in JSON (control chars, backslash) are re-encoded.
     * Characters valid in both JSON unescaped form and XML pass through as-is.
     *
     * @param decoded the decoded string from Jackson parser
     * @param hasEscapes output flag set to true if any escaping was performed
     * @return the string with JSON escapes preserved for XML
     */
    static String escapeJsonStringForXml(String decoded, boolean[] hasEscapes) {
        final StringBuilder sb = new StringBuilder(decoded.length());
        for (int i = 0; i < decoded.length(); i++) {
            final char c = decoded.charAt(i);
            if (c == '\\') {
                sb.append("\\\\");
                hasEscapes[0] = true;
            } else if (c == '\b') {
                sb.append("\\b");
                hasEscapes[0] = true;
            } else if (c == '\f') {
                sb.append("\\f");
                hasEscapes[0] = true;
            } else if (c == '\n') {
                sb.append("\\n");
                hasEscapes[0] = true;
            } else if (c == '\r') {
                sb.append("\\r");
                hasEscapes[0] = true;
            } else if (c == '\t') {
                sb.append("\\t");
                hasEscapes[0] = true;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 < decoded.length() && Character.isLowSurrogate(decoded.charAt(i + 1))) {
                    // Paired surrogate -> valid supplementary character
                    final int codePoint = Character.toCodePoint(c, decoded.charAt(i + 1));
                    if (isValidXmlChar(codePoint)) {
                        sb.appendCodePoint(codePoint);
                    } else {
                        sb.append(String.format("\\u%04X", (int) c));
                        sb.append(String.format("\\u%04X", (int) decoded.charAt(i + 1)));
                        hasEscapes[0] = true;
                    }
                    i++;
                } else {
                    // Unpaired high surrogate
                    sb.append(String.format("\\u%04x", (int) c));
                    hasEscapes[0] = true;
                }
            } else if (Character.isLowSurrogate(c)) {
                // Unpaired low surrogate
                sb.append(String.format("\\u%04x", (int) c));
                hasEscapes[0] = true;
            } else if (!isValidXmlChar(c)) {
                sb.append(String.format("\\u%04X", (int) c));
                hasEscapes[0] = true;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Replace characters that are not valid in XML 1.0 with U+FFFD or the fallback result.
     *
     * @param text the decoded text
     * @param fallbackFn optional fallback function for non-XML characters
     * @param context XQuery context for function evaluation
     * @return the processed string safe for XML 1.0
     */
    static String replaceNonXmlChars(String text, FunctionReference fallbackFn, XQueryContext context) throws XPathException {
        final StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            final char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < text.length() && Character.isLowSurrogate(text.charAt(i + 1))) {
                    final int codePoint = Character.toCodePoint(c, text.charAt(i + 1));
                    if (isValidXmlChar(codePoint)) {
                        sb.appendCodePoint(codePoint);
                    } else {
                        appendFallback(sb, String.format("\\u%04X", (int) c), fallbackFn, context);
                        appendFallback(sb, String.format("\\u%04X", (int) text.charAt(i + 1)), fallbackFn, context);
                    }
                    i++;
                } else {
                    // Unpaired high surrogate
                    appendFallback(sb, String.format("\\u%04X", (int) c), fallbackFn, context);
                }
            } else if (Character.isLowSurrogate(c)) {
                // Unpaired low surrogate
                appendFallback(sb, String.format("\\u%04X", (int) c), fallbackFn, context);
            } else if (!isValidXmlChar(c)) {
                appendFallback(sb, String.format("\\u%04X", (int) c), fallbackFn, context);
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Append a fallback replacement for a non-XML character.
     * If fallbackFn is provided, calls it with the backslash-uHHHH escape form;
     * otherwise appends U+FFFD.
     */
    private static void appendFallback(StringBuilder sb, String escapeForm,
                                        FunctionReference fallbackFn, XQueryContext context) throws XPathException {
        if (fallbackFn != null) {
            final Sequence result = fallbackFn.evalFunction(null, null,
                    new Sequence[]{new StringValue(escapeForm)});
            if (result.isEmpty()) {
                throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                        "fallback function must return a string, got empty sequence");
            }
            if (result.getItemCount() > 1) {
                throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                        "fallback function must return a single string, got " + result.getItemCount() + " items");
            }
            // getStringValue will throw FOTY0013 for function items
            sb.append(result.itemAt(0).getStringValue());
        } else {
            sb.append('\uFFFD');
        }
    }

    /**
     * Check if a Unicode code point is valid in XML 1.0.
     * Valid: #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
     */
    static boolean isValidXmlChar(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xA
                || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    /**
     * Strip a Unicode Byte Order Mark (U+FEFF) from the start of the string if present.
     */
    static String stripBOM(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }
}
