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

public class IntegerValue extends AtomicValue {

	private long value;
	
	public IntegerValue(long value) {
		this.value = value;
	}
	
	public IntegerValue(String stringValue) throws XPathException {
		try {
			value = Long.parseLong(stringValue);
		} catch(NumberFormatException e) {
			throw new XPathException("failed to convert '" + stringValue + "' to an integer");
		}
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.value.AtomicValue#getType()
	 */
	public int getType() {
		return Type.INTEGER;
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
		switch(requiredType) {
			case Type.NUMBER:
			case Type.INTEGER:
			case Type.ATOMIC:
			case Type.ITEM:
				return this;
			case Type.DECIMAL:
				return new DecimalValue(value);
			case Type.STRING:
				return new StringValue(getStringValue());
			case Type.BOOLEAN:
				return new BooleanValue(value != 0);
			default:
				throw new XPathException("cannot convert integer '" + value + "' to " + requiredType);
		}
	}

}
