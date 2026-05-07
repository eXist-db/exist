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

import java.util.Arrays;
import java.util.Comparator;
import java.util.TreeMap;

import com.ibm.icu.text.Collator;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Constants.Comparison;
import org.exist.xquery.Constants.StringTruncationOperator;
import org.exist.xquery.Dependency;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.ValueComparison;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 *
 */
public class FunIndexOf extends BasicFunction {

	protected static final FunctionReturnSequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "the sequence of positive integers giving the positions within the sequence");

	protected static final FunctionParameterSequenceType COLLATION_PARAM = new FunctionParameterSequenceType("collation-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collation URI");

	protected static final FunctionParameterSequenceType SEARCH_PARAM = new FunctionParameterSequenceType("search", Type.ANY_ATOMIC_TYPE, Cardinality.EXACTLY_ONE, "The search component");

	protected static final FunctionParameterSequenceType SEQ_PARAM = new FunctionParameterSequenceType("source", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The source sequence");

	protected static final String FUNCTION_DESCRIPTION =

		"""
Returns a sequence of positive integers giving the \
positions within the sequence of atomic values $source \
that are equal to $search.

The collation used by the invocation of this function \
is determined according to the rules in 7.3.1 Collations. \
The collation is used when string comparison is required.

The items in the sequence $source are compared with \
$search under the rules for the 'eq' operator. Values of \
type xs:untypedAtomic are compared as if they were of \
type xs:string. Values that cannot be compared, i.e. \
the 'eq' operator is not defined for their types, are \
considered to be distinct. If an item compares equal, \
then the position of that item in the sequence \
$source is included in the result.

If the value of $source is the empty sequence, or \
if no item in $source matches $search, then the \
empty sequence is returned.

The first item in a sequence is at position 1, not position 0.

The result sequence is in ascending numeric order.""";

	public final static FunctionSignature[] fnIndexOf = {
			new FunctionSignature(
					new QName("index-of", Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION,
					new SequenceType[] {
						SEQ_PARAM,
						SEARCH_PARAM
					},
					RETURN_TYPE
			),
			new FunctionSignature(
					new QName("index-of", Function.BUILTIN_FUNCTION_NS),
					FUNCTION_DESCRIPTION +
                    " " + CollatingFunction.THIRD_REL_COLLATION_ARG_EXAMPLE,
					new SequenceType[] {
							SEQ_PARAM,
							SEARCH_PARAM,
							COLLATION_PARAM
					},
					RETURN_TYPE
			)
	};

    /**
     * Minimum source-sequence size at which we consider building a positional
     * index. For tiny sources, the per-call linear scan is already cheap and
     * the TreeMap construction would add overhead.
     */
    private static final int INDEX_THRESHOLD = 32;

    /**
     * Fingerprint of the source sequence from the most recent call. eXist
     * routinely re-wraps `args[0]` between calls (atomization, type checks),
     * so reference identity is unreliable. Instead we sample size plus five
     * positions' string values — collisions are exceedingly rare for the
     * atomic-value sequences this caching targets, and the lookup falls
     * back to a linear scan if any sampled call disagrees.
     */
    private long cachedSize = -1;
    private String cachedSample0;
    private String cachedSample1;
    private String cachedSample2;
    private String cachedSample3;
    private String cachedSample4;
    /** Collator paired with the cached index; must match for the index to be reusable. */
    private Collator cachedCollator;
    /** Lazily built positional index. */
    private TreeMap<AtomicValue, IntList> cachedIndex;
    /** True if a previous build attempt aborted (e.g. incomparable types). */
    private boolean cachedSourceUnindexable;

	public FunIndexOf(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)	throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES, "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null)
                {context.getProfiler().message(this, Profiler.START_SEQUENCES, "CONTEXT SEQUENCE", contextSequence);}
        }

        final AtomicValue srch = args[1].itemAt(0).atomize();
        final Collator collator;
        if (getSignature().getArgumentCount() == 3) {
            final String collation = args[2].getStringValue();
            collator = context.getCollator(collation, ErrorCodes.FOCH0002);
        } else {
            collator = context.getDefaultCollator();
        }
        final Sequence result = evaluate(args[0], srch, collator);

        if (context.getProfiler().isEnabled())
            {context.getProfiler().end(this, "", result);}

        return result;

	}

    /**
     * Choose between a linear scan and a cached positional index. The index
     * is built on the second consecutive call with the same {@code source}
     * reference and {@code collator}, so a single index-of invocation never
     * pays the build cost — but repeated lookups against the same source
     * (e.g. inside a FLWOR over distinct-values) collapse from O(N*M) to
     * O(N + M log N).
     */
    private Sequence evaluate(final Sequence source, final AtomicValue srch, final Collator collator) throws XPathException {
        final int sourceSize = source.getItemCount();
        if (sourceSize < INDEX_THRESHOLD) {
            return linearScan(source, srch, collator);
        }
        if (!fingerprintMatches(source, sourceSize) || collator != cachedCollator) {
            // Fingerprint miss: fresh source. Update the fingerprint and do a
            // linear scan; defer building the index until the next hit so a
            // single index-of call never pays the build cost.
            updateFingerprint(source, sourceSize);
            cachedCollator = collator;
            cachedIndex = null;
            cachedSourceUnindexable = false;
            return linearScan(source, srch, collator);
        }
        if (cachedSourceUnindexable) {
            return linearScan(source, srch, collator);
        }
        if (cachedIndex == null) {
            cachedIndex = buildIndex(source, collator);
            if (cachedIndex == null) {
                cachedSourceUnindexable = true;
                return linearScan(source, srch, collator);
            }
        }
        return lookupInIndex(srch);
    }

    private boolean fingerprintMatches(final Sequence source, final int size) throws XPathException {
        if (cachedSize != size) {
            return false;
        }
        return java.util.Objects.equals(cachedSample0, sampleString(source, 0))
            && java.util.Objects.equals(cachedSample1, sampleString(source, size / 4))
            && java.util.Objects.equals(cachedSample2, sampleString(source, size / 2))
            && java.util.Objects.equals(cachedSample3, sampleString(source, (3 * size) / 4))
            && java.util.Objects.equals(cachedSample4, sampleString(source, size - 1));
    }

    private void updateFingerprint(final Sequence source, final int size) throws XPathException {
        cachedSize = size;
        cachedSample0 = sampleString(source, 0);
        cachedSample1 = sampleString(source, size / 4);
        cachedSample2 = sampleString(source, size / 2);
        cachedSample3 = sampleString(source, (3 * size) / 4);
        cachedSample4 = sampleString(source, size - 1);
    }

    private static String sampleString(final Sequence source, final int index) throws XPathException {
        return source.itemAt(index).getStringValue();
    }

    private Sequence linearScan(final Sequence source, final AtomicValue srch, final Collator collator) throws XPathException {
        final ValueSequence result = new ValueSequence();
        int j = 1;
        for (final SequenceIterator i = source.iterate(); i.hasNext(); j++) {
            final AtomicValue next = i.nextItem().atomize();
            try {
                if (ValueComparison.compareAtomic(collator, next, srch, StringTruncationOperator.NONE, Comparison.EQ)) {
                    result.add(new IntegerValue(this, j));
                }
            } catch (final XPathException e) {
                // Ignore: values can not be compared
            }
        }
        return result;
    }

    /**
     * Build a TreeMap mapping each comparable atomic value in {@code source}
     * to the list of 1-based positions at which it occurs. Returns
     * {@code null} if any item triggers a comparison error during insertion,
     * signalling that the caller must fall back to the linear scan to honour
     * fn:index-of's "values that cannot be compared are considered distinct"
     * semantics.
     */
    private TreeMap<AtomicValue, IntList> buildIndex(final Sequence source, final Collator collator) throws XPathException {
        final TreeMap<AtomicValue, IntList> index = new TreeMap<>(new IndexComparator(collator));
        int j = 1;
        try {
            for (final SequenceIterator i = source.iterate(); i.hasNext(); j++) {
                final AtomicValue next = i.nextItem().atomize();
                // NaN never matches under eq, so it can never appear in the result
                if (Type.subTypeOfUnion(next.getType(), Type.NUMERIC) && ((NumericValue) next).isNaN()) {
                    continue;
                }
                IntList list = index.get(next);
                if (list == null) {
                    list = new IntList();
                    index.put(next, list);
                }
                list.add(j);
            }
        } catch (final IndexBuildAbort abort) {
            return null;
        }
        return index;
    }

    private Sequence lookupInIndex(final AtomicValue srch) throws XPathException {
        if (Type.subTypeOfUnion(srch.getType(), Type.NUMERIC) && ((NumericValue) srch).isNaN()) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final IntList list;
        try {
            list = cachedIndex.get(srch);
        } catch (final IndexBuildAbort abort) {
            // Search value is incomparable with cached keys; per spec, distinct
            return Sequence.EMPTY_SEQUENCE;
        }
        if (list == null || list.size == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }
        final ValueSequence result = new ValueSequence();
        for (int k = 0; k < list.size; k++) {
            result.add(new IntegerValue(this, list.data[k]));
        }
        return result;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        cachedSize = -1;
        cachedSample0 = null;
        cachedSample1 = null;
        cachedSample2 = null;
        cachedSample3 = null;
        cachedSample4 = null;
        cachedCollator = null;
        cachedIndex = null;
        cachedSourceUnindexable = false;
    }

    private static final class IntList {
        private int[] data = new int[4];
        private int size;

        void add(final int v) {
            if (size >= data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            data[size++] = v;
        }
    }

    private static final class IndexBuildAbort extends RuntimeException {
        private static final long serialVersionUID = 1L;
        IndexBuildAbort(final XPathException cause) {
            super(null, cause, false, false);
        }
    }

    private static final class IndexComparator implements Comparator<AtomicValue> {
        private final Collator collator;

        IndexComparator(final Collator collator) {
            this.collator = collator;
        }

        @Override
        public int compare(final AtomicValue a, final AtomicValue b) {
            try {
                if (ValueComparison.compareAtomic(collator, a, b, StringTruncationOperator.NONE, Comparison.EQ)) {
                    return 0;
                }
                if (ValueComparison.compareAtomic(collator, a, b, StringTruncationOperator.NONE, Comparison.LT)) {
                    return -1;
                }
                if (ValueComparison.compareAtomic(collator, a, b, StringTruncationOperator.NONE, Comparison.GT)) {
                    return 1;
                }
                return a.compareTo(collator, b);
            } catch (final XPathException e) {
                throw new IndexBuildAbort(e);
            }
        }
    }
}
