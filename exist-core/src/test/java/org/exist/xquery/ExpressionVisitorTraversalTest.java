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

import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.junit.ClassRule;
import org.junit.Test;

import antlr.collections.AST;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Tests that {@link DefaultExpressionVisitor} traverses into all
 * expression types that have custom {@code accept()} methods.
 *
 * <p>Without the corresponding fix, all tests fail because the visitor
 * stops at the expression boundary and never reaches the function
 * calls inside.</p>
 */
public class ExpressionVisitorTraversalTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(true, true);

    @Test
    public void generalComparison() throws Exception {
        assertFindsFunction("upper-case('test') = 'TEST'", "upper-case");
    }

    @Test
    public void andExpr() throws Exception {
        assertFindsFunction("true() and false()", "true");
    }

    @Test
    public void orExpr() throws Exception {
        assertFindsFunction("true() or false()", "true");
    }

    @Test
    public void castExpr() throws Exception {
        assertFindsFunction("number('42') cast as xs:integer", "number");
    }

    @Test
    public void filterExpr() throws Exception {
        assertFindsFunction("(1, 2, 3)[last()]", "last");
    }

    // ==================== Helper ====================

    private void assertFindsFunction(final String xquery, final String expectedFunction)
            throws Exception {

        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.getBroker()) {
            final XQueryContext context = new XQueryContext(pool);
            try {
                final XQueryLexer lexer = new XQueryLexer(context, new StringReader(xquery));
                final XQueryParser parser = new XQueryParser(lexer);
                parser.xpath();
                assertFalse("Parse error: " + xquery, parser.foundErrors());

                final AST ast = parser.getAST();
                final PathExpr path = new PathExpr(context);
                new XQueryTreeParser(context).xpath(ast, path);

                try { path.analyze(new AnalyzeContextInfo()); }
                catch (final Exception ignored) { }

                final FunctionNameCollector collector = new FunctionNameCollector();
                path.accept(collector);

                assertTrue(
                        "Expected '" + expectedFunction + "' in: " + xquery +
                                " — found: " + collector.names,
                        collector.names.contains(expectedFunction));
            } finally {
                context.runCleanupTasks();
                context.reset(false);
            }
        }
    }

    static class FunctionNameCollector extends DefaultExpressionVisitor {
        final List<String> names = new ArrayList<>();

        @Override
        public void visitFunctionCall(final FunctionCall call) {
            names.add(call.getSignature().getName().getLocalPart());
            super.visitFunctionCall(call);
        }

        @Override
        public void visitBuiltinFunction(final Function function) {
            names.add(function.getSignature().getName().getLocalPart());
            super.visitBuiltinFunction(function);
        }
    }
}
