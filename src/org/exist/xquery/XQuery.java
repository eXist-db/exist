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
import java.text.NumberFormat;

import org.apache.log4j.Logger;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.security.xacml.ExistPDP;
import org.exist.security.xacml.XACMLSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
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

import com.sun.xacml.ctx.RequestCtx;


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

    public XQueryContext newContext(AccessContext accessCtx) {
        return new XQueryContext(broker, accessCtx);
    }
    
    public XQueryPool getXQueryPool() {
        return broker.getBrokerPool().getXQueryPool();
    }
    
    public CompiledXQuery compile(XQueryContext context, String expression) 
    throws XPathException {
    	Source source = new StringSource(expression);
    	try {
    		return compile(context, source);
		} catch(IOException ioe) {
			//should not happen because expression is a String
			throw new XPathException(ioe.getMessage(), ioe);
		}
    }
    
    public CompiledXQuery compile(XQueryContext context, Source source) 
    throws XPathException, IOException {
        return compile(context, source, false);
    }
    
    public CompiledXQuery compile(XQueryContext context, Source source, boolean xpointer) 
    throws XPathException, IOException {
		
    	XACMLSource xsource = XACMLSource.getInstance(source);
        Reader reader = source.getReader();
        try {
        	CompiledXQuery compiled = compile(context, reader, xpointer);
        	compiled.setSource(xsource);
            return compiled;
		} finally {
        	reader.close();
		}
    }
    
    private CompiledXQuery compile(XQueryContext context, Reader reader, boolean xpointer) throws XPathException {
        
    	//TODO: move XQueryContext.getUserFromHttpSession() here, have to check if servlet.jar is in the classpath
    	//before compiling/executing that code though to avoid a dependency on servlet.jar - reflection? - deliriumsky
    	
    	// how about - if(XQuery.class.getResource("servlet.jar") != null) do load my class with dependency and call method?
    	
    	/*
    	 	<|wolf77|> I think last time I checked, I already had problems with the call to
    	 	<|wolf77|> HTTPUtils.addLastModifiedHeader( result, context );
			<|wolf77|> in line 184 of XQuery.java, because it introduces another dependency on HTTP.
    	 */
    	
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

            expr.analyze(new AnalyzeContextInfo());

            if (context.optimizationsEnabled()) {
                Optimizer optimizer = new Optimizer(context);
                expr.accept(optimizer);
                if (optimizer.hasOptimized()) {
                    context.reset();
                    expr.analyze(new AnalyzeContextInfo());
                }
            }

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
    	XQueryContext context = expression.getContext();
        Sequence result = execute(expression, contextSequence, true);
        //TODO : move this elsewhere !
        HTTPUtils.addLastModifiedHeader( result, context );
    	return result;
    }
    
    public Sequence execute(CompiledXQuery expression, Sequence contextSequence, boolean resetContext) throws XPathException {
    	long start = System.currentTimeMillis();
    	XQueryContext context = expression.getContext();
    	
		//check access to the query
		XACMLSource source = expression.getSource();
		try {
			ExistPDP pdp = context.getPDP();
			if(pdp != null) {
				RequestCtx request = pdp.getRequestHelper().createQueryRequest(context, source);
				pdp.evaluate(request);
			}
		} catch (PermissionDeniedException pde) {
			throw new XPathException("Permission to execute query: " + source.createId() + " denied.", pde);
		}
		
        expression.reset();
        if (resetContext) {
        	context.setBroker(broker);
        	context.getWatchDog().reset();
        }
        
        //do any preparation before execution
        context.prepare();
        
        broker.getBrokerPool().getXQueryMonitor().queryStarted(context.getWatchDog());
        try {
        	Sequence result = expression.eval(contextSequence);
        	if (LOG.isDebugEnabled()) {
        		NumberFormat nf = NumberFormat.getNumberInstance();
        		LOG.debug("Execution took "  +  nf.format(System.currentTimeMillis() - start) + " ms");
        	}
        	return result;
        } finally {
            expression.reset();
            if (resetContext)
                context.reset();
        	broker.getBrokerPool().getXQueryMonitor().queryCompleted(context.getWatchDog());
        }
    }

	public Sequence execute(String expression, Sequence contextSequence, AccessContext accessCtx) throws XPathException {
		XQueryContext context = new XQueryContext(broker, accessCtx);
		CompiledXQuery compiled = compile(context, expression);
		return execute(compiled, null);
    }
}
