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
package org.exist.xquery.parser.next;

import javax.annotation.Nullable;

/**
 * Rich parse error with context-aware messaging.
 *
 * <p>Unlike ANTLR 2's generic "unexpected token" errors, this class provides:
 * <ul>
 *   <li>Exact line and column of the error</li>
 *   <li>What was expected vs what was found</li>
 *   <li>Suggestions for common typos (e.g., "Did you mean 'return'?")</li>
 *   <li>Context-aware messages (e.g., "Missing 'return' clause in FLWOR expression")</li>
 * </ul>
 */
public final class ParseError extends RuntimeException {

    /** Error code for unexpected token. */
    public static final String XPST0003 = "XPST0003";

    /** Error code for unexpected end of input. */
    public static final String XPST0003_EOF = "XPST0003";

    private final String errorCode;
    private final int line;
    private final int column;
    private final @Nullable String expected;
    private final @Nullable String found;
    private final @Nullable String suggestion;

    /**
     * Creates a parse error with full context.
     *
     * @param errorCode  XQuery error code (e.g., XPST0003)
     * @param message    human-readable error message
     * @param line       1-based line number
     * @param column     1-based column number
     * @param expected   what the parser expected (may be null)
     * @param found      what was actually found (may be null)
     * @param suggestion a suggestion for fixing the error (may be null)
     */
    public ParseError(final String errorCode, final String message,
                      final int line, final int column,
                      @Nullable final String expected,
                      @Nullable final String found,
                      @Nullable final String suggestion) {
        super(message);
        this.errorCode = errorCode;
        this.line = line;
        this.column = column;
        this.expected = expected;
        this.found = found;
        this.suggestion = suggestion;
    }

    /**
     * Creates a simple parse error without expected/found/suggestion.
     */
    public ParseError(final String errorCode, final String message,
                      final int line, final int column) {
        this(errorCode, message, line, column, null, null, null);
    }

    /**
     * Creates a parse error for an unexpected token.
     *
     * @param line     line number
     * @param column   column number
     * @param expected description of what was expected
     * @param found    what was found instead
     * @return a new ParseError
     */
    public static ParseError unexpected(final int line, final int column,
                                        final String expected, final String found) {
        final String msg = "Expected " + expected + " but found " + found;
        return new ParseError(XPST0003, msg, line, column, expected, found, null);
    }

    /**
     * Creates a parse error for an unexpected token, with a typo suggestion.
     *
     * @param line       line number
     * @param column     column number
     * @param expected   description of what was expected
     * @param found      what was found instead
     * @param suggestion the suggested correction
     * @return a new ParseError
     */
    public static ParseError unexpectedWithSuggestion(final int line, final int column,
                                                       final String expected, final String found,
                                                       final String suggestion) {
        final String msg = "Expected " + expected + " but found " + found
                + ". Did you mean '" + suggestion + "'?";
        return new ParseError(XPST0003, msg, line, column, expected, found, suggestion);
    }

    /**
     * Creates a parse error for unexpected end of input.
     *
     * @param line     line number at EOF
     * @param column   column number at EOF
     * @param expected description of what was expected
     * @return a new ParseError
     */
    public static ParseError unexpectedEOF(final int line, final int column,
                                           final String expected) {
        final String msg = "Unexpected end of input; expected " + expected;
        return new ParseError(XPST0003_EOF, msg, line, column, expected, "end of input", null);
    }

    public String getErrorCode() {
        return errorCode;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    @Nullable
    public String getExpected() {
        return expected;
    }

    @Nullable
    public String getFound() {
        return found;
    }

    @Nullable
    public String getSuggestion() {
        return suggestion;
    }

    /**
     * Returns a formatted error message suitable for display.
     */
    public String getFormattedMessage() {
        final StringBuilder sb = new StringBuilder();
        sb.append('[').append(errorCode).append("] ");
        sb.append("line ").append(line).append(", column ").append(column).append(": ");
        sb.append(getMessage());
        if (suggestion != null) {
            sb.append(" (did you mean '").append(suggestion).append("'?)");
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return getFormattedMessage();
    }
}
