/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.value;

import com.ibm.icu.text.Collator;
import org.exist.util.FastStringBuffer;
import org.exist.util.FloatingPointConverter;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigDecimal;

public class DoubleValue extends NumericValue {
    // m × 2^e, where m is an integer whose absolute value is less than 2^53,
    // and e is an integer between -1075 and 970, inclusive.
    // In addition also -INF, +INF and NaN.
    public static final DoubleValue ZERO = new DoubleValue(0.0E0);
    public static final DoubleValue POSITIVE_INFINITY = new DoubleValue(Double.POSITIVE_INFINITY);
    public static final DoubleValue NEGATIVE_INFINITY = new DoubleValue(Double.NEGATIVE_INFINITY);
    public static final DoubleValue NaN = new DoubleValue(Double.NaN);

    private final double value;

    public DoubleValue(final double value) {
        this.value = value;
    }

    public DoubleValue(final AtomicValue otherValue) throws XPathException {
        this(otherValue.getStringValue());
    }

    public DoubleValue(final String stringValue) throws XPathException {
        try {
            if ("INF".equals(stringValue)) {
                value = Double.POSITIVE_INFINITY;
            } else if ("-INF".equals(stringValue)) {
                value = Double.NEGATIVE_INFINITY;
            } else if ("NaN".equals(stringValue)) {
                value = Double.NaN;
            } else {
                value = Double.parseDouble(stringValue);
            }
        } catch (final NumberFormatException e) {
            throw new XPathException(ErrorCodes.FORG0001, "cannot construct " + Type.getTypeName(this.getItemType()) +
                    " from '" + stringValue + "'");
        }
    }

    @Override
    public int getType() {
        return Type.DOUBLE;
    }

    @Override
    public String getStringValue() {
        final FastStringBuffer sb = new FastStringBuffer(20);
        //0 is a dummy parameter
        FloatingPointConverter.appendDouble(sb, value).getNormalizedString(0);
        return sb.toString();
    }

    public double getValue() {
        return value;
    }

    @Override
    public boolean hasFractionalPart() {
        if (isNaN()) {
            return false;
        }
        if (isInfinite()) {
            return false;
        }
        return new DecimalValue(new BigDecimal(value)).hasFractionalPart();
    }

    @Override
    public Item itemAt(final int pos) {
        return pos == 0 ? this : null;
    }

    @Override
    public boolean isNaN() {
        return Double.isNaN(value);
    }

    @Override
    public boolean isInfinite() {
        return Double.isInfinite(value);
    }

    @Override
    public boolean isZero() {
        return Double.compare(Math.abs(value), 0.0) == Constants.EQUAL;
    }

    @Override
    public boolean isNegative() {
        return (Double.compare(value, 0.0) < Constants.EQUAL);
    }

