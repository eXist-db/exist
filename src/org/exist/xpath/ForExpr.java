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

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xpath.value.IntegerValue;
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

	private String positionalVariable = null;
	
	public ForExpr(StaticContext context) {
		super(context);
	}

	public void setPositionalVariable(String var) {
		positionalVariable = var;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#eval(org.exist.xpath.StaticContext, org.exist.dom.DocumentSet, org.exist.xpath.value.Sequence, org.exist.xpath.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		Sequence result = null;
		context.pushLocalContext(false);
		// declare the variable
		Variable var = new Variable(QName.parse(context, varName));
		context.declareVariable(var);
		
		// declare positional variable
		Variable at = null;
		if(positionalVariable != null) {
			at = new Variable(QName.parse(context, positionalVariable));
			context.declareVariable(at);
		}
		
		// evaluate the "in" expression
		Sequence in = inputSequence.eval(null, null);
		var.setValue(in);
		if(whereExpr != null) {
			whereExpr.setInPredicate(true);
			setContext(in);
		}
		boolean fastExec = whereExpr != null &&
			( whereExpr.getDependencies() & Dependency.CONTEXT_ITEM ) == 0 &&
			at == null &&
			in.getItemType() == Type.NODE;
			
		if(fastExec) {
			LOG.debug("fast evaluation mode");
			in = applyWhereExpression(null);
		}
		
		if(orderSpecs != null)
			result = 
				new OrderedValueSequence(orderSpecs, in.getLength());
		else
			result = new ValueSequence();
			
		Sequence val = null;
		int p = 1;
		IntegerValue atVal = new IntegerValue(1);
		if(positionalVariable != null)
			at.setValue(atVal);
		// loop through each variable binding
		for (SequenceIterator i = in.iterate(); i.hasNext(); p++) {
			contextItem = i.nextItem();
			context.setContextPosition(p);
			atVal.setValue(p);

			contextSequence = contextItem.toSequence();
			if(sequenceType != null)
				// check sequence type
				sequenceType.checkType(contextItem.getType());
			// set variable value to current item
			var.setValue(contextSequence);
			// check optional where clause
			if (whereExpr != null && (!fastExec)) {
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).addContextNode((NodeProxy)contextItem);
				val = applyWhereExpression(contextSequence);
				if(!val.effectiveBooleanValue())
					continue;
			} else
				val = contextItem.toSequence();
			
			// if there is no "order by" clause, immediately call
			// the return clause
			if(orderSpecs == null)
				val = returnExpr.eval( val);
			result.addAll(val);
		}
		if(orderSpecs != null) {
			// sort the result and call return for every item
			((OrderedValueSequence)result).sort();
			Sequence orderedResult = new ValueSequence();
			p = 1;
			for(SequenceIterator i = result.iterate(); i.hasNext(); p++) {
				contextItem = i.nextItem();
				contextSequence = contextItem.toSequence();
				// set variable value to current item
				var.setValue(contextSequence);
				context.setContextPosition(p);
				val = returnExpr.eval(contextSequence);
				orderedResult.addAll(val);
			}
			result = orderedResult;
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
		if(sequenceType != null) {
			buf.append(" as ");
			buf.append(sequenceType.toString());
		}
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
}
