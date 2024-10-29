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
import org.exist.xquery.BasicFunction;
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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * 
 * @author wolf
 *
 */
public class FunStringJoin extends BasicFunction {

	public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("string-join", Function.BUILTIN_FUNCTION_NS),
            "Returns a xs:string created by concatenating the members of the " +
            "$arg sequence using $separator as a separator. If the value of the separator is the zero-length " +
            "string, then the members of the sequence are concatenated without a separator. " +
            "The effect of calling the single-argument version of this function is the same as calling the " +
            "two-argument version with $separator set to a zero-length string.",
            new SequenceType[] {
                new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE,
                        "The sequence to be joined to form the string. If it is empty, " +
                                "a zero-length string is returned.")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the joined string")),
		new FunctionSignature(
            new QName("string-join", Function.BUILTIN_FUNCTION_NS),
            "Returns a xs:string created by concatenating the members of the " +
            "$arg sequence using $separator as a separator. If the value of the separator is the zero-length " +
            "string, then the members of the sequence are concatenated without a separator.",
            new SequenceType[] {
                new FunctionParameterSequenceType("arg", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE,
                    "The sequence to be joined to form the string. If it is empty, " +
                    "a zero-length string is returned."),
                new FunctionParameterSequenceType("separator", Type.STRING, Cardinality.EXACTLY_ONE, "The separator to be placed in the string between the items of $arg")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the joined string"))
    };

	public FunStringJoin(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);       
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }
        
		String sep = null;
        if (getArgumentCount() == 2) {
            sep = args[1].getStringValue();
            if(sep.isEmpty())
                {sep = null;}
        }
		final StringBuilder out = new StringBuilder();
		Item next;
		boolean gotOne = false;
		for(final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
			next = i.nextItem();
			if(gotOne && sep != null)
				{out.append(sep);}
			out.append(next.getStringValue());
			gotOne = true;
		}
		final Sequence result = new StringValue(this, out.toString());
        
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        
        return result;
	}

}
