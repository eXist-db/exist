
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
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.xpath.value.BooleanValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

public class OpAnd extends BinaryOp {

	public OpAnd(StaticContext context) {
		super(context);
	}

	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		if (getLength() == 0)
			return in_docs;
		DocumentSet out_docs = getExpression(0).preselect(in_docs);
		for (int i = 1; i < getLength(); i++)
			out_docs = out_docs.intersection(getExpression(i).preselect(out_docs));
		return out_docs;
	}

	public Sequence eval(DocumentSet docs, Sequence contextSequence, Item contextItem) throws XPathException {
		if (getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		
		boolean nodeSetCompare = true;
		for(int i = 0; i < getLength(); i++) {
			if(!Type.subTypeOf(getExpression(i).returnsType(), Type.NODE))
				nodeSetCompare = false;
		}
		
		if(nodeSetCompare) {
			NodeSet rr, rl = (NodeSet) getExpression(0).eval(docs, contextSequence, contextItem);
			rl = rl.getContextNodes((NodeSet)contextSequence, inPredicate);
			for (int i = 1; i < getLength(); i++) {
				LOG.debug("processing " + getExpression(i).pprint());
				rr = (NodeSet) getExpression(i).eval(docs, contextSequence, contextItem);
				rl = rl.intersection(rr.getContextNodes((NodeSet)contextSequence, inPredicate));
			}
			return rl;
		} else {
			Sequence result;
			if(Type.subTypeOf(contextSequence.getItemType(), Type.NODE))
				result = new ExtArrayNodeSet();
			else
				result = new ValueSequence();
			Item item;
			boolean r;
			for(SequenceIterator i = contextSequence.iterate(); i.hasNext(); ) {
				item = i.nextItem();
				r = true;
				for(int j = 0; j < getLength(); j++) {
					r = getExpression(j).
						eval(docs, contextSequence, item).effectiveBooleanValue();
					if(!r)
						break;
				}
				if(r)
					result.add(item);
			}
			return result;
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
