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

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Implements XQuery 4.0 fn:transitive-closure.
 *
 * Applies a step function repeatedly starting from an initial set of items,
 * accumulating results until no new items are produced. Handles cycles
 * through deduplication.
 */
public class FnTransitiveClosure extends BasicFunction {

    private AnalyzeContextInfo cachedContextInfo;

    public static final FunctionSignature FN_TRANSITIVE_CLOSURE = new FunctionSignature(
            new QName("transitive-closure", Function.BUILTIN_FUNCTION_NS),
            "Returns the transitive closure of applying $step to $input. " +
                    "The step function is applied repeatedly until no new items are produced.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("node", Type.NODE, Cardinality.ZERO_OR_ONE, "The initial node"),
                    new FunctionParameterSequenceType("step", Type.FUNCTION, Cardinality.EXACTLY_ONE, "The step function")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "The transitive closure"));

    public FnTransitiveClosure(final XQueryContext context, final FunctionSignature signature) {
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

        final FunctionReference stepRef = (FunctionReference) args[1].itemAt(0);
        stepRef.analyze(cachedContextInfo);

        try {
            // Apply step to initial input to get first results
            Sequence current = applyStep(stepRef, input);
            if (current.isEmpty()) {
                return Sequence.EMPTY_SEQUENCE;
            }

            // Track all seen items using identity (for nodes) or value (for atomics)
            final ValueSequence allResults = new ValueSequence();
            final Set<Object> seen = new LinkedHashSet<>();

            // Add initial step results
            addNewItems(current, allResults, seen);

            // Iterate until no new items are found
            while (true) {
                final Sequence nextStep = applyStep(stepRef, current);
                if (nextStep.isEmpty()) {
                    break;
                }

                final ValueSequence newItems = new ValueSequence();
                for (final SequenceIterator i = nextStep.iterate(); i.hasNext(); ) {
                    final Item item = i.nextItem();
                    final Object key = itemKey(item);
                    if (seen.add(key)) {
                        newItems.add(item);
                        allResults.add(item);
                    }
                }

                if (newItems.isEmpty()) {
                    break;
                }
                current = newItems;
            }

            return allResults;
        } finally {
            stepRef.close();
        }
    }

    private Sequence applyStep(final FunctionReference stepRef, final Sequence input) throws XPathException {
        final ValueSequence result = new ValueSequence();
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final Sequence stepResult = stepRef.evalFunction(null, null, new Sequence[]{item.toSequence()});
            result.addAll(stepResult);
        }
        return result;
    }

    private static void addNewItems(final Sequence seq, final ValueSequence target, final Set<Object> seen) throws XPathException {
        for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final Object key = itemKey(item);
            if (seen.add(key)) {
                target.add(item);
            }
        }
    }

    private static Object itemKey(final Item item) {
        if (item instanceof NodeValue) {
            // Use the node itself for identity-based deduplication
            return ((NodeValue) item).getNode();
        }
        // For atomic values, use the value itself
        return item;
    }
}
