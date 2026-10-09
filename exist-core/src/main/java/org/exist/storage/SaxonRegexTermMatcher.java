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
package org.exist.storage;

import net.sf.saxon.regex.RegularExpression;
import net.sf.saxon.str.StringView;

/**
 * Matches indexed terms against a compiled Saxon regular expression.
 *
 * <p>This is the index-scan counterpart of the value path in {@code fn:matches}: the same
 * {@link RegularExpression}, compiled once by {@code SaxonRegex} and cached there, is applied to
 * every candidate term, so a predicate over a range-indexed element gives the same answer as the
 * same pattern applied to a string. Before, the scan ran the pattern through Saxon's Java
 * translator into {@code java.util.regex}, whose reading of {@code \s}, {@code \d}, {@code \w},
 * {@code $}, {@code .}, character-class subtraction and block escapes differs from XPath's --
 * class subtraction silently so, since Java reads it as a union.</p>
 *
 * <p>Saxon matches over a {@code UnicodeString}; the index hands terms over as a reused
 * {@link CharSequence}, so each term is copied to a {@code String} first. That copy is part of the
 * per-term cost the migration benchmark measures.</p>
 */
public final class SaxonRegexTermMatcher implements TermMatcher {

    private final RegularExpression regex;

    public SaxonRegexTermMatcher(final RegularExpression regex) {
        this.regex = regex;
    }

    @Override
    public boolean matches(final CharSequence term) {
        return regex.containsMatch(StringView.of(term.toString()));
    }
}
