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

import java.util.ArrayList;
import java.util.List;

import org.exist.xquery.functions.array.ArrayType;

/**
 * Implements fn:partition (XQuery 4.0).
 *
 * Splits a sequence into partitions based on a predicate function.
 * The predicate receives (current-partition, next-item, position) and
 * returns true to start a new partition.
 */
public class FnPartition extends BasicFunction {

    public static final FunctionSignature FN_PARTITION = new FunctionSignature(
            new QName("partition", Function.BUILTIN_FUNCTION_NS),
            "Splits a sequence into partitions based on a predicate function.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                    new FunctionParameterSequenceType("split-when", Type.FUNCTION, Cardinality.EXACTLY_ONE,
                            "Predicate: fn(current-partition, next-item, position) as xs:boolean?")
            },
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE, "sequence of arrays, each containing a partition"));

    private AnalyzeContextInfo cachedContextInfo;

    public FnPartition(final XQueryContext context, final FunctionSignature signature) {
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

        try (final FunctionReference splitWhen = (FunctionReference) args[1].itemAt(0)) {
            splitWhen.analyze(cachedContextInfo);
            final int arity = splitWhen.getSignature().getArgumentCount();

            final List<ValueSequence> partitions = new ArrayList<>();
            ValueSequence currentPartition = new ValueSequence();

            int pos = 1;
            for (final SequenceIterator i = input.iterate(); i.hasNext(); pos++) {
                final Item item = i.nextItem();

                if (pos == 1) {
                    // First item always starts the first partition
                    currentPartition.add(item);
                } else {
                    // Call predicate to decide if we should split
                    final Sequence splitResult;
                    if (arity == 1) {
                        splitResult = splitWhen.evalFunction(null, null,
                                new Sequence[]{currentPartition});
                    } else if (arity == 2) {
                        splitResult = splitWhen.evalFunction(null, null,
                                new Sequence[]{currentPartition, item.toSequence()});
                    } else {
                        splitResult = splitWhen.evalFunction(null, null,
                                new Sequence[]{currentPartition, item.toSequence(), new IntegerValue(this, pos)});
                    }

                    final boolean split = !splitResult.isEmpty() && splitResult.effectiveBooleanValue();
                    if (split) {
                        partitions.add(currentPartition);
                        currentPartition = new ValueSequence();
                    }
                    currentPartition.add(item);
                }
            }

            // Add the last partition
            if (!currentPartition.isEmpty()) {
                partitions.add(currentPartition);
            }

            // Convert to sequence of arrays — each partition item becomes an array member
            final ValueSequence result = new ValueSequence(partitions.size());
            for (final ValueSequence partition : partitions) {
                final List<Sequence> members = new ArrayList<>(partition.getItemCount());
                for (final SequenceIterator pi = partition.iterate(); pi.hasNext(); ) {
                    members.add(pi.nextItem().toSequence());
                }
                result.add(new ArrayType(this, context, members));
            }
            return result;
        }
    }
}
