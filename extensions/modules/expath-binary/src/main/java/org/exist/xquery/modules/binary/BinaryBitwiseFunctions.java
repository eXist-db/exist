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
package org.exist.xquery.modules.binary;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.math.BigInteger;
import java.util.Arrays;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — Bitwise Operations (Section 8).
 *
 * <ul>
 *   <li>bin:or</li>
 *   <li>bin:xor</li>
 *   <li>bin:and</li>
 *   <li>bin:not</li>
 *   <li>bin:shift</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#bitwise-operations">EXPath Binary Module 4.0 §8</a>
 */
public class BinaryBitwiseFunctions extends BasicFunction {

    private static final QName QN_OR = new QName("or", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_XOR = new QName("xor", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_AND = new QName("and", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_NOT = new QName("not", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_SHIFT = new QName("shift", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    static final FunctionSignature FS_OR = functionSignature(
            QN_OR,
            "Returns the bitwise OR of two binary values.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value1", Type.BASE64_BINARY, "The first binary value"),
            optParam("value2", Type.BASE64_BINARY, "The second binary value")
    );

    static final FunctionSignature FS_XOR = functionSignature(
            QN_XOR,
            "Returns the bitwise XOR of two binary values.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value1", Type.BASE64_BINARY, "The first binary value"),
            optParam("value2", Type.BASE64_BINARY, "The second binary value")
    );

    static final FunctionSignature FS_AND = functionSignature(
            QN_AND,
            "Returns the bitwise AND of two binary values.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value1", Type.BASE64_BINARY, "The first binary value"),
            optParam("value2", Type.BASE64_BINARY, "The second binary value")
    );

    static final FunctionSignature FS_NOT = functionSignature(
            QN_NOT,
            "Returns the bitwise NOT of a binary value.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.BASE64_BINARY, "The binary value")
    );

    static final FunctionSignature FS_SHIFT = functionSignature(
            QN_SHIFT,
            "Shifts bits in binary data. Positive shifts left, negative shifts right.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.BASE64_BINARY, "The binary value"),
            param("by", Type.INTEGER, "The number of bits to shift (positive = left, negative = right)")
    );

    public BinaryBitwiseFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("or")) {
            return bitwiseOp(args, BitwiseOp.OR);
        } else if (isCalledAs("xor")) {
            return bitwiseOp(args, BitwiseOp.XOR);
        } else if (isCalledAs("and")) {
            return bitwiseOp(args, BitwiseOp.AND);
        } else if (isCalledAs("not")) {
            return bitwiseNot(args);
        } else {
            return bitwiseShift(args);
        }
    }

    private enum BitwiseOp { OR, XOR, AND }

    private Sequence bitwiseOp(final Sequence[] args, final BitwiseOp op) throws XPathException {
        final byte[] data1 = BinaryModuleHelper.getBinaryData(args[0]);
        final byte[] data2 = BinaryModuleHelper.getBinaryData(args[1]);

        if (data1 == null || data2 == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        if (data1.length != data2.length) {
            throw new XPathException(this, BinaryModuleErrorCode.DIFFERING_LENGTH_ARGUMENTS,
                    "Arguments to bin:" + op.name().toLowerCase() + "() must have equal length, but got "
                            + data1.length + " and " + data2.length);
        }

        final byte[] result = new byte[data1.length];
        for (int i = 0; i < data1.length; i++) {
            switch (op) {
                case OR:
                    result[i] = (byte) (data1[i] | data2[i]);
                    break;
                case XOR:
                    result[i] = (byte) (data1[i] ^ data2[i]);
                    break;
                case AND:
                    result[i] = (byte) (data1[i] & data2[i]);
                    break;
            }
        }
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence bitwiseNot(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) ~data[i];
        }
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence bitwiseShift(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int by = ((IntegerValue) args[1].itemAt(0)).getInt();
        final int originalLength = data.length;

        if (originalLength == 0) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        // Use BigInteger for bit shifting, then maintain original length
        BigInteger bigInt = new BigInteger(1, data);

        if (by > 0) {
            bigInt = bigInt.shiftLeft(by);
        } else if (by < 0) {
            bigInt = bigInt.shiftRight(-by);
        }

        // Convert back to byte array of original length
        final byte[] shifted = bigInt.toByteArray();
        final byte[] result = new byte[originalLength];

        if (shifted.length <= originalLength) {
            // Right-align in result
            System.arraycopy(shifted, 0, result, originalLength - shifted.length, shifted.length);
        } else {
            // Truncate from the left to maintain original length
            System.arraycopy(shifted, shifted.length - originalLength, result, 0, originalLength);
        }

        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }
}
