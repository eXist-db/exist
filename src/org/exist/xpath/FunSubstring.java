/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
 * http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */

package org.exist.xpath;

import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * xpath-library function: string(object)
 *
 */
public class FunSubstring extends Function {

	protected Expression arg1, start, length = null;

	public FunSubstring(BrokerPool pool, Expression arg1, Expression start, Expression length) {
		super(pool, "substring");
		this.arg1 = arg1;
		if(start.returnsType() != Constants.TYPE_NUM)
			throw new RuntimeException("wrong argument type");
		if(length != null && length.returnsType() != Constants.TYPE_NUM)
			throw new RuntimeException("wrong argument type");
		this.start = start;
		this.length = length;
	}
	
	public FunSubstring(BrokerPool pool) {
		super(pool);
	}

	public int returnsType() {
		return Constants.TYPE_STRING;
	}
		
	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		arg1 = getArgument(0);
		start = getArgument(1);

		if(node != null) {
			context = new SingleNodeSet(node);
		}
		int s = (int)start.eval(docs, context, node).getNumericValue();
		int l = 0;
		if(length != null)
			l = (int)length.eval(docs, context, node).getNumericValue();
		Value nodes = arg1.eval(docs, context, node);
		if(nodes.getType()==Value.isNodeList) {
			NodeSet nset = (NodeSet)nodes.getNodeList();
			NodeProxy n;
			String temp;
			ValueSet result = new ValueSet();
			for(int i = 0; i < nset.getLength(); i++) {
				n = nset.get(i);
				temp = n.getNodeValue();
				if(s >= 0 && temp.length() > s + l)
					result.add(new ValueString((l > 0) ? temp.substring(s, l) : temp.substring(s)));
			}
			return result;
		} else {
			String result = nodes.getStringValue();
			if(s < 0 || s + l >= result.length())
				return new ValueString("");
			return new ValueString((l > 0) ? result.substring(s, l) : result.substring(s));
		}
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("substring(");
		buf.append(getArgument(0).pprint());
		buf.append(", ");
		buf.append(getArgument(1).pprint());
		if(getLength() > 2) {
			buf.append(", ");
			buf.append(getArgument(2).pprint());
		}
		buf.append(")");
		return buf.toString();
	}
}
