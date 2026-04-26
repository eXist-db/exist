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
    public static final QName KEY = new QName("key",null);

    // Recognized option keys (XQuery 3.1 + 4.0)
    private static final java.util.Set<String> KNOWN_OPTIONS = java.util.Set.of(
            OPTION_LIBERAL, OPTION_DUPLICATES, "escape", "fallback", "number-parser",
            "validate"  // accepted but unsupported in 3.1; rejected in 4.0
    );

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
        boolean liberal = false;
        String handleDuplicates = OPTION_DUPLICATES_USE_LAST;
        if (getArgumentCount() == 2) {
            final MapType options = (MapType)args[1].itemAt(0);

            final boolean strictOptions = context.getXQueryVersion() >= 40;

            // Validate option keys (in XQuery 4.0, unknown options must be rejected)
            if (strictOptions) {
                for (final io.lacuna.bifurcan.IEntry<AtomicValue, Sequence> entry : options) {
                    final AtomicValue key = entry.key();
                    if (key.getType() == Type.QNAME) {
                        // QName keys with namespace are vendor extensions — ignored
                        continue;
                    }
                    if (!KNOWN_OPTIONS.contains(key.getStringValue())) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Unknown option for fn:" + getSignature().getName().getLocalPart()
                                        + ": '" + key.getStringValue() + "'");
                    }
                }
            }

            // Validate 'validate' option → XPTY0004 (deprecated/unsupported);
            // any presence of the key is an error, regardless of value.
            if (options.contains(new StringValue("validate"))) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "The 'validate' option is not supported");
            }

            // Validate liberal option — must be a single boolean
            if (options.contains(new StringValue(OPTION_LIBERAL))) {
                final Sequence liberalOpt = options.get(new StringValue(OPTION_LIBERAL));
                if (liberalOpt == null || liberalOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'liberal' must be a single boolean");
                }
                final Item liberalItem = liberalOpt.itemAt(0);
                if (liberalItem.getType() != Type.BOOLEAN) {
                    if (Type.subTypeOf(liberalItem.getType(), Type.STRING)) {
                        final String val = liberalItem.getStringValue();
                        if (!"true".equals(val) && !"false".equals(val) && !"1".equals(val) && !"0".equals(val)) {
                            throw new XPathException(this, ErrorCodes.XPTY0004,
                                    "Option 'liberal' must be a boolean, got: " + val);
                        }
                    }
                    liberal = liberalItem.convertTo(Type.BOOLEAN).effectiveBooleanValue();
                } else {
                    liberal = liberalItem.convertTo(Type.BOOLEAN).effectiveBooleanValue();
                }
            }

            // Validate duplicates option — must be a single string.
            // For json-to-xml, only "reject" and "use-first" are allowed (FOJS0005);
            // for parse-json/json-doc, all four values are accepted.
            if (options.contains(new StringValue(OPTION_DUPLICATES))) {
                final Sequence duplicateOpt = options.get(new StringValue(OPTION_DUPLICATES));
                if (duplicateOpt == null || duplicateOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'duplicates' must be a single string");
                }
                handleDuplicates = duplicateOpt.itemAt(0).getStringValue();
                if (!OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_USE_LAST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_REJECT.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_RETAIN.equals(handleDuplicates)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Invalid value for 'duplicates' option: " + handleDuplicates);
                }
                if (isCalledAs(FS_JSON_TO_XML_NAME)
                        && !OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)
                        && !OPTION_DUPLICATES_REJECT.equals(handleDuplicates)) {
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "fn:json-to-xml: 'duplicates' option must be 'reject' or 'use-first', got: "
                                    + handleDuplicates);
                }
            }

            // Validate escape option — must be a single boolean
            if (options.contains(new StringValue("escape"))) {
                final Sequence escapeOpt = options.get(new StringValue("escape"));
                if (escapeOpt == null || escapeOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'escape' must be a single boolean");
                }
                final Item escItem = escapeOpt.itemAt(0);
                if (escItem.getType() != Type.BOOLEAN) {
                    try {
                        escItem.atomize().convertTo(Type.BOOLEAN);
                    } catch (final XPathException e) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Option 'escape' must be a boolean");
                    }
                }
            }

            // Validate fallback option — must be a single function with arity 1
            if (options.contains(new StringValue("fallback"))) {
                final Sequence fallbackOpt = options.get(new StringValue("fallback"));
                if (fallbackOpt == null || fallbackOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' must be a single function");
                }
                final Item fallbackItem = fallbackOpt.itemAt(0);
                if (!(fallbackItem instanceof FunctionReference)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' must be a function, got: " + Type.getTypeName(fallbackItem.getType()));
                }
                final int arity = ((FunctionReference) fallbackItem).getSignature().getArgumentCount();
                if (arity != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'fallback' must be a function with arity 1, got arity " + arity);
                }
            }

            // Validate number-parser option — must be a single function with arity 1
            if (options.contains(new StringValue("number-parser"))) {
                final Sequence numberParserOpt = options.get(new StringValue("number-parser"));
                if (numberParserOpt == null || numberParserOpt.getItemCount() != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a single function");
                }
                final Item npItem = numberParserOpt.itemAt(0);
                if (!(npItem instanceof FunctionReference)) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a function, got: " + Type.getTypeName(npItem.getType()));
                }
                final int arity = ((FunctionReference) npItem).getSignature().getArgumentCount();
                if (arity != 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Option 'number-parser' must be a function with arity 1, got arity " + arity);
                }
            }
            final Sequence escapeOpt = options.get(new StringValue(OPTION_ESCAPE));
            if (escapeOpt.hasOne()) {
                try {
                    escapeOpt.itemAt(0).convertTo(Type.BOOLEAN);
                } catch (final XPathException e) {
                    throw new XPathException(this, ErrorCodes.FOJS0005,
                            "Value of option 'escape' is not a valid xs:boolean: " + escapeOpt.itemAt(0).getStringValue());
                }
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
        final String jsonText = json.itemAt(0).getStringValue();
        if (jsonText.isEmpty() || jsonText.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001,
                    "JSON text is empty");
        }
        try (final JsonParser parser = factory.createParser(jsonText)) {
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
        final String jsonText = json.itemAt(0).getStringValue();
        if (jsonText.isEmpty() || jsonText.trim().isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOJS0001,
                    "JSON text is empty");
        }
        try (final JsonParser parser = factory.createParser(jsonText)) {
            context.pushDocumentContext();
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            factory.configure(JsonParser.Feature.ALLOW_BACKSLASH_ESCAPING_ANY_CHARACTER, false);
            try {
                jsonToXml(builder, parser, handleDuplicates);
            } catch (final RuntimeException re) {
                // memtree builder may reject XML-invalid characters with FOCH0001;
                // surface this as FOJS0001 (parse error) per the spec
                if (re.getMessage() != null && re.getMessage().contains("FOCH0001")) {
                    throw new XPathException(this, ErrorCodes.FOJS0001, re.getMessage());
                }
                throw re;
            }
            return builder.getDocument() == null ? Sequence.EMPTY_SEQUENCE : builder.getDocument();
        } catch (IOException e) {
            // Duplicate key detection in jsonToXml uses an IOException with a FOJS0003 prefix
            if (e.getMessage() != null && e.getMessage().startsWith("FOJS0003:")) {
                throw new XPathException(this, ErrorCodes.FOJS0003, e.getMessage().substring(9).trim());
            }
            throw new XPathException(this, ErrorCodes.FOJS0001, e.getMessage());
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
            try (final java.io.Reader dynReader = context.getDynamicallyAvailableTextResource(
                    url, java.nio.charset.StandardCharsets.UTF_8)) {
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
                    } catch (final java.io.IOException jsonErr) {
                        throw new XPathException(this, ErrorCodes.FOJS0001, jsonErr.getMessage());
                    }
                }
            } catch (final java.io.IOException e) {
                // Not a dynamic resource, fall through to URL resolution
            }
            boolean resolvedFromBaseUri = false;
            if (url.indexOf(':') == Constants.STRING_NOT_FOUND) {
                // Relative URI: resolve against static base URI
                final String resolved = resolveAgainstBaseUri(url);
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
                final java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.isReadable(path)) {
                    try (final InputStream is = java.nio.file.Files.newInputStream(path)) {
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
                    // JSON fractional numbers → xs:double
                    next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                    break;
                case VALUE_NUMBER_INT:
                    // XQuery 4.0: JSON integers → xs:integer (was xs:double in 3.1)
                    try {
                        next = new IntegerValue(parser.getLongValue());
                    } catch (final Exception e) {
                        // Fallback to double for very large integers
                        next = new StringValue(parser.getText()).convertTo(Type.DOUBLE);
                    }
                    break;
                case VALUE_NULL:
                    next = null;
                    break;
                default:
                    next = new StringValue(replaceInvalidXmlChars(parser.getText()));
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
                        final StringValue name = new StringValue(replaceInvalidXmlChars(currentName));
                        final MapType map = (MapType) parent;
                        final Sequence newValue = next == null ? Sequence.EMPTY_SEQUENCE : next.toSequence();
                        if (map.contains(name)) {
                            // handle duplicate keys
                            if (handleDuplicates.equals(OPTION_DUPLICATES_REJECT)) {
                                throw new XPathException(map.getExpression(), ErrorCodes.FOJS0003, "Duplicate key: " + currentName);
                            } else if (handleDuplicates.equals(OPTION_DUPLICATES_USE_LAST)) {
                                map.add(name, newValue);
                            } else if (handleDuplicates.equals(OPTION_DUPLICATES_RETAIN)) {
                                final Sequence existing = map.get(name);
                                final ValueSequence combined = new ValueSequence(existing.getItemCount() + newValue.getItemCount());
                                combined.addAll(existing);
                                combined.addAll(newValue);
                                map.add(name, combined);
                            }
                            // USE_FIRST: keep existing value
                        } else {
                            map.add(name, newValue);
                        }
                        break;
                }
            }
        }
        return next;
    }

    /**
     * Replace characters that are invalid in XML 1.0 content (codepoints
     * outside the allowed XML character ranges, including unpaired surrogates)
     * with U+FFFD. Used by parse-json/json-to-xml when no fallback option is
     * supplied (per XPath 3.1 §17.5.1).
     */
    static String replaceInvalidXmlChars(final String s) {
        if (s == null) return null;
        StringBuilder sb = null;
        for (int i = 0; i < s.length(); i++) {
            final char c = s.charAt(i);
            // Valid XML 1.0: #x9, #xA, #xD, #x20-#xD7FF, #xE000-#xFFFD,
            // and supplementary chars via valid surrogate pairs
            boolean valid = (c == 0x9 || c == 0xA || c == 0xD
                    || (c >= 0x20 && c <= 0xD7FF)
                    || (c >= 0xE000 && c <= 0xFFFD));
            if (Character.isHighSurrogate(c)) {
                if (i + 1 < s.length() && Character.isLowSurrogate(s.charAt(i + 1))) {
                    if (sb != null) {
                        sb.append(c).append(s.charAt(i + 1));
                    }
                    i++;
                    continue;
                }
                valid = false;
            } else if (Character.isLowSurrogate(c)) {
                valid = false;
            }
            if (!valid) {
                if (sb == null) {
                    sb = new StringBuilder(s.length());
                    sb.append(s, 0, i);
                }
                sb.append('\uFFFD');
            } else if (sb != null) {
                sb.append(c);
            }
        }
        return sb == null ? s : sb.toString();
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
        jsonToXml(builder, parser, OPTION_DUPLICATES_USE_LAST);
    }

    /**
     * Generate XML from JSON tokens, with duplicate-key handling per the
     * `duplicates` option. When duplicates="reject" and a duplicate key is
     * seen, FOJS0003 is raised. Other modes (use-first/use-last/retain) are
     * not enforced on the XML output (the result simply contains all entries
     * as the spec allows).
     */
    public static void jsonToXml(final MemTreeBuilder builder, final JsonParser parser,
                                 final String handleDuplicates) throws IOException {
        final java.util.Deque<java.util.Set<String>> keyStack = new java.util.ArrayDeque<>();
        JsonToken token;
        while ((token = parser.nextValue()) != null) {
            if (token == JsonToken.END_OBJECT) {
                if (!keyStack.isEmpty()) keyStack.pop();
                builder.endElement();
                continue;
            }
            if (token == JsonToken.END_ARRAY) {
                builder.endElement();
                continue;
            }
            // Detect duplicate key in the enclosing object
            final String currentName = parser.getCurrentName();
            if (currentName != null && !keyStack.isEmpty()) {
                final java.util.Set<String> seen = keyStack.peek();
                if (!seen.add(currentName)) {
                    if (OPTION_DUPLICATES_REJECT.equals(handleDuplicates)) {
                        throw new IOException("FOJS0003: Duplicate key in object: " + currentName);
                    } else if (OPTION_DUPLICATES_USE_FIRST.equals(handleDuplicates)) {
                        // Skip this entry — including any subtree it introduces
                        if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                            parser.skipChildren();
                        }
                        continue;
                    }
                    // For use-last/retain, the simple emit-everything behavior
                    // produces XML containing duplicates; that is acceptable per
                    // the spec since the result is not schema-validated here.
                }
            }
            switch (token) {
                case START_OBJECT:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "map", "map", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    keyStack.push(new java.util.HashSet<>());
                    break;
                case START_ARRAY:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "array", "array", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    break;
                case VALUE_FALSE:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "boolean", "boolean", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    builder.characters(Boolean.toString(false));
                    builder.endElement();
                    break;
                case VALUE_TRUE:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "boolean", "boolean", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    builder.characters(Boolean.toString(true));
                    builder.endElement();
                    break;
                case VALUE_NUMBER_FLOAT:
                case VALUE_NUMBER_INT:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "number", "number", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    builder.characters(parser.getText());
                    builder.endElement();
                    break;
                case VALUE_NULL:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "null", "null", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    builder.endElement();
                    break;
                case VALUE_STRING:
                    builder.startElement(Namespaces.XPATH_FUNCTIONS_NS, "string", "string", null);
                    if (currentName != null) {
                        builder.addAttribute(KEY, replaceInvalidXmlChars(currentName));
                    }
                    builder.characters(replaceInvalidXmlChars(parser.getText()));
                    builder.endElement();
                    break;
                default:
                    break;
            }
        }
    }
}
