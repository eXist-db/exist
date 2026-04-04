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
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import javax.annotation.Nullable;

/**
 * An XQuery range expression, like "1 to 10".
 * Has a XQuery 1.0 compatibility mode when parsing boundaries.
 */
public class RangeExpression extends PathExpr {

    final Expression start;
    final Expression end;

    public RangeExpression(final XQueryContext context, final Expression startExpr, final Expression endExpr) {
        super(context);
        start = startExpr;
        end = endExpr;
    }

    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        inPredicate = (contextInfo.getFlags() & IN_PREDICATE) > 0;
        contextId = contextInfo.getContextId();
        contextInfo.setParent(this);
        // Operands of range expression are non-updating contexts
        final AnalyzeContextInfo startInfo = new AnalyzeContextInfo(contextInfo);
        startInfo.addFlag(NON_UPDATING_CONTEXT);
        start.analyze(startInfo);
        final AnalyzeContextInfo endInfo = new AnalyzeContextInfo(contextInfo);
        endInfo.addFlag(NON_UPDATING_CONTEXT);
        end.analyze(endInfo);
    }

    /**
     * Evaluate range boundary expressions and return the resulting RangeSequence
     * @param contextSequence the current context sequence, or null if there is no context sequence.
     * @param contextItem a single item, taken from context, or null if there is no context item.
     *                    This defines the item, the expression should work on.
     *
     * @return A sequence of integer values between start and end or an empty sequence when either start or end is empty
     * @throws XPathException raises XPTY0004 or FORG0006 if start or end have the wrong cardinality or type
     */
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence startSeq = start.eval(contextSequence, contextItem);
        final Sequence endSeq = end.eval(contextSequence, contextItem);

        if (startSeq.isEmpty() || endSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        if (startSeq.hasMany()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "The first operand must have at most one item", startSeq);
        }
        if (endSeq.hasMany()) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "The second operand must have at most one item", endSeq);
        }

        final IntegerValue valueStart;
        final IntegerValue valueEnd;

		if (context.isBackwardsCompatible()) {
            valueStart = getLegacyBoundaryValue(startSeq);
            valueEnd = getLegacyBoundaryValue(endSeq);
            return new RangeSequence(valueStart, valueEnd);
        }

		valueStart = getBoundaryValue(startSeq);
		valueEnd = getBoundaryValue(endSeq);
		return new RangeSequence(valueStart, valueEnd);
    }

    private IntegerValue getBoundaryValue(final Sequence boundarySeq) throws XPathException {
        final Item boundaryItem = boundarySeq.itemAt(0);
        final AtomicValue atomizedBoundary = boundaryItem.atomize();
        if (!(Type.subTypeOf(atomizedBoundary.getType(), Type.INTEGER)
			|| Type.subTypeOf(atomizedBoundary.getType(), Type.UNTYPED_ATOMIC))) {
            throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
                    Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(boundaryItem.getType()) + "(" +
                    boundaryItem.getStringValue() + ")'", boundarySeq);
        }
        return (IntegerValue) boundaryItem.convertTo(Type.INTEGER);
    }

    @Nullable
    private IntegerValue getLegacyBoundaryValue(Sequence startSeq) throws XPathException {
        final NumericValue valueStart;
        try {
            // Currently breaks 1e3 to 3
            valueStart = (NumericValue) startSeq.itemAt(0).convertTo(Type.NUMERIC);
        } catch (final XPathException e) {
            throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
                    Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(startSeq.itemAt(0).getType()) + "(" +
                    startSeq.itemAt(0).getStringValue() + ")'", startSeq);
        }
        // Implied by previous conversion
        if (valueStart.hasFractionalPart()) {
            throw new XPathException(this, ErrorCodes.FORG0006, "Required type is " +
                    Type.getTypeName(Type.INTEGER) + " but got '" + Type.getTypeName(startSeq.itemAt(0).getType()) + "(" +
                    startSeq.itemAt(0).getStringValue() + ")'", startSeq);
        }
        return (IntegerValue) valueStart.convertTo(Type.INTEGER);
    }

    public void dump(ExpressionDumper dumper) {
        dumper.display(start);
        dumper.display(" to ");
        dumper.display(end);
    }

    public String toString() {
        return "(" + start + " to " + end + ")";
    }

    public int returnsType() {
        return Type.INTEGER;
    }

    public Expression simplify() {
        return this;
    }
}
