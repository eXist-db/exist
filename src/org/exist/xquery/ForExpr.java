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
import org.exist.xquery.value.PreorderedValueSequence;
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
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem,
		Sequence resultSequence)
		throws XPathException {
		// Save the local variable stack
		LocalVariable mark = context.markLocalVariables();
		
		// Declare the iteration variable
		LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
		context.declareVariable(var);
		
		// Declare positional variable
		LocalVariable at = null;
		if(positionalVariable != null) {
			at = new LocalVariable(QName.parse(context, positionalVariable, null));
			context.declareVariable(at);
		}
		
		// Evaluate the "in" expression
		Sequence in = inputSequence.eval(null, null);
		
		// Assign the whole input sequence to the bound variable.
		// This is required if we process the "where" or "order by" clause
		// in one step.
		var.setValue(in);
		
		// Save the current context document set to the variable as a hint
		// for path expressions occurring in the "return" clause.
		if(in instanceof NodeSet) {
		    DocumentSet contextDocs = ((NodeSet)in).getDocumentSet();
		    var.setContextDocs(contextDocs);
		}

		// Check if we can speed up the processing of the "order by" clause.
		boolean fastOrderBy = false; // checkOrderSpecs(in);
		
		if(whereExpr != null) {
			whereExpr.setInPredicate(true);
			if(!(in.isCached() || fastOrderBy))
				setContext(in);
		}
		
		// See if we can process the "where" clause in a single step (instead of
		// calling the where expression for each item in the input sequence)
		// This is possible if the input sequence is a node set and has no
		// dependencies on the current context item.
		boolean fastExec = whereExpr != null &&
			( whereExpr.getDependencies() & Dependency.CONTEXT_ITEM ) == 0 &&
			at == null &&
			in.getItemType() == Type.NODE;
		
		// If possible, apply the where expression ahead of the iteration
		if(fastExec) {
//			LOG.debug("fast evaluation mode");
			in = applyWhereExpression(in);
		}
		
		// PreorderedValueSequence applies the order specs to all items
		// in one single processing step
		if(fastOrderBy) {
			in = new PreorderedValueSequence(orderSpecs, in);
		}
		
		// Otherwise, if there's an order by clause, wrap the result into
		// an OrderedValueSequence. OrderedValueSequence will compute
		// order expressions for every item when it is added to the result sequence.
		if(resultSequence == null) {
			if(orderSpecs != null && !fastOrderBy)
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
		
		// Loop through each variable binding
		for (SequenceIterator i = in.iterate(); i.hasNext(); p++) {
		    context.proceed(this);
			contextItem = i.nextItem();
			context.setContextPosition(p);
			
//			atVal.setValue(p); // seb: this does not create a new Value. the old Value is referenced from results
			if(positionalVariable != null)
				at.setValue(new IntegerValue(p));
			 
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
		if(orderSpecs != null && !fastOrderBy)
			((OrderedValueSequence)resultSequence).sort();
		
		// restore the local variable stack
		context.popLocalVariables(mark);
		return resultSequence;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#pprint()
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
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}
	
	public final static void setContext(Sequence seq) {
		Item next;
		for (SequenceIterator i = seq.unorderedIterator(); i.hasNext();) {
			next = i.nextItem();
			if (next instanceof NodeProxy)
				 ((NodeProxy) next).addContextNode((NodeProxy) next);
		}
	}
}
