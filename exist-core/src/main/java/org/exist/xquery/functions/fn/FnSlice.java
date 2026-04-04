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
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements fn:slice (XQuery 4.0).
 *
 * Returns selected items from a sequence based on position, with support for
 * negative indexing and step values (Python-style slicing with 1-based indexing).
 */
public class FnSlice extends BasicFunction {

    private static final String DESCRIPTION = "Returns selected items from the input sequence based on their position.";

    public static final FunctionSignature[] FN_SLICE = {
            new FunctionSignature(
                    new QName("slice", Function.BUILTIN_FUNCTION_NS),
                    DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the selected items")),
            new FunctionSignature(
                    new QName("slice", Function.BUILTIN_FUNCTION_NS),
                    DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The start position")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the selected items")),
            new FunctionSignature(
                    new QName("slice", Function.BUILTIN_FUNCTION_NS),
                    DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The start position"),
                            new FunctionParameterSequenceType("end", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The end position")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the selected items")),
            new FunctionSignature(
                    new QName("slice", Function.BUILTIN_FUNCTION_NS),
                    DESCRIPTION,
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The start position"),
                            new FunctionParameterSequenceType("end", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The end position"),
                            new FunctionParameterSequenceType("step", Type.INTEGER, Cardinality.ZERO_OR_ONE, "The step value")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the selected items"))
    };

    public FnSlice(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        if (input.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int count = input.getItemCount();

        // Resolve start
        int s;
        if (args.length < 2 || args[1].isEmpty() || ((IntegerValue) args[1].itemAt(0)).getLong() == 0) {
            s = 1;
        } else {
            final long sv = ((IntegerValue) args[1].itemAt(0)).getLong();
            s = (int) (sv < 0 ? count + sv + 1 : sv);
        }

        // Resolve end
        int e;
        if (args.length < 3 || args[2].isEmpty() || ((IntegerValue) args[2].itemAt(0)).getLong() == 0) {
            e = count;
        } else {
            final long ev = ((IntegerValue) args[2].itemAt(0)).getLong();
            e = (int) (ev < 0 ? count + ev + 1 : ev);
        }

        // Resolve step
        int step;
        if (args.length < 4 || args[3].isEmpty() || ((IntegerValue) args[3].itemAt(0)).getLong() == 0) {
            step = (e >= s) ? 1 : -1;
        } else {
            step = (int) ((IntegerValue) args[3].itemAt(0)).getLong();
        }

        // Handle negative step: reverse input and recurse with negated positions
        if (step < 0) {
            final ValueSequence reversed = new ValueSequence(count);
            for (int i = count - 1; i >= 0; i--) {
                reversed.add(input.itemAt(i));
            }
            // slice(reverse($input), -$s, -$e, -$step)
            final Sequence[] newArgs = new Sequence[4];
            newArgs[0] = reversed;
            newArgs[1] = new IntegerValue(this, -s);
            newArgs[2] = new IntegerValue(this, -e);
            newArgs[3] = new IntegerValue(this, -step);
            return eval(newArgs, contextSequence);
        }

        // Positive step: select items where position >= S, position <= E, and (position - S) mod step == 0
        final ValueSequence result = new ValueSequence();
        for (int pos = s; pos <= e && pos <= count; pos += step) {
            if (pos >= 1) {
                result.add(input.itemAt(pos - 1));
            }
        }
        return result;
    }
}
