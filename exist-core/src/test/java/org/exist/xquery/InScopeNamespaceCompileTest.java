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

import java.io.StringReader;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;

import antlr.collections.AST;

import org.junit.ClassRule;
import org.junit.Test;

/**
 * Verifies that namespaces registered on the static context via
 * {@link XQueryContext#declareInScopeNamespace(String, String)} before
 * compilation are visible to path-step QName resolution. The XQTS runner
 * relies on this to apply &lt;environment&gt;/&lt;namespace&gt; declarations
 * (e.g. the "atomic" environment) without rewriting test queries.
 *
 * <p>Regression test for set-operator XPST0081 failures discovered in the
 * XQ 3.1 develop gap analysis (op-intersect, op-union, op-except).
 */
public class InScopeNamespaceCompileTest {

    @ClassRule
    public static final ExistEmbeddedServer server = new ExistEmbeddedServer(true, true);

    private static final String ATOMIC_NS = "http://www.w3.org/XQueryTest";

    private void compileWithAtomicPrefix(final String query) throws Exception {
        final BrokerPool pool = BrokerPool.getInstance();
        final XQueryContext context = new XQueryContext(pool);
        context.declareInScopeNamespace("atomic", ATOMIC_NS);

        final XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
        final XQueryParser xparser = new XQueryParser(lexer);
        xparser.xpath();
        if (xparser.foundErrors()) {
            throw new AssertionError(xparser.getErrorMessage());
        }
        final AST ast = xparser.getAST();
        final XQueryTreeParser treeParser = new XQueryTreeParser(context);
        final PathExpr expr = new PathExpr(context);
        treeParser.xpath(ast, expr);
        if (treeParser.foundErrors()) {
            throw new AssertionError(treeParser.getErrorMessage());
        }
    }

    @Test
    public void intersectResolvesPrefix()
            throws EXistException, PermissionDeniedException, Exception {
        // mirrors XQTS fn-intersect-node-args-016
        compileWithAtomicPrefix("(/atomic:root/atomic:integer) intersect (/atomic:root/atomic:integer)");
    }

    @Test
    public void unionResolvesPrefix() throws Exception {
        // mirrors XQTS fn-union-node-args-016
        compileWithAtomicPrefix("(/atomic:root/atomic:integer) union (/atomic:root/atomic:integer)");
    }

    @Test
    public void exceptResolvesPrefix() throws Exception {
        // mirrors XQTS fn-except-node-args-016
        compileWithAtomicPrefix("(/atomic:root/atomic:integer) except (/atomic:root/atomic:integer)");
    }

    @Test
    public void plainPathResolvesPrefix() throws Exception {
        // baseline: a plain path expression must work too
        compileWithAtomicPrefix("/atomic:root/atomic:integer");
    }
}
