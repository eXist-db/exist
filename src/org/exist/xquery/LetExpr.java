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

import org.exist.dom.QName;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements an XQuery let-expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class LetExpr extends BindingExpression {

	public LetExpr(XQueryContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem, Sequence resultSequence)
		throws XPathException {
		// Save the local variable stack
		LocalVariable mark = context.markLocalVariables();
		
		// Declare the iteration variable
		LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
		context.declareVariable(var);
		
		Sequence in = inputSequence.eval(null, null);
		if (sequenceType != null) {
			sequenceType.checkType(in.getItemType());
			sequenceType.checkCardinality(in);
		}
		clearContext(in);
		var.setValue(in);
		Sequence filtered = null;
		if (whereExpr != null) {
			filtered = applyWhereExpression(null);
			// TODO: don't use returnsType here
			if (whereExpr.returnsType() == Type.BOOLEAN) {
				if (!filtered.effectiveBooleanValue())
					return Sequence.EMPTY_SEQUENCE;
			} else if (filtered.getLength() == 0)
				return Sequence.EMPTY_SEQUENCE;
		}
		
		// Check if we can speed up the processing of the "order by" clause.
		boolean fastOrderBy = checkOrderSpecs(in);
		
		//	PreorderedValueSequence applies the order specs to all items
		// in one single processing step
		if(fastOrderBy) {
			in = new PreorderedValueSequence(orderSpecs, in.toNodeSet());
		}
		
		// Otherwise, if there's an order by clause, wrap the result into
		// an OrderedValueSequence. OrderedValueSequence will compute
		// order expressions for every item when it is added to the result sequence.
		if(resultSequence == null) {
			if(orderSpecs != null && !fastOrderBy)
				resultSequence = new OrderedValueSequence(orderSpecs, in.getLength());
			else
				resultSequence = new ValueSequence();
		}
		
		if(returnExpr instanceof BindingExpression) {
			((BindingExpression)returnExpr).eval(null, null, resultSequence);
		} else {
			in = returnExpr.eval(null);
			resultSequence.addAll(in);
		}
		if(orderSpecs != null && !fastOrderBy)
			((OrderedValueSequence)resultSequence).sort();
		
		// Restore the local variable stack
		context.popLocalVariables(mark);
		return resultSequence;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("(let ");
		buf.append(varName);
		if (sequenceType != null) {
			buf.append(" as ");
			buf.append(sequenceType.toString());
		}
		buf.append(" := ");
		buf.append(inputSequence.pprint());
		if (whereExpr != null)
			buf.append(" where ").append(whereExpr.pprint());
		if (orderSpecs != null) {
			buf.append(" order by ");
			for (int i = 0; i < orderSpecs.length; i++) {
				buf.append(orderSpecs[i].toString());
			}
		}
		buf.append(" return ");
		buf.append(returnExpr.pprint());
		buf.append(')');
		return buf.toString();
	}

}
