
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import org.w3c.dom.NodeList;

public abstract class Value {

	public final static int isNodeList = 0;
	public final static int isString = 1;
	public final static int isNumber = 2;
	public final static int isBoolean = 3;
	public final static int isValueSet = 4;
	
	protected int type = -1;
   
	public Value(int type) {
		this.type = type;
	}

	public int getType() {
		return type;
	} 

	public abstract NodeList getNodeList();

	public abstract String getStringValue();
	
	public String getStringValueConcat() {
		return getStringValue();
	}
	
	public abstract double getNumericValue();
	
	public abstract boolean getBooleanValue();

	public int getLength() { return 0; }

	public Value get(int pos) { return this; }

	public ValueSet getValueSet() {
		return new ValueSet(this);
	}
}
