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

/**
 * @author wolf
 */
public class FloatValue extends NumericValue {
    // m × 2^e, where m is an integer whose absolute value is less than 2^24, 
    // and e is an integer between -149 and 104, inclusive.
    // In addition also -INF, +INF and NaN.
    public final static FloatValue NaN = new FloatValue(Float.NaN);
    public final static FloatValue POSITIVE_INFINITY = new FloatValue(Float.POSITIVE_INFINITY);
    public final static FloatValue NEGATIVE_INFINITY = new FloatValue(Float.NEGATIVE_INFINITY);
    public final static FloatValue ZERO = new FloatValue(0.0E0f);

    protected float value;

    public FloatValue(float value) {
        this.value = value;
    }

    public FloatValue(String stringValue) throws XPathException {
        try {
            if ("INF".equals(stringValue)) {
                value = Float.POSITIVE_INFINITY;
            } else if ("-INF".equals(stringValue)) {
                value = Float.NEGATIVE_INFINITY;
            } else if ("NaN".equals(stringValue)) {
                value = Float.NaN;
            } else {
                value = Float.parseFloat(stringValue);
            }
        } catch (final NumberFormatException e) {
            throw new XPathException(ErrorCodes.FORG0001, "cannot construct "
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
        FloatingPointConverter.appendFloat(sb, value).getNormalizedString(0);
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

    public boolean hasFractionalPart() {
        if (isNaN()) {
            return false;
        }
        if (isInfinite()) {
            return false;
        }
        return new DecimalValue(new BigDecimal(value)).hasFractionalPart();
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.Sequence#convertTo(int)
     */
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.ATOMIC:
            case Type.ITEM:
            case Type.NUMBER:
            case Type.FLOAT:
                return this;
            case Type.DOUBLE:
                return new DoubleValue(value);
            case Type.STRING:
                return new StringValue(getStringValue());
            case Type.DECIMAL:
                return new DecimalValue(value);
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
                    return new IntegerValue((long) value, requiredType);
                } else {
                    throw new XPathException(ErrorCodes.FOCA0002, "cannot convert ' xs:float(\""
                            + getStringValue()
                            + "\")' to "
                            + Type.getTypeName(requiredType));
                }
            case Type.BOOLEAN:
                return (value == 0.0f || Float.isNaN(value))
                        ? BooleanValue.FALSE
                        : BooleanValue.TRUE;
            case Type.UNTYPED_ATOMIC:
                return new UntypedAtomicValue(getStringValue());
            default:
                throw new XPathException(ErrorCodes.FORG0001, "cannot cast '"
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
        return new FloatValue(-value);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#ceiling()
     */
    public NumericValue ceiling() throws XPathException {
        return new FloatValue((float) Math.ceil(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#floor()
     */
    public NumericValue floor() throws XPathException {
        return new FloatValue((float) Math.floor(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#round()
     */
    public NumericValue round() throws XPathException {

        if (Float.isNaN(value) || Float.isInfinite(value) || value == 0.0) {
            return this;
        }

        if (value >= -0.5 && value < 0.0) {
            return new DoubleValue(-0.0);
        }

        if (value > Integer.MIN_VALUE && value < Integer.MAX_VALUE) {
            return new FloatValue((float) Math.round(value));
        }

        //too big return original value unchanged
        return this;
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
     */
    public NumericValue round(IntegerValue precision) throws XPathException {
        if (precision == null) {
            return round();
        }

        if (Float.isNaN(value) || Float.isInfinite(value) || value == 0.0) {
            return this;
        }
		
		/* use the decimal rounding method */
        return (FloatValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.FLOAT);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#minus(org.exist.xquery.value.NumericValue)
     */
    public ComputableValue minus(ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(value - ((FloatValue) other).value);
        } else {
            return minus((ComputableValue) other.convertTo(getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#plus(org.exist.xquery.value.NumericValue)
     */
    public ComputableValue plus(ComputableValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(value + ((FloatValue) other).value);
        } else {
            return plus((ComputableValue) other.convertTo(getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#mult(org.exist.xquery.value.NumericValue)
     */
    public ComputableValue mult(ComputableValue other) throws XPathException {
        switch (other.getType()) {
            case Type.FLOAT:
                return new FloatValue(value * ((FloatValue) other).value);
            case Type.DAY_TIME_DURATION:
            case Type.YEAR_MONTH_DURATION:
                return other.mult(this);
            default:
                return mult((ComputableValue) other.convertTo(getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#div(org.exist.xquery.value.NumericValue)
     */
    public ComputableValue div(ComputableValue other) throws XPathException {
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
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(value / ((FloatValue) other).value);
        } else {
            return div((ComputableValue) other.convertTo(getType()));
        }
    }

    public IntegerValue idiv(NumericValue other) throws XPathException {
        final ComputableValue result = div(other);
        return new IntegerValue(((IntegerValue) result.convertTo(Type.INTEGER)).getLong());
		/*
		if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
			float result = value / ((FloatValue) other).value;
			if (result == Float.NaN || result == Float.POSITIVE_INFINITY || result == Float.NEGATIVE_INFINITY)
				throw new XPathException("illegal arguments to idiv");
			return new IntegerValue(new BigDecimal(result).toBigInteger(), Type.INTEGER);
		}
		throw new XPathException("idiv called with incompatible argument type: " + getType() + " vs " + other.getType());
		*/
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
     */
    public NumericValue mod(NumericValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(value % ((FloatValue) other).value);
        } else {
            return mod((NumericValue) other.convertTo(getType()));
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#abs()
     */
    public NumericValue abs() throws XPathException {
        return new FloatValue(Math.abs(value));
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.value.NumericValue#max(org.exist.xquery.value.AtomicValue)
     */
    public AtomicValue max(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(Math.max(value, ((FloatValue) other).value));
        } else {
            return convertTo(other.getType()).max(collator, other);
        }
    }

    public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
        if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
            return new FloatValue(Math.min(value, ((FloatValue) other).value));
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
}
