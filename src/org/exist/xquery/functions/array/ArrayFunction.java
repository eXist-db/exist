package org.exist.xquery.functions.array;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapModule;
import org.exist.xquery.value.*;

/**
 * Created by wolf on 05/09/14.
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
                    "Adds one member at the end of an array, creating a new array.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("appendage", Type.ITEM, Cardinality.ZERO_OR_MORE, "The items to append")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.ZERO_OR_MORE, "A copy of $array with the new member attached")
            ),
            new FunctionSignature(
                    new QName("seq", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Returns a sequence containing all the members of a supplied array, concatenated into a single sequence. This is " +
                    "equivalent to calling (1 to ay:size($array)) ! $array(.)",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array")
                    },
                    new FunctionReturnSequenceType(Type.ARRAY, Cardinality.ZERO_OR_MORE, "A sequence containing all members of the array")
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
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "A new array containing all members except the first")
            ),
            new FunctionSignature(
                    new QName("subarray", ArrayModule.NAMESPACE_URI, ArrayModule.PREFIX),
                    "Gets an array containing all members from a supplied array starting at a supplied position, up to a specified length.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("array", Type.ARRAY, Cardinality.EXACTLY_ONE, "The array"),
                            new FunctionParameterSequenceType("start", Type.INTEGER, Cardinality.EXACTLY_ONE, "The start index")
                    },
                    new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "A new array containing all members from $start")
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
        if (isCalledAs("size")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            return new IntegerValue(array.getSize());
        } else if (isCalledAs("get")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            final IntegerValue index = (IntegerValue) args[1].itemAt(0);
            return array.get(index.getInt() - 1);
        } else if (isCalledAs("append")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            final Sequence seq = args[1];
            return array.append(seq);
        } else if (isCalledAs("seq")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            return array.asSequence();
        } else if (isCalledAs("head")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            if (array.getSize() == 0) {
                throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
            }
            return array.get(0);
        } else if (isCalledAs("tail")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            if (array.getSize() == 0) {
                throw new XPathException(this, ErrorCodes.FOAY0001, "Array is empty");
            }
            return array.tail();
        } else if (isCalledAs("subarray")) {
            final ArrayType array = (ArrayType) args[0].itemAt(0);
            final int start = ((IntegerValue) args[1].itemAt(0)).getInt();
            final int length = array.getSize() - start + 1;
            if (start < 1) {
                throw new XPathException(this, ErrorCodes.FOAY0001, "Start index into array is < 1");
            }
            return array.subarray(start - 1, length);
        }
        throw new XPathException(this, "Unknown function: " + getName());
    }
}
