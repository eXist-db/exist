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
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.RangeSequence;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.array.ArrayType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.HashMap;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.funParam;
import static org.exist.xquery.FunctionDSL.optManyParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;
import static org.exist.xquery.FunctionDSL.returns;
import static org.exist.xquery.FunctionDSL.returnsOptMany;

/**
 * Implement the higher order functions as described in
 * <a href="https://www.w3.org/TR/xpath-functions-31/#basic-hofs">XQuery Functions and Operators 3.1 §16.2</a>
 * <p>
 * 16.2.1 fn:for-each
 * 16.2.2 fn:filter
 * 16.2.3 fn:fold-left
 * 16.2.4 fn:fold-right
 * 16.2.5 fn:for-each-pair
 * 16.2.7 fn:apply
 * <p>
 * Note: fn:sort is implemented in org.exist.xquery.functions.fn.FunSort
 */
public class FunHigherOrderFun extends BasicFunction {
    public static final FunctionSignature FN_FOR_EACH = functionSignature(
            Fn.FOR_EACH.fname,
            "Applies the function item $function to every item from the sequence " +
                    "$sequence in turn, returning the concatenation of the resulting sequences in order.",
            returnsOptMany(Type.ITEM, "result of applying the function to each item of the sequence"),
            optManyParam("sequence", Type.ITEM, "the sequence on which to apply the function"),
            funParam("function",
                    params(
                            param("item", Type.ITEM, "the next item in the sequence")
                    ),
                    returnsOptMany(Type.ITEM),
                    "The function called on each item in the sequence"
            )
    );
    public static final FunctionSignature FN_FILTER = functionSignature(
            Fn.FILTER.fname,
            "Returns those items from the sequence $sequence for which the supplied function $function returns true.",
            returnsOptMany(Type.ITEM, "result of filtering the sequence"),

            optManyParam("sequence", Type.ITEM, "the sequence to filter"),
            funParam("function",
                    params(
                            param("next", Type.ITEM, "the next item to filter")
                    ),
                    returns(Type.BOOLEAN, "include items for which the filter function evaluates to true()"),
                    "The function called on each item, only items that yield true() will be returned"
            )
    );
    private static final FunctionParameterSequenceType[] FOLDING_PARAMS = params(
            optManyParam("sequence", Type.ITEM, "the sequence to iterate over"),
            optManyParam("zero", Type.ITEM, "initial value to start with"),
            funParam("function",
                    params(
                            optManyParam("accumulator", Type.ITEM, "the current accumulated result"),
                            param("next", Type.ITEM, "the next item in the sequence")
                    ),
                    returnsOptMany(Type.ITEM),
                    "The folding function"
            )
    );
    public static final FunctionSignature FN_FOLD_LEFT = functionSignature(
            Fn.FOLD_LEFT.fname,
            "Processes the supplied sequence from left to right, applying the supplied function repeatedly to each " +
                    "item in turn, together with an accumulated result value.",
            returnsOptMany(Type.ITEM, "result of the fold-left operation"),
            FOLDING_PARAMS
    );
    public static final FunctionSignature FN_FOLD_RIGHT = functionSignature(
            Fn.FOLD_RIGHT.fname,
            "Processes the supplied sequence from right to left, applying the supplied function repeatedly to each " +
                    "item in turn, together with an accumulated result value.",
            returnsOptMany(Type.ITEM, "result of the fold-right operation"),
            FOLDING_PARAMS
    );
    public static final FunctionSignature FN_FOR_EACH_PAIR = functionSignature(
            Fn.FOR_EACH_PAIR.fname,
            "Applies the function item $f to successive pairs of items taken one from $seq1 and one from $seq2, " +
                    "returning the concatenation of the resulting sequences in order.",
            returnsOptMany(Type.ITEM, "concatenation of resulting sequences"),
            optManyParam("seq1", Type.ITEM, "first sequence to take items from"),
            optManyParam("seq2", Type.ITEM, "second sequence to take items from"),
            funParam("function",
                    params(
                            param("a", Type.ITEM, "the next item from the first sequence"),
                            param("b", Type.ITEM, "the next item from the first sequence")
                    ),
                    returnsOptMany(Type.ITEM),
                    "The function called on each pair of items"
            )
    );
    public static final FunctionSignature FN_APPLY = functionSignature(
            Fn.APPLY.fname,
            "Processes the supplied sequence from right to left, applying the supplied function repeatedly to each " +
                    "item in turn, together with an accumulated result value.",
            returnsOptMany(Type.ITEM, "return value of the function call"),

            param("function", Type.FUNCTION, "the function to call"),
            param("array", Type.ARRAY_ITEM, "an array containing the arguments to pass to the function")
    );

