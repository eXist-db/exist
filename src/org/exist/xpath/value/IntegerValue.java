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
package org.exist.xpath.value;

import org.exist.xpath.XPathException;

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
			throw new XPathException(
				"failed to convert '" + stringValue + "' to an integer");
		}
	}

	public IntegerValue(String stringValue, int requiredType) throws XPathException {
		this.type = requiredType;
		try {
			value = Long.parseLong(stringValue);
		} catch (NumberFormatException e) {
			throw new XPathException(
				"failed to convert '" + stringValue + "' to an integer");
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
				return value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE;
			case Type.SHORT :
				return value >= Short.MIN_VALUE && value <= Short.MAX_VALUE;
			case Type.BYTE :
				return value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE;
			case Type.NON_NEGATIVE_INTEGER :
				return value > -1;
			case Type.UNSIGNED_LONG :
				return value > -1;
			case Type.UNSIGNED_INT :
				return value > -1 && value <= Integer.MAX_VALUE;
			case Type.UNSIGNED_SHORT :
				return value > -1 && value <= Short.MAX_VALUE;
			case Type.UNSIGNED_BYTE :
				return value > -1 && value <= Byte.MAX_VALUE;
			case Type.POSITIVE_INTEGER :
				return value > 0;
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
			case Type.DECIMAL :
			case Type.NUMBER :
			case Type.INTEGER :
			case Type.LONG :
			case Type.ATOMIC :
			case Type.ITEM :
				return this;
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
	public NumericValue ceiling() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#floor()
	 */
	public NumericValue floor() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#round()
	 */
	public NumericValue round() {
		return this;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue minus(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue)
			return new IntegerValue(value - ((IntegerValue) other).value);
		else
			return ((NumericValue) convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue plus(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue)
			return new IntegerValue(value + ((IntegerValue) other).value);
		else
			return ((NumericValue) convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mult(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue)
			return new IntegerValue(value * ((IntegerValue) other).value);
		else
			return ((NumericValue) convertTo(other.getType())).mult(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#div(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue div(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue) {
			long ov = ((IntegerValue) other).value;
			if (ov == 0)
				throw new XPathException("division by zero");
			return new DoubleValue(value / ov);
		} else
			return ((NumericValue) convertTo(other.getType())).div(other);
	}

	public NumericValue idiv(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue) {
			long ov = ((IntegerValue) other).value;
			if (ov == 0)
				throw new XPathException("division by zero");
			return new DoubleValue(value / ov);
		} else
			return ((IntegerValue) convertTo(Type.INTEGER)).idiv(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mod(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (other instanceof IntegerValue) {
			long ov = ((IntegerValue) other).value;
			if (ov == 0)
				throw new XPathException("division by zero");
			return new DoubleValue(value % ov);
		} else
			return ((NumericValue) convertTo(other.getType())).mod(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#unaryMinus()
	 */
	public NumericValue negate() {
		return new IntegerValue(-value);
	}
}
