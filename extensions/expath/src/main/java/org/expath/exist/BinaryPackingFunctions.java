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
package org.expath.exist;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DoubleValue;
import org.exist.xquery.value.FloatValue;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — Numeric Packing and Unpacking (Section 7).
 *
 * <ul>
 *   <li>bin:pack-double</li>
 *   <li>bin:pack-float</li>
 *   <li>bin:pack-integer</li>
 *   <li>bin:unpack-double</li>
 *   <li>bin:unpack-float</li>
 *   <li>bin:unpack-integer</li>
 *   <li>bin:unpack-unsigned-integer</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#numeric-packing-and-unpacking">EXPath Binary Module 4.0 §7</a>
 */
public class BinaryPackingFunctions extends BasicFunction {

    private static final QName QN_PACK_DOUBLE = new QName("pack-double", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_PACK_FLOAT = new QName("pack-float", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_PACK_INTEGER = new QName("pack-integer", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_UNPACK_DOUBLE = new QName("unpack-double", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_UNPACK_FLOAT = new QName("unpack-float", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_UNPACK_INTEGER = new QName("unpack-integer", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_UNPACK_UNSIGNED_INTEGER = new QName("unpack-unsigned-integer", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    static final FunctionSignature[] FS_PACK_DOUBLE = functionSignatures(
            QN_PACK_DOUBLE,
            "Returns the 8-octet binary representation of an xs:double value.",
            returns(Type.BASE64_BINARY),
            arities(
                    arity(param("value", Type.DOUBLE, "The double value to pack")),
                    arity(param("value", Type.DOUBLE, "The double value to pack"),
                          param("order", Type.STRING, "The octet order: 'most-significant-first' (default), 'big-endian', 'BE', 'least-significant-first', 'little-endian', 'LE'"))
            )
    );

    static final FunctionSignature[] FS_PACK_FLOAT = functionSignatures(
            QN_PACK_FLOAT,
            "Returns the 4-octet binary representation of an xs:float value.",
            returns(Type.BASE64_BINARY),
            arities(
                    arity(param("value", Type.FLOAT, "The float value to pack")),
                    arity(param("value", Type.FLOAT, "The float value to pack"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    static final FunctionSignature[] FS_PACK_INTEGER = functionSignatures(
            QN_PACK_INTEGER,
            "Returns the two's-complement binary representation of an xs:integer value.",
            returns(Type.BASE64_BINARY),
            arities(
                    arity(param("value", Type.INTEGER, "The integer value to pack"),
                          param("size", Type.INTEGER, "The number of octets in the result")),
                    arity(param("value", Type.INTEGER, "The integer value to pack"),
                          param("size", Type.INTEGER, "The number of octets in the result"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    static final FunctionSignature[] FS_UNPACK_DOUBLE = functionSignatures(
            QN_UNPACK_DOUBLE,
            "Extracts an xs:double value from binary data.",
            returns(Type.DOUBLE),
            arities(
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset")),
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    static final FunctionSignature[] FS_UNPACK_FLOAT = functionSignatures(
            QN_UNPACK_FLOAT,
            "Extracts an xs:float value from binary data.",
            returns(Type.FLOAT),
            arities(
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset")),
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    static final FunctionSignature[] FS_UNPACK_INTEGER = functionSignatures(
            QN_UNPACK_INTEGER,
            "Extracts a signed xs:integer value from binary data.",
            returns(Type.INTEGER),
            arities(
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("size", Type.INTEGER, "The number of octets to read")),
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("size", Type.INTEGER, "The number of octets to read"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    static final FunctionSignature[] FS_UNPACK_UNSIGNED_INTEGER = functionSignatures(
            QN_UNPACK_UNSIGNED_INTEGER,
            "Extracts an unsigned xs:integer value from binary data.",
            returns(Type.INTEGER),
            arities(
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("size", Type.INTEGER, "The number of octets to read")),
                    arity(param("value", Type.BASE64_BINARY, "The binary data"),
                          param("offset", Type.INTEGER, "The zero-based byte offset"),
                          param("size", Type.INTEGER, "The number of octets to read"),
                          param("order", Type.STRING, "The octet order"))
            )
    );

    public BinaryPackingFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("pack-double")) {
            return packDouble(args);
        } else if (isCalledAs("pack-float")) {
            return packFloat(args);
        } else if (isCalledAs("pack-integer")) {
            return packInteger(args);
        } else if (isCalledAs("unpack-double")) {
            return unpackDouble(args);
        } else if (isCalledAs("unpack-float")) {
            return unpackFloat(args);
        } else if (isCalledAs("unpack-integer")) {
            return unpackInteger(args);
        } else {
            return unpackUnsignedInteger(args);
        }
    }

    private boolean getByteOrder(final Sequence[] args, final int orderArgIndex) throws XPathException {
        if (args.length > orderArgIndex && !args[orderArgIndex].isEmpty()) {
            return BinaryModuleHelper.isLittleEndian(this, args[orderArgIndex].getStringValue());
        }
        return false; // big-endian by default
    }

    private Sequence packDouble(final Sequence[] args) throws XPathException {
        final double value = ((DoubleValue) args[0].itemAt(0)).getDouble();
        final boolean le = getByteOrder(args, 1);

        final byte[] data = new byte[8];
        ByteBuffer.wrap(data).putLong(Double.doubleToRawLongBits(value));
        if (le) {
            BinaryModuleHelper.reverseBytes(data);
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }

    private Sequence packFloat(final Sequence[] args) throws XPathException {
        final float value = ((FloatValue) args[0].itemAt(0)).getValue();
        final boolean le = getByteOrder(args, 1);

        final byte[] data = new byte[4];
        ByteBuffer.wrap(data).putInt(Float.floatToRawIntBits(value));
        if (le) {
            BinaryModuleHelper.reverseBytes(data);
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }

    private Sequence packInteger(final Sequence[] args) throws XPathException {
        final BigInteger value = ((IntegerValue) args[0].itemAt(0)).toJavaObject(BigInteger.class);
        final int size = ((IntegerValue) args[1].itemAt(0)).getInt();
        final boolean le = getByteOrder(args, 2);

        if (size < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Size must not be negative: " + size);
        }

        if (size == 0) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        final byte[] twosComplement = value.toByteArray();
        final byte[] data = new byte[size];

        // Fill with sign extension byte (0x00 for positive, 0xFF for negative)
        if (value.signum() < 0) {
            Arrays.fill(data, (byte) 0xFF);
        }

        // Copy the significant bytes into the result, right-aligned (big-endian)
        if (twosComplement.length <= size) {
            System.arraycopy(twosComplement, 0, data, size - twosComplement.length, twosComplement.length);
        } else {
            // Truncate from the left (most significant bytes)
            System.arraycopy(twosComplement, twosComplement.length - size, data, 0, size);
        }

        if (le) {
            BinaryModuleHelper.reverseBytes(data);
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }

    private Sequence unpackDouble(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        final boolean le = getByteOrder(args, 2);

        validateUnpackRange(data, offset, 8);

        final byte[] slice = Arrays.copyOfRange(data, offset, offset + 8);
        if (le) {
            BinaryModuleHelper.reverseBytes(slice);
        }
        final long bits = ByteBuffer.wrap(slice).getLong();
        return new DoubleValue(this, Double.longBitsToDouble(bits));
    }

    private Sequence unpackFloat(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        final boolean le = getByteOrder(args, 2);

        validateUnpackRange(data, offset, 4);

        final byte[] slice = Arrays.copyOfRange(data, offset, offset + 4);
        if (le) {
            BinaryModuleHelper.reverseBytes(slice);
        }
        final int bits = ByteBuffer.wrap(slice).getInt();
        return new FloatValue(this, Float.intBitsToFloat(bits));
    }

    private Sequence unpackInteger(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        final int size = ((IntegerValue) args[2].itemAt(0)).getInt();
        final boolean le = getByteOrder(args, 3);

        if (size < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Size must not be negative: " + size);
        }

        validateUnpackRange(data, offset, size);

        if (size == 0) {
            return new IntegerValue(this, 0);
        }

        final byte[] slice = Arrays.copyOfRange(data, offset, offset + size);
        if (le) {
            BinaryModuleHelper.reverseBytes(slice);
        }
        // BigInteger(byte[]) interprets as signed two's-complement
        final BigInteger result = new BigInteger(slice);
        return new IntegerValue(this, result);
    }

    private Sequence unpackUnsignedInteger(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        final int offset = ((IntegerValue) args[1].itemAt(0)).getInt();
        final int size = ((IntegerValue) args[2].itemAt(0)).getInt();
        final boolean le = getByteOrder(args, 3);

        if (size < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Size must not be negative: " + size);
        }

        validateUnpackRange(data, offset, size);

        if (size == 0) {
            return new IntegerValue(this, 0);
        }

        final byte[] slice = Arrays.copyOfRange(data, offset, offset + size);
        if (le) {
            BinaryModuleHelper.reverseBytes(slice);
        }
        // BigInteger(1, byte[]) interprets as unsigned (positive signum)
        final BigInteger result = new BigInteger(1, slice);
        return new IntegerValue(this, result);
    }

    private void validateUnpackRange(final byte[] data, final int offset, final int size) throws XPathException {
        if (data == null) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Binary data is empty");
        }
        if (offset < 0 || offset + size > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " + size " + size + " exceeds binary data length " + data.length);
        }
    }
}
