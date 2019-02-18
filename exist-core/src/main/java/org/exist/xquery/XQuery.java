/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
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
package org.exist.xquery;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import antlr.collections.AST;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.text.NumberFormat;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.debuggee.Debuggee;
import org.exist.security.EffectiveSubject;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.util.HTTPUtils;
import org.exist.xquery.value.Sequence;


/**
 * @author wolf
 */
public class XQuery {

    private final static Logger LOG = LogManager.getLogger(XQuery.class);
    
    public CompiledXQuery compile(final DBBroker broker, final XQueryContext context, final String expression) throws XPathException, PermissionDeniedException {
    	final Source source = new StringSource(expression);
    	try {
            return compile(broker, context, source);
        } catch(final IOException ioe) {
            //should not happen because expression is a String
            throw new XPathException(ioe.getMessage());
        }
    }
    
    public CompiledXQuery compile(final DBBroker broker, final XQueryContext context, final Source source) throws XPathException, IOException, PermissionDeniedException {
        return compile(broker, context, source, false);
    }
    
    public CompiledXQuery compile(final DBBroker broker, final XQueryContext context, final Source source, final boolean xpointer) throws XPathException, IOException, PermissionDeniedException {

        context.setSource(source);

        try(final Reader reader = source.getReader()) {
            return compile(broker, context, reader, xpointer);
        } catch(final UnsupportedEncodingException e) {
            throw new XPathException(ErrorCodes.XQST0087, "unsupported encoding " + e.getMessage());
        }
    }
    
    private CompiledXQuery compile(final DBBroker broker, final XQueryContext context, final Reader reader, final boolean xpointer) throws XPathException, PermissionDeniedException {
        
        //check read permission
        context.getSource().validate(broker.getCurrentSubject(), Permission.READ);
        
        
    	//TODO: move XQueryContext.getUserFromHttpSession() here, have to check if servlet.jar is in the classpath
    	//before compiling/executing that code though to avoid a dependency on servlet.jar - reflection? - deliriumsky
    	
    	// how about - if(XQuery.class.getResource("servlet.jar") != null) do load my class with dependency and call method?
    	
    	/*
    	 	<|wolf77|> I think last time I checked, I already had problems with the call to
    	 	<|wolf77|> HTTPUtils.addLastModifiedHeader( result, context );
			<|wolf77|> in line 184 of XQuery.java, because it introduces another dependency on HTTP.
    	 */
    	
    	final long start = System.currentTimeMillis();
        final XQueryLexer lexer = new XQueryLexer(context, reader);
        final XQueryParser parser = new XQueryParser(lexer);
        final XQueryTreeParser treeParser = new XQueryTreeParser(context);
        try {
            if(xpointer) {
                parser.xpointer();
            } else {
                parser.xpath();
            }
            
            if(parser.foundErrors()) {
            	LOG.debug(parser.getErrorMessage());
            	throw new StaticXQueryException(parser.getErrorMessage());
            }

            final AST ast = parser.getAST();
            if(ast == null) {
                throw new XPathException("Unknown XQuery parser error: the parser returned an empty syntax tree.");
            }
            
//            LOG.debug("Generated AST: " + ast.toStringTree());
            final PathExpr expr = new PathExpr(context);
            if(xpointer) {
                treeParser.xpointer(ast, expr);
            } else {
                treeParser.xpath(ast, expr);
            }
            
            if(treeParser.foundErrors()) {
                //AST treeAst = treeParser.getAST();
                throw new StaticXQueryException(ast.getLine(), ast.getColumn(), treeParser.getErrorMessage(), treeParser.getLastException());
            }
            
            context.getRootContext().resolveForwardReferences();
            context.analyzeAndOptimizeIfModulesChanged(expr);

            // Log the query if it is not too large, but avoid
            // dumping huge queries to the log
            if(LOG.isDebugEnabled()){
                if (context.getExpressionCount() < 150) {
                    LOG.debug("Query diagnostics:\n" + ExpressionDumper.dump(expr));
                } else {
                    LOG.debug("Query diagnostics:\n" + "[skipped: more than 150 expressions]");
                }
            }
            
            if (LOG.isDebugEnabled()) {
            	final NumberFormat nf = NumberFormat.getNumberInstance();
            	LOG.debug("Compilation took "  +  nf.format(System.currentTimeMillis() - start) + " ms");
            }
            
            return expr;
        } catch(final RecognitionException e) {
            LOG.debug("Error compiling query: " + e.getMessage(), e);
            String msg = e.getMessage();
            if (msg.endsWith(", found 'null'")) {
                msg = msg.substring(0, msg.length() - ", found 'null'".length());
            }
            throw new StaticXQueryException(e.getLine(), e.getColumn(), msg);
        } catch(final TokenStreamException e) {
            final String es = e.toString();
            if(es.matches("^line \\d+:\\d+:.+")) {
                LOG.debug("Error compiling query: " + e.getMessage(), e);
                final int line = Integer.parseInt(es.substring(5, es.indexOf(':')));
                final String tmpColumn = es.substring(es.indexOf(':') + 1);
                final int column = Integer.parseInt(tmpColumn.substring(0, tmpColumn.indexOf(':')));
                throw new StaticXQueryException(line, column, e.getMessage(), e);
            } else {
                LOG.debug("Error compiling query: " + e.getMessage(), e);
                throw new StaticXQueryException(e.getMessage(), e);
            }
            
        }
    }
    
    
    public Sequence execute(final DBBroker broker, final CompiledXQuery expression, final Sequence contextSequence) throws XPathException, PermissionDeniedException {
    	return execute(broker, expression, contextSequence, null);
    }
    
