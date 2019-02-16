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
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunRound extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("round", Function.BUILTIN_FUNCTION_NS),
				"Returns the number with no fractional part that is closest " +
				"to the argument $arg. If there are two such numbers, then the one " +
				"that is closest to positive infinity is returned. If type of " +
				"$arg is one of the four numeric types xs:float, xs:double, " +
				"xs:decimal or xs:integer the type of the result is the same " +
				"as the type of $arg. If the type of $arg is a type derived " +
				"from one of the numeric types, the result is an instance of " +
				"the base numeric type.\n\n" +
				"For xs:float and xs:double arguments, if the argument is " +
				"positive infinity, then positive infinity is returned. " +
				"If the argument is negative infinity, then negative infinity " +
				"is returned. If the argument is positive zero, then positive " +
				"zero is returned. If the argument is negative zero, then " +
				"negative zero is returned. If the argument is less than zero, " +
				"but greater than or equal to -0.5, then negative zero is returned. " +
				"In the cases where positive zero or negative zero is returned, " +
				"negative zero or positive zero may be returned as " +
				"[XML Schema Part 2: Datatypes Second Edition] does not " +
				"distinguish between the values positive zero and negative zero.",
				new SequenceType[] { new FunctionParameterSequenceType("arg", Type.NUMBER, Cardinality.ZERO_OR_ONE, "The input number") },
				new FunctionReturnSequenceType(Type.NUMBER, Cardinality.ZERO_OR_ONE, "the rounded value")
			);
			
	public FunRound(XQueryContext context) {
		super(context, signature);
	}

	public int returnsType() {
		return Type.NUMBER;
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
        
        if (contextItem != null)
			{contextSequence = contextItem.toSequence();}
		
        Sequence result;
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
		if (seq.isEmpty())
            {result = Sequence.EMPTY_SEQUENCE;}
        else {
        	final Item item = seq.itemAt(0);
        	NumericValue value;
        	if (item instanceof NumericValue) {
				value = (NumericValue) item;
			} else {
				value = (NumericValue) item.convertTo(Type.NUMBER);
			}
            result = value.round();
        }

        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;           
	}
}
