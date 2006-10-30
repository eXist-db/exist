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

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
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
	 * @see org.exist.xquery.BindingExpression#analyze(org.exist.xquery.Expression, int, org.exist.xquery.OrderSpec[])
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
		
		if(whereExpr != null) {
			AnalyzeContextInfo newContextInfo = new AnalyzeContextInfo(contextInfo);
			newContextInfo.setFlags(contextInfo.getFlags() | IN_PREDICATE | IN_WHERE_CLAUSE);
		    whereExpr.analyze(newContextInfo);
		}
        
        //Reset the context position
        context.setContextPosition(0);
        
		if(returnExpr instanceof BindingExpression) {
		    ((BindingExpression)returnExpr).analyze(contextInfo, orderBy);
		} else {
			if(orderBy != null) {
			    for(int i = 0; i < orderBy.length; i++)
			        orderBy[i].analyze(contextInfo);
			}
			returnExpr.analyze(contextInfo);
		}
		
		// restore the local variable stack
		context.popLocalVariables(mark);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.xquery.StaticContext, org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
        context.pushDocumentContext();
        try {
            // Save the local variable stack
            LocalVariable mark = context.markLocalVariables(false);
            
            // evaluate input sequence
            Sequence in = inputSequence.eval(contextSequence, null);
            clearContext(getExpressionId(), in);
            
            // Declare the iteration variable
            LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
            var.setSequenceType(sequenceType);
            context.declareVariableBinding(var);        
            var.setValue(in);
            var.setContextDocs(inputSequence.getContextDocSet());
            var.checkType();
            
            if (whereExpr != null) {
                Sequence filtered = applyWhereExpression(null);
                // TODO: don't use returnsType here
                if (filtered.isEmpty()) {
                    if (context.getProfiler().isEnabled())
                        context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);  
                    return Sequence.EMPTY_SEQUENCE; 
                } else if (filtered.getItemType() == Type.BOOLEAN &&
                           !filtered.effectiveBooleanValue()) {
                    if (context.getProfiler().isEnabled())
                        context.getProfiler().end(this, "", Sequence.EMPTY_SEQUENCE);                 
                    return Sequence.EMPTY_SEQUENCE;
                }  
            }        
            
            // Check if we can speed up the processing of the "order by" clause.
            boolean fastOrderBy = checkOrderSpecs(in);
    
            //  PreorderedValueSequence applies the order specs to all items
            // in one single processing step
            if(fastOrderBy) {
                in = new PreorderedValueSequence(orderSpecs, in.toNodeSet(), getExpressionId());
            }
    
            // Otherwise, if there's an order by clause, wrap the result into
            // an OrderedValueSequence. OrderedValueSequence will compute
            // order expressions for every item when it is added to the result sequence.
            if(resultSequence == null) {            
                if(orderSpecs != null && !fastOrderBy)
                    resultSequence = new OrderedValueSequence(orderSpecs, in.getLength());
            }
    
            if(returnExpr instanceof BindingExpression) {
            	if (resultSequence == null)
            		resultSequence = new ValueSequence();
                ((BindingExpression)returnExpr).eval(null, null, resultSequence);
            } else {
                in = returnExpr.eval(null);
                if (resultSequence == null)
                	resultSequence = in;
                else
                	resultSequence.addAll(in);
            }
    
            if(orderSpecs != null && !fastOrderBy)
                ((OrderedValueSequence)resultSequence).sort();
    
            clearContext(getExpressionId(), in);
            
            // Restore the local variable stack
            context.popLocalVariables(mark);
           
            if (context.getProfiler().isEnabled())
                context.getProfiler().end(this, "", resultSequence);
            
    		return resultSequence;
        } finally {
            context.popDocumentContext();
        }
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        dumper.display("let ", getASTNode());
        dumper.startIndent();
        dumper.display("$").display(varName);
        dumper.display(" := ");
        inputSequence.dump(dumper);
        dumper.endIndent();
        if(whereExpr != null) {
            dumper.nl().display("where ");
            whereExpr.dump(dumper);
        }
        if(orderSpecs != null) {
            dumper.nl().display("order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                    dumper.display(", ");
                //TODO : toString() or... dump ?
                dumper.display(orderSpecs[i].toString());
            }
        }
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            dumper.display(", ");
        else
            dumper.nl().display("return "); 
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append("let ");        
    	result.append("$").append(varName);
        result.append(" := ");
    	result.append(inputSequence.toString());   
        result.append(" ");
        if(whereExpr != null) {
        	result.append(" where ");
        	result.append(whereExpr.toString());
        }
        if(orderSpecs != null) {
        	result.append(" order by ");
            for(int i = 0; i < orderSpecs.length; i++) {
                if(i > 0)
                	result.append(", ");
                result.append(orderSpecs[i].toString());
            }
        }
        //TODO : QuantifiedExpr
        if (returnExpr instanceof LetExpr)
            result.append(", ");
        else
            result.append("return ");       
        result.append(returnExpr.toString());
        return result.toString();
    }    
}
