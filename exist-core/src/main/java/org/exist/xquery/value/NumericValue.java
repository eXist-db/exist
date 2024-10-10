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
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;
import java.math.RoundingMode;
import java.util.function.IntSupplier;

public abstract class NumericValue extends ComputableValue {

    protected NumericValue() {
        this(null);
    }

    protected NumericValue(final Expression expression) {
        super(expression);
    }

    public double getDouble() throws XPathException {
        return ((DoubleValue) convertTo(Type.DOUBLE)).getValue();
    }

    public float getFloat() throws XPathException {
        return ((FloatValue) convertTo(Type.FLOAT)).getValue();
    }

    public long getLong() throws XPathException {
        return ((IntegerValue) convertTo(Type.INTEGER)).getValue();
    }

    public int getInt() throws XPathException {
        return (int) ((IntegerValue) convertTo(Type.INTEGER)).getValue();
    }

    public abstract boolean hasFractionalPart();

    public abstract boolean isNaN();

    public abstract boolean isInfinite();

    public abstract boolean isZero();

    public abstract boolean isNegative();

    public abstract boolean isPositive();

    @Override
    public boolean effectiveBooleanValue() {
        //If its operand is a singleton value of any numeric type or derived from a numeric type,
        //fn:boolean returns false if the operand value is NaN or is numerically equal to zero;
        //otherwise it returns true.
        return !(isNaN() || isZero());
    }

    @Override
    public final boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other)
            throws XPathException {
        if (other.isEmpty()) {
            //Never equal, or inequal...
            return false;
        }

        if (Type.subTypeOfUnion(other.getType(), Type.NUMERIC)) {
            if (isNaN() || ((NumericValue) other).isNaN()) {
                // left or right is NaN

                // NaN never equals NaN or any other value
                return operator == Comparison.NEQ;
            }

            final IntSupplier comparison = createComparisonWith((NumericValue) other);
            if (comparison == null) {
                throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot apply operator to numeric value");
            }

            return switch (operator) {
                case EQ -> comparison.getAsInt() == 0;
                case NEQ -> comparison.getAsInt() != 0;
                case GT -> comparison.getAsInt() > 0;
                case GTEQ -> comparison.getAsInt() >= 0;
                case LT -> comparison.getAsInt() < 0;
                case LTEQ -> comparison.getAsInt() <= 0;
                default ->
                        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot apply operator to numeric value");
            };
        }

        throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot compare operands: " +
                Type.getTypeName(getType()) + " and " +
                Type.getTypeName(other.getType()));
    }

    /**
     * Creates a function which when called performs a comparison between this NumericValue
     * and the {@code other} NumericValue.
     *
     * @param other the other numberic value to compare this against.
     *
     * @return the comparison function or null.
     */
    protected abstract @Nullable IntSupplier createComparisonWith(final NumericValue other);

    @Override
    public final int compareTo(final Collator collator, final AtomicValue other) throws XPathException {
        if (other.isEmpty()) {
            //Never equal, or inequal...
            return Constants.INFERIOR;
        }

        if (Type.subTypeOfUnion(other.getType(), Type.NUMERIC)) {
            if (isNaN()) {
                //NaN does not equal itself.
                if (((NumericValue) other).isNaN()) {
                    return Constants.INFERIOR;
                }
            }

            final IntSupplier comparison = createComparisonWith((NumericValue) other);
            if (comparison == null) {
                throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "Type error: cannot apply operator to numeric value");
            }

            return comparison.getAsInt();
        } else {
            throw new XPathException(getExpression(), ErrorCodes.XPTY0004, "cannot compare numeric value to non-numeric value");
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null) {
            return false;
        }
        if (NumericValue.class.isAssignableFrom(obj.getClass()))
            try {
                return compareTo(null, Comparison.EQ, (NumericValue) obj);
            } catch (final XPathException e) {
                // should not be possible due to type check
            }
        return false;
    }

    public abstract NumericValue negate() throws XPathException;

    public abstract NumericValue ceiling() throws XPathException;

    public abstract NumericValue floor() throws XPathException;

    public abstract NumericValue round() throws XPathException;

    public abstract NumericValue round(IntegerValue precision) throws XPathException;

    public abstract NumericValue round(IntegerValue precision, RoundingMode roundingMode) throws XPathException;

    public abstract NumericValue mod(NumericValue other) throws XPathException;

    //TODO : implement here ?
    public abstract IntegerValue idiv(NumericValue other) throws XPathException;

    public abstract NumericValue abs() throws XPathException;
}
