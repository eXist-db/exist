/* eXist Open Source Native XML Database
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
import org.exist.storage.BrokerPool;
import org.w3c.dom.NodeList;

/**
 * xpath-library function: string-length(string?)
 *
 */
public class FunStrLength extends Function {

    public FunStrLength(BrokerPool pool) {
        super(pool, "string-length");
    }

    public int returnsType() {
		return Constants.TYPE_NUM;
	}
	
	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		ArraySet set = new ArraySet(1);
		set.add(node);
		DocumentSet dset = new DocumentSet();
		dset.add(node.doc);
		Value v = getArgument(0).eval(dset, set, node);
		if(v.getType() == Value.isNodeList) {
			NodeList list = v.getNodeList();
			ValueSet result = new ValueSet();
			for(int i = 0; i < list.getLength(); i++)
				result.add(new ValueNumber(list.item(i).getNodeValue().length()));
			return result;
		}
		return new ValueNumber(v.getStringValue().length());
	}
	
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("string-length(");
		buf.append(getArgument(0).pprint());
		buf.append(")");
		return buf.toString();
	}
}
