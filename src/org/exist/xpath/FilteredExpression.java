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
package org.exist.xpath;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;

/**
 * FilteredExpression represents a primary expression with a predicate. Examples:
 * for $i in (1 to 10)[$i mod 2 = 0], $a[1], (doc("test.xml")//section)[2]. Other predicate
 * expressions are handled by class {@link org.exist.xpath.LocationStep}.
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FilteredExpression extends AbstractExpression {

	protected Expression expression;
	protected List predicates = new ArrayList(2);

	/**
	 * @param context
	 */
	public FilteredExpression(StaticContext context, Expression expr) {
		super(context);
		this.expression = expr;
	}

	public void addPredicate(Predicate pred) {
		predicates.add(pred);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		if (contextItem != null)
			contextSequence = contextItem.toSequence();
		Sequence seq = expression.eval(contextSequence, contextItem);
		if (seq.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		setContext(seq);
		Predicate pred;
		Sequence result = seq;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			pred = (Predicate) i.next();
			result = pred.evalPredicate(contextSequence, result);
		}
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append(expression.pprint());
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			buf.append('[');
			buf.append(((Expression) i.next()).pprint());
			buf.append(']');
		}
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return expression.returnsType();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#resetState()
	 */
	public void resetState() {
		expression.resetState();
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			Predicate pred = (Predicate) i.next();
			pred.resetState();
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#setPrimaryAxis(int)
	 */
	public void setPrimaryAxis(int axis) {
		expression.setPrimaryAxis(axis);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		int deps = Dependency.CONTEXT_SET;
		for (Iterator i = predicates.iterator(); i.hasNext();) {
			deps |= ((Predicate) i.next()).getDependencies();
		}
		return deps;
	}
}
