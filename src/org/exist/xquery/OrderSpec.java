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

/**
 * An XQuery order specifier as specified in an "order by" clause.
 * 
 * @author wolf
 */
public class OrderSpec {

	public static final int ASCENDING_ORDER = 0;
	public static final int DESCENDING_ORDER = 1;
	
	public static final int EMPTY_GREATEST = 0;
	public static final int EMPTY_LEAST = 4;
	
	private Expression expression;
	private int modifiers = 0;
	
	/**
	 * 
	 */
	public OrderSpec(Expression sortExpr) {
		this.expression = sortExpr;
	}

	public void setModifiers(int modifiers) {
		this.modifiers = modifiers;
	}
	
	public Expression getSortExpression() {
		return expression;
	}
	
	public int getModifiers() {
		return modifiers;
	}
	
	public String toString() {
		StringBuffer buf = new StringBuffer();
		buf.append(expression.pprint());
		buf.append(' ');
		buf.append((modifiers & DESCENDING_ORDER) == 0 ? "ascending" : "descending");
		return buf.toString();
	}
	
	public void resetState() {
	    expression.resetState();
	}
}
