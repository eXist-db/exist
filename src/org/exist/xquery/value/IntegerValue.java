/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.value;

import java.math.BigDecimal;

import org.exist.xquery.XPathException;

public class IntegerValue extends NumericValue {

	public final static IntegerValue ZERO = new IntegerValue(0);

	private long value;
	private int type = Type.INTEGER;

	public IntegerValue(long value) {
		this.value = value;
	}

	public IntegerValue(long value, int type) throws XPathException {
		this(value);
		this.type = type;
		if (!checkType(value, type))
			throw new XPathException(
				"Value is not a valid integer for type " + Type.getTypeName(type));
	}

	public IntegerValue(String stringValue) throws XPathException {
		try {
			value = Long.parseLong(stringValue);
		} catch (NumberFormatException e) {
//			try {
//				value = (long) Double.parseDouble(stringValue);
//			} catch (NumberFormatException e1) {
				throw new XPathException(
					"failed to convert '" + stringValue + "' to an integer: " + e.getMessage(), e);
//			}
		}
	}

	public IntegerValue(String stringValue, int requiredType) throws XPathException {
		this.type = requiredType;
		try {
			value = Long.parseLong(stringValue);
		} catch (NumberFormatException e) {
//			try {
//				value = (long) Double.parseDouble(stringValue);
//			} catch (NumberFormatException e1) {
				throw new XPathException(
					"failed to convert '" + stringValue + "' to an integer: " + e.getMessage());
//			}
		}
		checkType(value, type);
	}

