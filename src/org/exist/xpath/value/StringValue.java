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

import org.exist.xpath.Constants;
import org.exist.xpath.XPathException;

public class StringValue extends AtomicValue {

	private String value;
	
	public StringValue(String stringValue) {
		value = stringValue;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.STRING;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.Item#getStringValue()
	 */
	public String getStringValue() {
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
			case Type.STRING:
				return this;
			case Type.BOOLEAN:
				return new BooleanValue(value);
			case Type.FLOAT:
			case Type.DOUBLE:
			case Type.DECIMAL:
			case Type.NUMBER:
				return new DoubleValue(value);
			case Type.INTEGER:
				return new IntegerValue(value);
			default:
				throw new XPathException("cannot convert string '" + value + "' to " + requiredType);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(int, org.exist.xpath.value.AtomicValue)
	 */
	public boolean compareTo(int operator, AtomicValue other) throws XPathException {
		if(Type.subTypeOf(other.getType(), Type.STRING)) {
			int cmp = value.compareTo(other.getStringValue());
			switch(operator) {
				case Constants.EQ:
					return cmp == 0;
				case Constants.NEQ:
					return cmp != 0;
				case Constants.LT:
					return cmp < 0;
				case Constants.LTEQ:
					return cmp <= 0;
				case Constants.GT:
					return cmp > 0;
				case Constants.GTEQ:
					return cmp >= 0;
				default:
					throw new XPathException("Type error: cannot apply operand to string value");
			}
		}
		throw new XPathException("Type error: operands are not comparable");
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#compareTo(org.exist.xpath.value.AtomicValue)
	 */
	public int compareTo(AtomicValue other) throws XPathException {
		return value.compareTo(other.getStringValue());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#effectiveBooleanValue()
	 */
	public boolean effectiveBooleanValue() throws XPathException {
		return value.length() > 0;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return value;
	}
}
