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

import com.evolvedbinary.j8fu.function.FunctionE;
import com.ibm.icu.text.Collator;
import org.exist.xquery.AnalyzeContextInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.NamedFunctionReference;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.FunData;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.FunctionReference;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.exist.xquery.FunctionDSL.funParam;
import static org.exist.xquery.FunctionDSL.optManyParam;
import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;
import static org.exist.xquery.FunctionDSL.params;
import static org.exist.xquery.FunctionDSL.returns;
import static org.exist.xquery.FunctionDSL.returnsMany;
import static org.exist.xquery.FunctionDSL.returnsOptMany;
import static org.exist.xquery.functions.array.ArrayModule.functionSignature;

/**
 * Implement functions operating on arrays as described in
 * <a href="http://www.w3.org/TR/xpath-functions-31/#array-functions">Xquery 3.1 §17.3</a>.
 * <p>
 * 17.3.1 array:size
 * 17.3.2 array:get
 * 17.3.3 array:put
 * 17.3.4 array:append
 * 17.3.5 array:subarray
 * 17.3.6 array:remove
 * 17.3.7 array:insert-before
 * 17.3.8 array:head
 * 17.3.9 array:tail
 * 17.3.10 array:reverse
 * 17.3.11 array:join
 * 17.3.12 array:for-each
 * 17.3.13 array:filter
 * 17.3.14 array:fold-left
 * 17.3.15 array:fold-right
 * 17.3.16 array:for-each-pair
 * 17.3.17 array:sort
 * 17.3.18 array:flatten
 */
public class ArrayFunction extends BasicFunction {
    private static final FunctionParameterSequenceType INPUT_ARRAY = param("array", Type.ARRAY_ITEM, "The input array");
    private static final FunctionParameterSequenceType START_INDEX = param("start", Type.INTEGER, "The start index");

    private static final FunctionParameterSequenceType FOLDING_FUNCTION = funParam("action",
            params(
                optManyParam("acc", Type.ITEM, "the accumulated result"),
                optManyParam("next", Type.ITEM, "the next item")
            ),
            returnsMany(Type.ITEM),
            "The folding function called on each member of the array"
    );

    private static final FunctionReturnSequenceType RESULT_ARRAY = returns(Type.ARRAY_ITEM, "The resulting array");
    private static final FunctionReturnSequenceType SORTED_ARRAY = returns(Type.ARRAY_ITEM, "The sorted array");

    private static final FunctionParameterSequenceType ZERO_ITEM = optManyParam("init", Type.ITEM, "The initial value");

