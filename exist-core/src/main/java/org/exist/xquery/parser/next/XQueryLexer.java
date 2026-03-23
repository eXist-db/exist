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

import java.util.ArrayList;
import java.util.List;

/**
 * Hand-written XQuery lexer operating directly on a Unicode codepoint array.
 *
 * <p>Design goals:
 * <ul>
 *   <li><b>Performance:</b> Direct character matching with zero per-keyword overhead.
 *       No hash table lookups per identifier (the ANTLR 2 {@code testLiterals} bottleneck).</li>
 *   <li><b>Correctness:</b> Full Unicode support via codepoint array (supplementary planes).</li>
 *   <li><b>Context-sensitivity:</b> The lexer is stateless by default; keyword recognition
 *       is deferred to the parser (XQuery keywords are context-sensitive).</li>
 * </ul>
 *
 * <p>The lexer produces a flat stream of {@link Token} objects. All identifiers are
 * emitted as {@link Token#NCNAME} or {@link Token#QNAME}. The parser determines
 * whether an identifier is a keyword based on grammatical context.
 *
 * <p>This approach eliminates the 191-keyword hash table lookup that causes the 2x
 * slowdown in ANTLR 2's generated lexer.
 */
public final class XQueryLexer {

    /** Input as Unicode codepoints (supports supplementary planes). */
    private final int[] input;

    /** Length of the input array. */
    private final int length;

    /** Current position in the input array. */
    private int pos;

    /** 1-based line number tracking. */
    private int line;

    /** 1-based column number tracking. */
    private int column;

    /** Line number at the start of the current token. */
    private int tokenLine;

    /** Column number at the start of the current token. */
    private int tokenColumn;

    /** Position at the start of the current token (for extracting text). */
    private int tokenStart;

    /**
     * Creates a lexer for the given XQuery source.
     *
     * @param source the XQuery source code
     */
    public XQueryLexer(final String source) {
        this.input = source.codePoints().toArray();
        this.length = input.length;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    /**
     * Creates a lexer operating on a pre-computed codepoint array.
     *
     * @param codepoints the input as Unicode codepoints
     */
    public XQueryLexer(final int[] codepoints) {
        this.input = codepoints;
        this.length = codepoints.length;
        this.pos = 0;
        this.line = 1;
        this.column = 1;
    }

    /**
     * Tokenizes the entire input and returns all tokens.
     * Useful for benchmarking and testing.
     *
     * @return list of all tokens including the final EOF
     */
    public List<Token> tokenizeAll() {
        final List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = nextToken();
            tokens.add(t);
        } while (t.type != Token.EOF);
        return tokens;
    }

