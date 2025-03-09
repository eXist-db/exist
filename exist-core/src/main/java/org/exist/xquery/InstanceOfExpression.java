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

import org.exist.dom.persistent.DocumentSet;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.*;

/**
 * Implements the XQuery "instance of" operator.
 * 
 * @author wolf
 */
public class InstanceOfExpression extends AbstractExpression {

	private final Expression expression;
	private final SequenceType type;

	public InstanceOfExpression(XQueryContext context, Expression expr, SequenceType type) {
		super(context);
		this.expression = expr;
		this.type = type;
	}

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }
    
	@Override
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        Sequence result = BooleanValue.TRUE;
		final Sequence seq = expression.eval(contextSequence, contextItem);
        
		final Cardinality requiredCardinality = type.getCardinality();
		if (!seq.isEmpty() && requiredCardinality == Cardinality.EMPTY_SEQUENCE)
            {result = BooleanValue.FALSE;}
        else if (seq.isEmpty() && requiredCardinality.atLeastOne())
            {result = BooleanValue.FALSE;}
		else if (seq.hasMany() && requiredCardinality.atMostOne())
            {result = BooleanValue.FALSE;}
        else {
    		for(final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
    			final Item next = i.nextItem();
    			if(!type.checkType(next)) {
                    result = BooleanValue.FALSE;   
                    break;
                }
    				
    		}
        }		
        
        if (context.getProfiler().isEnabled())           
            {context.getProfiler().end(this, "", result);}    
        
        return result;
	}

	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#dump(org.exist.xquery.util.ExpressionDumper)
     */
    public void dump(ExpressionDumper dumper) {
        expression.dump(dumper);
        dumper.display(" instance of ");
        dumper.display(type.toString());
    }
    
    public String toString() {
		return expression + " instance of " + type.toString();
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}

	@Override
	public Cardinality getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getDependencies()
	 */
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#resetState()
	 */
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
	}

	public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		expression.setContextDocSet(contextSet);
	}
}
