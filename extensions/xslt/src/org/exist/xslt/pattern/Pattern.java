/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.pattern;

import java.io.IOException;
import java.io.Reader;
import java.text.NumberFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.AnyNodeTest;
import org.exist.xquery.Constants;
import org.exist.xquery.LocationStep;
import org.exist.xquery.PathExpr;
import org.exist.xquery.StaticXQueryException;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xslt.XSLContext;
import org.exist.xslt.expression.XSLPathExpr;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;

/**
 * [1]    Pattern    ::=    PathPattern <br>
 *    | Pattern '|' PathPattern <br>
 * [2]    PathPattern    ::=    RelativePathPattern <br>
 *    | '/' RelativePathPattern? <br>
 *    | '//' RelativePathPattern <br>
 *    | IdKeyPattern (('/' | '//') RelativePathPattern)? <br>
 * [3]    RelativePathPattern    ::=    PatternStep (('/' | '//') RelativePathPattern)? <br>
 * [4]    PatternStep    ::=    PatternAxis? NodeTest XP PredicateListXP <br>
 * [5]    PatternAxis    ::=    ('child' '::' | 'attribute' '::' | '@') <br>
 * [6]    IdKeyPattern    ::=    'id' '(' IdValue ')' <br>
 *    | 'key' '(' StringLiteralXP ',' KeyValue ')' <br>
 * [7]    IdValue    ::=    StringLiteralXP | VarRef XP <br>
 * [8]    KeyValue    ::=    Literal XP | VarRef XP <br>
 * <br>
 * The constructs NodeTest XP, PredicateList XP, VarRef XP, Literal XP, and StringLiteral XP are part of the XPath expression language, and are defined in [XPath 2.0].
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Pattern {

    private final static Logger LOG = LogManager.getLogger(Pattern.class);
	
    static final String ELEMENT = "element()"; 
    static final String ELEMENT_A = "element(*)"; 
    static final String ATTRIBUTE = "attribute()"; 
    static final String ATTRIBUTE_A = "attribute(*)"; 

    //enclose expression
	public static XSLPathExpr parse(XQueryContext context, String str) throws XPathException {
		if (!(str != null && str.startsWith("{") && str.endsWith("}"))) {
			return null;
		}
		
		XSLPathExpr expr = new XSLPathExpr((XSLContext) context);
		parse(context, str.substring(1, str.length() - 1), expr);
		
		return expr;
	}

	public static void parse(XQueryContext context, String pattern, XSLPathExpr content) throws XPathException {
		boolean xpointer = false;
		
		//TODO: rewrite RootNode?
		if (pattern.equals("//")) {
			content.add(new LocationStep(context, Constants.SELF_AXIS, new AnyNodeTest()));
			return;
		}
		if (pattern.equals("/")) {
			content.add(new LocationStep(context, Constants.SELF_AXIS, new AnyNodeTest()));
			return;
		}
		
		Source source = new StringSource(pattern);
        Reader reader;
		try {
			reader = source.getReader();
		} catch (IOException e1) {
			return;//TODO: report error???
		}
		
    	long start = System.currentTimeMillis();
        XQueryLexer lexer = new XQueryLexer(context, reader);
		XQueryParser parser = new XQueryParser(lexer);
		XQueryTreeParser treeParser = new XQueryTreeParser(context);
		try {
            if (xpointer)
                parser.xpointer();
            else
                parser.xpath();
            if (parser.foundErrors()) {
            	LOG.debug(parser.getErrorMessage());
            	throw new StaticXQueryException(
            		parser.getErrorMessage());
            }

            AST ast = parser.getAST();
            if (ast == null)
                throw new XPathException("Unknown XQuery parser error: the parser returned an empty syntax tree.");

            PathExpr expr = new PathExpr(context);
            if (xpointer)
                treeParser.xpointer(ast, expr);
            else
                treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
                throw new StaticXQueryException(
            		treeParser.getErrorMessage(),
            		treeParser.getLastException());
            }

            expr.analyze(new AnalyzeContextInfo(context));

//            if (context.optimizationsEnabled()) {
//                Optimizer optimizer = new Optimizer(context);
//                expr.accept(optimizer);
//                if (optimizer.hasOptimized()) {
//                    context.reset(true);
//                    expr.resetState(true);
//                    expr.analyze(new AnalyzeContextInfo());
//                }
//            }

            // Log the query if it is not too large, but avoid
            // dumping huge queries to the log
            if (context.getExpressionCount() < 150) {
                LOG.debug("Query diagnostics:\n" + ExpressionDumper.dump(expr));
            } else {
                LOG.debug("Query diagnostics:\n" + "[skipped: more than 150 expressions]");
            }
            if (LOG.isDebugEnabled()) {
            	NumberFormat nf = NumberFormat.getNumberInstance();
            	LOG.debug("Compilation took "  +  nf.format(System.currentTimeMillis() - start) + " ms");
            }
            //return

            content.add(expr);

		} catch (RecognitionException e) {
			LOG.debug("Error compiling query: " + e.getMessage(), e);
			String msg = e.getMessage();
			if (msg.endsWith(", found 'null'"))
				msg = msg.substring(0, msg.length() - ", found 'null'".length());
            throw new StaticXQueryException(e.getLine(), e.getColumn(), msg);
        } catch (TokenStreamException e) {
        	LOG.debug("Error compiling query: " + e.getMessage(), e);
            throw new StaticXQueryException(e.getMessage(), e);
        }
	}
}
