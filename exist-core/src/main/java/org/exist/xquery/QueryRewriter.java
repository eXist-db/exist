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

/**
 * Base class to be implemented by an index module if it wants to rewrite
 * certain query expressions. Subclasses should overwrite the rewriteXXX methods
 * they are interested in. Each rewriteXXX method may return either:
 * <ul>
 *   <li>A {@link Pragma} that will be used to wrap the visited node in an
 *       {@link ExtensionExpression}, deferring the runtime decision (use index
 *       or fall through) to the pragma.</li>
 *   <li>{@code null}, meaning the rewriter declines to rewrite this node.</li>
 * </ul>
 * Rewriters may also modify the visited expression in place (e.g. replacing a
 * comparison with an Optimizable function call inside a predicate); the
 * surrounding optimizer continues with the modified subtree.
 *
 * @author Wolfgang Meier
 */
public class QueryRewriter {

    private final XQueryContext context;

    public QueryRewriter(XQueryContext context) {
        this.context = context;
    }

    /**
     * Rewrite a {@link LocationStep} expression to make use of indexes. The method
     * may also return an additional pragma to be added to the extension expression
     * which is inserted by the optimizer.
     *
     * @param locationStep the location step to rewrite
     * @return a pragma expression to wrap the step or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteLocationStep(LocationStep locationStep) throws XPathException {
        return null;
    }

    /**
     * Rewrite a {@link FilteredExpression} (e.g. {@code (A | B)[pred]} or
     * {@code $var[pred]}) to make use of indexes. Predicates on filtered
     * expressions are not visited by {@link #rewriteLocationStep}, so this hook
     * gives indexes a chance to optimize patterns like the union-with-predicate
     * case from issue #2363.
     *
     * @param filtered the filtered expression to rewrite
     * @return a pragma expression to wrap the expression or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteFilteredExpression(FilteredExpression filtered) throws XPathException {
        return null;
    }

    /**
     * Rewrite a {@link GeneralComparison} appearing outside a predicate (e.g.
     * inside a {@code where} clause or as the body of an {@code if} condition).
     * Most rewriters will decline unless the comparison's left-hand side is a
     * statically resolvable path with an applicable index.
     *
     * @param comparison the comparison to rewrite
     * @return a pragma expression to wrap the comparison or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteGeneralComparison(GeneralComparison comparison) throws XPathException {
        return null;
    }

    /**
     * Rewrite a {@link FunctionCall} to an index-backed equivalent. For example,
     * an n-gram rewriter could intercept {@code fn:contains(text, "x")} on an
     * n-gram-indexed path and substitute {@code ngram:contains(., "x")}.
     * Rewriters must respect the call's semantics (collation, case sensitivity,
     * tokenization model) and decline when the rewrite would not be a true
     * equivalence.
     *
     * @param call the function call to rewrite
     * @return a pragma expression to wrap the call or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteFunctionCall(FunctionCall call) throws XPathException {
        return null;
    }

    /**
     * Rewrite a {@link WhereClause} to make use of indexes. This hook lets
     * indexes intercept FLWOR where clauses for conversion to indexed lookups
     * before per-item evaluation begins.
     *
     * @param where the where clause to rewrite
     * @return a pragma expression to wrap the clause or null if not applicable
     * @throws XPathException in case of a static error
     */
    public Pragma rewriteWhereClause(WhereClause where) throws XPathException {
        return null;
    }

    protected XQueryContext getContext() {
        return context;
    }
}
