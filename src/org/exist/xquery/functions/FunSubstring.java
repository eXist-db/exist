/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
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
 *	@author Adam Retter <adam.retter@devon.gov.uk>
 */
public class FunSubstring extends Function {
	
	public final static FunctionSignature signatures[] = {
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				"Returns the portion of the value of $a beginning at the position indicated by the value of $b and continuing to the end of $a. The characters returned do not extend beyond the end of $a. If $b is zero or negative, only those characters in positions greater than zero are returned. If the value of $a is the empty sequence, the zero-length string is returned.",
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE),
					 new SequenceType(Type.DOUBLE, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
			),
			new FunctionSignature(
				new QName("substring", Function.BUILTIN_FUNCTION_NS),
				"Returns the portion of the value of $a beginning at the position indicated by the value of $b and continuing for the number of characters indicated by the value of $c. The characters returned do not extend beyond the end of $a. If $b is zero or negative, only those characters in positions greater than zero are returned. If the value of $a is the empty sequence, the zero-length string is returned.",
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
		
	public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException
	{
		//start profiler
		if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            if (contextItem != null)
                context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
        }  
        
		//get arguments
        Expression argSourceString = getArgument(0);
		Expression argStartingLoc = getArgument(1);
		Expression argLength = null;
		
		//get the context sequence
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
        Sequence result;
		Sequence seqSourceString = argSourceString.eval(contextSequence);
		
		//If the value of $sourceString is the empty sequence return EMPTY_STRING, there must be a string to operate on!
		if(seqSourceString.isEmpty())
		{
			result = StringValue.EMPTY_STRING;
		}
		else
		{
			//get the string to substring
        	String sourceString = seqSourceString.getStringValue();
    		
        	//check for a valid start position for the substring
        	NumericValue startingLoc = ((NumericValue)(argStartingLoc.eval(contextSequence).itemAt(0).convertTo(Type.NUMBER))).round();
        	if(!validStartPosition(startingLoc, sourceString.length()))
        	{
        		//invalid start position
        		result = StringValue.EMPTY_STRING;
        	}
        	else
        	{
				//are there 2 or 3 arguments to this function?
				if(getArgumentCount() > 2)
				{
					//three arguments, get the third argument value for the length
					argLength = getArgument(2);
					
					//check for a valid length for the substring
					NumericValue length = ((NumericValue)(argLength.eval(contextSequence).itemAt(0).convertTo(Type.NUMBER))).round();
					if(!validLength(length))
					{
						//invalid length
						result = StringValue.EMPTY_STRING;
					}
					else
					{
						//if the length extends past the end of the string, just return the string from the start position
						if(length.getInt() > sourceString.length() || startingLoc.getInt() -1 + length.getInt() > sourceString.length() || length.isInfinite())
						{
							//fallback to fn:substring(string, start)
							result = substring(sourceString, startingLoc);
						}
						else
						{
							//three arguments fn:substring(string, start, length)
							result = substring(sourceString, startingLoc, length);
						}
					}
				}
				else
				{
					//two arguments fn:substring(string, start)
					result = substring(sourceString, startingLoc);
				}
        	}
		}
        
		//end profiler
        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
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
			return false;
		
		//if start position is infinite return false
		if(startPosition.isInfinite())
			return false;
		
		//if the start position extends beyond $sourceString return EMPTY_STRING
    	try
    	{
    		//fn:substring("he",2) must return "e"
    		if(startPosition.getInt() > stringLength) 
    			return false;
    	}
    	catch(XPathException xpe)
    	{
    		return false;
    	}
    	
    	//start position is valid
    	return true;
	}
	
	/**
	 * Checks that the length is valid for the $sourceString
	 * 
	 * @param length		The user specified length for the fn:substring()
	 * 
	 * @return true if the length is valid, false otherwise
	 */
	private boolean validLength(NumericValue length)
	{
		//if length is not a number return false
		if(length.isNaN())
			return false;
		
		//length is valid
		return true;
	}
	
	/**
	 * fn:substring($sourceString, $startingLoc)
	 * 
	 * @see http://www.w3.org/TR/xpath-functions/#func-substring
	 * 
	 * @param stringSource	The source string to substring
	 * @param startingLoc	The Starting Location for the substring, start index is 1
	 * 
	 * @return The StringValue of the substring
	 */
	private StringValue substring(String sourceString, NumericValue startingLoc) throws XPathException
	{
		if(startingLoc.getInt() <= 1)
		{
			//start value is 1 or less, so just return the string
			return new StringValue(sourceString);
		}
		
		
		
		//start index of xs:string is 1, whereas java string is 0; so subtract 1
		return new StringValue(sourceString.substring(startingLoc.getInt() - 1));
	}
	
	/**
	 * fn:substring($sourceString, $startingLoc, $length)
	 * 
	 * @see http://www.w3.org/TR/xpath-functions/#func-substring
	 * 
	 * @param stringSource	The source string to substring
	 * @param startingLoc	The Starting Location for the substring, start index is 1
	 * @param length	The length of the substring
	 * 
	 * @return The StringValue of the substring
	 */
	private StringValue substring(String sourceString, NumericValue startingLoc, NumericValue length) throws XPathException
	{
		//if start value is 1 or less, start at the start of the string and adjust the length appropriately
		if(startingLoc.getInt() <= 1)
		{
			//the -1 is to transition from xs:string index which starts at 1 to Java string index which starts at 0
			int endIndex = length.getInt() + (-1 + startingLoc.getInt());
			
			return new StringValue(sourceString.substring(0, endIndex));
		}
		
		return new StringValue(sourceString.substring(startingLoc.getInt() - 1, startingLoc.getInt() - 1 + length.getInt()));
	}
	
}
