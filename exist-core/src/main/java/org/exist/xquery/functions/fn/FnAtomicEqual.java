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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AbstractDateTimeValue;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements fn:atomic-equal (XQuery 4.0).
 *
 * Compares two atomic values for equality. Unlike eq, this function:
 * - Never raises a dynamic error (returns false for incomparable types)
 * - NaN equals NaN
 * - Does not depend on static or dynamic context
 */
public class FnAtomicEqual extends BasicFunction {

    public static final FunctionSignature FN_ATOMIC_EQUAL = new FunctionSignature(
            new QName("atomic-equal", Function.BUILTIN_FUNCTION_NS),
            "Compares two atomic values for equality. NaN equals NaN, and incomparable types return false.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value1", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The first value"),
                    new FunctionParameterSequenceType("value2", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The second value")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the values are equal"));

    public FnAtomicEqual(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final AtomicValue v1 = args[0].itemAt(0).atomize();
        final AtomicValue v2 = args[1].itemAt(0).atomize();

        // Handle NaN: NaN equals NaN (across float/double)
        if (isNaN(v1) && isNaN(v2)) {
            return BooleanValue.TRUE;
        }
        if (isNaN(v1) || isNaN(v2)) {
            return BooleanValue.FALSE;
        }

        // Handle Infinity: float INF equals double INF (and -INF)
        if (isInfinite(v1) && isInfinite(v2)) {
            return BooleanValue.valueOf(toDouble(v1) == toDouble(v2));
        }

        try {
            final int t1 = v1.getType();
            final int t2 = v2.getType();

            // String-like types: string, untypedAtomic, anyURI all compare equal
            if (isStringLike(t1) && isStringLike(t2)) {
                return BooleanValue.valueOf(v1.getStringValue().equals(v2.getStringValue()));
            }

            // Numeric: compare by mathematical value regardless of type
            // Per XQ4 spec: "Two numeric values are equal if their mathematical values are equal"
            if (v1 instanceof NumericValue && v2 instanceof NumericValue) {
                return BooleanValue.valueOf(numericEqual((NumericValue) v1, (NumericValue) v2));
            }

            // Binary types: hexBinary and base64Binary compare equal by content
            if (isBinaryType(t1) && isBinaryType(t2)) {
                if (v1 instanceof BinaryValue && v2 instanceof BinaryValue) {
                    return BooleanValue.valueOf(v1.compareTo(null, v2) == 0);
                }
                return BooleanValue.FALSE;
            }

            // Boolean
            if (t1 == Type.BOOLEAN && t2 == Type.BOOLEAN) {
                return BooleanValue.valueOf(v1.effectiveBooleanValue() == v2.effectiveBooleanValue());
            }

            // Date/time: values with timezone never equal values without timezone
            if (v1 instanceof AbstractDateTimeValue && v2 instanceof AbstractDateTimeValue) {
                final AbstractDateTimeValue dt1 = (AbstractDateTimeValue) v1;
                final AbstractDateTimeValue dt2 = (AbstractDateTimeValue) v2;
                if (dt1.hasTimezone() != dt2.hasTimezone()) {
                    return BooleanValue.FALSE;
                }
            }

            // Different base types are never equal
            if (t1 != t2 && !Type.subTypeOf(t1, t2) && !Type.subTypeOf(t2, t1)) {
                return BooleanValue.FALSE;
            }

            // Same type — compare by value
            final int cmp = v1.compareTo(null, v2);
            return BooleanValue.valueOf(cmp == 0);
        } catch (final XPathException | RuntimeException e) {
            // Incomparable types or indeterminate ordering — return false per spec
            return BooleanValue.FALSE;
        }
    }

    private static boolean isNaN(final AtomicValue v) {
        if (v instanceof DoubleValue) {
            return Double.isNaN(((DoubleValue) v).getDouble());
        }
        if (v instanceof FloatValue) {
            return Float.isNaN(((FloatValue) v).getValue());
        }
        return false;
    }

    private static boolean isInfinite(final AtomicValue v) {
        if (v instanceof DoubleValue) {
            return Double.isInfinite(((DoubleValue) v).getDouble());
        }
        if (v instanceof FloatValue) {
            return Float.isInfinite(((FloatValue) v).getValue());
        }
        return false;
    }

    private static double toDouble(final AtomicValue v) {
        if (v instanceof DoubleValue) {
            return ((DoubleValue) v).getDouble();
        }
        if (v instanceof FloatValue) {
            return ((FloatValue) v).getValue();
        }
        return 0;
    }

    static boolean numericEqual(final NumericValue v1, final NumericValue v2) throws XPathException {
        // Both floating-point: use double comparison (handles 0.0 == -0.0)
        if ((v1 instanceof DoubleValue || v1 instanceof FloatValue)
                && (v2 instanceof DoubleValue || v2 instanceof FloatValue)) {
            return v1.getDouble() == v2.getDouble();
        }
        // Mixed floating-point and exact: convert to BigDecimal for exact mathematical comparison
        // This handles cases like atomic-equal(16777218, xs:double("16777218"))
        final java.math.BigDecimal bd1 = numericToBigDecimal(v1);
        final java.math.BigDecimal bd2 = numericToBigDecimal(v2);
        return bd1.compareTo(bd2) == 0;
    }

    private static java.math.BigDecimal numericToBigDecimal(final NumericValue v) throws XPathException {
        if (v instanceof DoubleValue) {
            // Use new BigDecimal(double) for exact binary representation,
            // not valueOf() which rounds via Double.toString()
            return new java.math.BigDecimal(((DoubleValue) v).getDouble());
        }
        if (v instanceof FloatValue) {
            return new java.math.BigDecimal(((FloatValue) v).getValue());
        }
        // Integer and decimal types: parse from string for exact representation
        return new java.math.BigDecimal(v.getStringValue());
    }


    private static boolean isStringLike(final int type) {
        return Type.subTypeOf(type, Type.STRING)
                || type == Type.UNTYPED_ATOMIC
                || Type.subTypeOf(type, Type.ANY_URI);
    }

    private static boolean isBinaryType(final int type) {
        return type == Type.HEX_BINARY || type == Type.BASE64_BINARY;
    }
}
