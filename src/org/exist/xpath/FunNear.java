/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  meier@ifs.tu-darmstadt.de
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
 *  $Id:
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
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.analysis.TextToken;
import org.exist.storage.analysis.Tokenizer;
/**
 *  near() function.
 *
 *@author     Wolfgang Meier <meier@ifs.tu-darmstadt.de>
 *@created    July 31, 2002
 */
public class FunNear extends FunContains {

	private static Logger LOG = Logger.getLogger(FunNear.class);
	private int max_distance = 1;
	private PatternCompiler globCompiler = new GlobCompiler();

	public FunNear(BrokerPool pool) {
		super(pool, Constants.FULLTEXT_AND);
	}

	public FunNear(BrokerPool pool, PathExpr path, String arg) {
		super(pool, path, arg);
	}

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) {
		NodeSet nodes = (NodeSet) path.eval(context, docs, contextSet, contextNode).getNodeList();
		if (hits == null)
			processQuery(docs);

		long pid;
		NodeProxy current;
		NodeProxy parent;
		NodeSet temp;
		long start = System.currentTimeMillis();
		for (int j = 0; j < hits.length; j++) {
			temp = new ArraySet(100);
			for (int k = 0; k < hits[j].length; k++) {
				if (hits[j][k] == null)
					continue;
				for (Iterator i = hits[j][k].iterator(); i.hasNext();) {
					current = (NodeProxy) i.next();
					parent = nodes.parentWithChild(current, false, true);
					if (parent != null) {
						if (temp.contains(parent)) {
							parent.addMatches(current.matches);
						} else {
							parent.addMatches(current.matches);
							temp.add(parent);
						}
					}
				}
			}
			hits[j][0] = temp;
		}
		NodeSet t0 = null;
		NodeSet t1;
		for (int j = 0; j < hits.length; j++) {
			t1 = hits[j][0];
			if (t0 == null)
				t0 = t1;
			else
				t0 = t0.intersection(t1);

		}
		if (t0 == null) {
			t0 = new ArraySet(1);
			return new ValueNodeSet(t0);
		}

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
				return new ValueNodeSet(NodeSet.EMPTY_SET);
			}

		// walk through hits and calculate term-distances
		String value;
		Pattern term;
		String word;
		TextToken token;
		ArraySet result = new ArraySet(100);
		DBBroker broker = null;
		PatternMatcher matcher = new Perl5Matcher();
		try {
			broker = pool.get();
			Tokenizer tok = broker.getTextEngine().getTokenizer();
			int j;
			int distance;
			for (Iterator i = t0.iterator(); i.hasNext();) {
				current = (NodeProxy) i.next();
				value = broker.getNodeValue(current);
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
							result.add(current);
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
		} catch (EXistException e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
		return new ValueNodeSet(result);
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
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

	/**
	 *  Sets the distance attribute of the FunNear object
	 *
	 *@param  distance  The new distance value
	 */
	public void setDistance(int distance) {
		max_distance = distance;
	}
}