    private AnalyzeContextInfo cachedContextInfo;

    public FunHigherOrderFun(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    static FunctionSignature functionSignature(final String name, final String description, final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
        return FunctionDSL.functionSignature(new QName(name, Function.BUILTIN_FUNCTION_NS), description, returnType, paramTypes);
    }

    @Override
    public void analyze(final AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(cachedContextInfo);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final FunHigherOrderFun.Fn called = FunHigherOrderFun.Fn.get(getSignature().getName().getLocalPart());
        return switch (called) {
            case FOR_EACH -> forEach(args);
            case FILTER -> filter(args);
            case FOLD_LEFT -> foldLeft(args);
            case FOLD_RIGHT -> foldRight(args);
            case FOR_EACH_PAIR -> forEachPair(args);
            case APPLY -> apply(args);
        };
    }

    private Sequence forEach(final Sequence[] args) throws XPathException {
        final Sequence result = new ValueSequence();
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            for (final SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                final Sequence[] fargs = new Sequence[]{item.toSequence()};
                checkFunctionParameterTypes(ref, fargs);
                final Sequence r = ref.evalFunction(null, null, fargs);
                result.addAll(r);
            }
        }
        return result;
    }

    private Sequence filter(final Sequence[] args) throws XPathException {
        final Sequence result = new ValueSequence();
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);

