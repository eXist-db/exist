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
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;

public class Union extends PathExpr {

	protected PathExpr left, right;

    public Union(PathExpr left, PathExpr right) {
        super();
		this.left = left;
		this.right = right;
    }

    public int returnsType() {
		return Constants.TYPE_NODELIST;
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

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		NodeSet lval = (NodeSet)left.eval(context, docs, contextSet, contextNode).getNodeList();
		LOG.debug("left " + left.pprint() + " returned: " + lval.getLength());
		NodeSet rval = (NodeSet)right.eval(context, docs, contextSet, contextNode).getNodeList();
		LOG.debug("right " + right.pprint() + " returned: " + rval.getLength());
		long start = System.currentTimeMillis();
        NodeSet result = lval.union(rval);
		LOG.debug("union found " + result.getLength() + " in "+ (System.currentTimeMillis() - start));
		return new ValueNodeSet(result);
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
