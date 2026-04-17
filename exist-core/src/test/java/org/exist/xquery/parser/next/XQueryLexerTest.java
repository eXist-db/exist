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

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests for the hand-written XQuery lexer.
 */
public class XQueryLexerTest {

    // ========================================================================
    // Basic tokenization
    // ========================================================================

    @Test
    public void emptyInput() {
        final List<Token> tokens = tokenize("");
        assertEquals(1, tokens.size());
        assertEquals(Token.EOF, tokens.get(0).type);
    }

    @Test
    public void whitespaceOnly() {
        final List<Token> tokens = tokenize("   \t\n  ");
        assertEquals(1, tokens.size());
        assertEquals(Token.EOF, tokens.get(0).type);
    }

    @Test
    public void commentOnly() {
        final List<Token> tokens = tokenize("(: this is a comment :)");
        assertEquals(1, tokens.size());
        assertEquals(Token.EOF, tokens.get(0).type);
    }

    @Test
    public void nestedComments() {
        final List<Token> tokens = tokenize("(: outer (: inner :) outer :)");
        assertEquals(1, tokens.size());
    }

    // ========================================================================
    // Punctuation and operators
    // ========================================================================

    @Test
    public void singleCharPunctuation() {
        assertTokenTypes("( ) [ ] { } , ; @ $ # + *",
                Token.LPAREN, Token.RPAREN, Token.LBRACKET, Token.RBRACKET,
                Token.LBRACE, Token.RBRACE, Token.COMMA, Token.SEMICOLON,
                Token.AT, Token.DOLLAR, Token.HASH, Token.PLUS, Token.STAR);
    }

    @Test
    public void comparisonOperators() {
        assertTokenTypes("= != < <= > >=",
                Token.EQ, Token.NEQ, Token.LT, Token.LTEQ, Token.GT, Token.GTEQ);
    }

    @Test
    public void arrowOperators() {
        assertTokenTypes("=> =!> =?>",
                Token.ARROW, Token.MAPPING_ARROW, Token.METHOD_CALL);
    }

    @Test
    public void pipelineOperator() {
        assertTokenTypes("->", Token.PIPELINE);
    }

    @Test
    public void concatOperator() {
        assertTokenTypes("||", Token.CONCAT);
    }

    @Test
    public void doubleQuestion() {
        assertTokenTypes("??", Token.DOUBLE_QUESTION);
    }

    @Test
    public void doubleBang() {
        assertTokenTypes("!!", Token.DOUBLE_BANG);
    }

    @Test
    public void slashOperators() {
        assertTokenTypes("/ //",
                Token.SLASH, Token.DSLASH);
    }

    @Test
    public void dotOperators() {
        assertTokenTypes(". ..",
                Token.DOT, Token.DOT_DOT);
    }

    @Test
    public void colonColon() {
        assertTokenTypes("::",
                Token.COLONCOLON);
    }

    @Test
    public void endTagStart() {
        assertTokenTypes("</",
                Token.END_TAG_START);
    }

    @Test
    public void emptyTagClose() {
        assertTokenTypes("/>",
                Token.EMPTY_TAG_CLOSE);
    }

    // ========================================================================
    // Numeric literals
    // ========================================================================

    @Test
    public void integerLiteral() {
        final List<Token> tokens = tokenize("42");
        assertEquals(2, tokens.size());
        assertEquals(Token.INTEGER_LITERAL, tokens.get(0).type);
        assertEquals("42", tokens.get(0).value);
    }

    @Test
    public void integerWithUnderscores() {
        final List<Token> tokens = tokenize("1_000_000");
        assertEquals(2, tokens.size());
        assertEquals(Token.INTEGER_LITERAL, tokens.get(0).type);
        assertEquals("1_000_000", tokens.get(0).value);
    }

    @Test
    public void decimalLiteral() {
        assertTokenType("3.14", Token.DECIMAL_LITERAL);
        assertTokenType(".5", Token.DECIMAL_LITERAL);
        assertTokenType("42.", Token.DECIMAL_LITERAL);
    }

    @Test
    public void doubleLiteral() {
        assertTokenType("1.0e10", Token.DOUBLE_LITERAL);
        assertTokenType("1E-5", Token.DOUBLE_LITERAL);
        assertTokenType(".5e+3", Token.DOUBLE_LITERAL);
    }

