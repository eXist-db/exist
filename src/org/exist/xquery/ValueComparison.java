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
package org.exist.xquery;

import java.text.Collator;
import java.util.Iterator;

import org.exist.dom.ContextItem;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class ValueComparison extends GeneralComparison {

	/**
	 * @param context
	 * @param relation
	 */
	public ValueComparison(XQueryContext context, int relation) {
		super(context, relation);
	}

	/**
	 * @param context
	 * @param left
	 * @param right
	 * @param relation
	 */
	public ValueComparison(
		XQueryContext context,
		Expression left,
		Expression right,
		int relation) {
		super(context, left, right, relation);
	}

	protected Sequence genericCompare(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence ls = getLeft().eval(contextSequence, contextItem);
		Sequence rs = getRight().eval(contextSequence, contextItem);
		if(ls.getLength() == 0 || rs.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		Collator collator = getCollator(contextSequence);
		AtomicValue lv, rv;
		if (ls.getLength() == 1 && rs.getLength() == 1) {
			lv = ls.itemAt(0).atomize();
			rv = rs.itemAt(0).atomize();
			return BooleanValue.valueOf(compareValues(collator, lv, rv));
		} else
			throw new XPathException("Type error: sequence with less or more than one item is not allowed here");
	}

	protected Sequence nodeSetCompare(
		NodeSet nodes,
		Sequence contextSequence)
		throws XPathException {
		NodeSet result = new ExtArrayNodeSet();
		NodeProxy current;
		ContextItem c;
		Sequence rs;
		AtomicValue lv, rv;
		Collator collator = getCollator(contextSequence);
		for (Iterator i = nodes.iterator(); i.hasNext();) {
			current = (NodeProxy) i.next();
			c = current.getContext();
			do {
				lv = current.atomize();
				rs = getRight().eval(c.getNode().toSequence());
				if(rs.getLength() != 1)
					throw new XPathException("Type error: sequence with less or more than one item is not allowed here");
				if(compareValues(collator, lv, rs.itemAt(0).atomize()))
					result.add(current);
			} while ((c = c.getNextItem()) != null);
		}
		return result;
	}
}
