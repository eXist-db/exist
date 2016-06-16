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
        
        final Sequence item = getExpression(0).eval(contextSequence);
        if (item.isEmpty())
        	{return item;}
        
		NumericValue value = (NumericValue)item.convertTo(Type.NUMBER);
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
