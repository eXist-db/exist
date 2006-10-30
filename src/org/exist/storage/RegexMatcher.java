/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.storage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.util.GlobToRegex;

/**
 * A {@link org.exist.storage.TermMatcher} that matches index entries against a
 * regular expression. Used by {@link org.exist.storage.NativeTextEngine} and
 * {@link org.exist.storage.NativeValueIndex}.
 * 
 * @author wolf
 *
 */
class RegexMatcher implements TermMatcher {
	
	private Matcher matcher;
    private boolean matchAll = false;

    public RegexMatcher(String expr, int type, int flags) throws EXistException {
        this(expr, type, flags, false);
    }
    
    public RegexMatcher(String expr, int type, int flags, boolean matchAll) throws EXistException {
        try {
            // if expr is a file glob, translate it to a regular expression first
            if (type == DBBroker.MATCH_WILDCARDS) {
                expr = GlobToRegex.globToRegexp(expr);
                flags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
            }
            
            Pattern pattern = Pattern.compile(expr, flags);
            matcher = pattern.matcher("");
        } catch(PatternSyntaxException e) {
            throw new EXistException("Invalid regular expression: " + e.getMessage());
        }
        this.matchAll = matchAll;        
    }

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Comparator#equals(java.lang.Object)
	 */
	public boolean matches(CharSequence term) {
        matcher.reset(term);
        return matchAll ? matcher.matches() : matcher.find();
	}
}