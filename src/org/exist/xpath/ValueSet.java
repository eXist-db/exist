
/* eXist Native XML Database
 * Copyright (C) 2000-03,  Wolfgang Meier (meier@ifs.tu-darmstadt.de)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.xpath;

import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import java.util.ArrayList;
import java.util.Iterator;
import org.exist.*;
import org.exist.dom.*;

public class ValueSet extends Value {

	protected ArrayList list = new ArrayList();

	public ValueSet() {
		super(Value.isValueSet);
	}

	public ValueSet(Value value) {
		super(Value.isValueSet);
		list.add(value);
	}

	public int getType() {
		return type;
	}

	public void add(Value value) {
		if (value.getType() == Value.isValueSet) {
			for (Iterator i = ((ValueSet) value).iterator(); i.hasNext();)
				list.add(i.next());
		} else
			list.add(value);
	}

	public Value get(int pos) {
		return (Value) list.get(pos);
	}

	public int getLength() {
		return list.size();
	}

	public NodeList getNodeList() {
		if (list.size() < 1)
			return new ArraySet(1);
		return ((Value) list.get(0)).getNodeList();
	}

	public String getStringValue() {
		if (list.size() < 1)
			return "";
		return ((Value) list.get(0)).getStringValue();
	}

	public double getNumericValue() {
		if (list.size() < 1)
			return Double.NaN;
		return ((Value) list.get(0)).getNumericValue();
	}

	public boolean getBooleanValue() {
		if (list.size() < 1)
			return false;
		return ((Value) list.get(0)).getBooleanValue();
	}

	public ValueSet getValueSet() {
		return this;
	}

	public Iterator iterator() {
		return new ValueSetIterator();
	}

	public class ValueSetIterator implements Iterator {

		protected int pos = 0;

		public boolean hasNext() {
			return (pos < list.size()) ? true : false;
		}

		public Object next() {
			return hasNext() ? list.get(pos++) : null;
		}

		public void remove() {
		}
	}
}
