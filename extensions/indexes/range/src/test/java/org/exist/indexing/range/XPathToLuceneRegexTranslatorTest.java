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

import org.junit.Test;

import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Unit tests for XPath fn:matches pattern translation to Lucene RegExp format.
 *
 * @see org.exist.indexing.range.XPathToLuceneRegexTranslator
 */
public class XPathToLuceneRegexTranslatorTest {

    @Test
    public void prefixPattern() {
        assertEquals("b.*", XPathToLuceneRegexTranslator.translate("^b"));
    }

    @Test
    public void prefixPatternLonger() {
        assertEquals("foo.*", XPathToLuceneRegexTranslator.translate("^foo"));
    }

    @Test
    public void prefixPatternWithDigits() {
        assertEquals("some_123_thing.*", XPathToLuceneRegexTranslator.translate("^some_123_thing"));
    }

    @Test
    public void suffixPattern() {
        assertEquals(".*b", XPathToLuceneRegexTranslator.translate("b$"));
    }

    @Test
    public void suffixPatternLonger() {
        assertEquals(".*z", XPathToLuceneRegexTranslator.translate("z$"));
    }

    @Test
    public void exactPattern() {
        assertEquals("b", XPathToLuceneRegexTranslator.translate("^b$"));
    }

    @Test
    public void exactPatternLonger() {
        assertEquals("baz", XPathToLuceneRegexTranslator.translate("^baz$"));
    }

    @Test
    public void anchorOnlyStart() {
        assertEquals("^", XPathToLuceneRegexTranslator.translate("^"));
    }

    @Test
    public void anchorOnlyEnd() {
        assertEquals("$", XPathToLuceneRegexTranslator.translate("$"));
    }

    @Test
    public void emptyPattern() {
        assertEquals("", XPathToLuceneRegexTranslator.translate(""));
    }

    @Test
    public void isTranslatablePrefix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b"));
    }

    @Test
    public void isTranslatableSuffix() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("b$"));
    }

    @Test
    public void isTranslatableExact() {
        assertEquals(true, XPathToLuceneRegexTranslator.isTranslatable("^b$"));
    }

    @Test
    public void isTranslatableUnanchored() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("b"));
    }

    @Test
    public void isTranslatableBackref() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^(.)\\1$"));
    }

    @Test
    public void isTranslatableXmlSchemaEscapeI() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\i"));
    }

    @Test
    public void isTranslatableXmlSchemaEscapeC() {
        assertEquals(false, XPathToLuceneRegexTranslator.isTranslatable("^\\c$"));
    }

    /**
     * Lucene's {@code \d} matches ASCII digits only, where XPath's matches any Unicode decimal
     * digit -- so a pattern using it must take the fallback. This test used to assert the
     * opposite; the probe that showed the divergence is recorded in the translator's Javadoc.
     */
    @Test
    public void digitEscapeIsNotTranslatable() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\d+$"));
    }

    // ---- translatability: only constructs that mean the same thing in both dialects ----

    @Test
    public void plainAnchoredPatternsAreTranslatable() {
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^abc$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^[abc]+x?$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^a.b$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^(ab|cd){2,3}$"));
        assertTrue(XPathToLuceneRegexTranslator.isTranslatable("^a\\.b\\-c$"));
    }

    @Test
    public void unanchoredPatternsAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("abc"));
    }

    /** Lucene throws IllegalArgumentException on these; the index must not be asked. */
    @Test
    public void unicodePropertiesAndBlocksAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\p{Lu}$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\P{L}+$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\p{IsBasicLatin}$"));
    }

    /** Lucene's \d is ASCII-only and its \w includes '_' and excludes non-ASCII letters. */
    @Test
    public void perlStyleClassesAreNot() {
        for (final String escape : new String[] {"d", "D", "w", "W", "s", "S"}) {
            assertFalse("\\" + escape, XPathToLuceneRegexTranslator.isTranslatable("^\\" + escape + "+$"));
        }
    }

    /** Lucene has no class subtraction and reads [a-z-[aeiou]] as something else entirely. */
    @Test
    public void characterClassSubtractionIsNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^[a-z-[aeiou]]+$"));
    }

    /** Lucene reads a backslash as escaping the next character, so \n would be the letter n. */
    @Test
    public void whitespaceEscapesAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a\\nb$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^a\\tb$"));
    }

    @Test
    public void nonCapturingGroupsAndBackReferencesAreNot() {
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^(?:ab)+$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^(a)\\1$"));
        assertFalse(XPathToLuceneRegexTranslator.isTranslatable("^\\i\\c*$"));
    }

    /** Lucene folds case for ASCII letters only. */
    @Test
    public void caseInsensitiveWithNonAsciiLettersIsNot() {
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
    public void dotDoesNotMatchLineEndsAfterTranslation() {
        final String lucene = XPathToLuceneRegexTranslator.translate("^a.b$");
        assertTrue(luceneMatches(lucene, "axb"));
        assertFalse("XPath's . excludes LF", luceneMatches(lucene, "a\nb"));
        assertFalse("XPath's . excludes CR", luceneMatches(lucene, "a\rb"));
    }

    @Test
    public void escapedDotAndDotInsideAClassAreLeftAlone() {
        assertEquals("a\\.b", XPathToLuceneRegexTranslator.translate("^a\\.b$"));
        assertEquals("a[.]b", XPathToLuceneRegexTranslator.translate("^a[.]b$"));
    }

    @Test
    public void anchorRewriteStillUsesDotStarForTheOpenEnd() {
        assertEquals("foo.*", XPathToLuceneRegexTranslator.translate("^foo"));
        assertTrue(luceneMatches(XPathToLuceneRegexTranslator.translate("^foo"), "foo\nbar"));
    }
}
