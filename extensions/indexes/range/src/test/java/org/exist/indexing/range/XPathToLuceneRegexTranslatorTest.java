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
package org.exist.indexing.range;

import org.junit.jupiter.api.Test;

import org.apache.lucene.search.AutomatonQuery;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.TooComplexToDeterminizeException;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.util.automaton.RegExp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for XPath fn:matches pattern translation to Lucene RegExp format.
 *
 * @see org.exist.indexing.range.XPathToLuceneRegexTranslator
 */
class XPathToLuceneRegexTranslatorTest {

    @Test
    void prefixPattern() {
        assertEquals("b.*", XPathToLuceneRegexTranslator.translate("^b"));
    }

    @Test
    void prefixPatternLonger() {
        assertEquals("foo.*", XPathToLuceneRegexTranslator.translate("^foo"));
    }

    @Test
    void prefixPatternWithDigits() {
        assertEquals("some_123_thing.*", XPathToLuceneRegexTranslator.translate("^some_123_thing"));
    }

    @Test
    void suffixPattern() {
        assertEquals(".*b", XPathToLuceneRegexTranslator.translate("b$"));
    }

    @Test
    void suffixPatternLonger() {
        assertEquals(".*z", XPathToLuceneRegexTranslator.translate("z$"));
    }

    @Test
    void exactPattern() {
        assertEquals("b", XPathToLuceneRegexTranslator.translate("^b$"));
    }

    @Test
    void exactPatternLonger() {
        assertEquals("baz", XPathToLuceneRegexTranslator.translate("^baz$"));
    }

    @Test
    void anchorOnlyStart() {
        // an anchor alone matches every string
        assertEquals(".*", XPathToLuceneRegexTranslator.translate("^"));
    }

    @Test
    void anchorOnlyEnd() {
        assertEquals(".*", XPathToLuceneRegexTranslator.translate("$"));
    }

    @Test
    void anchorsOnlyMatchTheEmptyString() {
        assertEquals("", XPathToLuceneRegexTranslator.translate("^$"));
    }

