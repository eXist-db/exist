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

/**
 * Translates XPath fn:matches regex patterns to Lucene RegExp format.
 *
 * <p>Lucene RegexpQuery matches whole terms implicitly; XPath anchors ^ and $
 * have different semantics. This translator converts common XPath patterns to
 * equivalent Lucene patterns.
 *
 * <p><b>Translatable</b>: Anchored patterns only (^X, X$, ^X$). Character class
 * escapes \d, \s, \w pass through (Lucene supports them).
 *
 * <p><b>Unsupported (fallback to non-index)</b>:
 * <ul>
 *   <li>Unanchored patterns (no ^ or $) – Lucene .*X.* is extremely slow</li>
 *   <li>Backreferences (\1 through \9) – Lucene automaton does not support them</li>
 *   <li>XML Schema escapes \i, \c (name chars) – Lucene has no equivalent</li>
 * </ul>
 *
 * @see org.apache.lucene.search.RegexpQuery
 */
public class XPathToLuceneRegexTranslator {

    /**
     * Returns true if the pattern can be translated to Lucene (anchored only).
     * Unanchored patterns and unsupported constructs fall back to non-index evaluation.
     */
    public static boolean isTranslatable(final String xpathPattern) {
        if (xpathPattern == null || xpathPattern.isEmpty()) {
            return false;
        }
        if (!xpathPattern.startsWith("^") && !xpathPattern.endsWith("$")) {
            return false;
        }
        return !containsUnsupportedEscape(xpathPattern);
    }

    /** Single pass: backrefs \1-\9 and XML Schema \i, \c. */
    private static boolean containsUnsupportedEscape(final String pattern) {
        for (int i = 0; i < pattern.length() - 1; i++) {
            if (pattern.charAt(i) == '\\') {
                final char c = pattern.charAt(i + 1);
                if (c >= '1' && c <= '9' || c == 'i' || c == 'c') {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Translate an XPath fn:matches pattern to Lucene RegExp format.
     *
     * @param xpathPattern the XPath regex pattern (e.g. "^b", "b$", "^b$")
     * @return the Lucene-equivalent pattern
     */
    public static String translate(final String xpathPattern) {
        // Lucene RegexpQuery matches whole terms implicitly; XPath anchors need translation
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
            return xpathPattern.substring(1, xpathPattern.length() - 1);
        }
        if (startsWithCaret && !xpathPattern.contains("$")) {
            // ^X -> X.* (prefix)
            if (xpathPattern.length() == 1) {
                return "^";
            }
            return xpathPattern.substring(1) + ".*";
        }
        if (endsWithDollar && !xpathPattern.contains("^")) {
            // X$ -> .*X (suffix)
            if (xpathPattern.length() == 1) {
                return "$";
            }
            return ".*" + xpathPattern.substring(0, xpathPattern.length() - 1);
        }
        return xpathPattern;
    }
}
