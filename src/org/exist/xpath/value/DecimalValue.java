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
	
	public DecimalValue(String str) {
		value = new BigDecimal(str);
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
		switch(requiredType) {
			case Type.ATOMIC:
			case Type.ITEM:
			case Type.NUMBER:
			case Type.DECIMAL:
				return this;
			case Type.DOUBLE:
				return new DoubleValue(value.doubleValue());
			case Type.FLOAT:
				return new FloatValue(value.floatValue());
			case Type.STRING:
				return new StringValue(getStringValue());
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
				return new IntegerValue(value.longValue(), requiredType);
			case Type.BOOLEAN:
				return value.signum() == 0 ? BooleanValue.FALSE : BooleanValue.TRUE;
			default:
				throw new XPathException("cannot convert double value '" + value + "' into " + 
						Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#negate()
	 */
	public NumericValue negate() {
		return new DecimalValue(value.negate());
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_CEILING));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#floor()
	 */
	public NumericValue floor() {
		return new DecimalValue(value.setScale(0, BigDecimal.ROUND_FLOOR));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#round()
	 */
	public NumericValue round() {
		switch(value.signum()) {
			case -1:
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_DOWN));
			case 0:
				return this;
			case 1:
				return new DecimalValue(value.setScale(0, BigDecimal.ROUND_HALF_UP));
			default:
				return this;
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue minus(NumericValue other) throws XPathException {
		if(other instanceof DecimalValue)
			return new DecimalValue(value.subtract(((DecimalValue)other).value));
		else
			return ((DecimalValue)convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue plus(NumericValue other) throws XPathException {
		if(other instanceof DecimalValue)
			return new DecimalValue(value.add(((DecimalValue)other).value));
		else
			return ((DecimalValue)convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mult(NumericValue other) throws XPathException {
		if(other instanceof DecimalValue)
			return new DecimalValue(value.multiply(((DecimalValue)other).value));
		else
			return ((DecimalValue)convertTo(other.getType())).mult(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#div(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue div(NumericValue other) throws XPathException {
		if(other instanceof DecimalValue)
			return new DecimalValue(value.divide(((DecimalValue)other).value, BigDecimal.ROUND_DOWN));
		else
			return ((DecimalValue)convertTo(other.getType())).div(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mod(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if(other instanceof DecimalValue) {
			BigDecimal quotient = value.divide(((DecimalValue)other).value, BigDecimal.ROUND_DOWN);
			BigDecimal remainder = value.subtract(quotient.multiply(((DecimalValue)other).value));
			return new DecimalValue(remainder);
		} else
			return ((DecimalValue)convertTo(other.getType())).mod(other);
	}

}