    @Test
    void escapedDollarIsNotAnAnchor() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("a\\$"));
    }

    @Test
    void escapedDollarAfterAStartAnchorIsALiteral() {
        assertEquals("a\\$.*", XPathToLuceneRegexTranslator.translate("^a\\$"));
    }

    @Test
    void escapedBackslashBeforeAnEndAnchor() {
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("a\\\\$"));
    }

    @Test
    void emptyPattern() {
        assertEquals("", XPathToLuceneRegexTranslator.translate(""));
    }

    @Test
    void isTranslatablePrefix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b"));
    }

    @Test
    void isTranslatableSuffix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("b$"));
    }

    @Test
    void isTranslatableExact() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b$"));
    }

    @Test
    void isTranslatableUnanchored() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("b"));
    }

    @Test
    void isTranslatableBackref() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^(.)\\1$"));
    }

    @Test
    void isTranslatableXmlSchemaEscapeI() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\i"));
    }

    @Test
    void isTranslatableXmlSchemaEscapeC() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\c$"));
    }

    /**
     * Lucene's {@code \d} matches ASCII digits only, where XPath's matches any Unicode decimal
     * digit -- so a pattern using it must take the fallback. This test used to assert the
     * opposite; the probe that showed the divergence is recorded in the translator's Javadoc.
     */
    @Test
    void digitEscapeIsNotTranslatable() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\d+$"));
    }

    // ---- translatability: only constructs that mean the same thing in both dialects ----

    @Test
    void plainAnchoredPatternsAreTranslatable() {
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^abc$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^[abc]+x?$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^a.b$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^(ab|cd){2,3}$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^a\\.b\\-c$"));
    }

    @Test
    void unanchoredPatternsAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("abc"));
    }

    /** Lucene throws IllegalArgumentException on these; the index must not be asked. */
    @Test
    void unicodePropertiesAndBlocksAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\p{Lu}$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\P{L}+$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\p{IsBasicLatin}$"));
    }

    /** Lucene's \d is ASCII-only and its \w includes '_' and excludes non-ASCII letters. */
    @Test
    void perlStyleClassesAreNot() {
        for (final String escape : new String[] {"d", "D", "w", "W", "s", "S"}) {
            assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\" + escape + "+$"), "\\" + escape);
        }
    }

    /** Lucene has no class subtraction and reads [a-z-[aeiou]] as something else entirely. */
    @Test
    void characterClassSubtractionIsNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^[a-z-[aeiou]]+$"));
    }

    /** Lucene reads a backslash as escaping the next character, so \n would be the letter n. */
    @Test
    void whitespaceEscapesAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a\\nb$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a\\tb$"));
    }

    @Test
    void nonCapturingGroupsAndBackReferencesAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^(?:ab)+$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^(a)\\1$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\i\\c*$"));
    }

    /** XPath's and Lucene's case variants are not guaranteed to agree for non-ASCII letters. */
    @Test
    void caseInsensitiveWithNonAsciiLettersIsNot() {
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^caf\u00e9$", false));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^caf\u00e9$", true));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^cafe$", true));
    }

    // ---- '.' is rewritten to XPath's meaning, checked by running Lucene ----

    private static boolean luceneMatches(final String lucenePattern, final String input) {
        final RegExp re = new RegExp(lucenePattern, RegExp.NONE, 0);
        return new CharacterRunAutomaton(Operations.determinize(re.toAutomaton(), 10_000)).run(input);
    }

    @Test
    void dotDoesNotMatchLineEndsAfterTranslation() {
        final String lucene = XPathToLuceneRegexTranslator.translate("^a.b$");
        assertTrue(luceneMatches(lucene, "axb"));
        assertFalse(luceneMatches(lucene, "a\nb"), "XPath's . excludes LF");
        assertFalse(luceneMatches(lucene, "a\rb"), "XPath's . excludes CR");
    }

    @Test
    void escapedDotAndDotInsideAClassAreLeftAlone() {
        assertEquals("a\\.b", XPathToLuceneRegexTranslator.translate("^a\\.b$"));
        assertEquals("a[.]b", XPathToLuceneRegexTranslator.translate("^a[.]b$"));
    }

    @Test
    void anchorRewriteStillUsesDotStarForTheOpenEnd() {
        assertEquals("foo.*", XPathToLuceneRegexTranslator.translate("^foo"));
        assertTrue(luceneMatches(XPathToLuceneRegexTranslator.translate("^foo"), "foo\nbar"));
    }

    // ---- constructs added for review of #6748 ----

    @Test
    void xquery40RegexExtensionsAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\bcat\\b$"), "word boundary");
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\Bcat$"), "non-word boundary");
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^(*positive_lookahead:a)a$"), "named lookahead");
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("a(*negative_lookbehind:b)$"), "named lookbehind");
    }

    /** A letter outside the BMP is two chars; it must still be seen as a non-ASCII letter. */
    @Test
    void supplementaryLettersAreSeenWholeUnderCaseInsensitive() {
        final String deseretCapitalLongI = "^\uD801\uDC00$";
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable(deseretCapitalLongI, false));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable(deseretCapitalLongI, true));
    }

    private static boolean luceneMatchesCaseInsensitive(final String lucenePattern, final String input) {
        final RegExp re = new RegExp(lucenePattern, RegExp.NONE, RegExp.ASCII_CASE_INSENSITIVE);
        return new CharacterRunAutomaton(Operations.determinize(re.toAutomaton(), 10_000)).run(input);
    }

    /**
     * XPath's case variants of i and I include U+0130 and U+0131, which Lucene's folding does not.
     * Under the i flag the translation adds them.
     */
    @Test
    void caseInsensitiveIIncludesTheTurkishVariants() {
        final String lucene = XPathToLuceneRegexTranslator.translate("^Smith$", true);
        assertEquals("[sS\u017F]m[iI\u0130\u0131]th", lucene);
        assertTrue(luceneMatchesCaseInsensitive(lucene, "SMITH"));
        assertTrue(luceneMatchesCaseInsensitive(lucene, "sm\u0131th"));
        assertTrue(luceneMatchesCaseInsensitive(lucene, "SM\u0130TH"));
        assertEquals("Smith", XPathToLuceneRegexTranslator.translate("^Smith$", false), "without the flag nothing changes");
    }

    @Test
    void caseInsensitiveClassesCoveringIIncludeTheTurkishVariants() {
        assertEquals("[a-z\u0130\u0131\u212A\u017F]+", XPathToLuceneRegexTranslator.translate("^[a-z]+$", true));
        assertEquals("[xi\u0130\u0131]", XPathToLuceneRegexTranslator.translate("^[xi]$", true));
        assertEquals("[a-h]", XPathToLuceneRegexTranslator.translate("^[a-h]$", true), "a class not covering i is left alone");

        final String negated = XPathToLuceneRegexTranslator.translate("^[^i]$", true);
        assertEquals("[^i\u0130\u0131]", negated);
        assertFalse(luceneMatchesCaseInsensitive(negated, "\u0131"), "a negated class excludes the variants too");
        assertTrue(luceneMatchesCaseInsensitive(negated, "x"));
    }

    /** Translatable, but beyond Lucene's determinization limit: the index must decline it. */
    @Test
    void patternsLuceneCannotDeterminizeAreNotServable() {
        final String exponential = "^(a|b)*a(a|b){20}$";
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable(exponential));
        assertFalse(XPathToLuceneRegexTranslator.isServable(exponential, 0));
        assertTrue(XPathToLuceneRegexTranslator.isServable("^HAM", 0));
        assertFalse(XPathToLuceneRegexTranslator.isServable("^\\p{Lu}", 0));
    }

    /** The query is built from the automaton the servability check built, not a second one. */
    @Test
    void queryReusesTheAutomatonOfTheServabilityCheck() {
        assertTrue(XPathToLuceneRegexTranslator.isServable("^reuse.b$", 0));
        final AutomatonQuery first = (AutomatonQuery) XPathToLuceneRegexTranslator.toQuery("f", "^reuse.b$", 0);
        final AutomatonQuery second = (AutomatonQuery) XPathToLuceneRegexTranslator.toQuery("g", "^reuse.b$", 0);
        assertSame(first.getAutomaton(), second.getAutomaton());
        assertEquals("f", first.getField());
        assertEquals("g", second.getField());
        assertTrue(new CharacterRunAutomaton(first.getAutomaton()).run("reuseXb"));
        assertFalse(new CharacterRunAutomaton(first.getAutomaton()).run("reuse\nb"));
    }

    /** A pattern Lucene cannot determinize is not tried again for every node. */
    @Test
    void failureOfAPatternIsRemembered() {
        final String exponential = "^(a|b)*a(a|b){21}$";
        assertFalse(XPathToLuceneRegexTranslator.isServable(exponential, 0));
        final TooComplexToDeterminizeException first = assertThrows(TooComplexToDeterminizeException.class,
                () -> XPathToLuceneRegexTranslator.toQuery("f", exponential, 0));
        final TooComplexToDeterminizeException second = assertThrows(TooComplexToDeterminizeException.class,
                () -> XPathToLuceneRegexTranslator.toQuery("f", exponential, 0));
        assertSame(first, second);
    }

    // ---- constructs the anchor rewrite or Lucene would read differently ----

    @Test
    void topLevelAlternationIsNotTranslatable() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a|b$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^ba|ux$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^(a|b)$"), "an alternation in a group keeps its anchors");
    }

    @Test
    void anchorsInsideThePatternAreNotTranslatable() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a^b"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("a$b$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("a[^b]$"), "a negated class is not an anchor");
    }

    @Test
    void quoteIsEscapedForLucene() {
        final String lucene = XPathToLuceneRegexTranslator.translate("^\"abc\"$");
        assertEquals("\\\"abc\\\"", lucene);
        assertTrue(luceneMatches(lucene, "\"abc\""));
        assertFalse(luceneMatches(lucene, "abc"));
    }

    @Test
    void caseInsensitiveAddsTheVariantsLuceneMisses() {
        assertEquals("[kK\u212A]", XPathToLuceneRegexTranslator.translate("^k$", true));
        assertEquals("[sS\u017F]", XPathToLuceneRegexTranslator.translate("^S$", true));
        assertEquals("[a-z\u0130\u0131\u212A\u017F]", XPathToLuceneRegexTranslator.translate("^[a-z]$", true));
        assertEquals("k", XPathToLuceneRegexTranslator.translate("^k$", false));
    }

    @Test
    void luceneSyntaxKeepsLuceneOperators() {
        final RegexpQuery complement = XPathToLuceneRegexTranslator.toLuceneSyntaxQuery("f", "~(qux)", 0);
        assertEquals("~(qux)", complement.getRegexp().text());
        final RegexpQuery prefix = XPathToLuceneRegexTranslator.toLuceneSyntaxQuery("f", "^ba", RegExp.ASCII_CASE_INSENSITIVE);
        assertEquals("ba.*", prefix.getRegexp().text());
    }
}
