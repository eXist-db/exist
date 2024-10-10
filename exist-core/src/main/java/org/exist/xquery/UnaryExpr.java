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

import org.exist.xquery.Constants.ArithmeticOperator;
import org.exist.xquery.util.ExpressionDumper;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

/**
 * A unary minus or plus.
 * 
 * @author wolf
 */
public class UnaryExpr extends PathExpr {

	private final ArithmeticOperator mode;
	
	public UnaryExpr(XQueryContext context, ArithmeticOperator mode) {
		super(context);
		assert(mode == ArithmeticOperator.ADDITION || mode == ArithmeticOperator.SUBTRACTION);
		this.mode = mode;
	}

	public int returnsType() {
		return Type.DECIMAL;
	}
	
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }
        
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
		if(getLength() == 0)
			{throw new XPathException(this, "unary expression requires an operand");}
        
        Sequence result;
        
        final Sequence item = getExpression(0).eval(contextSequence, null);
        if (item.isEmpty())
        	{return item;}
        
		final NumericValue value;
        if (Type.subTypeOfUnion(item.getItemType(), Type.NUMERIC)) {
        	if (item instanceof NumericValue) {
				value = (NumericValue) item;
			} else {
        		value = (NumericValue) item.convertTo(item.getItemType());
			}
		} else {
        	value = (NumericValue)item.convertTo(Type.NUMERIC);
		}

		if(mode == ArithmeticOperator.SUBTRACTION)
            {result = value.negate();}
		else
            {result =  value;}
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);}
        
        return result;        
	}

	@Override
    public void dump(ExpressionDumper dumper) {    
		dumper.display(mode.symbol);
    }    

	@Override
    public String toString() {
		return mode.symbol;
    }

    @Override
    public Expression simplify() {
    	return this;
    }
}
