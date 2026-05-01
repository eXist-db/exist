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

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import java.util.ArrayList;
import java.util.List;

/**
 * State-machine CSV parser following the XQuery 4.0 specification.
 * Parses CSV text into records (rows) of fields using SAX-like callbacks.
 *
 * Options supported (per XQ4 spec):
 * - field-delimiter (default: comma)
 * - row-delimiter (default: CRLF/LF/CR)
 * - quote-character (default: double-quote; empty string disables quoting)
 * - trim-whitespace (default: false)
 * - header (default: false; true or "present" means first row is header)
 * - select-columns (default: all)
 * - trim-rows (default: false; removes trailing empty rows)
 */
public class CsvParser {

    private final int fieldDelimiter;
    private final int rowDelimiter;
    private final int quoteChar;
    private final boolean trimWhitespace;
    private final boolean hasHeader;
    private final int[] selectColumns;
    private final boolean trimRows;
    private final Expression expression;

    /**
     * Callback interface for CSV parsing events.
     */
    public interface CsvConverter {
        void header(List<String> fields) throws XPathException;
        void record(List<String> fields) throws XPathException;
        void finish() throws XPathException;
    }

    public CsvParser(final CsvOptions options, final Expression expression) {
        this.fieldDelimiter = options.fieldDelimiter;
        this.rowDelimiter = options.rowDelimiter;
        this.quoteChar = options.quoteChar;
        this.trimWhitespace = options.trimWhitespace;
        this.hasHeader = options.hasHeader;
        this.selectColumns = options.selectColumns;
        this.trimRows = options.trimRows;
        this.expression = expression;
    }

    /**
     * Parse CSV text, calling the converter for each record.
     */
    public void parse(final String input, final CsvConverter converter) throws XPathException {
        final ParseState ps = new ParseState();
        runStateMachine(input, ps);
        finalizeRecords(ps);
        if (trimRows) {
            trimAndNormalize(ps.allRecords);
        }
        emit(ps.allRecords, converter);
    }

    private void runStateMachine(final String input, final ParseState ps) throws XPathException {
        final int len = input.length();
        while (ps.i < len) {
            final int cp = input.codePointAt(ps.i);
            final int cpLen = Character.charCount(cp);
            switch (ps.state) {
                case 0 -> handleFieldStart(input, ps, cp, cpLen);
                case 1 -> handleUnquoted(input, ps, cp, cpLen);
                case 2 -> handleQuoted(input, ps, cp, cpLen, len);
                case 3 -> handleAfterQuoted(input, ps, cp, cpLen);
                default -> throw new IllegalStateException("Unexpected CSV parser state: " + ps.state);
            }
        }
    }

    private void handleFieldStart(final String input, final ParseState ps,
            final int cp, final int cpLen) {
        if (cp == quoteChar && quoteChar != -1) {
            ps.state = 2;
            ps.i += cpLen;
        } else if (cp == fieldDelimiter) {
            ps.currentRecord.add(finishField(ps.field));
            ps.field.setLength(0);
            ps.i += cpLen;
        } else if (isRowDelimiter(cp)) {
            endRow(ps);
            ps.i += rowDelimiterLength(input, ps.i, cp);
        } else {
            ps.field.appendCodePoint(cp);
            ps.state = 1;
            ps.i += cpLen;
        }
    }

    private void handleUnquoted(final String input, final ParseState ps,
            final int cp, final int cpLen) throws XPathException {
        if (cp == quoteChar && quoteChar != -1) {
            throw new XPathException(expression, ErrorCodes.FOCV0001,
                    "Quote character found in middle of unquoted field");
        }
        if (cp == fieldDelimiter) {
            ps.currentRecord.add(finishField(ps.field));
            ps.field.setLength(0);
            ps.state = 0;
            ps.i += cpLen;
        } else if (isRowDelimiter(cp)) {
            endRow(ps);
            ps.state = 0;
            ps.i += rowDelimiterLength(input, ps.i, cp);
        } else {
            ps.field.appendCodePoint(cp);
            ps.i += cpLen;
        }
    }

    private void handleQuoted(final String input, final ParseState ps,
            final int cp, final int cpLen, final int len) {
        if (cp != quoteChar) {
            ps.field.appendCodePoint(cp);
            ps.i += cpLen;
            return;
        }
        if (ps.i + cpLen < len && input.codePointAt(ps.i + cpLen) == quoteChar) {
            ps.field.appendCodePoint(quoteChar);
            ps.i += cpLen * 2;
        } else {
            ps.state = 3;
            ps.i += cpLen;
        }
    }

    private void handleAfterQuoted(final String input, final ParseState ps,
            final int cp, final int cpLen) throws XPathException {
        if (cp == fieldDelimiter) {
            ps.currentRecord.add(finishField(ps.field));
            ps.field.setLength(0);
            ps.state = 0;
            ps.i += cpLen;
        } else if (isRowDelimiter(cp)) {
            endRow(ps);
            ps.state = 0;
            ps.i += rowDelimiterLength(input, ps.i, cp);
        } else if (cp == ' ' || cp == '\t') {
            ps.i += cpLen;
        } else {
            throw new XPathException(expression, ErrorCodes.FOCV0001,
                    "Content after closing quote in CSV field");
        }
    }

    private void endRow(final ParseState ps) {
        ps.currentRecord.add(finishField(ps.field));
        ps.field.setLength(0);
        ps.allRecords.add(ps.currentRecord);
        ps.currentRecord = new ArrayList<>();
    }

