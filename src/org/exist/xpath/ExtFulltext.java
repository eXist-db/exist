/*
 *  eXist Open Source Native XML Database
 * Copyright (C) 2001-03, Wolfgang M. Meier (meier@ifs. tu- darmstadt. de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */
package org.exist.xpath;

import java.util.Arrays;
import java.util.Iterator;
import java.util.ArrayList;
import org.exist.*;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;
import org.exist.dom.*;
import org.apache.log4j.Category;

public class ExtFulltext extends Function {

	private static Category LOG =
		Category.getInstance(ExtFulltext.class.getName());
	protected ArrayList containsExpr = new ArrayList(2);
	protected NodeSet[] hits = null;
	protected PathExpr path;
	protected String terms[] = null;
	protected int type = Constants.FULLTEXT_AND;
	protected boolean delayExecution = true;

	public ExtFulltext(int type) {
		super("contains");
		this.type = type;
	}

	public ExtFulltext(PathExpr path) {
		super("contains");
		this.path = path;
	}

	public void addTerm(String arg) {
		System.out.println("adding " + arg);
		this.containsExpr.add(arg);
	}

	public void addTerms(StaticContext context, String terms)
		throws EXistException {
		Tokenizer tokenizer =
			context.getBroker().getTextEngine().getTokenizer();
		tokenizer.setText(terms);
		org.exist.storage.analysis.TextToken token;
		String word;
		while (null != (token = tokenizer.nextToken(true))) {
			word = token.getText();
			System.out.println("adding " + word);
			containsExpr.add(word);
		}
	}

	public int countTerms() {
		return containsExpr.size();
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		NodeSet nodes =
			path == null
				? (NodeSet) contextSequence
				: (NodeSet) path.eval(context, docs, contextSequence);
		long start = System.currentTimeMillis();
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
					result =
						(type == Constants.FULLTEXT_AND)
							? result.intersection(t1)
							: result.union(t1);
			}
		} else
			result = hits[0];
		if (result == null)
			return Sequence.EMPTY_SEQUENCE;
			
		LOG.debug(
			"found "
				+ result.getLength()
				+ " in "
				+ (System.currentTimeMillis() - start));
		return result;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(path.pprint());
		buf.append(" &= ");
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

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) {
		if (!delayExecution) {
			processQuery(context, in_docs, null);
			DocumentSet ndocs = new DocumentSet();
			for (int i = 0; i < hits.length; i++) {
				((TextSearchResult) hits[i]).getDocuments(ndocs);
			}
			return ndocs;
		} else {
			return in_docs;
		}
	}

	protected void processQuery(
		StaticContext context,
		DocumentSet in_docs,
		NodeSet contextSet) {
		terms = new String[containsExpr.size()];
		int j = 0;
		String term;
		for (Iterator i = containsExpr.iterator(); i.hasNext(); j++) {
			terms[j] = (String) i.next();
		}
		if (terms == null)
			throw new RuntimeException("no search terms");
		hits = new NodeSet[terms.length];
		if (contextSet != null) {
			for (int k = 0; k < terms.length; k++) {
				hits[0] =
					context.getBroker().getTextEngine().getNodesContaining(
						in_docs,
						contextSet,
						terms[k]);
				if (type == Constants.FULLTEXT_AND)
					contextSet = hits[0];
			}
		} else {
			for (int k = 0; k < terms.length; k++) {
				hits[k] =
					context.getBroker().getTextEngine().getNodesContaining(
						in_docs,
						null,
						terms[k]);
			}
		}
	}

	public int returnsType() {
		return Type.NODE;
	}

	public void setPath(PathExpr path) {
		this.path = path;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		if (path != null)
			path.setInPredicate(inPredicate);
	}

	private static final class TermFreq implements Comparable {
		String term;
		int freq;
		
		/* (non-Javadoc)
		 * @see java.lang.Comparable#compareTo(java.lang.Object)
		 */
		public int compareTo(Object o) {
			TermFreq other = (TermFreq)o;
			if(freq == other.freq)
				return 0;
			else if(freq < other.freq)
				return -1;
			else
				return 1;
		}
	}
}
