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
	
	public String toString() {
		return Type.getTypeName(primaryType) + Cardinality.display(cardinality);
	}
}