    /**
     * Returns the next token from the input.
     *
     * <p>Skips whitespace and comments. Returns {@link Token#EOF} when
     * the input is exhausted.</p>
     *
     * @return the next token
     * @throws ParseError on lexical errors (unterminated strings, invalid characters, etc.)
     */
    public Token nextToken() {
        skipWhitespaceAndComments();

        if (pos >= length) {
            return new Token(Token.EOF, "", line, column, pos);
        }

        // Mark token start position
        tokenLine = line;
        tokenColumn = column;
        tokenStart = pos;

        final int ch = input[pos];

        // ---- Single-character tokens and multi-character operators ----
        switch (ch) {
            case '(':
                advance();
                // Pragma: (# — but NOT if followed by a name char (QName literal in function args)
                if (at('#') && !isNameStartChar(ahead(1))) {
                    advance();
                    return token(Token.PRAGMA_START, "(#");
                }
                return token(Token.LPAREN);

            case ')':
                advance();
                return token(Token.RPAREN);

            case '[':
                advance();
                return token(Token.LBRACKET);

            case ']':
                advance();
                return token(Token.RBRACKET);

            case '{':
                advance();
                return token(Token.LBRACE);

            case '}':
                advance();
                if (at('`')) {
                    advance();
                    return token(Token.STRING_CONSTRUCTOR_INTERPOLATION_END, "}`");
                }
                return token(Token.RBRACE);

            case ',':
                advance();
                return token(Token.COMMA);

            case ';':
                advance();
                return token(Token.SEMICOLON);

            case '@':
                advance();
                return token(Token.AT);

            case '$':
                advance();
                return token(Token.DOLLAR);

            case '#':
                advance();
                if (at(')')) {
                    advance();
                    return token(Token.PRAGMA_END, "#)");
                }
                return token(Token.HASH);

            case '?':
                advance();
                if (at('?')) {
                    advance();
                    return token(Token.DOUBLE_QUESTION, "??");
                }
                return token(Token.QUESTION);

            case '+':
                advance();
                return token(Token.PLUS);

            case '*':
                advance();
                return token(Token.STAR);

            case '|':
                advance();
                if (at('|')) {
                    advance();
                    return token(Token.CONCAT, "||");
                }
                return token(Token.PIPE);

            case '=':
                advance();
                if (at('>')) {
                    advance();
                    return token(Token.ARROW, "=>");
                }
                if (at('!') && ahead(1) == '>') {
                    advance();
                    advance();
                    return token(Token.MAPPING_ARROW, "=!>");
                }
                if (at('?') && ahead(1) == '>') {
                    advance();
                    advance();
                    return token(Token.METHOD_CALL, "=?>");
                }
                return token(Token.EQ);

            case '!':
                advance();
                if (at('=')) {
                    advance();
                    return token(Token.NEQ, "!=");
                }
                if (at('!')) {
                    advance();
                    return token(Token.DOUBLE_BANG, "!!");
                }
                return token(Token.BANG);

            case '<':
                advance();
                if (at('=')) {
                    advance();
                    return token(Token.LTEQ, "<=");
                }
                if (at('/')) {
                    advance();
                    return token(Token.END_TAG_START, "</");
                }
                // XML comment <!-- ... -->
                if (at('!') && ahead(1) == '-' && ahead(2) == '-') {
                    return scanXMLComment();
                }
                // CDATA <![CDATA[ ... ]]>
                if (at('!') && ahead(1) == '[') {
                    return scanCDATA();
                }
                // Processing instruction <? ... ?>
                if (at('?')) {
                    return scanXMLPI();
                }
                return token(Token.LT);

            case '>':
                advance();
                if (at('=')) {
                    advance();
                    return token(Token.GTEQ, ">=");
                }
                return token(Token.GT);

            case '/':
                advance();
                if (at('/')) {
                    advance();
                    return token(Token.DSLASH, "//");
                }
                if (at('>')) {
                    advance();
                    return token(Token.EMPTY_TAG_CLOSE, "/>");
                }
                return token(Token.SLASH);

            case '-':
                advance();
                if (at('>')) {
                    advance();
                    return token(Token.PIPELINE, "->");
                }
                return token(Token.MINUS);

            case ':':
                advance();
                if (at(':')) {
                    advance();
                    return token(Token.COLONCOLON, "::");
                }
                if (at('=')) {
                    advance();
                    return token(Token.COLON_EQ, ":=");
                }
                return token(Token.COLON);

            case '.':
                advance();
                if (at('.')) {
                    advance();
                    return token(Token.DOT_DOT, "..");
                }
                // Check for decimal literal starting with '.'
                if (pos < length && isDigit(input[pos])) {
                    // Back up and scan as number
                    pos = tokenStart;
                    column = tokenColumn;
                    return scanNumber();
                }
                return token(Token.DOT);

            // ---- String literals ----
            case '"':
            case '\'':
                return scanStringLiteral();

            // ---- String constructors and templates (backtick) ----
            case '`':
                return scanBacktick();

            // ---- Braced URI literal Q{...} ----
            case 'Q':
                if (ahead(1) == '{') {
                    return scanBracedURI();
                }
                // Fall through to name scanning
                return scanName();

            case '%':
                advance();
                return token(Token.PERCENT);

            default:
                // ---- Numeric literals ----
                if (isDigit(ch)) {
                    return scanNumber();
                }

                // ---- Names (NCName / QName) ----
                if (isNameStartChar(ch)) {
                    return scanName();
                }

                // Unknown character
                advance();
                throw new ParseError(ParseError.XPST0003,
                        "Unexpected character: " + new String(Character.toChars(ch)),
                        tokenLine, tokenColumn);
        }
    }

