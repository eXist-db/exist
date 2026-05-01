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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements XQuery 4.0 fn:every and fn:some.
 */
public class FnEverySome extends BasicFunction {

    public static final FunctionSignature[] FN_EVERY = {
            new FunctionSignature(
                    new QName("every", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if every item in the input sequence matches the supplied predicate.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The predicate function (defaults to fn:boolean#1)")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all items match")),
            new FunctionSignature(
                    new QName("every", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if every item in the input sequence has an effective boolean value of true.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if all items are truthy"))
    };

    public static final FunctionSignature[] FN_SOME = {
            new FunctionSignature(
                    new QName("some", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if at least one item in the input sequence matches the supplied predicate.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence"),
                            new FunctionParameterSequenceType("predicate", Type.FUNCTION, Cardinality.ZERO_OR_ONE, "The predicate function (defaults to fn:boolean#1)")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if any item matches")),
            new FunctionSignature(
                    new QName("some", Function.BUILTIN_FUNCTION_NS),
                    "Returns true if at least one item in the input sequence has an effective boolean value of true.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The input sequence")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if any item is truthy"))
    };

    private AnalyzeContextInfo cachedContextInfo;

    public FnEverySome(final XQueryContext context, final FunctionSignature signature) {
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
        final boolean isEvery = isCalledAs("every");

        // 1-arg overload: use effective boolean value
        if (args.length == 1) {
            return evalWithEBV(input, isEvery);
        }

        // 2-arg overload: use predicate function (empty predicate = use EBV)
        if (args[1].isEmpty()) {
            return evalWithEBV(input, isEvery);
        }

        if (input.isEmpty()) {
            return BooleanValue.valueOf(isEvery);
        }

        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = ref.getSignature().getArgumentCount();

            // Validate arity: predicate must accept 0, 1, or 2 arguments
            if (arity > 2) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Predicate function must accept 0, 1, or 2 arguments, but has arity " + arity);
            }

            int pos = 1;
            for (final SequenceIterator i = input.iterate(); i.hasNext(); pos++) {
                final Item item = i.nextItem();
                final Sequence r = callPredicate(ref, item, pos, arity);
                // XQ4: predicate must return xs:boolean (xs:untypedAtomic is coercible)
                if (!r.isEmpty()) {
                    final int rType = r.itemAt(0).getType();
                    if (rType != Type.BOOLEAN && rType != Type.UNTYPED_ATOMIC) {
                        throw new XPathException(this, ErrorCodes.XPTY0004,
                                "Predicate function must return xs:boolean, but returned "
                                        + Type.getTypeName(rType));
                    }
                }
                final boolean matches = !r.isEmpty() && r.effectiveBooleanValue();
                if (isEvery && !matches) {
                    return BooleanValue.FALSE;
                }
                if (!isEvery && matches) {
                    return BooleanValue.TRUE;
                }
            }
            return BooleanValue.valueOf(isEvery);
        }
    }

    private Sequence evalWithEBV(final Sequence input, final boolean isEvery) throws XPathException {
        if (input.isEmpty()) {
            return BooleanValue.valueOf(isEvery);
        }
        for (final SequenceIterator i = input.iterate(); i.hasNext(); ) {
            final Item item = i.nextItem();
            final boolean ebv = item.toSequence().effectiveBooleanValue();
            if (isEvery && !ebv) {
                return BooleanValue.FALSE;
            }
            if (!isEvery && ebv) {
                return BooleanValue.TRUE;
            }
        }
        return BooleanValue.valueOf(isEvery);
    }

    private Sequence callPredicate(final FunctionReference ref, final Item item, final int pos, final int arity) throws XPathException {
        if (arity == 0) {
            return ref.evalFunction(null, null, new Sequence[0]);
        } else if (arity == 1) {
            return ref.evalFunction(null, null, new Sequence[]{item.toSequence()});
        } else {
            return ref.evalFunction(null, null, new Sequence[]{item.toSequence(), new IntegerValue(this, pos)});
        }
    }
}
