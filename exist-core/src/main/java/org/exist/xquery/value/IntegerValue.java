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
import org.exist.util.ByteConversion;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

/**
 * Definition: integer is derived from decimal by fixing the value of fractionDigits to be 0.
 * This results in the standard mathematical concept of the integer numbers.
 * The value space of integer is the infinite set {...,-2,-1,0,1,2,...}.
 * The base type of integer is decimal.
 * See <a href="http://www.w3.org/TR/xmlschema-2/#integer">http://www.w3.org/TR/xmlschema-2/#integer</a>
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

    final BigInteger value;
    private final int type;

    public IntegerValue(final long value) {
        this(null, value);
    }

    public IntegerValue(final Expression expression, final long value) {
        super(expression);
        this.value = BigInteger.valueOf(value);
        this.type = Type.INTEGER;
    }

    public IntegerValue(final BigInteger integer) {
        this(null, integer);
    }

    public IntegerValue(final Expression expression, final BigInteger integer) {
        super(expression);
        this.value = integer;
        this.type = Type.INTEGER;
    }

    public IntegerValue(final long value, final int type) throws XPathException {
        this(null, value, type);
    }

    public IntegerValue(final Expression expression, final long value, final int type) throws XPathException {
        this(expression, BigInteger.valueOf(value), type);
    }

    public IntegerValue(final BigInteger value, final int requiredType) throws XPathException {
        this(null, value, requiredType);
    }

    public IntegerValue(final Expression expression, final BigInteger value, final int requiredType) throws XPathException {
        this(expression, value, requiredType, true);
    }

    private IntegerValue(final BigInteger value, final int requiredType, final boolean checkType) throws XPathException {
        this(null, value, requiredType, checkType);
    }

    private IntegerValue(final Expression expression, final BigInteger value, final int requiredType, boolean checkType) throws XPathException {
        super(expression);
        this.value = value;
        this.type = requiredType;

        if (checkType && !checkType()) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "can not convert '" +
                    value + "' to " + Type.getTypeName(type));
        }
    }

    public IntegerValue(final String stringValue) throws XPathException {
        this(null, stringValue);
    }

    public IntegerValue(final Expression expression, final String stringValue) throws XPathException {
        this(expression, stringValue, Type.INTEGER);
    }

    public IntegerValue(final String stringValue, final int requiredType) throws XPathException {
        this(null, stringValue, requiredType);
    }

    public IntegerValue(final Expression expression, final String stringValue, final int requiredType) throws XPathException {
        super(expression);
        try {
            this.value = new BigInteger(StringValue.trimWhitespace(stringValue));
            this.type = requiredType;
            if (!(checkType())) {
                throw new XPathException(getExpression(), ErrorCodes.FORG0001, "can not convert '" +
                        stringValue + "' to " + Type.getTypeName(type));
            }
        } catch (final NumberFormatException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "can not convert '" +
                    stringValue + "' to " + Type.getTypeName(requiredType));
        }
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

        throw new XPathException(getExpression(), "Unknown type: " + Type.getTypeName(type));
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
    protected @Nullable IntSupplier createComparisonWith(final NumericValue other) {
        final IntSupplier comparison;
        if (other instanceof IntegerValue) {
            comparison = () -> value.compareTo(((IntegerValue)other).value);
        } else if (other instanceof DecimalValue) {
            comparison = () -> new BigDecimal(value).compareTo(((DecimalValue)other).value);
        } else if (other instanceof DoubleValue) {
            comparison = () -> new BigDecimal(value).compareTo(BigDecimal.valueOf(((DoubleValue)other).value));
        } else if (other instanceof FloatValue) {
            comparison = () -> new BigDecimal(value).compareTo(BigDecimal.valueOf(((FloatValue)other).value));
        } else {
            return null;
        }
        return comparison;
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
        if (this.type == requiredType) {
            return this;
        }

        switch (requiredType) {
            case Type.ANY_ATOMIC_TYPE:
            case Type.ITEM:
                return this;
            case Type.DECIMAL:
                return new DecimalValue(getExpression(), new BigDecimal(value));
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            case Type.NUMERIC:
                return new IntegerValue(getExpression(), value, requiredType, false);
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
                return new IntegerValue(getExpression(), value, requiredType);
            case Type.DOUBLE:
                return new DoubleValue(getExpression(), value.doubleValue());
            case Type.FLOAT:
                return new FloatValue(getExpression(), value.floatValue());
            case Type.STRING:
                return new StringValue(getExpression(), getStringValue());
            case Type.BOOLEAN:
                return (value.compareTo(ZERO_BIGINTEGER) == 0) ? BooleanValue.FALSE : BooleanValue.TRUE;
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001,
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
    public NumericValue round(final IntegerValue precision, final RoundingMode roundingMode) throws XPathException {
        if (precision == null) {
            return round();
        }

        if (precision.getInt() <= 0) {
            return (IntegerValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision, roundingMode).convertTo(Type.INTEGER);
        } else {
            return this;
        }
    }

    @Override
    public NumericValue round(final IntegerValue precision) throws XPathException {

        /* use the decimal rounding method */
        return round(precision, DecimalValue.DEFAULT_ROUNDING_MODE);
    }

    @Override
    public ComputableValue minus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER))
        // return new IntegerValue(value - ((IntegerValue) other).value, type);
        {
            return new IntegerValue(getExpression(), value.subtract(((IntegerValue) other).value), type);
        } else {
            return ((ComputableValue) convertTo(other.getType())).minus(other);
        }
    }

    @Override
    public ComputableValue plus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER))
        // return new IntegerValue(value + ((IntegerValue) other).value, type);
        {
            return new IntegerValue(getExpression(), value.add(((IntegerValue) other).value), type);
        } else {
            return ((ComputableValue) convertTo(other.getType())).plus(other);
        }
    }

    @Override
    public ComputableValue mult(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(getExpression(), value.multiply(((IntegerValue) other).value), type);
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
                throw new XPathException(getExpression(), ErrorCodes.FOAR0001, "division by zero");
            }
            //http://www.w3.org/TR/xpath20/#mapping : numeric; but xs:decimal if both operands are xs:integer
            final BigDecimal d = new BigDecimal(value);
            final BigDecimal od = new BigDecimal(((IntegerValue) other).value);
            final int scale = Math.max(18, Math.max(d.scale(), od.scale()));
            return new DecimalValue(getExpression(), d.divide(od, scale, RoundingMode.HALF_DOWN));
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
            throw new XPathException(getExpression(), ErrorCodes.FOAR0001, "division by zero");
        }
        final ComputableValue result = div(other);
        return new IntegerValue(getExpression(), ((IntegerValue) result.convertTo(Type.INTEGER)).getLong());
    }

    @Override
    public NumericValue mod(final NumericValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            if (other.isZero()) {
                throw new XPathException(getExpression(), ErrorCodes.FOAR0001, "division by zero");
            }

            // long ov = ((IntegerValue) other).value.longValue();
            final BigInteger ov = ((IntegerValue) other).value;
            return new IntegerValue(getExpression(), value.remainder(ov), type);
        } else {
            return ((NumericValue) convertTo(other.getType())).mod(other);
        }
    }

    @Override
    public NumericValue negate() {
        return new IntegerValue(getExpression(), value.negate());
    }

    @Override
    public NumericValue abs() throws XPathException {
        return new IntegerValue(getExpression(), value.abs(), type);
    }

    @Override
    public AtomicValue max(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(getExpression(), value.max(((IntegerValue) other).value));
        } else {
            return convertTo(other.getType()).max(collator, other);
        }
    }

    @Override
    public AtomicValue min(final Collator collator, final AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
            return new IntegerValue(getExpression(), value.min(((IntegerValue) other).value));
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
            return (T) new BooleanValue(getExpression(), effectiveBooleanValue());
        } else if (target == byte[].class) {
            return (T) serialize();
        } else if (target == ByteBuffer.class) {
            return (T) ByteBuffer.wrap(serialize());
        } else if (target == String.class) {
            return (T) value.toString();
        } else if (target == BigInteger.class) {
            return (T) new BigInteger(value.toByteArray());
        } else if (target == Object.class) {
            return (T) value;
        }

        throw new XPathException(getExpression(), "cannot convert value of type " + Type.getTypeName(getType()) +
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

    //TODO(AR) this is not a very good serialization method, the size of the IntegerValue is unbounded and may not fit in 8 bytes.
    /**
     * Serializes to a byte array.
     *
     * 8 bytes.
     *
     * @return the serialized data.
     */
    public byte[] serialize() {
        final byte[] buf = new byte[8];
        final long l = value.longValue() - Long.MIN_VALUE;
        ByteConversion.longToByte(l, buf, 0);
        return buf;
    }

    //TODO(AR) this is not a very good serialization method, the size of the IntegerValue is unbounded and may not fit in 8 bytes.
    /**
     * Serializes to a ByteBuffer.
     *
     * 8 bytes.
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) throws IOException {
        final long l = value.longValue() - Long.MIN_VALUE;
        ByteConversion.longToByte(l, buf);
    }

    //TODO(AR) this is not a very good deserialization method, the size of the IntegerValue is unbounded and may not fit in 8 bytes.
    public static IntegerValue deserialize(final ByteBuffer buf) {
        final long l = ByteConversion.byteToLong(buf) ^ 0x8000000000000000L;
        return new IntegerValue(l);
    }
}
