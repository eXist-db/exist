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
package org.exist.xquery;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.EXistException;
import org.exist.dom.QName;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.StringReader;

import static org.junit.Assert.*;

/**
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowClauseTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    @Test
    public void simpleWindowConditions() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
        }
    }

    @Test
    public void complexWindowCondition() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "   start $first next $second when $first/price < $second/price\n" +
                "   end $last next $beyond when $last/price > $beyond/price\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
        }
    }

    @Test
    public void noEndWindowCondition() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "   start $first next $second when $first/price < $second/price\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
        }
    }

    @Test
    public void slidingWindowClause() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(WindowExpr.WindowType.SLIDING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
        }
    }

    @Test
    public void allWindowsVars() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "start $first at $s previous $start-previous next $start-next when fn:true()\n" +
                "only end $last at $e previous $end-previous next $end-next when $e - $s eq 2\n" +
                "return\n" +
                "  <window>{$first, $last}</window>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(new QName("first"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(new QName("start-previous"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(new QName("start-next"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(new QName("last"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getCurrentItem());
            assertEquals(new QName("e"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(new QName("end-previous"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals(new QName("end-next"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
            assertEquals(true, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }

    @Test
    public void tumblingWindowAllWindowVarsNoOnly() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10)\n" +
                "    start $s at $spos previous $sprev next $snext when true() \n" +
                "    end $e at $epos previous $eprev next $enext when true()\n" +
                "return\n" +
                "  <window>{$first, $last}</window>\n";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(WindowExpr.WindowType.TUMBLING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("spos"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(new QName("sprev"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(new QName("snext"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(new QName("e"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getCurrentItem());
            assertEquals(new QName("epos"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(new QName("eprev"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals(new QName("enext"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
            assertEquals(false, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }

    @Test
    public void tumblingWindowAvgReturn() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return avg($w)";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getCurrentItem());
            assertEquals(new QName("e"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
            assertEquals(true, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }

    @Test
    public void tumblingWindowNoEndWindowConditionPositional() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when $s mod 3 = 1\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition());
        }
    }

    @Test
    public void tumblingWindowNoEndWindowConditionCurrentItem() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start $first when $first mod 3 = 0\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals("first", ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem().getStringValue());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition());
        }
    }

    @Test
    public void slidingWindowAvgReturn() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return avg($w)";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(WindowExpr.WindowType.SLIDING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(new QName("e"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
            assertEquals(true, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }

    @Test
    public void slidingWindowEndWithoutOnly() throws EXistException, RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(WindowExpr.WindowType.SLIDING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
            assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
            assertEquals(new QName("e"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
            assertEquals(false, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }

    @Test
    public void tumblingWindowRunUp() throws EXistException, RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in $closings\n" +
                "   start $first next $second when $first/price < $second/price\n" +
                "   end $last next $beyond when $last/price > $beyond/price\n" +
                "return\n" +
                "   <run-up symbol=\"{$symbol}\">\n" +
                "      <start-date>{fn:data($first/date)}</start-date>\n" +
                "      <start-price>{fn:data($first/price)}</start-price>\n" +
                "      <end-date>{fn:data($last/date)}</end-date>\n" +
                "      <end-price>{fn:data($last/price)}</end-price>\n" +
                "   </run-up>";

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
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
                return;
            }

            assertTrue("Expression should be of type WindowExpr", expr.getFirst() instanceof WindowExpr);
            assertEquals(WindowExpr.WindowType.TUMBLING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
            assertEquals("first", ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem().getStringValue());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
            assertEquals("second", ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem().getStringValue());
            assertEquals("last", ((WindowExpr) expr.getFirst()).getWindowEndCondition().getCurrentItem().getStringValue());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
            assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
            assertEquals("beyond", ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem().getStringValue());
            assertEquals(false, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
        }
    }
}
