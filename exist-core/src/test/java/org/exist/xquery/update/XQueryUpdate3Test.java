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
import org.exist.xquery.*;
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

    @Test
    public void revalidationDeclaration() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "declare revalidation strict;";

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

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }
        }
    }

    @Test
    public void transformWith() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "$e transform with { $e + 1 }\n";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            Expression ret = treeParser.expr(ast, expr);
            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(ret instanceof CopyModifyExpression);
        }
    }

    @Test
    public void copyModifyExprTest() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "copy $je := $e\n" +
                "   modify $je\n" +
                "   return $je";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);
            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }
        }
    }

    @Test
    public void dynamicUpdatingFunctionCall() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query = "let $f := fn:put#2\n" +
                "return invoke updating $f(<newnode/>,\"newnode.xml\")";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.xpath();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(((DebuggableExpression) ((LetExpr)expr.getFirst()).getReturnExpression()).getFirst() instanceof DynamicFunctionCall);
            DynamicFunctionCall dfc = (DynamicFunctionCall) ((DebuggableExpression) ((LetExpr)expr.getFirst()).getReturnExpression()).getFirst();
            assertEquals(Expression.Category.UPDATING, dfc.getCategory());
        }
    }

    @Test
    public void insertExpr() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query =
                "insert node <year>2005</year>\n" +
                "    after book/publisher";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(expr.getFirst() instanceof InsertExpr);
            assertEquals(Expression.Category.UPDATING, expr.getFirst().getCategory());
        }
    }

    @Test
    public void deleteExpr() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query =
                "delete node fn:doc(\"bib.xml\")/books/book[1]/author[last()]";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(expr.getFirst() instanceof DeleteExpr);
            assertEquals(Expression.Category.UPDATING, expr.getFirst().getCategory());
        }
    }

    @Test
    public void replaceNodeExpr() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query =
                "replace node fn:doc(\"bib.xml\")/books/book[1]/publisher\n" +
                        "with fn:doc(\"bib.xml\")/books/book[2]/publisher";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(expr.getFirst() instanceof ReplaceExpr);
            assertEquals(ReplaceExpr.ReplacementType.NODE,((ReplaceExpr) expr.getFirst()).getReplacementType());
            assertEquals("doc(\"bib.xml\")/child::{}books/child::{}book[1]/child::{}publisher",((ReplaceExpr) expr.getFirst()).getTargetExpr().toString());
            assertEquals("doc(\"bib.xml\")/child::{}books/child::{}book[2]/child::{}publisher",((ReplaceExpr) expr.getFirst()).getWithExpr().toString());
        }
    }

    @Test
    public void replaceValueExpr() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query =
                "replace value of node fn:doc(\"bib.xml\")/books/book[1]/price\n" +
                        "with fn:doc(\"bib.xml\")/books/book[1]/price * 1.1";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(expr.getFirst() instanceof ReplaceExpr);
            assertEquals(ReplaceExpr.ReplacementType.VALUE,((ReplaceExpr) expr.getFirst()).getReplacementType());
            assertEquals("doc(\"bib.xml\")/child::{}books/child::{}book[1]/child::{}price",((ReplaceExpr) expr.getFirst()).getTargetExpr().toString());
            assertEquals("doc(\"bib.xml\")/child::{}books/child::{}book[1]/child::{}price * 1.1",((ReplaceExpr) expr.getFirst()).getWithExpr().toString());
        }
    }

    @Test
    public void renameExpr() throws EXistException, RecognitionException, XPathException, TokenStreamException, PermissionDeniedException
    {
        String query =
                "rename node fn:doc(\"bib.xml\")/books/book[1]/author[1]\n" +
                        "as \"principal-author\"";

        BrokerPool pool = BrokerPool.getInstance();
        try(final DBBroker broker = pool.getBroker()) {
            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            xparser.expr();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            XQueryAST ast = (XQueryAST) xparser.getAST();

            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            PathExpr expr = new PathExpr(context);
            treeParser.expr(ast, expr);

            if (treeParser.foundErrors()) {
                fail(treeParser.getErrorMessage());
                return;
            }

            assertTrue(expr.getFirst() instanceof RenameExpr);
            assertEquals("doc(\"bib.xml\")/child::{}books/child::{}book[1]/child::{}author[1]",((RenameExpr) expr.getFirst()).getTargetExpr().toString());
            assertEquals("\"principal-author\"",((RenameExpr) expr.getFirst()).getNewNameExpr().toString());
        }
    }
}
