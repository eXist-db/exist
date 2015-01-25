package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Functions on arrays {@link http://www.w3.org/TR/xpath-functions-31/#array-functions}.
 *
 * @author Wolf
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
        FLATTEN("flatten");

        final static Map<String, Fn> fnMap = new HashMap<>();
        static {
            for (Fn fn: Fn.values()) {
                fnMap.put(fn.fname, fn);
            }
        }

        static Fn get(String name) {
            return fnMap.get(name);
        }

        private final String fname;

        Fn(String name) {
            this.fname = name;
        }
    }

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName(Fn.SIZE.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns the number of members in the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of members in the supplied array")
            ),
            new FunctionSignature(
                    new QName(Fn.GET.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets the value at the specified position in the supplied array (counting from 1). This is the same " +
                    "as calling $array($index).",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                        new FunctionParameterSequenceType("index", Type.INTEGER, Cardinality.EXACTLY_ONE, "The index")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "The value at $index")
            ),
            new FunctionSignature(
                    new QName(Fn.APPEND.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all the members of the supplied array, plus one additional" +
                    "member at the end.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("appendage", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to append")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.ZERO_OR_MORE, "A copy of $array with the new member attached")
            ),
            new FunctionSignature(
                    new QName(Fn.HEAD.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns the first member of an array, i.e. $array(1)",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The first member of the array")
            ),
            new FunctionSignature(
                    new QName(Fn.TAIL.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all members except the first from a supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members except the first")
            ),
            new FunctionSignature(
                    new QName(Fn.SUBARRAY.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets an array containing all members from a supplied array starting at a supplied position, up to the end of the array",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.EXACTLY_ONE, "The start index")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members from $start")
            ),
            new FunctionSignature(
                    new QName(Fn.SUBARRAY.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets an array containing all members from a supplied array starting at a supplied position, up to a specified length.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                        new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.EXACTLY_ONE, "The start index"),
                        new FunctionParameterSequenceType("length", Type.INTEGER, Cardinality.EXACTLY_ONE, "Length of the subarray")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members from $start up to the specified length")
            ),
            new FunctionSignature(
                    new QName(Fn.REMOVE.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all members from $array except the member whose position is $position.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("position", Type.INTEGER, Cardinality.EXACTLY_ONE, "Position of the member to remove")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members except the one at $position")
            ),
            new FunctionSignature(
                    new QName(Fn.INSERT_BEFORE.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all the members of the supplied array, with one additional member at a specified position.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("position", Type.INTEGER, Cardinality.EXACTLY_ONE, "Position at which the new member is inserted"),
                            new FunctionParameterSequenceType("member", Type.ITEM, Cardinality.ZERO_OR_MORE, "The member to insert")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members plus the new member")
            ),
            new FunctionSignature(
                    new QName(Fn.REVERSE.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all the members of the supplied array, but in reverse order.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The array in reverse order")
            ),
            new FunctionSignature(
                    new QName(Fn.JOIN.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Concatenates the contents of several arrays into a single array",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("arrays", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The arrays to join")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName(Fn.FOR_EACH.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array whose size is the same as array:size($array), in which each member is computed by applying " +
                    "$function to the corresponding member of $array.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array to process"),
                        new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function called on each member of the array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName(Fn.FILTER.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing those members of the $array for which $function returns true.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array to process"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function called on each member of the array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName(Fn.FOLD_LEFT.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Evaluates the supplied function cumulatively on successive values of the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array to process"),
                            new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "Start value"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to call")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The result of the cumulative function call")
            ),
            new FunctionSignature(
                    new QName(Fn.FOLD_RIGHT.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Evaluates the supplied function cumulatively on successive values of the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array to process"),
                            new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "Start value"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to call")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The result of the cumulative function call")
            ),
            new FunctionSignature(
                    new QName(Fn.FOR_EACH_PAIR.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array obtained by evaluating the supplied function once for each pair of members at the same position in the two " +
                    "supplied arrays.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array1", Type.ARRAY, Cardinality.EXACTLY_ONE, "The first array to process"),
                            new FunctionParameterSequenceType("array1", Type.ARRAY, Cardinality.EXACTLY_ONE, "The second array to process"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to call for each pair")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName(Fn.FLATTEN.fname, ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Replaces an array appearing in a supplied sequence with the members of the array, recursively.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("input", Type.ITEM, Cardinality.ZERO_OR_MORE, "The sequence to flatten")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The resulting sequence")
            )
    };

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
        switch (called) {
            case JOIN:
                final List<ArrayType> arrays = new ArrayList<ArrayType>(args[0].getItemCount());
                for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                    arrays.add((ArrayType) i.nextItem());
                }
                return ArrayType.join(context, arrays);
            case FLATTEN:
                final ValueSequence result = new ValueSequence(args[0].getItemCount());
                ArrayType.flatten(args[0], result);
                return result;
            default:
                final ArrayType array = (ArrayType) args[0].itemAt(0);
                switch (called) {
                    case SIZE:
                        return new IntegerValue(array.getSize());
                    case GET:
                        final IntegerValue index = (IntegerValue) args[1].itemAt(0);
                        return array.get(index.getInt() - 1);
                    case APPEND:
                        return array.append(args[1]);
                    case HEAD:
                        if (array.getSize() == 0) {
                            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
                        }
                        return array.get(0);
                    case TAIL:
                        if (array.getSize() == 0) {
                            throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
                        }
                        return array.tail();
                    case SUBARRAY:
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
                    case REMOVE:
                        final int rpos = ((IntegerValue) args[1].itemAt(0)).getInt();
                        if (rpos < 1 || rpos > array.getSize()) {
                            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to remove (" + rpos + ") is out of bounds");
                        }
                        return array.remove(rpos - 1);
                    case INSERT_BEFORE:
                        final int ipos = ((IntegerValue) args[1].itemAt(0)).getInt();
                        if (ipos < 1 || ipos > array.getSize() + 1) {
                            throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to insert (" + ipos + ") is out of bounds");
                        }
                        return array.insertBefore(ipos - 1, args[2]);
                    case REVERSE:
                        return array.reverse();
                    case FOR_EACH:
                        return array.forEach(getFunction(args[1]));
                    case FILTER:
                        return array.filter(getFunction(args[1]));
                    case FOLD_LEFT:
                        return array.foldLeft(getFunction(args[2]), args[1]);
                    case FOLD_RIGHT:
                        return array.foldRight(getFunction(args[2]), args[1]);
                    case FOR_EACH_PAIR:
                        return array.forEachPair((ArrayType) args[1].itemAt(0), getFunction(args[2]));
                }
        }
        throw new XPathException(this, "Unknown function: " + getName());
    }

    private FunctionReference getFunction(Sequence arg) throws XPathException {
        final FunctionReference ref = (FunctionReference) arg.itemAt(0);
        ref.analyze(cachedContextInfo);
        return ref;
    }
}