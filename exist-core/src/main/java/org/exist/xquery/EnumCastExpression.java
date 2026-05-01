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
import org.exist.xquery.value.*;

/**
 * Implements cast as enum("a","b","c") and castable as enum("a","b","c") from XQuery 4.0.
 */
public class EnumCastExpression extends AbstractExpression {

    private final String[] enumValues;
    private final Cardinality cardinality;
    private final Expression expression;
    private final boolean isCastable;

    public EnumCastExpression(final XQueryContext context, final Expression expr,
                               final String[] enumValues, final Cardinality cardinality,
                               final boolean isCastable) {
        super(context);
        this.expression = expr;
        this.enumValues = enumValues;
        this.cardinality = cardinality;
        this.isCastable = isCastable;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence seq = Atomize.atomize(expression.eval(contextSequence, contextItem));

        if (seq.isEmpty()) {
            if (isCastable) {
                return BooleanValue.valueOf(
                        cardinality.isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE));
            }
            if (cardinality.atLeastOne()) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Type error: empty sequence is not allowed here");
            }
            return Sequence.EMPTY_SEQUENCE;
        }

        final String value = seq.itemAt(0).getStringValue();

        for (final String enumVal : enumValues) {
            if (enumVal.equals(value)) {
                if (isCastable) {
                    return BooleanValue.TRUE;
                }
                return new StringValue(this, value);
            }
        }

        if (isCastable) {
            return BooleanValue.FALSE;
        }
        throw new XPathException(this, ErrorCodes.FORG0001,
                "Cannot cast '" + value + "' to enum type");
    }

    @Override
    public int returnsType() {
        return isCastable ? Type.BOOLEAN : Type.STRING;
    }

    @Override
    public Cardinality getCardinality() {
        return isCastable ? Cardinality.EXACTLY_ONE : Cardinality.ZERO_OR_ONE;
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(isCastable ? " castable as enum(" : " cast as enum(");
        for (int i = 0; i < enumValues.length; i++) {
            if (i > 0) {
                dumper.display(", ");
            }
            dumper.display("\"" + enumValues[i] + "\"");
        }
        dumper.display(")");
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append(expression.toString()).append(isCastable ? " castable as enum(" : " cast as enum(");
        for (int i = 0; i < enumValues.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append("\"").append(enumValues[i]).append("\"");
        }
        sb.append(")");
        return sb.toString();
    }

    @Override
    public int getDependencies() {
        return expression.getDependencies() | Dependency.CONTEXT_ITEM;
    }

    @Override
    public void setContextDocSet(final DocumentSet contextSet) {
        super.setContextDocSet(contextSet);
        expression.setContextDocSet(contextSet);
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        expression.resetState(postOptimization);
    }
}
