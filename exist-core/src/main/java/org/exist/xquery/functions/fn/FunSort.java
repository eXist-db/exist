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
import org.exist.util.Collations;
import org.exist.xquery.*;
import org.exist.xquery.value.AtomicValue;
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

import java.util.ArrayList;
import java.util.Arrays;

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

  public FunSort(XQueryContext context, FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
    if (getContext().getXQueryVersion()<31) {
      throw new XPathException(this, ErrorCodes.EXXQDY0003,
          "Function " + getSignature().getName() + " is only supported for xquery version \"3.1\" and later.");
    }

    cachedContextInfo = new AnalyzeContextInfo(contextInfo);
    super.analyze(cachedContextInfo);
  }

  @Override
  public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    Sequence seq = args[0];

    Collator collator = collator(args, 1);
    ArrayList<Sequence> keys = new ArrayList<>(seq.getItemCount());

    try (FunctionReference ref = function(args, 2)) {

      final Sequence[] refArgs = new Sequence[1];

      Item item;
      Sequence value;

      for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
        item = i.nextItem();
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

  private Sequence sort(Sequence seq, ArrayList<Sequence> keys, Collator collator) throws XPathException {

    final Holder<XPathException> exception = new Holder<>();

    //preparing
    final int size = seq.getItemCount();
    final Integer[] order = new Integer[size];
    for(int i = 0; i < size; i++) order[i] = i;

    //sorting
    try {
      Arrays.sort(order, (i1, i2) -> {

        Sequence seq1 = keys.get(i1);
        Sequence seq2 = keys.get(i2);

        int size1 = seq1.getItemCount();
        int size2 = seq2.getItemCount();
        int minSize = Math.min(size1, size2);

        if (size1 == 0) {
          return -size2;
        }

        for (int pos = 0; pos < minSize; pos++) {
          Item item1 = seq1.itemAt(pos);
          Item item2 = seq2.itemAt(pos);

//          int res;
//          if (item1 instanceof org.exist.dom.memtree.NodeImpl && (!(item2 instanceof org.exist.dom.memtree.NodeImpl))) {
//            res = Constants.INFERIOR;
//          } else if (item1 instanceof Comparable && item2 instanceof Comparable) {
//            res = ((Comparable) item1).compareTo(item2);
//          } else {
//            res = Constants.INFERIOR;
//          }

          int res = Constants.EQUAL;
          if (FunDeepEqual.deepEquals(item1, item2, collator)) {
            continue;
          } if (Type.subTypeOfUnion(item1.getType(), Type.NUMERIC) && ((NumericValue)item1).isNaN()) {
            res = Constants.INFERIOR;

          } else if (Type.subTypeOf(item1.getType(), Type.STRING) && Type.subTypeOf(item2.getType(), Type.STRING)) {
            try {
              res = Collations.compare(collator, item1.getStringValue(), item2.getStringValue());
            } catch (XPathException e) {
              exception.set(e);
            }
          } else if (item1 instanceof AtomicValue && item2 instanceof AtomicValue) {
            try {
              // throw type error if values cannot be compared with lt
              ValueComparison.compareAtomic(collator, (AtomicValue)item1, (AtomicValue)item2, Constants.StringTruncationOperator.NONE, Constants.Comparison.LT);
              res = ((AtomicValue)item1).compareTo(collator, (AtomicValue)item2);
            } catch (XPathException e) {
              exception.set(e);
              throw new IllegalArgumentException();
            }

//          } else if (item1 instanceof Comparable && item2 instanceof Comparable) {
//            res = ((Comparable) item1).compareTo(item2);

          } else {
            res = Constants.INFERIOR;
          }

          if (res != Constants.EQUAL) {
            return res;
          }
        }

        return (size1 - size2);
      });
    } catch (IllegalArgumentException e) {
      if (exception.get() != null) {
        throw new XPathException(FunSort.this, ErrorCodes.XPTY0004, exception.get());
      } else {
        throw new XPathException(FunSort.this, ErrorCodes.XPTY0004, e.getMessage());
      }
    }

    //form final sequence
    final ValueSequence result = new ValueSequence(seq.getItemCount());
    result.keepUnOrdered(true);

    for(int i = 0; i < size; i++) {
      result.add(seq.itemAt(order[i]));
    }

    return result;
  }

  private Collator collator(Sequence[] args, int pos) throws XPathException {
    if (args.length > pos) {
      if (args[pos].isEmpty()) {
        return context.getDefaultCollator();
      }
      String collationURI = args[pos].getStringValue();
      return context.getCollator(collationURI);
    } else {
      return context.getDefaultCollator();
    }
  }

  private FunctionReference function(Sequence[] args, int pos) throws XPathException {
    if (args.length > pos) {
      FunctionReference ref = (FunctionReference) args[2].itemAt(0);
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

  static class Holder<T> {
    T obj;

    public void set(T obj) {
      this.obj = obj;
    }

    public T get() {
      return obj;
    }
  }
}