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

import org.exist.xpath.value.Item;
import org.exist.xpath.value.NumericValue;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * A unary minus or plus.
 * 
 * @author wolf
 */
public class UnaryExpr extends PathExpr {

	private int mode;
	
	public UnaryExpr(StaticContext context, int mode) {
		super(context);
		this.mode = mode;
	}

	public int returnsType() {
		return Type.DECIMAL;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) 
	throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		if(getLength() == 0)
			throw new XPathException("unary expression requires an operand");
		NumericValue value = (NumericValue)
			getExpression(0).eval(contextSequence).convertTo(Type.NUMBER);
		if(mode == Constants.MINUS)
			return value.negate();
		else
			return value;
	}
}