    public Sequence execute(final DBBroker broker, final CompiledXQuery expression, final Sequence contextSequence, final Properties outputProperties) throws XPathException, PermissionDeniedException {
    	final XQueryContext context = expression.getContext();
        final Sequence result = execute(broker, expression, contextSequence,  outputProperties, true);
        
        //TODO : move this elsewhere !
        HTTPUtils.addLastModifiedHeader(result, context);
    	
        return result;
    }
    
    public Sequence execute(final DBBroker broker, final CompiledXQuery expression, final Sequence contextSequence, final boolean resetContext) throws XPathException, PermissionDeniedException {
    	return execute(broker, expression, contextSequence, null, resetContext);
    }
    
    public Sequence execute(final DBBroker broker, final CompiledXQuery expression, Sequence contextSequence, final Properties outputProperties, final boolean resetContext) throws XPathException, PermissionDeniedException {
    	
        //check execute permissions
        expression.getContext().getSource().validate(broker.getCurrentSubject(), Permission.EXECUTE);
        
        final long start = System.currentTimeMillis();
    	
        final XQueryContext context = expression.getContext();

        expression.reset();
        if(resetContext) {
            //context.setBroker(broker);
            context.getWatchDog().reset();
        }

        if(context.requireDebugMode()) {
            final Debuggee debuggee = broker.getBrokerPool().getDebuggee();
            if (debuggee != null) {
                debuggee.joint(expression);
            }
        }
        
        //do any preparation before execution
        context.prepareForExecution();
        
        final Subject callingUser = broker.getCurrentSubject();

        //if setUid or setGid, become Effective User
        EffectiveSubject effectiveSubject = null;
        final Source src = expression.getContext().getSource();
        if(src instanceof DBSource) {
            final DBSource dbSrc = (DBSource)src;
            final Permission perm = dbSrc.getPermissions();

            if(perm.isSetUid()) {
                if(perm.isSetGid()) {
                    //setUid and SetGid
                    effectiveSubject = new EffectiveSubject(perm.getOwner(), perm.getGroup());
                } else {
                    //just setUid
                    effectiveSubject = new EffectiveSubject(perm.getOwner());
                }
            } else if(perm.isSetGid()) {
                //just setGid, so we use the current user as the effective user
                effectiveSubject = new EffectiveSubject(callingUser, perm.getGroup());
            }
        }
        
        try {
            if(effectiveSubject != null) {
                broker.pushSubject(effectiveSubject); //switch to effective user (e.g. setuid/setgid)
            }
            
            context.getProfiler().traceQueryStart();
            broker.getBrokerPool().getProcessMonitor().queryStarted(context.getWatchDog());
            try {

                // support for XQuery 3.0 - declare context item :=
                if(contextSequence == null) {
                    if(context.getContextItemDeclartion() != null) {
                        contextSequence = context.getContextItemDeclartion().eval(null, null);
                    }
                }

                final Sequence result = expression.eval(contextSequence);
                if(LOG.isDebugEnabled()) {
                    final NumberFormat nf = NumberFormat.getNumberInstance();
                    LOG.debug("Execution took "  +  nf.format(System.currentTimeMillis() - start) + " ms");
                }

                if(outputProperties != null) {
                    context.checkOptions(outputProperties); //must be done before context.reset!
                }

                return result;
            } finally {
                context.getProfiler().traceQueryEnd(context);
                // track query stats before context is reset
                broker.getBrokerPool().getProcessMonitor().queryCompleted(context.getWatchDog());
                expression.reset();
                if(resetContext) {
                    context.reset();
                }
            }
            
        } finally {
            if(effectiveSubject != null) {
                broker.popSubject();
            }
        }
    }

    public Sequence execute(final DBBroker broker, final String expression, final Sequence contextSequence) throws XPathException, PermissionDeniedException {
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        final CompiledXQuery compiled = compile(broker, context, expression);
        return execute(broker, compiled, contextSequence);
    }
	
    public Sequence execute(final DBBroker broker, File file, Sequence contextSequence) throws XPathException, IOException, PermissionDeniedException {
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        final CompiledXQuery compiled = compile(broker, context, new FileSource(file.toPath(), true));
        return execute(broker, compiled, contextSequence);
    }
}