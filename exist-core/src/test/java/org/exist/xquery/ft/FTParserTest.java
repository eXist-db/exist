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
package org.exist.xquery.ft;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import org.exist.xquery.XPathException;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTokenTypes;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.*;

/**
 * Tests that the XQFT grammar extensions parse correctly into AST nodes.
 * These tests verify Phase 1 (parser) without requiring a running database.
 */
public class FTParserTest {

    private AST parse(final String xquery) throws RecognitionException, TokenStreamException, XPathException {
        final XQueryLexer lexer = new XQueryLexer(new StringReader(xquery));
        final XQueryParser parser = new XQueryParser(lexer);
        parser.xpath();
        return parser.getAST();
    }

    private AST findToken(final AST root, final int tokenType) {
        if (root == null) return null;
        if (root.getType() == tokenType) return root;
        AST found = findToken(root.getFirstChild(), tokenType);
        if (found != null) return found;
        return findToken(root.getNextSibling(), tokenType);
    }

    @Test
    public void simpleContainsText() throws Exception {
        final AST ast = parse("$x contains text 'hello'");
        assertNotNull("AST should not be null", ast);
        final AST ftContains = findToken(ast, XQueryTokenTypes.FT_CONTAINS);
        assertNotNull("Should find FT_CONTAINS token", ftContains);
    }

