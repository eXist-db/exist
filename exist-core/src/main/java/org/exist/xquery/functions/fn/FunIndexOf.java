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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 *
 */
public class FunIndexOf extends BasicFunction {

	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "the sequence of positive integers giving the positions within the sequence");

	protected static final FunctionParameterSequenceType COLLATION_PARAM = new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI");

	protected static final FunctionParameterSequenceType SEARCH_PARAM = new FunctionParameterSequenceType("search", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The search component");

	protected static final FunctionParameterSequenceType SEQ_PARAM = new FunctionParameterSequenceType("source", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The source sequence");

	protected static final String FUNCTION_DESCRIPTION =

		"Returns a sequence of positive integers giving the " + 
		"positions within the sequence of atomic values $source " +  
		"that are equal to $search.\n\n" +
		"The collation used by the invocation of this function " + 
		"is determined according to the rules in 7.3.1 Collations. " + 
		"The collation is used when string comparison is required.\n\n" +
		"The items in the sequence $source are compared with " + 
		"$search under the rules for the 'eq' operator. Values of " +  
		"type xs:untypedAtomic are compared as if they were of " +  
		"type xs:string. Values that cannot be compared, i.e. " + 
		"the 'eq' operator is not defined for their types, are " + 
		"considered to be distinct. If an item compares equal, " +  
		"then the position of that item in the sequence " + 
		"$source is included in the result.\n\n" +

		"If the value of $source is the empty sequence, or " + 
		"if no item in $source matches $search, then the " + 
		"empty sequence is returned.\n\n" +

		"The first item in a sequence is at position 1, not position 0.\n\n" +

		"The result sequence is in ascending numeric order.";

	public final static FunctionSignature[] fnIndexOf = {
			new FunctionSignature(
					new QName("index-of", Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION,
					new SequenceType[] {
						SEQ_PARAM,
						SEARCH_PARAM
					},
					RETURN_TYPE
			),
			new FunctionSignature(
					new QName("index-of", Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION +
                    " " + CollatingFunction.THIRD_REL_COLLATION_ARG_EXAMPLE,
					new SequenceType[] {
							SEQ_PARAM,
							SEARCH_PARAM,
							COLLATION_PARAM
					},
					RETURN_TYPE
			)
	};
	
	public FunIndexOf(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }
        
        Sequence result;
		if (args[0].isEmpty())
			{return Sequence.EMPTY_SEQUENCE;}
        else {
    		final AtomicValue srch = args[1].itemAt(0).atomize();
    		Collator collator;
    		if (getSignature().getArgumentCount() == 3) {
    			final String collation = args[2].getStringValue();
    			collator = context.getCollator(collation);
    		} else
    			{collator = context.getDefaultCollator();}
    		result = new ValueSequence();
    		int j = 1;
    		for (final SequenceIterator i = args[0].iterate(); i.hasNext(); j++) {
    			final AtomicValue next = i.nextItem().atomize();
    			try {
	    			if (ValueComparison.compareAtomic(collator, next, srch, StringTruncationOperator.NONE, Comparison.EQ))
	    				{result.add(new IntegerValue(this, j));}
    			} catch (final XPathException e) {
    				//Ignore me : values can not be compared
    			}
    		}    		
        }
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result; 
        
	}

}