    public static final FunctionSignature SIZE = functionSignature(
            Fn.SIZE.fname,
            "Returns the number of members in the supplied array.",
            returns(Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of members in the supplied array"),
            INPUT_ARRAY
    );
    public static final FunctionSignature GET = functionSignature(
            Fn.GET.fname,
            "Gets the value at the specified position in the supplied array (counting from 1). This is the same " +
                "as calling $array($index).",
            returnsOptMany(Type.ITEM, "The value at $index"),
            INPUT_ARRAY,
            param("position", Type.INTEGER, "The index")
    );
    public static final FunctionSignature APPEND = functionSignature(
            Fn.APPEND.fname,
            "Returns an array containing all the members of the supplied array, plus one additional" +
                "member at the end.",
            returns(Type.ARRAY_ITEM, "A copy of $array with the new members attached"),
            INPUT_ARRAY,
            new FunctionParameterSequenceType("member", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to append")
    );
    public static final FunctionSignature HEAD = functionSignature(
            Fn.HEAD.fname,
            "Returns the first member of an array, i.e. $array(1)",
            returnsOptMany(Type.ITEM, "The first member of the array"),
            INPUT_ARRAY
    );
    public static final FunctionSignature TAIL = functionSignature(
            Fn.TAIL.fname,
            "Returns an array containing all members except the first from a supplied array.",
            returns(Type.ARRAY_ITEM, "A new array containing all members except the first"),
            INPUT_ARRAY
    );
    public static final FunctionSignature SUBARRAY_1 = functionSignature(
            Fn.SUBARRAY.fname,
            "Gets an array containing all members from a supplied array starting at a supplied position, up to the end of the array",
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "A new array containing all members from $start"),
            INPUT_ARRAY,
            START_INDEX
    );
    public static final FunctionSignature SUBARRAY_2 = functionSignature(
            Fn.SUBARRAY.fname,
            "Gets an array containing all members from a supplied array starting at a supplied position, up to a specified length.",
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE, "A new array containing all members from $start up to the specified length"),
            INPUT_ARRAY,
            START_INDEX,
            new FunctionParameterSequenceType("length", Type.INTEGER, Cardinality.ZERO_OR_ONE, "Length of the subarray")
    );
    public static final FunctionSignature REMOVE = functionSignature(
            Fn.REMOVE.fname,
            "Returns an array containing all the members of the supplied array, except for the members at specified positions.",
            new FunctionReturnSequenceType(Type.ARRAY_ITEM, Cardinality.EXACTLY_ONE,
                    "A new array containing all members from $array except the members whose position (counting from 1) is present in the sequence $positions"),
            INPUT_ARRAY,
            new FunctionParameterSequenceType("positions", Type.INTEGER, Cardinality.ZERO_OR_MORE, "Positions of the members to remove")
    );
    public static final FunctionSignature INSERT_BEFORE = functionSignature(
            Fn.INSERT_BEFORE.fname,
            "Returns an array containing all the members of the supplied array, with one additional member at a specified position.",
            RESULT_ARRAY,
            INPUT_ARRAY,
            param("position", Type.INTEGER, "Position at which the new member is inserted"),
            optManyParam("member", Type.ITEM, "The new member to be insert before the member at the specified position")
    );
    public static final FunctionSignature PUT = functionSignature(
            Fn.PUT.fname,
            "Returns an array containing all the members of the supplied array, with the member at the specified position replaced.",
            RESULT_ARRAY,
            INPUT_ARRAY,
            param("position", Type.INTEGER, "Position at which the member is replaced"),
            optManyParam("member", Type.ITEM, "The new member to put at the specified position")
    );
    public static final FunctionSignature REVERSE = functionSignature(
            Fn.REVERSE.fname,
            "Returns an array containing all the members of the supplied array, but in reverse order.",
            RESULT_ARRAY,
            INPUT_ARRAY
    );
    public static final FunctionSignature JOIN = functionSignature(
            Fn.JOIN.fname,
            "Concatenates the contents of several arrays into a single array",
            RESULT_ARRAY,
            new FunctionParameterSequenceType("arrays", Type.ARRAY_ITEM, Cardinality.ZERO_OR_MORE, "The arrays to join")
    );
    public static final FunctionSignature FOR_EACH = functionSignature(
            Fn.FOR_EACH.fname,
            "Returns an array whose size is the same as array:size($array), in which each member is computed by applying " +
                "$function to the corresponding member of $array.",
            RESULT_ARRAY,
            INPUT_ARRAY,
            funParam("action",
                    params(
                            optManyParam("next", Type.ITEM, "the next member")
                    ),
                    returnsOptMany(Type.ITEM),
                    "The action called on each member of the array"
            )
    );
    public static final FunctionSignature FILTER = functionSignature(
            Fn.FILTER.fname,
            "Returns an array containing those members of the $array for which $function returns true. " +
                "In XQuery 4.0, the callback may accept 0 to 2 arguments: (member, position).",
            RESULT_ARRAY,
            INPUT_ARRAY,
            param("predicate", Type.FUNCTION, "The filter function called on each member of the array")
    );
    public static final FunctionSignature FOLD_LEFT = functionSignature(
            Fn.FOLD_LEFT.fname,
            "Evaluates the supplied function cumulatively on successive values of the supplied array.",
            returnsOptMany(Type.ITEM, "The result of the cumulative function call"),
            INPUT_ARRAY,
            ZERO_ITEM,
            FOLDING_FUNCTION
    );
    public static final FunctionSignature FOLD_RIGHT = functionSignature(
            Fn.FOLD_RIGHT.fname,
            "Evaluates the supplied function cumulatively on successive values of the supplied array.",
            returnsOptMany(Type.ITEM, "The result of the cumulative function call"),
            INPUT_ARRAY,
            ZERO_ITEM,
            FOLDING_FUNCTION
    );
    public static final FunctionSignature FOR_EACH_PAIR = functionSignature(
            Fn.FOR_EACH_PAIR.fname,
            "Returns an array obtained by evaluating the supplied function once for each pair of members at the same position in the two " +
                "supplied arrays. In XQuery 4.0, the callback may accept 0 to 3 arguments: (memberA, memberB, position).",
            RESULT_ARRAY,
            param("array1", Type.ARRAY_ITEM, "The first array to process"),
            param("array2", Type.ARRAY_ITEM, "The second array to process"),
            param("action", Type.FUNCTION, "The function to call for each pair")
    );
    public static final FunctionSignature FLATTEN = functionSignature(
            Fn.FLATTEN.fname,
            "Replaces an array appearing in a supplied sequence with the members of the array, recursively.",
            returnsOptMany(Type.ITEM, "The resulting sequence"),
            optManyParam("input", Type.ITEM, "The sequence to flatten")
    );
    public static final FunctionSignature SORT_1 = functionSignature(
            Fn.SORT.fname,
            "Returns an array containing all the members of the supplied array, sorted according to their typed value",
            SORTED_ARRAY,
            INPUT_ARRAY
    );
    public static final FunctionSignature SORT_2 = functionSignature(
            Fn.SORT.fname,
            "Returns an array containing all the members of the supplied array, sorted according to the value of a sort key supplied as a function.",
            SORTED_ARRAY,
            INPUT_ARRAY,
            optParam("collation", Type.STRING, "The collation to use for sorting")
    );
    public static final FunctionSignature SORT_3 = functionSignature(
            Fn.SORT.fname,
            "Returns an array containing all the members of the supplied array, sorted according to the value of a sort key supplied as a function.",
            SORTED_ARRAY,
            INPUT_ARRAY,
            optParam("collation", Type.STRING, "The collation to use for sorting"),
            funParam("key",
                    params(
                        optManyParam("next", Type.ITEM, "the next member")
                    ),
                    returns(Type.ANY_ATOMIC_TYPE),
                    "A function called for each array member which produces a sort key"
            )
    );

