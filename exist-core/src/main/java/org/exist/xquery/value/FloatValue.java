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
import net.sf.saxon.tree.util.FastStringBuffer;
import net.sf.saxon.value.FloatingPointConverter;
import org.exist.util.ByteConversion;
import org.exist.xquery.Constants;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;

import javax.annotation.Nullable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.util.function.IntSupplier;

/**
 * @author wolf
 */
public class FloatValue extends NumericValue {

    public static final int SERIALIZED_SIZE = 4;

    // m × 2^e, where m is an integer whose absolute value is less than 2^24, 
    // and e is an integer between -149 and 104, inclusive.
    // In addition also -INF, +INF and NaN.
    public final static FloatValue NaN = new FloatValue(Float.NaN);
    public final static FloatValue POSITIVE_INFINITY = new FloatValue(Float.POSITIVE_INFINITY);
    public final static FloatValue NEGATIVE_INFINITY = new FloatValue(Float.NEGATIVE_INFINITY);
    public final static FloatValue ZERO = new FloatValue(0.0E0f);

    final float value;

    public FloatValue(final float value) {
        this(null, value);
    }

    public FloatValue(final Expression expression, float value) {
        super(expression);
        this.value = value;
    }

    public FloatValue(final String stringValue) throws XPathException {
        this(null, stringValue);
    }

    public FloatValue(final Expression expression, String stringValue) throws XPathException {
        super(expression);
        try {
            switch (stringValue) {
                case "INF" -> value = Float.POSITIVE_INFINITY;
                case "-INF" -> value = Float.NEGATIVE_INFINITY;
                case "NaN" -> value = Float.NaN;
                case null, default -> value = Float.parseFloat(stringValue);
            }
        } catch (final NumberFormatException e) {
            throw new XPathException(getExpression(), ErrorCodes.FORG0001, "cannot construct "
                    + Type.getTypeName(this.getItemType())
                    + " from \""
                    + getStringValue()
                    + "\"");
        }
    }

    public float getValue() {
        return value;
    }

	/*
	public float getFloat() throws XPathException  {
		return value;
	}
	*/

