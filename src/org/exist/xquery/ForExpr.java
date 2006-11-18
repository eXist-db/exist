/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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

import org.exist.dom.*;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.OrderedValueSequence;
import org.exist.xquery.value.PreorderedValueSequence;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.storage.UpdateListener;
import org.exist.numbering.NodeId;

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
    public void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[]) throws XPathException {
        // Save the local variable stack
		LocalVariable mark = context.markLocalVariables(false);
		
		contextInfo.setParent(this);
		inputSequence.analyze(contextInfo);
		
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
			AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
			newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
			newContextInfo.setContextId(getExpressionId());
		    whereExpr.analyze(newContextInfo);
		}
		// the order by specs should be analyzed by the last binding expression
		// in the chain to have access to all variables. So if the return expression
		// is another binding expression, we just forward the order specs.
		if(returnExpr instanceof BindingExpression) {
			AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
			newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
		    ((BindingExpression)returnExpr).analyze(newContextInfo, orderBy);
		} else {
			AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
			newContextInfo.addFlag(SINGLE_STEP_EXECUTION);
		    // analyze the order specs
			if(orderBy != null) {
			    for(int i = 0; i < orderBy.length; i++)
			        orderBy[i].analyze(newContextInfo);
			}
			returnExpr.analyze(newContextInfo);
		}
		
		// restore the local variable stack
		context.popLocalVariables(mark);
    }
    
	/**
	 * This implementation tries to process the "where" clause in advance, i.e. in one single
	 * step. This is possible if the input sequence is a node set and the where expression
	 * has no dependencies on other variables than those declared in this "for" statement.
	 * 
	 * @see org.exist.xquery.Expression#eval(Sequence, Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem, Sequence resultSequence)
        throws XPathException {

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            if (resultSequence != null)        
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "RESULT SEQUENCE", resultSequence);
        }
        
		// Save the local variable stack
		LocalVariable mark = context.markLocalVariables(false);
		
		// Evaluate the "in" expression
		Sequence in = inputSequence.eval(contextSequence, null);
        clearContext(getExpressionId(), in);
        
        // Declare the iteration variable
		LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
        var.setSequenceType(sequenceType);
		context.declareVariableBinding(var);      

        registerUpdateListener(in);
        
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
		} else
			var.setContextDocs(null);

		// Check if we can speed up the processing of the "order by" clause.
		boolean fastOrderBy = false; // checkOrderSpecs(in);
		
		// See if we can process the "where" clause in a single step (instead of
		// calling the where expression for each item in the input sequence)
		// This is possible if the input sequence is a node set and has no
		// dependencies on the current context item.
		boolean fastExec = 
			whereExpr != null && at == null &&
			!Dependency.dependsOn(whereExpr, Dependency.CONTEXT_ITEM) &&
			Type.subTypeOf(in.getItemType(), Type.NODE);
		
		// If possible, apply the where expression ahead of the iteration
		if(fastExec) {
			if(!in.isCached())
				setContext(getExpressionId(), in);
			in = applyWhereExpression(in);
			if(!in.isCached())
				clearContext(getExpressionId(), in);
		}
		
		// PreorderedValueSequence applies the order specs to all items
		// in one single processing step
		if(fastOrderBy) {
			in = new PreorderedValueSequence(orderSpecs, in, getExpressionId());
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

		Sequence val = null;
		int p = 1;
		IntegerValue atVal = new IntegerValue(1);
		if(positionalVariable != null)
			at.setValue(atVal);
		
		// Loop through each variable binding
		p = 0;
		for (SequenceIterator i = in.iterate(); i.hasNext(); p++) {
		    context.proceed(this);
			contextItem = i.nextItem();
			context.setContextPosition(p);
			
//			atVal.setValue(p); // seb: this does not create a new Value. the old Value is referenced from results
			if(positionalVariable != null)
				at.setValue(new IntegerValue(p + 1));
			 
			contextSequence = contextItem.toSequence();

			// set variable value to current item
			var.setValue(contextSequence);
           var.checkType();
			val = contextSequence;
			
			// check optional where clause
			if (whereExpr != null && (!fastExec)) {
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).addContextNode(getExpressionId(), (NodeProxy)contextItem);
				Sequence bool = applyWhereExpression(null);
				if(contextItem instanceof NodeProxy)
					((NodeProxy)contextItem).clearContext(getExpressionId());
				// if where returned false, continue
				if(!bool.effectiveBooleanValue())
					continue;
			} else
				val = contextItem.toSequence();
            
            //Reset the context position
            context.setContextPosition(0);
			
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
		
        clearContext(getExpressionId(), in);

		// restore the local variable stack
		context.popLocalVariables(mark);
		
        if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", resultSequence);

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
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            dumper.display(" ", returnExpr.getASTNode());
        else
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
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            result.append(" ");  
        else
            result.append("return ");        
        result.append(returnExpr.toString());
        return result.toString();
    }

    /* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.ITEM;
	}

    /* (non-Javadoc)
    * @see org.exist.xquery.AbstractExpression#resetState()
    */
    public void resetState() {
        super.resetState();
    }
}