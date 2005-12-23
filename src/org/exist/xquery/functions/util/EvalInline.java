/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Team
 *
 *  http://exist-db.org
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
package org.exist.xquery.functions.util;

import java.io.IOException;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.XQueryPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 */
public class EvalInline extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("eval-inline", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates the XPath/XQuery expression specified in $b within " +
				"the current instance of the query engine. The evaluation context is taken from " +
				"argument $a.",
				new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)),
		new FunctionSignature(
				new QName("eval-inline", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates the XPath/XQuery expression specified in $b within " +
				"the current instance of the query engine. The evaluation context is taken from " +
				"argument $a. The third argument, $c, specifies if the compiled query expression " +
				"should be cached. The cached query will be globally available within the db instance.",
				new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE))
	};
	
//	private CompiledXQuery compiled = null;
	
	/**
	 * @param context
	 * @param signature
	 */
	public EvalInline(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		// the current expression context
		Sequence exprContext = args[0];
		// get the query expression
		String expr = StringValue.expand(args[1].getStringValue());
		if ("".equals(expr.trim()))
		  return new EmptySequence();
		
		// should the compiled query be cached?
		boolean cache = false;
		if (getArgumentCount() == 3)
			cache = ((BooleanValue)args[2].itemAt(0)).effectiveBooleanValue();
		
		// save some context properties
        context.pushNamespaceContext();
		DocumentSet oldDocs = context.getStaticallyKnownDocuments();
		context.setStaticallyKnownDocuments(exprContext.getDocumentSet());
		
		if (context.isProfilingEnabled(2))
			context.getProfiler().start(this, "eval: " + expr);
		
		Sequence sequence = null;
		Source source = new StringSource(expr);
		XQuery xquery = context.getBroker().getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		CompiledXQuery compiled = cache ? pool.borrowCompiledXQuery(context.getBroker(), source) : null;
		try {
			if(compiled == null) {
			    compiled = xquery.compile(context, source);
			} else {
				compiled.setContext(context);
			}
			sequence = xquery.execute(compiled, exprContext, false);
			return sequence;
		} catch (IOException e) {
			throw new XPathException(getASTNode(), e.getMessage(), e);
		} finally {
			if (cache)
				pool.returnCompiledXQuery(source, compiled);
			if (oldDocs != null)
				context.setStaticallyKnownDocuments(oldDocs);
			context.popNamespaceContext();
			if (context.isProfilingEnabled(2))
				context.getProfiler().end(this, "eval: " + expr, sequence);
		}
	}

}
