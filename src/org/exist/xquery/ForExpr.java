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

import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Represents an XQuery "for" expression.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 */
public class ForExpr extends BindingExpression {

	private String positionalVariable = null;
	
	public ForExpr(XQueryContext context) {
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
		Item contextItem,
		Sequence resultSequence)
		throws XPathException {
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
		// assign to the bound variable
		var.setValue(in);
		if(in instanceof NodeSet) {
		    DocumentSet contextDocs = ((NodeSet)in).getDocumentSet();
		    var.setContextDocs(contextDocs);
		}
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
			in = applyWhereExpression(in);
		}
		// if there's an order by clause, wrap the result into
		// an OrderedValueSequence
		if(resultSequence == null) {
			if(orderSpecs != null)
				resultSequence = 
					new OrderedValueSequence(orderSpecs, in.getLength());
			else
				resultSequence = new ValueSequence();
		}
			
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
			val = contextSequence;
			// check optional where clause
			if (whereExpr != null && (!fastExec)) {
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).addContextNode((NodeProxy)contextItem);
				Sequence bool = applyWhereExpression(null);
				// if where returned false, continue
				if(!bool.effectiveBooleanValue())
					continue;
			} else
				val = contextItem.toSequence();
			
			/* if the returnExpr is another BindingExpression, call it
			 * with the result sequence.
			 */
			if(returnExpr instanceof BindingExpression)
				((BindingExpression)returnExpr).eval(null, null, resultSequence);
			
			// otherwise call the return expression and add results to resultSequence
			else {
				val = returnExpr.eval(null);
				resultSequence.addAll(val);
			}
		}
		if(orderSpecs != null)
			((OrderedValueSequence)resultSequence).sort();
		context.popLocalContext();
		return resultSequence;
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#pprint()
	 */
	public String pprint() {
		StringBuffer buf = new StringBuffer();
		buf.append("(for ");
		buf.append(varName);
		if(sequenceType != null) {
			buf.append(" as ");
			buf.append(sequenceType.toString());
		}
		buf.append(" in ");
		buf.append(inputSequence.pprint());
		if (whereExpr != null)
			buf.append(" where ").append(whereExpr.pprint());
		if (orderSpecs != null) {
			buf.append(" order by ");
			for(int i = 0; i < orderSpecs.length; i++) {
				buf.append(orderSpecs[i].toString());
			}
		}
		buf.append(" return ");
		buf.append(returnExpr.pprint());
		buf.append(')');
		return buf.toString();
	}

	/* (non-Javadoc)
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}
}