            final Sequence seq = args[0];
            for (final SequenceIterator i = seq.iterate(); i.hasNext(); ) {
                final Item item = i.nextItem();
                final Sequence[] fargs = new Sequence[]{item.toSequence()};
                checkFunctionParameterTypes(ref, fargs);
                final Sequence r = ref.evalFunction(null, null, fargs);

                if (r.getItemType() != Type.BOOLEAN) {
                    throw new XPathException(this, ErrorCodes.XPTY0004,
                            "The function returned " + r + "of type" + Type.getTypeName(r.getItemType()) + ", only xs:boolean is allowed");
                }
                if (r.effectiveBooleanValue()) {
                    result.add(item);
                }
            }
        }
        return result;
    }

    private Sequence foldLeft(final Sequence[] args) throws XPathException {
        final Sequence result;
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final Sequence seq = args[0];
            final Sequence zero = args[1];
            result = foldLeft(ref, zero, seq.iterate());
        }
        return result;
    }

    private Sequence foldLeft(final FunctionReference ref, Sequence accum, final SequenceIterator seq) throws XPathException {
        while (seq.hasNext()) {
            final Sequence[] refArgs = new Sequence[2];
            refArgs[0] = accum;
            refArgs[1] = seq.nextItem().toSequence();
            checkFunctionParameterTypes(ref, refArgs);
            accum = ref.evalFunction(null, null, refArgs);
        }
        return accum;
    }

    private Sequence foldRight(final Sequence[] args) throws XPathException {
        final Sequence result;
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final Sequence zero = args[1];
            final Sequence seq = args[0];
            if (seq instanceof ValueSequence) {
                result = foldRightNonRecursive(ref, zero, ((ValueSequence) seq).iterateInReverse());
            } else if (seq instanceof RangeSequence) {
                result = foldRightNonRecursive(ref, zero, ((RangeSequence) seq).iterateInReverse());
            } else {
                result = foldRight(ref, zero, seq);
            }
        }
        return result;
    }

    private Sequence foldRight(final FunctionReference ref, final Sequence zero, final Sequence seq) throws XPathException {
        if (seq.isEmpty()) {
            return zero;
        }
        final Sequence head = seq.itemAt(0).toSequence();
        final Sequence tailResult = foldRight(ref, zero, seq.tail());
        final Sequence[] refArgs = new Sequence[]{head, tailResult};
        checkFunctionParameterTypes(ref, refArgs);
        return ref.evalFunction(null, null, refArgs);
    }

    /**
     * High performance non-recursive implementation of fold-right
     * relies on the provided iterator moving in reverse
     *
     * @param seq An iterator which moves from right to left
     */
    private Sequence foldRightNonRecursive(final FunctionReference ref, Sequence accum, final SequenceIterator seq) throws XPathException {
        final Sequence[] refArgs = new Sequence[2];
        while (seq.hasNext()) {
            refArgs[0] = seq.nextItem().toSequence();
            refArgs[1] = accum;
            checkFunctionParameterTypes(ref, refArgs);
            accum = ref.evalFunction(null, null, refArgs);
        }
        return accum;
    }

    private Sequence forEachPair(final Sequence[] args) throws XPathException {
        final Sequence result = new ValueSequence();
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final SequenceIterator i1 = args[0].iterate();
            final SequenceIterator i2 = args[1].iterate();
            while (i1.hasNext() && i2.hasNext()) {
                final Sequence[] fargs = new Sequence[]{i1.nextItem().toSequence(), i2.nextItem().toSequence()};
                checkFunctionParameterTypes(ref, fargs);
                final Sequence r = ref.evalFunction(null, null, fargs);
                result.addAll(r);
            }
        }
        return result;
    }

    private Sequence apply(final Sequence[] args) throws XPathException {
        try (final FunctionReference ref = (FunctionReference) args[0].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final ArrayType array = (ArrayType) args[1].itemAt(0);
            if (!arityMatches(ref, array.getSize())) {
                throw new XPathException(this, ErrorCodes.FOAP0001,
                        "Number of arguments supplied to fn:apply does not match function signature. Expected: " +
                                ref.getSignature().getArgumentCount() + ", got: " + array.getSize());
            }
            final Sequence[] fargs = array.toArray();
            checkFunctionParameterTypes(ref, fargs);
            return ref.evalFunction(null, null, fargs);
        }
    }

    private boolean arityMatches(final FunctionReference ref, final int n) {
        return (ref.getSignature().isVariadic() ||
                ref.getSignature().getArgumentCount() == n);
    }

    /**
     * Validates that each argument is compatible with the declared parameter type
     * of the function reference. This enforces type checking for higher-order
     * function callbacks, which was previously missing (issue #3754).
     *
     * @param ref  the function reference whose parameter types to check against
     * @param args the actual arguments to validate
     * @throws XPathException with XPTY0004 if a type mismatch is detected
     */
    private void checkFunctionParameterTypes(final FunctionReference ref, final Sequence[] args) throws XPathException {
        final SequenceType[] paramTypes = ref.getSignature().getArgumentTypes();
        if (paramTypes == null) {
            return;
        }
        for (int i = 0; i < args.length && i < paramTypes.length; i++) {
            final SequenceType expected = paramTypes[i];
            final int expectedType = expected.getPrimaryType();
            // Skip check if the declared type is item() — accepts anything
            if (expectedType == Type.ITEM) {
                continue;
            }
            final Sequence arg = args[i];
            if (arg.isEmpty()) {
                continue;
            }
            if (!expected.checkType(arg)) {
                throw new XPathException(this, ErrorCodes.XPTY0004,
                        "Invalid type for parameter " + (i + 1) + " of higher-order function call. " +
                                "Expected " + Type.getTypeName(expectedType) +
                                ", got " + Type.getTypeName(arg.getItemType()));
            }
        }
    }

    private enum Fn {
        FOR_EACH("for-each"),
        FILTER("filter"),
        FOLD_LEFT("fold-left"),
        FOLD_RIGHT("fold-right"),
        FOR_EACH_PAIR("for-each-pair"),
        APPLY("apply");

        final static Map<String, FunHigherOrderFun.Fn> fnMap = new HashMap<>();

        static {
            for (final FunHigherOrderFun.Fn fn : FunHigherOrderFun.Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        private final String fname;

        Fn(final String name) {
            this.fname = name;
        }

        static FunHigherOrderFun.Fn get(final String name) {
            return fnMap.get(name);
        }
    }
}
