
/* eXist Open Source Native XML Database
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
import org.w3c.dom.Node;

public class FunSum extends Function {

    public FunSum(BrokerPool pool) {
		super(pool, "sum");
    }

    public int returnsType() {
		return Constants.TYPE_NUM;
    }

    public DocumentSet preselect(DocumentSet in_docs) {
		return getArgument(0).preselect(in_docs);
    }

    public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		NodeSet set = new ArraySet(1);
		DocumentSet dset = new DocumentSet();
		set.add(node);
		dset.add(node.doc);
		double sum = 0.0, val;
		// does argument return a node list?
		if(getArgument(0).returnsType() == Constants.TYPE_NODELIST) {
			NodeSet temp = (NodeSet)getArgument(0).eval(dset, set, node).getNodeList();
			Node n;
			for(int i = 0; i < temp.getLength(); i++) {
				n = temp.item(i);
				try {
					val = Double.parseDouble(n.getNodeValue());
					sum += val;
				} catch(NumberFormatException nfe) {
				}
			}
		} else {
			// does argument return a value set?
			Value v = getArgument(0).eval(dset, set, node);
			if(v.getType() == Value.isValueSet) {
				for(int i = 0; i < v.getLength(); i++) {
					val = v.get(i).getNumericValue();
					if(val != Double.NaN)
						sum += val;
				}
			} else // single value
				return v;
		}
		return new ValueNumber(sum);
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("sum(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
    }
}
			  