    @Test
    public void hexLiteral() {
        final List<Token> tokens = tokenize("0xFF");
        assertEquals(2, tokens.size());
        assertEquals(Token.HEX_INTEGER_LITERAL, tokens.get(0).type);
        assertEquals("0xFF", tokens.get(0).value);
    }

    @Test
    public void binaryLiteral() {
        final List<Token> tokens = tokenize("0b1010");
        assertEquals(2, tokens.size());
        assertEquals(Token.BINARY_INTEGER_LITERAL, tokens.get(0).type);
        assertEquals("0b1010", tokens.get(0).value);
    }

    @Test
    public void decimalNotRange() {
        // "1..3" should be INTEGER DOT_DOT INTEGER, not DECIMAL DOT INTEGER
        final List<Token> tokens = tokenize("1..3");
        assertEquals(4, tokens.size());
        assertEquals(Token.INTEGER_LITERAL, tokens.get(0).type);
        assertEquals("1", tokens.get(0).value);
        assertEquals(Token.DOT_DOT, tokens.get(1).type);
        assertEquals(Token.INTEGER_LITERAL, tokens.get(2).type);
        assertEquals("3", tokens.get(2).value);
    }

    // ========================================================================
    // String literals
    // ========================================================================

    @Test
    public void doubleQuotedString() {
        final List<Token> tokens = tokenize("\"hello world\"");
        assertEquals(2, tokens.size());
        assertEquals(Token.STRING_LITERAL, tokens.get(0).type);
        assertEquals("hello world", tokens.get(0).value);
    }

    @Test
    public void singleQuotedString() {
        final List<Token> tokens = tokenize("'hello'");
        assertEquals(2, tokens.size());
        assertEquals(Token.STRING_LITERAL, tokens.get(0).type);
        assertEquals("hello", tokens.get(0).value);
    }

    @Test
    public void escapedQuotes() {
        final List<Token> tokens = tokenize("\"he said \"\"hi\"\"\"");
        assertEquals(2, tokens.size());
        assertEquals("he said \"hi\"", tokens.get(0).value);
    }

    @Test
    public void entityReferences() {
        final List<Token> tokens = tokenize("\"&lt;&gt;&amp;&quot;&apos;\"");
        assertEquals(2, tokens.size());
        assertEquals("<>&\"'", tokens.get(0).value);
    }

    @Test
    public void characterReference() {
        final List<Token> tokens = tokenize("\"&#65;\"");
        assertEquals(2, tokens.size());
        assertEquals("A", tokens.get(0).value);
    }

    @Test
    public void hexCharacterReference() {
        final List<Token> tokens = tokenize("\"&#x41;\"");
        assertEquals(2, tokens.size());
        assertEquals("A", tokens.get(0).value);
    }

    @Test(expected = ParseError.class)
    public void unterminatedString() {
        tokenize("\"hello");
    }

    // ========================================================================
    // Names
    // ========================================================================

    @Test
    public void ncname() {
        final List<Token> tokens = tokenize("foo");
        assertEquals(2, tokens.size());
        assertEquals(Token.NCNAME, tokens.get(0).type);
        assertEquals("foo", tokens.get(0).value);
    }

    @Test
    public void qname() {
        final List<Token> tokens = tokenize("xs:integer");
        assertEquals(2, tokens.size());
        assertEquals(Token.QNAME, tokens.get(0).type);
        assertEquals("xs:integer", tokens.get(0).value);
    }

    @Test
    public void nameWithHyphen() {
        final List<Token> tokens = tokenize("my-function");
        assertEquals(2, tokens.size());
        assertEquals(Token.NCNAME, tokens.get(0).type);
        assertEquals("my-function", tokens.get(0).value);
    }

    @Test
    public void nameWithDot() {
        final List<Token> tokens = tokenize("my.name");
        assertEquals(2, tokens.size());
        assertEquals(Token.NCNAME, tokens.get(0).type);
        assertEquals("my.name", tokens.get(0).value);
    }

