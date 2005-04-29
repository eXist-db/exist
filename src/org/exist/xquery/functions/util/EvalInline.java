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

import java.io.StringReader;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * @author wolf
 *
 */
public class EvalInline extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("eval-inline", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Dynamically evaluates the XPath/XQuery expression specified in $b within " +
				"the current instance of the query engine. The evaluation context is taken from " +
				"argument $a.",
				new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
				
	/**
	 * @param context
	 * @param signature
	 */
	public EvalInline(XQueryContext context) {
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
		
		// save some context properties
        context.pushNamespaceContext();
		DocumentSet oldDocs = context.getStaticallyKnownDocuments();
		context.setStaticallyKnownDocuments(exprContext.getDocumentSet());
		
		XQueryLexer lexer = new XQueryLexer(context, new StringReader(expr));
		XQueryParser parser = new XQueryParser(lexer);
		// shares the context of the outer expression
		XQueryTreeParser astParser = new XQueryTreeParser(context);
		try {
		    parser.xpath();
			if(parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new XPathException("error found while executing expression: " +
					parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			
			PathExpr path = new PathExpr(context);
			astParser.xpath(ast, path);
			if(astParser.foundErrors()) {
				throw new XPathException("error found while executing expression: " +
						astParser.getErrorMessage(), astParser.getLastException());
			}
			long start = System.currentTimeMillis();
			path.analyze(null, 0);
			Sequence sequence = path.eval(exprContext, null);
			path.reset();
			LOG.debug("Found " + sequence.getLength() + " for " + expr);
			LOG.debug("Query took " + (System.currentTimeMillis() - start));
			return sequence;
		} catch (RecognitionException e) {
			throw new XPathException("error found while executing eval expression: " + e.getMessage(), 
					e.getLine(), e.getColumn());
		} catch (TokenStreamException e) {
			throw new XPathException(getASTNode(), e.getMessage(), e);
		} finally {
			if (oldDocs != null)
				context.setStaticallyKnownDocuments(oldDocs);
			context.popNamespaceContext();
		}
	}

}
