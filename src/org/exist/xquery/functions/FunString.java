/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2000-2006 The eXist team
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
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * xpath-library function: string(object)
 *
 */
public class FunString extends Function {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("string", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of the context item as xs:string. " +
			"If the context item is undefined, an error is raised.",
			new SequenceType[0],
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		),
		new FunctionSignature(
			new QName("string", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $a as xs:string. " +
			"If the value of $ is the empty sequence, the zero-length string is returned. " +
			"If the context item of $a is undefined, an error is raised.",
			new SequenceType[] {
				 new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE)},
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
		)
	};

	public FunString(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
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
        
		if (contextItem != null)
			contextSequence = contextItem.toSequence();

        // if the function is called with an argument and it is empty,
        // return the empty string
        if(getArgumentCount() == 1) {
			contextSequence = getArgument(0).eval(contextSequence);
            if (contextSequence.isEmpty())
                return StringValue.EMPTY_STRING;
        } else if (contextSequence == null)
			throw new XPathException(this, "err:XPDY0002 : undefined context sequence for '" + this.toString() + "'");
        // no argument and the context sequence is empty: return the empty sequence
        else if (contextSequence.isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        
        Sequence result = contextSequence.convertTo(Type.STRING);        

        if (context.getProfiler().isEnabled()) 
            context.getProfiler().end(this, "", result); 
        
        return result;           
	}
}
