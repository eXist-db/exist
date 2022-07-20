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
import org.exist.dom.QName;
import org.exist.xquery.functions.fn.FnModule;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.AnyURIValue;
import org.junit.jupiter.api.Test;

import java.io.StringReader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * @author <a href="adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="gabriele@strumenta.com">Gabriele Tomassetti</a>
 */
public class WindowClauseTest {

    @Test
    public void simpleWindowConditions() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
    }

    @Test
    public void complexWindowCondition() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "   start $first next $second when $first/price < $second/price\n" +
                "   end $last next $beyond when $last/price > $beyond/price\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
    }

    @Test
    public void noEndWindowCondition() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "   start $first next $second when $first/price < $second/price\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
    }

    @Test
    public void slidingWindowClause() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
        assertEquals(WindowExpr.WindowType.SLIDING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
    }

    @Test
    public void allWindowsVars() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "start $first at $s previous $start-previous next $start-next when fn:true()\n" +
                "only end $last at $e previous $end-previous next $end-next when $e - $s eq 2\n" +
                "return\n" +
                "  <window>{$first, $last}</window>\n";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
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

    @Test
    public void tumblingWindowAllWindowVarsNoOnly() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10)\n" +
                "    start $s at $spos previous $sprev next $snext when fn:true() \n" +
                "    end $e at $epos previous $eprev next $enext when fn:true()\n" +
                "return\n" +
                "  <window>{$first, $last}</window>\n";

            // parse the query into the internal syntax tree
            final XQueryContext context = new XQueryContext();
            context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

            assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
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

    @Test
    public void tumblingWindowAvgReturn() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return avg($w)";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
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

    @Test
    public void tumblingWindowNoEndWindowConditionPositional() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when $s mod 3 = 1\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
        assertEquals(new QName("s"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition());
    }

    @Test
    public void tumblingWindowNoEndWindowConditionCurrentItem() throws RecognitionException, XPathException, TokenStreamException {
        final String query = "xquery version \"3.1\";\n" +
                "for tumbling window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start $first when $first mod 3 = 0\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
        assertEquals("first", ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem().getStringValue());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition());
    }

    @Test
    public void slidingWindowAvgReturn() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    only end at $e when $e - $s eq 2\n" +
                "return avg($w)";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
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

    @Test
    public void slidingWindowEndWithoutOnly() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
        final String query = "xquery version \"3.1\";\n" +
                "for sliding window $w in (2, 4, 6, 8, 10, 12, 14)\n" +
                "    start at $s when fn:true()\n" +
                "    end at $e when $e - $s eq 2\n" +
                "return <window>{ $w }</window>";

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
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

    @Test
    public void tumblingWindowRunUp() throws RecognitionException, XPathException, TokenStreamException, QName.IllegalQNameException {
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

        // parse the query into the internal syntax tree
        final XQueryContext context = new XQueryContext();
        context.importModule(Function.BUILTIN_FUNCTION_NS, "fn", new AnyURIValue[] { new AnyURIValue("java:" + FnModule.class.getName()) });
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

        assertTrue(expr.getFirst() instanceof WindowExpr, "Expression should be of type WindowExpr");
        assertEquals(WindowExpr.WindowType.TUMBLING_WINDOW, ((WindowExpr) expr.getFirst()).getWindowType());
        assertEquals(new QName("first"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getCurrentItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPosVar());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowStartCondition().getPreviousItem());
        assertEquals(new QName("second"), ((WindowExpr) expr.getFirst()).getWindowStartCondition().getNextItem());
        assertEquals(new QName("last"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getCurrentItem());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPosVar());
        assertEquals(null, ((WindowExpr) expr.getFirst()).getWindowEndCondition().getPreviousItem());
        assertEquals(new QName("beyond"), ((WindowExpr) expr.getFirst()).getWindowEndCondition().getNextItem());
        assertEquals(false, ((WindowExpr) expr.getFirst()).getWindowEndCondition().isOnly());
    }
}
