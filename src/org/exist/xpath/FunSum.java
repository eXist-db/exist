
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 * 
 * $Id$
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;

public class FunSum extends Function {

    public FunSum() {
		super("sum");
    }

    public int returnsType() {
		return Constants.TYPE_NUM;
    }

    public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		return getArgument(0).preselect(in_docs, context);
    }

    public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet,
    	NodeProxy contextNode) throws XPathException {
		double sum = 0.0, val;
		// does argument return a node list?
		if(getArgument(0).returnsType() == Constants.TYPE_NODELIST) {
			NodeSet args = (NodeSet) getArgument(0).eval(context, docs, contextSet, contextNode).getNodeList();
			String nval;
			for(int i = 0; i < args.getLength(); i++) {
				try {
					nval = args.get(i).getNodeValue();
					val = Double.parseDouble(nval);
					if(val != Double.NaN)
						sum += val;
				} catch (NumberFormatException nfe) {
				}
			}
		} else {
			// does argument return a value set?
			Value v = getArgument(0).eval(context, docs, contextSet, contextNode);
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
}
			  
