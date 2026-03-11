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
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunData;
import org.exist.xquery.value.*;

import java.util.*;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.array.ArrayModule.functionSignature;

/**
 * Implement functions operating on arrays as described in
 * <a href="http://www.w3.org/TR/xpath-functions-31/#array-functions">Xquery 3.1 §17.3</a>.
 */
public class ArrayFunction extends BasicFunction {

    private enum Fn {
        SIZE("size"),
        GET("get"),
        APPEND("append"),
        HEAD("head"),
        TAIL("tail"),
        SUBARRAY("subarray"),
        REMOVE("remove"),
        INSERT_BEFORE("insert-before"),
        REVERSE("reverse"),
        JOIN("join"),
        FOR_EACH("for-each"),
        FILTER("filter"),
        FOLD_LEFT("fold-left"),
        FOLD_RIGHT("fold-right"),
        FOR_EACH_PAIR("for-each-pair"),
        FLATTEN("flatten"),
        PUT("put"),
        SORT("sort");

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

    static final FunctionParameterSequenceType INPUT_ARRAY = param("array", Type.ARRAY_ITEM, "The input array");
    static final FunctionParameterSequenceType START_INDEX = param("start", Type.INTEGER, "The start index");
    static final FunctionParameterSequenceType[] INSERT_PUT_PARAMETERS = params(
            INPUT_ARRAY,
            param("position", Type.INTEGER, "Position at which the new member is inserted"),
            optManyParam("member", Type.ITEM, "The member to insert")
    );

    static final FunctionParameterSequenceType FOLDING_FUNCTION = funParam("function", Type.FUNCTION,
            params(
                optManyParam("acc", Type.ITEM, "the accumulated result"),
                optManyParam("next", Type.ITEM, "the next item")
            ),
            returnsMany(Type.ITEM),
            Cardinality.EXACTLY_ONE,
            "The folding function called on each member of the array"
    );

    static final FunctionReturnSequenceType RESULT_ARRAY = returns(Type.ARRAY_ITEM, "The resulting array");
    static final FunctionReturnSequenceType SORTED_ARRAY = returns(Type.ARRAY_ITEM, "The sorted array");

