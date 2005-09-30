/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions;

import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.EXistException;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Tokenizer;
import org.exist.util.GlobToRegex;
import org.exist.xquery.Constants;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 *  near() function.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    July 31, 2002
 */
public class ExtNear extends ExtFulltext {

	private int max_distance = 1;
	private Expression distance = null;

	public ExtNear(XQueryContext context) {
		super(context, Constants.FULLTEXT_AND);
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.functions.ExtFulltext#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags) throws XPathException {
        super.analyze(parent, flags);
        if(distance != null)
            distance.analyze(this, flags);
    }
    
	public Sequence evalQuery(
		String searchArg,
		NodeSet nodes)
		throws XPathException {
		if(distance != null)
			max_distance = ((IntegerValue)distance.eval(nodes).convertTo(Type.INTEGER)).getInt();
		try {
			getSearchTerms(context, searchArg);
		} catch (EXistException e) {
			throw new XPathException(e.getMessage(), e);
		}
		NodeSet hits = processQuery(nodes);
		if (hits == null)
			return Sequence.EMPTY_SEQUENCE;
				
		boolean hasWildcards = false;
		for(int i = 0; i < terms.length; i++) {
			hasWildcards |=
				NativeTextEngine.containsWildcards(terms[i]);
		}
		return hasWildcards
			? patternMatch(context, hits)
			: exactMatch(context, hits);
	}

	private Sequence exactMatch(XQueryContext context, NodeSet result) {
		// walk through hits and calculate term-distances
		String value;
		String term;
		String word;
		TextToken token;
		NodeProxy current;
		NodeSet r = new ExtArrayNodeSet();
		Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
		int j;
		int distance;
		for (Iterator i = result.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			value = current.getNodeValueSeparated();
			tok.setText(value);
			j = 0;
			if (j < terms.length)
				term = terms[j];
			else
				break;
			distance = -1;
			while ((token = tok.nextToken()) != null) {
				word = token.getText().toLowerCase();
				if (distance > max_distance) {
					// reset
					j = 0;
					term = terms[j];
					distance = -1;
					
				} // that else would cause some words to be ignored in the matching
				if (word.equalsIgnoreCase(term)) {
					distance = 0;
					j++;
					if (j == terms.length) {
						// all terms found
						r.add(current);
						break;
					} else
						term = terms[j];

				} else if (j > 0 && word.equalsIgnoreCase(terms[0])) {
					// first search term found: start again
					j = 1;
					term = terms[j];
					distance = 0 ;
					continue;
				} // that else MAY cause the distance count to be off by one but i'm not sure
				if (-1 < distance)
					++distance;
			}
		}
//		LOG.debug("found " + r.getLength());
		return r;
	}

	private Sequence patternMatch(XQueryContext context, NodeSet result) {
		// generate list of search term patterns
		Pattern patterns[] = new Pattern[terms.length];
        Matcher matchers[] = new Matcher[terms.length];
		for (int i = 0; i < patterns.length; i++)
			try {
                patterns[i] = Pattern.compile(GlobToRegex.globToRegexp(terms[i]), 
                        Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                matchers[i] = patterns[i].matcher("");
			} catch (PatternSyntaxException e) {
				LOG.warn("malformed pattern", e);
				return Sequence.EMPTY_SEQUENCE;
			}

		// walk through hits and calculate term-distances
		String value;
		Pattern term;
		String word;
		TextToken token;
		NodeProxy current;
		ExtArrayNodeSet r = new ExtArrayNodeSet(100);
		Matcher matcher;
		Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
		int j;
		int distance;
		for (Iterator i = result.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			value = current.getNodeValueSeparated();
			tok.setText(value);
			j = 0;
			if (j < patterns.length) {
				term = patterns[j];
                matcher = matchers[j];
            } else
				break;
			distance = -1;
			while ((token = tok.nextToken()) != null) {
				word = token.getText().toLowerCase();
				if (distance > max_distance) {
					// reset
					j = 0;
					term = patterns[j];
                    matcher = matchers[j];
					distance = -1;
					continue;
				}
                matcher.reset(word);
                matchers[0].reset(word);
				if (matcher.matches()) {
					distance = 0;
					j++;
					if (j == patterns.length) {
						// all terms found
						r.add(current);
						break;
					} else {
						term = patterns[j];
                        matcher = matchers[j];
                    }
				} else if (j > 0 && matchers[0].matches()) {
					// first search term found: start again
					j = 1;
					term = patterns[j];
                    matcher = matchers[j];
					distance = 0;
					continue;
				} else if (-1 < distance)
					++distance;

			}
		}
		return r;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.functions.ExtFulltext#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("near(");
        path.dump(dumper);
        dumper.display(", ");
        searchTerm.dump(dumper);
        dumper.display(")");
    }
    
	public void setDistance(Expression expr) {
		distance = expr;
	}
}
