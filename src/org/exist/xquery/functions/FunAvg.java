/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
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
package org.exist.xquery.functions;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class FunAvg extends Function {
	protected static final Logger logger = Logger.getLogger(FunAvg.class);	
	//Used to detect overflows : currently not used.
	private boolean gotInfinity = false;

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("avg", Function.BUILTIN_FUNCTION_NS),
			"Returns the average of the values in the input sequence $values, that is, the "
				+ "sum of the values divided by the number of values.",
			new SequenceType[] {
                new FunctionParameterSequenceType("values", Type.ATOMIC, Cardinality.ZERO_OR_MORE, "The values")},
			new FunctionReturnSequenceType(Type.ATOMIC, Cardinality.ZERO_OR_ONE, "the average of the values in the input sequence"));

	/**
	 * @param context
	 */
	public FunAvg(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
	 */
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }
        
        Sequence result;
		Sequence inner = getArgument(0).eval(contextSequence, contextItem);      
		if (inner.isEmpty())
            result = Sequence.EMPTY_SEQUENCE;
        else {
    		SequenceIterator iter = inner.iterate();
    		Item item = iter.nextItem();
    		AtomicValue value = item.atomize();
            //Any values of type xdt:untypedAtomic in the sequence $arg are cast to xs:double
            if (value.getType() == Type.UNTYPED_ATOMIC) 
            	value = value.convertTo(Type.DOUBLE);
    		if (!(value instanceof ComputableValue)) {
                logger.error("XPTY0004: '" + Type.getTypeName(value.getType()) + "(" + value + ")' can not be an operand in a sum");
				throw new XPathException(this, "XPTY0004: '" + Type.getTypeName(value.getType()) + "(" + value + ")' can not be an operand in a sum");
            }
    		//Set the first value
    		ComputableValue sum = (ComputableValue) value;
    		while (iter.hasNext()) {
    			item = iter.nextItem();
    			value = item.atomize();
                //Any values of type xdt:untypedAtomic in the sequence $arg are cast to xs:double
                if (value.getType() == Type.UNTYPED_ATOMIC) 
                	value = value.convertTo(Type.DOUBLE);
        		if (!(value instanceof ComputableValue)) {
                    logger.error("XPTY0004: '" + Type.getTypeName(value.getType()) + "(" + value + ")' can not be an operand in a sum");
    				throw new XPathException(this, "XPTY0004: '" + Type.getTypeName(value.getType()) + "(" + value + ")' can not be an operand in a sum");
                }
    			if (Type.subTypeOf(value.getType(), Type.NUMBER)) {
    				if (((NumericValue)value).isInfinite())
    					gotInfinity = true;    					
    				if (((NumericValue)value).isNaN()) {
    					sum = DoubleValue.NaN;
    					break;
    				}
    			}
    			try {
    				sum = (ComputableValue) sum.promote(value);
	    			//Aggregate next values	    	
    				sum = sum.plus((ComputableValue) value);
				} catch(XPathException e) {
                    logger.error("FORG0006: " + e.getMessage());
					throw new XPathException(this, "FORG0006: " + e.getMessage(), e);    					
				}
    		}
    		result = sum.div(new IntegerValue(inner.getItemCount()));
        }
        
		if (!gotInfinity) {
			if (Type.subTypeOf(result.getItemType(), Type.NUMBER) && ((NumericValue)result).isInfinite()) {
				//Throw an overflow eception here since we get an infinity 
				//whereas is hasn't been provided by the sequence
			}
		}

		if (context.getProfiler().isEnabled())
            context.getProfiler().end(this, "", result);  
        
        return result;        
	}
}
