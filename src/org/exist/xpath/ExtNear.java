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
package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.PatternCompiler;
import org.apache.oro.text.regex.PatternMatcher;
import org.apache.oro.text.regex.Perl5Matcher;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.TextSearchResult;
import org.exist.storage.NativeTextEngine;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 *  near() function.
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    July 31, 2002
 */
public class ExtNear extends ExtFulltext {

	private static Logger LOG = Logger.getLogger(ExtNear.class);
	private int max_distance = 1;
	private PatternCompiler globCompiler = new GlobCompiler();

	public ExtNear() {
		super(Constants.FULLTEXT_AND);
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		NodeSet nodes =
			(NodeSet) path.eval(context, docs, contextSequence, contextItem);
		if (hits == null)
			processQuery(context, docs, nodes);

		NodeSet result = null;
		if (!delayExecution) {
			for (int j = 0; j < hits.length; j++) {
				if (hits[j] == null)
					continue;
				hits[j] = ((TextSearchResult) hits[j]).process(nodes);
			}

			NodeSet t1;
			for (int j = 0; j < hits.length; j++) {
				t1 = hits[j];
				if (t1 == null)
					break;
				if (result == null)
					result = t1;
				else
					result = result.intersection(t1);
			}
		} else
			result = hits[0];
		if (result == null)
			return Sequence.EMPTY_SEQUENCE;
		
		boolean hasWildcards = false;
		for(int i = 0; i < containsExpr.size(); i++)
			hasWildcards = NativeTextEngine.containsWildcards(containsExpr.get(i).toString());
		LOG.debug("scanning " + result.getLength() + " nodes");
		return hasWildcards ? patternMatch(context, result) : exactMatch(context, result);
	}

	private Sequence exactMatch(StaticContext context, NodeSet result) {
			// generate list of search term patterns
			String terms[] = new String[containsExpr.size()];
			for (int i = 0; i < terms.length; i++)
				terms[i] = containsExpr.get(i).toString();

			// walk through hits and calculate term-distances
			String value;
			String term;
			String word;
			TextToken token;
			NodeProxy current;
			ArraySet r = new ArraySet(100);
			Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
			int j;
			int distance;
			for (Iterator i = result.iterator(); i.hasNext();) {
				current = (NodeProxy) i.next();
				value = current.getNodeValue();
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
						continue;
					}
					if (word.equalsIgnoreCase( term)) {
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
						j = 0;
						term = terms[j];
						distance = -1;
						continue;
					} else if (-1 < distance)
						++distance;

				}
			}
			LOG.debug("found " + r.getLength());
			return r;
		}
		
	private Sequence patternMatch(StaticContext context, NodeSet result) {
		// generate list of search term patterns
		Pattern terms[] = new Pattern[containsExpr.size()];
		for (int i = 0; i < terms.length; i++)
			try {
				terms[i] =
					globCompiler.compile(
						containsExpr.get(i).toString(),
						GlobCompiler.CASE_INSENSITIVE_MASK
							| GlobCompiler.QUESTION_MATCHES_ZERO_OR_ONE_MASK);
			} catch (MalformedPatternException e) {
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
		PatternMatcher matcher = new Perl5Matcher();
		Tokenizer tok = context.getBroker().getTextEngine().getTokenizer();
		int j;
		int distance;
		for (Iterator i = result.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			value = current.getNodeValue();
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
					continue;
				}
				if (matcher.matches(word, term)) {
					distance = 0;
					j++;
					if (j == terms.length) {
						// all terms found
						r.add(current);
						break;
					} else
						term = terms[j];

				} else if (j > 0 && matcher.matches(word, terms[0])) {
					// first search term found: start again
					j = 0;
					term = terms[j];
					distance = -1;
					continue;
				} else if (-1 < distance)
					++distance;

			}
		}
		return r;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("near(");
		buf.append(path.pprint());
		buf.append(", ");
		for (Iterator i = containsExpr.iterator(); i.hasNext();) {
			buf.append('\'');
			buf.append(i.next());
			buf.append('\'');
			if (i.hasNext())
				buf.append(", ");

		}
		buf.append(')');
		return buf.toString();
	}

	public void setDistance(int distance) {
		max_distance = distance;
	}
}
