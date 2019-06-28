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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Definition: integer is derived from decimal by fixing the value of fractionDigits to be 0.
 * This results in the standard mathematical concept of the integer numbers.
 * The value space of integer is the infinite set {...,-2,-1,0,1,2,...}.
 * The base type of integer is decimal.
 * See http://www.w3.org/TR/xmlschema-2/#integer
 */
public class IntegerValue extends NumericValue {

    //TODO this class should be split into numerous sub classes for each xs: type with proper
    //inheritance as defined by http://www.w3.org/TR/xmlschema-2/#built-in-datatypes

    public static final IntegerValue ZERO = new IntegerValue(0);

    private static final BigInteger ZERO_BIGINTEGER = new BigInteger("0");
    private static final BigInteger ONE_BIGINTEGER = new BigInteger("1");
    private static final BigInteger MINUS_ONE_BIGINTEGER = new BigInteger("-1");

    private static final BigInteger LARGEST_LONG = new BigInteger("9223372036854775807");
    private static final BigInteger SMALLEST_LONG = new BigInteger("-9223372036854775808");

    private static final BigInteger LARGEST_UNSIGNED_LONG = new BigInteger("18446744073709551615");

    private static final BigInteger LARGEST_INT = new BigInteger("2147483647");
    private static final BigInteger SMALLEST_INT = new BigInteger("-2147483648");

    private static final BigInteger LARGEST_UNSIGNED_INT = new BigInteger("4294967295");

    private static final BigInteger LARGEST_SHORT = new BigInteger("32767");
    private static final BigInteger SMALLEST_SHORT = new BigInteger("-32768");

    private static final BigInteger LARGEST_UNSIGNED_SHORT = new BigInteger("65535");

    private static final BigInteger LARGEST_BYTE = new BigInteger("127");
    private static final BigInteger SMALLEST_BYTE = new BigInteger("-128");

    private static final BigInteger LARGEST_UNSIGNED_BYTE = new BigInteger("255");

    private final BigInteger value;
    // 	private long value;

    //should default type be NUMBER or LONG ? -shabanovd
    private final int type;

    public IntegerValue(final BigInteger value, final int requiredType) {
        this.value = value;
        this.type = requiredType;
    }

    public IntegerValue(final BigInteger integer) {
        this(integer, Type.INTEGER);
    }

    public IntegerValue(final long value) {
        this(BigInteger.valueOf(value));
    }

    public IntegerValue(final long value, final int type) throws XPathException {
        this(BigInteger.valueOf(value), type);
        if (!checkType(value, type)) {
            throw new XPathException(
                    "Value is not a valid integer for type " + Type.getTypeName(type));
        }
    }

    public IntegerValue(final String stringValue) throws XPathException {
        try {
            this.value = new BigInteger(StringValue.trimWhitespace(stringValue));
            this.type = Type.INTEGER;
        } catch (final NumberFormatException e) {
            throw new XPathException(ErrorCodes.FORG0001,
                    "failed to convert '" + stringValue + "' to an integer: " + e.getMessage(), e);
        }
    }

    public IntegerValue(final String stringValue, final int requiredType) throws XPathException {
        try {
            this.value = new BigInteger(StringValue.trimWhitespace(stringValue));
            this.type = requiredType;
            if (!(checkType())) {
                throw new XPathException(ErrorCodes.FORG0001, "can not convert '" +
                        stringValue + "' to " + Type.getTypeName(type));
            }
        } catch (final NumberFormatException e) {
            throw new XPathException(ErrorCodes.FORG0001, "can not convert '" +
                    stringValue + "' to " + Type.getTypeName(requiredType));
        }
    }

    private static boolean checkType(final long value, final int type) throws XPathException {
        switch (type) {
            case Type.LONG:
            case Type.INTEGER:
            case Type.DECIMAL:
                return true;
            case Type.NON_POSITIVE_INTEGER:
                return value < 1;
            case Type.NEGATIVE_INTEGER:
                return value < 0;
            case Type.INT:
                return value >= -4294967295L && value <= 4294967295L;
            case Type.SHORT:
                return value >= -65535 && value <= 65535;
            case Type.BYTE:
                return value >= -255 && value <= 255;
            case Type.NON_NEGATIVE_INTEGER:
                return value > -1;
            case Type.UNSIGNED_LONG:
                return value > -1;
            case Type.UNSIGNED_INT:
                return value > -1 && value <= 4294967295L;
            case Type.UNSIGNED_SHORT:
                return value > -1 && value <= 65535;
            case Type.UNSIGNED_BYTE:
                return value > -1 && value <= 255;
            case Type.POSITIVE_INTEGER:
                return value > 0; // jmv >= 0;
        }
        throw new XPathException("Unknown type: " + Type.getTypeName(type));
    }