    private void finalizeRecords(final ParseState ps) throws XPathException {
        if (ps.state == 2) {
            throw new XPathException(expression, ErrorCodes.FOCV0001,
                    "Unterminated quoted field in CSV input");
        }
        // Handle last field/record (if input doesn't end with row delimiter).
        if (!ps.currentRecord.isEmpty() || ps.state == 3) {
            ps.currentRecord.add(finishField(ps.field));
            ps.allRecords.add(ps.currentRecord);
        } else if (ps.field.length() > 0) {
            final String finished = finishField(ps.field);
            if (!finished.isEmpty()) {
                ps.currentRecord.add(finished);
                ps.allRecords.add(ps.currentRecord);
            }
        }
    }

    private static void trimAndNormalize(final List<List<String>> allRecords) {
        while (!allRecords.isEmpty() && isEmptyRow(allRecords.get(allRecords.size() - 1))) {
            allRecords.remove(allRecords.size() - 1);
        }
        if (allRecords.isEmpty()) {
            return;
        }
        final int columnCount = allRecords.get(0).size();
        for (int r = 1; r < allRecords.size(); r++) {
            final List<String> row = allRecords.get(r);
            if (row.size() > columnCount) {
                allRecords.set(r, new ArrayList<>(row.subList(0, columnCount)));
            } else {
                while (row.size() < columnCount) {
                    row.add("");
                }
            }
        }
    }

    private void emit(final List<List<String>> allRecords, final CsvConverter converter) throws XPathException {
        int startIdx = 0;
        if (hasHeader && !allRecords.isEmpty()) {
            // Headers are always trimmed (per XQ4 spec), regardless of trim-whitespace option
            final List<String> headerFields = allRecords.get(0);
            final List<String> trimmedHeader = new ArrayList<>(headerFields.size());
            for (final String h : headerFields) {
                trimmedHeader.add(h.trim());
            }
            converter.header(selectFields(trimmedHeader));
            startIdx = 1;
        }
        for (int r = startIdx; r < allRecords.size(); r++) {
            converter.record(selectFields(allRecords.get(r)));
        }
        converter.finish();
    }

    private static final class ParseState {
        final List<List<String>> allRecords = new ArrayList<>();
        List<String> currentRecord = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        // 0=field_start, 1=in_unquoted, 2=in_quoted, 3=after_quoted
        int state = 0;
        int i = 0;
    }

    private String finishField(final StringBuilder field) {
        if (trimWhitespace) {
            return field.toString().trim();
        }
        return field.toString();
    }

    private boolean isRowDelimiter(final int cp) {
        if (rowDelimiter == -1) {
            // Auto-detect: CR, LF, or CRLF
            return cp == '\n' || cp == '\r';
        }
        return cp == rowDelimiter;
    }

    private int rowDelimiterLength(final String input, final int pos, final int cp) {
        if (rowDelimiter == -1) {
            // Auto-detect: CRLF counts as one delimiter
            if (cp == '\r' && pos + 1 < input.length() && input.charAt(pos + 1) == '\n') {
                return 2;
            }
            return 1;
        }
        return Character.charCount(rowDelimiter);
    }

    private List<String> selectFields(final List<String> fields) {
        if (selectColumns == null) {
            return fields;
        }
        final List<String> selected = new ArrayList<>(selectColumns.length);
        for (final int col : selectColumns) {
            if (col >= 1 && col <= fields.size()) {
                selected.add(fields.get(col - 1));
            } else {
                selected.add("");
            }
        }
        return selected;
    }

    private static boolean isEmptyRow(final List<String> row) {
        for (final String field : row) {
            if (!field.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parsed CSV options from an XQuery map.
     */
    public static class CsvOptions {
        public int fieldDelimiter = ',';
        public int rowDelimiter = -1; // -1 = auto-detect (CR/LF/CRLF)
        public int quoteChar = '"';
        public boolean trimWhitespace = false;
        public boolean hasHeader = false;
        public List<String> explicitHeader = null; // explicit column names from options
        public int[] selectColumns = null;
        public boolean trimRows = false;

        /**
         * Validate options per the XQ4 spec.
         */
        public void validate(final Expression expression) throws XPathException {
            // Field delimiter and quote character must be different
            if (quoteChar != -1 && fieldDelimiter == quoteChar) {
                throw new XPathException(expression, ErrorCodes.FOCV0003,
                        "Field delimiter and quote character must be different");
            }
            // Field delimiter and row delimiter must be different
            if (rowDelimiter != -1 && fieldDelimiter == rowDelimiter) {
                throw new XPathException(expression, ErrorCodes.FOCV0003,
                        "Field delimiter and row delimiter must be different");
            }
            // When using auto-detect row delimiters, field delimiter can't be CR or LF
            if (rowDelimiter == -1 && (fieldDelimiter == '\n' || fieldDelimiter == '\r')) {
                throw new XPathException(expression, ErrorCodes.FOCV0003,
                        "Field delimiter conflicts with auto-detected row delimiter (CR/LF)");
            }
            // Quote character and row delimiter must be different
            if (quoteChar != -1 && rowDelimiter != -1 && quoteChar == rowDelimiter) {
                throw new XPathException(expression, ErrorCodes.FOCV0003,
                        "Quote character and row delimiter must be different");
            }
        }
    }
}
