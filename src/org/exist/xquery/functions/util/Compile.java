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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.ErrorCodes.ErrorCode;
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

import javax.xml.XMLConstants;

public class Compile extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(Compile.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("compile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Compiles the XQuery expression given in parameter $expression. Returns an empty string " +
			"if no errors were found, a description of the error otherwise.",
			new SequenceType[] {
				new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.EXACTLY_ONE, "The XPath/XQuery expression.")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the results of the expression")),
		new FunctionSignature(
				new QName("compile", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
				"Compiles the XQuery expression given in parameter $expression. Returns an empty string " +
				"if no errors were found, a description of the error otherwise.",
				new SequenceType[] {
					new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.EXACTLY_ONE, "The XPath/XQuery expression."),
					new FunctionParameterSequenceType("module-load-path", Type.STRING, Cardinality.EXACTLY_ONE, "The module load path. " +
							"Imports will be resolved relative to this. Use xmldb:exist:///db if your modules are stored in db.")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the results of the expression")),
		new FunctionSignature(
			new QName("compile-query", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Compiles the XQuery expression given in parameter $expression. Returns an XML fragment which describes " +
			"any errors found. If the query could be compiled successfully, a fragment <info result=\"pass\"/> is " +
			"returned. Otherwise, an error description is returned as follows: <info result=\"fail\"><error code=\"errcode\" " +
			"line=\"line\" column=\"column\">error description</error></info>.",
			new SequenceType[] {
				new FunctionParameterSequenceType("expression", Type.STRING, Cardinality.EXACTLY_ONE, "The XPath/XQuery expression."),
				new FunctionParameterSequenceType("module-load-path", Type.STRING, Cardinality.ZERO_OR_ONE, "The module load path. " +
					"Imports will be resolved relative to this. Use xmldb:exist:///db if your modules are stored in db.")
			},
			new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.EXACTLY_ONE, "the results of the expression"))
	};

	private static final QName QNAME_INFO = new QName("info", XMLConstants.NULL_NS_URI);
	private static final QName ERROR_INFO = new QName("error", XMLConstants.NULL_NS_URI);
	private static final QName QNAME_RESULT_ATTR = new QName("result", XMLConstants.NULL_NS_URI);
	private static final QName QNAME_ERRCODE_ATTR = new QName("code", XMLConstants.NULL_NS_URI);
	private static final QName QNAME_LINE_ATTR = new QName("line", XMLConstants.NULL_NS_URI);
	private static final QName QNAME_COLUMN_ATTR = new QName("column", XMLConstants.NULL_NS_URI);
	
	public Compile(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		
		// get the query expression
		final String expr = args[0].getStringValue();
		if ("".equals(expr.trim())) {
		  return new EmptySequence();
		}
		context.pushNamespaceContext();
		logger.debug("eval: " + expr);
		// TODO(pkaminsk2): why replicate XQuery.compile here?
		
		String error = null;
		ErrorCodes.ErrorCode code = null;
		int line = -1;
		int column = -1;
		
		final XQueryContext pContext = 
			new XQueryContext(context.getBroker().getBrokerPool());
		
		if (getArgumentCount() == 2 && args[1].hasOne()) {
			pContext.setModuleLoadPath(args[1].getStringValue());
		}
		final XQueryLexer lexer = new XQueryLexer(pContext, new StringReader(expr));
		final XQueryParser parser = new XQueryParser(lexer);
		// shares the context of the outer expression
		final XQueryTreeParser astParser = new XQueryTreeParser(pContext);
		try {
		    parser.xpath();
			if(parser.foundErrors()) {
				logger.debug(parser.getErrorMessage());
				throw new XPathException(this, "error found while executing expression: " +
					parser.getErrorMessage());
			}
			final AST ast = parser.getAST();
			
			final PathExpr path = new PathExpr(pContext);
			astParser.xpath(ast, path);
			if(astParser.foundErrors()) {
				throw astParser.getLastException();
			}
			path.analyze(new AnalyzeContextInfo());
		} catch (final RecognitionException e) {			
			error = e.toString();
		} catch (final TokenStreamException e) {
			error = e.toString();
		} catch (final XPathException e) {
			line = e.getLine();
			column = e.getColumn();
			code = e.getCode();
			error = e.getDetailMessage();
		} catch (final Exception e) {
			error = e.getMessage();
		} finally {
			context.popNamespaceContext();
            pContext.reset(false);
		}
		
		if (isCalledAs("compile")) {
			return error == null ? Sequence.EMPTY_SEQUENCE : new StringValue(error);
		} else {
			return response(pContext, error, code, line, column);
		}
	}

	private Sequence response(XQueryContext pContext, String error, ErrorCode code, int line, int column) {
		context.pushDocumentContext();
		final MemTreeBuilder builder = context.getDocumentBuilder();
		
		builder.startElement(QNAME_INFO, null);
		builder.addAttribute(QNAME_RESULT_ATTR, error == null ? "pass" : "fail");
		
		if (error != null) {
			builder.startElement(ERROR_INFO, null);
			if (code != null)
				{builder.addAttribute(QNAME_ERRCODE_ATTR, code.toString());}
			if (line > -1) {
				builder.addAttribute(QNAME_LINE_ATTR, Integer.toString(line));
				builder.addAttribute(QNAME_COLUMN_ATTR, Integer.toString(column));
			}
			builder.characters(error);
			builder.endElement();
		}

		builder.endElement();
		
		return builder.getDocument().getNode(1);
	}
}
