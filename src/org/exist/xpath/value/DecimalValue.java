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

public class DecimalValue extends NumericValue {

	private double value;
	
	public DecimalValue(double value) {
		this.value = value;
	}
	
	public DecimalValue(String stringValue) throws XPathException {
		try {
			value = Double.parseDouble(stringValue);
		} catch(NumberFormatException e) {
			throw new XPathException("cannot convert string '" + stringValue + "' into a decimal");
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.DECIMAL;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
		return Double.toString(value);
	}

	public double getValue() {
		return value;
	}
	
	public Item itemAt(int pos) {
		return pos == 0 ? this : null;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#convertTo(int)
	 */
	public AtomicValue convertTo(int requiredType) throws XPathException {
		switch(requiredType) {
			case Type.ATOMIC:
			case Type.ITEM:
			case Type.NUMBER:
			case Type.DECIMAL:
				return this;
			case Type.STRING:
				return new StringValue(getStringValue());
			case Type.INTEGER:
				return new IntegerValue((long)value);
			case Type.BOOLEAN:
				return new BooleanValue(value != 0.0);
			default:
				throw new XPathException("cannot convert decimal value '" + value + "' into " + requiredType);
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getDouble()
	 */
	public double getDouble() throws XPathException {
		return value;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getInt()
	 */
	public int getInt() throws XPathException {
		return (int)value;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.NumericValue#getLong()
	 */
	public long getLong() throws XPathException {
		return (long)value;
	}
	
	public void setValue(double val) {
		value = val;
	}
}
