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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AtomicValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements fn:all-equal and fn:all-different (XQuery 4.0).
 */
public class FnAllEqualDifferent extends BasicFunction {

    public static final FunctionSignature[] FN_ALL_EQUAL = {
            new FunctionSignature(
                    new QName("all-equal", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if all items in the supplied sequence are equal.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The values to compare")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all values are equal")),
            new FunctionSignature(
                    new QName("all-equal", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if all items in the supplied sequence are equal, using the specified collation.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The values to compare"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all values are equal"))
    };

    public static final FunctionSignature[] FN_ALL_DIFFERENT = {
            new FunctionSignature(
                    new QName("all-different", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if no two items in the supplied sequence are equal.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The values to compare")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all values are different")),
            new FunctionSignature(
                    new QName("all-different", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if no two items in the supplied sequence are equal, using the specified collation.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("values", Type.ANY_ATOMIC_TYPE, Cardinality.ZERO_OR_MORE, "The values to compare"),
                            new FunctionParameterSequenceType("collation", Type.STRING, Cardinality.ZERO_OR_ONE, "The collation URI")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all values are different"))
    };

    public FnAllEqualDifferent(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final Sequence values = args[0];
        if (values.getItemCount() <= 1) {
            return BooleanValue.TRUE;
        }

        final Collator collator = getCollator(args);

        // Collect all atomized values
        final java.util.List<AtomicValue> items = new java.util.ArrayList<>(values.getItemCount());
        for (final SequenceIterator i = values.iterate(); i.hasNext(); ) {
            items.add(i.nextItem().atomize());
        }

        if (isCalledAs("all-equal")) {
            return allEqual(items, collator);
        } else {
            return allDifferent(items, collator);
        }
    }

    private Sequence allEqual(final java.util.List<AtomicValue> items, final Collator collator) throws XPathException {
        // all-equal iff count(distinct-values) <= 1, using contextual equality
        final AtomicValue first = items.get(0);
        for (int i = 1; i < items.size(); i++) {
            if (!contextuallyEqual(first, items.get(i), collator)) {
                return BooleanValue.FALSE;
            }
        }
        return BooleanValue.TRUE;
    }

    private Sequence allDifferent(final java.util.List<AtomicValue> items, final Collator collator) throws XPathException {
        // all-different iff count(distinct-values) == count
        for (int i = 0; i < items.size(); i++) {
            for (int j = i + 1; j < items.size(); j++) {
                if (contextuallyEqual(items.get(i), items.get(j), collator)) {
                    return BooleanValue.FALSE;
                }
            }
        }
        return BooleanValue.TRUE;
    }

    /**
     * XQ4 contextual equality: two values are contextually equal if fn:compare returns 0.
     * NaN is treated as equal to NaN. Errors in comparison mean values are unequal.
     */
    static boolean contextuallyEqual(final AtomicValue v1, final AtomicValue v2, final Collator collator) {
        try {
            // NaN handling: NaN equals NaN
            if (v1 instanceof NumericValue && v2 instanceof NumericValue) {
                final boolean nan1 = ((NumericValue) v1).isNaN();
                final boolean nan2 = ((NumericValue) v2).isNaN();
                if (nan1 && nan2) {
                    return true;
                }
                if (nan1 || nan2) {
                    return false;
                }
            }
            return FunCompare.compare(v1, v2, collator) == 0;
        } catch (final Exception e) {
            // Errors in comparison mean values are unequal
            return false;
        }
    }

    private Collator getCollator(final Sequence[] args) throws XPathException {
        if (args.length > 1 && !args[1].isEmpty()) {
            final String collationURI = args[1].getStringValue();
            return context.getCollator(collationURI, ErrorCodes.FOCH0002);
        }
        return context.getDefaultCollator();
    }

}
