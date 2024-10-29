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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.ComputableValue;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.DurationValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunSum extends Function {
	
	//Used to detect overflows : currently not used.
	private boolean gotInfinity = false;

	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName("sum", Function.BUILTIN_FUNCTION_NS),
			"Returns a value obtained by adding together the values in $arg. " +
            "If $arg is the the empty sequence the xs:double value 0.0e0 is returned.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The sequence of numbers to be summed up")},
			new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "the sum of all numbers in $arg")
		),
		new FunctionSignature(
			new QName("sum", Function.BUILTIN_FUNCTION_NS),
			"Returns a value obtained by adding together the values in $arg. " +
            "If $arg is the the empty sequence then $default is returned.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The sequence of numbers to be summed up"),
				 new FunctionParameterSequenceType("default", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "The default value if $arg computes to the empty sequence")
				 },
					new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "the sum of all numbers in $arg")
		)
	};
				
    public FunSum(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
    }

	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES",
                    Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());}
        }
        
        Sequence result;
        
		final Sequence inner = getArgument(0).eval(contextSequence, contextItem);	
		if (inner.isEmpty()) {
			//If $zero is not specified, then the value returned for an empty sequence is the xs:integer value 0
			Sequence zero = IntegerValue.ZERO;
			if(getSignature().getArgumentCount() == 2)
				{zero = getArgument(1).eval(contextSequence, contextItem);}
			result = zero;
		} else {
    		final SequenceIterator iter = inner.iterate();
    		Item item = iter.nextItem();
    		AtomicValue value = item.atomize();

        	value = check(value, null);
    		
    		//Set the first value
    		ComputableValue sum = (ComputableValue) value;
    		while (iter.hasNext()) {
    			item = iter.nextItem();
    			value = item.atomize();

            	value = check(value, sum);
    			
        		if (Type.subTypeOfUnion(value.getType(), Type.NUMERIC)) {
    				if (((NumericValue)value).isInfinite())
    					{gotInfinity = true;}    					
    				if (((NumericValue)value).isNaN()) {
    					sum = DoubleValue.NaN;
    					break;
    				}
    			}
    			sum = (ComputableValue)sum.promote(value);
    			//Aggregate next values
    			sum = sum.plus((ComputableValue) value);
    		}
    		result = sum;
        }
        
		if (!gotInfinity) {
			if (Type.subTypeOfUnion(result.getItemType(), Type.NUMERIC) && ((NumericValue)result).isInfinite()) {
				//Throw an overflow eception here since we get an infinity 
				//whereas is hasn't been provided by the sequence
			}
		}

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}

        return result;        
	}
	
	private AtomicValue check(AtomicValue value, ComputableValue sum) throws XPathException {
		//Duration values must either all be xs:yearMonthDuration values or must all be xs:dayTimeDuration values.
		if (Type.subTypeOf(value.getType(), Type.DURATION)) {
			value = ((DurationValue)value).wrap();
			if (value.getType() == Type.YEAR_MONTH_DURATION) {
            	if (sum != null && sum.getType() != Type.YEAR_MONTH_DURATION)
            		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(sum.getType()) +
            				" and " + Type.getTypeName(value.getType()), value);}
    		
			} else if (value.getType() == Type.DAY_TIME_DURATION) {
            	if (sum != null && sum.getType() != Type.DAY_TIME_DURATION)
            		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(sum.getType()) +
            				" and " + Type.getTypeName(value.getType()), value);}
				
			} else
				{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(value.getType()), value);}

		//Any values of type xdt:untypedAtomic in the sequence $arg are cast to xs:double
		} else if (value.getType() == Type.UNTYPED_ATOMIC) 
        	{value = value.convertTo(Type.DOUBLE);}
		
		if (!(value instanceof ComputableValue))
			{throw new XPathException(this, ErrorCodes.XPTY0004, "" + Type.getTypeName(value.getType()) + "(" + value + ")' can not be an operand in a sum");}

		return value;
	}
}