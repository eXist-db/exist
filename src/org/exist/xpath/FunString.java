/* eXist xml document repository and xpath implementation
 * Copyright (C) 2000-01,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * You should have received a copy of the GNU Library General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package org.exist.xpath;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

/**
 * xpath-library function: string(object)
 *
 */
public class FunString extends Function {

	public FunString(BrokerPool pool) {
		super(pool, "string");
	}
	
	public int returnsType() {
		return Constants.TYPE_STRING;
	}
	
	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		if(node != null) {
			context = new SingleNodeSet(node);
		}
		String result = getArgument(0).eval(docs, context, node).getStringValue();
		return new ValueString(result);
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("string(");
		buf.append(getArgument(0).pprint());
		buf.append(")");
		return buf.toString();
	}
}
