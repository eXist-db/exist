
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

public class OpOr extends BinaryOp {

	public OpOr() {
		super();
	}

	public DocumentSet preselect(DocumentSet in_docs, StaticContext context) throws XPathException {
		if (getLength() == 0)
			return in_docs;
		DocumentSet out_docs = getExpression(0).preselect(in_docs, context);
		for (int i = 1; i < getLength(); i++)
			out_docs = out_docs.union(getExpression(i).preselect(in_docs, context));
		return out_docs;
	}

	public Sequence eval(StaticContext context, DocumentSet docs, Sequence contextSequence, 
		Item contextItem) throws XPathException {
		if (getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		LOG.debug("processing " + getExpression(0).pprint());
		NodeSet rr, rl = (NodeSet) getExpression(0).eval(context, docs, contextSequence, null);
		rl = rl.getContextNodes((NodeSet)contextSequence, inPredicate);
		for (int i = 1; i < getLength(); i++) {
			LOG.debug("processing " + getExpression(i).pprint());
			rr = (NodeSet) getExpression(i).eval(context, docs, contextSequence, null);
			rl = rl.union(rr.getContextNodes((NodeSet)contextSequence, inPredicate));
		}
		return rl;
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

	/* (non-Javadoc)
		 * @see org.exist.xpath.Expression#setInPredicate(boolean)
		 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		for (int i = 0; i < getLength(); i++) {
			getExpression(i).setInPredicate(inPredicate);
		}
	}
}
