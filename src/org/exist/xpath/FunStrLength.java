/* eXist Open Source Native XML Database
 * Copyright (C) 2000-03,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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

/**
 * xpath-library function: string-length(string?)
 *
 */
public class FunStrLength extends Function {

	public FunStrLength() {
		super("string-length");
	}

	public int returnsType() {
		return Constants.TYPE_NUM;
	}

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet,
		NodeProxy contextNode) throws XPathException {
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		Value v = getArgument(0).eval(context, docs, contextSet);
		String strval;
		if(getArgument(0).returnsType() == Constants.TYPE_NODELIST) {
			NodeSet nodes = (NodeSet)v.getNodeList();
			if(nodes.getLength() == 0)
				strval = "";
			else
				strval = nodes.get(0).getNodeValue();
		} else
			strval = v.getStringValue();
		return new ValueNumber(strval.length());
	}
}
