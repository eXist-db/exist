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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.exist.xquery.functions.array.ArrayType;

/**
 * Implements XQuery 4.0 higher-order functions:
 * fn:index-where, fn:take-while, fn:do-until, fn:while-do, fn:sort-with,
 * fn:scan-left, fn:scan-right.
 */
public class FnHigherOrderFun40 extends BasicFunction {

    public static final FunctionSignature FN_INDEX_WHERE = new FunctionSignature(
            new QName("index-where", Function.BUILTIN_FUNCTION_NS),
            "Returns the positions of items that match the supplied predicate.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The predicate function")
            },
            new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "positions where the predicate is true"));

    public static final FunctionSignature FN_TAKE_WHILE = new FunctionSignature(
            new QName("take-while", Function.BUILTIN_FUNCTION_NS),
            "Returns items from the input sequence prior to the first one that fails to match a supplied predicate.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The predicate function")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the leading items matching the predicate"));

    public static final FunctionSignature FN_WHILE_DO = new FunctionSignature(
            new QName("while-do", Function.BUILTIN_FUNCTION_NS),
            "Processes a supplied value repeatedly, continuing while a condition is true.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The initial input"),
                    new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The condition to test"),
                    new FunctionParameterSequenceType("action", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The action to apply")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the first value that fails the predicate"));

    public static final FunctionSignature FN_DO_UNTIL = new FunctionSignature(
            new QName("do-until", Function.BUILTIN_FUNCTION_NS),
            "Processes a supplied value repeatedly, continuing until a condition becomes true.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The initial input"),
                    new FunctionParameterSequenceType("action", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The action to apply"),
                    new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The condition to test")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the first value that satisfies the predicate"));

    public static final FunctionSignature FN_SORT_WITH = new FunctionSignature(
            new QName("sort-with", Function.BUILTIN_FUNCTION_NS),
            "Sorts a sequence according to a supplied comparator function.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence to sort"),
                    new FunctionParameterSequenceType("comparators", Type.FUNCTION, Cardinality.ONE_OR_MORE, "The comparator function(s)")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the sorted sequence"));

    public static final FunctionSignature FN_SCAN_LEFT = new FunctionSignature(
            new QName("scan-left", Function.BUILTIN_FUNCTION_NS),
            "Returns successive partial results of fold-left.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("init", Type.ITEM, Cardinality.ZERO_OR_MORE, "The initial value"),
                    new FunctionParameterSequenceType("action", Type.FUNCTION, Cardinality.EXACTLY_ONE,
                            "The accumulation function: fn(accumulator, item) as item()*")
            },
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE,
                    "sequence of single-member arrays with successive fold results"));

    public static final FunctionSignature FN_SCAN_RIGHT = new FunctionSignature(
            new QName("scan-right", Function.BUILTIN_FUNCTION_NS),
            "Returns successive partial results of fold-right.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("init", Type.ITEM, Cardinality.ZERO_OR_MORE, "The initial value"),
                    new FunctionParameterSequenceType("action", Type.FUNCTION, Cardinality.EXACTLY_ONE,
                            "The accumulation function: fn(item, accumulator) as item()*")
            },
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE,
                    "sequence of single-member arrays with successive fold results"));

    private AnalyzeContextInfo cachedContextInfo;

    public FnHigherOrderFun40(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("while-do")) {
            return whileDo(args);
        } else if (isCalledAs("do-until")) {
            return doUntil(args);
        } else if (isCalledAs("sort-with")) {
            return sortWith(args);
        } else if (isCalledAs("scan-left")) {
            return scanLeft(args);
        } else if (isCalledAs("scan-right")) {
            return scanRight(args);
        }

        final Sequence input = args[0];
        if (input.isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = ref.getSignature().getArgumentCount();

            if (isCalledAs("index-where")) {
                return indexWhere(input, ref, arity);
            } else {
                return takeWhile(input, ref, arity);
            }
        }
    }

    private Sequence indexWhere(final Sequence input, final FunctionReference ref, final int arity) throws XPathException {
        final ValueSequence result = new ValueSequence();
        int pos = 1;
        for (final SequenceIterator i = input.iterate(); i.hasNext(); pos++) {
            final Item item = i.nextItem();
            final Sequence r = callPredicate(ref, item, pos, arity);
            if (!r.isEmpty() && r.effectiveBooleanValue()) {
                result.add(new IntegerValue(this, pos));
            }
        }
        return result;
    }

    private Sequence takeWhile(final Sequence input, final FunctionReference ref, final int arity) throws XPathException {
        final ValueSequence result = new ValueSequence();
        int pos = 1;
        for (final SequenceIterator i = input.iterate(); i.hasNext(); pos++) {
            final Item item = i.nextItem();
            final Sequence r = callPredicate(ref, item, pos, arity);
            if (r.isEmpty() || !r.effectiveBooleanValue()) {
                break;
            }
            result.add(item);
        }
        return result;
    }

    private Sequence callPredicate(final FunctionReference ref, final Item item, final int pos, final int arity) throws XPathException {
        if (arity == 1) {
            return ref.evalFunction(null, null, new Sequence[]{item.toSequence()});
        } else {
            return ref.evalFunction(null, null, new Sequence[]{item.toSequence(), new IntegerValue(this, pos)});
        }
    }

    private Sequence callWithSeqAndPos(final FunctionReference ref, final Sequence input, final int pos, final int arity) throws XPathException {
        if (arity == 1) {
            return ref.evalFunction(null, null, new Sequence[]{input});
        } else {
            return ref.evalFunction(null, null, new Sequence[]{input, new IntegerValue(this, pos)});
        }
    }

    private Sequence whileDo(final Sequence[] args) throws XPathException {
        Sequence input = args[0];
        try (final FunctionReference predicate = (FunctionReference) args[1].itemAt(0);
             final FunctionReference action = (FunctionReference) args[2].itemAt(0)) {
            predicate.analyze(cachedContextInfo);
            action.analyze(cachedContextInfo);
            final int predArity = predicate.getSignature().getArgumentCount();
            final int actArity = action.getSignature().getArgumentCount();
            int pos = 1;
            while (true) {
                final Sequence test = callWithSeqAndPos(predicate, input, pos, predArity);
                if (test.isEmpty() || !test.effectiveBooleanValue()) {
                    return input;
                }
                input = callWithSeqAndPos(action, input, pos, actArity);
                pos++;
            }
        }
    }

    private Sequence doUntil(final Sequence[] args) throws XPathException {
        Sequence input = args[0];
        try (final FunctionReference action = (FunctionReference) args[1].itemAt(0);
             final FunctionReference predicate = (FunctionReference) args[2].itemAt(0)) {
            action.analyze(cachedContextInfo);
            predicate.analyze(cachedContextInfo);
            final int actArity = action.getSignature().getArgumentCount();
            final int predArity = predicate.getSignature().getArgumentCount();
            int pos = 1;
            while (true) {
                input = callWithSeqAndPos(action, input, pos, actArity);
                final Sequence test = callWithSeqAndPos(predicate, input, pos, predArity);
                if (!test.isEmpty() && test.effectiveBooleanValue()) {
                    return input;
                }
                pos++;
            }
        }
    }

    private Sequence sortWith(final Sequence[] args) throws XPathException {
        final Sequence input = args[0];
        if (input.getItemCount() <= 1) {
            return input;
        }
        final Sequence comparators = args[1];

        // Collect all items into a list
        final List<Item> items = new ArrayList<>(input.getItemCount());
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            items.add(i.nextItem());
        }

        // Get the first comparator (most test cases use a single one)
        final FunctionReference[] comparatorRefs = new FunctionReference[comparators.getItemCount()];
        for (int c = 0; c < comparators.getItemCount(); c++) {
            comparatorRefs[c] = (FunctionReference) comparators.itemAt(c);
            comparatorRefs[c].analyze(cachedContextInfo);
        }

        // Sort using the comparator(s)
        try {
            items.sort((a, b) -> {
                try {
                    for (final FunctionReference comp : comparatorRefs) {
                        final Sequence result = comp.evalFunction(null, null,
                                new Sequence[]{a.toSequence(), b.toSequence()});
                        final long cmp = ((IntegerValue) result.itemAt(0)).getLong();
                        if (cmp != 0) {
                            return Long.compare(cmp, 0);
                        }
                    }
                    return 0;
                } catch (final XPathException e) {
                    throw new RuntimeException(e);
                }
            });
        } catch (final RuntimeException e) {
            if (e.getCause() instanceof XPathException) {
                throw (XPathException) e.getCause();
            }
            throw e;
        }

        final ValueSequence result = new ValueSequence(items.size());
        for (final Item item : items) {
            result.add(item);
        }
        return result;
    }

    private Sequence scanLeft(final Sequence[] args) throws XPathException {
        final Sequence input = args[0];
        Sequence accumulator = args[1];
        try (final FunctionReference action = (FunctionReference) args[2].itemAt(0)) {
            action.analyze(cachedContextInfo);

            final int count = input.getItemCount();
            final ValueSequence result = new ValueSequence(count + 1);

            // First element: [init]
            result.add(new ArrayType(this, context, Collections.singletonList(accumulator)));

            // For each input item, apply action and wrap result
            for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                accumulator = action.evalFunction(null, null,
                        new Sequence[]{accumulator, item.toSequence()});
                result.add(new ArrayType(this, context, Collections.singletonList(accumulator)));
            }

            return result;
        }
    }

    private Sequence scanRight(final Sequence[] args) throws XPathException {
        final Sequence input = args[0];
        final Sequence init = args[1];
        try (final FunctionReference action = (FunctionReference) args[2].itemAt(0)) {
            action.analyze(cachedContextInfo);

            // Collect items into a list for reverse iteration
            final List<Item> items = new ArrayList<>(input.getItemCount());
            for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
                items.add(i.nextItem());
            }

            // Build results from right to left
            final List<Sequence> results = new ArrayList<>(items.size() + 1);
            Sequence accumulator = init;
            results.add(accumulator);

            for (int idx = items.size() - 1; idx >= 0; idx--) {
                accumulator = action.evalFunction(null, null,
                        new Sequence[]{items.get(idx).toSequence(), accumulator});
                results.add(accumulator);
            }

            // Reverse so first result is fold-right of entire sequence
            Collections.reverse(results);

            final ValueSequence result = new ValueSequence(results.size());
            for (final Sequence s : results) {
                result.add(new ArrayType(this, context, Collections.singletonList(s)));
            }
            return result;
        }
    }
}
