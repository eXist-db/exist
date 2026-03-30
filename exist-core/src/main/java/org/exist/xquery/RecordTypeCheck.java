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
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Runtime check that a function argument matches a declared record type.
 *
 * <p>When a function parameter is declared as {@code $param as record(name as xs:string, ...)},
 * the argument expression is wrapped in a {@code RecordTypeCheck}. At runtime,
 * the check verifies the argument is a map that matches all required fields and
 * field types declared in the {@link RecordType}.</p>
 *
 * <p>Modeled on {@link DynamicTypeCheck} and {@link FunctionTypeCheck}.</p>
 */
public class RecordTypeCheck extends AbstractExpression {

    private final Expression expression;
    private final RecordType recordType;

    public RecordTypeCheck(final XQueryContext context, final RecordType recordType, final Expression expr) {
        super(context);
        this.recordType = recordType;
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

        if (seq.isEmpty()) {
            return seq;
        }

        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            check(item);
        }

        return seq;
    }

    private void check(final Item item) throws XPathException {
        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            throw new XPathException(expression, ErrorCodes.XPTY0004,
                    "Expected " + recordType + " but got " + Type.getTypeName(item.getType()));
        }

        final AbstractMapType map = (AbstractMapType) item;
        if (!recordType.matches(map)) {
            throw new XPathException(expression, ErrorCodes.XPTY0004,
                    "Map does not match " + recordType + ": " + describeFailure(map));
        }
    }

    private String describeFailure(final AbstractMapType map) {
        final StringBuilder sb = new StringBuilder();
        for (final RecordType.FieldDeclaration field : recordType.getFieldDeclarations()) {
            final Sequence value = map.get(new StringValue(field.getName()));
            if ((value == null || value.isEmpty()) && !field.isOptional()) {
                if (sb.length() > 0) sb.append("; ");
                sb.append("missing required field '").append(field.getName()).append("'");
            } else if (value != null && !value.isEmpty() && field.getType() != null) {
                try {
                    if (!field.getType().checkType(value)) {
                        if (sb.length() > 0) sb.append("; ");
                        sb.append("field '").append(field.getName()).append("' has type ")
                                .append(Type.getTypeName(value.getItemType()))
                                .append(", expected ").append(field.getType());
                    }
                } catch (final XPathException e) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append("field '").append(field.getName()).append("' type check error: ").append(e.getMessage());
                }
            }
        }
        if (sb.length() == 0) {
            // Extensibility check failure
            sb.append("map contains unexpected keys not declared in ").append(recordType);
        }
        return sb.toString();
    }

    @Override
    public int returnsType() {
        return Type.MAP_ITEM;
    }

    @Override
    public int getDependencies() {
        return expression.getDependencies();
    }

    @Override
    public void dump(final ExpressionDumper dumper) {
        if (dumper.verbosity() > 1) {
            dumper.display("record-type-check[");
            dumper.display(recordType.toString());
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

    @Override
    public int getLine() {
        return expression.getLine();
    }

    @Override
    public int getColumn() {
        return expression.getColumn();
    }

    @Override
    public void accept(final ExpressionVisitor visitor) {
        expression.accept(visitor);
    }

    @Override
    public int getSubExpressionCount() {
        return 1;
    }

    @Override
    public Expression getSubExpression(final int index) {
        if (index == 0) {
            return expression;
        }
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + getSubExpressionCount());
    }
}
