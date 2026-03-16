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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements XQuery 4.0 fn:contains-subsequence, fn:starts-with-subsequence,
 * fn:ends-with-subsequence.
 */
public class FnSubsequenceMatching extends BasicFunction {

    public static final FunctionSignature[] FN_CONTAINS_SUBSEQUENCE = {
            new FunctionSignature(
                    new QName("contains-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence contains a contiguous subsequence matching the supplied subsequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to find")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input contains the subsequence")),
            new FunctionSignature(
                    new QName("contains-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence contains a contiguous subsequence matching the supplied subsequence, using a custom comparison function.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to find"),
                            new FunctionParameterSequenceType("compare", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The comparison function")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input contains the subsequence"))
    };

    public static final FunctionSignature[] FN_STARTS_WITH_SUBSEQUENCE = {
            new FunctionSignature(
                    new QName("starts-with-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence starts with the supplied subsequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to match at start")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input starts with the subsequence")),
            new FunctionSignature(
                    new QName("starts-with-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence starts with the supplied subsequence, using a custom comparison function.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to match at start"),
                            new FunctionParameterSequenceType("compare", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The comparison function")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input starts with the subsequence"))
    };

    public static final FunctionSignature[] FN_ENDS_WITH_SUBSEQUENCE = {
            new FunctionSignature(
                    new QName("ends-with-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence ends with the supplied subsequence.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to match at end")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input ends with the subsequence")),
            new FunctionSignature(
                    new QName("ends-with-subsequence", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if the input sequence ends with the supplied subsequence, using a custom comparison function.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("subsequence", Type.ITEM, Cardinality.ZERO_OR_MORE, "The subsequence to match at end"),
                            new FunctionParameterSequenceType("compare", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The comparison function")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if input ends with the subsequence"))
    };

    private AnalyzeContextInfo cachedContextInfo;

    public FnSubsequenceMatching(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence input = args[0];
        final Sequence subsequence = args[1];

        // Empty subsequence always matches
        if (subsequence.isEmpty()) {
            return BooleanValue.TRUE;
        }

        final int inputLen = input.getItemCount();
        final int subLen = subsequence.getItemCount();

        // Input shorter than subsequence: can't match
        if (inputLen < subLen) {
            return BooleanValue.FALSE;
        }

        // Get optional compare function
        FunctionReference compareRef = null;
        if (args.length > 2 && !args[2].isEmpty()) {
            compareRef = (FunctionReference) args[2].itemAt(0);
            compareRef.analyze(cachedContextInfo);
            // Validate arity: comparison function must accept exactly 2 arguments
            final int arity = compareRef.getSignature().getArgumentCount();
            if (arity != 2) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Comparison function must accept exactly 2 arguments, but has arity " + arity);
            }
        }

        try {
            // Materialize subsequence for random access
            final List<Item> subItems = new ArrayList<>(subLen);
            for (int i = 0; i < subLen; i++) {
                subItems.add(subsequence.itemAt(i));
            }

            if (isCalledAs("starts-with-subsequence")) {
                return BooleanValue.valueOf(matchesAt(input, subItems, 0, compareRef));
            } else if (isCalledAs("ends-with-subsequence")) {
                return BooleanValue.valueOf(matchesAt(input, subItems, inputLen - subLen, compareRef));
            } else {
                // contains-subsequence: try all starting positions
                for (int start = 0; start <= inputLen - subLen; start++) {
                    if (matchesAt(input, subItems, start, compareRef)) {
                        return BooleanValue.TRUE;
                    }
                }
                return BooleanValue.FALSE;
            }
        } finally {
            if (compareRef != null) {
                compareRef.close();
            }
        }
    }

    private boolean matchesAt(final Sequence input, final List<Item> subItems, final int start,
                              final FunctionReference compareRef) throws XPathException {
        for (int i = 0; i < subItems.size(); i++) {
            final Item inputItem = input.itemAt(start + i);
            final Item subItem = subItems.get(i);
            if (compareRef != null) {
                final Sequence result = compareRef.evalFunction(null, null,
                        new Sequence[]{inputItem.toSequence(), subItem.toSequence()});
                // XQ4: comparison function must return xs:boolean
                if (!result.isEmpty() && result.itemAt(0).getType() != Type.BOOLEAN) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "Comparison function must return xs:boolean, but returned "
                                    + Type.getTypeName(result.itemAt(0).getType()));
                }
                if (result.isEmpty() || !result.effectiveBooleanValue()) {
                    return false;
                }
            } else {
                // Default: deep-equal semantics
                if (!FunDeepEqual.deepEquals(inputItem, subItem, context.getDefaultCollator())) {
                    return false;
                }
            }
        }
        return true;
    }
}
