/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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

package org.exist.xpath.value;

import java.math.BigDecimal;

import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public class DecimalValue extends NumericValue {

	BigDecimal value;

	public DecimalValue(BigDecimal decimal) {
		this.value = decimal;
	}

	public DecimalValue(String str) throws XPathException {
		try {
			value = new BigDecimal(str);
		} catch(NumberFormatException e) {
			throw new XPathException("Type error: " + str + " cannot be cast to a decimal");
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DECIMAL;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		return value.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch (requiredType) {
			case Type.ATOMIC :
			case Type.ITEM :
			case Type.NUMBER :
			case Type.DECIMAL :
				return this;
			case Type.DOUBLE :
				return new DoubleValue(value.doubleValue());
			case Type.FLOAT :
				return new FloatValue(value.floatValue());
			case Type.STRING :
				return new StringValue(getStringValue());
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
				return new IntegerValue(value.longValue(), requiredType);
			case Type.BOOLEAN :
				return value.signum() == 0 ? BooleanValue.FALSE : BooleanValue.TRUE;
			default :
				throw new XPathException(
					"cannot convert double value '"
						+ value
						+ "' into "
						+ Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#negate()
	 */
	public NumericValue negate() throws XPathException {
		return new DecimalValue(value.negate());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() throws XPathException {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_CEILING));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#floor()
	 */
	public NumericValue floor() throws XPathException {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_FLOOR));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#round()
	 */
	public NumericValue round() throws XPathException {
		switch (value.signum()) {
			case -1 :
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_DOWN));
			case 0 :
				return this;
			case 1 :
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_UP));
			default :
				return this;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue minus(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL)
			return new DecimalValue(value.subtract(((DecimalValue) other).value));
		else
			return ((ComputableValue) convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue plus(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL)
			return new DecimalValue(value.add(((DecimalValue) other).value));
		else
			return ((ComputableValue) convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue mult(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL)
			return new DecimalValue(value.multiply(((DecimalValue) other).value));
		else
			return ((ComputableValue) convertTo(other.getType())).mult(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#div(org.exist.xpath.value.NumericValue)
	 */
	public ComputableValue div(ComputableValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL)
			return new DecimalValue(
				value.divide(((DecimalValue) other).value, BigDecimal.ROUND_DOWN));
		else
			return ((ComputableValue) convertTo(other.getType())).div(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mod(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			BigDecimal quotient =
				value.divide(((DecimalValue) other).value, BigDecimal.ROUND_DOWN);
			BigDecimal remainder =
				value.subtract(quotient.multiply(((DecimalValue) other).value));
			return new DecimalValue(remainder);
		} else
			return ((NumericValue) convertTo(other.getType())).mod(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#abs(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue abs() throws XPathException {
		return new DecimalValue(value.abs());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#max(org.exist.xpath.value.AtomicValue)
	 */
	public AtomicValue max(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			return new DecimalValue(value.max(((DecimalValue) other).value));
		} else
			return new DecimalValue(
				value.max(((DecimalValue) other.convertTo(Type.DECIMAL)).value));
	}

	public AtomicValue min(AtomicValue other) throws XPathException {
		if (other.getType() == Type.DECIMAL) {
			return new DecimalValue(value.min(((DecimalValue) other).value));
		} else {
			System.out.println(other.getClass().getName());
			return new DecimalValue(
				value.min(((DecimalValue) other.convertTo(Type.DECIMAL)).value));
		}
	}
}