    private boolean checkType() throws XPathException {
        switch (type) {

            case Type.LONG:
                // jmv: add test since now long is not the default implementation anymore:
                return value.compareTo(SMALLEST_LONG) >= 0 &&
                        value.compareTo(LARGEST_LONG) <= 0;

            case Type.UNSIGNED_LONG:
                return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                        value.compareTo(LARGEST_UNSIGNED_LONG) <= 0;

            case Type.INTEGER:
            case Type.DECIMAL:
                return true;

            case Type.POSITIVE_INTEGER:
                return value.compareTo(ZERO_BIGINTEGER) == 1; // >0
            case Type.NON_NEGATIVE_INTEGER:
                return value.compareTo(MINUS_ONE_BIGINTEGER) == 1; // > -1

            case Type.NEGATIVE_INTEGER:
                return value.compareTo(ZERO_BIGINTEGER) == -1; // <0
            case Type.NON_POSITIVE_INTEGER:
                return value.compareTo(ONE_BIGINTEGER) == -1; // <1

            case Type.INT:
                return value.compareTo(SMALLEST_INT) >= 0 &&
                        value.compareTo(LARGEST_INT) <= 0;

            case Type.UNSIGNED_INT:
                return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                        value.compareTo(LARGEST_UNSIGNED_INT) <= 0;

            case Type.SHORT:
                return value.compareTo(SMALLEST_SHORT) >= 0 &&
                        value.compareTo(LARGEST_SHORT) <= 0;

            case Type.UNSIGNED_SHORT:
                return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                        value.compareTo(LARGEST_UNSIGNED_SHORT) <= 0;

            case Type.BYTE:
                return value.compareTo(SMALLEST_BYTE) >= 0 &&
                        value.compareTo(LARGEST_BYTE) <= 0;

            case Type.UNSIGNED_BYTE:
                return value.compareTo(ZERO_BIGINTEGER) >= 0 &&
                        value.compareTo(LARGEST_UNSIGNED_BYTE) <= 0;
        }

        throw new XPathException("Unknown type: " + Type.getTypeName(type));
    }

    @Override
    public int getType() {
        return type;
    }

    @Override
    public boolean hasFractionalPart() {
        return false;
    }

    @Override
    public Item itemAt(final int pos) {
        return pos == 0 ? this : null;
    }

    public long getValue() {
        return value.longValue();
    }

    @Override
    public String getStringValue() {
        return // Long.toString(value);
                value.toString();
    }

    @Override
    public boolean isNaN() {
        return false;
    }

    @Override
    public boolean isInfinite() {
        return false;
    }

    @Override
    public boolean isZero() {
        return value.signum() == 0;
        //return value.compareTo(ZERO_BIGINTEGER) == Constants.EQUAL;
    }

    @Override
    public boolean isNegative() {
        return value.signum() < 0;
    }

    @Override
    public boolean isPositive() {
        return value.signum() > 0;
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        if (this.type == requiredType) {
            return this;
        }

        switch (requiredType) {
            case Type.ATOMIC:
            case Type.ITEM:
                return this;
            case Type.DECIMAL:
                return new DecimalValue(new BigDecimal(value));
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            case Type.NUMBER:
            case Type.LONG:
            case Type.INTEGER:
            case Type.NON_POSITIVE_INTEGER:
            case Type.NEGATIVE_INTEGER:
            case Type.INT:
            case Type.SHORT:
            case Type.BYTE:
            case Type.NON_NEGATIVE_INTEGER:
            case Type.UNSIGNED_LONG:
            case Type.UNSIGNED_INT:
            case Type.UNSIGNED_SHORT:
            case Type.UNSIGNED_BYTE:
            case Type.POSITIVE_INTEGER:
                return new IntegerValue(value, requiredType);
            case Type.DOUBLE:
                return new DoubleValue(value.doubleValue());
            case Type.FLOAT:
                return new FloatValue(value.floatValue());
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.BOOLEAN:
                return (value.compareTo(ZERO_BIGINTEGER) == 0) ? BooleanValue.FALSE : BooleanValue.TRUE;
            default:
                throw new XPathException(ErrorCodes.FORG0001,
                        "cannot convert '"
                                + Type.getTypeName(this.getType())
                                + " ("
                                + value
                                + ")' into "
                                + Type.getTypeName(requiredType));
        }
    }

    @Override
    public int getInt() {
        return value.intValue();
    }

    @Override
    public long getLong() {
        return value.longValue();
    }

    @Override
    public double getDouble() {
        return value.doubleValue();
    }

    @Override
    public NumericValue ceiling() {
        return this;
    }

    @Override
    public NumericValue floor() {
        return this;
    }

    @Override
    public NumericValue round() {
        return this;
    }

    @Override
    public NumericValue round(final IntegerValue precision) throws XPathException {
        if (precision == null) {
            return round();
        }

        if (precision.getInt() <= 0) {
            return (IntegerValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.INTEGER);
        } else {
            return this;
        }
    }

