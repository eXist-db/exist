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
package org.exist.xpath.functions;

import java.util.ArrayList;
import java.util.List;

import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.analysis.Tokenizer;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Constants;
import org.exist.xpath.Dependency;
import org.exist.xpath.Expression;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class ExtFulltext extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("contains", BUILTIN_FUNCTION_NS),
			new SequenceType[] { new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE) },
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE)
		);
			
	protected PathExpr path;
	protected Expression searchTerm = null;
	protected String terms[] = null;
	protected int type = Constants.FULLTEXT_AND;

	public ExtFulltext(StaticContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	public ExtFulltext(StaticContext context, PathExpr path) {
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

	protected void getSearchTerms(StaticContext context, String searchString)
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
		DocumentSet docs,
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
					: path.eval(docs, contextSequence).toNodeSet();
			String arg =
				searchTerm
					.eval(docs, contextSequence)
					.getStringValue();
			return evalQuery(context, docs, arg, nodes);
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
						: path.eval(docs, contextSequence).toNodeSet();
				haveNodes = true;
			}
			for (SequenceIterator i = contextSequence.iterate();
				i.hasNext();
				) {
				current = i.nextItem();
				arg =
					searchTerm
						.eval(docs, current.toSequence())
						.getStringValue();
				long start = System.currentTimeMillis();
				if (!haveNodes) {
					nodes =
						path == null
							? contextSequence.toNodeSet()
							: path
								.eval(docs, current.toSequence())
								.toNodeSet();
				}
				temp = evalQuery(context, docs, arg, nodes);
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
		StaticContext context,
		DocumentSet docs,
		String searchArg,
		NodeSet nodes)
		throws XPathException {
		try {
			getSearchTerms(context, searchArg);
		} catch (EXistException e) {
			throw new XPathException(e.getMessage(), e);
		}
		NodeSet hits = processQuery(context, docs, nodes);

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
	 * @see org.exist.xpath.functions.Function#getDependencies()
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
		StaticContext context,
		DocumentSet in_docs,
		NodeSet contextSet) {
		if (terms == null)
			throw new RuntimeException("no search terms");
		NodeSet hits = null;
		for (int k = 0; k < terms.length; k++) {
			hits =
				context.getBroker().getTextEngine().getNodesContaining(
					in_docs,
					contextSet,
					terms[k]);
			if (type == Constants.FULLTEXT_AND)
				contextSet = hits;
		}
		return hits;
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
}
