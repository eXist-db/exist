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
import org.exist.dom.persistent.ContextItem;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.ExtArrayNodeSet;
import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.persistent.NodeSet;
import org.exist.dom.persistent.VirtualNodeSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implements a where clause inside a FLWOR expressions.
 *
 * @author wolf
 */
public class WhereClause extends AbstractFLWORClause {

    protected Expression whereExpr;
    protected boolean fastTrack = false;

    public WhereClause(XQueryContext context, Expression whereExpr) {
        super(context);
        this.whereExpr = whereExpr;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.WHERE;
    }

    public Expression getWhereExpr() {
        return whereExpr;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE | NON_UPDATING_CONTEXT);
        newContextInfo.setContextId(getExpressionId());
        whereExpr.analyze(newContextInfo);

        newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        returnExpr.analyze(newContextInfo);
    }

    @Override
    public Sequence preEval(Sequence in) throws XPathException {
        if (in != null && Type.subTypeOf(in.getItemType(), Type.NODE) &&
                in.isPersistentSet() &&
                !Dependency.dependsOn(whereExpr, Dependency.CONTEXT_ITEM) &&
                //We might not be sure of the return type at this level
                Type.subTypeOf(whereExpr.returnsType(), Type.ITEM)) {
            if (!in.isCached()) {
                BindingExpression.setContext(getExpressionId(), in);
            }
            try {
                final Sequence seq = in.isEmpty() ? in : whereExpr.eval(in, null);
                //But *now*, we are ;-)
                if (Type.subTypeOf(whereExpr.returnsType(), Type.NODE)) {
                    final NodeSet nodes = seq.toNodeSet();
                    // if the where expression returns a node set, check the context
                    // node of each node in the set
                    final NodeSet contextSet = in.toNodeSet();
                    final boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
                    final NodeSet result = new ExtArrayNodeSet();
                    DocumentImpl lastDoc = null;

                    for (final NodeProxy current : nodes) {
                        int sizeHint = Constants.NO_SIZE_HINT;
                        if (lastDoc == null || current.getOwnerDocument() != lastDoc) {
                            lastDoc = current.getOwnerDocument();
                            sizeHint = nodes.getSizeHint(lastDoc);
                        }
                        ContextItem context = current.getContext();
                        if (context == null) {
                            throw new XPathException(this, "Internal evaluation error: context node is missing for node " +
                                    current.getNodeId() + "!");
                        }
                        //				LOG.debug(current.debugContext());
                        while (context != null) {
                            //TODO : Is this the context we want ? Not sure... would have prefered the LetExpr.
                            if (context.getContextId() == whereExpr.getContextId()) {
                                final NodeProxy contextNode = context.getNode();
                                if (contextIsVirtual || contextSet.contains(contextNode)) {
                                    contextNode.addMatches(current);
                                    result.add(contextNode, sizeHint);
                                }
                            }
                            context = context.getNextDirect();
                        }
                    }
                    fastTrack = true;
                    return result;
                }
            } finally {
                if (!in.isCached()) {
                    BindingExpression.clearContext(getExpressionId(), in);
                }
            }
        }
        return super.preEval(in);
    }

    @Override
    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        final List<NodeSet> carriers = new ArrayList<>();
        final boolean passesWhere = applyWhereExpression(carriers);
        if (passesWhere) {
            propagateMatchCarriersForCurrentTuple(carriers);
            return returnExpr.eval(null, null);
        }
        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public Sequence postEval(Sequence seq) throws XPathException {
        fastTrack = false;
        if (returnExpr instanceof FLWORClause flworClause) {
            seq = flworClause.postEval(seq);
        }
        return super.postEval(seq);
    }

    private boolean applyWhereExpression(final List<NodeSet> carriers) throws XPathException {
        if (fastTrack) {
            return true;
        }

        final Expression unwrappedWhereExpr = unwrapBooleanExpression(whereExpr);
        if (unwrappedWhereExpr instanceof OpAnd opAnd) {
            return evalExpressionWithCarriers(opAnd, carriers);
        }

        final Sequence innerSeq = whereExpr.eval(null, null);
        captureCarrierFromSequence(whereExpr, innerSeq, carriers);
        return innerSeq.effectiveBooleanValue();
    }

    private boolean evalExpressionWithCarriers(final Expression expr, final List<NodeSet> carriers) throws XPathException {
        final Expression unwrappedExpr = unwrapBooleanExpression(expr);
        if (unwrappedExpr instanceof OpAnd opAnd) {
            if (!evalExpressionWithCarriers(opAnd.getLeft(), carriers)) {
                return false;
            }
            return evalExpressionWithCarriers(opAnd.getRight(), carriers);
        }

        final Sequence seq = expr.eval(null, null);
        captureCarrierFromSequence(expr, seq, carriers);
        return seq.effectiveBooleanValue();
    }

    private void captureCarrierFromSequence(final Expression expr, final Sequence seq, final List<NodeSet> carriers) throws XPathException {
        if (seq.isEmpty() || !seq.isPersistentSet() || !Type.subTypeOf(seq.getItemType(), Type.NODE)) {
            return;
        }
        final Expression unwrappedExpr = unwrapBooleanExpression(expr);
        if (!Type.subTypeOf(unwrappedExpr.returnsType(), Type.NODE) ||
                Dependency.dependsOn(unwrappedExpr, Dependency.CONTEXT_ITEM)) {
            return;
        }

        carriers.add(seq.toNodeSet());
    }

    private void propagateMatchCarriersForCurrentTuple(final List<NodeSet> carriers) throws XPathException {
        if (fastTrack || carriers.isEmpty()) {
            return;
        }

        LocalVariable startVar = getStartVariable();
        if (startVar == null) {
            final FLWORClause prev = getPreviousClause();
            if (prev != null) {
                // In some wrapped FLWOR shapes (e.g. XQSuite + debug wrappers), the
                // tuple variable is attached to the previous clause, not this where.
                startVar = prev.getStartVariable();
            }
        }
        if (startVar == null) {
            return;
        }
        final Sequence tupleSeq = startVar.getValue();
        if (tupleSeq == null || tupleSeq.isEmpty() || !tupleSeq.isPersistentSet() ||
                !Type.subTypeOf(tupleSeq.getItemType(), Type.NODE)) {
            return;
        }

        final NodeSet contextSet = tupleSeq.toNodeSet();
        for (final NodeSet carrier : carriers) {
            propagateToContext(carrier, contextSet);
        }
    }

    private void propagateToContext(final NodeSet carrierNodes, final NodeSet contextSet) {
        final boolean contextIsVirtual = contextSet instanceof VirtualNodeSet;
        for (final NodeProxy current : carrierNodes) {
            ContextItem context = current.getContext();
            while (context != null) {
                final NodeProxy contextNode = context.getNode();
                if (contextIsVirtual || contextSet.contains(contextNode)) {
                    contextNode.addMatches(current);
                }
                context = context.getNextDirect();
            }
        }
    }

    private static Expression unwrapBooleanExpression(final Expression expr) {
        Expression current = expr;
        boolean changed = true;
        while (changed) {
            changed = false;
            if (current instanceof DebuggableExpression debugExpr) {
                current = debugExpr.getFirst();
                changed = true;
            }
            if (current instanceof PathExpr pathExpr && pathExpr.getSubExpressionCount() == 1) {
                current = pathExpr.getSubExpression(0);
                changed = true;
            }
        }
        return current;
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        visitor.visitWhereClause(this);
    }

    @Override
    public void dump(ExpressionDumper dumper) {
        dumper.display("where", whereExpr.getLine());
        dumper.startIndent();
        whereExpr.dump(dumper);
        dumper.endIndent().nl();
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        whereExpr.resetState(postOptimization);
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