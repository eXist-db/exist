/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Library General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Library General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.xpath;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public class FunEndsWith extends Function {

	protected Expression arg1, arg2;

	public FunEndsWith(BrokerPool pool, Expression arg1, Expression arg2) {
		super(pool, "ends-with");
		this.arg1 = arg1;
		this.arg2 = arg2;
	}
	
	public int returnsType() {
		return Constants.TYPE_BOOL;
	}
	
	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		ArraySet set = new ArraySet(1);
		set.add(node);
		DocumentSet dset = new DocumentSet();
		dset.add(node.doc);
		String s1 = arg1.eval(dset, set, node).getStringValue();
		String s2 = arg2.eval(dset, set, node).getStringValue();
		if(s1.endsWith(s2))
			return new ValueBoolean(true);
		else
			return new ValueBoolean(false);
	}
	
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("ends-with(");
		buf.append(arg1.pprint());
		buf.append(", ");
		buf.append(arg2.pprint());
		buf.append(")");
		return buf.toString();
	}
}
