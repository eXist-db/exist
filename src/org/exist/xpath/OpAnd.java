
/* eXist Open Source Native XML Database
 * Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de)
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
 */

package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Category;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeIDSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.storage.BrokerPool;

public class OpAnd extends BinaryOp {

	private static Category LOG = Category.getInstance(OpAnd.class.getName());

	public OpAnd(BrokerPool pool) {
		super(pool);
	}

	public DocumentSet preselect(DocumentSet in_docs) {
		if (getLength() == 0)
			return in_docs;
		DocumentSet out_docs = getExpression(0).preselect(in_docs);
		for (int i = 1; i < getLength(); i++)
			out_docs = out_docs.intersection(getExpression(i).preselect(out_docs));
		return out_docs;
	}

	public Value eval(DocumentSet docs, NodeSet context, NodeProxy node) {
		if (getLength() == 0)
			return new ValueNodeSet(context);
		LOG.debug("processing " + getExpression(0).pprint());
		NodeSet rr, rl = (NodeSet) getExpression(0).eval(docs, context, null).getNodeList();
		rl = rl.getContextNodes(context, inPredicate);
		for (int i = 1; i < getLength(); i++) {
			LOG.debug("processing " + getExpression(i).pprint());
			rr = (NodeSet) getExpression(i).eval(docs, context, null).getNodeList();
			rl = rl.intersection(rr.getContextNodes(context, inPredicate));
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
