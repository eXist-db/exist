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

import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

/**
 * Check a function parameter type at runtime.
 *  
 * @author wolf
 */
public class DynamicTypeCheck extends AbstractExpression {

	private Expression expression;
	private int requiredType;
	
	public DynamicTypeCheck(XQueryContext context, int requiredType, Expression expr) {
		super(context);
		this.requiredType = requiredType;
		this.expression = expr;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence seq = expression.eval(contextSequence, contextItem);
		Item item;
		for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
			item = i.nextItem();
			if(!Type.subTypeOf(item.getType(), requiredType)) {
				throw new XPathException(expression.getASTNode(), "Type error in expression" +
					": required type is " + Type.getTypeName(requiredType) +
					"; got: " + Type.getTypeName(item.getType()) + ": " + item.getStringValue());
			}
		}
		return seq;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public String pprint() {
		return expression.pprint();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return requiredType;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return expression.getDependencies();
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#resetState()
	 */
	public void resetState() {
		expression.resetState();
	}
	
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getASTNode()
	 */
	public XQueryAST getASTNode() {
		return expression.getASTNode();
	}
}
