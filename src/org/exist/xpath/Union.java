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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

public class Union extends PathExpr {

	protected PathExpr left, right;

    public Union(PathExpr left, PathExpr right) {
        super();
		this.left = left;
		this.right = right;
    }

    public int returnsType() {
		return Type.NODE;
	}
	
	/**
	 * check relevant documents. if right operand is a string literal
	 * we check which documents contain it at all. in other cases
	 * do nothing.
	 */
	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
        //return in_docs;
		DocumentSet left_docs = left.preselect(in_docs, context);
		DocumentSet right_docs = right.preselect(in_docs, context);
		return left_docs.union(right_docs);
	}

	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
		Item contextItem) throws XPathException {
		Sequence lval = left.eval(context, docs, contextSequence, contextItem);
		Sequence rval = right.eval(context, docs, contextSequence, contextItem);
		if(lval.getItemType() != Type.NODE || rval.getItemType() != Type.NODE)
			throw new XPathException("union operand is not a node sequence");
        NodeSet result = ((NodeSet)lval).union((NodeSet)rval);
		return result;
	}

	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(left.pprint());
		buf.append("|");
		buf.append(right.pprint());
		return buf.toString();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		left.setInPredicate(inPredicate);
		right.setInPredicate(inPredicate);
	}

}
