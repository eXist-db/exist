/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.exist.dom.QName;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import java.util.HashSet;
import java.util.Set;

/**
 * Represents a quantified expression: "some ... in ... satisfies", 
 * "every ... in ... satisfies".
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class QuantifiedExpression extends BindingExpression {
	
	public final static int SOME = 0;
	public final static int EVERY = 1;
	
	private final int mode;

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

    @Override
    public ClauseType getType() {
        return mode == SOME ? ClauseType.SOME : ClauseType.EVERY;
    }

    @Override
	public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
		final LocalVariable mark = context.markLocalVariables(false);
		try {
			context.declareVariableBinding(new LocalVariable(varName));

			contextInfo.setParent(this);
			inputSequence.analyze(contextInfo);
			returnExpr.analyze(contextInfo);
		} finally {
			context.popLocalVariables(mark);
		}
	}

	public Sequence eval(Sequence contextSequence, Item contextItem)
        throws XPathException {
        
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }        
        
		final LocalVariable var = new LocalVariable(varName);
        
		final Sequence inSeq = inputSequence.eval(contextSequence, contextItem);
        if (sequenceType != null) {
        	//Type.EMPTY is *not* a subtype of other types ; the tests below would fail without this prior cardinality check
        	if (!inSeq.isEmpty() && !Type.subTypeOf(inSeq.getItemType(), sequenceType.getPrimaryType()))
				{throw new XPathException(this, ErrorCodes.XPTY0004, "Invalid type for variable $" + varName +
						". Expected " +
						Type.getTypeName(sequenceType.getPrimaryType()) +
						", got " +Type.getTypeName(inSeq.getItemType()), inSeq);}
        }	

		boolean found = (mode == EVERY) ? true : false;
		boolean canDecide = (mode == EVERY) ? true : false;

		for (final SequenceIterator i = inSeq.iterate(); i.hasNext(); ) {
			canDecide = true;
			
			final Item item = i.nextItem();			
			// set variable value to current item
            var.setValue(item.toSequence());
            if (sequenceType == null) 
                {var.checkType();} //... because is makes some conversions
			
            Sequence satisfiesSeq = null;
            
            //Binds the variable : now in scope
    		final LocalVariable mark = context.markLocalVariables(false);
    		try {
    			context.declareVariableBinding(var);
    			//Evaluate the return clause for the current value of the variable
    			satisfiesSeq = returnExpr.eval(contextSequence, contextItem);
    		} finally {
    			//Unbind the variable until the next iteration : now out of scope
    			context.popLocalVariables(mark, satisfiesSeq);
    		}
            
            if (sequenceType != null) {
        		//TODO : ignore nodes right now ; they are returned as xs:untypedAtomicType
        		if (!Type.subTypeOf(sequenceType.getPrimaryType(), Type.NODE)) {
	            	if (!Type.subTypeOf(item.toSequence().getItemType(), sequenceType.getPrimaryType()))
	    				{throw new XPathException(this, ErrorCodes.XPTY0004, "Invalid type for variable $" + varName +
	    						". Expected " +
	    						Type.getTypeName(sequenceType.getPrimaryType()) +
	    						", got " +Type.getTypeName(contextItem.toSequence().getItemType()), inSeq);}
            	} else if (!Type.subTypeOf(item.getType(), Type.NODE))
    				{throw new XPathException(this, ErrorCodes.XPTY0004, "Invalid type for variable $" + varName +
    						". Expected " +
    						Type.getTypeName(Type.NODE) +
    						" (or more specific), got " + Type.getTypeName(item.getType()), inSeq);}
    	    	//trigger the old behaviour
        		else {var.checkType();}
            }	
            
			found = satisfiesSeq.effectiveBooleanValue();
			if ((mode == SOME ) && found)
				{break;}
			if ((mode == EVERY) && !found)
				{break;}
		}
        
		final Sequence result = canDecide && found ? BooleanValue.TRUE : BooleanValue.FALSE;
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
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
    	final StringBuilder result = new StringBuilder();
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

	@Override
	public Set<QName> getTupleStreamVariables() {
		final Set<QName> variables = new HashSet<>();

		final QName variable = getVariable();
		if (variable != null) {
			variables.add(variable);
		}

		final LocalVariable startVar = getStartVariable();
		if (startVar != null) {
			variables.add(startVar.getQName());
		}

		return variables;
	}

}
