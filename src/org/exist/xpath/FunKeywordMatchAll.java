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
 * $Id$
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.dom.TextSearchResult;
import org.exist.storage.DBBroker;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 *  xpath-library function: match-keywords(XPATH, arg1, arg2 ...)
 *
 *@author     Wolfgang Meier <wolfgang@exist-db.org>
 *@created    7. Oktober 2002
 */
public class FunKeywordMatchAll extends Function {

	protected String terms[] = null;
	protected NodeSet[] hits = null;
	protected boolean delayExecution = true;

	public FunKeywordMatchAll() {
		super("match-all");
	}

	public FunKeywordMatchAll(String name) {
		super(name);
	}

	public Sequence eval(
		StaticContext context,
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Expression path = getArgument(0);
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
					result =
						(getOperatorType() == Constants.FULLTEXT_AND)
							? result.intersection(t1)
							: result.union(t1);
			}
		} else
			result = hits[0];
		if (result == null)
			return Sequence.EMPTY_SEQUENCE;

		return result;
	}

	protected int getOperatorType() {
		return Constants.FULLTEXT_AND;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(name);
		buf.append(getArgument(0).pprint());
		for (int i = 1; i < getArgumentCount(); i++) {
			buf.append(", ");
			buf.append(getArgument(i).pprint());
		}
		buf.append(')');
		return buf.toString();
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context)
		throws XPathException {
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

	private Literal getLiteral(Expression expr) {
		if (expr instanceof PathExpr && ((PathExpr) expr).getLength() == 1)
			expr = ((PathExpr) expr).getExpression(0);
		if (expr instanceof Literal)
			return (Literal) expr;
		return null;
	}

	protected void processQuery(
		StaticContext context,
		DocumentSet in_docs,
		NodeSet contextSet)
		throws XPathException {
		terms = new String[getArgumentCount() - 1];
		for (int i = 1; i < getArgumentCount(); i++)
			terms[i - 1] = getLiteral(getArgument(i)).literalValue;
		if (terms == null)
			throw new XPathException("no search terms");
		hits = new NodeSet[terms.length];
		if (context != null) {
			for (int k = 0; k < terms.length; k++) {
				hits[0] =
					context.getBroker().getTextEngine().getNodesContaining(
						in_docs,
						contextSet,
						terms[k],
						DBBroker.MATCH_REGEXP);
				if (getOperatorType() == Constants.FULLTEXT_AND)
					contextSet = hits[0];
			}
		} else {
			for (int k = 0; k < terms.length; k++) {
				hits[k] =
					context.getBroker().getTextEngine().getNodesContaining(
						in_docs,
						null,
						terms[k],
						DBBroker.MATCH_REGEXP);
			}
		}
	}

	/**
	 *  Description of the Method
	 *
	 *@return    Description of the Return Value
	 */
	public int returnsType() {
		return Type.NODE;
	}
}
