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
package org.exist.xpath.functions;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xpath.*;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Constants;
import org.exist.xpath.Dependency;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class ExtRegexp extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("match-all", BUILTIN_FUNCTION_NS),
			new SequenceType[] { 
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE) 
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true
		);
			
	private int type = Constants.FULLTEXT_AND;

	public ExtRegexp(StaticContext context) {
		super(context, signature);
	}
	
	/**
	 * @param type
	 */
	public ExtRegexp(StaticContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = 0;
		for(int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}
	
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException("function requires at least two arguments");
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression path = getArgument(0);
		if ((getDependencies() & Dependency.CONTEXT_ITEM) == Dependency.NO_DEPENDENCY) {
			LOG.debug("single execution");
			NodeSet nodes =
				path == null
					? contextSequence.toNodeSet()
					: path.eval(docs, contextSequence).toNodeSet();
			String[] terms = getSearchTerms(context, docs, contextSequence);
			return evalQuery(context, docs, nodes, terms);
		} else {
			Item current;
			String arg;
			NodeSet nodes;
			NodeSet result = new ExtArrayNodeSet();
			Sequence temp;
			for (SequenceIterator i = contextSequence.iterate();
				i.hasNext();
				) {
				current = i.nextItem();
				String[] terms = getSearchTerms(context, docs, current.toSequence());
				long start = System.currentTimeMillis();
				nodes =
					path == null
						? contextSequence.toNodeSet()
						: path
							.eval(docs, current.toSequence())
							.toNodeSet();
				temp = evalQuery(context, docs, nodes, terms);
				result.addAll(temp);
				LOG.debug(
					"found "
						+ temp.getLength()
						+ " in "
						+ (System.currentTimeMillis() - start));
			}
			return result;
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.functions.ExtFulltext#evalQuery(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, java.lang.String, org.exist.dom.NodeSet)
	 */
	public Sequence evalQuery(
		StaticContext context,
		DocumentSet docs,
		NodeSet nodes,
		String[] terms)
		throws XPathException {
		if(terms == null || terms.length == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
		NodeSet hits = null;
		for (int k = 0; k < terms.length; k++) {
			hits =
				context.getBroker().getTextEngine().getNodesContaining(
					docs,
					nodes,
					terms[k],
					DBBroker.MATCH_REGEXP);
			if (type == Constants.FULLTEXT_AND)
				nodes = hits;
		}
		return hits;
	}
	
	protected String[] getSearchTerms(StaticContext context, DocumentSet docs, 
	Sequence contextSequence) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException("function requires at least 2 arguments");
		String[] terms = new String[getArgumentCount() - 1];
		Expression next;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			terms[i - 1] = next.eval(docs, contextSequence).getStringValue();
		}
		return terms;
	}
}
