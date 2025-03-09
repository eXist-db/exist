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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
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

	public final static FunctionSignature[] signatures = {
		new FunctionSignature(
			new QName("string", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of the context item as xs:string. " +
			"If the context item is undefined, an error is raised.",
			new SequenceType[0],
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the value of the context item as an xs:string")
		),
		new FunctionSignature(
			new QName("string", Function.BUILTIN_FUNCTION_NS),
			"Returns the value of $arg as xs:string. " +
			"If the value of $arg is the empty sequence, the zero-length string is returned. " +
			"If the context item of $arg is undefined, an error is raised.",
			new SequenceType[] {
				 new FunctionParameterSequenceType("arg", Type.ITEM, Cardinality.ZERO_OR_ONE, "The sequence to get the value of as an xs:string")},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the value of $arg as an xs:string")
		)
	};

	public FunString(final XQueryContext context, final FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence contextSequence, final Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
            	context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
            	context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT ITEM", contextItem.toSequence());
            }
        }

		if (contextItem != null) {
			contextSequence = contextItem.toSequence();
		}

        // if the function is called with an argument and it is empty,
        // return the empty string
        if(getArgumentCount() == 1) {
			contextSequence = getArgument(0).eval(contextSequence, null);
            if (contextSequence.isEmpty()) {
            	return StringValue.EMPTY_STRING;
            }
        } else if (contextSequence == null) {
			throw new XPathException(this, ErrorCodes.XPDY0002, "Undefined context sequence for '" + this + "'");
		}
        // no argument and the context sequence is empty: return the empty sequence
        else if (contextSequence.isEmpty()) {
        	return Sequence.EMPTY_SEQUENCE;
        }

        final Sequence result = contextSequence.convertTo(Type.STRING);

        if (context.getProfiler().isEnabled()) {
        	context.getProfiler().end(this, "", result);
        }

        return result;
	}
}
