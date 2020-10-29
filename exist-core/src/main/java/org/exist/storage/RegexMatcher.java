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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.util.PatternFactory;

/**
 * A {@link org.exist.storage.TermMatcher} that matches index entries against a
 * regular expression. Used by {@link org.exist.storage.NativeValueIndex}.
 */
class RegexMatcher implements TermMatcher {
	
	private Matcher matcher;
    private boolean matchAll;

    public RegexMatcher(final String expr, final int flags) throws EXistException {
        this(expr, flags, false);
    }

    public RegexMatcher(final String expr, final int flags, final boolean matchAll) throws EXistException {
        try {
            final Pattern pattern = PatternFactory.getInstance().getPattern(expr, flags);
            matcher = pattern.matcher("");
        } catch(final PatternSyntaxException e) {
            throw new EXistException("Invalid regular expression: " + e.getMessage());
        }
        this.matchAll = matchAll;        
    }

	@Override
	public boolean matches(final CharSequence term) {
        matcher.reset(term);
        return matchAll ? matcher.matches() : matcher.find();
	}
}
