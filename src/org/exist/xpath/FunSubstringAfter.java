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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

/**
 * xpath-library function: string(object)
 *
 */
public class FunSubstringAfter extends Function {
	
	public FunSubstringAfter(BrokerPool pool) {
		super(pool, "substring-before");
	}

	public int returnsType() {
		return Constants.TYPE_STRING;
	}
		
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) {
		if(getArgumentCount() < 2)
			throw new IllegalArgumentException("substring-before requires two arguments");
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);
		String value = arg0.eval(context, docs, contextSet).getStringValue();
		String cmp = arg1.eval(context, docs, contextSet).getStringValue();
		int p = value.indexOf(cmp);
		if(p > -1)
			return new ValueString( p + 1 < value.length() ? value.substring(p + 1) : "");
		else
			return new ValueString("");
	}
}
