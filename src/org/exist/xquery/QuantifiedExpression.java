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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

/**
 * Represents a quantified expression: "some ... in ... satisfies", 
 * "every ... in ... satisfies".
 * 
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class QuantifiedExpression extends BindingExpression {
	
	public final static int SOME = 0;
	public final static int EVERY = 1;
	
	private int mode = SOME;
	
	/**
	 * @param context
	 */
	public QuantifiedExpression(XQueryContext context, int mode) {
		super(context);
		this.mode = mode;
	}

	public Sequence eval(Sequence contextSequence, Item contextItem, Sequence resultSequence) throws XPathException {
		context.pushLocalContext(false);
		Variable var = new Variable(QName.parse(context, varName));
		context.declareVariable(var);
		Sequence inSeq = inputSequence.eval(null);
		Sequence satisfiesSeq;
		boolean found = false;
		for(SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
			contextItem = i.nextItem();
			if(sequenceType != null)
				// check sequence type
				sequenceType.checkType(contextItem.getType());
			var.setValue(contextItem.toSequence());
			satisfiesSeq = returnExpr.eval(null);
			if(returnExpr.returnsType() == Type.BOOLEAN)
				found = satisfiesSeq.effectiveBooleanValue();
			else
				found = satisfiesSeq.getLength() != 0;
			if((mode == SOME && found) || (mode == EVERY && !found))
				break;
		}
		context.popLocalContext();
		return found ? BooleanValue.TRUE : BooleanValue.FALSE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("some $");
		buf.append(varName);
		buf.append(" in ");
		buf.append(inputSequence.pprint());
		buf.append(" satisfies ");
		buf.append(returnExpr.pprint());
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_ITEM | Dependency.CONTEXT_SET;
	}

}
