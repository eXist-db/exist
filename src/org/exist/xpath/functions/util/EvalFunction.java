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
package org.exist.xpath.functions.util;

import java.io.StringReader;

import org.exist.dom.DocumentSet;
import org.exist.dom.QName;
import org.exist.parser.XPathLexer2;
import org.exist.parser.XPathParser2;
import org.exist.parser.XPathTreeParser2;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Function;
import org.exist.xpath.FunctionSignature;
import org.exist.xpath.PathExpr;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.StringValue;
import org.exist.xpath.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * @author wolf
 */
public class EvalFunction extends Function {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("eval", UTIL_FUNCTION_NS, "util"),
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
			},
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE));

	/**
	 * @param context
	 * @param signature
	 */
	public EvalFunction(StaticContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		String expr = StringValue.expand(
			getArgument(0).eval(docs, contextSequence, contextItem).getStringValue()
		);
		XPathLexer2 lexer = new XPathLexer2(new StringReader(expr));
		XPathParser2 parser = new XPathParser2(lexer, false);
		// shares the context of the outer expression
		XPathTreeParser2 astParser = new XPathTreeParser2(context);
		try {
			parser.xpath();
			if(parser.foundErrors()) {
				LOG.debug(parser.getErrorMessage());
				throw new XPathException("error found while executing expression: " +
					parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			LOG.debug("generated AST: " + ast.toStringList());
			
			PathExpr path = new PathExpr(context);
			astParser.xpath(ast, path);
			if(astParser.foundErrors()) {
				throw new XPathException("error found while executing expression: " +
					astParser.getErrorMessage(), astParser.getLastException());
			}
			Sequence sequence = path.eval(docs, null, null);
			LOG.debug("found " + sequence.getLength());
			return sequence;
		} catch (RecognitionException e) {
			throw new XPathException("error found while executing expression: " +
				e.getMessage(), e);
		} catch (TokenStreamException e) {
			throw new XPathException("error found while executing expression: " +
				e.getMessage(), e);
		} catch (XPathException e) {
			throw new XPathException("error found while executing expression: " +
				e.getMessage(), e);
		}
	}
}
