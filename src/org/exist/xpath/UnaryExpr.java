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
import org.exist.xpath.value.DoubleValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public class UnaryExpr extends PathExpr {

	private int mode;
	
	public UnaryExpr(int mode) {
		super();
		this.mode = mode;
	}

	public int returnsType() {
		return Type.DECIMAL;
	}
	
	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, Item contextItem) 
	throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		if(getLength() == 0)
			throw new XPathException("unary expression requires an operand");
		DoubleValue value = (DoubleValue)
			getExpression(0).eval(context, docs, contextSequence).convertTo(Type.DECIMAL);
		switch(mode) {
			case Constants.MINUS :
				value.setValue(-value.getDouble());
				break;
			case Constants.PLUS :
				value.setValue(+value.getDouble());
		}
		return value;
	}
}
