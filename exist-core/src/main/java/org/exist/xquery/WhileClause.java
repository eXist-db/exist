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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import java.util.HashSet;
import java.util.Set;

/**
 * Implements the XQuery 4.0 while clause in FLWOR expressions.
 *
 * <p>The while clause evaluates a condition for each tuple in the stream.
 * If the condition is true, the tuple is retained; if false, the tuple
 * and all subsequent tuples are discarded (iteration stops).</p>
 */
public class WhileClause extends AbstractFLWORClause {

    /**
     * Thread-local flag that signals all enclosing binding expressions
     * in the same FLWOR to stop iteration after the current item.
     */
    private static final ThreadLocal<Boolean> terminated = ThreadLocal.withInitial(() -> false);

    private final Expression whileExpr;

    /**
     * Lightweight control-flow exception used to signal the immediately
     * enclosing for/let binding expression to stop iteration.
     */
    public static class WhileTerminationException extends XPathException {
        public WhileTerminationException() {
            super((Expression) null, "while clause terminated");
        }
    }

    public static boolean isTerminated() {
        return terminated.get();
    }

    public static void clearTerminated() {
        terminated.set(false);
    }

    public WhileClause(final XQueryContext context, final Expression whileExpr) {
        super(context);
        this.whileExpr = whileExpr;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.WHILE;
    }

    public Expression getWhileExpr() {
        return whileExpr;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
        newContextInfo.setContextId(getExpressionId());
        whileExpr.analyze(newContextInfo);

        final AnalyzeContextInfo returnContextInfo = new AnalyzeContextInfo(contextInfo);
        returnContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        returnExpr.analyze(returnContextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence condResult = whileExpr.eval(null, null);
        if (condResult.effectiveBooleanValue()) {
            return returnExpr.eval(null, null);
        }
        terminated.set(true);
        throw new WhileTerminationException();
    }

    @Override
    public Sequence postEval(final Sequence seq) throws XPathException {
        if (returnExpr instanceof FLWORClause flworClause) {
            return flworClause.postEval(seq);
        }
        return super.postEval(seq);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("while", whileExpr.getLine());
        dumper.startIndent();
        whileExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        whileExpr.resetState(postOptimization);
        returnExpr.resetState(postOptimization);
    }

    @Override
    public Set<QName> getTupleStreamVariables() {
        final Set<QName> vars = new HashSet<>();
        final LocalVariable startVar = getStartVariable();
        if (startVar != null) {
            vars.add(startVar.getQName());
        }
        return vars;
    }
}
