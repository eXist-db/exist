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

import org.exist.xpath.value.AtomicValue;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * CastExpression represents cast expressions as well as all type 
 * constructors.
 * 
 * @author wolf
 */
public class CastExpression extends AbstractExpression {

	private Expression expression;
	private int requiredType;
	private int cardinality = Cardinality.EXACTLY_ONE;
	
	/**
	 * Constructor. When calling {@link #eval(Sequence, Item)} 
	 * the passed expression will be cast into the required type and cardinality.
	 * 
	 * @param context
	 */
	public CastExpression(XQueryContext context, Expression expr, int requiredType, int cardinality) {
		super(context);
		this.expression = expr;
		this.requiredType = requiredType;
		this.cardinality = cardinality;
		if(!Type.subTypeOf(expression.returnsType(), Type.ATOMIC))
			expression = new Atomize(context, expression);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence seq = expression.eval(contextSequence, contextItem);
		if(seq.getLength() == 0) {
			if((cardinality & Cardinality.ZERO) == 0)
				throw new XPathException("Type error: empty sequence is not allowed here");
			else
				return Sequence.EMPTY_SEQUENCE;
		}
		return (AtomicValue)seq.itemAt(0).convertTo(requiredType);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		return "(" + expression.pprint() + " cast as " + Type.getTypeName(requiredType) + ")";
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return requiredType;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return Cardinality.ZERO_OR_ONE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#resetState()
	 */
	public void resetState() {
		expression.resetState();
	}

}
