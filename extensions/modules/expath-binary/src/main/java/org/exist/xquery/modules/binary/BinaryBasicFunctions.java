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

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.util.Arrays;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — Basic Operations (Section 5).
 *
 * <ul>
 *   <li>bin:length</li>
 *   <li>bin:part</li>
 *   <li>bin:join</li>
 *   <li>bin:insert-before</li>
 *   <li>bin:pad-left</li>
 *   <li>bin:pad-right</li>
 *   <li>bin:find</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#basic-operations">EXPath Binary Module 4.0 §5</a>
 */
public class BinaryBasicFunctions extends BasicFunction {

    private static final QName QN_LENGTH = new QName("length", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_PART = new QName("part", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_JOIN = new QName("join", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_INSERT_BEFORE = new QName("insert-before", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_PAD_LEFT = new QName("pad-left", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_PAD_RIGHT = new QName("pad-right", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_FIND = new QName("find", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    static final FunctionSignature FS_LENGTH = functionSignature(
            QN_LENGTH,
            "Returns the size of binary data in octets.",
            returns(Type.INTEGER),
            param("value", Type.BASE64_BINARY, "The binary data")
    );

    static final FunctionSignature[] FS_PART = functionSignatures(
            QN_PART,
            "Returns a specified part of binary data.",
            returnsOpt(Type.BASE64_BINARY),
            arities(
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("offset", Type.INTEGER, "The zero-based offset")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("offset", Type.INTEGER, "The zero-based offset"),
                            param("size", Type.INTEGER, "The number of octets to return")
                    )
            )
    );

    static final FunctionSignature FS_JOIN = functionSignature(
            QN_JOIN,
            "Returns the concatenation of binary data.",
            returns(Type.BASE64_BINARY),
            optManyParam("values", Type.BASE64_BINARY, "The binary data items to join")
    );

    static final FunctionSignature FS_INSERT_BEFORE = functionSignature(
            QN_INSERT_BEFORE,
            "Inserts additional binary data at a given point in other binary data.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.BASE64_BINARY, "The binary data"),
            param("offset", Type.INTEGER, "The zero-based offset for insertion"),
            optParam("extra", Type.BASE64_BINARY, "The binary data to insert")
    );

    static final FunctionSignature[] FS_PAD_LEFT = functionSignatures(
            QN_PAD_LEFT,
            "Pads binary data on the left to a specified size.",
            returnsOpt(Type.BASE64_BINARY),
            arities(
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("count", Type.INTEGER, "The number of octets to pad")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("count", Type.INTEGER, "The number of octets to pad"),
                            param("octet", Type.INTEGER, "The octet value to use for padding (0-255)")
                    )
            )
    );

    static final FunctionSignature[] FS_PAD_RIGHT = functionSignatures(
            QN_PAD_RIGHT,
            "Pads binary data on the right to a specified size.",
            returnsOpt(Type.BASE64_BINARY),
            arities(
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("count", Type.INTEGER, "The number of octets to pad")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data"),
                            param("count", Type.INTEGER, "The number of octets to pad"),
                            param("octet", Type.INTEGER, "The octet value to use for padding (0-255)")
                    )
            )
    );

    static final FunctionSignature FS_FIND = functionSignature(
            QN_FIND,
            "Returns the first location of a binary search sequence within binary data.",
            returnsOpt(Type.INTEGER),
            optParam("value", Type.BASE64_BINARY, "The binary data to search"),
            param("offset", Type.INTEGER, "The zero-based offset to start searching from"),
            param("search", Type.BASE64_BINARY, "The binary data to search for")
    );

    public BinaryBasicFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("length")) {
            return length(args);
        } else if (isCalledAs("part")) {
            return part(args);
        } else if (isCalledAs("join")) {
            return join(args);
        } else if (isCalledAs("insert-before")) {
            return insertBefore(args);
        } else if (isCalledAs("pad-left")) {
            return padLeft(args);
        } else if (isCalledAs("pad-right")) {
            return padRight(args);
        } else {
            return find(args);
        }
    }

    private Sequence length(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            throw new XPathException(this, org.exist.xquery.ErrorCodes.XPTY0004,
                    "Empty sequence is not allowed as argument to bin:length()");
        }
        return new IntegerValue(this, data.length);
    }

    private Sequence part(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (offset < 0 || offset > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " is out of range for binary data of length " + data.length);
        }

        final int size;
        if (args.length > 2 && !args[2].isEmpty()) {
            size = ((IntegerValue) args[2].itemAt(0)).getInt();
            if (size < 0) {
                throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                        "Size must not be negative: " + size);
            }
            if (offset + size > data.length) {
                throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                        "Offset " + offset + " + size " + size + " exceeds binary data length " + data.length);
            }
        } else {
            size = data.length - offset;
        }

        final byte[] result = Arrays.copyOfRange(data, offset, offset + size);
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence join(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            for (int i = 0; i < args[0].getItemCount(); i++) {
                final byte[] data = BinaryModuleHelper.getBinaryData(args[0].itemAt(i).toSequence());
                if (data != null) {
                    os.write(data);
                }
            }
            return BinaryModuleHelper.createBinaryResult(context, this, os.toByteArray());
        } catch (final IOException e) {
            throw new XPathException(this, "Failed to join binary data: " + e.getMessage(), e);
        }
    }

    private Sequence insertBefore(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (offset < 0 || offset > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " is out of range for binary data of length " + data.length);
        }

        final byte[] extra = BinaryModuleHelper.getBinaryData(args[2]);
        if (extra == null || extra.length == 0) {
            return BinaryModuleHelper.createBinaryResult(context, this, data);
        }

        final byte[] result = new byte[data.length + extra.length];
        System.arraycopy(data, 0, result, 0, offset);
        System.arraycopy(extra, 0, result, offset, extra.length);
        System.arraycopy(data, offset, result, offset + extra.length, data.length - offset);
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence padLeft(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int count = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (count < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Pad count must not be negative: " + count);
        }

        final byte octet = (args.length > 2 && !args[2].isEmpty())
                ? (byte) ((IntegerValue) args[2].itemAt(0)).getInt()
                : 0;

        final byte[] result = new byte[data.length + count];
        Arrays.fill(result, 0, count, octet);
        System.arraycopy(data, 0, result, count, data.length);
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence padRight(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int count = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (count < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Pad count must not be negative: " + count);
        }

        final byte octet = (args.length > 2 && !args[2].isEmpty())
                ? (byte) ((IntegerValue) args[2].itemAt(0)).getInt()
                : 0;

        final byte[] result = new byte[data.length + count];
        System.arraycopy(data, 0, result, 0, data.length);
        Arrays.fill(result, data.length, result.length, octet);
        return BinaryModuleHelper.createBinaryResult(context, this, result);
    }

    private Sequence find(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        if (offset < 0 || offset > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " is out of range for binary data of length " + data.length);
        }

        final byte[] search = BinaryModuleHelper.getBinaryData(args[2]);
        if (search == null || search.length == 0) {
            return new IntegerValue(this, offset);
        }

        // Naive byte subsequence search
        outer:
        for (int i = offset; i <= data.length - search.length; i++) {
            for (int j = 0; j < search.length; j++) {
                if (data[i + j] != search[j]) {
                    continue outer;
                }
            }
            return new IntegerValue(this, i);
        }

        return Sequence.EMPTY_SEQUENCE;
    }
}
