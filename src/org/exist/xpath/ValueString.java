
/* eXist Native XML Database
 * Copyright (C) 2001-03,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.exist.xpath;

import org.exist.dom.ArraySet;
import org.w3c.dom.NodeList;

public class ValueString extends Value {

	protected String stringValue = null;

	public ValueString(String value) {
		super(Value.isString);
		this.stringValue = value;
	}

	public NodeList getNodeList() {
		return new ArraySet(1);
	}

	public String getStringValue() {
		return stringValue;
	}

	public double getNumericValue() {
		try {
			return Double.parseDouble(stringValue);
		} catch(NumberFormatException f) {
			return Double.NaN;
		}
	}

	public boolean getBooleanValue() {
		return (stringValue.length() > 0);
	}
}