    // --- XQuery 4.0 array functions ---
    public static final FunctionSignature ARRAY_EMPTY = functionSignature(
            Fn.EMPTY.fname, "Returns true if the supplied array is empty.",
            returns(Type.BOOLEAN, "true if the array is empty"),
            INPUT_ARRAY
    );
    public static final FunctionSignature ARRAY_FOOT = functionSignature(
            Fn.FOOT.fname, "Returns the last member of an array.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE, "The last member"),
            INPUT_ARRAY
    );
    public static final FunctionSignature ARRAY_TRUNK = functionSignature(
            Fn.TRUNK.fname, "Returns all members except the last.",
            RESULT_ARRAY,
            INPUT_ARRAY
    );
    public static final FunctionSignature ARRAY_ITEMS = functionSignature(
            Fn.ITEMS.fname, "Returns the members of an array as a sequence.",
            returns(Type.ITEM, Cardinality.ZERO_OR_MORE, "The members as a sequence"),
            INPUT_ARRAY
    );
    public static final FunctionSignature ARRAY_MEMBERS = functionSignature(
            Fn.MEMBERS.fname, "Returns each member as a map with a 'value' key.",
            makeMembersReturnType(),
            INPUT_ARRAY
    );

    private static FunctionReturnSequenceType makeMembersReturnType() {
        // XQ4 spec: record(value as item()*)*
        // RecordType is not yet available on this branch, so fall back to the
        // function-shape of the record: map(xs:string, item()*).
        return new FunctionReturnSequenceType(Type.MAP_ITEM, Cardinality.ZERO_OR_MORE,
                "Sequence of member maps");
    }

    private AnalyzeContextInfo cachedContextInfo;

    public ArrayFunction(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public void analyze(AnalyzeContextInfo contextInfo) throws XPathException {
        cachedContextInfo = new AnalyzeContextInfo(contextInfo);
        super.analyze(contextInfo);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        if (context.getXQueryVersion() < 31) {
            throw new XPathException(this, ErrorCodes.EXXQDY0004, "arrays are only available in XQuery 3.1, but version declaration states " +
                    context.getXQueryVersion());
        }
        final Fn called = Fn.get(getSignature().getName().getLocalPart());
        return switch (called) {
            case SIZE -> size(args);
            case GET -> get(args);
            case PUT -> put(args);
            case APPEND -> append(args);
            case SUBARRAY -> subArray(args);
            case REMOVE -> remove(args);
            case INSERT_BEFORE -> insertBefore(args);
            case HEAD -> head(args);
            case TAIL -> tail(args);
            case REVERSE -> reverse(args);
            case JOIN -> join(args);
            case FOR_EACH -> forEach(args);
            case FILTER -> filter(args);
            case FOLD_LEFT -> foldLeft(args);
            case FOLD_RIGHT -> foldRight(args);
            case FOR_EACH_PAIR -> forEachPair(args);
            case SORT -> sort(args);
            case FLATTEN -> flatten(args);
            case EMPTY -> arrayEmpty(args);
            case FOOT -> foot(args);
            case TRUNK -> trunk(args);
            case ITEMS -> items(args);
            case MEMBERS -> members(args);
        };
    }

    private IntegerValue size(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return new IntegerValue(this, array.getSize());
    }

    private static Sequence get(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final IntegerValue index = (IntegerValue) args[1].itemAt(0);
        return array.get(index);
    }

    private ArrayType put(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int pos = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (pos  < 1 || pos  > array.getSize() ) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to insert (" + pos + ") is out of bounds");
        }
        return array.put(pos - 1, args[2]);
    }

