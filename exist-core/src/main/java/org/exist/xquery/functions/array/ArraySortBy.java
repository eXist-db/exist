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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FunCompare;
import org.exist.xquery.functions.fn.FunData;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.NamedFunctionReference;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements array:sort-by (XQuery 4.0).
 *
 * Sorts a supplied array based on the value of sort keys supplied as
 * record (map) specifications with optional key, collation, and order fields.
 */
public class ArraySortBy extends BasicFunction {

    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("sort-by", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
            "Sorts the array based on sort key specifications.",
            new SequenceType[] {
                new FunctionParameterSequenceType("array", Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The input array"),
                new FunctionParameterSequenceType("keys", Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                    "Sort key records with optional key, collation, and order fields")
            },
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "the sorted array"))
    };

    private AnalyzeContextInfo cachedContextInfo = new AnalyzeContextInfo();

    public ArraySortBy(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int size = array.getSize();
        if (size <= 1) {
            return array;
        }

        final Sequence keys = args[1];

        // Parse sort key specifications
        final List<SortKey> sortKeys = new ArrayList<>();
        if (keys.isEmpty()) {
            final SortKey defaultKey = new SortKey();
            defaultKey.collator = context.getDefaultCollator();
            sortKeys.add(defaultKey);
        } else {
            for (final SequenceIterator ki = keys.iterate(); ki.hasNext(); ) {
                final AbstractMapType keyMap = (AbstractMapType) ki.nextItem();
                sortKeys.add(parseSortKey(keyMap));
            }
        }

        // Pre-compute sort keys for each member
        final Sequence[][] keyValues = new Sequence[size][sortKeys.size()];
        for (int idx = 0; idx < size; idx++) {
            final Sequence member = array.get(idx);
            for (int k = 0; k < sortKeys.size(); k++) {
                final SortKey sk = sortKeys.get(k);
                if (sk.keyFunction != null) {
                    keyValues[idx][k] = sk.keyFunction.evalFunction(null, null,
                            new Sequence[]{member});
                } else {
                    // Default: atomize members
                    final ValueSequence atomized = new ValueSequence();
                    for (final SequenceIterator mi = member.iterate(); mi.hasNext(); ) {
                        atomized.add(mi.nextItem().atomize());
                    }
                    keyValues[idx][k] = atomized;
                }
            }
        }

        // Build index array for stable sort
        final Integer[] indices = new Integer[size];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        try {
            java.util.Arrays.sort(indices, (a, b) -> {
                try {
                    for (int k = 0; k < sortKeys.size(); k++) {
                        final SortKey sk = sortKeys.get(k);
                        final int cmp = compareKeys(keyValues[a][k], keyValues[b][k], sk.collator);
                        if (cmp != 0) {
                            return sk.descending ? -cmp : cmp;
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

        // Build result array
        final List<Sequence> resultMembers = new ArrayList<>(size);
        for (final int idx : indices) {
            resultMembers.add(array.get(idx));
        }
        return new ArrayType(this, context, resultMembers);
    }

    private int compareKeys(final Sequence a, final Sequence b, final Collator collator) throws XPathException {
        final boolean emptyA = a.isEmpty();
        final boolean emptyB = b.isEmpty();
        if (emptyA && emptyB) return 0;
        if (emptyA) return -1;
        if (emptyB) return 1;

        final int len = Math.min(a.getItemCount(), b.getItemCount());
        for (int i = 0; i < len; i++) {
            final AtomicValue va = a.itemAt(i).atomize();
            final AtomicValue vb = b.itemAt(i).atomize();
            final int cmp = FunCompare.compare(va, vb, collator);
            if (cmp != 0) return cmp;
        }
        return Integer.compare(a.getItemCount(), b.getItemCount());
    }

    private SortKey parseSortKey(final AbstractMapType map) throws XPathException {
        final SortKey sk = new SortKey();

        final Sequence keySeq = map.get(new StringValue(this, "key"));
        if (keySeq != null && !keySeq.isEmpty()) {
            final Item keyItem = keySeq.itemAt(0);
            if (!(keyItem instanceof FunctionReference)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Expected function reference for 'key', got " + Type.getTypeName(keyItem.getType()));
            }
            sk.keyFunction = (FunctionReference) keyItem;
            sk.keyFunction.analyze(cachedContextInfo);
        }

        final Sequence collSeq = map.get(new StringValue(this, "collation"));
        if (collSeq != null && !collSeq.isEmpty()) {
            sk.collator = context.getCollator(collSeq.getStringValue(), ErrorCodes.FOCH0002);
        } else {
            sk.collator = context.getDefaultCollator();
        }

        final Sequence orderSeq = map.get(new StringValue(this, "order"));
        if (orderSeq != null && !orderSeq.isEmpty()) {
            sk.descending = "descending".equals(orderSeq.getStringValue());
        }

        return sk;
    }

    private static class SortKey {
        FunctionReference keyFunction;
        Collator collator;
        boolean descending;
    }
}
