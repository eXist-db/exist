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
 * Base class for the boolean operators "and" and "or".
 * 
 * @author Wolfgang <wolfgang@exist-db.org>
 */
public abstract class LogicalOp extends BinaryOp {

	/**
	 * @param context
	 */
	public LogicalOp(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public abstract Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException;

	/* (non-Javadoc)
	 * @see org.exist.xquery.BinaryOp#returnsType()
	 */
	public int returnsType() {
		if(Type.subTypeOf(getLeft().returnsType(), Type.NODE) &&
			Type.subTypeOf(getRight().returnsType(), Type.NODE) &&
			(getLeft().getDependencies() & Dependency.CONTEXT_ITEM) == 0 &&
			(getRight().getDependencies() & Dependency.CONTEXT_ITEM) == 0)
			return Type.NODE;
		else
			return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.PathExpr#getDependencies()
	 */
	public int getDependencies() {
		if(returnsType() == Type.BOOLEAN)
			return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
		else
			return Dependency.CONTEXT_SET;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#setInPredicate(boolean)
	 */
	public void setInPredicate(boolean inPredicate) {
		super.setInPredicate(inPredicate);
		for (int i = 0; i < getLength(); i++) {
			getExpression(i).setInPredicate(inPredicate);
		}
	}
		
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public abstract String pprint();

}
