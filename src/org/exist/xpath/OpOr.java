
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
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * Boolean operator "or".
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class OpOr extends LogicalOp {

	public OpOr(StaticContext context) {
		super(context);
	}

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
		if (getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression left = getLeft();
		Expression right = getRight();
		if(Type.subTypeOf(left.returnsType(), Type.NODE) &&
			Type.subTypeOf(right.returnsType(), Type.NODE)) {
			NodeSet rl = left.eval(contextSequence, null).toNodeSet();
			rl = rl.getContextNodes(inPredicate);
			NodeSet rr = right.eval(contextSequence, null).toNodeSet();
			rr = rr.getContextNodes(inPredicate);
			rl = rl.union(rr);
			return rl;
		} else {
			boolean ls = left.eval(contextSequence).effectiveBooleanValue();
			boolean rs = right.eval(contextSequence).effectiveBooleanValue();
			return ls || rs ? BooleanValue.TRUE : BooleanValue.FALSE;
		}
	}
	
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(getExpression(0).pprint());
		for (int i = 1; i < getLength(); i++) {
			buf.append(" or ");
			buf.append(getExpression(i).pprint());
		}
		return buf.toString();
	}
}
