/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xpath;

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.SingleNodeSet;

public class UnaryExpr extends PathExpr {

	private int mode;
	
	public UnaryExpr(int mode) {
		super();
		this.mode = mode;
	}

	public int returnsType() {
		return Constants.TYPE_NUM;
	}
	
	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, NodeProxy node) throws XPathException {
		if(node != null)
			contextSet = new SingleNodeSet(node);
		if(getLength() == 0)
			throw new XPathException("unary expression requires an operand");
		double value = getExpression(0).eval(context, docs, contextSet).getNumericValue();
		switch(mode) {
			case Constants.MINUS :
				value = -value;
				break;
			case Constants.PLUS :
				value = +value;
		}
		return new ValueNumber(value);
	}
}
