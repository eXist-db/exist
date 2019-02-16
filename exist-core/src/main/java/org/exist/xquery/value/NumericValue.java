package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.xquery.Constants;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

public abstract class NumericValue extends ComputableValue {

    public double getDouble() throws XPathException {
        return ((DoubleValue) convertTo(Type.DOUBLE)).getValue();
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
    public boolean compareTo(final Collator collator, final Comparison operator, final AtomicValue other)
            throws XPathException {
        if (other.isEmpty()) {
            //Never equal, or inequal...
            return false;
        }
        if (Type.subTypeOf(other.getType(), Type.NUMBER)) {
            if (isNaN()) {
                //NaN does not equal itself.
                if (((NumericValue) other).isNaN()) {
                    return operator == Comparison.NEQ;
                }
            }
            final double otherVal = ((NumericValue) other).getDouble();
            final double val = getDouble();
            switch (operator) {
                case EQ:
                    return val == otherVal;
                case NEQ:
                    return val != otherVal;
                case GT:
                    return val > otherVal;
                case GTEQ:
                    return val >= otherVal;
                case LT:
                    return val < otherVal;
                case LTEQ:
                    return val <= otherVal;
                default:
                    throw new XPathException("Type error: cannot apply operator to numeric value");
            }
        }
        throw new XPathException(ErrorCodes.XPTY0004, "Type error: cannot compare operands: " +
                Type.getTypeName(getType()) + " and " +
                Type.getTypeName(other.getType()));
    }

    @Override
    public int compareTo(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.NUMBER)) {
            if (isNaN()) {
                //NaN does not equal itself.
                if (((NumericValue) other).isNaN()) {
                    return Constants.INFERIOR;
                }
            }
            final double otherVal = ((NumericValue) other).getDouble();
            final double val = getDouble();
            return Double.compare(val, otherVal);
        } else {
            throw new XPathException("cannot compare numeric value to non-numeric value");
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

    public abstract NumericValue mod(NumericValue other) throws XPathException;

    //TODO : implement here ?
    public abstract IntegerValue idiv(NumericValue other) throws XPathException;

    public abstract NumericValue abs() throws XPathException;
}