    // ========================================================================
    // Scanning methods
    // ========================================================================

    /**
     * Scans a string literal (double or single quoted).
     * Handles escaped quotes (doubled) and entity/character references.
     */
    private Token scanStringLiteral() {
        final int quote = input[pos];
        advance(); // consume opening quote

        final StringBuilder sb = new StringBuilder();
        while (pos < length) {
            final int ch = input[pos];
            if (ch == quote) {
                advance();
                // Escaped quote (doubled)?
                if (pos < length && input[pos] == quote) {
                    sb.appendCodePoint(quote);
                    advance();
                    continue;
                }
                // End of string
                return new Token(Token.STRING_LITERAL, sb.toString(), tokenLine, tokenColumn, pos);
            }
            if (ch == '&') {
                sb.append(scanReference());
                continue;
            }
            if (ch == '\n') {
                sb.appendCodePoint(ch);
                advance();
                newline();
                continue;
            }
            if (ch == '\r') {
                advance();
                if (pos < length && input[pos] == '\n') {
                    advance();
                }
                sb.append('\n'); // normalize CR/CRLF to LF
                newline();
                continue;
            }
            sb.appendCodePoint(ch);
            advance();
        }
        throw new ParseError(ParseError.XPST0003,
                "Unterminated string literal",
                tokenLine, tokenColumn);
    }

    /**
     * Scans an entity or character reference (&amp; ... ;).
     * Returns the resolved characters as a string.
     */
    private String scanReference() {
        advance(); // consume '&'
        if (pos >= length) {
            throw new ParseError(ParseError.XPST0003,
                    "Unterminated reference", line, column);
        }

        if (input[pos] == '#') {
            // Character reference
            advance();
            int value;
            if (pos < length && input[pos] == 'x') {
                // Hex character reference &#xHHHH;
                advance();
                final int start = pos;
                while (pos < length && isHexDigit(input[pos])) {
                    advance();
                }
                if (pos == start) {
                    throw new ParseError(ParseError.XPST0003,
                            "Empty hex character reference", line, column);
                }
                value = Integer.parseInt(codepointsToString(start, pos), 16);
            } else {
                // Decimal character reference &#DDDD;
                final int start = pos;
                while (pos < length && isDigit(input[pos])) {
                    advance();
                }
                if (pos == start) {
                    throw new ParseError(ParseError.XPST0003,
                            "Empty decimal character reference", line, column);
                }
                value = Integer.parseInt(codepointsToString(start, pos));
            }
            if (pos >= length || input[pos] != ';') {
                throw new ParseError(ParseError.XPST0003,
                        "Character reference missing closing ';'", line, column);
            }
            advance(); // consume ';'
            return new String(Character.toChars(value));
        } else {
            // Predefined entity reference
            final int start = pos;
            while (pos < length && input[pos] != ';') {
                advance();
            }
            if (pos >= length) {
                throw new ParseError(ParseError.XPST0003,
                        "Unterminated entity reference", line, column);
            }
            final String name = codepointsToString(start, pos);
            advance(); // consume ';'
            switch (name) {
                case "lt":   return "<";
                case "gt":   return ">";
                case "amp":  return "&";
                case "quot": return "\"";
                case "apos": return "'";
                default:
                    throw new ParseError(ParseError.XPST0003,
                            "Unknown entity reference: &" + name + ";",
                            line, column);
            }
        }
    }

