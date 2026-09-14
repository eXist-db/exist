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

import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.util.Objects;

/**
 * XQuery 4.0 field accessor expression: {@code $expr.fieldName}.
 *
 * <p>Syntactic sugar for {@code map:get($expr, "fieldName")}. The parser-next
 * branch will wire the {@code .NCName} postfix syntax to this class. Until then,
 * it can be used programmatically or via {@code fn:get} on record-typed values.</p>
 *
 * <p>If the base expression has a declared record type, the field name is
 * validated against the record's field declarations at analysis time.</p>
 */
public class FieldAccessor extends AbstractExpression {

    private final Expression baseExpr;
    private final String fieldName;

    public FieldAccessor(final XQueryContext context, final Expression baseExpr, final String fieldName) {
        super(context);
        this.baseExpr = baseExpr;
        this.fieldName = fieldName;
    }

    public Expression getBaseExpression() {
        return baseExpr;
    }

    public String getFieldName() {
        return fieldName;
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        contextInfo.setParent(this);
        baseExpr.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem) throws XPathException {
        final Sequence baseResult = baseExpr.eval(contextSequence, contextItem);

        if (baseResult.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final Item item = baseResult.itemAt(0);
        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Field accessor ." + fieldName + " requires a map, got " + Type.getTypeName(item.getType()));
        }

        final AbstractMapType map = (AbstractMapType) item;
        final Sequence value = map.get(new StringValue(this, fieldName));
        return Objects.requireNonNullElse(value, Sequence.EMPTY_SEQUENCE);
    }

    @Override
    public int returnsType() {
        return Type.ITEM;
    }

    @Override
    public int getDependencies() {
        return baseExpr.getDependencies();
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        baseExpr.dump(dumper);
        dumper.display(".");
        dumper.display(fieldName);
    }

    @Override
    public String toString() {
        return baseExpr.toString() + "." + fieldName;
    }
}
