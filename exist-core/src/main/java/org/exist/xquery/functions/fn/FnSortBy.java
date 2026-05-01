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

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.functions.map.AbstractMapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Implements XQuery 4.0 fn:sort-by.
 *
 * fn:sort-by($input, $keys) sorts a sequence based on sort key specifications
 * provided as records (maps) with optional key, collation, and order fields.
 */
public class FnSortBy extends BasicFunction {

    public static final FunctionSignature FN_SORT_BY = new FunctionSignature(
            new QName("sort-by", Function.BUILTIN_FUNCTION_NS),
            "Sorts a sequence based on sort key specifications.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence to sort"),
                    new FunctionParameterSequenceType("keys", Type.MAP_ITEM, Cardinality.ZERO_OR_MORE, "Sort key records with optional key, collation, and order fields")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the sorted sequence"));

    private AnalyzeContextInfo cachedContextInfo;

    public FnSortBy(final XQueryContext context, final FunctionSignature signature) {
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
        if (input.getItemCount() <= 1) {
            return input;
        }

        final Sequence keys = args[1];

        // Parse sort key specifications
        final List<SortKey> sortKeys = new ArrayList<>();
        if (keys.isEmpty()) {
            // Default: sort by fn:data#1 ascending
            final SortKey defaultKey = new SortKey();
            defaultKey.collator = context.getDefaultCollator();
            sortKeys.add(defaultKey);
        } else {
            for (final SequenceIterator ki = keys.iterate(); ki.hasNext(); ) {
                final AbstractMapType keyMap = (AbstractMapType) ki.nextItem();
                sortKeys.add(parseSortKey(keyMap));
            }
        }

        // Collect items
        final List<Item> items = new ArrayList<>(input.getItemCount());
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            items.add(i.nextItem());
        }

        // Pre-compute sort keys for each item
        final Sequence[][] keyValues = new Sequence[items.size()][sortKeys.size()];
        for (int idx = 0; idx < items.size(); idx++) {
            for (int k = 0; k < sortKeys.size(); k++) {
                final SortKey sk = sortKeys.get(k);
                if (sk.keyFunction != null) {
                    keyValues[idx][k] = sk.keyFunction.evalFunction(null, null,
                            new Sequence[]{items.get(idx).toSequence()});
                } else {
                    final Item item = items.get(idx);
                    if (item instanceof ArrayType) {
                        // Arrays use composite sort key: flatten members to atomic values
                        final ArrayType arr = (ArrayType) item;
                        final ValueSequence atomized = new ValueSequence(arr.getSize());
                        for (int m = 0; m < arr.getSize(); m++) {
                            final Sequence member = arr.get(m);
                            for (final SequenceIterator mi = member.iterate(); mi.hasNext(); ) {
                                atomized.add(mi.nextItem().atomize());
                            }
                        }
                        keyValues[idx][k] = atomized;
                    } else {
                        keyValues[idx][k] = item.atomize().toSequence();
                    }
                }
            }
        }

        // Build index array for stable sort
        final Integer[] indices = new Integer[items.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = i;
        }

        try {
            java.util.Arrays.sort(indices, (a, b) -> {
                try {
                    for (int k = 0; k < sortKeys.size(); k++) {
                        final SortKey sk = sortKeys.get(k);
                        final Sequence va = keyValues[a][k];
                        final Sequence vb = keyValues[b][k];
                        final int cmp = compareKeys(va, vb, sk.collator);
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

        final ValueSequence result = new ValueSequence(items.size());
        for (final int idx : indices) {
            result.add(items.get(idx));
        }
        return result;
    }

    private int compareKeys(final Sequence a, final Sequence b, final Collator collator) throws XPathException {
        final boolean emptyA = a.isEmpty();
        final boolean emptyB = b.isEmpty();
        if (emptyA && emptyB) {
            return 0;
        }
        if (emptyA) {
            return -1; // empty precedes non-empty
        }
        if (emptyB) {
            return 1;
        }
        // Lexicographic comparison for composite sort keys
        final int len = Math.min(a.getItemCount(), b.getItemCount());
        for (int i = 0; i < len; i++) {
            final AtomicValue va = a.itemAt(i).atomize();
            final AtomicValue vb = b.itemAt(i).atomize();
            // Type check: sort keys must be mutually comparable
            checkComparable(va, vb);
            final int cmp = FunCompare.compare(va, vb, collator);
            if (cmp != 0) {
                return cmp;
            }
        }
        // Shorter sequence is less
        return Integer.compare(a.getItemCount(), b.getItemCount());
    }

    /**
     * Check that two sort key values are of mutually comparable types.
     * Per XQ4 spec, it is XPTY0004 if they are not.
     */
    private void checkComparable(final AtomicValue va, final AtomicValue vb) throws XPathException {
        final int t1 = va.getType();
        final int t2 = vb.getType();
        // Same base type family is always comparable
        if (t1 == t2) {
            return;
        }
        // String-like types are mutually comparable
        if (isStringLike(t1) && isStringLike(t2)) {
            return;
        }
        // Numeric types are mutually comparable
        if (va instanceof org.exist.xquery.value.NumericValue && vb instanceof org.exist.xquery.value.NumericValue) {
            return;
        }
        // Same base type hierarchy is comparable
        if (Type.subTypeOf(t1, t2) || Type.subTypeOf(t2, t1)) {
            return;
        }
        throw new XPathException(this, ErrorCodes.XPTY0004,
                "Sort key values are not mutually comparable: " + Type.getTypeName(t1) + " and " + Type.getTypeName(t2));
    }

    private static boolean isStringLike(final int type) {
        return Type.subTypeOf(type, Type.STRING)
                || type == Type.UNTYPED_ATOMIC
                || Type.subTypeOf(type, Type.ANY_URI);
    }

    private SortKey parseSortKey(final AbstractMapType map) throws XPathException {
        final SortKey sk = new SortKey();

        // key field: function to extract sort key
        final Sequence keySeq = map.get(new org.exist.xquery.value.StringValue(this, "key"));
        if (keySeq != null && !keySeq.isEmpty()) {
            sk.keyFunction = (FunctionReference) keySeq.itemAt(0);
            sk.keyFunction.analyze(cachedContextInfo);
        }

        // collation field
        final Sequence collSeq = map.get(new org.exist.xquery.value.StringValue(this, "collation"));
        if (collSeq != null && !collSeq.isEmpty()) {
            sk.collator = context.getCollator(collSeq.getStringValue(), ErrorCodes.FOCH0002);
        } else {
            sk.collator = context.getDefaultCollator();
        }

        // order field: "ascending" (default) or "descending"
        final Sequence orderSeq = map.get(new org.exist.xquery.value.StringValue(this, "order"));
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