    /**
     * Scans a numeric literal (integer, decimal, double, hex, binary).
     *
     * <p>Supports XQuery 4.0 numeric underscores (e.g., {@code 1_000_000})
     * and hex/binary integer literals ({@code 0xFF}, {@code 0b1010}).</p>
     */
    private Token scanNumber() {
        // Check for 0x (hex) or 0b (binary) prefix
        if (input[pos] == '0' && pos + 1 < length) {
            final int next = input[pos + 1];
            if (next == 'x' || next == 'X') {
                return scanHexLiteral();
            }
            if (next == 'b' || next == 'B') {
                return scanBinaryLiteral();
            }
        }

        // Scan integer part (digits with optional underscores)
        boolean hasIntPart = false;
        if (pos < length && isDigit(input[pos])) {
            scanDigitsWithUnderscores();
            hasIntPart = true;
        }

        // Check for decimal point
        boolean isDecimal = false;
        if (pos < length && input[pos] == '.') {
            // Must distinguish 1.2 (decimal) from 1..3 (range)
            if (pos + 1 < length && input[pos + 1] == '.') {
                // ".." — don't consume, it's a range operator
            } else {
                advance();
                isDecimal = true;
                // Scan fractional digits
                if (pos < length && isDigit(input[pos])) {
                    scanDigitsWithUnderscores();
                }
            }
        } else if (!hasIntPart) {
            // Started with '.', already advanced past it, scan fractional digits
            isDecimal = true;
            scanDigitsWithUnderscores();
        }

        // Check for exponent (double literal)
        if (pos < length && (input[pos] == 'e' || input[pos] == 'E')) {
            advance();
            if (pos < length && (input[pos] == '+' || input[pos] == '-')) {
                advance();
            }
            if (pos >= length || !isDigit(input[pos])) {
                throw new ParseError(ParseError.XPST0003,
                        "Invalid double literal: missing exponent digits",
                        tokenLine, tokenColumn);
            }
            scanDigitsWithUnderscores();
            return token(Token.DOUBLE_LITERAL);
        }

        if (isDecimal) {
            return token(Token.DECIMAL_LITERAL);
        }
        return token(Token.INTEGER_LITERAL);
    }

    /**
     * Scans a hexadecimal integer literal (0xHHHH).
     */
    private Token scanHexLiteral() {
        advance(); // consume '0'
        advance(); // consume 'x' or 'X'
        if (pos >= length || !isHexDigit(input[pos])) {
            throw new ParseError(ParseError.XPST0003,
                    "Invalid hex literal: expected hex digits after '0x'",
                    tokenLine, tokenColumn);
        }
        while (pos < length && (isHexDigit(input[pos]) || input[pos] == '_')) {
            advance();
        }
        return token(Token.HEX_INTEGER_LITERAL);
    }

    /**
     * Scans a binary integer literal (0b0101).
     */
    private Token scanBinaryLiteral() {
        advance(); // consume '0'
        advance(); // consume 'b' or 'B'
        if (pos >= length || (input[pos] != '0' && input[pos] != '1')) {
            throw new ParseError(ParseError.XPST0003,
                    "Invalid binary literal: expected binary digits after '0b'",
                    tokenLine, tokenColumn);
        }
        while (pos < length && (input[pos] == '0' || input[pos] == '1' || input[pos] == '_')) {
            advance();
        }
        return token(Token.BINARY_INTEGER_LITERAL);
    }

    /**
     * Scans one or more digits with optional underscore separators.
     */
    private void scanDigitsWithUnderscores() {
        while (pos < length && (isDigit(input[pos]) || input[pos] == '_')) {
            advance();
        }
    }

