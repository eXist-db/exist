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

import org.exist.dom.QName;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceType;

/**
 * A global variable declaration (with: declare variable). Variable bindings within
 * for and let expressions are handled by {@link org.exist.xpath.ForExpr} and
 * {@link org.exist.xpath.LetExpr}.
 * 
 * @author wolf
 */
public class VariableDeclaration extends AbstractExpression {

	String qname;
	SequenceType sequenceType = null;
	Expression expression;
	
	/**
	 * @param context
	 */
	public VariableDeclaration(StaticContext context, String qname, Expression expr) {
		super(context);
		this.qname = qname;
		this.expression = expr;
	}

	/**
	 * Set the sequence type of the variable.
	 * 
	 * @param type
	 */
	public void setSequenceType(SequenceType type) {
		this.sequenceType = type;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		// declare the variable
		Variable var = new Variable(QName.parse(context, qname));
		Sequence seq = expression.eval(contextSequence, contextItem);
		if(sequenceType != null) {
			sequenceType.checkType(seq.getItemType());
			sequenceType.checkCardinality(seq);
		}
		var.setValue(seq);
		context.declareVariable(var);
		return Sequence.EMPTY_SEQUENCE;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		return "";
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return expression.returnsType();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return expression.getDependencies();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return expression.getCardinality();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#resetState()
	 */
	public void resetState() {
		expression.resetState();
	}
}
