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
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Implements array:sort-with (XQuery 4.0).
 *
 * Sorts a supplied array according to the order induced by one or more
 * supplied comparator functions. Sort is stable.
 */
public class ArraySortWith extends BasicFunction {

    public static final FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("sort-with", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
            "Sorts the array using the supplied comparator function(s).",
            new SequenceType[] {
                new FunctionParameterSequenceType("array", Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "The input array"),
                new FunctionParameterSequenceType("comparators", Type.FUNCTION, Cardinality.ONE_OR_MORE,
                    "One or more comparator functions (fn(item()*, item()*) as xs:integer)")
            },
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "the sorted array"))
    };

    private AnalyzeContextInfo cachedContextInfo = new AnalyzeContextInfo();

    public ArraySortWith(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int size = array.getSize();
        if (size <= 1) {
            return array;
        }

        // Collect comparator functions
        final Sequence comparatorsSeq = args[1];
        final List<FunctionReference> comparators = new ArrayList<>(comparatorsSeq.getItemCount());
        for (final SequenceIterator it = comparatorsSeq.iterate(); it.hasNext(); ) {
            final FunctionReference ref = (FunctionReference) it.nextItem();
            ref.analyze(cachedContextInfo);
            comparators.add(ref);
        }

        // Build list of (index, member) to sort
        final List<IndexedMember> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new IndexedMember(i, array.get(i)));
        }

        // Stable sort using comparator chain
        try {
            members.sort((a, b) -> {
                try {
                    for (final FunctionReference comp : comparators) {
                        final Sequence[] funcArgs = new Sequence[] { a.value, b.value };
                        final Sequence result = comp.evalFunction(null, null, funcArgs);
                        if (result.isEmpty()) {
                            continue;
                        }
                        final long cmp = ((IntegerValue) result.itemAt(0).convertTo(Type.INTEGER)).getLong();
                        if (cmp != 0) {
                            return cmp < 0 ? -1 : 1;
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
        for (final IndexedMember m : members) {
            resultMembers.add(m.value);
        }

        return new ArrayType(this, context, resultMembers);
    }

    private static class IndexedMember {
        final int index;
        final Sequence value;

        IndexedMember(int index, Sequence value) {
            this.index = index;
            this.value = value;
        }
    }
}
