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

import org.exist.xpath.Cardinality;
import org.exist.xpath.XPathException;

/**
 * @author wolf
 */
public class SequenceType {

	private int primaryType = Type.ITEM;
	private int cardinality = Cardinality.EXACTLY_ONE;

	public SequenceType() {
	}

	public SequenceType(int primaryType, int cardinality) {
		this.primaryType = primaryType;
		this.cardinality = cardinality;
	}

	public int getPrimaryType() {
		return primaryType;
	}

	public void setPrimaryType(int type) {
		this.primaryType = type;
	}

	public int getCardinality() {
		return cardinality;
	}

	public void setCardinality(int cardinality) {
		this.cardinality = cardinality;
	}

	public void checkType(int type) throws XPathException {
		if (type == Type.EMPTY)
			return;
		if (!Type.subTypeOf(type, primaryType))
			throw new XPathException(
				"Type error: expected type: "
					+ Type.getTypeName(primaryType)
					+ "; got: "
					+ Type.getTypeName(type));
	}

	public void checkCardinality(Sequence seq) throws XPathException {
		int items = seq.getLength();
		if (items > 0 && cardinality == Cardinality.EMPTY)
			throw new XPathException("Empty sequence expected; got " + items);
		if (items == 0 && (cardinality & Cardinality.ZERO) == 0)
			throw new XPathException("Empty sequence is not allowed here");
		else if (items > 1 && (cardinality & Cardinality.MANY) == 0)
			throw new XPathException("Sequence with more than one item is not allowed here");
	}

	public String toString() {
		if (cardinality == Cardinality.EMPTY)
			return "empty()";
		return Type.getTypeName(primaryType) + Cardinality.display(cardinality);
	}

}
