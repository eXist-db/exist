/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery;

import org.exist.dom.DocumentSet;
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

	private Expression expression;	
	private int requiredCardinality;
	private final int requiredType;
	
	/**
     * 
     * 
     * @param requiredCardinality 
     * @param context 
     * @param expr 
     * @param requiredType 
     */
	public CastableExpression(XQueryContext context, Expression expr, int requiredType, int requiredCardinality) {
		super(context);
		this.expression = expr;
		this.requiredType = requiredType;
		this.requiredCardinality = requiredCardinality;
		if (!Type.subTypeOf(expression.returnsType(), Type.ATOMIC))
			{expression = new Atomize(context, expression);}
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.CastExpression#returnsType()
	 */
	public int returnsType() {
		return Type.BOOLEAN;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.AbstractExpression#getCardinality()
	 */
	public int getCardinality() {
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
        
        if (requiredType == Type.ATOMIC || (requiredType == Type.NOTATION && expression.returnsType() != Type.NOTATION))
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
			final Sequence seq = expression.eval(contextSequence, contextItem);
			if(seq.isEmpty()) {
				//If ? is specified after the target type, the result of the cast expression is an empty sequence.
				if (Cardinality.checkCardinality(requiredCardinality, Cardinality.ZERO))
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
	    			if (Cardinality.checkCardinality(requiredCardinality, seq.getCardinality()))
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
    	final StringBuilder result = new StringBuilder();
    	result.append(expression.toString());
    	result.append(" castable as ");
    	result.append(Type.getTypeName(requiredType));
    	return result.toString();
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
