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
package org.exist.xquery.lock;

import org.exist.dom.QName;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.Type;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/**
 * Walks the compiled expression tree to statically determine which
 * documents and collections a query will access. This enables
 * "preclaiming" locks before query evaluation begins, following the
 * BaseX approach of preclaiming two-phase locking.
 *
 * <p>Static targets (string literal arguments to fn:doc, fn:collection)
 * are collected as specific URIs. If any document/collection reference
 * is dynamic (non-literal argument), {@link #requiresGlobalLock()} returns
 * true, indicating that a global write lock is needed as a safe fallback.</p>
 *
 * <p>Traversal strategy: each visit method handles the specific expression
 * type and then calls {@link #traverseSubExpressions(Expression)} to
 * recurse into children. This ensures the entire tree is walked even for
 * expression types that don't explicitly delegate in their {@code accept()}
 * method.</p>
 */
public class LockTargetCollector extends BasicExpressionVisitor {

    private static final String FN_NS = Function.BUILTIN_FUNCTION_NS;

    private final Set<XmldbURI> documentTargets = new TreeSet<>();
    private final Set<XmldbURI> collectionTargets = new TreeSet<>();
    private boolean globalLockRequired = false;

    /**
     * Collect lock targets from the given root expression.
     *
     * @param root the compiled expression tree root
     */
    public void collect(final Expression root) {
        root.accept(this);
    }

    // --- Visitor methods ---

    @Override
    public void visit(final Expression expression) {
        // Fallback for expression types without a specific visitor method.
        traverseSubExpressions(expression);
    }

    @Override
    public void visitBuiltinFunction(final Function function) {
        final QName name = function.getSignature().getName();
        if (FN_NS.equals(name.getNamespaceURI())) {
            switch (name.getLocalPart()) {
                case "doc":
                case "doc-available":
                    collectDocTarget(function);
                    break;
                case "collection":
                case "uri-collection":
                    collectCollectionTarget(function);
                    break;
                default:
                    break;
            }
        }
        // Traverse function arguments to find nested fn:doc/fn:collection calls
        traverseSubExpressions(function);
    }

    @Override
    public void visitFunctionCall(final FunctionCall call) {
        // User-defined function call — traverse arguments
        traverseSubExpressions(call);
    }

    @Override
    public void visitPathExpr(final PathExpr expression) {
        for (int i = 0; i < expression.getLength(); i++) {
            expression.getExpression(i).accept(this);
        }
    }

    @Override
    public void visitUnionExpr(final Union union) {
        union.getLeft().accept(this);
        union.getRight().accept(this);
    }

    @Override
    public void visitIntersectionExpr(final Intersect intersect) {
        intersect.getLeft().accept(this);
        intersect.getRight().accept(this);
    }

    @Override
    public void visitForExpression(final ForExpr forExpr) {
        forExpr.getInputSequence().accept(this);
        final Expression returnExpr = forExpr.getReturnExpression();
        if (returnExpr != null) {
            returnExpr.accept(this);
        }
    }

    @Override
    public void visitLetExpression(final LetExpr letExpr) {
        letExpr.getInputSequence().accept(this);
        final Expression returnExpr = letExpr.getReturnExpression();
        if (returnExpr != null) {
            returnExpr.accept(this);
        }
    }

    @Override
    public void visitConditional(final ConditionalExpression conditional) {
        conditional.getTestExpr().accept(this);
        conditional.getThenExpr().accept(this);
        conditional.getElseExpr().accept(this);
    }

    @Override
    public void visitTryCatch(final TryCatchExpression tryCatch) {
        tryCatch.getTryTargetExpr().accept(this);
    }

    @Override
    public void visitFilteredExpr(final FilteredExpression filtered) {
        final Expression inner = filtered.getExpression();
        if (inner != null) {
            inner.accept(this);
        }
        traverseSubExpressions(filtered);
    }

    @Override
    public void visitWhereClause(final WhereClause where) {
        traverseSubExpressions(where);
        final Expression returnExpr = where.getReturnExpression();
        if (returnExpr != null) {
            returnExpr.accept(this);
        }
    }

