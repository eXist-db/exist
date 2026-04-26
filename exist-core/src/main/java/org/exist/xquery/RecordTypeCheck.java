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
import org.exist.xquery.functions.map.MapType;
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

        // Single item: coerce directly
        if (seq.getItemCount() == 1) {
            return coerce(seq.itemAt(0));
        }

        // Multiple items: coerce each
        final ValueSequence result = new ValueSequence(seq.getItemCount());
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Sequence coerced = coerce(i.nextItem());
            result.addAll(coerced);
        }
        return result;
    }

    /**
     * Coerce a single item to this record type.
     *
     * <p>Per XQuery 4.0, record coercion:
     * <ul>
     *   <li>Validates that the item is a map with all required fields</li>
     *   <li>Drops undeclared fields (non-extensible records)</li>
     *   <li>Coerces field values to declared types</li>
     *   <li>Builds the result map with fields in declaration order</li>
     * </ul></p>
     */
    private Sequence coerce(final Item item) throws XPathException {
        if (!Type.subTypeOf(item.getType(), Type.MAP_ITEM)) {
            throw new XPathException(expression, ErrorCodes.XPTY0004,
                    "Expected " + recordType + " but got " + Type.getTypeName(item.getType()));
        }

        final AbstractMapType sourceMap = (AbstractMapType) item;
        final java.util.List<RecordType.FieldDeclaration> fields = recordType.getFieldDeclarations();

        // Build a new map with only declared fields, in declaration order
        final MapType coercedMap = new MapType(expression, context);

        for (final RecordType.FieldDeclaration field : fields) {
            final StringValue key = new StringValue(expression, field.getName());
            final Sequence value = sourceMap.get(key);

            if (value == null || value.isEmpty()) {
                if (!field.isOptional()) {
                    throw new XPathException(expression, ErrorCodes.XPTY0004,
                            "Missing required field '" + field.getName() + "' in " + recordType);
                }
                // Optional field not present — omit from result
                continue;
            }

            // Coerce value to declared type if specified
            if (field.getType() != null) {
                final Sequence coerced = coerceFieldValue(field, value);
                coercedMap.add(key, coerced);
            } else {
                coercedMap.add(key, value);
            }
        }

        return coercedMap;
    }

    /**
     * Coerce a field value to the declared type.
     */
    private Sequence coerceFieldValue(final RecordType.FieldDeclaration field,
                                       final Sequence value) throws XPathException {
        final SequenceType declaredType = field.getType();
        final int targetType = declaredType.getPrimaryType();

        // If already the right type, return as-is
        if (Type.subTypeOf(value.getItemType(), targetType)) {
            return value;
        }

        // Attempt type promotion/casting for atomic types
        if (Type.subTypeOf(targetType, Type.ANY_ATOMIC_TYPE) && value.getItemCount() > 0) {
            final ValueSequence result = new ValueSequence(value.getItemCount());
            for (final SequenceIterator it = value.iterate(); it.hasNext(); ) {
                final Item item = it.nextItem();
                if (item instanceof AtomicValue) {
                    try {
                        result.add(((AtomicValue) item).convertTo(targetType));
                    } catch (final XPathException e) {
                        throw new XPathException(expression, ErrorCodes.XPTY0004,
                                "Cannot coerce field '" + field.getName() + "' value to " +
                                        Type.getTypeName(targetType) + ": " + e.getMessage());
                    }
                } else {
                    result.add(item);
                }
            }
            return result;
        }

        return value;
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
