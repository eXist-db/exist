
/* eXist Open Source Native XML Database
 * Copyright (C) 2001-03,  Wolfgang M. Meier (wolfgang@exist-db.org)
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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * Boolean operator "and".
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public class OpAnd extends LogicalOp {

	public OpAnd(StaticContext context) {
		super(context);
	}

	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;

		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression left = getLeft();
		Expression right = getRight();
		if (Type.subTypeOf(left.returnsType(), Type.NODE)
			&& Type.subTypeOf(right.returnsType(), Type.NODE)) {
			NodeSet rl = left.eval(docs, contextSequence, null).toNodeSet();
			rl = rl.getContextNodes((NodeSet) contextSequence, inPredicate);
			NodeSet rr = right.eval(docs, contextSequence, null).toNodeSet();
			rl =
				rl.intersection(
					rr.getContextNodes((NodeSet) contextSequence, inPredicate));
			return rl;
		} else {
			boolean ls =
				left.eval(docs, contextSequence).effectiveBooleanValue();
			boolean rs =
				right.eval(docs, contextSequence).effectiveBooleanValue();
			return ls && rs ? BooleanValue.TRUE : BooleanValue.FALSE;
		}
	}
	
	public String pprint() {
		if (getLength() == 0)
			return "";
		StringBuffer buf = new StringBuffer();
		buf.append(getExpression(0).pprint());
		for (int i = 1; i < getLength(); i++) {
			buf.append(" and ");
			buf.append(getExpression(i).pprint());
		}
		return buf.toString();
	}

}