    /**
     * Scans an NCName or QName.
     *
     * <p>All identifiers are returned as {@link Token#NCNAME} or {@link Token#QNAME}.
     * Keyword recognition is deferred to the parser — this is the key design difference
     * from ANTLR 2, which performs a hash table lookup on every identifier.</p>
     */
    private Token scanName() {
        // Scan first NCName segment
        scanNCNameChars();
        final int firstEnd = pos;

        // Check for QName (prefix:local)
        if (pos < length && input[pos] == ':' && pos + 1 < length && isNameStartChar(input[pos + 1])) {
            // Don't consume ':' if followed by ':' (axis separator ::)
            if (input[pos + 1] != ':') {
                advance(); // consume ':'
                scanNCNameChars();
                return token(Token.QNAME);
            }
        }

        return token(Token.NCNAME);
    }

    /**
     * Consumes NCName characters (NameStartChar followed by NameChars).
     */
    private void scanNCNameChars() {
        if (pos < length && isNameStartChar(input[pos])) {
            advance();
        }
        while (pos < length && isNameChar(input[pos])) {
            advance();
        }
    }

    /**
     * Scans a braced URI literal: Q{uri}.
     */
    private Token scanBracedURI() {
        advance(); // consume 'Q'
        advance(); // consume '{'
        final int start = pos;
        while (pos < length && input[pos] != '}') {
            if (input[pos] == '\n') {
                newline();
            }
            advance();
        }
        if (pos >= length) {
            throw new ParseError(ParseError.XPST0003,
                    "Unterminated braced URI literal",
                    tokenLine, tokenColumn);
        }
        advance(); // consume '}'
        // Return the full Q{...} including delimiters
        return token(Token.BRACED_URI_LITERAL);
    }

    /**
     * Scans a backtick-delimited construct (string template or string constructor).
     */
    private Token scanBacktick() {
        advance(); // consume first '`'

        // String constructor: ``[
        if (at('`')) {
            if (ahead(1) == '[') {
                advance(); // second `
                advance(); // [
                return token(Token.STRING_CONSTRUCTOR_START, "``[");
            }
        }

        // String constructor interpolation start: `{
        if (at('{')) {
            advance();
            return token(Token.STRING_CONSTRUCTOR_INTERPOLATION_START, "`{");
        }

        // String template start (single backtick)
        return token(Token.STRING_TEMPLATE_START, "`");
    }

    /**
     * Scans an XML comment: &lt;!-- ... --&gt;.
     */
    private Token scanXMLComment() {
        // pos is past '<', at '!'
        advance(); // consume '!'
        advance(); // consume '-'
        advance(); // consume '-'
        final int start = pos;
        while (pos < length) {
            if (input[pos] == '-' && ahead(1) == '-' && ahead(2) == '>') {
                advance(); // -
                advance(); // -
                advance(); // >
                return token(Token.XML_COMMENT);
            }
            if (input[pos] == '\n') {
                newline();
            }
            advance();
        }
        throw new ParseError(ParseError.XPST0003,
                "Unterminated XML comment", tokenLine, tokenColumn);
    }

    /**
     * Scans a CDATA section: &lt;![CDATA[ ... ]]&gt;.
     */
    private Token scanCDATA() {
        // pos is past '<', at '!'
        advance(); // !
        advance(); // [
        // Verify "CDATA["
        for (final char c : new char[]{'C', 'D', 'A', 'T', 'A', '['}) {
            if (pos >= length || input[pos] != c) {
                throw new ParseError(ParseError.XPST0003,
                        "Invalid CDATA section", tokenLine, tokenColumn);
            }
            advance();
        }
        while (pos < length) {
            if (input[pos] == ']' && ahead(1) == ']' && ahead(2) == '>') {
                advance(); // ]
                advance(); // ]
                advance(); // >
                return token(Token.XML_CDATA);
            }
            if (input[pos] == '\n') {
                newline();
            }
            advance();
        }
        throw new ParseError(ParseError.XPST0003,
                "Unterminated CDATA section", tokenLine, tokenColumn);
    }

