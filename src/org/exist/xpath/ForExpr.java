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
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.OrderedValueSequence;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.Type;
import org.exist.xpath.value.ValueSequence;

/**
 * Represents an XQuery "for" expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class ForExpr extends BindingExpression {

	public ForExpr(StaticContext context) {
		super(context);
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence result = new ValueSequence();
		context.pushLocalContext(false);
		// declare the variable
		Variable var = new Variable(QName.parse(context, varName));
		context.declareVariable(var);
		// evaluate the "in" expression
		Sequence in = inputSequence.eval(docs, null, null);
		var.setValue(in);
		if(whereExpr != null)
			whereExpr.setInPredicate(true);
		if(whereExpr != null && 
			( whereExpr.getDependencies() & Dependency.CONTEXT_ITEM ) == 0) {
			LOG.debug("using single walk-through");
			setContext(in);
			in = applyWhereExpression(context, docs, null);
			whereExpr = null;
		}
		if(orderSpecs != null) {
			OrderedValueSequence ordered = new OrderedValueSequence(docs, orderSpecs);
			ordered.addAll(in);
			in = ordered;
		}
		Sequence val = null;
		int p = 0;
		context.setContextPosition(0);
		// loop through each variable binding
		for (SequenceIterator i = in.iterate(); i.hasNext(); p++) {
			contextItem = i.nextItem();
			context.setContextPosition(p);
			if(contextItem instanceof NodeProxy)
				((NodeProxy)contextItem).addContextNode((NodeProxy)contextItem);
			contextSequence = contextItem.toSequence();
			var.setValue(contextSequence);
			if (whereExpr != null) {
				val = applyWhereExpression(context, docs, contextSequence);
				if(val.getLength() == 0)
					continue;
			} else
				val = contextItem.toSequence();
			val = returnExpr.eval(docs, val);
			result.addAll(val);
		}
		context.popLocalContext();
		return result;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("for ");
		buf.append(varName);
		buf.append(" in ");
		buf.append(inputSequence.pprint());
		if (whereExpr != null)
			buf.append(" where ").append(whereExpr.pprint());
		buf.append(" return ");
		buf.append(returnExpr.pprint());
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

	private final static void setContext(Sequence seq) {
		Item next;
		for(SequenceIterator i = seq.iterate(); i.hasNext(); ) {
			next = i.nextItem();
			if(next instanceof NodeProxy)
				((NodeProxy)next).addContextNode((NodeProxy)next);
		}
	}
}
