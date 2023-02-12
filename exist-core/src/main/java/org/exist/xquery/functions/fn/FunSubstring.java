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
import org.exist.xquery.Expression;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Built-in function fn:substring().
 *
 *	@author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 *	@author <a href="mailto:ellefj@gmail.com">ljo</a>
 */
public class FunSubstring extends Function {
	
	public final static FunctionSignature[] signatures = {
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				"Returns the portion of the value of $source beginning at the position indicated " +
				"by the value of $starting-at and continuing to the end of $source. " +
				"The characters returned do not extend beyond the end of $source. If $starting-at " +
				"is zero or negative, only those characters in positions greater than zero are returned." +
				"If the value of $source is the empty sequence, the zero-length string is returned.",
				new SequenceType[] {
					 new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The source string"),
					 new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the substring")
			),
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				"Returns the portion of the value of $source beginning at the position indicated by the value of $starting-at " +
				"and continuing for the number of characters indicated by the value of $length. The characters returned do not extend " +
				"beyond the end of $source. If $starting-at is zero or negative, only those characters in positions greater " +
				"than zero are returned. If the value of $source is the empty sequence, the zero-length string is returned.",
				new SequenceType[] {
					 new FunctionParameterSequenceType("source", Type.STRING, Cardinality.ZERO_OR_ONE, "The source string"),
					 new FunctionParameterSequenceType("starting-at", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The starting position"),
					 new FunctionParameterSequenceType("length", Type.DOUBLE, Cardinality.EXACTLY_ONE, "The number of characters in the substring")
				},
				new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the substring")
			)
	};
				
	public FunSubstring(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.STRING;
	}
		
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		//start profiler
		if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());}
        }  
        
		//get arguments
        final Expression argSourceString = getArgument(0);
		final Expression argStartingLoc = getArgument(1);
		Expression argLength = null;
		
		//get the context sequence
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
        Sequence result;
		final Sequence seqSourceString = argSourceString.eval(contextSequence, null);
		
		//If the value of $sourceString is the empty sequence return EMPTY_STRING, there must be a string to operate on!
		if(seqSourceString.isEmpty()) {
			result = StringValue.EMPTY_STRING;
		} else {
			//get the string to substring
        	final String sourceString = seqSourceString.getStringValue();
    		
        	//check for a valid start position for the substring
        	final NumericValue startingLoc = ((NumericValue)(argStartingLoc.eval(contextSequence, null).itemAt(0).convertTo(Type.NUMERIC))).round();
        	if(!validStartPosition(startingLoc, sourceString.length())) {
        		//invalid start position
        		result = StringValue.EMPTY_STRING;
        	} else {
				//are there 2 or 3 arguments to this function?
				if(getArgumentCount() > 2) {
					argLength = getArgument(2);
                    final NumericValue length = ((NumericValue)(argLength.eval(contextSequence, null).itemAt(0).convertTo(Type.NUMERIC))).round();

                    // Relocate length to position according to spec:
                    // fn:round($startingLoc) <=
                    // $p 
                    // < fn:round($startingLoc) + fn:round($length)
                    NumericValue endingLoc;
                    if (!length.isInfinite()) {
                        endingLoc = (NumericValue) new IntegerValue(this, startingLoc.getInt() + length.getInt());
                    } else {
                        endingLoc = length;
                    }
					//check for a valid end position for the substring
					if(!validEndPosition(endingLoc, startingLoc)) {
						//invalid length
						result = StringValue.EMPTY_STRING;
					} else {
						if(endingLoc.getInt() > sourceString.length() 
                           || endingLoc.isInfinite()) {
							//fallback to fn:substring(string, start)
							result = substring(sourceString, startingLoc);
						} else {
							//three arguments fn:substring(string, start, end)
							result = substring(sourceString, startingLoc, endingLoc);
						}
					}
				} else { // No length argument
					//two arguments fn:substring(string, start)
					result = substring(sourceString, startingLoc);
				}
        	}
		}
        
		//end profiler
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;
	}
	
	/**
	 * Checks that the startPosition is valid for the length of the $sourceString
	 * 
	 * @param startPosition		The user specified startPosition for the fn:substring(), start index is 1
	 * @param stringLength		The length of the $sourceString passed to fn:substring()
	 * 
	 * @return true if the startPosition is valid, false otherwise
	 */
	private boolean validStartPosition(NumericValue startPosition, int stringLength)
	{
		//if start position is not a number return false
		if(startPosition.isNaN())
			{return false;}
		
		//if start position is infinite return false
		if(startPosition.isInfinite())
			{return false;}
		
		//if the start position extends beyond $sourceString return EMPTY_STRING
    	try {
    		//fn:substring("he",2) must return "e"
    		if(startPosition.getInt() > stringLength) 
    			{return false;}
    	} catch(final XPathException xpe) {
    		return false;
    	}
    	
    	//start position is valid
    	return true;
	}
	
	/**
     * Checks that the end position is valid for the $sourceString
	 * 
     * @param end a <code>NumericValue</code> value
     * @param start a <code>NumericValue</code> value
     * @return true if the length is valid, false otherwise
     * @throws XPathException if an error occurs
     */
    private boolean validEndPosition(NumericValue end, NumericValue start)
        throws XPathException {
        //if end position is not a number
        if (end.isNaN())
            {return false;}

        //if end position is +/-infinite
        if (end.isInfinite())
            {return true;}
        
        //if end position is less than 1
        if (end.getInt() < 1)
			{return false;}

        //if end position is less than start position
        if(end.getInt() <= start.getInt())
			{return false;}
		
		//length is valid
		return true;
	}
	
	/**
	 * fn:substring($sourceString, $startingLoc)
	 * 
	 * @see <a href="http://www.w3.org/TR/xpath-functions/#func-substring">w3.org</a>
	 * 
	 * @param sourceString	The source string to substring
	 * @param startingLoc	The Starting Location for the substring, start 
     * index is 1
	 * 
	 * @return The StringValue of the substring
	 */
	private StringValue substring(String sourceString, NumericValue startingLoc)
        throws XPathException {
		if(startingLoc.getInt() <= 1) {
			//start value is 1 or less, so just return the string
			return new StringValue(this, sourceString);
		}
        final ValueSequence codepoints = FunStringToCodepoints.getCodePoints(this, sourceString);
        // transition from xs:string index to Java string index.
        return new StringValue(this, FunStringToCodepoints.subSequence(codepoints, startingLoc.getInt() - 1));
 	}
	
	/**
	 * fn:substring($sourceString, $startingLoc, $length)
	 * 
	 * @see <a href="http://www.w3.org/TR/xpath-functions/#func-substring">http://www.w3.org/TR/xpath-functions/#func-substring</a>
	 * 
	 * @param sourceString	The source string to substring
	 * @param startingLoc	The Starting Location for the substring, start index is 1
	 * @param endingLoc		The Ending Location for the substring
	 * 
	 * @return The StringValue of the substring
	 */
	private StringValue substring(String sourceString, NumericValue startingLoc, NumericValue endingLoc)
        throws XPathException {
        final ValueSequence codepoints = FunStringToCodepoints.getCodePoints(this, sourceString);
        // transition from xs:string index to Java string index.
        return new StringValue(this, FunStringToCodepoints.subSequence(codepoints,
                                                                 startingLoc.getInt() - 1,
                                                                 endingLoc.getInt() - 1));
	}
    
}