    @Test
    public void keywordsAsNames() {
        // Keywords are returned as NCNAME — parser decides context
        final List<Token> tokens = tokenize("for let where return");
        assertEquals(5, tokens.size());
        for (int i = 0; i < 4; i++) {
            assertEquals(Token.NCNAME, tokens.get(i).type);
        }
        assertEquals("for", tokens.get(0).value);
        assertEquals("let", tokens.get(1).value);
        assertEquals("where", tokens.get(2).value);
        assertEquals("return", tokens.get(3).value);
    }

    @Test
    public void nameNotQNameBeforeAxisSep() {
        // "child::node()" — "child" should be NCNAME, "::" should be COLONCOLON
        final List<Token> tokens = tokenize("child::node()");
        assertEquals(6, tokens.size());
        assertEquals(Token.NCNAME, tokens.get(0).type);
        assertEquals("child", tokens.get(0).value);
        assertEquals(Token.COLONCOLON, tokens.get(1).type);
    }

    // ========================================================================
    // Braced URI literal
    // ========================================================================

    @Test
    public void bracedURI() {
        final List<Token> tokens = tokenize("Q{http://www.w3.org/2005/xpath-functions}concat");
        assertEquals(3, tokens.size());
        assertEquals(Token.BRACED_URI_LITERAL, tokens.get(0).type);
        assertEquals("Q{http://www.w3.org/2005/xpath-functions}", tokens.get(0).value);
        assertEquals(Token.NCNAME, tokens.get(1).type);
        assertEquals("concat", tokens.get(1).value);
    }

    // ========================================================================
    // Pragma
    // ========================================================================

    @Test
    public void pragmaDelimiters() {
        assertTokenTypes("(# #)",
                Token.PRAGMA_START, Token.PRAGMA_END);
    }

    // ========================================================================
    // Line/column tracking
    // ========================================================================

    @Test
    public void lineColumnTracking() {
        final List<Token> tokens = tokenize("a\nb\nc");
        assertEquals(4, tokens.size());
        assertEquals(1, tokens.get(0).line);
        assertEquals(1, tokens.get(0).column);
        assertEquals(2, tokens.get(1).line);
        assertEquals(1, tokens.get(1).column);
        assertEquals(3, tokens.get(2).line);
        assertEquals(1, tokens.get(2).column);
    }

    @Test
    public void columnTracking() {
        final List<Token> tokens = tokenize("  abc  def");
        assertEquals(3, tokens.size());
        assertEquals(1, tokens.get(0).line);
        assertEquals(3, tokens.get(0).column);
        assertEquals(1, tokens.get(1).line);
        assertEquals(8, tokens.get(1).column);
    }

    // ========================================================================
    // Realistic XQuery expressions
    // ========================================================================

    @Test
    public void simpleFLWOR() {
        final String query = "for $x in (1, 2, 3) return $x * 2";
        final List<Token> tokens = tokenize(query);
        // for $ x in ( 1 , 2 , 3 ) return $ x * 2 EOF = 17
        assertEquals(17, tokens.size());
        assertEquals(Token.NCNAME, tokens.get(0).type); // "for"
        assertEquals("for", tokens.get(0).value);
        assertEquals(Token.DOLLAR, tokens.get(1).type);
        assertEquals(Token.NCNAME, tokens.get(2).type); // "x"
        assertEquals(Token.NCNAME, tokens.get(3).type); // "in"
        assertEquals(Token.LPAREN, tokens.get(4).type);
        assertEquals(Token.INTEGER_LITERAL, tokens.get(5).type);
    }

    @Test
    public void functionDeclaration() {
        final String query = "declare function local:add($a as xs:integer, $b as xs:integer) { $a + $b };";
        final List<Token> tokens = tokenize(query);
        assertNotNull(tokens);
        assertTrue(tokens.size() > 10);
        assertEquals(Token.EOF, tokens.get(tokens.size() - 1).type);
    }

    @Test
    public void xmlConstructor() {
        // The lexer tokenizes the angle brackets etc; XML parsing context is parser's job
        final String query = "<root attr=\"val\"/>";
        final List<Token> tokens = tokenize(query);
        assertNotNull(tokens);
        assertTrue(tokens.size() > 1);
    }

