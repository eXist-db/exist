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
 * Functions related to JSON parsing: fn:parse-json, fn:json-doc, fn:json-to-xml.
 *
 * @author Wolf
 */
public class JSON extends BasicFunction {

    private static final FunctionParameterSequenceType FS_PARAM_JSON_TEXT = optParam("json-text", Type.STRING, "JSON text as defined in [RFC 7159]. The function parses this string to return an XDM value");
    private static final FunctionParameterSequenceType FS_PARAM_HREF = optParam("href", Type.STRING, "URL pointing to a JSON resource");
    private static final FunctionParameterSequenceType FS_PARAM_OPTIONS = optParam("options", Type.MAP_ITEM, "Parsing options");

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
                        FS_PARAM_OPTIONS
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
    public static final String OPTION_FALLBACK = "fallback";
    public static final String OPTION_NUMBER_PARSER = "number-parser";
    public static final String OPTION_VALIDATE = "validate";
    public static final String OPTION_NULL = "null";
    public static final QName KEY = new QName("key", null);
    public static final QName ESCAPED = new QName("escaped", null);
    public static final QName ESCAPED_KEY = new QName("escaped-key", null);

    // Recognized option keys (XQuery 3.1 + 4.0). In XQuery 4.0, unknown options are rejected.
    private static final java.util.Set<String> KNOWN_OPTIONS = java.util.Set.of(
            OPTION_LIBERAL, OPTION_DUPLICATES, OPTION_ESCAPE, OPTION_FALLBACK,
            OPTION_NUMBER_PARSER, OPTION_NULL
    );

    /** Bundle of validated parse-json options to thread through the parser. */
    private static final class ParseOptions {
        final String duplicates;
        final boolean escape;
        final FunctionReference fallbackFn;
        final FunctionReference numberParserFn;
        final Sequence nullValue;

        ParseOptions(String duplicates, boolean escape, FunctionReference fallbackFn,
                FunctionReference numberParserFn, Sequence nullValue) {
            this.duplicates = duplicates;
            this.escape = escape;
            this.fallbackFn = fallbackFn;
            this.numberParserFn = numberParserFn;
            this.nullValue = nullValue;
        }
    }

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
        final boolean strictOptions = context.getXQueryVersion() >= 40;

        boolean liberal = false;
        boolean escape = false;
        // Default for parse-json/json-doc is use-first (XPath/XQuery 3.1+ §17.5.1).
        // For json-to-xml it is also use-first; we use retain only as a safe default
        // when callers do not specify, but the spec default is use-first.
        String handleDuplicates = OPTION_DUPLICATES_USE_FIRST;
        FunctionReference fallbackFn = null;
        FunctionReference numberParserFn = null;
        Sequence nullValue = null;  // null indicates "use empty sequence" (default)

        if (getArgumentCount() == 2 && !args[1].isEmpty()) {
            final Item optItem = args[1].itemAt(0);
            if (optItem.getType() != Type.MAP_ITEM) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Expected map for options parameter, got " + Type.getTypeName(optItem.getType()));
            }
            final MapType options = (MapType) optItem;

