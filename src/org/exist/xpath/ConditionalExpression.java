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

import org.exist.dom.DocumentSet;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * @author wolf
 */
public class ConditionalExpression extends AbstractExpression {

	private Expression testExpr;
	private Expression thenExpr;
	private Expression elseExpr;
	
	public ConditionalExpression(StaticContext context, Expression testExpr, Expression thenExpr,
		Expression elseExpr) {
		super(context);
		this.testExpr = testExpr;
		this.thenExpr = thenExpr;
		this.elseExpr = elseExpr;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET | Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
		return thenExpr.getCardinality() | elseExpr.getCardinality();
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence testSeq = testExpr.eval(docs, contextSequence, contextItem);
		if(testSeq.effectiveBooleanValue()) {
			return thenExpr.eval(docs, contextSequence, contextItem);
		} else {
			System.out.println("else");
			return elseExpr.eval(docs, contextSequence, contextItem);
		}
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#preselect(org.exist.dom.DocumentSet)
	 */
	public DocumentSet preselect(DocumentSet in_docs) throws XPathException {
		return in_docs;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("if ");
		buf.append(testExpr.pprint());
		buf.append(" then ");
		buf.append(thenExpr.pprint());
		buf.append(" else ");
		buf.append(elseExpr.pprint());
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		if(thenExpr.returnsType() == elseExpr.returnsType())
			return thenExpr.returnsType();
		return Type.ITEM;
	}

}
