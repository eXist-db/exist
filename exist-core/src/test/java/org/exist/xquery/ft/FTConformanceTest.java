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

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * W3C XQFT 3.0 conformance tests based on spec examples and XQFTTS patterns.
 *
 * Tests are organized by spec section. Each test name includes the spec section
 * reference for traceability.
 *
 * @see <a href="https://www.w3.org/TR/xpath-full-text-30/">W3C XQFT 3.0 Spec</a>
 */
public class FTConformanceTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private Sequence executeQuery(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        final XQuery xquery = pool.getXQueryService();
        try (final DBBroker broker = pool.getBroker()) {
            return xquery.execute(broker, query, null);
        }
    }

    private boolean evalBool(final String query) throws EXistException, PermissionDeniedException, XPathException {
        final Sequence result = executeQuery(query);
        assertNotNull(result);
        assertEquals(1, result.getItemCount());
        return result.effectiveBooleanValue();
    }

    private int evalCount(final String query) throws EXistException, PermissionDeniedException, XPathException {
        return executeQuery(query).getItemCount();
    }

    private String evalString(final String query) throws EXistException, PermissionDeniedException, XPathException {
        return executeQuery(query).getStringValue();
    }

    // =========================================================================
    // §2.1 FTContainsExpr — basic "contains text" semantics
    // =========================================================================

    @Test
    public void s2_1_basicContainsText() throws Exception {
        assertTrue(evalBool("'usability testing' contains text 'usability'"));
    }

    @Test
    public void s2_1_noMatch() throws Exception {
        assertFalse(evalBool("'usability testing' contains text 'performance'"));
    }

    @Test
    public void s2_1_multiWordMatch() throws Exception {
        // Default "any" mode: each search string is treated as a phrase
        assertTrue(evalBool("'usability testing and analysis' contains text 'usability testing'"));
    }

    @Test
    public void s2_1_emptyStringAlwaysMatches() throws Exception {
        assertTrue(evalBool("'anything' contains text ''"));
    }

    @Test
    public void s2_1_xmlElement() throws Exception {
        assertTrue(evalBool("<p>The quick brown fox</p> contains text 'quick'"));
    }

    @Test
    public void s2_1_variableSource() throws Exception {
        assertTrue(evalBool("let $x := 'hello world' return $x contains text 'hello'"));
    }

    // =========================================================================
    // §2.2 FTWords — word/phrase matching with AnyallOption
    // =========================================================================

    // --- "any" (default) ---

    @Test
    public void s2_2_anyDefault() throws Exception {
        // "any" is the default; any single search string can match as a phrase
        assertTrue(evalBool("'hello world' contains text 'hello'"));
    }

    @Test
    public void s2_2_anyMultipleStrings() throws Exception {
        // With computed value producing multiple strings (must use {Expr} syntax)
        assertTrue(evalBool("'hello world' contains text {('goodbye', 'hello')}"));
    }

    // --- "any word" ---

    @Test
    public void s2_2_anyWord() throws Exception {
        // Tokenize into individual words; any one can match
        assertTrue(evalBool("'hello world' contains text 'goodbye hello' any word"));
    }

    @Test
    public void s2_2_anyWordNoMatch() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'goodbye farewell' any word"));
    }

    // --- "all" ---

    @Test
    public void s2_2_all() throws Exception {
        // All search strings must match (each as a phrase)
        assertTrue(evalBool("'hello world' contains text 'hello' all"));
    }

    @Test
    public void s2_2_allMultiple() throws Exception {
        assertTrue(evalBool("'hello world' contains text {('hello', 'world')} all"));
    }

    @Test
    public void s2_2_allFails() throws Exception {
        assertFalse(evalBool("'hello world' contains text {('hello', 'gone')} all"));
    }

    // --- "all words" ---

    @Test
    public void s2_2_allWords() throws Exception {
        // Tokenize into words; all must individually match
        assertTrue(evalBool("'the quick brown fox' contains text 'quick fox' all words"));
    }

    @Test
    public void s2_2_allWordsFail() throws Exception {
        assertFalse(evalBool("'the quick brown fox' contains text 'quick gone' all words"));
    }

    // --- "phrase" ---

    @Test
    public void s2_2_phrase() throws Exception {
        // All words form one phrase — must appear consecutively
        assertTrue(evalBool("'the quick brown fox' contains text 'quick brown' phrase"));
    }

    @Test
    public void s2_2_phraseNoMatch() throws Exception {
        // Words not consecutive
        assertFalse(evalBool("'the quick brown fox' contains text 'quick fox' phrase"));
    }

    // =========================================================================
    // §2.3 FTOr, FTAnd, FTMildNot, FTUnaryNot
    // =========================================================================

    @Test
    public void s2_3_ftor() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello' ftor 'goodbye'"));
        assertTrue(evalBool("'hello world' contains text 'goodbye' ftor 'hello'"));
        assertFalse(evalBool("'hello world' contains text 'goodbye' ftor 'farewell'"));
    }

    @Test
    public void s2_3_ftand() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello' ftand 'world'"));
        assertFalse(evalBool("'hello world' contains text 'hello' ftand 'gone'"));
    }

    @Test
    public void s2_3_ftnot() throws Exception {
        // ftnot: negation — matches if search term does NOT appear
        assertTrue(evalBool("'hello world' contains text ftnot 'gone'"));
        assertFalse(evalBool("'hello world' contains text ftnot 'hello'"));
    }

    @Test
    public void s2_3_mildNot() throws Exception {
        // "not in": matches from left that don't overlap with right positions
        // "hello" matches at pos 0, "hello" also matches at pos 0 in right operand
        // So the match DOES overlap → should be excluded
        assertFalse(evalBool("'hello world' contains text 'hello' not in 'hello'"));
    }

    @Test
    public void s2_3_mildNotNoOverlap() throws Exception {
        // "hello" at pos 0, "world" at pos 1 — no overlap
        assertTrue(evalBool("'hello world' contains text 'hello' not in 'world'"));
    }

    @Test
    public void s2_3_complexBoolean() throws Exception {
        // Nested: (A ftand B) ftor C
        assertTrue(evalBool(
            "'the quick brown fox' contains text ('quick' ftand 'fox') ftor 'elephant'"
        ));
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'elephant' ftor ('quick' ftand 'fox')"
        ));
    }

    // =========================================================================
    // §2.4 Positional Filters
    // =========================================================================

    // --- ordered ---

    @Test
    public void s2_4_ordered() throws Exception {
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'fox' ordered"
        ));
    }

    @Test
    public void s2_4_orderedReverse() throws Exception {
        // "fox" (first operand) at pos 3, "quick" (second operand) at pos 1.
        // Ordered requires first operand before second in text → 3 > 1 → fails.
        assertFalse(evalBool(
            "'the quick brown fox' contains text 'fox' ftand 'quick' ordered"
        ));
    }

    // --- window ---

    @Test
    public void s2_4_windowFits() throws Exception {
        // "quick" at pos 1, "brown" at pos 2 → span = 2, fits in window 3
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'brown' window 3 words"
        ));
    }

    @Test
    public void s2_4_windowTooSmall() throws Exception {
        // "quick" at pos 1, "fox" at pos 3 → span = 3, doesn't fit in window 2
        assertFalse(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'fox' window 2 words"
        ));
    }

    @Test
    public void s2_4_windowExact() throws Exception {
        // span = 3, window = 3 → exactly fits
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'fox' window 3 words"
        ));
    }

    // --- distance ---

    @Test
    public void s2_4_distanceExactly() throws Exception {
        // "quick" at pos 1, "brown" at pos 2 → gap = 0
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'brown' distance exactly 0 words"
        ));
    }

    @Test
    public void s2_4_distanceAtMost() throws Exception {
        // "quick" at pos 1, "fox" at pos 3 → gap = 1
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'fox' distance at most 2 words"
        ));
    }

    @Test
    public void s2_4_distanceFromTo() throws Exception {
        // gap = 1 (one word "brown" between "quick" and "fox")
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'quick' ftand 'fox' distance from 1 to 3 words"
        ));
    }

    // --- at start / at end / entire content ---

    @Test
    public void s2_4_atStart() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello' at start"));
        assertFalse(evalBool("'hello world' contains text 'world' at start"));
    }

    @Test
    public void s2_4_atEnd() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'world' at end"));
        assertFalse(evalBool("'hello world' contains text 'hello' at end"));
    }

    @Test
    public void s2_4_entireContent() throws Exception {
        assertTrue(evalBool("'hello' contains text 'hello' entire content"));
        assertFalse(evalBool("'hello world' contains text 'hello' entire content"));
    }

    @Test
    public void s2_4_entireContentAllWords() throws Exception {
        assertTrue(evalBool(
            "'hello world' contains text 'hello world' all words entire content"
        ));
    }

    // =========================================================================
    // §2.5 Match Options
    // =========================================================================

    // --- case ---

    @Test
    public void s2_5_caseSensitive() throws Exception {
        assertFalse(evalBool("'Hello World' contains text 'hello' using case sensitive"));
        assertTrue(evalBool("'Hello World' contains text 'Hello' using case sensitive"));
    }

    @Test
    public void s2_5_caseInsensitive() throws Exception {
        assertTrue(evalBool("'Hello World' contains text 'hello' using case insensitive"));
        assertTrue(evalBool("'HELLO WORLD' contains text 'hello' using case insensitive"));
    }

    @Test
    public void s2_5_lowercase() throws Exception {
        // XQFTTS interpretation: "lowercase" only matches tokens already in lowercase.
        // "Hello" is mixed case, so it doesn't match "hello" using lowercase.
        assertFalse(evalBool("'Hello World' contains text 'hello' using lowercase"));
        // All-lowercase source matches
        assertTrue(evalBool("'hello world' contains text 'hello' using lowercase"));
    }

    @Test
    public void s2_5_uppercase() throws Exception {
        // XQFTTS interpretation: "uppercase" only matches tokens already in uppercase.
        // "Hello" is mixed case, so it doesn't match "HELLO" using uppercase.
        assertFalse(evalBool("'Hello World' contains text 'HELLO' using uppercase"));
        // All-uppercase source matches
        assertTrue(evalBool("'HELLO WORLD' contains text 'HELLO' using uppercase"));
    }

    // --- wildcards ---

    @Test
    public void s2_5_wildcardsStar() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hel.*' using wildcards"));
    }

    @Test
    public void s2_5_wildcardsDot() throws Exception {
        // . matches exactly one character
        assertTrue(evalBool("'hello world' contains text 'h.llo' using wildcards"));
        assertFalse(evalBool("'hello world' contains text 'h.lo' using wildcards"));
    }

    @Test
    public void s2_5_wildcardsPlus() throws Exception {
        // .+ matches one or more
        assertTrue(evalBool("'hello world' contains text 'hel.+' using wildcards"));
        assertFalse(evalBool("'hello world' contains text 'hello.+' using wildcards"));
    }

    @Test
    public void s2_5_wildcardsCaseInsensitive() throws Exception {
        assertTrue(evalBool(
            "'Hello World' contains text 'hel.*' using wildcards using case insensitive"
        ));
    }

    // --- multiple using clauses ---

    @Test
    public void s2_5_multipleMatchOptions() throws Exception {
        assertTrue(evalBool(
            "'Hello World' contains text 'hel.*' using case insensitive using wildcards"
        ));
    }

    // =========================================================================
    // §2.6 FTTimes — occurrence constraints
    // =========================================================================

    @Test
    public void s2_6_occursExactly() throws Exception {
        // "the" appears 2 times in "the quick brown the fox"
        assertTrue(evalBool(
            "'the quick brown the fox' contains text 'the' occurs exactly 2 times"
        ));
        assertFalse(evalBool(
            "'the quick brown the fox' contains text 'the' occurs exactly 3 times"
        ));
    }

    @Test
    public void s2_6_occursAtLeast() throws Exception {
        assertTrue(evalBool(
            "'the quick the brown the fox' contains text 'the' occurs at least 2 times"
        ));
    }

    @Test
    public void s2_6_occursAtMost() throws Exception {
        assertTrue(evalBool(
            "'the quick brown fox' contains text 'the' occurs at most 2 times"
        ));
        assertFalse(evalBool(
            "'the quick the brown the fox' contains text 'the' occurs at most 1 times"
        ));
    }

    @Test
    public void s2_6_occursFromTo() throws Exception {
        assertTrue(evalBool(
            "'the quick the fox' contains text 'the' occurs from 1 to 3 times"
        ));
    }

    // =========================================================================
    // §2.7 Parenthesized FTSelection
    // =========================================================================

    @Test
    public void s2_7_parenthesizedSelection() throws Exception {
        assertTrue(evalBool(
            "'the quick brown fox' contains text ('quick' ftand 'fox')"
        ));
    }

    @Test
    public void s2_7_parenthesizedWithPosFilter() throws Exception {
        assertTrue(evalBool(
            "'the quick brown fox' contains text " +
            "('quick' ftand 'fox') using case insensitive ordered"
        ));
    }

    // =========================================================================
    // Practical use cases — XML document queries
    // =========================================================================

    @Test
    public void useCase_filterBooks() throws Exception {
        assertEquals(2, evalCount(
            "let $books := (" +
            "  <book><title>Learning XQuery</title></book>," +
            "  <book><title>Java Programming</title></book>," +
            "  <book><title>XQuery for Web Developers</title></book>" +
            ") return $books[title contains text 'XQuery']"
        ));
    }

    @Test
    public void useCase_filterWithBoolean() throws Exception {
        // 2 XQuery books + 2 Java books = 4 matches
        assertEquals(4, evalCount(
            "let $books := (" +
            "  <book><title>Learning XQuery</title></book>," +
            "  <book><title>Java Programming</title></book>," +
            "  <book><title>XQuery for Web Developers</title></book>," +
            "  <book><title>Advanced Java</title></book>" +
            ") return $books[title contains text 'XQuery' ftor 'Java']"
        ));
    }

    @Test
    public void useCase_nestedElements() throws Exception {
        // "contains text" uses the string value of the element (including descendants)
        // String value of <div><p>Hello</p><p>World</p></div> is "HelloWorld"
        assertTrue(evalBool(
            "<div><p>Hello</p><p>World</p></div> contains text 'HelloWorld'"
        ));
    }

    @Test
    public void useCase_flworFilter() throws Exception {
        assertEquals(2, evalCount(
            "for $w in ('apple', 'banana', 'apricot', 'cherry') " +
            "where $w contains text 'ap.*' using wildcards " +
            "return $w"
        ));
    }

    @Test
    public void useCase_conditionalFT() throws Exception {
        assertEquals("found", evalString(
            "if ('hello world' contains text 'hello') then 'found' else 'not found'"
        ));
    }

    @Test
    public void useCase_countMatches() throws Exception {
        // hello, help, hero, hope all start with 'h' — 4 matches
        assertEquals("4", evalString(
            "let $words := ('hello', 'world', 'help', 'hero', 'hope') " +
            "return count(for $w in $words where $w contains text 'h.*' using wildcards return $w)"
        ));
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    public void edge_emptySource() throws Exception {
        assertFalse(evalBool("'' contains text 'hello'"));
    }

    @Test
    public void edge_emptySearchEmptySource() throws Exception {
        assertTrue(evalBool("'' contains text ''"));
    }

    @Test
    public void edge_numericSource() throws Exception {
        assertTrue(evalBool("42 contains text '42'"));
    }

    @Test
    public void edge_sequenceSource() throws Exception {
        // String value of a sequence of strings is their concatenation
        assertTrue(evalBool("('hello', 'world') contains text 'hello'"));
    }

    @Test
    public void edge_multipleSpaces() throws Exception {
        // Extra whitespace shouldn't affect word tokenization
        assertTrue(evalBool("'hello   world' contains text 'hello'"));
        assertTrue(evalBool("'hello   world' contains text 'world'"));
    }

    @Test
    public void edge_punctuation() throws Exception {
        assertTrue(evalBool("'hello, world!' contains text 'hello'"));
        assertTrue(evalBool("'hello, world!' contains text 'world'"));
    }

    @Test
    public void edge_unicodeText() throws Exception {
        assertTrue(evalBool("'Stra\u00DFe und Gr\u00FC\u00DFe' contains text 'Stra\u00DFe'"));
    }

    // =========================================================================
    // XQFTTS-style tests: predicates with step expressions and positional filters
    // =========================================================================

    @Test
    public void xqftts_predicateWithDistance() throws Exception {
        // Reproduces XQFTTS FTDistance-words1: step expression "para" in predicate with distance filter
        final String query =
            "let $doc := <books><book>" +
            "<title>Book1</title>" +
            "<para>The physical swift movement</para>" +
            "</book><book>" +
            "<title>Book2</title>" +
            "<para>No match here</para>" +
            "</book></books> " +
            "return $doc/book[para contains text ('physical' ftand 'swift') distance exactly 0 words]/title/string()";
        assertEquals("Book1", evalString(query));
    }

    @Test
    public void xqftts_predicateWithWindow() throws Exception {
        final String query =
            "let $doc := <books><book>" +
            "<title>Book1</title>" +
            "<para>The physical swift movement</para>" +
            "</book></books> " +
            "return $doc/book[para contains text ('physical' ftand 'swift') window 3 words]/title/string()";
        assertEquals("Book1", evalString(query));
    }

    @Test
    public void xqftts_predicateWithOrdered() throws Exception {
        final String query =
            "let $doc := <books><book>" +
            "<title>Book1</title>" +
            "<para>The physical swift movement</para>" +
            "</book></books> " +
            "return $doc/book[para contains text 'physical' ftand 'swift' ordered]/title/string()";
        assertEquals("Book1", evalString(query));
    }

    @Test
    public void xqftts_predicateBasicFTAnd() throws Exception {
        // This pattern already works (FTAnd-q1 in XQFTTS passes)
        final String query =
            "let $doc := <books><book>" +
            "<title>Book1</title>" +
            "<para>software ninja skills</para>" +
            "</book></books> " +
            "return $doc/book[para contains text 'software' ftand 'ninja']/title/string()";
        assertEquals("Book1", evalString(query));
    }
}
