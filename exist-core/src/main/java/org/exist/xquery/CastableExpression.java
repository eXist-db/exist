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
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * Implements the "castable as" XQuery expression.
 * 
 * @author wolf
 */
public class CastableExpression extends AbstractExpression {

	private final Expression expression;
	private final Cardinality requiredCardinality;
	private final int requiredType;

	/**
     * Wrap a CastableExpression around an expression, expecting the given type and
	 * cardinality.
     * 
     * @param requiredCardinality the {@link Cardinality} expected
     * @param context current context
     * @param expr the expression to be wrapped
     * @param requiredType the {@link Type} expected
     */
	public CastableExpression(final XQueryContext context, final Expression expr, final int requiredType,
			final Cardinality requiredCardinality) {
		super(context);
		this.expression = expr;
		this.requiredType = requiredType;
		this.requiredCardinality = requiredCardinality;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.CastExpression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	@Override
	public Cardinality getCardinality() {
		return Cardinality.EXACTLY_ONE;
	}
	
	public int getDependencies() {
		return Dependency.CONTEXT_SET + Dependency.CONTEXT_ITEM;
	}
	
	/* (non-Javadoc)
     * @see org.exist.xquery.Expression#analyze(org.exist.xquery.Expression)
     */
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    	contextInfo.setParent(this);
        expression.analyze(contextInfo);
    }
    
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#eval(org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        if (requiredType == Type.ANY_ATOMIC_TYPE || (requiredType == Type.NOTATION && expression.returnsType() != Type.NOTATION))
            {throw new XPathException(this, ErrorCodes.XPST0080, "cannot convert to " + Type.getTypeName(requiredType));}

        if (requiredType == Type.ANY_SIMPLE_TYPE || expression.returnsType() == Type.ANY_SIMPLE_TYPE || requiredType == Type.UNTYPED || expression.returnsType() == Type.UNTYPED)
            {throw new XPathException(this, ErrorCodes.XPST0051, "cannot convert to " + Type.getTypeName(requiredType));}

        Sequence result;
        //See : http://article.gmane.org/gmane.text.xml.xquery.general/1413
        //... for the rationale
        //may be more complicated : let's see with following XQTS versions
        if (requiredType == Type.QNAME && Dependency.dependsOnVar(expression))
        	{result = BooleanValue.FALSE;}
        else {
			final Sequence seq = Atomize.atomize(expression.eval(contextSequence, contextItem));
			if(seq.isEmpty()) {
				//If ? is specified after the target type, the result of the cast expression is an empty sequence.
				if (requiredCardinality.isSuperCardinalityOrEqualOf(Cardinality.EMPTY_SEQUENCE))
	                {result = BooleanValue.TRUE;}
				//If ? is not specified after the target type, a type error is raised [err:XPTY0004].
				else
					//TODO : raise the error ?
	                {result = BooleanValue.FALSE;}
			}
	        else {
	    		try {
	    			seq.itemAt(0).convertTo(requiredType);
	    			//If ? is specified after the target type, the result of the cast expression is an empty sequence.
	    			if (requiredCardinality.isSuperCardinalityOrEqualOf(seq.getCardinality()))
	    				{result = BooleanValue.TRUE;}
	    			//If ? is not specified after the target type, a type error is raised [err:XPTY0004].
	    			else
	    				{result = BooleanValue.FALSE;}
	            //TODO : improve by *not* using a costly exception ?
	    		} catch(final XPathException e) {
	                result = BooleanValue.FALSE;
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
        dumper.display(" castable as ");
        dumper.display(Type.getTypeName(requiredType));
    }
    
    public String toString() {
		return expression.toString() + " castable as " + Type.getTypeName(requiredType);
    }
    
    public void setContextDocSet(DocumentSet contextSet) {
		super.setContextDocSet(contextSet);
		expression.setContextDocSet(contextSet);
	}
    
	public void resetState(boolean postOptimization) {
		super.resetState(postOptimization);
		expression.resetState(postOptimization);
	}
}
