/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
package org.exist.xquery;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;

import org.apache.log4j.Logger;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.Sequence;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;


/**
 * @author wolf
 */
public class XQuery {

    private final static Logger LOG = Logger.getLogger(XQuery.class);
    
    private DBBroker broker;
     
    /**
     * 
     */
    public XQuery(DBBroker broker) {
        this.broker = broker;
    }

    public XQueryContext newContext() {
        return new XQueryContext(broker);
    }
    
    public XQueryPool getXQueryPool() {
        return broker.getBrokerPool().getXQueryPool();
    }
    
    public CompiledXQuery compile(XQueryContext context, Source source) 
    throws XPathException, IOException {
        return compile(context, source, false);
    }
    
    public CompiledXQuery compile(XQueryContext context, Source source, boolean xpointer) 
    throws XPathException, IOException {
        Reader reader = source.getReader();
        CompiledXQuery compiled = compile(context, reader, xpointer);
        reader.close();
        return compiled;
    }
    
    public CompiledXQuery compile(XQueryContext context, Reader reader) throws XPathException {
        return compile(context, reader, false);
    }
    
    public CompiledXQuery compile(XQueryContext context, Reader reader, boolean xpointer) throws XPathException {
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
//            LOG.debug("Generated AST: " + ast.toStringTree());
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
            
            // Log the query if it is not too large, but avoid
            // dumping huge queries to the log
            if (context.getExpressionCount() < 150)
                LOG.debug("Query diagnostics:\n" + ExpressionDumper.dump(expr));
            expr.analyze(new AnalyzeContextInfo());
            LOG.debug("Compilation took "  +  (System.currentTimeMillis() - start));
            return expr;
        } catch (RecognitionException e) {
			LOG.debug("Error compiling query: " + e.getMessage(), e);
			String msg = e.getMessage();
			if (msg.endsWith(", found 'null'"))
				msg = msg.substring(0, msg.length() - ", found 'null'".length());
            throw new StaticXQueryException(msg, e.getLine(), e.getColumn());
        } catch (TokenStreamException e) {
			LOG.debug("Error compiling query: " + e.getMessage(), e);
            throw new StaticXQueryException(e.getMessage(), e);
        }
    }
    
    public Sequence execute(CompiledXQuery expression, Sequence contextSequence) throws XPathException {
    	return execute(expression, contextSequence, true);
    }
    
    public Sequence execute(CompiledXQuery expression, Sequence contextSequence, boolean resetContext) throws XPathException {
        XQueryContext context = expression.getContext();
        expression.reset();
        if (resetContext) {
        	context.setBroker(broker);
        	context.getWatchDog().reset();
        }
        broker.getBrokerPool().getXQueryMonitor().queryStarted(context.getWatchDog());
        try {
        	Sequence result = expression.eval(contextSequence);
        	expression.reset();
        	if (resetContext) {
        		context.reset();
        		HTTPUtils.addLastModifiedHeader( result, context );
        	}
        	return result;
        } finally {
        	broker.getBrokerPool().getXQueryMonitor().queryCompleted(context.getWatchDog());
        }
    }
    


	public Sequence execute(String expression, Sequence contextSequence) throws XPathException {
		XQueryContext context = new XQueryContext(broker);
		CompiledXQuery compiled = compile(context, new StringReader(expression));
		return execute(compiled, null);
    }
}
