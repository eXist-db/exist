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
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

// import sun.security.action.GetLongAction;

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

	/**
	 * A "for" expression may have an optional positional variable whose
	 * QName can be set via this method.
	 * 
	 * @param var
	 */
	public void setPositionalVariable(String var) {
		positionalVariable = var;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(Expression parent, int flags, OrderSpec orderBy[]) throws XPathException {
        // Save the local variable stack
		LocalVariable mark = context.markLocalVariables(false);
		
		inputSequence.analyze(this, flags);
		
		// Declare the iteration variable
        LocalVariable inVar = new LocalVariable(QName.parse(context, varName, null));
        inVar.setSequenceType(sequenceType);
		context.declareVariableBinding(inVar);
		
		// Declare positional variable
		if(positionalVariable != null) {
			LocalVariable posVar = new LocalVariable(QName.parse(context, positionalVariable, null));
            posVar.setSequenceType(POSITIONAL_VAR_TYPE);
            context.declareVariableBinding(posVar);
        }
        
		if(whereExpr != null) {
		    whereExpr.analyze(this, flags | IN_PREDICATE | IN_WHERE_CLAUSE);
		}
		// the order by specs should be analyzed by the last binding expression
		// in the chain to have access to all variables. So if the return expression
		// is another binding expression, we just forward the order specs.
		if(returnExpr instanceof BindingExpression) {
		    ((BindingExpression)returnExpr).analyze(this, flags | SINGLE_STEP_EXECUTION, orderBy);
		} else {
		    // analyze the order specs
			if(orderBy != null) {
			    for(int i = 0; i < orderBy.length; i++)
			        orderBy[i].analyze(this, flags | SINGLE_STEP_EXECUTION);
			}
			returnExpr.analyze(this, flags);
		}
		
		// restore the local variable stack
		context.popLocalVariables(mark);
    }
    
	/**
	 * This implementation tries to process the "where" clause in advance, i.e. in one single
	 * step. This is possible if the input sequence is a node set and the where expression
	 * has no dependencies on other variables than those declared in this "for" statement.
	 * 
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem,
		Sequence resultSequence)
		throws XPathException {

        context.getProfiler().start(this, "for expression: " + 
        		// " line " +getASTNode().getLine() +
        		ExpressionDumper.dump(this) );

		// Save the local variable stack
		LocalVariable mark = context.markLocalVariables(false);
		
		// Evaluate the "in" expression
		Sequence in = inputSequence.eval(null, null);
		clearContext(in);
        
		// Declare the iteration variable
		LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
        var.setSequenceType(sequenceType);
		context.declareVariableBinding(var);
		
		// Declare positional variable
		LocalVariable at = null;
		if(positionalVariable != null) {
			at = new LocalVariable(QName.parse(context, positionalVariable, null));
            at.setSequenceType(POSITIONAL_VAR_TYPE);
			context.declareVariableBinding(at);
		}
		
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
		
		// See if we can process the "where" clause in a single step (instead of
		// calling the where expression for each item in the input sequence)
		// This is possible if the input sequence is a node set and has no
		// dependencies on the current context item.
		boolean fastExec = whereExpr != null &&
			( whereExpr.getDependencies() & Dependency.CONTEXT_ITEM ) == 0 &&
			at == null &&
			Type.subTypeOf( in.getItemType() , Type.NODE);
		
		// If possible, apply the where expression ahead of the iteration
		if(fastExec) {
			if(!in.isCached())
				setContext(in);
			in = applyWhereExpression(in);
			if(!in.isCached())
				clearContext(in);
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

			// set variable value to current item
			var.setValue(contextSequence);
            var.checkType();
			val = contextSequence;
			
			// check optional where clause
			if (whereExpr != null && (!fastExec)) {
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).addContextNode((NodeProxy)contextItem);
				Sequence bool = applyWhereExpression(null);
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).clearContext();
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
		
        context.getProfiler().end(  this, "for expression: " + this );

		return resultSequence;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("for ", getASTNode());
        dumper.startIndent();
        dumper.display("$").display(varName);
        if(positionalVariable != null)
            dumper.display(" at ").display(positionalVariable);
        if(sequenceType != null)
            dumper.display(" as ").display(sequenceType);
        dumper.display(" in ");
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        if(whereExpr != null) {
            dumper.display("where", whereExpr.getASTNode());
            dumper.startIndent();
            whereExpr.dump(dumper);
            dumper.endIndent().nl();
        }
        if(orderSpecs != null) {
            dumper.display("order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                    dumper.display(", ");
                dumper.display(orderSpecs[i]);
            }
            dumper.nl();
        }
        dumper.display("return", returnExpr.getASTNode());
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent().nl();
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("for ");           
        result.append("$").append(varName);
        if(positionalVariable != null)
        	result.append(" at ").append(positionalVariable);
        if(sequenceType != null)
        	result.append(" as ").append(sequenceType);
        result.append(" in ");
        result.append(inputSequence.toString());
        result.append(" ");
        if(whereExpr != null) {
        	result.append("where");
        	result.append(" ");
        	result.append(whereExpr.toString());
        	result.append(" ");
        }
        if(orderSpecs != null) {
        	result.append("order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                	result.append(", ");
                result.append(orderSpecs[i].toString());
            }
            result.append(" ");
        }
        result.append("return");
        result.append(" ");
        result.append(returnExpr.toString());
        result.append("");
        return result.toString();
    }
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}
}