    static final FunctionParameterSequenceType ZERO_ITEM = optManyParam("zero", Type.ITEM, "The initial value");

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
            param("index", Type.INTEGER, "The index")
    );
    public static final FunctionSignature APPEND = functionSignature(
            Fn.APPEND.fname,
            "Returns an array containing all the members of the supplied array, plus one additional" +
                "member at the end.",
            returns(Type.ARRAY_ITEM, "A copy of $array with the new members attached"),
            INPUT_ARRAY,
            new FunctionParameterSequenceType("appendage", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to append")
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
            new FunctionParameterSequenceType("length", Type.INTEGER, Cardinality.EXACTLY_ONE, "Length of the subarray")
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
            INSERT_PUT_PARAMETERS
    );
    public static final FunctionSignature PUT = functionSignature(
            Fn.PUT.fname,
            "Returns an array containing all the members of the supplied array, with one additional member at the specified position.",
            RESULT_ARRAY,
            INSERT_PUT_PARAMETERS
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
            funParam("action", Type.FUNCTION,
                    params(
                            optManyParam("next", Type.ITEM, "the next member")
                    ),
                    returnsOptMany(Type.ITEM),
                    Cardinality.EXACTLY_ONE,
                    "The action called on each member of the array"
            )
    );
    public static final FunctionSignature FILTER = functionSignature(
            Fn.FILTER.fname,
            "Returns an array containing those members of the $array for which $function returns true.",
            RESULT_ARRAY,
            INPUT_ARRAY,
            funParam("action", Type.FUNCTION,
                    params(
                            optManyParam("next", Type.ITEM, "the next member")
                    ),
                    returns(Type.BOOLEAN),
                    Cardinality.EXACTLY_ONE,
                    "The filter function called on each member of the array"
            )
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
                "supplied arrays.",
            RESULT_ARRAY,
            INPUT_ARRAY,
            param("array2", Type.ARRAY_ITEM, "The second array to process"),
            funParam("action", Type.FUNCTION,
                    params(
                            optManyParam("a", Type.ITEM, "the current member of array 1"),
                            optManyParam("b", Type.ITEM, "the current member of array 2")
                    ),
                    returnsOptMany(Type.ITEM),
                    Cardinality.EXACTLY_ONE,
                    "The function to call for each pair"
            )
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
            funParam("key", Type.FUNCTION,
                    params(
                        optManyParam("next", Type.ITEM, "the next member")
                    ),
                    returns(Type.ANY_ATOMIC_TYPE),
                    Cardinality.EXACTLY_ONE,
                    "A function called for each array member which produces a sort key"
            )
    );

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
            case JOIN -> join(args);
            case FLATTEN -> flatten(args);
            case SIZE -> size(args);
            case GET -> get(args);
            case APPEND -> append(args);
            case HEAD -> head(args);
            case TAIL -> tail(args);
            case SUBARRAY -> subArray(args);
            case REMOVE -> remove(args);
            case INSERT_BEFORE -> insertBefore(args);
            case PUT -> put(args);
            case REVERSE -> reverse(args);
            case FOR_EACH -> forEach(args);
            case FILTER -> filter(args);
            case FOLD_LEFT -> foldLeft(args);
            case FOLD_RIGHT -> foldRight(args);
            case FOR_EACH_PAIR -> forEachPair(args);
            case SORT -> sort(args);
        };
    }

    private Sequence sort(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (args.length == 3) {
            final Collator collator;
            if (!args[1].isEmpty()) {
                final String collationURI = args[1].getStringValue();
                collator = context.getCollator(collationURI);
            } else {
                collator = context.getDefaultCollator();
            }

            //user specified key function
            return getFunction(args[2], ref -> array.sort(collator, ref));
        }
        final Collator collator;
        if (args.length == 2 && !args[1].isEmpty()) {
            final String collationURI = args[1].getStringValue();
            collator = context.getCollator(collationURI);
        } else {
            collator = context.getDefaultCollator();
        }

        //by default use fn:data#1 as the key function
        final FunctionReference keyFun = new FunctionReference(this, NamedFunctionReference.lookupFunction(this, context, FunData.qnData, 1));
        return array.sort(collator, keyFun);
    }

    private Sequence forEachPair(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[2], ref -> array.forEachPair((ArrayType) args[1].itemAt(0), ref));
    }

    private Sequence foldRight(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[2], ref -> array.foldRight(ref, args[1]));
    }

    private Sequence foldLeft(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[2], ref -> array.foldLeft(ref, args[1]));
    }

    private Sequence filter(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[1], array::filter);
    }

    private Sequence forEach(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return getFunction(args[1], array::forEach);
    }

    private ArrayType put(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int ppos = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (ppos  < 1 || ppos  > array.getSize() ) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to insert (" + ppos + ") is out of bounds");
        }
        return array.put(ppos - 1, args[2]);
    }

    private ArrayType insertBefore(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int ipos = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (ipos < 1 || ipos > array.getSize() + 1) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to insert (" + ipos + ") is out of bounds");
        }
        return array.insertBefore(ipos - 1, args[2]);
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

    private ArrayType subArray(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final int start = ((IntegerValue) args[1].itemAt(0)).getInt();
        int end = array.getSize();
        if (getArgumentCount() == 3) {
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

    private Sequence tail(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.tail();
    }

    private Sequence head(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        if (array.getSize() == 0) {
            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
        }
        return array.get(0);
    }

    private IntegerValue size(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return new IntegerValue(this, array.getSize());
    }

    private static ArrayType reverse(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return array.reverse();
    }

    private static ArrayType append(Sequence[] args) {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        return array.append(args[1]);
    }

    private static Sequence get(Sequence[] args) throws XPathException {
        final ArrayType array = (ArrayType) args[0].itemAt(0);
        final IntegerValue index = (IntegerValue) args[1].itemAt(0);
        return array.get(index);
    }

    private static ValueSequence flatten(Sequence[] args) throws XPathException {
        final ValueSequence result = new ValueSequence(args[0].getItemCount());
        ArrayType.flatten(args[0], result);
        return result;
    }

    private ArrayType join(Sequence[] args) throws XPathException {
        final List<ArrayType> arrays = new ArrayList<>(args[0].getItemCount());
        for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
            arrays.add((ArrayType) i.nextItem());
        }
        return ArrayType.join(context, arrays);
    }

    private Sequence getFunction(Sequence arg, FunctionE<FunctionReference, Sequence, XPathException> action) throws XPathException {
        try (final FunctionReference ref = (FunctionReference) arg.itemAt(0)) {
            ref.analyze(cachedContextInfo);
            return action.apply(ref);
        }
    }
}
