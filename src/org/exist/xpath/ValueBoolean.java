
/* eXist xml document repository and xpath implementation
 * Copyright (C) 2001,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
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

import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.exist.*;
import org.exist.dom.*;

public class ValueBoolean extends Value {

	protected boolean booleanValue = false;

	public ValueBoolean(boolean value) {
		super(Value.isBoolean);
		booleanValue = value;
	}

	public NodeList getNodeList() {
		return new ArraySet(1);
	}

	public String getStringValue() {
		return (booleanValue) ? "true" : "false";
	}

	public double getNumericValue() {
		return (booleanValue) ? 1 : 0;
	}

	public boolean getBooleanValue() {
		return booleanValue;
	}
}
