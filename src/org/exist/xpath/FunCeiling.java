
/* eXist xml document repository and xpath implementation
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
import org.w3c.dom.NodeList;

public class FunCeiling extends Function {

    public FunCeiling(BrokerPool pool) {
		super(pool, "ceiling");
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
		double val;
		// Argument is a node list
		if(getArgument(0).returnsType() == Constants.TYPE_NODELIST) {
			ValueSet values = new ValueSet();
			NodeList args = getArgument(0).eval(dset, set, node).getNodeList();
			for(int i = 0; i < args.getLength(); i++) {
				try {
					val = Double.parseDouble(args.item(i).getNodeValue());
					values.add(new ValueNumber(Math.ceil(val)));
				} catch(NumberFormatException nfe) {
				}
			}
			return values;
		} else {
			// does argument return a value set?
			Value v = getArgument(0).eval(dset, set, node);
			ValueSet values = new ValueSet();
			if(v.getType() == Value.isValueSet) {
				for(int i = 0; i < v.getLength(); i++) {
					val = v.get(i).getNumericValue();
					if(val != Double.NaN)
						values.add(new ValueNumber(Math.ceil(val)));
				}
				return values;
			} else
				// Argument is a single number
				return new ValueNumber(Math.ceil(v.getNumericValue()));
		}
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("ceiling(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
    }
}
			  
