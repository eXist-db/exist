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

import org.apache.log4j.Logger;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
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
        Reader reader = source.getReader();
        return compile(context, reader);
    }
    
    public CompiledXQuery compile(XQueryContext context, Reader reader) throws XPathException {
        long start = System.currentTimeMillis();
        XQueryLexer lexer = new XQueryLexer(context, reader);
		XQueryParser parser = new XQueryParser(lexer);
		XQueryTreeParser treeParser = new XQueryTreeParser(context);
		try {
            parser.xpath();
            if (parser.foundErrors()) {
            	LOG.debug(parser.getErrorMessage());
            	throw new XPathException(
            		parser.getErrorMessage());
            }

            AST ast = parser.getAST();
//            LOG.debug("Generated AST: " + ast.toStringTree());
            PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
            	throw new XPathException(
            		treeParser.getErrorMessage(),
            		treeParser.getLastException());
            }
            LOG.debug("Query:\n" + expr.pprint() + "\nCompilation took "  +  (System.currentTimeMillis() - start));
            return expr;
        } catch (RecognitionException e) {
            throw new XPathException(e.getMessage(), e.getLine(), e.getColumn());
        } catch (TokenStreamException e) {
            throw new XPathException(e.getMessage());
        }
    }
    
    public Sequence execute(CompiledXQuery expression, Sequence contextSequence) throws XPathException {
        XQueryContext context = expression.getContext();
        context.setBroker(broker);
        expression.reset();
        context.getWatchDog().reset();
        Sequence result = expression.eval(contextSequence);
        expression.reset();
        context.reset();
        return result;
    }
}
