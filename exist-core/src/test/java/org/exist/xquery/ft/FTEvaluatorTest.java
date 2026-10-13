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

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Unit tests for the FTEvaluator sequential full-text matching engine.
 */
public class FTEvaluatorTest {

    @Test
    public void tokenizeSimple() {
        final List<String> tokens = FTEvaluator.tokenize("hello world");
        assertEquals(Arrays.asList("hello", "world"), tokens);
    }

    @Test
    public void tokenizePunctuation() {
        final List<String> tokens = FTEvaluator.tokenize("Hello, World! How's it going?");
        assertEquals(Arrays.asList("Hello", "World", "How's", "it", "going"), tokens);
    }

    @Test
    public void tokenizeEmpty() {
        assertTrue(FTEvaluator.tokenize("").isEmpty());
        assertTrue(FTEvaluator.tokenize(null).isEmpty());
        assertTrue(FTEvaluator.tokenize("   ").isEmpty());
    }

    @Test
    public void tokenizeNumbers() {
        final List<String> tokens = FTEvaluator.tokenize("abc 123 def");
        assertEquals(Arrays.asList("abc", "123", "def"), tokens);
    }

    @Test
    public void wildcardToRegexSimple() {
        // . matches any single char
        assertTrue("hXllo".matches(FTEvaluator.wildcardToRegex("h.llo", false)));
        assertFalse("hllo".matches(FTEvaluator.wildcardToRegex("h.llo", false)));
    }

    @Test
    public void wildcardToRegexStar() {
        // .* matches zero or more
        assertTrue("hello".matches(FTEvaluator.wildcardToRegex("hel.*", false)));
        assertTrue("hel".matches(FTEvaluator.wildcardToRegex("hel.*", false)));
    }

    @Test
    public void wildcardToRegexPlus() {
        // .+ matches one or more
        assertTrue("hello".matches(FTEvaluator.wildcardToRegex("hel.+", false)));
        assertFalse("hel".matches(FTEvaluator.wildcardToRegex("hel.+", false)));
    }

    @Test
    public void wildcardToRegexCaseInsensitive() {
        assertTrue("HELLO".matches(FTEvaluator.wildcardToRegex("hello", true)));
    }

    @Test
    public void mergeOptionsLocalOverrides() {
        final FTMatchOptions inherited = new FTMatchOptions();
        inherited.setCaseMode(FTMatchOptions.CaseMode.SENSITIVE);
        inherited.setLanguage("en");

        final FTMatchOptions local = new FTMatchOptions();
        local.setCaseMode(FTMatchOptions.CaseMode.INSENSITIVE);

        final FTMatchOptions merged = FTEvaluator.mergeOptions(inherited, local);
        assertEquals(FTMatchOptions.CaseMode.INSENSITIVE, merged.getCaseMode());
        assertEquals("en", merged.getLanguage()); // inherited
    }

    @Test
    public void mergeOptionsNullLocal() {
        final FTMatchOptions inherited = new FTMatchOptions();
        inherited.setCaseMode(FTMatchOptions.CaseMode.SENSITIVE);
        assertSame(inherited, FTEvaluator.mergeOptions(inherited, null));
    }

    @Test
    public void wildcardMatchInEvaluator() {
        final FTEvaluator evaluator = new FTEvaluator("hello world");
        final String regex = FTEvaluator.wildcardToRegex("hel.*", false);
        assertTrue("hel.* should match hello", "hello".matches(regex));
    }

    @Test
    public void mergeOptionsNullInherited() {
        final FTMatchOptions local = new FTMatchOptions();
        local.setCaseMode(FTMatchOptions.CaseMode.INSENSITIVE);
        assertSame(local, FTEvaluator.mergeOptions(null, local));
    }
}
