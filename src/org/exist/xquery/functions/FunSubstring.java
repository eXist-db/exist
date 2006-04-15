/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001 Wolfgang M. Meier
 * meier@ifs.tu-darmstadt.de
 * http://exist.sourceforge.net
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
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 * $Id$
 */

package org.exist.xquery.functions;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:substring().
 *
 */
public class FunSubstring extends Function {
	
	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			),
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE),
					 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			)
	};
				
	public FunSubstring(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }  
        
        Expression arg0 = getArgument(0);
		Expression arg1 = getArgument(1);
		Expression arg2 = null;
		if (getArgumentCount() > 2)
			arg2 = getArgument(2);

		if(contextItem != null)
			contextSequence = contextItem.toSequence();
        
        Sequence result;
		Sequence seq = arg0.eval(contextSequence);
		if(seq.isEmpty())
			//If the value of $sourceString is the empty sequence, the zero-length string is returned.
			result = StringValue.EMPTY_STRING;
        else {
    		String string = seq.getStringValue();
    		NumericValue startValue = ((NumericValue)(arg1.eval(contextSequence).itemAt(0).convertTo(Type.NUMBER))).round();
    		if (startValue.isNaN())
    			result = StringValue.EMPTY_STRING;
    		else if (startValue.isInfinite())
    			result = StringValue.EMPTY_STRING;
    		else {
    			int start = startValue.getInt();    		
	    		if (start >= string.length()) 
	    			//The characters returned do not extend beyond $sourceString
	    			result = StringValue.EMPTY_STRING;    			
	    		else if (arg2 == null) {
	    			//If $startingLoc is zero or negative, only those characters in positions greater than zero are returned.
	    			//Because we will substract 1 ;-)
	    			if (start < 1)
	    				start = 1;
	    			result = new StringValue(string.substring(start - 1));
	    		} else {
	    			NumericValue lengthValue =((NumericValue)(arg2.eval(contextSequence).itemAt(0).convertTo(Type.NUMBER))).round();
	    			if (lengthValue.isNaN())
	    				result = StringValue.EMPTY_STRING;
	    			else if (((NumericValue)lengthValue.plus(startValue)).isInfinite() ||
	    					((NumericValue)lengthValue.plus(startValue)).getInt() >= string.length()) {
	        			if (start < 0)
	        				start = 0;        				
	        			result = new StringValue(string.substring(start));
	    			} else {
		    			int length = lengthValue.getInt();
		    			//If $startingLoc is zero or negative, only those characters in positions greater than zero are returned.
		    			if (start <= 0) {    				
		    				//Because we will will add 1
		    				length = length + start - 1;
		    				//Because we will substract 1 ;-)
		    				start = 1;
		    			}
		    			result = new StringValue(string.substring(start - 1, start + length - 1));
	    			}
	    		}
    		}
        }
        
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;    
        
	}
}
