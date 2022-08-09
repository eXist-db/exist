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
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunSort extends BasicFunction {

  public final static FunctionSignature[] signatures = {
    new FunctionSignature(
      new QName("sort", Function.BUILTIN_FUNCTION_NS),
        "Sorts a supplied sequence.",
      new SequenceType[] {
        new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "")
      },
      new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the first item or the empty sequence")
    ),
    new FunctionSignature(
      new QName("sort", Function.BUILTIN_FUNCTION_NS),
        "Sorts a supplied sequence, based on the value of a sort key supplied as a function.",
      new SequenceType[] {
          new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, ""),
          new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "")
      },
      new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the resulting sequence")
    ),
    new FunctionSignature(
        new QName("sort", Function.BUILTIN_FUNCTION_NS),
        "Sorts a supplied sequence, based on the value of a sort key supplied as a function.",
        new SequenceType[] {
            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, ""),
            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, ""),
            new FunctionParameterSequenceType("key", Type.FUNCTION, Cardinality.EXACTLY_ONE, "")
        },
        new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the resulting sequence")
    )
  };

  AnalyzeContextInfo cachedContextInfo;

  public FunSort(final XQueryContext context, final FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
    if (getContext().getXQueryVersion()<31) {
      throw new XPathException(this, ErrorCodes.EXXQDY0003,
          "Function " + getSignature().getName() + " is only supported for xquery version \"3.1\" and later.");
    }

    cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    super.analyze(cachedContextInfo);
  }

  @Override
  public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
    final Sequence seq = args[0];
    final Collator collator = collator(args, 1);
    final List<Sequence> keys = new ArrayList<>(seq.getItemCount());

    try (final FunctionReference ref = function(args, 2)) {
      final Sequence[] refArgs = new Sequence[1];
      for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
        final Item item = i.nextItem();
        final Sequence value;
        if (ref != null) {
          refArgs[0] = item.toSequence();
          value = ref.evalFunction(null, null, refArgs);
        } else {
          value = item.toSequence();
        }
        keys.add(Atomize.atomize(value));
      }
    }

    return sort(seq, keys, collator);
  }

  private Sequence sort(final Sequence seq, final List<Sequence> keys, final Collator collator) throws XPathException {
    // prepare
    final int size = seq.getItemCount();
    final Integer[] order = new Integer[size];
    for (int i = 0; i < size; i++) {
      order[i] = i;
    }

    // sort
    final FnSortComparator fnSortComparator = new FnSortComparator(keys, collator);
    try {
      Arrays.sort(order, fnSortComparator);
    } catch (final FnSortComparator.SortCompareException e) {
      throw (XPathException) e.getCause();
    }

    // form the final sequence
    final ValueSequence result = new ValueSequence(seq.getItemCount());
    result.keepUnOrdered(true);

    for(int i = 0; i < size; i++) {
      result.add(seq.itemAt(order[i]));
    }

    return result;
  }

  private static class FnSortComparator implements Comparator<Integer> {
    private final List<Sequence> keys;
    @Nullable private final Collator collator;

    private FnSortComparator(final List<Sequence> keys, final @Nullable Collator collator) {
      this.keys = keys;
      this.collator = collator;
    }

    @Override
    public int compare(final Integer i1, final Integer i2) {
      final Sequence seq1 = keys.get(i1);
      final Sequence seq2 = keys.get(i2);

      // If (fn:deep-equal($key($A), $key($B), $C), then the relative order of $A and $B in the output
      // is the same as their relative order in the input (that is, the sort is stable)
      if (FunDeepEqual.deepEqualsSeq(seq1, seq2, collator)) {
        return Constants.EQUAL;
      }

      // Otherwise, if (deep-less-than($key($A), $key($B), $C), then $A precedes $B in the output.
      try {
        if (deepLessThan(seq1, seq2, collator)) {
          return Constants.INFERIOR;
        } else {
          return Constants.SUPERIOR;
        }
      } catch (final XPathException e) {
        throw new SortCompareException(e);
      }
    }

    public static class SortCompareException extends RuntimeException {
      public SortCompareException(final XPathException e) {
        super(e);
      }
    }

    /**
     * The function deep-less-than is defined as the boolean result of the expression:
     *
     * if (fn:empty($A))
     *      then fn:exists($B)
     * else if (fn:empty($B))           See - https://xmlcom.slack.com/archives/C011NLXE4DU/p1659977972377909
     *      then fn:false()
     * else if (fn:deep-equal($A[1], $B[1], $C))
     *      then deep-less-than(fn:tail($A), fn:tail($B), $C)
     * else if ($A[1] ne $A[1] (:that is, $A[1] is NaN:))
     *      then fn:true()
     * else if (is-string($A[1]) and is-string($B[1])
     *      then fn:compare($A[1], $B[1], $C) lt 0
     * else $A[1] lt $B[1]
     */
    private static boolean deepLessThan(final Sequence seq1, final Sequence seq2, @Nullable final Collator collator) throws XPathException {
      if (seq1.isEmpty()) {
        return !seq2.isEmpty();
      }

      if (seq2.isEmpty()) {
        return false;
      }

      final Item seq1Item1 = seq1.itemAt(0);
      final Item seq2Item1 = seq2.itemAt(0);

      if (FunDeepEqual.deepEquals(seq1Item1, seq2Item1, collator)) {
        return deepLessThan(seq1.tail(), seq2.tail(), collator);
      }

      if (Type.subTypeOfUnion(seq1Item1.getType(), Type.NUMERIC) && ((NumericValue)seq1Item1).isNaN()) {
        return true;
      }

      if (deepLessThanIsString(seq1Item1) && deepLessThanIsString(seq2Item1)) {
        return FunCompare.compare(seq1Item1, seq2Item1, collator) < 0;
      }

      return ValueComparison.compareAtomic(collator, seq1Item1.atomize(), seq2Item1.atomize(), Constants.StringTruncationOperator.NONE, Constants.Comparison.LT);
    }

    private static boolean deepLessThanIsString(final Item item) {
      return Type.subTypeOf(item.getType(), Type.STRING) || item.getType() == Type.ANY_URI || item.getType() == Type.UNTYPED_ATOMIC;
    }
  }

  private Collator collator(final Sequence[] args, final int pos) throws XPathException {
    if (args.length > pos) {
      if (args[pos].isEmpty()) {
        return context.getDefaultCollator();
      }
      final String collationURI = args[pos].getStringValue();
      return context.getCollator(collationURI);
    } else {
      return context.getDefaultCollator();
    }
  }

  private @Nullable FunctionReference function(final Sequence[] args, final int pos) throws XPathException {
    if (args.length > pos) {
      final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
      if (ref != null) {
        // need to create a new AnalyzeContextInfo to avoid memory leak
        // cachedContextInfo will stay in memory
        ref.analyze(new AnalyzeContextInfo(cachedContextInfo));
      }
      return ref;
    } else {
      return null;
    }
  }
}
