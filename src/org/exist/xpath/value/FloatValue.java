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

import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public class FloatValue extends NumericValue {

	public final static FloatValue NaN = new FloatValue(Float.NaN);
	public final static FloatValue ZERO = new FloatValue(0.0E0f);
	
	protected float value;
	
	public FloatValue(float value) {
		this.value = value;
	}
	
	public FloatValue(String stringValue) throws XPathException {
		try {
			value = Float.parseFloat(stringValue);
		} catch(NumberFormatException e) {
			throw new XPathException("cannot convert string '" + stringValue + "' into a float");
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#getStringValue()
	 */
	public String getStringValue() throws XPathException {
		return Float.toString(value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Sequence#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
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
				return new IntegerValue((long)value, requiredType);
			case Type.BOOLEAN:
				return (value == 0.0 && value == Double.NaN) ? BooleanValue.FALSE : 
															 BooleanValue.TRUE;
			default:
				throw new XPathException("cannot convert double value '" + value + "' into " + 
						Type.getTypeName(requiredType));
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#negate()
	 */
	public NumericValue negate() {
		return new FloatValue(-value);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#ceiling()
	 */
	public NumericValue ceiling() {
		return new FloatValue((float)Math.ceil(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#floor()
	 */
	public NumericValue floor() {
		return new FloatValue((float)Math.floor(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#round()
	 */
	public NumericValue round() {
		return new FloatValue((float)Math.round(value));
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#minus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue minus(NumericValue other) throws XPathException {
		if(other instanceof FloatValue)
			return new FloatValue(value - ((FloatValue)other).value);
		else
			return ((NumericValue)convertTo(other.getType())).minus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#plus(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue plus(NumericValue other) throws XPathException {
		if(other instanceof FloatValue)
			return new FloatValue(value + ((FloatValue)other).value);
		else
			return ((NumericValue)convertTo(other.getType())).plus(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mult(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mult(NumericValue other) throws XPathException {
		if(other instanceof FloatValue)
			return new FloatValue(value * ((FloatValue)other).value);
		else
			return ((NumericValue)convertTo(other.getType())).mult(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#div(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue div(NumericValue other) throws XPathException {
		if(other instanceof FloatValue)
			return new FloatValue(value / ((FloatValue)other).value);
		else
			return ((NumericValue)convertTo(other.getType())).div(other);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#mod(org.exist.xpath.value.NumericValue)
	 */
	public NumericValue mod(NumericValue other) throws XPathException {
		if(other instanceof FloatValue)
			return new FloatValue(value % ((FloatValue)other).value);
		else
			return ((NumericValue)convertTo(other.getType())).mod(other);
	}

}
