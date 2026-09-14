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
 * W3C XQuery Update Facility 3.0 - replace node expression.
 *
 * <pre>
 * ReplaceExpr ::= "replace" ("value" "of")? "node" TargetExpr "with" ExprSingle
 * </pre>
 *
 * This class handles "replace node" (not "replace value of node").
 */
public class XQUFReplaceNodeExpr extends AbstractExpression {

    private final Expression target;
    private final Expression replacement;

    public XQUFReplaceNodeExpr(final XQueryContext context, final Expression target, final Expression replacement) {
        super(context);
        this.target = target;
        this.replacement = replacement;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.hasFlag(NON_UPDATING_CONTEXT)) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "replace expression is not allowed in a non-updating context");
        }
        // Target and replacement expressions are non-updating contexts
        final AnalyzeContextInfo subInfo = new AnalyzeContextInfo(contextInfo);
        subInfo.setParent(this);
        subInfo.addFlag(IN_UPDATE);
        subInfo.addFlag(NON_UPDATING_CONTEXT);
        target.analyze(subInfo);
        replacement.analyze(subInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;

        final NodeValue targetNode = resolveTargetNode(ctxSeq);
        final int nodeType = targetNode.getNode().getNodeType();

        final Sequence replacementSeq = replacement.eval(ctxSeq, null);
        checkReplacementTypes(replacementSeq, nodeType);

        final PendingUpdateList pul = context.getPendingUpdateList();
        pul.addPrimitive(UpdatePrimitive.replaceNode(targetNode.getNode(), replacementSeq, this));

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    /**
     * Evaluate and validate the replace target: a single element, attribute,
     * text, comment, or PI node with a parent (XUDY0027/XUTY0008/XUTY0006/XUDY0009).
     */
    private NodeValue resolveTargetNode(final Sequence ctxSeq) throws XPathException {
        final Sequence targetSeq = target.eval(ctxSeq, null);
        if (targetSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XUDY0027,
                    "Target of replace expression must not be an empty sequence.");
        }

        // XUTY0008: target must be a single node
        if (targetSeq.getItemCount() != 1 || !Type.subTypeOf(targetSeq.itemAt(0).getType(), Type.NODE)) {
            throw new XPathException(this, ErrorCodes.XUTY0008,
                    "Target of replace expression must be a single node.");
        }

        final NodeValue targetNode = (NodeValue) targetSeq.itemAt(0);
        checkTargetNodeKind(targetNode.getNode());
        return targetNode;
    }

    /**
     * Validate the target node kind (XUTY0008/XUTY0006) and that it has a
     * parent (XUDY0009).
     */
    private void checkTargetNodeKind(final Node domNode) throws XPathException {
        final int nodeType = domNode.getNodeType();

        // XUTY0008: target must not be a document node
        if (nodeType == Node.DOCUMENT_NODE) {
            throw new XPathException(this, ErrorCodes.XUTY0008,
                    "Target of replace expression must not be a document node.");
        }

        // XUTY0006: target must be element, attribute, text, comment, or PI
        if (nodeType != Node.ELEMENT_NODE && nodeType != Node.ATTRIBUTE_NODE
                && nodeType != Node.TEXT_NODE && nodeType != Node.COMMENT_NODE
                && nodeType != Node.PROCESSING_INSTRUCTION_NODE) {
            throw new XPathException(this, ErrorCodes.XUTY0006,
                    "Target of replace expression must be an element, attribute, text, comment, or processing instruction node.");
        }

        // XUDY0009: target must have a parent
        final boolean hasParent = nodeType == Node.ATTRIBUTE_NODE
                ? ((org.w3c.dom.Attr) domNode).getOwnerElement() != null
                : domNode.getParentNode() != null;
        if (!hasParent) {
            throw new XPathException(this, ErrorCodes.XUDY0009,
                    "Target node of replace expression has no parent.");
        }
    }

    /**
     * Type-check the replacement sequence against the target node type:
     * attributes replace attributes (XUTY0011); everything else must not
     * contain attributes (XUTY0010).
     */
    private void checkReplacementTypes(final Sequence replacementSeq, final int nodeType) throws XPathException {
        final boolean targetIsAttribute = nodeType == Node.ATTRIBUTE_NODE;
        for (final SequenceIterator i = replacementSeq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (!Type.subTypeOf(item.getType(), Type.NODE)) {
                continue;
            }
            final boolean itemIsAttribute = ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE;
            if (targetIsAttribute && !itemIsAttribute) {
                throw new XPathException(this, ErrorCodes.XUTY0011,
                        "Replacement of an attribute node must be attribute node(s).");
            }
            if (!targetIsAttribute && itemIsAttribute) {
                throw new XPathException(this, ErrorCodes.XUTY0010,
                        "Replacement of an element, text, comment, or PI node must not contain attribute nodes.");
            }
        }
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
        replacement.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("replace node ");
        target.dump(dumper);
        dumper.display(" with ");
        replacement.dump(dumper);
    }

    @Override
    public String toString() {
        return "replace node " + target.toString() + " with " + replacement.toString();
    }
}
