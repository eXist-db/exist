
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
import org.exist.dom.SingleNodeSet;

public class FunRound extends Function {

	public FunRound() {
		super("round");
	}

	public int returnsType() {
		return Constants.TYPE_NUM;
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		return getArgument(0).preselect(in_docs, context);
	}

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, NodeProxy contextNode) throws XPathException {
		double val;
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		// Argument is a node list
		if (getArgument(0).returnsType() == Constants.TYPE_NODELIST) {
			ValueSet values = new ValueSet();
			NodeSet args = (NodeSet) getArgument(0).eval(context, docs, contextSet, contextNode).getNodeList();
			if (args.getLength() > 0) {
				try {
					val = Double.parseDouble(args.get(0).getNodeValue());
					values.add(new ValueNumber(Math.round(val)));
				} catch (NumberFormatException nfe) {
					throw new XPathException("Argument is not a number");
				}
			}
			return values;
		} else {
			// does argument return a value set?
			Value v = getArgument(0).eval(context, docs, contextSet, contextNode);
			return new ValueNumber(Math.ceil(v.getNumericValue()));
		}
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("round(");
		buf.append(getArgument(0).pprint());
		buf.append(')');
		return buf.toString();
	}
}
