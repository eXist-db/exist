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
import org.exist.xpath.value.OrderedValueSequence;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.Type;

/**
 * Implements an XQuery let-expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class LetExpr extends BindingExpression {

	public LetExpr(StaticContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem)
		throws XPathException {
		context.pushLocalContext(false);
		Variable var = new Variable(QName.parse(context, varName));
		context.declareVariable(var);
		Sequence val = inputSequence.eval(null, null);
		if (sequenceType != null) {
			sequenceType.checkType(val.getItemType());
			sequenceType.checkCardinality(val);
		}
		var.setValue(val);

		Sequence filtered = null;
		if (whereExpr != null) {
			filtered = applyWhereExpression(null);
			if (whereExpr.returnsType() == Type.BOOLEAN) {
				if (!filtered.effectiveBooleanValue())
					return Sequence.EMPTY_SEQUENCE;
			} else if (filtered.getLength() == 0)
				return Sequence.EMPTY_SEQUENCE;
		}
		Sequence returnSeq = null;
		if (orderSpecs == null)
			returnSeq = returnExpr.eval(filtered, null);
		else {
			if (filtered != null)
				val = filtered;
			OrderedValueSequence ordered =
				new OrderedValueSequence(orderSpecs, val.getLength());
			ordered.addAll(val);
			returnSeq = returnExpr.eval(ordered, null);
		}
		context.popLocalContext();
		return returnSeq;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("let ");
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
		return buf.toString();
	}

}
