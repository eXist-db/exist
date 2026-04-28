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

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;
import org.w3c.dom.Node;

/**
 * W3C XQuery Update Facility 3.0 - rename expression.
 *
 * <pre>
 * RenameExpr ::= "rename" "node" TargetExpr "as" NewNameExpr
 * </pre>
 */
public class XQUFRenameExpr extends AbstractExpression {

    private final Expression target;
    private final Expression newName;

    public XQUFRenameExpr(final XQueryContext context, final Expression target, final Expression newName) {
        super(context);
        this.target = target;
        this.newName = newName;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        if (contextInfo.hasFlag(NON_UPDATING_CONTEXT)) {
            throw new XPathException(this, ErrorCodes.XUST0001,
                    "rename expression is not allowed in a non-updating context");
        }
        // Target and new name expressions are non-updating contexts
        final AnalyzeContextInfo subInfo = new AnalyzeContextInfo(contextInfo);
        subInfo.setParent(this);
        subInfo.addFlag(IN_UPDATE);
        subInfo.addFlag(NON_UPDATING_CONTEXT);
        target.analyze(subInfo);
        newName.analyze(subInfo);
    }

    // PMD.NPathComplexity: XQUF rename evaluates spec-mandated validation
    // (XUDY0027/XUTY0008/XUDY0023/XUDY0024) inline before emitting the PUL
    // primitive. The branches encode independent W3C error conditions and
    // splitting them obscures spec-to-code mapping. Covered by XQUFBasicTest.
    @SuppressWarnings("PMD.NPathComplexity")
    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
        }

        final Sequence ctxSeq = contextItem != null ? contextItem.toSequence() : contextSequence;

        final Sequence targetSeq = target.eval(ctxSeq, null);
        if (targetSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XUDY0027,
                    "Target of rename expression must not be an empty sequence.");
        }

        // XUTY0012: target must be a single element, attribute, or PI node
        if (targetSeq.getItemCount() != 1 || !Type.subTypeOf(targetSeq.itemAt(0).getType(), Type.NODE)) {
            throw new XPathException(this, ErrorCodes.XUTY0012,
                    "Target of rename expression must be a single element, attribute, or processing instruction node.");
        }

        final NodeValue targetNode = (NodeValue) targetSeq.itemAt(0);
        final int nodeType = targetNode.getNode().getNodeType();

        if (nodeType != Node.ELEMENT_NODE && nodeType != Node.ATTRIBUTE_NODE
                && nodeType != Node.PROCESSING_INSTRUCTION_NODE) {
            throw new XPathException(this, ErrorCodes.XUTY0012,
                    "Target of rename expression must be an element, attribute, or processing instruction node.");
        }

        // Evaluate new name — must be a single item castable to xs:QName
        final Sequence nameSeq = newName.eval(ctxSeq, null);
        if (nameSeq.isEmpty()) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "New name expression in rename must not be empty; expected xs:QName or xs:string.");
        }
        if (nameSeq.getItemCount() > 1) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "New name expression in rename must be a single item; got " + nameSeq.getItemCount() + " items.");
        }

        // Atomize the new name expression per W3C spec
        final Item nameItem;
        final Item rawItem = nameSeq.itemAt(0);
        if (Type.subTypeOf(rawItem.getType(), Type.NODE)) {
            nameItem = rawItem.atomize();
        } else {
            nameItem = rawItem;
        }

        final QName qname;
        if (nameItem.getType() == Type.QNAME) {
            qname = ((QNameValue) nameItem).getQName();
        } else if (Type.subTypeOf(nameItem.getType(), Type.STRING) || nameItem.getType() == Type.UNTYPED_ATOMIC) {
            final String nameStr = nameItem.getStringValue().trim();
            try {
                qname = QName.parse(context, nameStr);
            } catch (final QName.IllegalQNameException e) {
                throw new XPathException(this, ErrorCodes.XQDY0074, "Invalid QName for rename: " + nameStr);
            }
            // Validate the name is a valid QName (NCName with optional prefix)
            if (org.exist.dom.QName.isQName(nameStr) != QName.Validity.VALID.val) {
                throw new XPathException(this, ErrorCodes.XQDY0074, "Invalid QName for rename: " + nameStr);
            }
        } else {
            // Non-QName/string/untypedAtomic types are a type error (XPTY0004)
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "New name expression in rename must be of type xs:QName or xs:string; got " + Type.getTypeName(nameItem.getType()));
        }

        // XQDY0064: PI target name must not be "xml" (case-insensitive)
        if (nodeType == Node.PROCESSING_INSTRUCTION_NODE) {
            if ("xml".equalsIgnoreCase(qname.getLocalPart())) {
                throw new XPathException(this, ErrorCodes.XQDY0064,
                        "Processing instruction target name cannot be 'xml'.");
            }
        }

        final PendingUpdateList pul = context.getPendingUpdateList();
        pul.addPrimitive(UpdatePrimitive.rename(targetNode.getNode(), qname, this));

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
        newName.resetState(postOptimization);
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        dumper.display("rename node ");
        target.dump(dumper);
        dumper.display(" as ");
        newName.dump(dumper);
    }

    @Override
    public String toString() {
        return "rename node " + target.toString() + " as " + newName.toString();
    }
}
