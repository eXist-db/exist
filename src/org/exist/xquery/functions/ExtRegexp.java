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

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 */
public class ExtRegexp extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("match-all", BUILTIN_FUNCTION_NS),
			"eXist-specific extension function. Tries to match each of the regular expression " +
			"strings passed in $b and all following parameters against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing all of the keywords is copied to the result sequence.",
			new SequenceType[] { 
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE) 
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true
		);
			
	protected int type = Constants.FULLTEXT_AND;

	public ExtRegexp(XQueryContext context) {
		super(context, signature);
	}
	
	/**
	 * @param type
	 */
	public ExtRegexp(XQueryContext context, int type) {
		super(context, signature);
		this.type = type;
	}

	public ExtRegexp(XQueryContext context, int type, FunctionSignature signature) {
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
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least two arguments");
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression path = getArgument(0);
		if ((getDependencies() & Dependency.CONTEXT_ITEM) == Dependency.NO_DEPENDENCY) {
			LOG.debug("single execution");
			NodeSet nodes =
				path == null
					? contextSequence.toNodeSet()
					: path.eval(contextSequence).toNodeSet();
			String[] terms = getSearchTerms(context, contextSequence);
			return evalQuery(context, nodes, terms);
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
				String[] terms = getSearchTerms(context, current.toSequence());
				long start = System.currentTimeMillis();
				nodes =
					path == null
						? contextSequence.toNodeSet()
						: path
							.eval(current.toSequence())
							.toNodeSet();
				temp = evalQuery(context, nodes, terms);
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
		XQueryContext context,
		NodeSet nodes,
		String[] terms)
		throws XPathException {
		if(terms == null || terms.length == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
		NodeSet hits[] = new NodeSet[terms.length];
		for (int k = 0; k < terms.length; k++) {
			hits[k] =
				context.getBroker().getTextEngine().getNodesContaining(
					nodes.getDocumentSet(),
					nodes,
					terms[k], DBBroker.MATCH_REGEXP);
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
	
	protected String[] getSearchTerms(XQueryContext context, Sequence contextSequence) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least 2 arguments");
		String[] terms = new String[getArgumentCount() - 1];
		Expression next;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			terms[i - 1] = next.eval(contextSequence).getStringValue();
		}
		return terms;
	}
}