    /**
     * Scans an XML processing instruction: &lt;? ... ?&gt;.
     */
    private Token scanXMLPI() {
        // pos is past '<', at '?'
        advance(); // consume '?'
        while (pos < length) {
            if (input[pos] == '?' && ahead(1) == '>') {
                advance(); // ?
                advance(); // >
                return token(Token.XML_PI);
            }
            if (input[pos] == '\n') {
                newline();
            }
            advance();
        }
        throw new ParseError(ParseError.XPST0003,
                "Unterminated processing instruction", tokenLine, tokenColumn);
    }

    // ========================================================================
    // Whitespace and comment handling
    // ========================================================================

    /**
     * Skips whitespace and XQuery comments ({@code (: ... :)}).
     * Comments may be nested.
     */
    private void skipWhitespaceAndComments() {
        while (pos < length) {
            final int ch = input[pos];
            if (ch == ' ' || ch == '\t') {
                advance();
            } else if (ch == '\n') {
                advance();
                newline();
            } else if (ch == '\r') {
                advance();
                if (pos < length && input[pos] == '\n') {
                    advance();
                }
                newline();
            } else if (ch == '(' && ahead(1) == ':') {
                skipComment();
            } else {
                break;
            }
        }
    }

    /**
     * Skips an XQuery comment, handling nesting.
     * Assumes current position is at '(' with ':' following.
     */
    private void skipComment() {
        advance(); // consume '('
        advance(); // consume ':'

        // Check for XQDoc comment (:~ ... :)
        // For now, skip it like any other comment.
        // TODO: capture XQDoc comments and attach to following declarations

        int depth = 1;
        while (pos < length && depth > 0) {
            if (input[pos] == '(' && ahead(1) == ':') {
                advance();
                advance();
                depth++;
            } else if (input[pos] == ':' && ahead(1) == ')') {
                advance();
                advance();
                depth--;
            } else {
                if (input[pos] == '\n') {
                    newline();
                } else if (input[pos] == '\r') {
                    if (ahead(1) == '\n') {
                        advance();
                    }
                    newline();
                }
                advance();
            }
        }
        if (depth > 0) {
            throw new ParseError(ParseError.XPST0003,
                    "Unterminated comment", tokenLine, tokenColumn);
        }
    }

    // ========================================================================
    // Character classification (XML Name production, Unicode-aware)
    // ========================================================================

    /**
     * Tests whether a codepoint is an XML NameStartChar (excluding ':').
     * See XML 1.0 §2.3, excluding the colon which is handled separately for QNames.
     */
    static boolean isNameStartChar(final int cp) {
        return (cp >= 'A' && cp <= 'Z')
                || cp == '_'
                || (cp >= 'a' && cp <= 'z')
                || (cp >= 0xC0 && cp <= 0xD6)
                || (cp >= 0xD8 && cp <= 0xF6)
                || (cp >= 0xF8 && cp <= 0x2FF)
                || (cp >= 0x370 && cp <= 0x37D)
                || (cp >= 0x37F && cp <= 0x1FFF)
                || (cp >= 0x200C && cp <= 0x200D)
                || (cp >= 0x2070 && cp <= 0x218F)
                || (cp >= 0x2C00 && cp <= 0x2FEF)
                || (cp >= 0x3001 && cp <= 0xD7FF)
                || (cp >= 0xF900 && cp <= 0xFDCF)
                || (cp >= 0xFDF0 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0xEFFFF);
    }

    /**
     * Tests whether a codepoint is an XML NameChar (excluding ':').
     */
    static boolean isNameChar(final int cp) {
        return isNameStartChar(cp)
                || cp == '-'
                || cp == '.'
                || (cp >= '0' && cp <= '9')
                || cp == 0xB7
                || (cp >= 0x0300 && cp <= 0x036F)
                || (cp >= 0x203F && cp <= 0x2040);
    }

