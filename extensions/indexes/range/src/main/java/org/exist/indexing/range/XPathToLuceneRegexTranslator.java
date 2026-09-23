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

import javax.annotation.Nullable;

/**
 * Decides whether an XPath regular expression can be evaluated by Lucene's {@code RegExp} with
 * XPath semantics, and translates it when it can.
 *
 * <p>Lucene's {@code RegExp} is a different dialect from XPath's. Probed against each other on the
 * constructs users actually write, they disagree on: {@code \d} (Lucene is ASCII-only), {@code \w}
 * (Lucene includes {@code _} and excludes non-ASCII letters), {@code .} (Lucene matches a newline;
 * XPath does not), character-class subtraction (Lucene has no such syntax and misreads it), and
 * {@code \p{..}} (Lucene throws {@code IllegalArgumentException}). Case-insensitive matching in
 * Lucene folds ASCII only. Lucene also treats a backslash before any character as escaping that
 * character, so XPath's {@code \n}, {@code \r} and {@code \t} would become the letters.</p>
 *
 * <p>The rule is therefore conservative: a pattern is translatable only if every construct in it
 * means the same thing in both dialects. Anything else goes to the fallback expression, which is
 * {@code fn:matches} itself -- slower, and right. The one construct that is rewritten rather than
 * rejected is {@code .}, which becomes {@code [^\n\r]} so that the common {@code ^a.b$} stays on
 * the index.</p>
 *
 * <p>Anchors are handled as before: Lucene's {@code RegexpQuery} matches whole terms, so
 * {@code ^X} becomes {@code X.*}, {@code X$} becomes {@code .*X}, and {@code ^X$} becomes
 * {@code X}. A pattern with neither anchor is never sent to the index.</p>
 *
 * @see org.apache.lucene.search.RegexpQuery
 */
public final class XPathToLuceneRegexTranslator {

    /**
     * Escapes whose meaning differs between the two dialects, or which Lucene does not support:
     * back-references, XML name classes and their complements, Unicode properties and blocks,
     * the Perl-style classes, and the whitespace escapes Lucene would read as letters.
     */
    private static final String UNTRANSLATABLE_ESCAPES = "123456789icICpPdDwWsSnrt";

    /** What XPath's {@code .} matches, spelled for Lucene: any character except LF and CR. */
    private static final String XPATH_ANY_CHARACTER = "[^\n\r]";

    private XPathToLuceneRegexTranslator() {
    }

    /**
     * Whether a case-sensitive pattern can be served by Lucene with XPath semantics.
     *
     * @param xpathPattern the pattern
     * @return true if {@link #translate} yields an equivalent Lucene pattern
     */
    public static boolean isTranslatable(@Nullable final String xpathPattern) {
        return isTranslatable(xpathPattern, false);
    }

    /**
     * Whether a pattern can be served by Lucene with XPath semantics.
     *
     * @param xpathPattern the pattern
     * @param caseInsensitive whether the {@code i} flag is in effect; Lucene folds case for ASCII
     *   letters only, so a case-insensitive pattern containing any other letter is not translatable
     * @return true if {@link #translate} yields an equivalent Lucene pattern
     */
    public static boolean isTranslatable(@Nullable final String xpathPattern, final boolean caseInsensitive) {
        if (xpathPattern == null || xpathPattern.isEmpty()) {
            return false;
        }
        if (!xpathPattern.startsWith("^") && !xpathPattern.endsWith("$")) {
            return false;
        }
        return !containsUntranslatableConstruct(xpathPattern, caseInsensitive);
    }

