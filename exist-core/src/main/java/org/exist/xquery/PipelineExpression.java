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

import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements the XQuery 4.0 pipeline operator "->".
 *
 * The expression {@code E1 -> E2} evaluates E1, then evaluates E2 with the
 * result of E1 as the context value, position 1, and last 1.
 */
public class PipelineExpression extends AbstractExpression {

    private Expression left;
    private Expression right;

    public PipelineExpression(final XQueryContext context, final Expression left, final Expression right) {
        super(context);
        this.left = left;
        this.right = right;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        left.analyze(new AnalyzeContextInfo(contextInfo));
        right.analyze(new AnalyzeContextInfo(contextInfo));
    }

    @Override
    public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (contextItem != null) {
            contextSequence = contextItem.toSequence();
        }
        final Sequence leftResult = left.eval(contextSequence, null);

        // Pipeline: set context position=0 (position()=1) and a single-item
        // context sequence so last()=1, per XQ4 spec.
        final Sequence singletonContext;
        if (leftResult.isEmpty()) {
            singletonContext = Sequence.EMPTY_SEQUENCE;
        } else {
            singletonContext = new ValueSequence(1);
            singletonContext.add(leftResult.itemAt(0));
        }
        final int savedPos = context.getContextPosition();
        final Sequence savedSeq = context.getContextSequence();
        context.setContextSequencePosition(0, singletonContext);
        try {
            return right.eval(leftResult, null);
        } finally {
            context.setContextSequencePosition(savedPos, savedSeq);
        }
    }

    @Override
    public int returnsType() {
        return right.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        left.dump(dumper);
        dumper.display(" -> ");
        right.dump(dumper);
    }

    @Override
    public String toString() {
        return left.toString() + " -> " + right.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        left.resetState(postOptimization);
        right.resetState(postOptimization);
    }
}