    @Override
    public boolean isPositive() {
        return (Double.compare(value, 0.0) > Constants.EQUAL);
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ATOMIC:
            case Type.ITEM:
            case Type.NUMBER:
            case Type.DOUBLE:
                return this;
            case Type.FLOAT:
                //if (Float.compare(value, 0.0f) && (value < Float.MIN_VALUE || value > Float.MAX_VALUE)
                //	throw new XPathException("Value is out of range for type xs:float");
                //return new FloatValue((float) value);
                return new FloatValue((float) value);
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.DECIMAL:
                if (isNaN()) {
                    throw new XPathException(ErrorCodes.FORG0001, "can not convert "
                            + Type.getTypeName(getType())
                            + "('"
                            + getStringValue()
                            + "') to "
                            + Type.getTypeName(requiredType));
                }
                if (isInfinite()) {
                    throw new XPathException(ErrorCodes.FORG0001, "can not convert "
                            + Type.getTypeName(getType())
                            + "('" + getStringValue()
                            + "') to "
                            + Type.getTypeName(requiredType));
                }
                return new DecimalValue(new BigDecimal(value));
            case Type.INTEGER:
            case Type.NON_POSITIVE_INTEGER:
            case Type.NEGATIVE_INTEGER:
            case Type.LONG:
            case Type.INT:
            case Type.SHORT:
            case Type.BYTE:
            case Type.NON_NEGATIVE_INTEGER:
            case Type.UNSIGNED_LONG:
            case Type.UNSIGNED_INT:
            case Type.UNSIGNED_SHORT:
            case Type.UNSIGNED_BYTE:
            case Type.POSITIVE_INTEGER:
                if (isNaN()) {
                    throw new XPathException(ErrorCodes.FORG0001, "can not convert "
                            + Type.getTypeName(getType())
                            + "('" + getStringValue()
                            + "') to "
                            + Type.getTypeName(requiredType));
                }
                if (Double.isInfinite(value)) {
                    throw new XPathException(ErrorCodes.FORG0001, "can not convert "
                            + Type.getTypeName(getType())
                            + "('"
                            + getStringValue()
                            + "') to "
                            + Type.getTypeName(requiredType));
                }
                if (requiredType != Type.INTEGER && value > Integer.MAX_VALUE) {
                    throw new XPathException(ErrorCodes.FOCA0003, "Value is out of range for type " + Type.getTypeName(requiredType));
                }
                return new IntegerValue(Double.valueOf(value).longValue(), requiredType);
            case Type.BOOLEAN:
                return new BooleanValue(this.effectiveBooleanValue());
            default:
                throw new XPathException(ErrorCodes.FORG0001, "cannot cast '"
                        + Type.getTypeName(this.getItemType())
                        + "(\""
                        + getStringValue()
                        + "\")' to "
                        + Type.getTypeName(requiredType));
        }
    }

    @Override
    public double getDouble() {
        return value;
    }

    @Override
    public int getInt() {
        return Long.valueOf(Math.round(value)).intValue();
    }

    @Override
    public long getLong() {
        return Math.round(value);
    }

    @Override
    public NumericValue ceiling() {
        return new DoubleValue(Math.ceil(value));
    }

    @Override
    public NumericValue floor() {
        return new DoubleValue(Math.floor(value));
    }

    @Override
    public NumericValue round() {
        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
            return this;
        }

        if (value >= -0.5 && value < 0.0) {
            return new DoubleValue(-0.0);
        }

        if (value > Long.MIN_VALUE && value < Long.MAX_VALUE) {
            return new DoubleValue(Math.round(value));
        }

        //too big return original value unchanged
        return this;
    }

    @Override
    public NumericValue round(final IntegerValue precision) throws XPathException {
        if (precision == null) {
            return round();
        }

        if (Double.isNaN(value) || Double.isInfinite(value) || value == 0.0) {
            return this;
        }

        /* use the decimal rounding method */
        return (DoubleValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.DOUBLE);
    }


    @Override
    public ComputableValue minus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(value - ((DoubleValue) other).value);
        } else {
            return minus((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public ComputableValue plus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(value + ((DoubleValue) other).value);
        } else {
            return plus((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public ComputableValue mult(final ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.DOUBLE:
                return new DoubleValue(value * ((DoubleValue) other).value);
            case Type.DAY_TIME_DURATION:
            case Type.YEAR_MONTH_DURATION:
                return other.mult(this);
            default:
                return mult((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public ComputableValue div(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.NUMBER)) {
            //Positive or negative zero divided by positive or negative zero returns NaN.
            if (this.isZero() && ((NumericValue) other).isZero()) {
                return NaN;
            }

            //A negative number divided by positive zero returns -INF.
            if (this.isNegative() &&
                    ((NumericValue) other).isZero() && ((NumericValue) other).isPositive()) {
                return NEGATIVE_INFINITY;
            }

            //A negative number divided by positive zero returns -INF.
            if (this.isNegative() &&
                    ((NumericValue) other).isZero() && ((NumericValue) other).isNegative()) {
                return POSITIVE_INFINITY;
            }

            //Division of Positive by negative zero returns -INF and INF, respectively.
            if (this.isPositive() &&
                    ((NumericValue) other).isZero() && ((NumericValue) other).isNegative()) {
                return NEGATIVE_INFINITY;
            }

            if (this.isPositive() &&
                    ((NumericValue) other).isZero() && ((NumericValue) other).isPositive()) {
                return POSITIVE_INFINITY;
            }

            //Also, INF or -INF divided by INF or -INF returns NaN.
            if (this.isInfinite() && ((NumericValue) other).isInfinite()) {
                return NaN;
            }
        }

        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(value / ((DoubleValue) other).value);
        } else {
            return div((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public IntegerValue idiv(final NumericValue other) throws XPathException {
        final ComputableValue result = div(other);
        return new IntegerValue(((IntegerValue) result.convertTo(Type.INTEGER)).getLong());
		/*
		if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
			double result = value / ((DoubleValue) other).value;
			if (result == Double.NaN || result == Double.POSITIVE_INFINITY || result == Double.NEGATIVE_INFINITY)
				throw new XPathException("illegal arguments to idiv");
			return new IntegerValue(new BigDecimal(result).toBigInteger(), Type.INTEGER);
		}
		throw new XPathException("idiv called with incompatible argument type: " + getType() + " vs " + other.getType());
		*/
    }

    @Override
    public NumericValue mod(final NumericValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(value % ((DoubleValue) other).value);
        } else {
            return mod((NumericValue) other.convertTo(getType()));
        }
    }

    @Override
    public NumericValue negate() {
        return new DoubleValue(-value);
    }

    @Override
    public NumericValue abs() {
        return new DoubleValue(Math.abs(value));
    }

    @Override
    public AtomicValue max(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(Math.max(value, ((DoubleValue) other).value));
        } else {
            return new DoubleValue(
                    Math.max(value, ((DoubleValue) other.convertTo(Type.DOUBLE)).value));
        }
    }

    @Override
    public AtomicValue min(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return new DoubleValue(Math.min(value, ((DoubleValue) other).value));
        } else {
            return new DoubleValue(
                    Math.min(value, ((DoubleValue) other.convertTo(Type.DOUBLE)).value));
        }
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        if (javaClass.isAssignableFrom(DoubleValue.class)) {
            return 0;
        }
        if (javaClass == Long.class || javaClass == long.class) {
            return 3;
        }
        if (javaClass == Integer.class || javaClass == int.class) {
            return 4;
        }
        if (javaClass == Short.class || javaClass == short.class) {
            return 5;
        }
        if (javaClass == Byte.class || javaClass == byte.class) {
            return 6;
        }
        if (javaClass == Double.class || javaClass == double.class) {
            return 1;
        }
        if (javaClass == Float.class || javaClass == float.class) {
            return 2;
        }
        if (javaClass == String.class) {
            return 7;
        }
        if (javaClass == Boolean.class || javaClass == boolean.class) {
            return 8;
        }
        if (javaClass == Object.class) {
            return 20;
        }

        return Integer.MAX_VALUE;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(DoubleValue.class)) {
            return (T) this;
        } else if (target == Double.class || target == double.class) {
            return (T) Double.valueOf(value);
        } else if (target == Float.class || target == float.class) {
            return (T) new Float(value);
        } else if (target == Long.class || target == long.class) {
            return (T) Long.valueOf(((IntegerValue) convertTo(Type.LONG)).getValue());
        } else if (target == Integer.class || target == int.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.INT);
            return (T) Integer.valueOf((int) v.getValue());
        } else if (target == Short.class || target == short.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
            return (T) Short.valueOf((short) v.getValue());
        } else if (target == Byte.class || target == byte.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
            return (T) Byte.valueOf((byte) v.getValue());
        } else if (target == String.class) {
            return (T) getStringValue();
        } else if (target == Boolean.class) {
            return (T) Boolean.valueOf(effectiveBooleanValue());
        }

        throw new XPathException(
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    /**
     * size writen by {link #serialize(short, boolean)}
     *
     * @return the size in number of bytes
     */
    public int getSerializedSize() {
        return 1 + 8;
    }

    @Override
    public int compareTo(final Object o) {
        final AtomicValue other = (AtomicValue) o;
        if (Type.subTypeOf(other.getType(), Type.DOUBLE)) {
            return Double.compare(value, ((DoubleValue) other).value);
        } else {
            return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;
        }
    }

    @Override
    public int hashCode() {
        return Double.valueOf(value).hashCode();
    }
}
