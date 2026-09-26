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
package org.exist.xquery.xquf;

import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * W3C XQuery Update Facility 3.0 - delete expression.
 *
 * <pre>
 * DeleteExpr ::= "delete" ("node" | "nodes") TargetExpr
 * </pre>
 */
public class XQUFDeleteExpr extends AbstractExpression {

    private final Expression target;

    public XQUFDeleteExpr(final XQueryContext context, final Expression target) {
        super(context);
        this.target = target;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.hasFlag(NON_UPDATING_CONTEXT)) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "delete expression is not allowed in a non-updating context");
        }
        // Target expression of delete is a non-updating context
        final AnalyzeContextInfo subInfo = new AnalyzeContextInfo(contextInfo);
        subInfo.setParent(this);
        subInfo.addFlag(IN_UPDATE);
        subInfo.addFlag(NON_UPDATING_CONTEXT);
        target.analyze(subInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;
        final Sequence targetSeq = target.eval(ctxSeq, null);

        if (!targetSeq.isEmpty()) {
            final PendingUpdateList pul = context.getPendingUpdateList();
            for (final SequenceIterator i = targetSeq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                    throw new XPathException(this, ErrorCodes.XUTY0007,
                            "Target of delete expression must be a node.");
                }
                final NodeValue nv = (NodeValue) item;
                pul.addPrimitive(UpdatePrimitive.delete(nv.getNode(), this));
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    @Override
    public boolean isUpdating() {
        return true;
    }

    @Override
    public int returnsType() {
        return Type.EMPTY_SEQUENCE;
    }

    @Override
    public Cardinality getCardinality() {
        return Cardinality.EMPTY_SEQUENCE;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        target.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("delete node ");
        target.dump(dumper);
    }

    @Override
    public String toString() {
        return "delete node " + target.toString();
    }
}
