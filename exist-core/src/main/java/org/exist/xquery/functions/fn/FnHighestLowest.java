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
import java.util.List;

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
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
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

/**
 * Implements fn:highest and fn:lowest (XQuery 4.0).
 *
 * Returns items from the input having the highest/lowest key values.
 */
public class FnHighestLowest extends BasicFunction {

    public static final FunctionSignature[] FN_HIGHEST = {
            new FunctionSignature(
                    new QName("highest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the highest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with highest key")),
            new FunctionSignature(
                    new QName("highest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the highest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with highest key")),
            new FunctionSignature(
                    new QName("highest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the highest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI"),
                            new FunctionParameterSequenceType("key", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "Key function")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with highest key"))
    };

    public static final FunctionSignature[] FN_LOWEST = {
            new FunctionSignature(
                    new QName("lowest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the lowest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with lowest key")),
            new FunctionSignature(
                    new QName("lowest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the lowest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with lowest key")),
            new FunctionSignature(
                    new QName("lowest", Function.BUILTIN_FUNCTION_NS),
                    "Returns items with the lowest key value.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI"),
                            new FunctionParameterSequenceType("key", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "Key function")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "items with lowest key"))
    };

    private AnalyzeContextInfo cachedContextInfo;

    public FnHighestLowest(final XQueryContext context, final FunctionSignature signature) {
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

        // Resolve collation
        final Collator collator;
        if (args.length >= 2 && !args[1].isEmpty()) {
            collator = context.getCollator(args[1].getStringValue());
        } else {
            collator = context.getDefaultCollator();
        }

        // Resolve key function (default is data#1)
        FunctionReference keyRef = null;
        if (args.length >= 3 && !args[2].isEmpty()) {
            keyRef = (FunctionReference) args[2].itemAt(0);
            keyRef.analyze(cachedContextInfo);
        }

        final boolean findHighest = isCalledAs("highest");

        // Compute keys for all items
        final List<Item> items = new ArrayList<>(input.getItemCount());
        final List<AtomicValue> keys = new ArrayList<>(input.getItemCount());

        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            items.add(item);

            // Compute key: apply key function or default atomization (fn:data)
            final AtomicValue keyVal;
            if (keyRef != null) {
                final Sequence keyResult = keyRef.evalFunction(null, null, new Sequence[]{item.toSequence()});
                if (keyResult.isEmpty()) {
                    keyVal = null;
                } else {
                    AtomicValue kv = keyResult.itemAt(0).atomize();
                    if (kv.getType() == Type.UNTYPED_ATOMIC) {
                        kv = kv.convertTo(Type.DOUBLE);
                    }
                    keyVal = kv;
                }
            } else {
                // Default key is fn:data() — atomize the item directly
                final AtomicValue atomized = item.atomize();
                if (atomized.getType() == Type.UNTYPED_ATOMIC) {
                    keyVal = atomized.convertTo(Type.DOUBLE);
                } else {
                    keyVal = atomized;
                }
            }
            keys.add(keyVal);
        }

        // Find the extreme value
        AtomicValue extremeKey = null;
        for (final AtomicValue key : keys) {
            if (key == null || isNaN(key)) {
                continue;
            }
            if (extremeKey == null) {
                extremeKey = key;
            } else {
                final int cmp = key.compareTo(collator, extremeKey);
                if (findHighest ? cmp > 0 : cmp < 0) {
                    extremeKey = key;
                }
            }
        }

        if (extremeKey == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Collect all items with the extreme key value
        final ValueSequence result = new ValueSequence();
        for (int i = 0; i < items.size(); i++) {
            final AtomicValue key = keys.get(i);
            if (key != null && !isNaN(key) && key.compareTo(collator, extremeKey) == 0) {
                result.add(items.get(i));
            }
        }

        if (keyRef != null) {
            keyRef.close();
        }

        return result;
    }

    private static boolean isNaN(final AtomicValue v) {
        if (v instanceof DoubleValue) {
            return Double.isNaN(((DoubleValue) v).getDouble());
        }
        if (v instanceof FloatValue) {
            return Float.isNaN(((FloatValue) v).getValue());
        }
        return false;
    }
}