    @Override
    public ComputableValue minus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER))
        // return new IntegerValue(value - ((IntegerValue) other).value, type);
        {
            return new IntegerValue(value.subtract(((IntegerValue) other).value), type);
        } else {
            return ((ComputableValue) convertTo(other.getType())).minus(other);
        }
    }

    @Override
    public ComputableValue plus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER))
        // return new IntegerValue(value + ((IntegerValue) other).value, type);
        {
            return new IntegerValue(value.add(((IntegerValue) other).value), type);
        } else {
            return ((ComputableValue) convertTo(other.getType())).plus(other);
        }
    }

    @Override
    public ComputableValue mult(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(value.multiply(((IntegerValue) other).value), type);
        } else if (Type.subTypeOf(other.getType(), Type.DURATION)) {
            return other.mult(this);
        } else {
            return ((ComputableValue) convertTo(other.getType())).mult(other);
        }
    }

    /**
     * The div operator performs floating-point division according to IEEE 754.
     *
     * @see org.exist.xquery.value.NumericValue#idiv(org.exist.xquery.value.NumericValue)
     */
    @Override
    public ComputableValue div(final ComputableValue other) throws XPathException {
        if (other instanceof IntegerValue) {
            if (((IntegerValue) other).isZero()) {
                throw new XPathException(ErrorCodes.FOAR0001, "division by zero");
            }
            //http://www.w3.org/TR/xpath20/#mapping : numeric; but xs:decimal if both operands are xs:integer
            final BigDecimal d = new BigDecimal(value);
            final BigDecimal od = new BigDecimal(((IntegerValue) other).value);
            final int scale = Math.max(18, Math.max(d.scale(), od.scale()));
            return new DecimalValue(d.divide(od, scale, BigDecimal.ROUND_HALF_DOWN));
        } else {
            //TODO : review type promotion
            return ((ComputableValue) convertTo(other.getType())).div(other);
        }
    }

    @Override
    public IntegerValue idiv(final NumericValue other) throws XPathException {
        if (other.isZero())
        //If the divisor is (positive or negative) zero, then an error is raised [err:FOAR0001]
        {
            throw new XPathException(ErrorCodes.FOAR0001, "division by zero");
        }
        final ComputableValue result = div(other);
        return new IntegerValue(((IntegerValue) result.convertTo(Type.INTEGER)).getLong());
    }

    @Override
    public NumericValue mod(final NumericValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            if (other.isZero()) {
                throw new XPathException(ErrorCodes.FOAR0001, "division by zero");
            }

            // long ov = ((IntegerValue) other).value.longValue();
            final BigInteger ov = ((IntegerValue) other).value;
            return new IntegerValue(value.remainder(ov), type);
        } else {
            return ((NumericValue) convertTo(other.getType())).mod(other);
        }
    }

    @Override
    public NumericValue negate() {
        return new IntegerValue(value.negate());
    }

    @Override
    public NumericValue abs() {
        return new IntegerValue(value.abs(), type);
    }

    @Override
    public AtomicValue max(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(value.max(((IntegerValue) other).value));
        } else {
            return convertTo(other.getType()).max(collator, other);
        }
    }

    @Override
    public AtomicValue min(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(value.min(((IntegerValue) other).value));
        } else {
            return convertTo(other.getType()).min(collator, other);
        }
    }

    @Override
    public int conversionPreference(final Class<?> javaClass) {
        if (javaClass.isAssignableFrom(IntegerValue.class)) {
            return 0;
        }
        if (javaClass == Long.class || javaClass == long.class) {
            return 1;
        }
        if (javaClass == Integer.class || javaClass == int.class) {
            return 2;
        }
        if (javaClass == Short.class || javaClass == short.class) {
            return 3;
        }
        if (javaClass == Byte.class || javaClass == byte.class) {
            return 4;
        }
        if (javaClass == Double.class || javaClass == double.class) {
            return 5;
        }
        if (javaClass == Float.class || javaClass == float.class) {
            return 6;
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
        if (target.isAssignableFrom(IntegerValue.class)) {
            return (T) this;
        } else if (target == Long.class || target == long.class) {
            // ?? jmv: return new Long(value);
            return (T) Long.valueOf(value.longValue());
        } else if (target == Integer.class || target == int.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.INT);
            return (T) Integer.valueOf(v.value.intValue());
        } else if (target == Short.class || target == short.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
            return (T) Short.valueOf(v.value.shortValue());
        } else if (target == Byte.class || target == byte.class) {
            final IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
            return (T) Byte.valueOf(v.value.byteValue());
        } else if (target == Double.class || target == double.class) {
            final DoubleValue v = (DoubleValue) convertTo(Type.DOUBLE);
            return (T) Double.valueOf(v.getValue());
        } else if (target == Float.class || target == float.class) {
            final FloatValue v = (FloatValue) convertTo(Type.FLOAT);
            return (T) Float.valueOf(v.value);
        } else if (target == Boolean.class || target == boolean.class) {
            return (T) new BooleanValue(effectiveBooleanValue());
        } else if (target == String.class) {
            return (T) value.toString();
        } else if (target == BigInteger.class) {
            return (T) new BigInteger(value.toByteArray());
        } else if (target == Object.class) {
            return (T) value; // Long(value);
        }

        throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
                " to Java object of type " + target.getName());
    }

    @Override
    public int compareTo(final Object o) {
        final AtomicValue other = (AtomicValue) o;
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return value.compareTo(((IntegerValue) other).value);
        } else {
            return getType() > other.getType() ? 1 : -1;
        }
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
