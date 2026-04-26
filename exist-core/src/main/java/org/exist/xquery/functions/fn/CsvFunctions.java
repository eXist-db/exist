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

import org.apache.commons.io.IOUtils;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xquery.*;
import org.exist.xquery.functions.array.ArrayType;
import io.lacuna.bifurcan.IEntry;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Implements XQuery 4.0 CSV functions:
 * fn:csv-to-arrays, fn:parse-csv, fn:csv-to-xml, fn:csv-doc.
 */
public class CsvFunctions extends BasicFunction {

    // XQ4 namespace for CSV XML output
    private static final String CSV_NS = "http://www.w3.org/2005/xpath-functions";

    // fn:csv-to-arrays signatures
    public static final FunctionSignature[] FN_CSV_TO_ARRAYS = {
            new FunctionSignature(
                    new QName("csv-to-arrays", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as a sequence of arrays.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE, "A sequence of arrays, one per row")),
            new FunctionSignature(
                    new QName("csv-to-arrays", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as a sequence of arrays, using the specified options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Parsing options")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE, "A sequence of arrays, one per row"))
    };

    // fn:parse-csv signatures
    public static final FunctionSignature[] FN_PARSE_CSV = {
            new FunctionSignature(
                    new QName("parse-csv", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as a map.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "A map with columns, column-index, rows, and get")),
            new FunctionSignature(
                    new QName("parse-csv", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as a map, using the specified options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Parsing options")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "A map with columns, column-index, rows, and get"))
    };

    // fn:csv-to-xml signatures
    public static final FunctionSignature[] FN_CSV_TO_XML = {
            new FunctionSignature(
                    new QName("csv-to-xml", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as an XML document.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse")
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE, "An XML document representing the CSV data")),
            new FunctionSignature(
                    new QName("csv-to-xml", Function.BUILTIN_FUNCTION_NS),
                    "Parses a string as CSV data and returns the result as an XML document, using the specified options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The CSV string to parse"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Parsing options")
                    },
                    new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ZERO_OR_ONE, "An XML document representing the CSV data"))
    };

    // fn:csv-doc signatures
    public static final FunctionSignature[] FN_CSV_DOC = {
            new FunctionSignature(
                    new QName("csv-doc", Function.BUILTIN_FUNCTION_NS),
                    "Reads CSV data from the specified URI and returns the result as a map.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The URI of the CSV resource")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "A map with columns, column-index, rows, and get")),
            new FunctionSignature(
                    new QName("csv-doc", Function.BUILTIN_FUNCTION_NS),
                    "Reads CSV data from the specified URI and returns the result as a map, using the specified options.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The URI of the CSV resource"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.EXACTLY_ONE, "Parsing options")
                    },
                    new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "A map with columns, column-index, rows, and get"))
    };

    public CsvFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("csv-doc")) {
            return evalCsvDoc(args);
        }

        // Empty sequence input returns empty
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String csv = args[0].getStringValue();
        final CsvParser.CsvOptions options = parseOptions(args);

        if (isCalledAs("csv-to-arrays")) {
            return evalCsvToArrays(csv, options);
        } else if (isCalledAs("parse-csv")) {
            return evalParseCsv(csv, options);
        } else if (isCalledAs("csv-to-xml")) {
            return evalCsvToXml(csv, options);
        }
        throw new XPathException(this, ErrorCodes.XPST0017, "Unknown CSV function: " + getSignature().getName().getLocalPart());
    }

    // ==================== fn:csv-to-arrays ====================

    private Sequence evalCsvToArrays(final String csv, final CsvParser.CsvOptions options) throws XPathException {
        options.validate(this);
        final CsvParser parser = new CsvParser(options, this);
        final ValueSequence result = new ValueSequence();

        parser.parse(csv, new CsvParser.CsvConverter() {
            @Override
            public void header(final List<String> fields) {
                // Header row is also returned as an array in csv-to-arrays
                // (per XQ4 spec: "If header is true, the first row is treated as a header
                //  but still appears in the output")
                // Actually per spec: if header=true, the header row is NOT included
                // in the result of csv-to-arrays.
            }

            @Override
            public void record(final List<String> fields) throws XPathException {
                result.add(fieldsToArray(fields));
            }

            @Override
            public void finish() {
            }
        });
        return result;
    }

    // ==================== fn:parse-csv ====================

    private Sequence evalParseCsv(final String csv, final CsvParser.CsvOptions options) throws XPathException {
        options.validate(this);
        final CsvParser parser = new CsvParser(options, this);
        final List<List<String>> allRows = new ArrayList<>();
        final List<String>[] headerHolder = new List[]{null};

        parser.parse(csv, new CsvParser.CsvConverter() {
            @Override
            public void header(final List<String> fields) {
                headerHolder[0] = fields;
            }

            @Override
            public void record(final List<String> fields) {
                allRows.add(fields);
            }

            @Override
            public void finish() {
            }
        });

        // Explicit header from options overrides parsed header
        final List<String> effectiveHeader = options.explicitHeader != null
                ? options.explicitHeader : headerHolder[0];

        return buildParseCsvResult(effectiveHeader, allRows, options);
    }

    private Sequence buildParseCsvResult(final List<String> header, final List<List<String>> rows,
                                          final CsvParser.CsvOptions options) throws XPathException {
        final MapType result = new MapType(this, context);

        // "columns" - sequence of column names (empty sequence if no header)
        final Sequence columns;
        if (header != null) {
            final ValueSequence colSeq = new ValueSequence(header.size());
            for (final String h : header) {
                colSeq.add(new StringValue(this, h));
            }
            columns = colSeq;
        } else {
            columns = Sequence.EMPTY_SEQUENCE;
        }

        // "column-index" - map from column name to 1-based position
        // Empty names are excluded; duplicate names map to first occurrence
        final MapType columnIndex = new MapType(this, context);
        MapType colIdxResult = columnIndex;
        if (header != null) {
            final java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i = 0; i < header.size(); i++) {
                final String name = header.get(i);
                if (!name.isEmpty() && seen.add(name)) {
                    colIdxResult = (MapType) colIdxResult.put(new StringValue(this, name),
                            new IntegerValue(this, i + 1));
                }
            }
        }

        // "rows" - sequence of arrays
        final ValueSequence rowSeq = new ValueSequence(rows.size());
        for (final List<String> row : rows) {
            rowSeq.add(fieldsToArray(row));
        }

        // Build the result map
        MapType map = (MapType) result.put(new StringValue(this, "columns"), columns);
        map = (MapType) map.put(new StringValue(this, "column-index"), colIdxResult);
        map = (MapType) map.put(new StringValue(this, "rows"), rowSeq);

        // "get" - accessor function: fn($row as xs:integer, $column as item()) as xs:string
        // $column can be an integer (1-based) or a string (column name)
        final UserDefinedFunction getFunc = new UserDefinedFunction(context,
                new FunctionSignature(
                        new QName("get", Function.BUILTIN_FUNCTION_NS),
                        null,
                        new SequenceType[]{
                                new FunctionParameterSequenceType("row", Type.INTEGER, Cardinality.EXACTLY_ONE, "Row number (1-based)"),
                                new FunctionParameterSequenceType("column", Type.ITEM, Cardinality.EXACTLY_ONE, "Column number (1-based) or column name")
                        },
                        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The field value")));
        getFunc.addVariable("row");
        getFunc.addVariable("column");
        getFunc.setFunctionBody(new CsvGetExpression(context, rowSeq, header));
        final FunctionCall getCall = new FunctionCall(context, getFunc);
        getCall.setLocation(getLine(), getColumn());
        final FunctionReference getFuncRef = new FunctionReference(this, getCall);
        map = (MapType) map.put(new StringValue(this, "get"), getFuncRef);

        return map;
    }

    // ==================== fn:csv-to-xml ====================

    private Sequence evalCsvToXml(final String csv, final CsvParser.CsvOptions options) throws XPathException {
        options.validate(this);
        final CsvParser parser = new CsvParser(options, this);

        final List<String>[] headerHolder = new List[]{null};
        final List<List<String>> allRecords = new ArrayList<>();

        parser.parse(csv, new CsvParser.CsvConverter() {
            @Override
            public void header(final List<String> fields) {
                headerHolder[0] = fields;
            }

            @Override
            public void record(final List<String> fields) {
                allRecords.add(fields);
            }

            @Override
            public void finish() {
            }
        });

        // Explicit header from options overrides parsed header
        final List<String> effectiveHeader = options.explicitHeader != null
                ? options.explicitHeader : headerHolder[0];

        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();

            builder.startElement(new QName("csv", CSV_NS), null);

            // Write columns element only if headers are present
            if (effectiveHeader != null) {
                builder.startElement(new QName("columns", CSV_NS), null);
                for (final String col : effectiveHeader) {
                    builder.startElement(new QName("column", CSV_NS), null);
                    builder.characters(col);
                    builder.endElement();
                }
                builder.endElement(); // </columns>
            }

            // Write rows
            builder.startElement(new QName("rows", CSV_NS), null);
            for (final List<String> record : allRecords) {
                builder.startElement(new QName("row", CSV_NS), null);
                // A row with a single empty field is an empty row (no field elements)
                final boolean isEmptyRow = record.size() == 1 && record.get(0).isEmpty();
                if (!isEmptyRow) {
                    for (int f = 0; f < record.size(); f++) {
                        final String field = record.get(f);
                        builder.startElement(new QName("field", CSV_NS), null);
                        if (effectiveHeader != null && f < effectiveHeader.size()
                                && !effectiveHeader.get(f).isEmpty()) {
                            builder.addAttribute(new QName("column", null, null), effectiveHeader.get(f));
                        }
                        if (!field.isEmpty()) {
                            builder.characters(field);
                        }
                        builder.endElement();
                    }
                }
                builder.endElement(); // </row>
            }
            builder.endElement(); // </rows>

            builder.endElement(); // </csv>

            return builder.getDocument();
        } finally {
            context.popDocumentContext();
        }
    }

    // ==================== fn:csv-doc ====================

    private Sequence evalCsvDoc(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final String uri = args[0].getStringValue();
        final byte[] bytes = readResourceBytes(uri);
        final String csvContent = decodeWithBomDetection(bytes);

        final CsvParser.CsvOptions options = parseOptions(args);
        return evalParseCsv(csvContent, options);
    }

    /**
     * Read the bytes of a CSV resource by URI. Resolves relative URIs against
     * the static base URI (as fn:json-doc does), then falls back to SourceFactory.
     */
    private byte[] readResourceBytes(final String uri) throws XPathException {
        try {
            String url = uri;
            boolean resolvedFromBaseUri = false;
            if (url.indexOf(':') == Constants.STRING_NOT_FOUND) {
                final String resolved = resolveAgainstBaseUri(url);
                if (resolved != null && resolved.startsWith("file:")) {
                    url = resolved;
                    resolvedFromBaseUri = true;
                }
            }

            if (resolvedFromBaseUri && url.startsWith("file:")) {
                final String filePath = url.replaceFirst("^file:(?://[^/]*)?", "");
                final java.nio.file.Path path = java.nio.file.Paths.get(filePath);
                if (java.nio.file.Files.isReadable(path)) {
                    return java.nio.file.Files.readAllBytes(path);
                }
                throw new XPathException(this, ErrorCodes.FODC0002,
                        "Could not find CSV resource: " + uri);
            }

            final URI parsedUri = new URI(url);
            if (parsedUri.getFragment() != null) {
                throw new XPathException(this, ErrorCodes.FODC0005,
                        "URI may not contain a fragment identifier: " + uri);
            }
            final Source source = SourceFactory.getSource(context.getBroker(), "", parsedUri.toASCIIString(), false);
            if (source == null) {
                throw new XPathException(this, ErrorCodes.FODC0002,
                        "Could not find CSV resource: " + uri);
            }
            if (source instanceof FileSource && !context.getBroker().getCurrentSubject().hasDbaRole()) {
                throw new PermissionDeniedException("non-dba user not allowed to read from file system");
            }
            try (final InputStream is = source.getInputStream()) {
                return IOUtils.toByteArray(is);
            }
        } catch (final IOException | PermissionDeniedException | URISyntaxException e) {
            throw new XPathException(this, ErrorCodes.FODC0002,
                    "Error reading CSV resource: " + uri + " - " + e.getMessage());
        }
    }

    /**
     * Detect a Unicode BOM and decode bytes accordingly. If no BOM is present,
     * assumes UTF-8 and validates strict UTF-8. Throws FOUT1200 if the encoding
     * cannot be inferred.
     */
    private String decodeWithBomDetection(final byte[] bytes) throws XPathException {
        // UTF-32BE: 00 00 FE FF
        if (bytes.length >= 4
                && bytes[0] == 0 && bytes[1] == 0
                && bytes[2] == (byte) 0xFE && bytes[3] == (byte) 0xFF) {
            return new String(bytes, 4, bytes.length - 4, java.nio.charset.Charset.forName("UTF-32BE"));
        }
        // UTF-32LE: FF FE 00 00 (must check before UTF-16LE)
        if (bytes.length >= 4
                && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE
                && bytes[2] == 0 && bytes[3] == 0) {
            return new String(bytes, 4, bytes.length - 4, java.nio.charset.Charset.forName("UTF-32LE"));
        }
        // UTF-8 BOM: EF BB BF
        if (bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF) {
            return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        }
        // UTF-16BE BOM: FE FF
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFE && bytes[1] == (byte) 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        // UTF-16LE BOM: FF FE
        if (bytes.length >= 2 && bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        // Invalid: FF FF or other 0xFF-prefixed sequences without recognized BOM
        if (bytes.length >= 1 && bytes[0] == (byte) 0xFF) {
            throw new XPathException(this, ErrorCodes.FOUT1200,
                    "Cannot infer encoding of CSV resource (invalid byte order mark)");
        }
        // No BOM — assume UTF-8 and validate
        try {
            final java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT);
            return decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (final java.nio.charset.CharacterCodingException e) {
            throw new XPathException(this, ErrorCodes.FOUT1200,
                    "Cannot infer encoding of CSV resource: not valid UTF-8");
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
        } catch (final URISyntaxException | XPathException e) {
            // fall through
        }
        return null;
    }

    // ==================== Shared utilities ====================

    private CsvParser.CsvOptions parseOptions(final Sequence[] args) throws XPathException {
        final CsvParser.CsvOptions options = new CsvParser.CsvOptions();
        if (args.length < 2 || args[1].isEmpty()) {
            return options;
        }

        final AbstractMapType map = (AbstractMapType) args[1].itemAt(0);

        // field-delimiter
        final Sequence fdSeq = map.get(new StringValue(this, "field-delimiter"));
        if (fdSeq != null && !fdSeq.isEmpty()) {
            final String fd = fdSeq.getStringValue();
            if (fd.isEmpty()) {
                throw new XPathException(this, ErrorCodes.FOCV0002,
                        "field-delimiter must be a single character");
            }
            if (fd.codePointCount(0, fd.length()) != 1) {
                throw new XPathException(this, ErrorCodes.FOCV0002,
                        "field-delimiter must be a single character, got: \"" + fd + "\"");
            }
            options.fieldDelimiter = fd.codePointAt(0);
        }

        // row-delimiter
        final Sequence rdSeq = map.get(new StringValue(this, "row-delimiter"));
        if (rdSeq != null && !rdSeq.isEmpty()) {
            if (rdSeq.getItemCount() != 1) {
                throw new XPathException(this, ErrorCodes.FOCV0002,
                        "row-delimiter must be a single string, got " + rdSeq.getItemCount() + " items");
            }
            final String rd = rdSeq.itemAt(0).getStringValue();
            if (rd.isEmpty() || rd.codePointCount(0, rd.length()) != 1) {
                throw new XPathException(this, ErrorCodes.FOCV0002,
                        "row-delimiter must be a single character");
            }
            options.rowDelimiter = rd.codePointAt(0);
        }

        // quote-character
        final Sequence qcSeq = map.get(new StringValue(this, "quote-character"));
        if (qcSeq != null && !qcSeq.isEmpty()) {
            final String qc = qcSeq.getStringValue();
            if (qc.isEmpty()) {
                options.quoteChar = -1; // disable quoting
            } else if (qc.codePointCount(0, qc.length()) != 1) {
                throw new XPathException(this, ErrorCodes.FOCV0002,
                        "quote-character must be a single character or empty string");
            } else {
                options.quoteChar = qc.codePointAt(0);
            }
        }

        // trim-whitespace
        final Sequence twSeq = map.get(new StringValue(this, "trim-whitespace"));
        if (twSeq != null && !twSeq.isEmpty()) {
            options.trimWhitespace = twSeq.effectiveBooleanValue();
        }

        // header: boolean, "present", or sequence of explicit column names
        final Sequence hdrSeq = map.get(new StringValue(this, "header"));
        if (hdrSeq != null && !hdrSeq.isEmpty()) {
            final Item hdrItem = hdrSeq.itemAt(0);
            if (hdrItem.getType() == Type.BOOLEAN) {
                options.hasHeader = hdrItem.toSequence().effectiveBooleanValue();
            } else if (hdrSeq.getItemCount() == 1) {
                final String hdrStr = hdrItem.getStringValue();
                if ("true".equals(hdrStr) || "present".equals(hdrStr)) {
                    options.hasHeader = true;
                } else if ("false".equals(hdrStr) || "absent".equals(hdrStr)) {
                    options.hasHeader = false;
                } else {
                    // Single string → explicit column name
                    options.explicitHeader = new ArrayList<>();
                    options.explicitHeader.add(hdrStr);
                    options.hasHeader = false; // don't consume first data row
                }
            } else {
                // Multiple items → sequence of explicit column names
                options.explicitHeader = new ArrayList<>(hdrSeq.getItemCount());
                for (int j = 0; j < hdrSeq.getItemCount(); j++) {
                    options.explicitHeader.add(hdrSeq.itemAt(j).getStringValue());
                }
                options.hasHeader = false; // don't consume first data row
            }
        }

        // select-columns
        final Sequence scSeq = map.get(new StringValue(this, "select-columns"));
        if (scSeq != null && !scSeq.isEmpty()) {
            final int count = scSeq.getItemCount();
            options.selectColumns = new int[count];
            for (int j = 0; j < count; j++) {
                final int col = ((IntegerValue) scSeq.itemAt(j).convertTo(Type.INTEGER)).getInt();
                if (col < 1) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "select-columns values must be positive integers, got: " + col);
                }
                options.selectColumns[j] = col;
            }
        }

        // trim-rows
        final Sequence trSeq = map.get(new StringValue(this, "trim-rows"));
        if (trSeq != null && !trSeq.isEmpty()) {
            options.trimRows = trSeq.effectiveBooleanValue();
        }

        // Validate no unknown option keys
        final java.util.Set<String> knownKeys = java.util.Set.of(
                "field-delimiter", "row-delimiter", "quote-character",
                "trim-whitespace", "header", "select-columns", "trim-rows");
        for (final IEntry<AtomicValue, Sequence> entry : map) {
            final String key = entry.key().getStringValue();
            if (!knownKeys.contains(key)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Unknown CSV option: '" + key + "'");
            }
        }

        return options;
    }

    private ArrayType fieldsToArray(final List<String> fields) throws XPathException {
        // XQ4 spec: a row with a single empty field produces an empty array
        if (fields.size() == 1 && fields.get(0).isEmpty()) {
            return new ArrayType(this, context, new ArrayList<>());
        }
        final List<Sequence> items = new ArrayList<>(fields.size());
        for (final String field : fields) {
            items.add(new StringValue(this, field));
        }
        return new ArrayType(this, context, items);
    }

    /**
     * Expression body for the "get" accessor function in fn:parse-csv results.
     * Implements fn($row as xs:integer, $column as xs:integer) as xs:string.
     * Both row and column are 1-based indexes.
     */
    private static class CsvGetExpression extends AbstractExpression {

        private final ValueSequence rows;
        private final List<String> header;

        public CsvGetExpression(final XQueryContext context, final ValueSequence rows, final List<String> header) {
            super(context);
            this.rows = rows;
            this.header = header;
        }

        @Override
        public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
            final Sequence rowIdxSeq = context.resolveVariable("row").getValue();
            final Sequence colSeq = context.resolveVariable("column").getValue();

            if (rowIdxSeq.isEmpty() || colSeq.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            final int rowIdx = ((IntegerValue) rowIdxSeq.itemAt(0).convertTo(Type.INTEGER)).getInt();

            if (rowIdx < 1 || rowIdx > rows.getItemCount()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            // Resolve column: integer index or string name
            final Item colItem = colSeq.itemAt(0);
            final int colIdx;
            if (Type.subTypeOf(colItem.getType(), Type.INTEGER)) {
                colIdx = ((IntegerValue) colItem.convertTo(Type.INTEGER)).getInt();
            } else {
                // String column name — look up in header
                final String colName = colItem.getStringValue();
                if (header == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }
                int found = -1;
                for (int i = 0; i < header.size(); i++) {
                    if (header.get(i).equals(colName)) {
                        found = i + 1; // 1-based
                        break;
                    }
                }
                if (found == -1) {
                    return Sequence.EMPTY_SEQUENCE;
                }
                colIdx = found;
            }

            final ArrayType row = (ArrayType) rows.itemAt(rowIdx - 1);
            if (colIdx < 1 || colIdx > row.getSize()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            return row.get(colIdx - 1);
        }

        @Override
        public int returnsType() {
            return Type.STRING;
        }

        @Override
        public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
            // no-op
        }

        @Override
        public void dump(final org.exist.xquery.util.ExpressionDumper dumper) {
            dumper.display("[csv-get]");
        }

        @Override
        public String toString() {
            return "[csv-get]";
        }
    }
}
