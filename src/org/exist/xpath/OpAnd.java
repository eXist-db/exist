
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
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;

public class OpAnd extends BinaryOp {

	public OpAnd() {
		super();
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		if (getLength() == 0)
			return in_docs;
		DocumentSet out_docs = getExpression(0).preselect(in_docs, context);
		for (int i = 1; i < getLength(); i++)
			out_docs = out_docs.intersection(getExpression(i).preselect(out_docs, context));
		return out_docs;
	}

	public Value eval(StaticContext context, DocumentSet docs, NodeSet contextSet, 
		NodeProxy contextNode) throws XPathException {
		if (getLength() == 0)
			return new ValueNodeSet(contextSet);
		LOG.debug("processing " + getExpression(0).pprint());
		NodeSet rr, rl = (NodeSet) getExpression(0).eval(context, docs, contextSet, contextNode).getNodeList();
		rl = rl.getContextNodes(contextSet, inPredicate);
		for (int i = 1; i < getLength(); i++) {
			LOG.debug("processing " + getExpression(i).pprint());
			rr = (NodeSet) getExpression(i).eval(context, docs, contextSet, contextNode).getNodeList();
			rl = rl.intersection(rr.getContextNodes(contextSet, inPredicate));
		}
		return new ValueNodeSet(rl);
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
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		for(int i = 0; i < getLength(); i++) {
			getExpression(i).setInPredicate(inPredicate);
		}
	}

}