    @Test
    public void ftAnd() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftand 'world'");
        assertNotNull(ast);
        assertNotNull("Should find FT_CONTAINS", findToken(ast, XQueryTokenTypes.FT_CONTAINS));
        assertNotNull("Should find FT_AND", findToken(ast, XQueryTokenTypes.FT_AND));
    }

    @Test
    public void ftOr() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftor 'world'");
        assertNotNull(ast);
        assertNotNull("Should find FT_OR", findToken(ast, XQueryTokenTypes.FT_OR));
    }

    @Test
    public void ftNot() throws Exception {
        final AST ast = parse("$x contains text ftnot 'hello'");
        assertNotNull(ast);
        assertNotNull("Should find FT_UNARY_NOT", findToken(ast, XQueryTokenTypes.FT_UNARY_NOT));
    }

    @Test
    public void ftMildNot() throws Exception {
        final AST ast = parse("$x contains text 'hello' not in 'world'");
        assertNotNull(ast);
        assertNotNull("Should find FT_MILD_NOT", findToken(ast, XQueryTokenTypes.FT_MILD_NOT));
    }

    @Test
    public void allWords() throws Exception {
        final AST ast = parse("$x contains text 'hello world' all words");
        assertNotNull(ast);
        final AST anyall = findToken(ast, XQueryTokenTypes.FT_ANYALL_OPTION);
        assertNotNull("Should find FT_ANYALL_OPTION", anyall);
        assertEquals("all words", anyall.getText());
    }

    @Test
    public void phrase() throws Exception {
        final AST ast = parse("$x contains text 'hello world' phrase");
        assertNotNull(ast);
        final AST anyall = findToken(ast, XQueryTokenTypes.FT_ANYALL_OPTION);
        assertNotNull(anyall);
        assertEquals("phrase", anyall.getText());
    }

    @Test
    public void ordered() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftand 'world' ordered");
        assertNotNull(ast);
        assertNotNull("Should find FT_ORDER", findToken(ast, XQueryTokenTypes.FT_ORDER));
    }

    @Test
    public void window() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftand 'world' window 5 words");
        assertNotNull(ast);
        assertNotNull("Should find FT_WINDOW", findToken(ast, XQueryTokenTypes.FT_WINDOW));
    }

    @Test
    public void distance() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftand 'world' distance at most 3 words");
        assertNotNull(ast);
        assertNotNull("Should find FT_DISTANCE", findToken(ast, XQueryTokenTypes.FT_DISTANCE));
    }

    @Test
    public void scope() throws Exception {
        final AST ast = parse("$x contains text 'hello' ftand 'world' same sentence");
        assertNotNull(ast);
        final AST scope = findToken(ast, XQueryTokenTypes.FT_SCOPE);
        assertNotNull("Should find FT_SCOPE", scope);
        assertEquals("same sentence", scope.getText());
    }

    @Test
    public void contentAtStart() throws Exception {
        final AST ast = parse("$x contains text 'hello' at start");
        assertNotNull(ast);
        final AST content = findToken(ast, XQueryTokenTypes.FT_CONTENT);
        assertNotNull("Should find FT_CONTENT", content);
        assertEquals("at start", content.getText());
    }

    @Test
    public void contentAtEnd() throws Exception {
        final AST ast = parse("$x contains text 'hello' at end");
        assertNotNull(ast);
        final AST content = findToken(ast, XQueryTokenTypes.FT_CONTENT);
        assertNotNull(content);
        assertEquals("at end", content.getText());
    }

    @Test
    public void entireContent() throws Exception {
        final AST ast = parse("$x contains text 'hello' entire content");
        assertNotNull(ast);
        final AST content = findToken(ast, XQueryTokenTypes.FT_CONTENT);
        assertNotNull(content);
        assertEquals("entire content", content.getText());
    }

    @Test
    public void caseOption() throws Exception {
        final AST ast = parse("$x contains text 'hello' using case insensitive");
        assertNotNull(ast);
        final AST caseOpt = findToken(ast, XQueryTokenTypes.FT_CASE_OPTION);
        assertNotNull("Should find FT_CASE_OPTION", caseOpt);
        assertEquals("insensitive", caseOpt.getText());
    }

    @Test
    public void stemmingOption() throws Exception {
        final AST ast = parse("$x contains text 'hello' using stemming");
        assertNotNull(ast);
        final AST stemOpt = findToken(ast, XQueryTokenTypes.FT_STEM_OPTION);
        assertNotNull("Should find FT_STEM_OPTION", stemOpt);
        assertEquals("stemming", stemOpt.getText());
    }

    @Test
    public void languageOption() throws Exception {
        final AST ast = parse("$x contains text 'hello' using language 'en'");
        assertNotNull(ast);
        assertNotNull("Should find FT_LANGUAGE_OPTION", findToken(ast, XQueryTokenTypes.FT_LANGUAGE_OPTION));
    }

    @Test
    public void wildcardOption() throws Exception {
        final AST ast = parse("$x contains text 'hel.*' using wildcards");
        assertNotNull(ast);
        final AST wcOpt = findToken(ast, XQueryTokenTypes.FT_WILDCARD_OPTION);
        assertNotNull("Should find FT_WILDCARD_OPTION", wcOpt);
        assertEquals("wildcards", wcOpt.getText());
    }

    @Test
    public void weightExpr() throws Exception {
        final AST ast = parse("$x contains text 'hello' weight { 2.0 }");
        assertNotNull(ast);
        assertNotNull("Should find FT_WEIGHT", findToken(ast, XQueryTokenTypes.FT_WEIGHT));
    }

    @Test
    public void occurs() throws Exception {
        final AST ast = parse("$x contains text 'hello' occurs at least 2 times");
        assertNotNull(ast);
        assertNotNull("Should find FT_TIMES", findToken(ast, XQueryTokenTypes.FT_TIMES));
    }

    @Test
    public void complexExpression() throws Exception {
        // Mix of operators, positional filters, and match options
        // Match options go on ftPrimaryWithOptions (before pos filters)
        // Pos filters go on ftSelection (after the ftOr chain)
        final AST ast = parse(
            "$x contains text ('hello' ftand 'world' all words) " +
            "using case insensitive using stemming " +
            "ordered window 10 words"
        );
        assertNotNull(ast);
        assertNotNull("Should find FT_CONTAINS", findToken(ast, XQueryTokenTypes.FT_CONTAINS));
        assertNotNull("Should find FT_AND", findToken(ast, XQueryTokenTypes.FT_AND));
        assertNotNull("Should find FT_ORDER", findToken(ast, XQueryTokenTypes.FT_ORDER));
        assertNotNull("Should find FT_WINDOW", findToken(ast, XQueryTokenTypes.FT_WINDOW));
        assertNotNull("Should find FT_CASE_OPTION", findToken(ast, XQueryTokenTypes.FT_CASE_OPTION));
        assertNotNull("Should find FT_STEM_OPTION", findToken(ast, XQueryTokenTypes.FT_STEM_OPTION));
    }

    @Test
    public void containsFunctionNotAffected() throws Exception {
        // Ensure fn:contains() still parses as a function call, not as FT
        final AST ast = parse("contains('hello world', 'hello')");
        assertNotNull(ast);
        assertNull("Should NOT find FT_CONTAINS", findToken(ast, XQueryTokenTypes.FT_CONTAINS));
    }

    @Test
    public void withoutContent() throws Exception {
        final AST ast = parse("$x contains text 'hello' without content $footnotes");
        assertNotNull(ast);
        assertNotNull("Should find FT_IGNORE_OPTION", findToken(ast, XQueryTokenTypes.FT_IGNORE_OPTION));
    }
}