    /** Walks the pattern once, tracking whether the position is inside a character class. */
    private static boolean containsUntranslatableConstruct(final String pattern, final boolean caseInsensitive) {
        boolean inClass = false;
        int i = 0;
        while (i < pattern.length()) {
            final char c = pattern.charAt(i);
            if (c == '\\') {
                if (isUntranslatableEscape(pattern, i)) {
                    return true;
                }
                i += 2;
                continue;
            }
            if (isUntranslatableAt(pattern, i, inClass) || (caseInsensitive && isNonAsciiLetter(c))) {
                return true;
            }
            inClass = nextClassState(inClass, c);
            i++;
        }
        return false;
    }

    /** A dangling backslash, or one of the escapes the two dialects read differently. */
    private static boolean isUntranslatableEscape(final String pattern, final int backslashAt) {
        return backslashAt + 1 >= pattern.length()
                || UNTRANSLATABLE_ESCAPES.indexOf(pattern.charAt(backslashAt + 1)) >= 0;
    }

    /**
     * Inside a class, a nested {@code [} is XPath's class subtraction, which Lucene misreads.
     * Outside one, {@code (?} opens a non-capturing group, which Lucene does not have.
     */
    private static boolean isUntranslatableAt(final String pattern, final int i, final boolean inClass) {
        final char c = pattern.charAt(i);
        if (inClass) {
            return c == '[';
        }
        return c == '(' && i + 1 < pattern.length() && pattern.charAt(i + 1) == '?';
    }

    private static boolean nextClassState(final boolean inClass, final char c) {
        if (inClass) {
            return c != ']';
        }
        return c == '[';
    }

    /** Lucene's case folding is ASCII-only, so any other letter defeats the {@code i} flag. */
    private static boolean isNonAsciiLetter(final char c) {
        return c > 0x7F && Character.isLetter(c);
    }

    /**
     * Translate an XPath fn:matches pattern to Lucene RegExp format.
     *
     * <p>Callers are expected to have checked {@link #isTranslatable} first.</p>
     *
     * @param xpathPattern the XPath regex pattern (e.g. "^b", "b$", "^b$")
     * @return the Lucene-equivalent pattern
     */
    public static String translate(final String xpathPattern) {
        if (xpathPattern == null || xpathPattern.isEmpty()) {
            return xpathPattern;
        }
        final boolean startsWithCaret = xpathPattern.startsWith("^");
        final boolean endsWithDollar = xpathPattern.endsWith("$");
        if (startsWithCaret && endsWithDollar) {
            // ^X$ -> X (exact match)
            if (xpathPattern.length() == 2) {
                return "";
            }
            return rewriteDots(xpathPattern.substring(1, xpathPattern.length() - 1));
        }
        if (startsWithCaret && !xpathPattern.contains("$")) {
            // ^X -> X.* (prefix)
            if (xpathPattern.length() == 1) {
                return "^";
            }
            return rewriteDots(xpathPattern.substring(1)) + ".*";
        }
        if (endsWithDollar && !xpathPattern.contains("^")) {
            // X$ -> .*X (suffix)
            if (xpathPattern.length() == 1) {
                return "$";
            }
            return ".*" + rewriteDots(xpathPattern.substring(0, xpathPattern.length() - 1));
        }
        return rewriteDots(xpathPattern);
    }

    /**
     * Replaces each unescaped {@code .} outside a character class with {@link #XPATH_ANY_CHARACTER}.
     * The {@code .*} the anchor rewrite appends is deliberately left alone: there it stands for
     * "anything at all", which is what an unanchored end means in XPath too.
     */
    private static String rewriteDots(final String body) {
        final StringBuilder out = new StringBuilder(body.length() + 8);
        boolean inClass = false;
        int i = 0;
        while (i < body.length()) {
            final char c = body.charAt(i);
            if (c == '\\' && i + 1 < body.length()) {
                out.append(c).append(body.charAt(i + 1));
                i += 2;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                out.append(c);
            } else if (c == '[') {
                inClass = true;
                out.append(c);
            } else if (c == '.') {
                out.append(XPATH_ANY_CHARACTER);
            } else {
                out.append(c);
            }
            i++;
        }
        return out.toString();
    }
}
