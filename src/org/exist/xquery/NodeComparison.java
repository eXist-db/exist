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

import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Implements node comparisons: is, isnot, &lt;&lt;, &gt;&gt;.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class NodeComparison extends BinaryOp {

	private int relation;
	
	/**
	 * @param context
	 */
	public NodeComparison(XQueryContext context, Expression left, Expression right, int relation) {
		super(context);
		this.relation = relation;
		add(new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, left));
		add(new DynamicCardinalityCheck(context, Cardinality.EXACTLY_ONE, right));
		//add(left);
		//add(right);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.ZERO_OR_ONE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.BinaryOp#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence ls = getLeft().eval(contextSequence, contextItem);
		Sequence rs = getRight().eval(contextSequence, contextItem);
		if(ls.getLength() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}
		if(rs.getLength() == 0) {
			return Sequence.EMPTY_SEQUENCE;
		}
		NodeValue sv = (NodeValue)ls.itemAt(0);
		NodeValue rv = (NodeValue)rs.itemAt(0);
		if(sv.getImplementationType() != rv.getImplementationType()) {
			// different implementations
			return BooleanValue.FALSE;
		}
		BooleanValue result;
		switch(relation) {
			case Constants.IS:
				result = sv.equals(rv) ? BooleanValue.TRUE : BooleanValue.FALSE;
				break;
			case Constants.ISNOT:
				result = sv.equals(rv) ? BooleanValue.FALSE : BooleanValue.TRUE;
				break;
			case Constants.BEFORE:
				result = sv.before(rv) ? BooleanValue.TRUE : BooleanValue.FALSE;
				break;
			case Constants.AFTER:
				result = sv.after(rv) ? BooleanValue.TRUE : BooleanValue.FALSE;
				break;
			default:
				throw new XPathException("Illegal argument: unknown relation");
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(getLeft().pprint()).append(' ').append(Constants.OPS[relation]);
		buf.append(' ').append(getRight().pprint());
		return buf.toString();
	}

}
