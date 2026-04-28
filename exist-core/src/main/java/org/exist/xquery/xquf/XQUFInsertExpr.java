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
 * W3C XQuery Update Facility 3.0 - insert expression.
 *
 * <pre>
 * InsertExpr ::= "insert" ("node" | "nodes") SourceExpr InsertExprTargetChoice TargetExpr
 * InsertExprTargetChoice ::= (("as" ("first" | "last"))? "into") | "after" | "before"
 * </pre>
 */
public class XQUFInsertExpr extends AbstractExpression {

    public static final int INSERT_INTO = 0;
    public static final int INSERT_INTO_AS_FIRST = 1;
    public static final int INSERT_INTO_AS_LAST = 2;
    public static final int INSERT_BEFORE = 3;
    public static final int INSERT_AFTER = 4;

    private final Expression source;
    private final Expression target;
    private final int mode;

    public XQUFInsertExpr(final XQueryContext context, final Expression source,
                          final Expression target, final int mode) {
        super(context);
        this.source = source;
        this.target = target;
        this.mode = mode;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.hasFlag(NON_UPDATING_CONTEXT)) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "insert expression is not allowed in a non-updating context");
        }
        // Source and target expressions of insert are non-updating contexts
        final AnalyzeContextInfo subInfo = new AnalyzeContextInfo(contextInfo);
        subInfo.setParent(this);
        subInfo.addFlag(IN_UPDATE);
        subInfo.addFlag(NON_UPDATING_CONTEXT);
        source.analyze(subInfo);
        target.analyze(subInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;

        final Sequence sourceSeq = source.eval(ctxSeq, null);
        if (sourceSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Node domTarget = resolveTarget(target.eval(ctxSeq, null));
        validateForMode(domTarget, sourceSeq);
        emitPrimitives(domTarget, sourceSeq);

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);
        }

        return Sequence.EMPTY_SEQUENCE;
    }

    private boolean isIntoMode() {
        return mode == INSERT_INTO || mode == INSERT_INTO_AS_FIRST || mode == INSERT_INTO_AS_LAST;
    }

    private static boolean isAttributeNode(final Item item) {
        return Type.subTypeOf(item.getType(), Type.NODE)
                && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE;
    }

    private Node resolveTarget(final Sequence targetSeq) throws XPathException {
        if (targetSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XUDY0027,
                    "Target of insert expression must not be an empty sequence.");
        }
        if (targetSeq.getItemCount() != 1 || !Type.subTypeOf(targetSeq.itemAt(0).getType(), Type.NODE)) {
            if (isIntoMode()) {
                throw new XPathException(this, ErrorCodes.XUTY0005,
                        "Target of insert into expression must be a single element or document node.");
            }
            throw new XPathException(this, ErrorCodes.XUTY0006,
                    "Target of insert before/after expression must be a single element, text, comment, or processing instruction node.");
        }
        return ((NodeValue) targetSeq.itemAt(0)).getNode();
    }

    private void validateForMode(final Node domTarget, final Sequence sourceSeq) throws XPathException {
        if (isIntoMode()) {
            validateIntoMode(domTarget, sourceSeq);
        } else {
            validateBeforeAfterMode(domTarget, sourceSeq);
        }
    }

    private void validateIntoMode(final Node domTarget, final Sequence sourceSeq) throws XPathException {
        final int targetType = domTarget.getNodeType();
        if (targetType != Node.ELEMENT_NODE && targetType != Node.DOCUMENT_NODE) {
            throw new XPathException(this, ErrorCodes.XUTY0005,
                    "Target of insert into expression must be an element or document node.");
        }
        validateAttributeOrdering(sourceSeq);
        if (targetType == Node.DOCUMENT_NODE) {
            rejectAttributesInSource(sourceSeq, ErrorCodes.XUTY0022,
                    "Cannot insert attribute nodes into a document node.");
        }
    }

    private void validateBeforeAfterMode(final Node domTarget, final Sequence sourceSeq) throws XPathException {
        final int targetType = domTarget.getNodeType();
        if (targetType == Node.DOCUMENT_NODE || targetType == Node.ATTRIBUTE_NODE) {
            throw new XPathException(this, ErrorCodes.XUTY0006,
                    "Target of insert before/after must be an element, text, comment, or processing instruction node.");
        }
        final Node parent = domTarget.getParentNode();
        if (parent == null) {
            throw new XPathException(this, ErrorCodes.XUDY0029,
                    "Target of insert before/after must have a parent node.");
        }
        if (parent.getNodeType() == Node.DOCUMENT_NODE) {
            rejectAttributesInSource(sourceSeq, ErrorCodes.XUDY0030,
                    "Cannot insert attribute node before/after a node whose parent is a document node.");
            if (targetType == Node.ELEMENT_NODE || targetType == Node.TEXT_NODE) {
                throw new XPathException(this, ErrorCodes.XUDY0027,
                        "Target of insert before/after is a root element or root text node of a document.");
            }
        }
    }

    private void validateAttributeOrdering(final Sequence sourceSeq) throws XPathException {
        boolean seenNonAttribute = false;
        for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (isAttributeNode(item)) {
                if (seenNonAttribute) {
                    throw new XPathException(this, ErrorCodes.XUTY0004,
                            "In the source of an insert expression, attribute nodes must not follow non-attribute nodes.");
                }
            } else {
                seenNonAttribute = true;
            }
        }
    }

    private void rejectAttributesInSource(final Sequence sourceSeq, final ErrorCodes.ErrorCode code, final String msg)
            throws XPathException {
        for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
            if (isAttributeNode(i.nextItem())) {
                throw new XPathException(this, code, msg);
            }
        }
    }

    private UpdatePrimitive.Type modeToPrimType() {
        return switch (mode) {
            case INSERT_INTO -> UpdatePrimitive.Type.INSERT_INTO;
            case INSERT_INTO_AS_FIRST -> UpdatePrimitive.Type.INSERT_INTO_AS_FIRST;
            case INSERT_INTO_AS_LAST -> UpdatePrimitive.Type.INSERT_INTO_AS_LAST;
            case INSERT_BEFORE -> UpdatePrimitive.Type.INSERT_BEFORE;
            case INSERT_AFTER -> UpdatePrimitive.Type.INSERT_AFTER;
            default -> UpdatePrimitive.Type.INSERT_INTO;
        };
    }

    private void emitPrimitives(final Node domTarget, final Sequence sourceSeq) throws XPathException {
        final PendingUpdateList pul = context.getPendingUpdateList();
        final UpdatePrimitive.Type primType = modeToPrimType();

        if (isIntoMode() && domTarget.getNodeType() == Node.ELEMENT_NODE) {
            emitSplitPrimitives(pul, primType, domTarget, domTarget, sourceSeq);
        } else if (mode == INSERT_BEFORE || mode == INSERT_AFTER) {
            emitSplitPrimitives(pul, primType, domTarget, domTarget.getParentNode(), sourceSeq);
        } else {
            pul.addPrimitive(new UpdatePrimitive(primType, domTarget, sourceSeq, null, null, this));
        }
    }

    private void emitSplitPrimitives(final PendingUpdateList pul, final UpdatePrimitive.Type primType,
                                     final Node domTarget, final Node attrTarget,
                                     final Sequence sourceSeq) throws XPathException {
        final ValueSequence attrContent = new ValueSequence();
        final ValueSequence otherContent = new ValueSequence();
        for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (isAttributeNode(item)) {
                attrContent.add(item);
            } else {
                otherContent.add(item);
            }
        }
        if (!attrContent.isEmpty()) {
            pul.addPrimitive(new UpdatePrimitive(UpdatePrimitive.Type.INSERT_ATTRIBUTES,
                    attrTarget, attrContent, null, null, this));
        }
        if (!otherContent.isEmpty()) {
            pul.addPrimitive(new UpdatePrimitive(primType, domTarget, otherContent, null, null, this));
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
        source.resetState(postOptimization);
        target.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("insert node ");
        source.dump(dumper);
        switch (mode) {
            case INSERT_INTO -> dumper.display(" into ");
            case INSERT_INTO_AS_FIRST -> dumper.display(" as first into ");
            case INSERT_INTO_AS_LAST -> dumper.display(" as last into ");
            case INSERT_BEFORE -> dumper.display(" before ");
            case INSERT_AFTER -> dumper.display(" after ");
        }
        target.dump(dumper);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("insert node ");
        sb.append(source.toString());
        switch (mode) {
            case INSERT_INTO -> sb.append(" into ");
            case INSERT_INTO_AS_FIRST -> sb.append(" as first into ");
            case INSERT_INTO_AS_LAST -> sb.append(" as last into ");
            case INSERT_BEFORE -> sb.append(" before ");
            case INSERT_AFTER -> sb.append(" after ");
        }
        sb.append(target.toString());
        return sb.toString();
    }
}