    /**
     * Tests whether a codepoint is an ASCII digit.
     */
    static boolean isDigit(final int cp) {
        return cp >= '0' && cp <= '9';
    }

    /**
     * Tests whether a codepoint is a hexadecimal digit.
     */
    static boolean isHexDigit(final int cp) {
        return (cp >= '0' && cp <= '9')
                || (cp >= 'a' && cp <= 'f')
                || (cp >= 'A' && cp <= 'F');
    }

    // ========================================================================
    // Position management
    // ========================================================================

    /**
     * Advances position by one codepoint, updating column.
     */
    private void advance() {
        pos++;
        column++;
    }

    /**
     * Resets line tracking after a newline.
     */
    private void newline() {
        line++;
        column = 1;
    }

    /**
     * Returns true if the current position has the given codepoint.
     */
    private boolean at(final int cp) {
        return pos < length && input[pos] == cp;
    }

    /**
     * Looks ahead n positions from current. Returns 0 if out of bounds.
     */
    private int ahead(final int n) {
        final int idx = pos + n;
        return idx < length ? input[idx] : 0;
    }

    /**
     * Returns the source text from tokenStart to current position.
     */
    private String tokenText() {
        return codepointsToString(tokenStart, pos);
    }

    /**
     * Converts a slice of the codepoint array to a String.
     */
    private String codepointsToString(final int start, final int end) {
        final StringBuilder sb = new StringBuilder(end - start);
        for (int i = start; i < end; i++) {
            sb.appendCodePoint(input[i]);
        }
        return sb.toString();
    }

    /**
     * Creates a token with the text extracted from tokenStart to current position.
     */
    private Token token(final int type) {
        return new Token(type, tokenText(), tokenLine, tokenColumn, pos);
    }

    /**
     * Creates a token with explicit text.
     */
    private Token token(final int type, final String text) {
        return new Token(type, text, tokenLine, tokenColumn, pos);
    }

    // ========================================================================
    // Keyword matching utilities (for use by the parser)
    // ========================================================================

    /**
     * Checks whether a token's value matches a keyword string.
     * This is used by the parser for context-sensitive keyword recognition.
     *
     * @param token   the token to check
     * @param keyword the keyword to match against
     * @return true if the token is an NCNAME with the given keyword value
     */
    // ========================================================================
    // Raw character access (for parser-driven XML mode scanning)
    // ========================================================================

    /**
     * Returns the current lexer position in the codepoint array.
     * Used by the parser for XML mode character-level scanning.
     */
    public int getPosition() { return pos; }

    /** Sets the lexer position (for parser-driven scanning). */
    public void setPosition(final int newPos) { this.pos = newPos; }

    /** Returns current line number. */
    public int getLine() { return line; }

    /** Returns current column number. */
    public int getColumn() { return column; }

    /** Sets line/column tracking. */
    public void setLineColumn(final int line, final int column) {
        this.line = line;
        this.column = column;
    }

    /** Returns the codepoint at the given absolute position, or 0 if out of bounds. */
    public int charAt(final int index) {
        return index < length ? input[index] : 0;
    }

    /** Returns the input length. */
    public int getLength() { return length; }

    /** Extracts a substring from the codepoint array. */
    public String substring(final int start, final int end) {
        return codepointsToString(start, end);
    }

    public static boolean isKeyword(final Token token, final String keyword) {
        return token.type == Token.NCNAME && keyword.equals(token.value);
    }

    /**
     * Checks whether a token matches any of the given keywords.
     *
     * @param token    the token to check
     * @param keywords the keywords to match against
     * @return true if the token is an NCNAME matching any keyword
     */
    public static boolean isKeyword(final Token token, final String... keywords) {
        if (token.type != Token.NCNAME) {
            return false;
        }
        for (final String kw : keywords) {
            if (kw.equals(token.value)) {
                return true;
            }
        }
        return false;
    }
}
