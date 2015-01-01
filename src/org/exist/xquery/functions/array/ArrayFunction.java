package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapModule;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Functions on arrays {@link http://www.w3.org/TR/xpath-functions-31/#array-functions}.
 *
 * @author Wolf
 */
public class ArrayFunction extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            new FunctionSignature(
                    new QName("size", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns the number of members in the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "The number of members in the supplied array")
            ),
            new FunctionSignature(
                    new QName("get", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets the value at the specified position in the supplied array (counting from 1). This is the same " +
                    "as calling $array($index).",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                        new FunctionParameterSequenceType("index", Type.INTEGER, Cardinality.EXACTLY_ONE, "The index")
                    },
                    new FunctionReturnSequenceType(Type.INTEGER, Cardinality.ZERO_OR_MORE, "The value at $index")
            ),
            new FunctionSignature(
                    new QName("append", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all the members of the supplied array, plus one additional" +
                    "member at the end.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("appendage", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to append")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.ZERO_OR_MORE, "A copy of $array with the new member attached")
            ),
            new FunctionSignature(
                    new QName("head", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns the first member of an array, i.e. $array(1)",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The first member of the array")
            ),
            new FunctionSignature(
                    new QName("tail", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all members except the first from a supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members except the first")
            ),
            new FunctionSignature(
                    new QName("subarray", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets an array containing all members from a supplied array starting at a supplied position, up to the end of the array",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.EXACTLY_ONE, "The start index")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members from $start")
            ),
            new FunctionSignature(
                    new QName("subarray", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets an array containing all members from a supplied array starting at a supplied position, up to a specified length.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                        new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.EXACTLY_ONE, "The start index"),
                        new FunctionParameterSequenceType("length", Type.INTEGER, Cardinality.EXACTLY_ONE, "Length of the subarray")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members from $start up to the specified length")
            ),
            new FunctionSignature(
                    new QName("remove", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all members from $array except the member whose position is $position.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("position", Type.INTEGER, Cardinality.EXACTLY_ONE, "Position of the member to remove")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "A new array containing all members except the one at $position")
            ),
            new FunctionSignature(
                    new QName("reverse", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing all the members of the supplied array, but in reverse order.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The array in reverse order")
            ),
            new FunctionSignature(
                    new QName("join", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Concatenates the contents of several arrays into a single array",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("arrays", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The arrays to join")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName("for-each", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array whose size is the same as array:size($array), in which each member is computed by applying " +
                    "$function to the corresponding member of $array.",
                    new SequenceType[] {
                        new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The array to process"),
                        new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function called on each member of the array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName("filter", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns an array containing those members of the $array for which $function returns true.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The array to process"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function called on each member of the array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.EXACTLY_ONE, "The resulting array")
            ),
            new FunctionSignature(
                    new QName("fold-left", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Evaluates the supplied function cumulatively on successive values of the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The array to process"),
                            new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "Start value"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to call")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The result of the cumulative function call")
            ),
            new FunctionSignature(
                    new QName("fold-right", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Evaluates the supplied function cumulatively on successive values of the supplied array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.ZERO_OR_MORE, "The array to process"),
                            new FunctionParameterSequenceType("zero", Type.ITEM, Cardinality.ZERO_OR_MORE, "Start value"),
                            new FunctionParameterSequenceType("function", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "The function to call")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "The result of the cumulative function call")
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
        if (isCalledAs("join")) {
            final List<ArrayType> arrays = new ArrayList<ArrayType>(args[0].getItemCount());
            for (SequenceIterator i = args[0].iterate(); i.hasNext(); ) {
                arrays.add((ArrayType) i.nextItem());
            }
            return ArrayType.join(context, arrays);
        } else {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            if (isCalledAs("size")) {
                return new IntegerValue(array.getSize());
            } else if (isCalledAs("get")) {
                final IntegerValue index = (IntegerValue) args[1].itemAt(0);
                return array.get(index.getInt() - 1);
            } else if (isCalledAs("append")) {
                return array.append(args[1]);
            } else if (isCalledAs("head")) {
                if (array.getSize() == 0) {
                    throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
                }
                return array.get(0);
            } else if (isCalledAs("tail")) {
                if (array.getSize() == 0) {
                    throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
                }
                return array.tail();
            } else if (isCalledAs("subarray")) {
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
            } else if (isCalledAs("remove")) {
                final int position = ((IntegerValue) args[1].itemAt(0)).getInt();
                if (position < 1 || position > array.getSize()) {
                    throw new XPathException(this, ErrorCodes.FOAY0001, "Index of item to remove (" + position + ") is out of bounds");
                }
                return array.remove(position - 1);
            } else if (isCalledAs("reverse")) {
                return array.reverse();
            } else if (isCalledAs("for-each")) {
                final FunctionReference ref = (FunctionReference) args[1].itemAt(0);
                ref.analyze(cachedContextInfo);
                return array.forEach(ref);
            } else if (isCalledAs("filter")) {
                final FunctionReference ref = (FunctionReference) args[1].itemAt(0);
                ref.analyze(cachedContextInfo);
                return array.filter(ref);
            } else if (isCalledAs("fold-left")) {
                final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
                ref.analyze(cachedContextInfo);
                return array.foldLeft(ref, args[1]);
            } else if (isCalledAs("fold-right")) {
                final FunctionReference ref = (FunctionReference) args[2].itemAt(0);
                ref.analyze(cachedContextInfo);
                return array.foldRight(ref, args[1]);
            }
        }
        throw new XPathException(this, "Unknown function: " + getName());
    }
}