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

import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Abstract base class for the XQuery/XPath combining operators "union", "intersect"
 * and "except".
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public abstract class CombiningExpression extends AbstractExpression {

	protected PathExpr left;
	protected PathExpr right;
	
	/**
	 * @param context
	 */
	public CombiningExpression(XQueryContext context, PathExpr left, PathExpr right) {
		super(context);
		this.left = left;
		this.right = right;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException;

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public abstract String pprint();

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.NODE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#resetState()
	 */
	public void resetState() {
		left.resetState();
		right.resetState();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		left.setPrimaryAxis(axis);
		right.setPrimaryAxis(axis);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		left.setInPredicate(inPredicate);
		right.setInPredicate(inPredicate);
	}
}