    private static ArrayType append(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return array.append(args[1]);
    }

    private ArrayType subArray(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int start = ((IntegerValue) args[1].itemAt(0)).getInt();
        int end = array.getSize();
        if (getArgumentCount() == 3 && !args[2].isEmpty()) {
            final int length = ((IntegerValue) args[2].itemAt(0)).getInt();
            if (start + length > array.getSize() + 1) {
                throw new XPathException(this, ErrorCodes.FOAY0001, "Array index out of bounds: " + (start + length - 1));
            }
            if (length < 0) {
                throw new XPathException(this, ErrorCodes.FOAY0002, "Specified length < 0");
            }
            end = start + length - 1;
        }
        if (start < 1) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Start index into array is < 1");
        }
        return array.subarray(start - 1, end);
    }

    private ArrayType remove(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        // Handle empty sequence
        if (args[1].isEmpty()) {
            return array;
        }

        final int arraySize = args[1].getItemCount();

        // Fetch and reverse sort parameters
        int[] positions = new int[arraySize];
        for (int i = 0; i < arraySize; i++) {
            final int position = ((IntegerValue) args[1].itemAt(i)).getInt();
            if (position < 1 || position > array.getSize()) {
                throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to remove (" + position + ") is out of bounds");
            }
            positions[i] = position - 1;
        }
        Arrays.sort(positions);

        // Iterate reverse over array, delete items
        ArrayType resultArray = array;
        for (int pos = arraySize - 1; pos >= 0; pos--) {
            resultArray = resultArray.remove(positions[pos]);
        }

        return resultArray;
    }

    private ArrayType insertBefore(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int ipos = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (ipos < 1 || ipos > array.getSize() + 1) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to insert (" + ipos + ") is out of bounds");
        }
        return array.insertBefore(ipos - 1, args[2]);
    }

    private Sequence head(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.get(0);
    }

    private Sequence tail(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.tail();
    }

    private static ArrayType reverse(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return array.reverse();
    }

    private ArrayType join(Sequence[] args) throws XPathException {
        final List<ArrayType> arrays = new ArrayList<>(args[0].getItemCount());
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            arrays.add((ArrayType) i.nextItem());
        }
        return ArrayType.join(context, arrays);
    }

    private Sequence forEach(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[1], array::forEach);
    }

    private Sequence filter(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[1].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = callbackArity(ref);
            if (arity == 1) {
                // Standard XQ 3.1 behavior
                return array.filter(ref);
            }
            // XQ4 arity coercion: 0 or 2 args (member, position)
            final List<Sequence> ret = new ArrayList<>();
            for (int i = 0; i < array.getSize(); i++) {
                final Sequence member = array.get(i);
                final Sequence[] callArgs = buildArrayCallbackArgs(arity, member, i + 1, 2);
                final Sequence fret = ref.evalFunction(null, null, callArgs);
                if (fret.effectiveBooleanValue()) {
                    ret.add(member);
                }
            }
            return new ArrayType(this, context, ret);
        }
    }

    private Sequence foldLeft(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[2], ref -> array.foldLeft(ref, args[1]));
    }

    private Sequence foldRight(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[2], ref -> array.foldRight(ref, args[1]));
    }

    private Sequence forEachPair(Sequence[] args) throws XPathException {
        final ArrayType array1 = (ArrayType) args[0].itemAt(0);
        final ArrayType array2 = (ArrayType) args[1].itemAt(0);
        try (final FunctionReference ref = (FunctionReference) args[2].itemAt(0)) {
            ref.analyze(cachedContextInfo);
            final int arity = callbackArity(ref);
            if (arity == 2) {
                // Standard XQ 3.1 behavior
                return array1.forEachPair(array2, ref);
            }
            // XQ4 arity coercion: 0, 1, or 3 args (memberA, memberB, position)
            final int minSize = Math.min(array1.getSize(), array2.getSize());
            final List<Sequence> ret = new ArrayList<>(minSize);
            for (int i = 0; i < minSize; i++) {
                final Sequence[] callArgs = switch (arity) {
                    case 0 -> new Sequence[0];
                    case 1 -> new Sequence[]{array1.get(i)};
                    case 3 -> new Sequence[]{array1.get(i), array2.get(i),
                            new IntegerValue(this, i + 1, Type.INTEGER)};
                    default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                            "array:for-each-pair callback must accept 0 to 3 arguments, got " + arity);
                };
                ret.add(ref.evalFunction(null, null, callArgs));
            }
            return new ArrayType(this, context, ret);
        }
    }

    /**
     * Resolve the arity of a callback function reference. Prefers the signature
     * arity, falling back to the call's bound argument count when the signature
     * is variadic (so e.g. concat#2 reports 2 instead of -1).
     */
    private static int callbackArity(final FunctionReference ref) {
        final int sigArity = ref.getSignature().getArgumentCount();
        return sigArity >= 0 ? sigArity : ref.getCall().getArgumentCount();
    }

    /**
     * Build an argument array for an array callback function, supporting XQ4 arity coercion.
     */
    private Sequence[] buildArrayCallbackArgs(final int arity, final Sequence member,
            final int position, final int maxArity) throws XPathException {
        return switch (arity) {
            case 0 -> new Sequence[0];
            case 1 -> new Sequence[]{member};
            case 2 -> new Sequence[]{member, new IntegerValue(this, position, Type.INTEGER)};
            default -> throw new XPathException(this, ErrorCodes.XPTY0004,
                    "Array callback function must accept 0 to " + maxArity + " arguments, got " + arity);
        };
    }

    private Sequence sort(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final Collator collator;
        if (args.length == 3) {
            if (!args[1].isEmpty()) {
                final String collationURI = args[1].getStringValue();
                collator = context.getCollator(collationURI);
            } else {
                collator = context.getDefaultCollator();
            }

            // user specified key function
            return getFunction(args[2], ref -> array.sort(collator, ref));
        }
        if (args.length == 2 && !args[1].isEmpty()) {
            final String collationURI = args[1].getStringValue();
            collator = context.getCollator(collationURI);
        } else {
            collator = context.getDefaultCollator();
        }

        // by default use fn:data#1 as the key function
        final FunctionReference keyFun = new FunctionReference(this, NamedFunctionReference.lookupFunction(this, context, FunData.qnData, 1));
        return array.sort(collator, keyFun);
    }

    private static ValueSequence flatten(Sequence[] args) throws XPathException {
        final ValueSequence result = new ValueSequence(args[0].getItemCount());
        ArrayType.flatten(args[0], result);
        return result;
    }

    private Sequence getFunction(Sequence arg, FunctionE<FunctionReference, Sequence, XPathException> action) throws XPathException {
        try (final FunctionReference ref = (FunctionReference) arg.itemAt(0)) {
            ref.analyze(cachedContextInfo);
            return action.apply(ref);
        }
    }

    // --- XQuery 4.0 implementations ---

    private BooleanValue arrayEmpty(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return BooleanValue.valueOf(array.getSize() == 0);
    }

    private Sequence foot(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.get(array.getSize() - 1);
    }

    private ArrayType trunk(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.subarray(0, array.getSize() - 1);
    }

    private Sequence items(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence();
        for (int i = 0; i < array.getSize(); i++) {
            final Sequence member = array.get(i);
            for (SequenceIterator si = member.iterate(); si.hasNext(); ) {
                result.add(si.nextItem());
            }
        }
        return result;
    }

    private Sequence members(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final ValueSequence result = new ValueSequence();
        for (int i = 0; i < array.getSize(); i++) {
            final org.exist.xquery.functions.map.MapType memberMap =
                    new org.exist.xquery.functions.map.MapType(this, context, null);
            memberMap.add(new StringValue("value"), array.get(i));
            result.add(memberMap);
        }
        return result;
    }

    private enum Fn {
        SIZE("size"),
        GET("get"),
        PUT("put"),
        APPEND("append"),
        SUBARRAY("subarray"),
        REMOVE("remove"),
        INSERT_BEFORE("insert-before"),
        HEAD("head"),
        TAIL("tail"),
        REVERSE("reverse"),
        JOIN("join"),
        FOR_EACH("for-each"),
        FILTER("filter"),
        FOLD_LEFT("fold-left"),
        FOLD_RIGHT("fold-right"),
        FOR_EACH_PAIR("for-each-pair"),
        SORT("sort"),
        FLATTEN("flatten"),
        // --- XQuery 4.0 ---
        EMPTY("empty"),
        FOOT("foot"),
        TRUNK("trunk"),
        ITEMS("items"),
        MEMBERS("members");

        final static Map<String, Fn> fnMap = new HashMap<>();
        private final String fname;

        static {
            for (Fn fn: Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        static Fn get(String name) {
            return fnMap.get(name);
        }

        Fn(String name) {
            this.fname = name;
        }
    }
}
