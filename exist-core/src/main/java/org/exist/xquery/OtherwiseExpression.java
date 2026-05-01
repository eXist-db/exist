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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;

/**
 * Implements the XQuery 4.0 "otherwise" operator.
 *
 * {@code E1 otherwise E2} returns E1 if it is non-empty, otherwise E2.
 */
public class OtherwiseExpression extends AbstractExpression {

    private Expression left;
    private Expression right;

    public OtherwiseExpression(final XQueryContext context, final Expression left, final Expression right) {
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
        if (leftResult != null && !leftResult.isEmpty()) {
            return leftResult;
        }
        return right.eval(contextSequence, null);
    }

    @Override
    public int returnsType() {
        return left.returnsType();
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.ZERO_OR_MORE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        left.dump(dumper);
        dumper.display(" otherwise ");
        right.dump(dumper);
    }

    @Override
    public String toString() {
        return left.toString() + " otherwise " + right.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        left.resetState(postOptimization);
        right.resetState(postOptimization);
    }
}
