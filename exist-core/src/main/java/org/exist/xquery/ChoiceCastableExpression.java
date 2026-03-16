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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Implements castable as (T1 | T2 | ...) from XQuery 4.0.
 * Returns true if the value can be cast to any of the target types.
 */
public class ChoiceCastableExpression extends AbstractExpression {

    private final int[] targetTypes;
    private final Cardinality requiredCardinality;
    private final Expression expression;

    public ChoiceCastableExpression(final XQueryContext context, final Expression expr,
                                    final int[] targetTypes, final Cardinality requiredCardinality) {
        super(context);
        this.expression = expr;
        this.targetTypes = targetTypes;
        this.requiredCardinality = requiredCardinality;
    }

    @Override
    public int returnsType() {
        return Type.BOOLEAN;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EXACTLY_ONE;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence seq = Atomize.atomize(expression.eval(contextSequence, contextItem));
        if (seq.isEmpty()) {
            return BooleanValue.valueOf(
                    requiredCardinality.isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE));
        }
        if (!requiredCardinality.isSuperCardinalityOrEqualOf(seq.getCardinality())) {
            return BooleanValue.FALSE;
        }

        final Item item = seq.itemAt(0);
        for (final int targetType : targetTypes) {
            try {
                item.convertTo(targetType);
                return BooleanValue.TRUE;
            } catch (final XPathException e) {
                // try next type
            }
        }
        return BooleanValue.FALSE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(" castable as (");
        for (int i = 0; i < targetTypes.length; i++) {
            if (i > 0) {
                dumper.display(" | ");
            }
            dumper.display(Type.getTypeName(targetTypes[i]));
        }
        dumper.display(")");
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(expression.toString()).append(" castable as (");
        for (int i = 0; i < targetTypes.length; i++) {
            if (i > 0) {
                sb.append(" | ");
            }
            sb.append(Type.getTypeName(targetTypes[i]));
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }
}
