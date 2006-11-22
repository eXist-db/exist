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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.GroupedValueSequenceList; 
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
	
	private final int mode;
	
	/**
	 * @param context
	 */
	public QuantifiedExpression(XQueryContext context, int mode) {
		super(context);
		switch (mode) {
		case SOME:
		case EVERY:
			this.mode = mode;
			break;
		default:
			throw new IllegalArgumentException("QuantifiedExpression");
		}		
	}

    /* (non-Javadoc)
     * @see org.exist.xquery.BindingExpression#analyze(org.exist.xquery.Expression, int, org.exist.xquery.OrderSpec[])
     */
    public void analyze(AnalyzeContextInfo contextInfo, OrderSpec orderBy[]) throws XPathException {
        LocalVariable mark = context.markLocalVariables(false);
		context.declareVariableBinding(new LocalVariable(QName.parse(context, varName, null)));
		
		contextInfo.setParent(this);
		inputSequence.analyze(contextInfo);
		returnExpr.analyze(contextInfo);
		
		context.popLocalVariables(mark);
    }
    
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
        
        if (contextItem != null)
            contextSequence = contextItem.toSequence();
        
		LocalVariable mark = context.markLocalVariables(false);
		LocalVariable var = new LocalVariable(QName.parse(context, varName, null));
		context.declareVariableBinding(var);
        
		Sequence inSeq = inputSequence.eval(contextSequence);
		Sequence satisfiesSeq;
		boolean found = (mode == EVERY) ? true : false;
		for (SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
			contextItem = i.nextItem();
			var.setValue(contextItem.toSequence());
            var.checkType();
			satisfiesSeq = returnExpr.eval(contextSequence);
			found = satisfiesSeq.effectiveBooleanValue();
			if ((mode == SOME ) && found)
				break;
			if ((mode == EVERY) && !found)
				break;
		}
        
		context.popLocalVariables(mark);
        
		Sequence result = found ? BooleanValue.TRUE : BooleanValue.FALSE;
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result);
        
        return result;        
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
    	dumper.display(mode == SOME ? "some" : "every");
        dumper.display(" $").display(varName).display(" in");
        dumper.startIndent();
        inputSequence.dump(dumper);
        dumper.endIndent().nl();
        dumper.display("satisfies");
        dumper.startIndent();
        returnExpr.dump(dumper);
        dumper.endIndent();
    }
    
    public String toString() {
    	StringBuffer result = new StringBuffer();
    	result.append(mode == SOME ? "some" : "every");
    	result.append(" $").append(varName).append(" in");
    	result.append(" ");
    	result.append(inputSequence.toString());
    	result.append(" ");
    	result.append("satisfies");
    	result.append(" ");
    	result.append(returnExpr.toString());
    	result.append(" ");
    	return result.toString();
    }    
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_ITEM | Dependency.CONTEXT_SET;
	}

}
