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

        // Evaluate source expression (content to insert)
        final Sequence sourceSeq = source.eval(ctxSeq, null);
        if (sourceSeq.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Evaluate target expression
        final Sequence targetSeq = target.eval(ctxSeq, null);

        // XUDY0027: target must not be empty
        if (targetSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XUDY0027,
                    "Target of insert expression must not be an empty sequence.");
        }

        // Target must be a single node
        if (targetSeq.getItemCount() != 1 || !Type.subTypeOf(targetSeq.itemAt(0).getType(), Type.NODE)) {
            if (mode == INSERT_INTO || mode == INSERT_INTO_AS_FIRST || mode == INSERT_INTO_AS_LAST) {
                // XUTY0005: target of insert-into must be single element or document
                throw new XPathException(this, ErrorCodes.XUTY0005,
                        "Target of insert into expression must be a single element or document node.");
            } else {
                // XUTY0006: target of insert-before/after must be single element/text/comment/PI
                throw new XPathException(this, ErrorCodes.XUTY0006,
                        "Target of insert before/after expression must be a single element, text, comment, or processing instruction node.");
            }
        }

        final NodeValue targetNode = (NodeValue) targetSeq.itemAt(0);
        final Node domTarget = targetNode.getNode();
        final int targetType = domTarget.getNodeType();

        // Validate target and source based on insert mode
        switch (mode) {
            case INSERT_INTO:
            case INSERT_INTO_AS_FIRST:
            case INSERT_INTO_AS_LAST:
                // XUTY0005: target must be element or document
                if (targetType != Node.ELEMENT_NODE && targetType != Node.DOCUMENT_NODE) {
                    throw new XPathException(this, ErrorCodes.XUTY0005,
                            "Target of insert into expression must be an element or document node.");
                }

                // XUTY0004: source must not have attribute after non-attribute
                {
                    boolean seenNonAttribute = false;
                    for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
                        final Item item = i.nextItem();
                        if (Type.subTypeOf(item.getType(), Type.NODE)
                                && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                            if (seenNonAttribute) {
                                throw new XPathException(this, ErrorCodes.XUTY0004,
                                        "In the source of an insert expression, attribute nodes must not follow non-attribute nodes.");
                            }
                        } else {
                            seenNonAttribute = true;
                        }
                    }
                }

                // XUTY0022: cannot insert attributes into document node
                if (targetType == Node.DOCUMENT_NODE) {
                    for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
                        final Item item = i.nextItem();
                        if (Type.subTypeOf(item.getType(), Type.NODE)
                                && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                            throw new XPathException(this, ErrorCodes.XUTY0022,
                                    "Cannot insert attribute nodes into a document node.");
                        }
                    }
                }
                break;

            case INSERT_BEFORE:
            case INSERT_AFTER:
                // XUTY0006: target must be element, text, comment, or PI (not document or attribute)
                if (targetType == Node.DOCUMENT_NODE || targetType == Node.ATTRIBUTE_NODE) {
                    throw new XPathException(this, ErrorCodes.XUTY0006,
                            "Target of insert before/after must be an element, text, comment, or processing instruction node.");
                }

                // XUDY0029: target must have a parent
                if (domTarget.getParentNode() == null) {
                    throw new XPathException(this, ErrorCodes.XUDY0029,
                            "Target of insert before/after must have a parent node.");
                }

                // Checks when parent is document node
                if (domTarget.getParentNode().getNodeType() == Node.DOCUMENT_NODE) {
                    // XUDY0030: cannot insert attribute before/after node whose parent is document
                    for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
                        final Item item = i.nextItem();
                        if (Type.subTypeOf(item.getType(), Type.NODE)
                                && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                            throw new XPathException(this, ErrorCodes.XUDY0030,
                                    "Cannot insert attribute node before/after a node whose parent is a document node.");
                        }
                    }
                    // XUDY0027: target is root element or root text of a document
                    if (targetType == Node.ELEMENT_NODE || targetType == Node.TEXT_NODE) {
                        throw new XPathException(this, ErrorCodes.XUDY0027,
                                "Target of insert before/after is a root element or root text node of a document.");
                    }
                }
                break;
        }

        // Add to PUL
        final PendingUpdateList pul = context.getPendingUpdateList();
        final UpdatePrimitive.Type primType = switch (mode) {
            case INSERT_INTO -> UpdatePrimitive.Type.INSERT_INTO;
            case INSERT_INTO_AS_FIRST -> UpdatePrimitive.Type.INSERT_INTO_AS_FIRST;
            case INSERT_INTO_AS_LAST -> UpdatePrimitive.Type.INSERT_INTO_AS_LAST;
            case INSERT_BEFORE -> UpdatePrimitive.Type.INSERT_BEFORE;
            case INSERT_AFTER -> UpdatePrimitive.Type.INSERT_AFTER;
            default -> UpdatePrimitive.Type.INSERT_INTO;
        };

        // Separate attribute and non-attribute content
        if (mode == INSERT_INTO || mode == INSERT_INTO_AS_FIRST || mode == INSERT_INTO_AS_LAST) {
            // For into modes: attributes go as INSERT_ATTRIBUTES on target element
            if (domTarget.getNodeType() == Node.ELEMENT_NODE) {
                final ValueSequence attrContent = new ValueSequence();
                final ValueSequence otherContent = new ValueSequence();
                for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();
                    if (Type.subTypeOf(item.getType(), Type.NODE)
                            && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                        attrContent.add(item);
                    } else {
                        otherContent.add(item);
                    }
                }
                if (!attrContent.isEmpty()) {
                    pul.addPrimitive(new UpdatePrimitive(UpdatePrimitive.Type.INSERT_ATTRIBUTES,
                            domTarget, attrContent, null, null, this));
                }
                if (!otherContent.isEmpty()) {
                    pul.addPrimitive(new UpdatePrimitive(primType, domTarget, otherContent, null, null, this));
                }
            } else {
                pul.addPrimitive(new UpdatePrimitive(primType, domTarget, sourceSeq, null, null, this));
            }
        } else if (mode == INSERT_BEFORE || mode == INSERT_AFTER) {
            // For before/after modes: per W3C spec, attribute nodes in source are
            // added to the PARENT element of the target node
            final ValueSequence attrContent = new ValueSequence();
            final ValueSequence otherContent = new ValueSequence();
            for (final SequenceIterator i = sourceSeq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)
                        && ((NodeValue) item).getNode().getNodeType() == Node.ATTRIBUTE_NODE) {
                    attrContent.add(item);
                } else {
                    otherContent.add(item);
                }
            }
            if (!attrContent.isEmpty()) {
                // Attributes go to the parent element
                final Node parentNode = domTarget.getParentNode();
                pul.addPrimitive(new UpdatePrimitive(UpdatePrimitive.Type.INSERT_ATTRIBUTES,
                        parentNode, attrContent, null, null, this));
            }
            if (!otherContent.isEmpty()) {
                pul.addPrimitive(new UpdatePrimitive(primType, domTarget, otherContent, null, null, this));
            }
        } else {
            pul.addPrimitive(new UpdatePrimitive(primType, domTarget, sourceSeq, null, null, this));
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
