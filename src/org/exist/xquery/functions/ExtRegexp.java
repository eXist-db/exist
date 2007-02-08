/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import java.util.ArrayList;
import java.util.List;

import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.xquery.CachedResult;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.util.RegexTranslator;
import org.exist.xquery.util.RegexTranslator.RegexSyntaxException;
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
			new QName("match-all", Function.BUILTIN_FUNCTION_NS),
			"eXist-specific extension function. Tries to match each of the regular expression " +
			"strings passed in $b and all following parameters against the keywords contained in " +
			"the fulltext index. The keywords found are then compared to the node set in $a. Every " +
			"node containing all of the keywords is copied to the result sequence.",
			new SequenceType[] { 
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE) 
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
			true,
            "This function is eXist-specific and should not be in the standard functions namespace. Please " +
            "use text:match-all instead."
        );
			
	protected int type = Constants.FULLTEXT_AND;
	protected CachedResult cached = null;
	
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
	 * @see org.exist.xquery.functions.Function#getDependencies()
	 */
	public int getDependencies() {
		int deps = 0;
		for(int i = 0; i < getArgumentCount(); i++)
			deps = deps | getArgument(i).getDependencies();
		return deps;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least two arguments");
		
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		
		Expression path = getArgument(0);
		Sequence result;
		
		if (!Dependency.dependsOn(path, Dependency.CONTEXT_ITEM)) {
			
			boolean canCache = (getTermDependencies() & Dependency.CONTEXT_ITEM)
				== Dependency.NO_DEPENDENCY;
			
			if(	canCache && cached != null && cached.isValid(contextSequence)) {
				return cached.getResult();
			}
			
			NodeSet nodes =
				path == null
					? contextSequence.toNodeSet()
					: path.eval(contextSequence).toNodeSet();
			List terms = getSearchTerms(contextSequence);
			result = evalQuery(nodes, terms);
			
			if(canCache && contextSequence instanceof NodeSet)
				cached = new CachedResult((NodeSet)contextSequence, result);
			
		} else {			
			result = new ExtArrayNodeSet();
			for (SequenceIterator i = contextSequence.iterate(); i.hasNext();) {
				Item current = i.nextItem();
				List terms = getSearchTerms(current.toSequence());
				NodeSet nodes =
					path == null
						? contextSequence.toNodeSet()
						: path.eval(current.toSequence()).toNodeSet();
				Sequence temp = evalQuery(nodes, terms);
				result.addAll(temp);
			}
		}
		
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 

		return result;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.ExtFulltext#evalQuery(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, java.lang.String, org.exist.dom.NodeSet)
	 */
	public Sequence evalQuery(
		NodeSet nodes,
		List terms)
		throws XPathException {
		if(terms == null || terms.size() == 0)
			return Sequence.EMPTY_SEQUENCE;	// no search terms
		NodeSet hits[] = new NodeSet[terms.size()];
		for (int k = 0; k < terms.size(); k++) {
			hits[k] =
				context.getBroker().getTextEngine().getNodesContaining(
				    context,
					nodes.getDocumentSet(),
					nodes, NodeSet.ANCESTOR, null,
					(String)terms.get(k), DBBroker.MATCH_REGEXP);
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
	
	protected List getSearchTerms(Sequence contextSequence) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException(getASTNode(), "function requires at least 2 arguments");
		List terms = new ArrayList();
		Expression next;
		Sequence seq;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			seq = next.eval(contextSequence);			
			if(seq.hasOne())
			    terms.add(translateRegexp(seq.itemAt(0).getStringValue()));
			else {
				for(SequenceIterator it = seq.iterate(); it.hasNext(); ) {
				    terms.add(translateRegexp(it.nextItem().getStringValue()));
				}
			}
		}
		return terms;
	}
	
	protected int getTermDependencies() throws XPathException {
		int deps = 0;
		Expression next;
		for(int i = 1; i < getLength(); i++) {
			next = getArgument(i);
			deps |= next.getDependencies();
		}
		return deps;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#resetState()
	 */
	public void resetState() {
		super.resetState();
		cached = null;
	}
	
	/**
	 * Translates the regular expression from XPath2 syntax to java regex
	 * syntax.
	 * 
	 * @param pattern
	 * @return
	 * @throws XPathException
	 */
	protected String translateRegexp(String pattern) throws XPathException {
		// convert pattern to Java regex syntax
       try {
			pattern = RegexTranslator.translate(pattern, true);
		} catch (RegexSyntaxException e) {
			throw new XPathException(getASTNode(), "Conversion from XPath2 to Java regular expression " +
					"syntax failed: " + e.getMessage(), e);
		}
		return pattern;
	}
}
