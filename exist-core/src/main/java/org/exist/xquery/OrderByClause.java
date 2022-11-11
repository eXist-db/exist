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
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.Sequence;

import java.util.*;

/**
 * Represents an "order by" clause within a FLWOR expression.
 */
public class OrderByClause extends AbstractFLWORClause {

    private final List<OrderSpec> orderSpecs;

    /*  OrderByClause needs to keep state between calls to eval and postEval. We thus need
        to track state in a stack to avoid overwrites if we're called recursively. */
    private final Deque<OrderedValueSequence> stack = new ArrayDeque<>();

    public OrderByClause(final XQueryContext context, final List<OrderSpec> orderSpecs) {
        super(context);
        this.orderSpecs = orderSpecs;
    }

    public List<OrderSpec> getOrderSpecs() {
        return orderSpecs;
    }

    @Override
    public ClauseType getType() {
        return ClauseType.ORDERBY;
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        unordered = (contextInfo.getFlags() & UNORDERED) > 0;
        final AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
        newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
        if (orderSpecs != null) {
            for (OrderSpec spec: orderSpecs) {
                spec.analyze(newContextInfo);
            }
        }
        returnExpr.analyze(newContextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        OrderedValueSequence orderedResult = stack.pollFirst();
        if (orderedResult == null) {
            orderedResult = new OrderedValueSequence(orderSpecs, 100);
        }

        final Sequence result = getReturnExpression().eval(contextSequence, contextItem);
        if (result != null) {
            orderedResult.setContextSequence(contextSequence);
            orderedResult.addAll(result);
            orderedResult.setContextSequence(null);
        }

        stack.addFirst(orderedResult);

        return result;
    }

    @Override
    public Sequence postEval(final Sequence seq) throws XPathException {
        final OrderedValueSequence orderedResult = stack.pollFirst();
        if (orderedResult == null) {
            return seq;
        }

        orderedResult.sort();
        Sequence result = orderedResult;

        if (getReturnExpression() instanceof FLWORClause flworClause) {
            result = flworClause.postEval(result);
        }
        return super.postEval(result);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("order by ");
        for (int i = 0; i < orderSpecs.size(); i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            dumper.display(orderSpecs.get(i));
        }
        dumper.nl();
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("order by ");
        for (int i = 0; i < orderSpecs.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(orderSpecs.get(i));
        }
        return builder.toString();
    }

    @Override
    public void accept(ExpressionVisitor visitor) {
        visitor.visitOrderByClause(this);
    }

    @Override
    public void resetState(boolean postOptimization) {
        super.resetState(postOptimization);
        returnExpr.resetState(postOptimization);
        stack.clear();
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