    /* (non-Javadoc)
     * @see org.exist.xquery.value.AtomicValue#getType()
     */
    public int getType() {
        return Type.FLOAT;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#getStringValue()
     */
    public String getStringValue() throws XPathException {
		/*
		if (value == Float.POSITIVE_INFINITY)
			return "INF"; 
		if (value == Float.NEGATIVE_INFINITY)
			return "-INF";		
		String s = String.valueOf(value);
		s = s.replaceAll("\\.0+$", "");
		return s;	
		*/

        final FastStringBuffer sb = new FastStringBuffer(20);
        //0 is a dummy parameter
        FloatingPointConverter.appendFloat(sb, value, false);
        return sb.toString();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#isNaN()
     */
    public boolean isNaN() {
        return Float.isNaN(value);
    }

    public boolean isInfinite() {
        return Float.isInfinite(value);
    }

    public boolean isZero() {
        return Float.compare(value, 0f) == Constants.EQUAL;
    }

    public boolean isNegative() {
        return (Float.compare(value, 0f) < Constants.EQUAL);
    }

    public boolean isPositive() {
        return (Float.compare(value, 0f) > Constants.EQUAL);
    }

    @Override
    protected @Nullable IntSupplier createComparisonWith(final NumericValue other) {
        final IntSupplier comparison;
        if (isNaN()) {
            comparison = () -> Constants.INFERIOR;
        } else if (other.isNaN()) {
            comparison = () -> Constants.SUPERIOR;
        } else if (isPositiveInfinity()) {
            // +INF
            comparison = () -> other.isPositiveInfinity() ? Constants.EQUAL : Constants.SUPERIOR;
        } else if (other.isPositiveInfinity()) {
            comparison = () -> Constants.INFERIOR;
        } else if (isNegativeInfinity()) {
            // -INF
            comparison = () -> other.isNegativeInfinity() ? Constants.EQUAL : Constants.INFERIOR;
        } else if (other.isNegativeInfinity()) {
            comparison = () -> Constants.SUPERIOR;
        } else if (other instanceof IntegerValue iv) {
            comparison = () -> BigDecimal.valueOf(value).compareTo(new BigDecimal(iv.value));
        } else if (other instanceof DecimalValue dv) {
            final BigDecimal promoted = new BigDecimal(Float.toString(value));
            comparison = () -> promoted.compareTo(dv.value);
        } else if (other instanceof DoubleValue dv) {
            comparison = () -> BigDecimal.valueOf(value).compareTo(BigDecimal.valueOf(dv.value));
        } else if (other instanceof FloatValue fv) {
            comparison = () -> Float.compare(value, fv.value);
        } else {
            return null;
        }
        return comparison;
    }

    public boolean hasFractionalPart() {
        if (isNaN()) {
            return false;
        }
        if (isInfinite()) {
            return false;
        }
        return new DecimalValue(getExpression(), BigDecimal.valueOf(value)).hasFractionalPart();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ANY_ATOMIC_TYPE:
            case Type.ITEM:
            case Type.NUMERIC:
            case Type.FLOAT:
                return this;
            case Type.DOUBLE:
                return new DoubleValue(getExpression(), value);
            case Type.STRING:
                return new StringValue(getExpression(), getStringValue());
            case Type.DECIMAL:
                return new DecimalValue(getExpression(), value);
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
                if (!(Float.isInfinite(value) || Float.isNaN(value))) {
                    return new IntegerValue(getExpression(), (long) value, requiredType);
                } else {
                    throw new XPathException(getExpression(), ErrorCodes.FOCA0002, "cannot convert ' xs:float(\""
                            + getStringValue()
                            + "\")' to "
                            + Type.getTypeName(requiredType));
                }
            case Type.BOOLEAN:
                return (value == 0.0f || Float.isNaN(value))
                        ? BooleanValue.FALSE
                        : BooleanValue.TRUE;
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getExpression(), getStringValue());
            default:
                throw new XPathException(getExpression(), ErrorCodes.FORG0001, "cannot cast '"
                        + Type.getTypeName(this.getItemType())
                        + "(\""
                        + getStringValue()
                        + "\")' to "
                        + Type.getTypeName(requiredType));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#negate()
     */
    public NumericValue negate() throws XPathException {
        return new FloatValue(getExpression(), -value);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#ceiling()
     */
    public NumericValue ceiling() throws XPathException {
        return new FloatValue(getExpression(), (float) Math.ceil(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#floor()
     */
    public NumericValue floor() throws XPathException {
        return new FloatValue(getExpression(), (float) Math.floor(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#round()
     */
    public NumericValue round() throws XPathException {

        if (Float.isNaN(value) || Float.isInfinite(value) || value == 0.0) {
            return this;
        }

        if (value >= -0.5 && value < 0.0) {
            return new DoubleValue(getExpression(), -0.0);
        }

        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return new FloatValue(getExpression(), (float) Math.round(value));
        }

        //too big return original value unchanged
        return this;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
     */
    public NumericValue round(final IntegerValue precision, final RoundingMode roundingMode) throws XPathException {
        if (precision == null) {
            return round();
        }

        if (Float.isNaN(value) || Float.isInfinite(value) || value == 0.0) {
            return this;
        }
		
		/* use the decimal rounding method */
        return (FloatValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision, roundingMode).convertTo(Type.FLOAT);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
     */
    public NumericValue round(IntegerValue precision) throws XPathException {
        return round(precision, DecimalValue.DEFAULT_ROUNDING_MODE);
    }

    @Override
    public ComputableValue minus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), value - ((FloatValue) other).value);
        } else if (other.getType() == Type.DOUBLE) {
            // type promotion - see https://www.w3.org/TR/xpath-31/#promotion
            return ((DoubleValue) convertTo(Type.DOUBLE)).minus(other);
        } else {
            return minus((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public ComputableValue plus(final ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), value + ((FloatValue) other).value);
        } else if (other.getType() == Type.DOUBLE) {
            // type promotion - see https://www.w3.org/TR/xpath-31/#promotion
            return ((DoubleValue) convertTo(Type.DOUBLE)).plus(other);
        } else {
            return plus((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public ComputableValue mult(final ComputableValue other) throws XPathException {
        return switch (other.getType()) {
            case Type.FLOAT -> new FloatValue(getExpression(), value * ((FloatValue) other).value);
            case Type.DOUBLE ->
                // type promotion - see https://www.w3.org/TR/xpath-31/#promotion
                    ((DoubleValue) convertTo(Type.DOUBLE)).mult(other);
            case Type.DAY_TIME_DURATION, Type.YEAR_MONTH_DURATION -> other.mult(this);
            default -> mult((ComputableValue) other.convertTo(getType()));
        };
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#div(org.exist.xquery.value.NumericValue)
     */
    public ComputableValue div(ComputableValue other) throws XPathException {
        if (Type.subTypeOfUnion(other.getType(), Type.NUMERIC)) {
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
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), value / ((FloatValue) other).value);
        } else {
            return div((ComputableValue) other.convertTo(getType()));
        }
    }

    @Override
    public IntegerValue idiv(final NumericValue other) throws XPathException {
        if (other.getType() == Type.DOUBLE) {
            // type promotion - see https://www.w3.org/TR/xpath-31/#promotion
            return ((DoubleValue) convertTo(Type.DOUBLE)).idiv(other);
        } else if (other.getType() == Type.DECIMAL) {
            return idiv((FloatValue) other.convertTo(Type.FLOAT));
        } else {
            final ComputableValue result = div(other);
            return new IntegerValue(getExpression(), ((IntegerValue) result.convertTo(Type.INTEGER)).getLong());
        }
    }

    @Override
    public NumericValue mod(final NumericValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), value % ((FloatValue) other).value);
        } else if (other.getType() == Type.DOUBLE) {
            // type promotion - see https://www.w3.org/TR/xpath-31/#promotion
            return ((DoubleValue) convertTo(Type.DOUBLE)).mod(other);
        } else {
            return mod((NumericValue) other.convertTo(getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#abs()
     */
    public NumericValue abs() throws XPathException {
        return new FloatValue(getExpression(), Math.abs(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#max(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), Math.max(value, ((FloatValue) other).value));
        } else {
            return convertTo(other.getType()).max(collator, other);
        }
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(getExpression(), Math.min(value, ((FloatValue) other).value));
        } else {
            return convertTo(other.getType()).min(collator, other);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
     */
    public int conversionPreference(Class<?> javaClass) {
        if (javaClass.isAssignableFrom(FloatValue.class)) {
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
            return 2;
        }
        if (javaClass == Float.class || javaClass == float.class) {
            return 1;
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

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
     */
    @Override
    public <T> T toJavaObject(final Class<T> target) throws XPathException {
        if (target.isAssignableFrom(FloatValue.class)) {
            return (T) this;
        } else if (target == Double.class || target == double.class) {
            return (T) Double.valueOf(value);
        } else if (target == Float.class || target == float.class) {
            return (T) Float.valueOf(value);
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
        } else if (target == byte[].class) {
            final ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf.array();
        } else if (target == ByteBuffer.class) {
            final ByteBuffer buf = ByteBuffer.allocate(SERIALIZED_SIZE);
            serialize(buf);
            return (T) buf;
        } else if (target == String.class) {
            return (T) getStringValue();
        } else if (target == Boolean.class) {
            return (T) Boolean.valueOf(effectiveBooleanValue());
        }

        throw new XPathException(getExpression(), 
                "cannot convert value of type "
                        + Type.getTypeName(getType())
                        + " to Java object of type "
                        + target.getName());
    }

    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue) o;
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return Float.compare(value, ((FloatValue) other).value);
        } else {
            return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;
        }
    }

    @Override
    public int hashCode() {
        return Float.valueOf(value).hashCode();
    }

    /**
     * Serializes to a ByteBuffer.
     *
     * 4 bytes.
     *
     * @param buf the ByteBuffer to serialize to.
     */
    public void serialize(final ByteBuffer buf) {
        final int fBits = Float.floatToIntBits(value) ^ 0x80000000;
        ByteConversion.intToByteH(fBits, buf);
    }

    public static FloatValue deserialize(final ByteBuffer buf) {
        final int fBits = ByteConversion.byteToIntH(buf) ^ 0x80000000;
        final float f = Float.intBitsToFloat(fBits);
        return new FloatValue(f);
    }
}
