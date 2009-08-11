/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
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
package org.exist.xquery.functions;

import java.text.Collator;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants;
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
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class FunIndexOf extends BasicFunction {

	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_ONE, "the sequence of positive integers giving the positions within the sequence");

	protected static final FunctionParameterSequenceType COLLATION_PARAM = new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.EXACTLY_ONE, "The collation");

	protected static final FunctionParameterSequenceType SEARCH_PARAM = new FunctionParameterSequenceType("srchParam", Type.ATOMIC, Cardinality.EXACTLY_ONE, "The search component");

	protected static final FunctionParameterSequenceType SEQ_PARAM = new FunctionParameterSequenceType("seqParam", Type.ATOMIC, Cardinality.ZERO_OR_MORE, "The sequence");

	protected static final String FUNCTION_DESCRIPTION =

		"Returns a sequence of positive integers giving the " + 
		"positions within the sequence $seqParam of items " +  
		"that are equal to $srchParam.\n\n" +
		"The collation used by the invocation of this function " + 
		"is determined according to the rules in 7.3.1 Collations. " + 
		"The collation is used when string comparison is required.\n\n" +
		"The items in the sequence $seqParam are compared with " + 
		"$srchParam under the rules for the eq operator. Values of " +  
		"type xs:untypedAtomic are compared as if they were of " +  
		"type xs:string. Values that cannot be compared, i.e. " + 
		"the eq operator is not defined for their types, are " + 
		"considered to be distinct. If an item compares equal, " +  
		"then the position of that item in the sequence " + 
		"$seqParam is included in the result.\n\n" +

		"If the value of $seqParam is the empty sequence, or " + 
		"if no item in $seqParam matches $srchParam, then the " + 
		"empty sequence is returned.\n\n" +

		"The first item in a sequence is at position 1, not position 0.\n\n" +

		"The result sequence is in ascending numeric order.";

	public final static FunctionSignature fnIndexOf[] = {
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
					FUNCTION_DESCRIPTION,
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
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
        }
        
        Sequence result;
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;
        else {
    		AtomicValue srch = args[1].itemAt(0).atomize();
    		Collator collator;
    		if (getSignature().getArgumentCount() == 3) {
    			String collation = args[2].getStringValue();
    			collator = context.getCollator(collation);
    		} else
    			collator = context.getDefaultCollator();
    		result = new ValueSequence();
    		int j = 1;
    		for (SequenceIterator i = args[0].iterate(); i.hasNext(); j++) {
    			AtomicValue next = i.nextItem().atomize();
    			try {
	    			if (ValueComparison.compareAtomic(collator, next, srch, Constants.TRUNC_NONE, Constants.EQ))
	    				result.add(new IntegerValue(j));
    			} catch (XPathException e) {
    				//Ignore me : values can not be compared
    			}
    		}    		
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result; 
        
	}

}
