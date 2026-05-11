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
import io.lacuna.bifurcan.IEntry;
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
import java.nio.file.Path;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.isReadable;
import static java.nio.file.Files.newInputStream;

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
                        optParam("options", Type.MAP_ITEM, "Parsing options")
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
    public static final String OPTION_DUPLICATES_RETAIN = "retain";
    public static final String OPTION_LIBERAL = "liberal";
    public static final String OPTION_ESCAPE = "escape";
    public static final String OPTION_UNESCAPE = "unescape";
    public static final String OPTION_VALIDATE = "validate";
    public static final String OPTION_FALLBACK = "fallback";
    public static final QName KEY = new QName("key",null);

    private static final Set<String> PERMITTED_DUPLICATES = Set.of(
            OPTION_DUPLICATES_REJECT, OPTION_DUPLICATES_USE_FIRST, OPTION_DUPLICATES_RETAIN);

    private static final Set<String> JSON_TO_XML_KNOWN_OPTIONS = Set.of(
            OPTION_LIBERAL, OPTION_DUPLICATES, OPTION_VALIDATE, OPTION_ESCAPE, OPTION_FALLBACK);

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
        // Empty options sequence -> defaults. Otherwise the value must be a map
        // (XPTY0004 per F&O 3.1 §2.4); the per-key validation lives in parseOptions.
        final ParsedOptions opts;
        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            final Item optItem = args[1].itemAt(0);
            if (optItem.getType() != Type.MAP_ITEM) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Expected map for options parameter, got " + Type.getTypeName(optItem.getType()));
            }
            opts = parseOptions((MapType) optItem, isCalledAs(FS_JSON_TO_XML_NAME));
        } else {
            opts = ParsedOptions.DEFAULTS;
        }

        JsonFactory factory = createJsonFactory(opts.liberal);
        final String handleDuplicates = opts.handleDuplicates;

        if (isCalledAs(FS_PARSE_JSON_NAME)) {
            return parse(args[0], handleDuplicates, factory);
        }  else if (isCalledAs(FS_JSON_TO_XML_NAME)) {
            return toxml(args[0], handleDuplicates, factory);
        } else {
            return parseResource(args[0], handleDuplicates, factory);
        }
    }

    private record ParsedOptions(boolean liberal, String handleDuplicates) {
        static final ParsedOptions DEFAULTS = new ParsedOptions(false, OPTION_DUPLICATES_USE_LAST);
    }

    /**
     * Validate and extract options per F&O 3.1 §2.4 (option-parameter conventions) and §17.5.3.
     * Wrong-typed values raise XPTY0004; bad permitted values raise FOJS0005.
     */
    private ParsedOptions parseOptions(final MapType options, final boolean isJsonToXml) throws XPathException {
        final Boolean liberalOpt = requireBooleanOpt(options, OPTION_LIBERAL);
        final String duplicatesOpt = requireStringOpt(options, OPTION_DUPLICATES);
        final Boolean validateOpt = requireBooleanOpt(options, OPTION_VALIDATE);
        requireBooleanOpt(options, OPTION_ESCAPE);
        requireFunctionOpt(options, OPTION_FALLBACK, 1);

        String handleDuplicates = OPTION_DUPLICATES_USE_LAST;
        if (duplicatesOpt != null) {
            if (!PERMITTED_DUPLICATES.contains(duplicatesOpt)) {
                throw new XPathException(this, ErrorCodes.FOJS0005,
                        "Value of option 'duplicates' is not permitted: " + duplicatesOpt);
            }
            handleDuplicates = duplicatesOpt;
        } else if (isJsonToXml) {
            handleDuplicates = Boolean.TRUE.equals(validateOpt)
                    ? OPTION_DUPLICATES_REJECT
                    : OPTION_DUPLICATES_RETAIN;
        }

        if (isJsonToXml) {
            if (Boolean.TRUE.equals(validateOpt) && OPTION_DUPLICATES_RETAIN.equals(duplicatesOpt)) {
                throw new XPathException(this, ErrorCodes.FOJS0005,
                        "Option 'duplicates' value 'retain' is not permitted when 'validate' is true");
            }
            rejectUnknownJsonToXmlOptions(options);
        }

        return new ParsedOptions(Boolean.TRUE.equals(liberalOpt), handleDuplicates);
    }

    private void rejectUnknownJsonToXmlOptions(final MapType options) throws XPathException {
        for (final IEntry<AtomicValue, Sequence> entry : options) {
            final String key = entry.key().getStringValue();
            if (!JSON_TO_XML_KNOWN_OPTIONS.contains(key)) {
                throw new XPathException(this, ErrorCodes.FOJS0005,
                        "Unknown option key for fn:json-to-xml: " + key);
            }
        }
    }

    /**
     * Return the value of an option as an xs:boolean, or null if the option is absent.
     * Raises XPTY0004 if the value is the wrong type or has cardinality other than 1.
     * An xs:untypedAtomic value is cast to xs:boolean per F&O 3.1 §2.4.
     */
    private Boolean requireBooleanOpt(final MapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            if (!options.contains(new StringValue(key))) {
                return null;
            }
            // Key present with empty-sequence value: cardinality mismatch.
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a single value, got empty sequence");
        }
        if (!value.hasOne()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a single xs:boolean, got cardinality " + value.getItemCount());
        }
        final AtomicValue atom = value.itemAt(0).atomize();
        final int t = atom.getType();
        if (Type.subTypeOf(t, Type.BOOLEAN)) {
            return ((BooleanValue) atom).getValue();
        }
        if (t == Type.UNTYPED_ATOMIC) {
            return atom.convertTo(Type.BOOLEAN).effectiveBooleanValue();
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Option '" + key + "' must be an xs:boolean, got " + Type.getTypeName(t));
    }

    /**
     * Return the value of an option as an xs:string, or null if the option is absent.
     * Raises XPTY0004 if the value is the wrong type or has cardinality other than 1.
     */
    private String requireStringOpt(final MapType options, final String key) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value == null) {
            return null;
        }
        if (value.isEmpty()) {
            if (!options.contains(new StringValue(key))) {
                return null;
            }
            // Key present with empty-sequence value: cardinality mismatch.
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a single value, got empty sequence");
        }
        if (!value.hasOne()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a single xs:string, got cardinality " + value.getItemCount());
        }
        final AtomicValue atom = value.itemAt(0).atomize();
        final int t = atom.getType();
        if (Type.subTypeOf(t, Type.STRING) || t == Type.UNTYPED_ATOMIC) {
            return atom.getStringValue();
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Option '" + key + "' must be an xs:string, got " + Type.getTypeName(t));
    }

    /**
     * Validate that an option, if present, is a single function reference of the given arity.
     * Raises XPTY0004 on type or arity mismatch (or wrong cardinality).
     */
    private void requireFunctionOpt(final MapType options, final String key, final int arity) throws XPathException {
        final Sequence value = options.get(new StringValue(key));
        if (value == null || value.isEmpty()) {
            return;
        }
        if (!value.hasOne()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a single function, got cardinality " + value.getItemCount());
        }
        final Item item = value.itemAt(0);
        if (!(item instanceof final FunctionReference fr)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a function, got " + Type.getTypeName(item.getType()));
        }
        final int actualArity = fr.getSignature().getArgumentCount();
        if (actualArity != arity) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Option '" + key + "' must be a function with arity " + arity + ", got arity " + actualArity);
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

            // Check dynamically available text resources first (XQTS runner registers these)
            try (final Reader dynReader = context.getDynamicallyAvailableTextResource(url, UTF_8)) {
                if (dynReader != null) {
                    final StringBuilder sb = new StringBuilder();
                    final char[] buf = new char[4096];
                    int read;
                    while ((read = dynReader.read(buf)) > 0) {
                        sb.append(buf, 0, read);
                    }
                    try (final JsonParser parser = factory.createParser(sb.toString())) {
                        final Item result = readValue(context, parser, handleDuplicates);
                        return result == null ? Sequence.EMPTY_SEQUENCE : result.toSequence();
                    } catch (final IOException jsonErr) {
                        throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                    }
                }
            } catch (final IOException e) {
                // Not a dynamic resource, fall through to URL resolution
            }
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
