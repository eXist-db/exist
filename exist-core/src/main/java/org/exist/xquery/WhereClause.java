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
import org.exist.dom.persistent.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.util.HashSet;
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
        newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
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
        if (applyWhereExpression()) {
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

    private boolean applyWhereExpression() throws XPathException {
        if (fastTrack) {
            return true;
        }
        final Sequence innerSeq = whereExpr.eval(null, null);
        return innerSeq.effectiveBooleanValue();
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