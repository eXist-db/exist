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
import org.exist.xquery.ErrorCodes;
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
 * Implements fn:replicate (XQuery 4.0).
 *
 * Produces multiple copies of a sequence.
 */
public class FnReplicate extends BasicFunction {

    public static final FunctionSignature FN_REPLICATE = new FunctionSignature(
            new QName("replicate", Function.BUILTIN_FUNCTION_NS),
            "Produces multiple copies of a sequence.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence to replicate"),
                    new FunctionParameterSequenceType("count", Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of copies")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the replicated sequence"));

    public FnReplicate(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final long count = ((IntegerValue) args[1].itemAt(0)).getLong();
        if (count < 0) {
            throw new XPathException(this, ErrorCodes.XPTY0004, "The count argument to fn:replicate must be non-negative, got: " + count);
        }
        if (count == 0 || input.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final int inputSize = input.getItemCount();
        final ValueSequence result = new ValueSequence((int) Math.min(count * inputSize, Integer.MAX_VALUE));
        for (long c = 0; c < count; c++) {
            result.addAll(input);
        }
        return result;
    }
}
