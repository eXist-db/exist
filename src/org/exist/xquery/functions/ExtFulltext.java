/*
 *  eXist Open Source Native XML Database
 * Copyright (C) 2001-04, Wolfgang M. Meier (wolfgang@exist-db.org)
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
package org.exist.xquery.functions;

import java.util.ArrayList;
import java.util.List;

import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class ExtFulltext extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("contains", Module.BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		);

	protected PathExpr path;
	protected Expression searchTerm = null;
	protected String terms[] = null;
	protected int type = Constants.FULLTEXT_AND;

	public ExtFulltext(XQueryContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	public ExtFulltext(XQueryContext context, PathExpr path) {
		super(context, signature);
		this.path = path;
	}

	public void addTerm(Expression term) {
		if (term instanceof PathExpr) {
			if (((PathExpr) term).getLength() == 1)
				term = ((PathExpr) term).getExpression(0);
		}
		searchTerm = term;
	}

	protected void getSearchTerms(XQueryContext context, String searchString)
		throws EXistException {
		List tokens = new ArrayList();
		Tokenizer tokenizer =
			context.getBroker().getTextEngine().getTokenizer();
		tokenizer.setText(searchString);
		org.exist.storage.analysis.TextToken token;
		String word;
		while (null != (token = tokenizer.nextToken(true))) {
			word = token.getText();
			tokens.add(word);
		}
		terms = new String[tokens.size()];
		terms = (String[]) tokens.toArray(terms);
	}

	public int countTerms() {
		return terms.length;
	}

	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		if ((getDependencies() & Dependency.CONTEXT_ITEM)
			== Dependency.NO_DEPENDENCY) {
			NodeSet nodes =
				path == null
					? contextSequence.toNodeSet()
					: path.eval(contextSequence).toNodeSet();
			String arg =
				searchTerm
					.eval(contextSequence)
					.getStringValue();
			return evalQuery(context, arg, nodes);
		} else {
			Item current;
			String arg;
			NodeSet nodes = null;
			NodeSet result = new ExtArrayNodeSet();
			Sequence temp;
			boolean haveNodes = false;
			if ((path.getDependencies() & Dependency.CONTEXT_ITEM)
				== Dependency.NO_DEPENDENCY) {
				nodes =
					path == null
						? contextSequence.toNodeSet()
						: path.eval(contextSequence).toNodeSet();
				haveNodes = true;
			}
			for (SequenceIterator i = contextSequence.iterate();
				i.hasNext();
				) {
				current = i.nextItem();
				arg =
					searchTerm
						.eval(current.toSequence())
						.getStringValue();
				long start = System.currentTimeMillis();
				if (!haveNodes) {
					nodes =
						path == null
							? contextSequence.toNodeSet()
							: path
								.eval(current.toSequence())
								.toNodeSet();
				}
				temp = evalQuery(context, arg, nodes);
				result.addAll(temp);
				LOG.debug(
					"found "
						+ temp.getLength()
						+ " for "
						+ arg
						+ " in "
						+ (System.currentTimeMillis() - start));
			}
			return result;
		}
	}

	public Sequence evalQuery(
		XQueryContext context,
		String searchArg,
		NodeSet nodes)
		throws XPathException {
		try {
			getSearchTerms(context, searchArg);
		} catch (EXistException e) {
			throw new XPathException(e.getMessage(), e);
		}
		NodeSet hits = processQuery(context, nodes);

		if (hits == null)
			return NodeSet.EMPTY_SET;
		return hits;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(path.pprint());
		buf.append(" &= \"");
		buf.append(searchTerm.pprint());
		buf.append("\")");
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.NO_DEPENDENCY;
		for (int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}

	public DocumentSet preselect(DocumentSet in_docs) {
		return in_docs;
	}

	protected NodeSet processQuery(
		XQueryContext context,
		NodeSet contextSet) throws XPathException {
		if (terms == null)
			throw new RuntimeException("no search terms");
		if(terms.length == 0)
			return NodeSet.EMPTY_SET;
		NodeSet hits[] = new NodeSet[terms.length];
		for (int k = 0; k < terms.length; k++) {
			hits[k] =
				context.getBroker().getTextEngine().getNodesContaining(
						context,
						contextSet.getDocumentSet(),
						contextSet,
						terms[k]);
		}
		NodeSet result = hits[0];
		if(result != null) {
			for(int k = 1; k < hits.length; k++) {
				if(hits[k] != null)
					result = (type == Constants.FULLTEXT_AND ? 
							result.deepIntersection(hits[k]) : result.union(hits[k]));
			}
			return result;
		} else
			return NodeSet.EMPTY_SET;
	}

	public int returnsType() {
		return Type.NODE;
	}

	public void setPath(PathExpr path) {
		this.path = path;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		if (path != null)
			path.setInPredicate(inPredicate);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		path.resetState();
		searchTerm.resetState();
	}
}
