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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Built-in function fn:string-length($srcval as xs:string?) as xs:integer?
 *
 */
public class FunStrLength extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("string-length", Function.BUILTIN_FUNCTION_NS),
				"Returns an xs:integer equal to the length in characters of the value of the context item.\n" +
				"If the context item is undefined an error is raised. ",
				new SequenceType[0],
				new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the length in characters")
		),
		new FunctionSignature(
			new QName("string-length", Function.BUILTIN_FUNCTION_NS),
			"Returns an xs:integer equal to the length in characters of the value of $arg.\n" +
			"If the value of $arg is the empty sequence, the xs:integer 0 is returned.\n" +
			"If no argument is supplied, $arg defaults to the string value (calculated using fn:string()) of the context item (.). If no argument is supplied or if the argument is the context item and the context item is undefined an error is raised",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.STRING, Cardinality.ZERO_OR_ONE, "The input string")
			},
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the length in characters")
		)
	};
			
	public FunStrLength(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
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
        
        
		if(contextItem != null)
			{contextSequence = contextItem.toSequence();}
        
        
		if(getSignature().getArgumentCount() == 1)
			{contextSequence = getArgument(0).eval(contextSequence);}
		
		if (contextSequence == null)
			{throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context item");}
		
		final String strval = contextSequence.getStringValue();

		final Sequence result = new IntegerValue(FunStringToCodepoints.getCodePointCount(strval));
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;            
	}
}
