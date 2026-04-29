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
import org.w3c.dom.Node;

/**
 * W3C XQuery Update Facility 3.0 - replace value of node expression.
 *
 * <pre>
 * ReplaceExpr ::= "replace" "value" "of" "node" TargetExpr "with" ExprSingle
 * </pre>
 */
public class XQUFReplaceValueExpr extends AbstractExpression {

    private final Expression target;
    private final Expression value;

    public XQUFReplaceValueExpr(final XQueryContext context, final Expression target, final Expression value) {
        super(context);
        this.target = target;
        this.value = value;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.hasFlag(NON_UPDATING_CONTEXT)) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "replace value of expression is not allowed in a non-updating context");
        }
        // Target and value expressions are non-updating contexts
        final AnalyzeContextInfo subInfo = new AnalyzeContextInfo(contextInfo);
        subInfo.setParent(this);
        subInfo.addFlag(IN_UPDATE);
        subInfo.addFlag(NON_UPDATING_CONTEXT);
        target.analyze(subInfo);
        value.analyze(subInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;

        final Sequence targetSeq = target.eval(ctxSeq, null);
        if (targetSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XUDY0027,
                    "Target of replace value of expression must not be an empty sequence.");
        }

        // XUTY0008: target must be a single node of the right type
        if (targetSeq.getItemCount() != 1 || !Type.subTypeOf(targetSeq.itemAt(0).getType(), Type.NODE)) {
            throw new XPathException(this, ErrorCodes.XUTY0008,
                    "Target of replace value of expression must be a single element, attribute, text, comment, or processing instruction node.");
        }

        final NodeValue targetNode = (NodeValue) targetSeq.itemAt(0);
        final int nodeType = targetNode.getNode().getNodeType();

        if (nodeType == Node.DOCUMENT_NODE || (nodeType != Node.ELEMENT_NODE && nodeType != Node.ATTRIBUTE_NODE
                && nodeType != Node.TEXT_NODE && nodeType != Node.COMMENT_NODE
                && nodeType != Node.PROCESSING_INSTRUCTION_NODE)) {
            throw new XPathException(this, ErrorCodes.XUTY0008,
                    "Target of replace value of expression must be a single element, attribute, text, comment, or processing instruction node, not " +
                    (nodeType == Node.DOCUMENT_NODE ? "a document node" : "node type " + nodeType) + ".");
        }

        final Sequence valueSeq = value.eval(ctxSeq, null);

        // Per W3C spec, the replacement value is the string value obtained by atomizing
        // the content expression and joining with single space separator.
        // We materialize this now (at snapshot time) rather than deferring to PUL application,
        // to ensure we capture the original value before any other PUL primitives modify the tree.
        final String stringValue = PendingUpdateList.atomizeAndJoin(valueSeq);

        final PendingUpdateList pul = context.getPendingUpdateList();
        pul.addPrimitive(UpdatePrimitive.replaceValue(targetNode.getNode(),
                new StringValue(this, stringValue), this));

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
        value.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("replace value of node ");
        target.dump(dumper);
        dumper.display(" with ");
        value.dump(dumper);
    }

    @Override
    public String toString() {
        return "replace value of node " + target.toString() + " with " + value.toString();
    }
}
