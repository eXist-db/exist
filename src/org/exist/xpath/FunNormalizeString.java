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

import java.util.StringTokenizer;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;

/**
 * xpath-library function: string(object)
 *
 */
public class FunNormalizeString extends Function {
	
	public FunNormalizeString(BrokerPool pool) {
		super(pool, "normalize-space");
	}

	public int returnsType() {
		return Constants.TYPE_STRING;
	}
		
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		if(contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		String value;
		if(getArgumentCount() == 0)
			value = contextSet.getLength() > 0 ? contextSet.get(0).getNodeValue() : "";
		else
			value = getArgument(0).eval(context, docs, contextSet).getStringValue();
		StringBuffer result = new StringBuffer();
		if(value.length() > 0) {
			StringTokenizer tok = new StringTokenizer(value);
			while (tok.hasMoreTokens()) {
				result.append(tok.nextToken());
				if(tok.hasMoreTokens()) result.append(' ');
			}
		}
		return new ValueString(result.toString());
	}
}
