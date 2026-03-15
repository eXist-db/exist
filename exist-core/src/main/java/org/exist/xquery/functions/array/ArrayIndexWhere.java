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
package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * Implements array:index-where (XQuery 4.0).
 *
 * Returns the positions in an input array of members that match a supplied
 * predicate function, as a sequence of integers in ascending order.
 */
public class ArrayIndexWhere extends BasicFunction {

    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("index-where", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
            "Returns positions of array members matching the predicate.",
            new SequenceType[] {
                new FunctionParameterSequenceType("array", Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The input array"),
                new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE,
                    "The predicate function")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE,
                "positions of matching members"))
    };

    public ArrayIndexWhere(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int size = array.getSize();
        if (size == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        try (final FunctionReference func = (FunctionReference) args[1].itemAt(0)) {
            func.analyze(cachedContextInfo);

            final int arity = func.getSignature().getArgumentCount();
            final ValueSequence result = new ValueSequence();

            for (int i = 0; i < size; i++) {
                final Sequence member = array.get(i);
                final Sequence[] funcArgs;
                if (arity >= 2) {
                    funcArgs = new Sequence[] { member, new IntegerValue(this, i + 1) };
                } else {
                    funcArgs = new Sequence[] { member };
                }

                final Sequence predResult = func.evalFunction(null, null, funcArgs);
                if (!predResult.isEmpty() && predResult.effectiveBooleanValue()) {
                    result.add(new IntegerValue(this, i + 1));
                }
            }
            return result;
        }
    }

    private org.exist.xquery.AnalyzeContextInfo cachedContextInfo =
        new org.exist.xquery.AnalyzeContextInfo();

    @Override
    public void analyze(org.exist.xquery.AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new org.exist.xquery.AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }
}
