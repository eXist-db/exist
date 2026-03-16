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
import org.exist.xquery.*;
import org.exist.xquery.value.*;

/**
 * Implements XQuery 4.0 fn:subsequence-where.
 *
 * Returns items from $input starting from the first item where $from returns true,
 * up to and including the first subsequent item where $to returns true.
 *
 * Also supports fn:subsequence-before and fn:subsequence-after as derived
 * convenience functions.
 */
public class FnSubsequenceWhere extends BasicFunction {

    public static final FunctionSignature[] FN_SUBSEQUENCE_WHERE = {
            new FunctionSignature(
                    new QName("subsequence-where", Function.BUILTIN_FUNCTION_NS),
                    "Returns a contiguous subsequence defined by from/to predicates.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("from", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "Predicate for start position"),
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The selected subsequence")),
            new FunctionSignature(
                    new QName("subsequence-where", Function.BUILTIN_FUNCTION_NS),
                    "Returns a contiguous subsequence defined by from/to predicates.",
                    new SequenceType[]{
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("from", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "Predicate for start position"),
                            new FunctionParameterSequenceType("to", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "Predicate for end position"),
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The selected subsequence")),
    };

    private AnalyzeContextInfo cachedContextInfo;

    public FnSubsequenceWhere(final XQueryContext context, final FunctionSignature signature) {
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
        if (input.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // $from predicate (default: match first item)
        FunctionReference fromRef = null;
        if (args.length >= 2 && !args[1].isEmpty()) {
            fromRef = (FunctionReference) args[1].itemAt(0);
            fromRef.analyze(cachedContextInfo);
        }

        // $to predicate (default: no end match, include all remaining)
        FunctionReference toRef = null;
        if (args.length >= 3 && !args[2].isEmpty()) {
            toRef = (FunctionReference) args[2].itemAt(0);
            toRef.analyze(cachedContextInfo);
        }

        try {
            final int len = input.getItemCount();

            // Find start index: first item where $from returns true
            int startIdx = -1;
            if (fromRef == null) {
                startIdx = 0; // default: start from first item
            } else {
                for (int i = 0; i < len; i++) {
                    if (callPredicate(fromRef, input.itemAt(i), i + 1)) {
                        startIdx = i;
                        break;
                    }
                }
            }

            if (startIdx < 0) {
                return Sequence.EMPTY_SEQUENCE;
            }

            // Find end index: first item at or after start where $to returns true
            int endIdx = len - 1; // default: include all to end
            if (toRef != null) {
                endIdx = -1;
                for (int i = startIdx; i < len; i++) {
                    if (callPredicate(toRef, input.itemAt(i), i + 1)) {
                        endIdx = i;
                        break;
                    }
                }
                if (endIdx < 0) {
                    // No match for $to — per spec, include all remaining
                    endIdx = len - 1;
                }
            }

            // Build result
            final ValueSequence result = new ValueSequence(endIdx - startIdx + 1);
            for (int i = startIdx; i <= endIdx; i++) {
                result.add(input.itemAt(i));
            }
            return result;

        } finally {
            if (fromRef != null) {
                fromRef.close();
            }
            if (toRef != null) {
                toRef.close();
            }
        }
    }

    private boolean callPredicate(final FunctionReference ref, final Item item, final int position) throws XPathException {
        final int arity = ref.getSignature().getArgumentCount();
        final Sequence result;
        if (arity == 0) {
            result = ref.evalFunction(null, null, new Sequence[]{});
        } else if (arity == 1) {
            result = ref.evalFunction(null, null, new Sequence[]{item.toSequence()});
        } else {
            result = ref.evalFunction(null, null, new Sequence[]{item.toSequence(), new IntegerValue(this, position)});
        }

        if (result.isEmpty()) {
            return false;
        }

        // Must be xs:boolean — EBV of other types is not allowed
        final Item resultItem = result.itemAt(0);
        if (resultItem.getType() == Type.BOOLEAN) {
            return ((BooleanValue) resultItem).getValue();
        }

        // Check if it's a map (maps can be used as predicates)
        if (resultItem instanceof org.exist.xquery.functions.map.AbstractMapType) {
            // Map used as predicate: look up the item in the map
            final org.exist.xquery.functions.map.AbstractMapType map =
                    (org.exist.xquery.functions.map.AbstractMapType) resultItem;
            final Sequence mapResult = map.get((AtomicValue) item.atomize());
            if (mapResult == null || mapResult.isEmpty()) {
                return false;
            }
            return mapResult.effectiveBooleanValue();
        }

        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Predicate in subsequence-where must return xs:boolean, got " + Type.getTypeName(resultItem.getType()));
    }
}