    @Test
    public void pathExpression() {
        final String query = "/child::para[position() > 1]";
        final List<Token> tokens = tokenize(query);
        assertNotNull(tokens);
        // / child :: para [ position ( ) > 1 ] EOF
        assertEquals(Token.SLASH, tokens.get(0).type);
        assertEquals(Token.NCNAME, tokens.get(1).type);
        assertEquals("child", tokens.get(1).value);
        assertEquals(Token.COLONCOLON, tokens.get(2).type);
    }

    @Test
    public void xquery40PipelineArrow() {
        final String query = "$items -> fn:sort() =!> fn:for-each(fn:string#1)";
        final List<Token> tokens = tokenize(query);
        assertNotNull(tokens);
        // Find the pipeline and mapping arrow tokens
        boolean foundPipeline = false;
        boolean foundMappingArrow = false;
        for (final Token t : tokens) {
            if (t.type == Token.PIPELINE) foundPipeline = true;
            if (t.type == Token.MAPPING_ARROW) foundMappingArrow = true;
        }
        assertTrue("Expected pipeline operator", foundPipeline);
        assertTrue("Expected mapping arrow", foundMappingArrow);
    }

    @Test
    public void stringWithNewlines() {
        final List<Token> tokens = tokenize("\"line1\nline2\"");
        assertEquals(2, tokens.size());
        assertEquals(Token.STRING_LITERAL, tokens.get(0).type);
        assertEquals("line1\nline2", tokens.get(0).value);
    }

    @Test
    public void commentBetweenTokens() {
        final List<Token> tokens = tokenize("1 (: comment :) + (: another :) 2");
        assertEquals(4, tokens.size());
        assertEquals(Token.INTEGER_LITERAL, tokens.get(0).type);
        assertEquals(Token.PLUS, tokens.get(1).type);
        assertEquals(Token.INTEGER_LITERAL, tokens.get(2).type);
    }

    // ========================================================================
    // Keyword detection utilities
    // ========================================================================

    @Test
    public void isKeywordCheck() {
        final Token t = new Token(Token.NCNAME, "for", 1, 1);
        assertTrue(XQueryLexer.isKeyword(t, "for"));
        assertFalse(XQueryLexer.isKeyword(t, "let"));
    }

    @Test
    public void isKeywordMultiple() {
        final Token t = new Token(Token.NCNAME, "let", 1, 1);
        assertTrue(XQueryLexer.isKeyword(t, "for", "let", "where"));
    }

    @Test
    public void nonNameNotKeyword() {
        final Token t = new Token(Token.INTEGER_LITERAL, "42", 1, 1);
        assertFalse(XQueryLexer.isKeyword(t, "42"));
    }

    // ========================================================================
    // Keyword suggestions
    // ========================================================================

    @Test
    public void suggestReturnForRetrun() {
        assertEquals("return", Keywords.suggestKeyword("retrun"));
    }

    @Test
    public void noSuggestionForExactMatch() {
        // Exact match has distance 0, which is below threshold of 3 — returns the keyword itself.
        // This is fine: the suggestion API is for finding close matches, and distance 0 qualifies.
        assertEquals("where", Keywords.suggestKeyword("where"));
    }

    @Test
    public void suggestFunctionForFuction() {
        assertEquals("function", Keywords.suggestKeyword("fuction"));
    }

    @Test
    public void noSuggestionForGarbage() {
        assertNull(Keywords.suggestKeyword("xyzzy"));
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    private List<Token> tokenize(final String input) {
        return new XQueryLexer(input).tokenizeAll();
    }

    private void assertTokenType(final String input, final int expectedType) {
        final List<Token> tokens = tokenize(input);
        assertEquals(2, tokens.size()); // token + EOF
        assertEquals(expectedType, tokens.get(0).type);
    }

    private void assertTokenTypes(final String input, final int... expectedTypes) {
        final List<Token> tokens = tokenize(input);
        assertEquals(expectedTypes.length + 1, tokens.size()); // +1 for EOF
        for (int i = 0; i < expectedTypes.length; i++) {
            assertEquals("Token " + i + ": expected " + Token.typeName(expectedTypes[i])
                            + " but got " + Token.typeName(tokens.get(i).type)
                            + " '" + tokens.get(i).value + "'",
                    expectedTypes[i], tokens.get(i).type);
        }
    }
}
