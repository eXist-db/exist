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

import org.exist.dom.NodeSet;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 * Represents a primary expression in a simple path step like
 * foo//$x. The class is mainly used to wrap variable references inside
 * a path expression.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class SimpleStep extends Step {

	private Expression expression;
	
	/**
	 * @param context
	 * @param axis
	 */
	public SimpleStep(XQueryContext context, int axis, Expression expression) {
		super(context, axis);
		this.expression = expression;
		this.expression.setPrimaryAxis(axis);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		NodeSet set = expression.eval(contextSequence).toNodeSet();
		if(set.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		switch(axis) {
			case Constants.DESCENDANT_SELF_AXIS:
				set = set.selectAncestorDescendant(contextSequence.toNodeSet(), NodeSet.DESCENDANT, true, inPredicate);
				break;
			case Constants.CHILD_AXIS:
				set = set.selectParentChild(contextSequence.toNodeSet(), NodeSet.DESCENDANT, inPredicate);
				break;
			default:
				throw new XPathException("Wrong axis specified");
		}
		return set;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#pprint()
	 */
	public String pprint() {
		return "simple: " + (axis == Constants.DESCENDANT_SELF_AXIS ? "//" + expression.pprint() : '/' + expression.pprint());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#resetState()
	 */
	public void resetState() {
		super.resetState();
		expression.resetState();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Step#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		expression.setInPredicate(inPredicate);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		expression.setPrimaryAxis(axis);
	}
}
