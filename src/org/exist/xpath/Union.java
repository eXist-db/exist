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
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 * $Id$
 */

package org.exist.xpath;

import org.exist.dom.NodeSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public class Union extends CombiningExpression {

    public Union(StaticContext context, PathExpr left, PathExpr right) {
        super(context, left, right);
    }
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		Sequence lval = left.eval(contextSequence, contextItem);
		Sequence rval = right.eval(contextSequence, contextItem);
		if(!(Type.subTypeOf(lval.getItemType(), Type.NODE) && Type.subTypeOf(rval.getItemType(), Type.NODE)))
			throw new XPathException("union operand is not a node sequence");
        NodeSet result = ((NodeSet)lval).union((NodeSet)rval);
		return result;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(left.pprint());
		buf.append(" union ");
		buf.append(right.pprint());
		return buf.toString();
	}
}