            // In XQuery 4.0, unknown options must be rejected. Includes 'validate' and
            // any vendor option not recognized here. QName-keyed entries with a namespace
            // are vendor extensions and pass through silently.
            if (strictOptions) {
                for (final io.lacuna.bifurcan.IEntry<AtomicValue, Sequence> entry : options) {
                    final AtomicValue key = entry.key();
                    if (key.getType() == Type.QNAME) {
                        continue;
                    }
                    final String keyName = key.getStringValue();
                    if (!KNOWN_OPTIONS.contains(keyName)) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Unknown option for fn:" + getSignature().getName().getLocalPart()
                                        + ": '" + keyName + "'");
                    }
                }
            }

            // In XQuery 3.1 mode, 'validate' is accepted but ignored. In 4.0 mode, the
            // option-key check above already rejected it.
            liberal = getBooleanOption(options, OPTION_LIBERAL, false);
            escape = getBooleanOption(options, OPTION_ESCAPE, false);

            // duplicates option
            if (options.contains(new StringValue(OPTION_DUPLICATES))) {
                final Sequence dupOpt = options.get(new StringValue(OPTION_DUPLICATES));
                if (dupOpt == null || dupOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'duplicates' must be a single string value");
                }
                final Item dupItem = dupOpt.itemAt(0);
                final AtomicValue atomized = dupItem.atomize();
                if (!Type.subTypeOf(atomized.getType(), Type.STRING)
                        && atomized.getType() != Type.UNTYPED_ATOMIC) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'duplicates' must be a string, got " + Type.getTypeName(atomized.getType()));
                }
                handleDuplicates = atomized.getStringValue();
                final boolean validForParse = OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)
                        || OPTION_DUPLICATES_USE_LAST.equals(handleDuplicates)
                        || OPTION_DUPLICATES_REJECT.equals(handleDuplicates);
                if (!validForParse) {
                    // 'retain' is allowed only as a vendor extension in 3.1 and removed in 4.0;
                    // any other value is invalid for parse-json/json-doc.
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "Invalid value for 'duplicates' option: " + handleDuplicates);
                }
                if (isJsonToXml && !OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_REJECT.equals(handleDuplicates)) {
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "fn:json-to-xml: 'duplicates' option must be 'reject' or 'use-first', got: "
                                    + handleDuplicates);
                }
            }

            // fallback option (function with arity 1)
            if (options.contains(new StringValue(OPTION_FALLBACK))) {
                final Sequence fbOpt = options.get(new StringValue(OPTION_FALLBACK));
                if (fbOpt == null || fbOpt.getItemCount() != 1) {
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
                            "Option 'fallback' function must have arity 1, got "
                                    + fallbackFn.getSignature().getArgumentCount());
                }
            }

            // number-parser option (function with arity 1)
            if (options.contains(new StringValue(OPTION_NUMBER_PARSER))) {
                final Sequence npOpt = options.get(new StringValue(OPTION_NUMBER_PARSER));
                if (npOpt == null || npOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a single function value");
                }
                final Item npItem = npOpt.itemAt(0);
                if (!(npItem instanceof FunctionReference)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a function, got " + Type.getTypeName(npItem.getType()));
                }
                // Per QT4 PR975 the spec doesn't enforce a strict arity-1 check at
                // option-validation time -- arity mismatches surface naturally when
                // the function is invoked.
                numberParserFn = (FunctionReference) npItem;
            }

            // null option: any sequence (0+ items) used in place of JSON null in parse-json output.
            if (options.contains(new StringValue(OPTION_NULL))) {
                nullValue = options.get(new StringValue(OPTION_NULL));
            }

            // The escape and fallback options conflict: escape preserves JSON-escape
            // forms while fallback substitutes them. Spec section 22.3.2 makes this
            // a static error.
            if (escape && fallbackFn != null) {
                throw new XPathException(this, ErrorCodes.FOJS0005,
                        "Options 'escape' and 'fallback' cannot both be specified");
            }
        }

        final JsonFactory factory = createJsonFactory(liberal);

        if (isCalledAs(FS_PARSE_JSON_NAME)) {
            final ParseOptions opts = new ParseOptions(handleDuplicates, escape,
                    fallbackFn, numberParserFn, nullValue);
            return parse(args[0], factory, opts);
        } else if (isJsonToXml) {
            return toxml(args[0], handleDuplicates, escape, fallbackFn, numberParserFn, factory);
        } else {
            final ParseOptions opts = new ParseOptions(handleDuplicates, escape,
                    fallbackFn, numberParserFn, nullValue);
            return parseResource(args[0], factory, opts);
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
        if (optVal == null || optVal.getItemCount() != 1) {
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
        final JsonFactory factory = new JsonFactory();
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

    private Sequence parse(Sequence json, JsonFactory factory, ParseOptions opts) throws XPathException {
        if (json.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String jsonText = stripBOM(json.itemAt(0).getStringValue());
        if (jsonText.isEmpty() || jsonText.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001, "JSON text is empty");
        }
        try (final JsonParser parser = factory.createParser(jsonText)) {
            final Sequence result = readValue(context, parser, null, opts);
            return result == null ? Sequence.EMPTY_SEQUENCE : result;
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
        final String jsonText = stripBOM(json.itemAt(0).getStringValue());
        if (jsonText.isEmpty() || jsonText.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001, "JSON text is empty");
        }
        try (final JsonParser parser = factory.createParser(jsonText)) {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            try {
                jsonToXml(builder, parser, escape, handleDuplicates, fallbackFn, numberParserFn, context);
            } catch (final RuntimeException re) {
                if (re.getMessage() != null && re.getMessage().contains("FOCH0001")) {
                    throw new XPathException(this, ErrorCodes.FOJS0001, re.getMessage());
                }
                throw re;
            }
            return builder.getDocument() == null ? Sequence.EMPTY_SEQUENCE : builder.getDocument();
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("FOJS0003:")) {
                throw new XPathException(this, ErrorCodes.FOJS0003, e.getMessage().substring(9).trim());
            }
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }

    private Sequence parseResource(Sequence href, JsonFactory factory, ParseOptions opts) throws XPathException {
        if (href.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        try {
            String url = href.getStringValue();

            try (final java.io.Reader dynReader = context.getDynamicallyAvailableTextResource(
                    url, java.nio.charset.StandardCharsets.UTF_8)) {
                if (dynReader != null) {
                    final StringBuilder sb = new StringBuilder();
                    final char[] buf = new char[4096];
                    int read;
                    while ((read = dynReader.read(buf)) > 0) {
                        sb.append(buf, 0, read);
                    }
                    try (final JsonParser parser = factory.createParser(stripBOM(sb.toString()))) {
                        final Sequence result = readValue(context, parser, null, opts);
                        return result == null ? Sequence.EMPTY_SEQUENCE : result;
                    } catch (final java.io.IOException jsonErr) {
                        throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                    }
                }
            } catch (final java.io.IOException e) {
                // Not a dynamic resource, fall through to URL resolution
            }

            boolean resolvedFromBaseUri = false;
            if (url.indexOf(':') == Constants.STRING_NOT_FOUND) {
                final String resolved = resolveAgainstBaseUri(url);
                if (resolved != null && resolved.startsWith("file:")) {
                    url = resolved;
                    resolvedFromBaseUri = true;
                } else {
                    url = XmldbURI.EMBEDDED_SERVER_URI_PREFIX + url;
                }
            }

            if (resolvedFromBaseUri && url.startsWith("file:")) {
                final String filePath = url.replaceFirst("^file:(?://[^/]*)?", "");
                final java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.isReadable(path)) {
                    try (final InputStream is = java.nio.file.Files.newInputStream(path)) {
                        try (final JsonParser parser = factory.createParser(is)) {
                            final Sequence result = readValue(context, parser, null, opts);
                            return result == null ? Sequence.EMPTY_SEQUENCE : result;
                        } catch (final IOException jsonErr) {
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
                    final Sequence result = readValue(context, parser, null, opts);
                    return result == null ? Sequence.EMPTY_SEQUENCE : result;
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

    // ========================================================================
    // fn:parse-json / fn:json-doc -- recursive XDM builder with full options.
    // ========================================================================

    /**
     * Legacy entry point used by callers that don't have ParseOptions context.
     * Uses default duplicate handling, no fallback / number-parser, empty null.
     */
    public static Item readValue(XQueryContext context, JsonParser parser, String handleDuplicates) throws IOException, XPathException {
        final ParseOptions opts = new ParseOptions(handleDuplicates, false, null, null, null);
        final Sequence seq = readValue(context, parser, null, opts);
        if (seq == null || seq.isEmpty()) {
            return null;
        }
        return seq.itemAt(0);
    }

    /**
     * Read a single JSON value from the parser. Returns a Sequence so that the
     * 'null' option (with a multi-item replacement value) and number-parser
     * (which may legitimately return empty or non-atomic results) can be
     * represented faithfully.
     */
    private static Sequence readValue(XQueryContext context, JsonParser parser, Sequence parent,
                                       ParseOptions opts) throws IOException, XPathException {
        JsonToken token;
        Sequence next = null;
        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                return parent;
            }
            switch (token) {
                case START_OBJECT -> {
                    final MapType map = new MapType(null, context, null);
                    next = map.toSequence();
                    readValue(context, parser, next, opts);
                }
                case START_ARRAY -> {
                    final ArrayType arr = new ArrayType(null, context, Sequence.EMPTY_SEQUENCE);
                    next = arr.toSequence();
                    readValue(context, parser, next, opts);
                }
                case VALUE_FALSE -> next = BooleanValue.FALSE.toSequence();
                case VALUE_TRUE -> next = BooleanValue.TRUE.toSequence();
                case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT ->
                        next = parseNumber(context, parser, token, opts);
                case VALUE_NULL -> next = opts.nullValue;  // null means "use empty sequence"
                default -> next = new StringValue(processString(parser.getText(), opts)).toSequence();
            }
            if (parent != null) {
                final Item parentItem = parent.itemAt(0);
                if (parentItem instanceof ArrayType arr) {
                    arr.add(next == null ? Sequence.EMPTY_SEQUENCE : next);
                } else if (parentItem instanceof MapType map) {
                    final String currentName = parser.getCurrentName();
                    if (currentName == null) {
                        throw new XPathException((Expression) null, ErrorCodes.FOJS0001, "Invalid JSON object");
                    }
                    final StringValue normalizedKey = new StringValue(processString(currentName, opts));
                    if (map.contains(normalizedKey)) {
                        switch (opts.duplicates) {
                            case OPTION_DUPLICATES_REJECT ->
                                    throw new XPathException(map.getExpression(), ErrorCodes.FOJS0003,
                                            "Duplicate key: " + currentName);
                            case OPTION_DUPLICATES_USE_LAST ->
                                    map.add(normalizedKey, next == null ? Sequence.EMPTY_SEQUENCE : next);
                            case OPTION_DUPLICATES_RETAIN -> {
                                final Sequence existing = map.get(normalizedKey);
                                final ValueSequence combined = new ValueSequence(
                                        existing.getItemCount() + (next == null ? 0 : next.getItemCount()));
                                combined.addAll(existing);
                                if (next != null) {
                                    combined.addAll(next);
                                }
                                map.add(normalizedKey, combined);
                            }
                            default -> { /* USE_FIRST: keep existing */ }
                        }
                    } else {
                        map.add(normalizedKey, next == null ? Sequence.EMPTY_SEQUENCE : next);
                    }
                }
            }
        }
        return next;
    }

    private static Sequence parseNumber(XQueryContext context, JsonParser parser, JsonToken token,
                                         ParseOptions opts) throws IOException, XPathException {
        if (opts.numberParserFn != null) {
            final Sequence numResult = opts.numberParserFn.evalFunction(null, null,
                    new Sequence[]{new StringValue(parser.getText())});
            if (numResult == null || numResult.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }
            if (numResult.getItemCount() > 1) {
                throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                        "number-parser function must return zero or one item, got "
                                + numResult.getItemCount());
            }
            return numResult;
        }
        if (token == JsonToken.VALUE_NUMBER_INT) {
            try {
                return new IntegerValue(parser.getLongValue()).toSequence();
            } catch (final Exception e) {
                return new StringValue(parser.getText()).convertTo(Type.DOUBLE).toSequence();
            }
        }
        return new StringValue(parser.getText()).convertTo(Type.DOUBLE).toSequence();
    }

    /**
     * Process a JSON string value or key per the parse-json options.
     * Applies the escape option (re-encodes special chars to JSON form) or
     * invokes the fallback (replacing chars not allowed in XML) when chars
     * not valid in XML are encountered. Without fallback, invalid chars are
     * replaced with U+FFFD.
     */
    private static String processString(String text, ParseOptions opts) throws XPathException {
        if (text == null) {
            return null;
        }
        if (opts.escape) {
            return escapeJsonString(text);
        }
        return replaceNonXmlChars(text, opts.fallbackFn);
    }

    /**
     * Re-encode a Jackson-decoded JSON string with JSON escape sequences for
     * special characters. Used when the {@code escape} option is true.
     * <p>
     * Per spec: control characters (&lt;0x20), the backslash and the double quote
     * are escaped; characters not allowed in XML are emitted as <code>\\uXXXX</code>;
     * other characters pass through unchanged.
     */
    static String escapeJsonString(final String s) {
        // Per XPath/XQuery 4.0, escape=true preserves a JSON-escape representation
        // for the backslash and for chars that cannot appear in XML; the double
        // quote is NOT escaped because XML element/string contexts allow it
        // unescaped (see QT4 tests json-to-xml-049 / json-doc-012 / parse-json-107).
        final StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (Character.isHighSurrogate(c)
                            && i + 1 < s.length()
                            && Character.isLowSurrogate(s.charAt(i + 1))) {
                        final int cp = Character.toCodePoint(c, s.charAt(i + 1));
                        if (isValidXmlChar(cp)) {
                            sb.append(c).append(s.charAt(i + 1));
                        } else {
                            appendUnicodeEscape(sb, c);
                            appendUnicodeEscape(sb, s.charAt(i + 1));
                        }
                        i++;
                    } else if (Character.isHighSurrogate(c) || Character.isLowSurrogate(c)) {
                        // unpaired surrogate
                        appendUnicodeEscape(sb, c);
                    } else if (c < 0x20 || !isValidXmlChar(c)) {
                        appendUnicodeEscape(sb, c);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static void appendUnicodeEscape(StringBuilder sb, char c) {
        sb.append('\\').append('u');
        final String hex = Integer.toHexString(c & 0xffff);
        for (int pad = hex.length(); pad < 4; pad++) {
            sb.append('0');
        }
        sb.append(hex);
    }

    /**
     * Replace characters not valid in XML 1.0 (NUL, control chars except #x9/#xA/#xD,
     * unpaired surrogates, #xFFFE, #xFFFF) with either the result of the fallback
     * function (if supplied) or U+FFFD.
     */
    static String replaceNonXmlChars(final String s, final FunctionReference fallbackFn) throws XPathException {
        if (s == null) {
            return null;
        }
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    if (sb != null) {
                        sb.append(c).append(s.charAt(i + 1));
                    }
                    i++;
                    continue;
                }
                // unpaired high surrogate
                sb = ensureBuffer(sb, s, i);
                appendInvalidReplacement(sb, c, fallbackFn);
                continue;
            }
            if (Character.isLowSurrogate(c)) {
                sb = ensureBuffer(sb, s, i);
                appendInvalidReplacement(sb, c, fallbackFn);
                continue;
            }
            if (!isValidXmlChar(c)) {
                sb = ensureBuffer(sb, s, i);
                appendInvalidReplacement(sb, c, fallbackFn);
                continue;
            }
            if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
    }

    private static StringBuilder ensureBuffer(StringBuilder sb, String s, int i) {
        if (sb == null) {
            sb = new StringBuilder(s.length());
            sb.append(s, 0, i);
        }
        return sb;
    }

    private static void appendInvalidReplacement(StringBuilder sb, char c,
                                                  FunctionReference fallbackFn) throws XPathException {
        if (fallbackFn == null) {
            sb.append('\uFFFD');
            return;
        }
        final String escapeForm = jsonEscapeForm(c);
        final Sequence result = fallbackFn.evalFunction(null, null,
                new Sequence[]{new StringValue(escapeForm)});
        if (result == null || result.isEmpty()) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                    "fallback function must return a single string, got empty sequence");
        }
        if (result.getItemCount() > 1) {
            throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                    "fallback function must return a single string, got " + result.getItemCount() + " items");
        }
        final Item resultItem = result.itemAt(0);
        if (resultItem instanceof FunctionReference) {
            throw new XPathException((Expression) null, ErrorCodes.FOTY0013,
                    "fallback function must return an atomic value, got a function item");
        }
        sb.append(resultItem.getStringValue());
    }

    /** Return the JSON escape sequence that represents {@code c}. */
    private static String jsonEscapeForm(char c) {
        return switch (c) {
            case '\b' -> "\\b";
            case '\f' -> "\\f";
            case '\n' -> "\\n";
            case '\r' -> "\\r";
            case '\t' -> "\\t";
            default -> String.format("\\u%04X", (int) c);
        };
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

    /** Strip a Unicode Byte Order Mark (U+FEFF) from the start of the string if present. */
    static String stripBOM(String s) {
        if (s != null && !s.isEmpty() && s.charAt(0) == '\uFEFF') {
            return s.substring(1);
        }
        return s;
    }

    /**
     * Backwards-compatible variant of {@link #replaceNonXmlChars(String, FunctionReference)}.
     */
    static String replaceInvalidXmlChars(final String s) {
        try {
            return replaceNonXmlChars(s, null);
        } catch (XPathException e) {
            // No fallback supplied, so XPathException cannot be raised
            throw new IllegalStateException(e);
        }
    }

    // ========================================================================
    // fn:json-to-xml -- recursive XML builder.
    // ========================================================================

    public static void jsonToXml(MemTreeBuilder builder, JsonParser parser) throws IOException {
        try {
            jsonToXml(builder, parser, false, OPTION_DUPLICATES_RETAIN, null, null, null);
        } catch (XPathException e) {
            throw new IOException("XPath error during JSON-to-XML conversion", e);
        }
    }

    public static void jsonToXml(final MemTreeBuilder builder, final JsonParser parser,
                                 final String handleDuplicates) throws IOException {
        try {
            jsonToXml(builder, parser, false, handleDuplicates, null, null, null);
        } catch (XPathException e) {
            throw new IOException("XPath error during JSON-to-XML conversion", e);
        }
    }

    public static void jsonToXml(MemTreeBuilder builder, JsonParser parser,
                                  boolean escape, String duplicates,
                                  FunctionReference fallbackFn, FunctionReference numberParserFn,
                                  XQueryContext context) throws IOException, XPathException {
        final JsonToken token = parser.nextValue();
        if (token == null) {
            return;
        }
        writeJsonValueAsXml(builder, parser, token, null, escape, duplicates, fallbackFn, numberParserFn);
    }

    private static void writeJsonValueAsXml(MemTreeBuilder builder, JsonParser parser,
                                             JsonToken token, String key,
                                             boolean escape, String duplicates,
                                             FunctionReference fallbackFn,
                                             FunctionReference numberParserFn) throws IOException, XPathException {
        switch (token) {
            case START_OBJECT -> writeMapAsXml(builder, parser, key, escape, duplicates, fallbackFn, numberParserFn);
            case START_ARRAY -> writeArrayAsXml(builder, parser, key, escape, duplicates, fallbackFn, numberParserFn);
            case VALUE_STRING -> writeStringAsXml(builder, parser, key, escape, fallbackFn);
            case VALUE_NUMBER_FLOAT, VALUE_NUMBER_INT -> writeNumberAsXml(builder, parser, key, escape, fallbackFn, numberParserFn);
            case VALUE_TRUE -> writeBooleanAsXml(builder, key, "true", escape, fallbackFn);
            case VALUE_FALSE -> writeBooleanAsXml(builder, key, "false", escape, fallbackFn);
            case VALUE_NULL -> writeNullAsXml(builder, key, escape, fallbackFn);
            default -> { /* ignore */ }
        }
    }

    private static void writeMapAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                       boolean escape, String duplicates,
                                       FunctionReference fallbackFn, FunctionReference numberParserFn)
            throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "map", "map", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);

        final java.util.Set<String> seenKeys =
                OPTION_DUPLICATES_RETAIN.equals(duplicates) ? null : new java.util.HashSet<>();
        JsonToken token;
        while ((token = parser.nextValue()) != JsonToken.END_OBJECT) {
            if (token == null) {
                break;
            }
            final String childKey = parser.getCurrentName();
            if (seenKeys != null && childKey != null) {
                final String normalized = escape ? childKey : replaceNonXmlChars(childKey, fallbackFn);
                if (!seenKeys.add(normalized)) {
                    if (OPTION_DUPLICATES_REJECT.equals(duplicates)) {
                        throw new IOException("FOJS0003: Duplicate key in object: " + childKey);
                    } else if (OPTION_DUPLICATES_USE_FIRST.equals(duplicates)) {
                        skipJsonValue(parser, token);
                        continue;
                    }
                }
            }
            writeJsonValueAsXml(builder, parser, token, childKey, escape, duplicates, fallbackFn, numberParserFn);
        }
        builder.endElement();
    }

    private static void writeArrayAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                         boolean escape, String duplicates,
                                         FunctionReference fallbackFn, FunctionReference numberParserFn)
            throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "array", "array", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);

        JsonToken token;
        while ((token = parser.nextValue()) != JsonToken.END_ARRAY) {
            if (token == null) {
                break;
            }
            writeJsonValueAsXml(builder, parser, token, null, escape, duplicates, fallbackFn, numberParserFn);
        }
        builder.endElement();
    }

    private static void writeStringAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                          boolean escape, FunctionReference fallbackFn)
            throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "string", "string", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);

        final String text = parser.getText();
        if (escape) {
            final String escaped = escapeJsonString(text);
            if (!escaped.equals(text)) {
                builder.addAttribute(ESCAPED, "true");
            }
            builder.characters(escaped);
        } else {
            builder.characters(replaceNonXmlChars(text, fallbackFn));
        }
        builder.endElement();
    }

    private static void writeNumberAsXml(MemTreeBuilder builder, JsonParser parser, String key,
                                          boolean escape, FunctionReference fallbackFn,
                                          FunctionReference numberParserFn) throws IOException, XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "number", "number", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);

        if (numberParserFn != null) {
            final Sequence numResult = numberParserFn.evalFunction(null, null,
                    new Sequence[]{new StringValue(parser.getText())});
            if (numResult != null && !numResult.isEmpty()) {
                if (numResult.getItemCount() > 1) {
                    throw new XPathException((Expression) null, ErrorCodes.XPTY0004,
                            "number-parser function must return zero or one item, got " + numResult.getItemCount());
                }
                builder.characters(numResult.itemAt(0).getStringValue());
            }
        } else {
            builder.characters(parser.getText());
        }
        builder.endElement();
    }

    private static void writeBooleanAsXml(MemTreeBuilder builder, String key, String value,
                                           boolean escape, FunctionReference fallbackFn) throws XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "boolean", "boolean", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);
        builder.characters(value);
        builder.endElement();
    }

    private static void writeNullAsXml(MemTreeBuilder builder, String key,
                                        boolean escape, FunctionReference fallbackFn) throws XPathException {
        builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "null", "null", null);
        writeKeyAttribute(builder, key, escape, fallbackFn);
        builder.endElement();
    }

    private static void writeKeyAttribute(MemTreeBuilder builder, String key,
                                           boolean escape, FunctionReference fallbackFn) throws XPathException {
        if (key == null) {
            return;
        }
        if (escape) {
            final String escapedKey = escapeJsonString(key);
            builder.addAttribute(KEY, escapedKey);
            if (!escapedKey.equals(key)) {
                builder.addAttribute(ESCAPED_KEY, "true");
            }
        } else {
            builder.addAttribute(KEY, replaceNonXmlChars(key, fallbackFn));
        }
    }

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
    }
}
