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

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Compiler;
import org.apache.oro.text.regex.Perl5Matcher;
import org.exist.EXistException;


class RegexMatcher implements TermMatcher {

	private PatternCompiler regexCompiler = new Perl5Compiler();
	private PatternCompiler globCompiler = new GlobCompiler();
	private PatternMatcher matcher = new Perl5Matcher();
	
	private Pattern regexp;

	public RegexMatcher(String expr, int type) throws EXistException {
		try {
			regexp = (type == DBBroker.MATCH_REGEXP
					? regexCompiler.compile(expr,
							Perl5Compiler.CASE_INSENSITIVE_MASK)
					: globCompiler
							.compile(
									expr,
									GlobCompiler.CASE_INSENSITIVE_MASK
											| GlobCompiler.QUESTION_MATCHES_ZERO_OR_ONE_MASK));
		} catch (MalformedPatternException e) {
			throw new EXistException(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see java.util.Comparator#equals(java.lang.Object)
	 */
	public boolean matches(String term) {
		return matcher.contains(term, regexp);
	}
}