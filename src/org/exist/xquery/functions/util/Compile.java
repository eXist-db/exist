/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist team
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.io.StringReader;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

public class Compile extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(Compile.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("compile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically evaluates the XPath/XQuery expression specified in $expression within " +
			"the current instance of the query engine.",
			new SequenceType[] {
				new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.EXACTLY_ONE, "The XPath/XQuery expression.")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the results of the expression"));
	
	public Compile(XQueryContext context) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		// get the query expression
		String expr = args[0].getStringValue();
		if ("".equals(expr.trim())) {
		  return new EmptySequence();
		}
		context.pushNamespaceContext();
		logger.debug("eval: " + expr);
		// TODO(pkaminsk2): why replicate XQuery.compile here?
		XQueryLexer lexer = new XQueryLexer(context, new StringReader(expr));
		XQueryParser parser = new XQueryParser(lexer);
		// shares the context of the outer expression
		XQueryTreeParser astParser = new XQueryTreeParser(context);
		try {
		    parser.xpath();
			if(parser.foundErrors()) {
				logger.debug(parser.getErrorMessage());
				throw new XPathException(this, "error found while executing expression: " +
					parser.getErrorMessage());
			}
			AST ast = parser.getAST();
			
			PathExpr path = new PathExpr(context);
			astParser.xpath(ast, path);
			if(astParser.foundErrors()) {
				throw new XPathException(this, "error found while executing expression: " +
						astParser.getErrorMessage(), astParser.getLastException());
			}
			path.analyze(new AnalyzeContextInfo());
		} catch (RecognitionException e) {			
			return new StringValue(e.toString());
		} catch (TokenStreamException e) {
			return new StringValue(e.toString());
		} catch (XPathException e) {
			return new StringValue(e.toString());
		} catch (Exception e) {
			return new StringValue(e.getMessage());
		} finally {
			context.popNamespaceContext();
		}
		return Sequence.EMPTY_SEQUENCE;
	}

}
