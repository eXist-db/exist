/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.update;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.PathExpr;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.*;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class XQueryUpdate3Test {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void updatingCompatibilityAnnotation() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException {
        final String query =
                "xquery version \"3.0\"\n;" +
                        "module namespace t=\"http://exist-db.org/xquery/test/examples\";\n" +
                        "declare updating function" +
                        "   t:upsert($e as element(), \n" +
                        "          $an as xs:QName, \n" +
                        "          $av as xs:anyAtomicType) \n" +
                        "   {\n" +
                        "   let $ea := $e/attribute()[fn:node-name(.) = $an]\n" +
                        "   return\n" +
                        "     $ea\n" +
                        "   };";

        final BrokerPool pool = BrokerPool.getInstance();
        try (final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            final XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            final XQueryParser xparser = new XQueryParser(lexer);
            xparser.xpath();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
            }

            final XQueryAST ast = (XQueryAST) xparser.getAST();
            final XQueryTreeParser treeParser = new XQueryTreeParser(context);
            final PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
            }
        }
    }

    @Test
    public void simpleAnnotation() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException {
        final String query =
                "xquery version \"3.0\"\n;" +
                        "module namespace t=\"http://exist-db.org/xquery/test/examples\";\n" +
                        "declare %simple function" +
                        "   t:upsert($e as element(), \n" +
                        "          $an as xs:QName, \n" +
                        "          $av as xs:anyAtomicType) \n" +
                        "   {\n" +
                        "   let $ea := $e/attribute()[fn:node-name(.) = $an]\n" +
                        "   return\n" +
                        "     $ea\n" +
                        "   };";

        final BrokerPool pool = BrokerPool.getInstance();
        try (final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            final XQueryContext context = new XQueryContext(broker.getBrokerPool());
            final XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            final XQueryParser xparser = new XQueryParser(lexer);
            xparser.xpath();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            final XQueryAST ast = (XQueryAST) xparser.getAST();
            final XQueryTreeParser treeParser = new XQueryTreeParser(context);
            final PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
            }
        }
    }

    @Test
    public void simpleAnnotationIsInvalidForVariableDeclaration() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "declare %simple variable $ab := 1;";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.prolog();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();
            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.prolog(ast, expr);
        }
        catch(XPathException ex) {
            assertEquals(ErrorCodes.XUST0032, ex.getErrorCode());
        }
    }

    @Test
    public void testingForUpdatingFunction() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "%simple function ( * )";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.sequenceType();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            SequenceType type = new SequenceType();
            treeParser.sequenceType(ast, type);
            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }
        }
    }
}
