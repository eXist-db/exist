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
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class FunCeiling extends Function {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("ceiling", Function.BUILTIN_FUNCTION_NS),
            "Returns a value of the same type as the argument. Specifically, " +
            "returns the smallest (closest to negative infinity) number " +
            "with no fractional part that is not less than the value of the argument, $number.",
            new SequenceType[] { 
                new FunctionParameterSequenceType("number", Type.NUMERIC,
                    Cardinality.ZERO_OR_ONE, "The number")
            },
            new FunctionReturnSequenceType(Type.NUMERIC, Cardinality.ZERO_OR_ONE,
                "The non-fractional number not less than $number")
        );

    public FunCeiling(XQueryContext context) {
        super(context, signature);
    }

    public int returnsType() {
        return Type.NUMERIC;
    }

    public Sequence eval(Sequence contextSequence, Item contextItem) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT SEQUENCE", contextSequence);}
            if (contextItem != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES,
                    "CONTEXT ITEM", contextItem.toSequence());}
        }
        final Sequence seq = getArgument(0).eval(contextSequence, contextItem);
        Sequence result;
        if (seq.isEmpty()) {
            result = Sequence.EMPTY_SEQUENCE; 
        } else {
        	final Item item = seq.itemAt(0);
        	NumericValue value;
        	if (item instanceof NumericValue) {
				value = (NumericValue) item;
			} else {
				value = (NumericValue) item.convertTo(Type.NUMERIC);
			}
            result = value.ceiling();
        }
        if (context.getProfiler().isEnabled()) 
            {context.getProfiler().end(this, "", result);} 
        return result;
    }
}