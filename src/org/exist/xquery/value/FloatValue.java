/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */

package org.exist.xquery.value;

import java.math.BigDecimal;
import java.text.Collator;
import java.text.NumberFormat;

import org.exist.storage.Indexable;
import org.exist.util.ByteConversion;
import org.exist.xquery.Constants;
import org.exist.xquery.XPathException;

/**
 * @author wolf
 */
public class FloatValue extends NumericValue implements Indexable {

	public final static FloatValue NaN = new FloatValue(Float.NaN);
	public final static FloatValue ZERO = new FloatValue(0.0E0f);

	protected float value;

	public FloatValue(float value) {
		this.value = value;
	}

	public FloatValue(String stringValue) throws XPathException {
		try {
			if (stringValue.equals("INF"))
				value = Float.POSITIVE_INFINITY;
			else if (stringValue.equals("-INF"))
				value = Float.NEGATIVE_INFINITY;
			else
				value = Float.parseFloat(stringValue);
		} catch (NumberFormatException e) {
			throw new XPathException(
				"cannot convert string '" + stringValue + "' into a float");
		}
	}

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
		if (value == Float.POSITIVE_INFINITY)
			return "INF"; ;
		if (value == Float.NEGATIVE_INFINITY)
			return "-INF";		
		String s = String.valueOf(value);
		if (s.endsWith(".0"))
			return s.substring(0, s.length() - 2);
		return s;	
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		if (isNaN())
			return false;
		return value != 0.0f;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#isNaN()
	 */
	public boolean isNaN() {
		return value == Float.NaN;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.NUMBER :
			case Type.FLOAT :
				return this;
			case Type.DOUBLE :
				return new DoubleValue(value);
			case Type.STRING :
				return new StringValue(getStringValue());
			case Type.DECIMAL :
				return new DecimalValue(value);
			case Type.INTEGER :
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
			case Type.LONG :
			case Type.INT :
			case Type.SHORT :
			case Type.BYTE :
			case Type.NON_NEGATIVE_INTEGER :
			case Type.UNSIGNED_LONG :
			case Type.UNSIGNED_INT :
			case Type.UNSIGNED_SHORT :
			case Type.UNSIGNED_BYTE :
			case Type.POSITIVE_INTEGER :
				return new IntegerValue((long) value, requiredType);
			case Type.BOOLEAN :
				return (value == 0.0f || value == Float.NaN)
					? BooleanValue.FALSE
					: BooleanValue.TRUE;
			default :
				throw new XPathException(
					"cannot convert double value '"
						+ value
						+ "' into "
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
		return new FloatValue((float) Math.round(value));
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#round(org.exist.xquery.value.IntegerValue)
	 */
	public NumericValue round(IntegerValue precision) throws XPathException {
		/* use the decimal rounding method */
		return (FloatValue) ((DecimalValue) convertTo(Type.DECIMAL)).round(precision).convertTo(Type.FLOAT);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#minus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(value - ((FloatValue) other).value);
		else
			return minus((ComputableValue) other.convertTo(getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#plus(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(value + ((FloatValue) other).value);
		else
			return plus((ComputableValue) other.convertTo(getType()));
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mult(org.exist.xquery.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		switch(other.getType()) {
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
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(value / ((FloatValue) other).value);
		else
			return div((ComputableValue) other.convertTo(getType()));
	}

	public IntegerValue idiv(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.FLOAT)) {
			float result = value / ((FloatValue) other).value;
			if (result == Float.NaN || result == Float.POSITIVE_INFINITY || result == Float.NEGATIVE_INFINITY)
				throw new XPathException("illegal arguments to idiv");
			return new IntegerValue(new BigDecimal(result).toBigInteger(), Type.INTEGER);
		}
		throw new XPathException("idiv called with incompatible argument type: " + getType() + " vs " + other.getType());
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.NumericValue#mod(org.exist.xquery.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(value % ((FloatValue) other).value);
		else
			return mod((NumericValue) other.convertTo(getType()));
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
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(Math.max(value, ((FloatValue) other).value));
		else
			return ((FloatValue) convertTo(other.getType())).max(collator, other);
	}

	public AtomicValue min(Collator collator, AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.FLOAT))
			return new FloatValue(Math.min(value, ((FloatValue) other).value));
		else
			return ((FloatValue) convertTo(other.getType())).min(collator, other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if (javaClass.isAssignableFrom(FloatValue.class))
			return 0;
		if (javaClass == Long.class || javaClass == long.class)
			return 3;
		if (javaClass == Integer.class || javaClass == int.class)
			return 4;
		if (javaClass == Short.class || javaClass == short.class)
			return 5;
		if (javaClass == Byte.class || javaClass == byte.class)
			return 6;
		if (javaClass == Double.class || javaClass == double.class)
			return 2;
		if (javaClass == Float.class || javaClass == float.class)
			return 1;
		if (javaClass == String.class)
			return 7;
		if (javaClass == Boolean.class || javaClass == boolean.class)
			return 8;
		if (javaClass == Object.class)
			return 20;

		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if (target.isAssignableFrom(FloatValue.class))
			return this;
		else if (target == Double.class || target == double.class)
			return new Double(value);
		else if (target == Float.class || target == float.class)
			return new Float(value);
		else if (target == Long.class || target == long.class) {
			return new Long( ((IntegerValue) convertTo(Type.LONG)).getValue() );
		} else if (target == Integer.class || target == int.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.INT);
			return new Integer((int) v.getValue());
		} else if (target == Short.class || target == short.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.SHORT);
			return new Short((short) v.getValue());
		} else if (target == Byte.class || target == byte.class) {
			IntegerValue v = (IntegerValue) convertTo(Type.BYTE);
			return new Byte((byte) v.getValue());
		} else if (target == String.class)
			return getStringValue();
		else if (target == Boolean.class)
			return Boolean.valueOf(effectiveBooleanValue());

		throw new XPathException(
			"cannot convert value of type "
				+ Type.getTypeName(getType())
				+ " to Java object of type "
				+ target.getName());
	}

	/** @deprecated
     * @see org.exist.storage.Indexable#serialize(short)
     */
    public byte[] serialize(short collectionId, boolean caseSensitive) {
        final byte[] data = new byte[7];
        ByteConversion.shortToByte(collectionId, data, 0);
        data[2] = (byte) Type.FLOAT;
        final int bits = (int)(Float.floatToIntBits(value) ^ 0x80000000);
        ByteConversion.intToByte(bits, data, 3);
        return data;
    }

    /** Serialize for the persistant storage */
    public byte[] serializeValue ( int offset, boolean caseSensitive) {
		final byte[] data = new byte[ offset + 1 + 4 ];
		data[offset] = (byte) Type.FLOAT;
        final int bits = (int)(Float.floatToIntBits(value) ^ 0x80000000);
        ByteConversion.intToByte(bits, data, offset+1 );
        return data;
    }
	
    /* (non-Javadoc)
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        final AtomicValue other = (AtomicValue)o;
        if(Type.subTypeOf(other.getType(), Type.FLOAT))
            return Float.compare(value, ((FloatValue)other).value);
        else
            return getType() < other.getType() ? Constants.INFERIOR : Constants.SUPERIOR;
    }
	
}