	private final static boolean checkType(long value, int type) throws XPathException {
		switch (type) {
			case Type.LONG :
			case Type.INTEGER :
			case Type.DECIMAL :
				return true;
			case Type.NON_POSITIVE_INTEGER :
				return value < 1;
			case Type.NEGATIVE_INTEGER :
				return value < 0;
			case Type.INT :
				return value >= -4294967295L && value <= 4294967295L;
			case Type.SHORT :
				return value >= -65535 && value <= 65535;
			case Type.BYTE :
				return value >= -255 && value <= 255;
			case Type.NON_NEGATIVE_INTEGER :
				return value > -1;
			case Type.UNSIGNED_LONG :
				return value > -1;
			case Type.UNSIGNED_INT:
				return value > -1 && value <= 4294967295L;
			case Type.UNSIGNED_SHORT :
				return value > -1 && value <= 65535;
			case Type.UNSIGNED_BYTE :
				return value > -1 && value <= 255;
			case Type.POSITIVE_INTEGER :
				return value >= 0;
		}
		throw new XPathException("Unknown type: " + Type.getTypeName(type));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return type;
	}

	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}

	public long getValue() {
		return value;
	}

	public void setValue(long value) {
		this.value = value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return Long.toString(value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.NUMBER :
			case Type.INTEGER :
			case Type.LONG :
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
			case Type.DECIMAL :
				return new DecimalValue(new BigDecimal(value));
			case Type.NON_POSITIVE_INTEGER :
			case Type.NEGATIVE_INTEGER :
			case Type.INT :
			case Type.SHORT :
			case Type.BYTE :
			case Type.NON_NEGATIVE_INTEGER :
			case Type.UNSIGNED_LONG :
			case Type.UNSIGNED_INT :
			case Type.UNSIGNED_SHORT :
			case Type.UNSIGNED_BYTE :
			case Type.POSITIVE_INTEGER :
				return new IntegerValue(value, requiredType);
			case Type.DOUBLE :
				return new DoubleValue(value);
			case Type.STRING :
				return new StringValue(getStringValue());
			case Type.BOOLEAN :
				return (value == 0) ? BooleanValue.FALSE : BooleanValue.TRUE;
			default :
				throw new XPathException(
					"cannot convert integer '" + value + "' to " + requiredType);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getInt()
	 */
	public int getInt() throws XPathException {
		return (int) value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getLong()
	 */
	public long getLong() throws XPathException {
		return value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getDouble()
	 */
	public double getDouble() throws XPathException {
		return (double) value;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return value != 0;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#floor()
	 */
	public NumericValue floor() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#round()
	 */
	public NumericValue round() throws XPathException {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue(value - ((IntegerValue) other).value, type);
		else
			return ((ComputableValue) convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue(value + ((IntegerValue) other).value, type);
		else
			return ((ComputableValue) convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue(value * ((IntegerValue) other).value, type);
		else
			return ((ComputableValue) convertTo(other.getType())).mult(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#div(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		if (other instanceof IntegerValue) {
			if(((IntegerValue) other).value == 0)
				throw new XPathException("division by zero");
			double od = ((IntegerValue) other).value;
			double d = value;
			return new DecimalValue(d / od);
		} else
			return ((ComputableValue) convertTo(other.getType())).div(other);
	}

	public NumericValue idiv(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
			long ov = ((IntegerValue) other).value;
			if (ov == 0)
				throw new XPathException("division by zero");
			return new IntegerValue(value / ov, type);
		} else
			return ((IntegerValue) convertTo(Type.INTEGER)).idiv(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mod(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER)) {
			long ov = ((IntegerValue) other).value;
			if (ov == 0)
				throw new XPathException("division by zero");
			return new IntegerValue(value % ov, type);
		} else
			return ((NumericValue) convertTo(other.getType())).mod(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#unaryMinus()
	 */
	public NumericValue negate() throws XPathException {
		return new IntegerValue(-value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#abs()
	 */
	public NumericValue abs() throws XPathException {
		return new IntegerValue(Math.abs(value), type);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue(Math.max(value, ((IntegerValue) other).value));
		else
			return new IntegerValue(
				Math.max(value, ((IntegerValue) other.convertTo(type)).value), type);
	}

	public AtomicValue min(AtomicValue other) throws XPathException {
		if (Type.subTypeOf(other.getType(), Type.INTEGER))
			return new IntegerValue(Math.min(value, ((IntegerValue) other).value));
		else
			return new IntegerValue(
				Math.min(value, ((IntegerValue) other.convertTo(type)).value), type);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#conversionPreference(java.lang.Class)
	 */
	public int conversionPreference(Class javaClass) {
		if(javaClass.isAssignableFrom(IntegerValue.class)) return 0;
		if(javaClass == Long.class || javaClass == long.class) return 1;
		if(javaClass == Integer.class || javaClass == int.class) return 2;
		if(javaClass == Short.class || javaClass == short.class) return 3;
		if(javaClass == Byte.class || javaClass == byte.class) return 4;
		if(javaClass == Double.class || javaClass == double.class) return 5;
		if(javaClass == Float.class || javaClass == float.class) return 6;
		if(javaClass == String.class) return 7;
		if(javaClass == Boolean.class || javaClass == boolean.class) return 8;
		if(javaClass == Object.class) return 20;
		
		return Integer.MAX_VALUE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#toJavaObject(java.lang.Class)
	 */
	public Object toJavaObject(Class target) throws XPathException {
		if(target.isAssignableFrom(IntegerValue.class)) 
			return this;
		else if(target == Long.class || target == long.class)
			return new Long(value);
		else if(target == Integer.class || target == int.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.INT);
			return new Integer((int)v.value);
		} else if(target == Short.class || target == short.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.SHORT);
			return new Short((short)v.value);
		} else if(target == Byte.class || target == byte.class) {
			IntegerValue v = (IntegerValue)convertTo(Type.BYTE);
			return new Byte((byte)v.value);
		} else if(target == Double.class || target == double.class) {
			DoubleValue v = (DoubleValue)convertTo(Type.DOUBLE);
			return new Double(v.getValue());
		} else if(target == Float.class || target == float.class) {
			FloatValue v = (FloatValue)convertTo(Type.FLOAT);
			return new Float(v.value);
		} else if(target == Boolean.class || target == boolean.class)
			return new BooleanValue(effectiveBooleanValue());
		else if(target == String.class)
			return Long.toString(value);
		else if(target == Object.class)
			return new Long(value);
		
		throw new XPathException("cannot convert value of type " + Type.getTypeName(getType()) +
			" to Java object of type " + target.getName());
	}
}
