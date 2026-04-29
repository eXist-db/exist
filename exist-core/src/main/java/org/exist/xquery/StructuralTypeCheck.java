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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Runtime structural check for an argument whose declared type carries
 * structural information beyond a primary type and cardinality, e.g.
 * {@code map(K, V)} or {@code array(T)}. The plain {@link DynamicTypeCheck}
 * only validates the primary type code, so a value of the right shape
 * (a map) but wrong contents (e.g. xs:untypedAtomic where xs:integer was
 * required) would pass undetected. This wrapper invokes
 * {@link SequenceType#checkType(Item)} per item, which walks the entries
 * or members of typed maps/arrays.
 */
public class StructuralTypeCheck extends AbstractExpression {

    private final Expression expression;
    private final SequenceType requiredType;

    public StructuralTypeCheck(final XQueryContext context, final SequenceType requiredType,
            final Expression expr) {
        super(context);
        this.requiredType = requiredType;
        this.expression = expr;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence seq = expression.eval(contextSequence, contextItem);
        // XQ4 PR1501 introduced function-coercion semantics for typed maps and
        // arrays (xs:untypedAtomic gets converted to the required type). We do
        // not implement that yet, so for XQ4 mode we skip the strict structural
        // check and let calls succeed as before. XP31/XQ31 strict mode keeps
        // the structural validation that issues XPTY0004 for shape mismatches.
        if (context.getXQueryVersion() >= 40) {
            return seq;
        }
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            if (!requiredType.checkType(item)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Item does not match required type " + requiredType + ": " + item);
            }
        }
        return seq;
    }

    @Override
    public int returnsType() {
        return requiredType.getPrimaryType();
    }

    @Override
    public Cardinality getCardinality() {
        return requiredType.getCardinality();
    }

    @Override
    public int getDependencies() {
        return expression.getDependencies();
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        if (dumper.verbosity() > 1) {
            dumper.display("structural-type-check[");
            dumper.display(requiredType.toString());
            dumper.display(", ");
        }
        expression.dump(dumper);
        if (dumper.verbosity() > 1) {
            dumper.display("]");
        }
    }

    @Override
    public String toString() {
        return expression.toString();
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }
}
