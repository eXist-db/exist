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
package org.exist.xquery.functions.util;

import java.io.StringReader;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.EmptySequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * Dynamically evaluates a string argument as an XPath/Query
 * expression.
 * 
 * @author wolf
 */
public class EvalFunction extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("eval", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically evaluates its string argument as an XPath/XQuery expression. " +
			"The argument expression will inherit the current execution context, i.e. all " +
			"namespace declarations and variable declarations are visible from within the " +
			"inner expression. The function accepts a second string argument to specify " +
			"the static context collection to which the expression applies. It will return" +
			"an empty sequence if you pass a whitespace string.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
            true);

	/**
	 * @param context
	 * @param signature
	 */
	public EvalFunction(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
        // get the query expression
		String expr = StringValue.expand(getArgument(0).eval(contextSequence, contextItem).getStringValue());
		if ("".equals(expr.trim()))
		  return new EmptySequence();
        // check optional collection argument
        DocumentSet oldDocumentSet = null;
        if(getArgumentCount() > 1) {
        	oldDocumentSet = context.getStaticallyKnownDocuments();
        	Sequence collectionArgs = getArgument(1).eval(contextSequence, contextItem);
        	if(collectionArgs.getLength() > 0)
            	context.setStaticallyKnownDocuments(getCollectionContext(collectionArgs));
        }
        context.pushNamespaceContext();
		LOG.debug("eval: " + expr);
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
			Sequence sequence = path.eval(null, null);
			path.reset();
			LOG.debug("Found " + sequence.getLength() + " for " + expr);
			LOG.debug("Query took " + (System.currentTimeMillis() - start));
			return sequence;
		} catch (RecognitionException e) {
			throw new XPathException("error found while executing expression: " +
				e.getMessage(), e);
		} catch (TokenStreamException e) {
			throw new XPathException("error found while executing expression: " +
				e.getMessage(), e);
		} finally {
			if(oldDocumentSet != null)
				context.setStaticallyKnownDocuments(oldDocumentSet);
			context.popNamespaceContext();
		}
	}

    private String[] getCollectionContext(Sequence arg) throws XPathException {
    	String collections[] = new String[arg.getLength()];
    	int j = 0;
        for(SequenceIterator i = arg.iterate(); i.hasNext(); j++) {
        	String collection = i.nextItem().getStringValue();
        	collections[j] = collection;
        }
        return collections;
    }
}
