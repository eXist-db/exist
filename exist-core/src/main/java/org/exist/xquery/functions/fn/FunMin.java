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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.util.Collations;
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
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.QNameValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class FunMin extends CollatingFunction {

	protected static final String FUNCTION_DESCRIPTION_COMMON_1 =

		"Selects an item from the input sequence $arg whose value is " +
		"less than or equal to the value of every other item in the " +
		"input sequence. If there are two or more such items, then " + 
		"the specific item whose value is returned is implementation dependent.\n\n" +
		"The following rules are applied to the input sequence:\n\n" +
		"- Values of type xs:untypedAtomic in $arg are cast to xs:double.\n" +
		"- Numeric and xs:anyURI values are converted to the least common " +
		"type that supports the 'le' operator by a combination of type promotion " + 
		"and subtype substitution. See Section B.1 Type PromotionXP and " +
		"Section B.2 Operator MappingXP.\n\n" +

		"The items in the resulting sequence may be reordered in an arbitrary " +
		"order. The resulting sequence is referred to below as the converted " +
		"sequence. This function returns an item from the converted sequence " +
		"rather than the input sequence.\n\n" +

		"If the converted sequence is empty, the empty sequence is returned.\n\n" +

		"All items in $arg must be numeric or derived from a single base type " + 
		"for which the 'le' operator is defined. In addition, the values in the " +
		"sequence must have a total order. If date/time values do not have a " +
		"timezone, they are considered to have the implicit timezone provided " +
		"by the dynamic context for the purpose of comparison. Duration values " +
		"must either all be xs:yearMonthDuration values or must all be " +
		"xs:dayTimeDuration values.\n\n" +

		"If any of these conditions is not met, a type error is raised [err:FORG0006].\n\n" +

		"If the converted sequence contains the value NaN, the value NaN is returned.\n\n" +

		"If the items in the value of $arg are of type xs:string or types derived " +
		"by restriction from xs:string, then the determination of the item with " + 
		"the smallest value is made according to the collation that is used. ";
    protected static final String FUNCTION_DESCRIPTION_2_PARAM =
        "If the type of the items in $arg is not xs:string and $collation is " +
		"specified, the collation is ignored.\n\n";
    protected static final String FUNCTION_DESCRIPTION_COMMON_2 =
		"The collation used by the invocation of this function is determined " +
		"according to the rules in 7.3.1 Collations.";

	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName("min", Function.BUILTIN_FUNCTION_NS),
            FUNCTION_DESCRIPTION_COMMON_1 +
            FUNCTION_DESCRIPTION_COMMON_2,
			new SequenceType[] { new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The input sequence")},
			new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "the minimum value")
		),
		new FunctionSignature(
			new QName("min", Function.BUILTIN_FUNCTION_NS),
            FUNCTION_DESCRIPTION_COMMON_1  + FUNCTION_DESCRIPTION_2_PARAM +
            FUNCTION_DESCRIPTION_COMMON_2,
			new SequenceType[] { 
				new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The input sequence"),
				new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI")
			},
			new FunctionReturnSequenceType(Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_ONE, "the minimum value")
		)
	};

	public FunMin(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
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
        
		boolean computableProcessing = false;
        Sequence result;
		final Sequence arg = getArgument(0).eval(contextSequence, contextItem);
		if (arg.isEmpty())
			{result = Sequence.EMPTY_SEQUENCE;}
        else {
        	//TODO : test if a range index is defined *iff* it is compatible with the collator
    		final Collator collator = getCollator(contextSequence, contextItem, 2);
    		final SequenceIterator iter = arg.unorderedIterator();
    		AtomicValue min = null;
    		while (iter.hasNext()) {
                final Item item = iter.nextItem();
                if (item instanceof QNameValue)
            		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(item.getType()), arg);}
                AtomicValue value = item.atomize();

                //Duration values must either all be xs:yearMonthDuration values or must all be xs:dayTimeDuration values.
        		if (Type.subTypeOf(value.getType(), Type.DURATION)) {
        			value = ((DurationValue)value).wrap();
        			if (value.getType() == Type.YEAR_MONTH_DURATION) {
	                	if (min != null && min.getType() != Type.YEAR_MONTH_DURATION)
	                		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
	                				" and " + Type.getTypeName(value.getType()), value);}
            		
        			} else if (value.getType() == Type.DAY_TIME_DURATION) {
	                	if (min != null && min.getType() != Type.DAY_TIME_DURATION)
	                		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
	                				" and " + Type.getTypeName(value.getType()), value);}
        				
        			} else
        				{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(value.getType()), value);}

    			//Any value of type xdt:untypedAtomic is cast to xs:double
        		} else if (value.getType() == Type.UNTYPED_ATOMIC) 
                	{value = value.convertTo(Type.DOUBLE);} 
                
        		if (min == null)
                    {min = value;}
                else {                	
                	if (Type.getCommonSuperType(min.getType(), value.getType()) == Type.ANY_ATOMIC_TYPE) {
                		throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
                				" and " + Type.getTypeName(value.getType()), value);
                	}
                    //Any value of type xdt:untypedAtomic is cast to xs:double
                    if (value.getType() == Type.ANY_ATOMIC_TYPE)
                    	{value = value.convertTo(Type.DOUBLE);}
                	//Numeric tests
	                if (Type.subTypeOfUnion(value.getType(), Type.NUMERIC)) {
	                	//Don't mix comparisons
	                	if (!Type.subTypeOfUnion(min.getType(), Type.NUMERIC))
	                		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
	                				" and " + Type.getTypeName(value.getType()), min);}
	                	if (((NumericValue) value).isNaN()) {
	                		//Type NaN correctly
	                		value = value.promote(min);	                		
                           if (value.getType() == Type.FLOAT)
                               {min = FloatValue.NaN;}
                           else
                               {min = DoubleValue.NaN;}
                           //although result will be NaN, we need to continue on order to type correctly 
                           continue;
	                	} 
	                	min = min.promote(value);
	                }
	                //Ugly test
	                if (value instanceof ComputableValue) {
                        if (!(min instanceof ComputableValue))
                            {throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
                                    " and " + Type.getTypeName(value.getType()), min);}
	                	//Type value correctly
	                	value = value.promote(min);
	                    min = min.min(collator, value);
	                    computableProcessing = true;
                	} else {
	                	if (computableProcessing)
	                		{throw new XPathException(this, ErrorCodes.FORG0006, "Cannot compare " + Type.getTypeName(min.getType()) +
	                				" and " + Type.getTypeName(value.getType()), value);}
	                	if (Collations.compare(collator, value.getStringValue(), min.getStringValue()) < 0)	               
	                		{min = value;}
	                }
                }
            }           
            result = min;
        }
    
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;   
    }

}
