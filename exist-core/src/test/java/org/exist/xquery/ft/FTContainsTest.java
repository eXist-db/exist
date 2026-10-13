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
 * End-to-end integration tests for W3C XQFT 3.0 "contains text" expressions.
 * These tests exercise the full pipeline: parse → tree-walk → evaluate.
 */
public class FTContainsTest {

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

    // === Basic matching ===

    @Test
    public void simpleWordMatch() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello'"));
    }

    @Test
    public void simpleWordNoMatch() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'goodbye'"));
    }

    @Test
    public void caseInsensitiveByDefault() throws Exception {
        // XQFT 3.0 §4.1: default case mode is implementation-defined.
        // Our implementation defaults to case-insensitive, matching XQFTTS expectations.
        assertTrue(evalBool("'Hello World' contains text 'hello'"));
    }

    @Test
    public void caseInsensitive() throws Exception {
        assertTrue(evalBool("'Hello World' contains text 'hello' using case insensitive"));
    }

    @Test
    public void phraseMatch() throws Exception {
        assertTrue(evalBool("'the quick brown fox' contains text 'quick brown' phrase"));
    }

    @Test
    public void phraseNoMatch() throws Exception {
        assertFalse(evalBool("'the quick brown fox' contains text 'brown quick' phrase"));
    }

    // === AnyallMode ===

    @Test
    public void anyWordMode() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'goodbye hello' any word"));
    }

    @Test
    public void allWordsMode() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello world' all words"));
    }

    @Test
    public void allWordsModeFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'hello goodbye' all words"));
    }

    // === Boolean operators ===

    @Test
    public void ftand() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello' ftand 'world'"));
    }

    @Test
    public void ftandFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'hello' ftand 'goodbye'"));
    }

    @Test
    public void ftor() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'goodbye' ftor 'hello'"));
    }

    @Test
    public void ftorFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'goodbye' ftor 'farewell'"));
    }

    @Test
    public void ftnot() throws Exception {
        assertTrue(evalBool("'hello world' contains text ftnot 'goodbye'"));
    }

    @Test
    public void ftnotFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text ftnot 'hello'"));
    }

    @Test
    public void mildNot() throws Exception {
        // "hello" not in "world" — "hello" matches at pos 0, "world" matches at pos 1
        // They don't overlap, so hello's match survives
        assertTrue(evalBool("'hello world' contains text 'hello' not in 'world'"));
    }

    // === Positional filters ===

    @Test
    public void atStart() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello' at start"));
    }

    @Test
    public void atStartFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'world' at start"));
    }

    @Test
    public void atEnd() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'world' at end"));
    }

    @Test
    public void atEndFailure() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'hello' at end"));
    }

    @Test
    public void entireContent() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hello world' all words entire content"));
    }

    @Test
    public void entireContentFailure() throws Exception {
        assertFalse(evalBool("'hello world foo' contains text 'hello world' all words entire content"));
    }

    // === Window ===

    @Test
    public void windowMatch() throws Exception {
        assertTrue(evalBool("'the quick brown fox' contains text 'quick' ftand 'fox' window 4 words"));
    }

    @Test
    public void windowTooSmall() throws Exception {
        assertFalse(evalBool("'the quick brown fox' contains text 'quick' ftand 'fox' window 2 words"));
    }

    // === Distance ===

    @Test
    public void distanceMatch() throws Exception {
        // "quick" is at pos 1, "fox" at pos 3 → gap = 1 (brown is between)
        assertTrue(evalBool("'the quick brown fox' contains text 'quick' ftand 'fox' distance at most 2 words"));
    }

    @Test
    public void distanceTooFar() throws Exception {
        assertFalse(evalBool("'the quick brown fox' contains text 'quick' ftand 'fox' distance exactly 0 words"));
    }

    // === Wildcards ===

    @Test
    public void wildcards() throws Exception {
        assertTrue(evalBool("'hello world' contains text 'hel.*' using wildcards"));
    }

    @Test
    public void wildcardsNoMatch() throws Exception {
        assertFalse(evalBool("'hello world' contains text 'xyz.*' using wildcards"));
    }

    @Test
    public void wildcardLiteralPunctuation() throws Exception {
        // "task?" has no wildcard indicator (no unescaped "."), so punctuation
        // is stripped from the search token: "task?" -> "task". Source token
        // "task" matches. XQFTTS ftwildcard-q4 confirms this behavior.
        assertTrue(evalBool("'complete the task? yes' contains text 'task?' using wildcards"));
    }

    @Test
    public void wildcardEscapedDot() throws Exception {
        // "specialist\." — escaped dot matches literal period in raw token "specialist."
        // The backslash escape triggers raw token matching.
        assertTrue(evalBool("'the specialist. good' contains text 'specialist\\.' using wildcards"));
    }

    @Test
    public void wildcardDotThenEscapedQuestion() throws Exception {
        // "nex.\?" — "." matches any char, "\?" is literal ?
        // Raw token for "next?" is "next?" — pattern matches via escape-triggered raw fallback.
        assertTrue(evalBool("'what is next? ok' contains text 'nex.\\?' using wildcards"));
    }

    // === With XML nodes ===

    @Test
    public void xmlNodeMatch() throws Exception {
        assertTrue(evalBool("<title>Hello World</title> contains text 'Hello'"));
    }

    @Test
    public void xmlFilterExpression() throws Exception {
        final Sequence result = executeQuery(
            "let $books := (<book><title>XQuery in Action</title></book>," +
            "               <book><title>Java Programming</title></book>," +
            "               <book><title>XML and XQuery</title></book>)" +
            "return $books[title contains text 'XQuery']"
        );
        assertEquals(2, result.getItemCount());
    }

    // === FLWOR with contains text ===

    @Test
    public void flworWithContainsText() throws Exception {
        final Sequence result = executeQuery(
            "for $w in ('hello', 'goodbye', 'world') " +
            "where $w contains text 'hello' ftor 'world' " +
            "return $w"
        );
        assertEquals(2, result.getItemCount());
    }

    // === Case modes ===

    @Test
    public void lowercaseMode() throws Exception {
        // "using lowercase" normalizes search to lowercase, then compares case-sensitively.
        // Source "hello" matches search "hello" (both lowercase).
        assertTrue(evalBool("'hello world' contains text 'Hello' using lowercase"));
    }

    @Test
    public void lowercaseModeNoMatch() throws Exception {
        // XQFT §4.1: "using lowercase" normalizes BOTH source and search to lowercase.
        // For no-match, the actual word must differ.
        assertFalse(evalBool("'Hello World' contains text 'goodbye' using lowercase"));
    }

    @Test
    public void uppercaseMode() throws Exception {
        // XQFT §4.1: "using uppercase" normalizes BOTH source and search to uppercase.
        assertTrue(evalBool("'HELLO WORLD' contains text 'hello' using uppercase"));
    }

    @Test
    public void uppercaseModeNoMatch() throws Exception {
        // XQFT §4.1: "using uppercase" normalizes BOTH source and search to uppercase.
        // For no-match, the actual word must differ.
        assertFalse(evalBool("'Hello World' contains text 'GOODBYE' using uppercase"));
    }

    // === FTTimes ===

    @Test
    public void timesAtMostZeroOccurrences() throws Exception {
        // "goodbye" doesn't appear in "hello world", which satisfies "at most 1 times"
        assertTrue(evalBool("'hello world' contains text 'goodbye' occurs at most 1 times"));
    }

    @Test
    public void timesAtMostOneOccurrence() throws Exception {
        // "hello" appears exactly 1 time, which satisfies "at most 1 times"
        assertTrue(evalBool("'hello world' contains text 'hello' occurs at most 1 times"));
    }

    @Test
    public void timesAtMostExceeded() throws Exception {
        // "hello" appears 2 times, which does NOT satisfy "at most 1 times"
        assertFalse(evalBool("'hello hello world' contains text 'hello' occurs at most 1 times"));
    }

    // === FTOr with empty sequence ===

    @Test
    public void ftorEmptySequence() throws Exception {
        // {()} (empty sequence) produces no match; only "hello" side of ftor matches
        assertTrue(evalBool("'hello world' contains text {()} ftor 'hello'"));
    }

    @Test
    public void ftorEmptySequenceNoMatch() throws Exception {
        // {()} produces no match, and 'goodbye' doesn't match — result is false
        assertFalse(evalBool("'hello world' contains text {()} ftor 'goodbye'"));
    }

    // === XPTY0004 for non-string FTWords values ===

    @Test(expected = XPathException.class)
    public void ftWordsIntegerRaisesTypeError() throws Exception {
        evalBool("'hello world' contains text {42} ftor 'hello'");
    }

    // === Stemming ===

    @Test
    public void stemmingMatch() throws Exception {
        // "pictures" stems to same root as "picture"
        assertTrue(evalBool("'hand-drawn pictures of pages' contains text 'picture' using stemming"));
    }

    @Test
    public void stemmingNoMatch() throws Exception {
        // "tasks" stems to "task", but "picture" stems to "pictur" — no match
        assertFalse(evalBool("'tasks and training' contains text 'picture' using stemming"));
    }

    @Test
    public void stemmingVerbForms() throws Exception {
        // "performing" and "performed" should share same stem
        assertTrue(evalBool("'performing specified tasks' contains text 'performed' using stemming"));
    }

    // === declare ft-option ===

    @Test
    public void declareFtOption() throws Exception {
        assertTrue(evalBool(
            "declare ft-option using case sensitive;\n" +
            "'Hello World' contains text 'Hello'"
        ));
    }

    @Test
    public void declareFtOptionCaseSensitiveRejects() throws Exception {
        // With case sensitive declared, 'hello' (lowercase) should NOT match 'Hello'
        assertFalse(evalBool(
            "declare ft-option using case sensitive;\n" +
            "'Hello World' contains text 'hello'"
        ));
    }

    // === FTST0019: conflicting match options ===

    @Test(expected = XPathException.class)
    public void conflictingCaseOptionsInProlog() throws Exception {
        // FTST0019: conflicting case options in declare ft-option
        evalBool(
            "declare ft-option using case sensitive using case insensitive;\n" +
            "'Hello World' contains text 'Hello'"
        );
    }

    // === entire content strictness ===

    @Test
    public void entireContentRejectsPartialMatch() throws Exception {
        // "entire content" must cover ALL token positions, not just first and last
        assertFalse(evalBool(
            "'one two three four five' contains text 'one' ftand 'five' entire content"
        ));
    }

    // === FTST0001: mild not operand restrictions ===

    @Test(expected = XPathException.class)
    public void mildNotRejectsFtnotLeft() throws Exception {
        // ftnot in left operand of "not in" must raise FTST0001
        evalBool("'hello world' contains text ('hello' ftand ftnot 'x') not in 'y'");
    }

    @Test(expected = XPathException.class)
    public void mildNotRejectsFtnotRight() throws Exception {
        // ftnot in right operand of "not in" must raise FTST0001
        evalBool("'hello world' contains text 'hello' not in ('world' ftand ftnot 'x')");
    }

    @Test(expected = XPathException.class)
    public void mildNotRejectsOccurs() throws Exception {
        // "occurs" in operand of "not in" must raise FTST0001
        evalBool("'hello world' contains text 'hello' occurs exactly 1 times not in 'world'");
    }

    // === Positional filter interaction ===

    @Test
    public void orderedAfterWindowInParens() throws Exception {
        // After window collapses groups, ordered sees a single unit → vacuously true
        assertTrue(evalBool(
            "'one two three' contains text ('three' ftand 'one' window 3 words) ordered"
        ));
    }

    // === Complex distance/window interactions ===

    @Test
    public void distanceWithWindow() throws Exception {
        // Window collapses inner group to positions {2,3}; 'swift' is at position 6.
        // Distance between last of {2,3} (=3) and first of {6} (=6): 6-3-1 = 2 words gap.
        // "distance exactly 2 words" matches, so the expression is true.
        assertTrue("distance exactly 2 between window group and swift",
            evalBool("'They prefer usability studies to the swift application' contains text " +
                "('usability' ftand 'studies' window 2 words) ftand 'swift' distance exactly 2 words"));
        // With distance exactly 1, it should reject (actual gap is 2)
        assertFalse("distance exactly 1 should reject (actual gap is 2)",
            evalBool("'They prefer usability studies to the swift application' contains text " +
                "('usability' ftand 'studies' window 2 words) ftand 'swift' distance exactly 1 words"));
    }

    // === Dynamic expressions in positional filters ===

    @Test
    public void dynamicWindowSize() throws Exception {
        // Window size computed from a dynamic expression using context
        final Sequence result = executeQuery(
            "let $items := <items><item>the quick brown fox jumps</item></items>" +
            "return $items/item[. contains text 'quick' ftand 'fox' window (2 + 2) words]"
        );
        assertEquals(1, result.getItemCount());
    }

    // === contains text with comparison ===

    // === Score variables ===

    @Test
    public void forScoreVariable() throws Exception {
        // for $t score $s in expr — $s should be bound to a double in [0, 1]
        assertTrue(evalBool(
            "for $w score $s in ('hello', 'world') " +
            "where $w contains text 'hello' " +
            "return ($s ge 0.0) and ($s le 1.0)"
        ));
    }

    @Test
    public void letScoreVariable() throws Exception {
        // let score $s := expr — $s should be a double in [0, 1]
        assertTrue(evalBool(
            "let score $s := 'hello' " +
            "return ($s ge 0.0) and ($s le 1.0)"
        ));
    }

    @Test
    public void containsTextEqComparison() throws Exception {
        // "contains text" has higher precedence than "eq"
        assertFalse(evalBool(
            "'Hello World' contains text 'Hello' eq fn:false()"
        ));
    }
}
