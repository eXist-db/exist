/*
 * NativeBroker.java - eXist Open Source Native XML Database
 * Copyright (C) 2001-03 Wolfgang M. Meier
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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

/**
 * xpath-library function: string(object)
 *
 */
public class FunConcat extends Function {
	
	public FunConcat(BrokerPool pool) {
		super(pool, "concat");
	}

	public int returnsType() {
		return Constants.TYPE_STRING;
	}
		
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		if(getArgumentCount() < 2)
			throw new XPathException ("concat requires at least two arguments");
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		StringBuffer result = new StringBuffer();
		Expression arg;
		for(int i = 0; i < getArgumentCount(); i++) {
			arg = getArgument(i);
			result.append(arg.eval(context, docs, contextSet).getStringValue());
		}
		return new ValueString(result.toString());
	}
}
