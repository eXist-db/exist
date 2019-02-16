/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2009 The eXist Project
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
package org.exist.xquery.functions.fn;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements the fn:roud-half-to-even() function.
 *
 * @author wolf
 *
 */
public class FunRoundHalfToEven extends Function {
	
	protected static final String FUNCTION_DESCRIPTION_1_PARAM = 
        "The value returned is the nearest (that is, numerically closest) " +
		"value to $arg that is a multiple of ten to the power of minus 0. ";
	protected static final String FUNCTION_DESCRIPTION_2_PARAM = 
		"The value returned is the nearest (that is, numerically closest) " +
		"value to $arg that is a multiple of ten to the power of minus " +
		"$precision. ";
    protected static final String FUNCTION_DESCRIPTION_COMMON = 
        "If two such values are equally near (e.g. if the " +
		"fractional part in $arg is exactly .500...), the function returns " +
		"the one whose least significant digit is even.\n\nIf the type of " +
		"$arg is one of the four numeric types xs:float, xs:double, " +
		"xs:decimal or xs:integer the type of the result is the same as " +
		"the type of $arg. If the type of $arg is a type derived from one " +
		"of the numeric types, the result is an instance of the " +
		"base numeric type.\n\n" +
		"The three argument version of the function with $precision = 0 " +
        "produces the same result as the two argument version.\n\n" +
		"For arguments of type xs:float and xs:double, if the argument is " +
		"NaN, positive or negative zero, or positive or negative infinity, " +
		"then the result is the same as the argument. In all other cases, " +
		"the argument is cast to xs:decimal, the function is applied to this " +
		"xs:decimal value, and the resulting xs:decimal is cast back to " +
		"xs:float or xs:double as appropriate to form the function result. " +
		"If the resulting xs:decimal value is zero, then positive or negative " +
		"zero is returned according to the sign of the original argument.\n\n" +
		"Note that the process of casting to xs:decimal " +
		"may result in an error [err:FOCA0001].\n\n" +
		"If $arg is of type xs:float or xs:double, rounding occurs on the " +
		"value of the mantissa computed with exponent = 0.";
	
	protected static final FunctionParameterSequenceType ARG_PARAM = new FunctionParameterSequenceType("arg", Type.NUMBER, Cardinality.ZERO_OR_ONE, "The input number");
	protected static final FunctionParameterSequenceType PRECISION_PARAM = new FunctionParameterSequenceType("precision", Type.INTEGER, Cardinality.EXACTLY_ONE, "The precision factor");
	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.NUMBER, Cardinality.ZERO_OR_ONE, "the rounded value");

	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
					new QName("round-half-to-even", Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION_1_PARAM + FUNCTION_DESCRIPTION_COMMON,
					new SequenceType[] { ARG_PARAM }, 
					RETURN_TYPE),
			
			new FunctionSignature(new QName("round-half-to-even",
					Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION_2_PARAM + FUNCTION_DESCRIPTION_COMMON,
					new SequenceType[] { ARG_PARAM, PRECISION_PARAM }, 
					RETURN_TYPE ) 
	};

	public FunRoundHalfToEven(XQueryContext context,
			FunctionSignature signatures) {
		super(context, signatures);
	}

	public int returnsType() {
		return Type.DOUBLE;
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
        
        Sequence result;
		IntegerValue precision = null;
		final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty())
			{result = Sequence.EMPTY_SEQUENCE;}
        else {		
            if (contextItem != null) 
    			{contextSequence = contextItem.toSequence();}
            
    		if (getSignature().getArgumentCount() > 1) {
    			precision = (IntegerValue) getArgument(1).eval(contextSequence, contextItem).itemAt(0).convertTo(Type.INTEGER);
    		}
            
        	final Item item = seq.itemAt(0);
        	NumericValue value;
        	if (item instanceof NumericValue) {
				value = (NumericValue) item;
			} else {
				value = (NumericValue) item.convertTo(Type.NUMBER);
			}
        	
			result = value.round(precision);
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
	}
}