    @Override
    public void visitOrderByClause(final OrderByClause orderBy) {
        traverseSubExpressions(orderBy);
        final Expression returnExpr = orderBy.getReturnExpression();
        if (returnExpr != null) {
            returnExpr.accept(this);
        }
    }

    @Override
    public void visitGroupByClause(final GroupByClause groupBy) {
        traverseSubExpressions(groupBy);
        final Expression returnExpr = groupBy.getReturnExpression();
        if (returnExpr != null) {
            returnExpr.accept(this);
        }
    }

    // --- Results ---

    /**
     * Returns the set of document URIs found as static string literals
     * in fn:doc() or fn:doc-available() calls.
     */
    public Set<XmldbURI> getDocumentTargets() {
        return Collections.unmodifiableSet(documentTargets);
    }

    /**
     * Returns the set of collection URIs found as static string literals
     * in fn:collection() or fn:uri-collection() calls.
     */
    public Set<XmldbURI> getCollectionTargets() {
        return Collections.unmodifiableSet(collectionTargets);
    }

    /**
     * Returns true if any document/collection reference could not be
     * resolved statically, requiring a global write lock as a safe fallback.
     */
    public boolean requiresGlobalLock() {
        return globalLockRequired;
    }

    /**
     * Returns true if any targets were collected or a global lock is required.
     */
    public boolean hasTargets() {
        return !documentTargets.isEmpty() || !collectionTargets.isEmpty() || globalLockRequired;
    }

    // --- Internal ---

    private void collectDocTarget(final Function function) {
        if (function.getArgumentCount() == 0) {
            return;
        }
        final String uri = extractStaticStringArg(function.getArgument(0));
        if (uri != null) {
            try {
                documentTargets.add(XmldbURI.xmldbUriFor(uri));
            } catch (final Exception e) {
                // Malformed URI — can't preclaim, fall back to global
                globalLockRequired = true;
            }
        } else {
            // Dynamic argument — can't determine target statically
            globalLockRequired = true;
        }
    }

    private void collectCollectionTarget(final Function function) {
        if (function.getArgumentCount() == 0) {
            // Zero-arg fn:collection() — accesses context, need global lock
            globalLockRequired = true;
            return;
        }
        final String uri = extractStaticStringArg(function.getArgument(0));
        if (uri != null) {
            try {
                collectionTargets.add(XmldbURI.xmldbUriFor(uri));
            } catch (final Exception e) {
                globalLockRequired = true;
            }
        } else {
            globalLockRequired = true;
        }
    }

    /**
     * Try to extract a static string value from an expression.
     * Returns the string if the expression is a string/anyURI literal,
     * or null if it's dynamic.
     */
    private String extractStaticStringArg(final Expression expr) {
        final Expression unwrapped = unwrap(expr);
        if (unwrapped instanceof LiteralValue literalValue) {
            final AtomicValue val = literalValue.getValue();
            if (Type.subTypeOf(val.getType(), Type.STRING) ||
                    Type.subTypeOf(val.getType(), Type.ANY_URI)) {
                try {
                    return val.getStringValue();
                } catch (final Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * Unwrap transparent expression wrappers to get to the underlying value.
     */
    private Expression unwrap(final Expression expr) {
        Expression current = expr;
        while (true) {
            if (current instanceof PathExpr pathExpr && pathExpr.getLength() == 1) {
                current = pathExpr.getExpression(0);
            } else if (current instanceof final DynamicCardinalityCheck check) {
                current = check.getSubExpression(0);
            } else if (current instanceof final DynamicTypeCheck check) {
                current = check.getSubExpression(0);
            } else if (current instanceof final UntypedValueCheck check) {
                current = check.getSubExpression(0);
            } else if (current instanceof final Atomize atomize) {
                current = atomize.getExpression();
            } else {
                return current;
            }
        }
    }

    /**
     * Generic traversal of an expression's sub-expressions using
     * the {@link Expression#getSubExpressionCount()} /
     * {@link Expression#getSubExpression(int)} API.
     */
    private void traverseSubExpressions(final Expression expression) {
        final int count = expression.getSubExpressionCount();
        for (int i = 0; i < count; i++) {
            final Expression sub = expression.getSubExpression(i);
            if (sub != null) {
                sub.accept(this);
            }
        }
    }
}
