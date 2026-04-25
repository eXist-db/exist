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
import org.exist.xquery.value.ValueSequence;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — Constants and Conversions (Section 4).
 *
 * <ul>
 *   <li>bin:hex</li>
 *   <li>bin:bin</li>
 *   <li>bin:octal</li>
 *   <li>bin:to-octets</li>
 *   <li>bin:from-octets</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#encoding-and-decoding-of-values">EXPath Binary Module 4.0 §4</a>
 */
public class BinaryConversionFunctions extends BasicFunction {

    private static final QName QN_HEX = new QName("hex", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_BIN = new QName("bin", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_OCTAL = new QName("octal", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_TO_OCTETS = new QName("to-octets", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_FROM_OCTETS = new QName("from-octets", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    static final FunctionSignature FS_HEX = functionSignature(
            QN_HEX,
            "Creates an xs:base64Binary value from a hexadecimal string.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.STRING, "The hexadecimal string")
    );

    static final FunctionSignature FS_BIN = functionSignature(
            QN_BIN,
            "Creates an xs:base64Binary value from a binary (0/1) string.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.STRING, "The binary digit string")
    );

    static final FunctionSignature FS_OCTAL = functionSignature(
            QN_OCTAL,
            "Creates an xs:base64Binary value from an octal string.",
            returnsOpt(Type.BASE64_BINARY),
            optParam("value", Type.STRING, "The octal string")
    );

    static final FunctionSignature FS_TO_OCTETS = functionSignature(
            QN_TO_OCTETS,
            "Returns the binary data as a sequence of octets.",
            returnsOptMany(Type.INTEGER),
            param("value", Type.BASE64_BINARY, "The binary data")
    );

    static final FunctionSignature FS_FROM_OCTETS = functionSignature(
            QN_FROM_OCTETS,
            "Converts a sequence of octets into binary data.",
            returns(Type.BASE64_BINARY),
            optManyParam("values", Type.INTEGER, "The octet values (0-255)")
    );

    public BinaryConversionFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("hex")) {
            return hexToBinary(args);
        } else if (isCalledAs("bin")) {
            return binToBinary(args);
        } else if (isCalledAs("octal")) {
            return octalToBinary(args);
        } else if (isCalledAs("to-octets")) {
            return toOctets(args);
        } else {
            return fromOctets(args);
        }
    }

    private Sequence hexToBinary(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        String hex = args[0].getStringValue();
        // Strip whitespace and underscores per spec
        hex = hex.replaceAll("[\\s_]", "");

        if (hex.isEmpty()) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        // Validate characters
        for (int i = 0; i < hex.length(); i++) {
            final char c = hex.charAt(i);
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                throw new XPathException(this, BinaryModuleErrorCode.NON_NUMERIC_CHARACTER,
                        "Invalid hexadecimal character: '" + c + "'");
            }
        }

        // Prepend "0" if odd length
        if (hex.length() % 2 != 0) {
            hex = "0" + hex;
        }

        final byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }

    private Sequence binToBinary(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        String bin = args[0].getStringValue();
        bin = bin.replaceAll("[\\s_]", "");

        if (bin.isEmpty()) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        // Validate characters
        for (int i = 0; i < bin.length(); i++) {
            final char c = bin.charAt(i);
            if (c != '0' && c != '1') {
                throw new XPathException(this, BinaryModuleErrorCode.NON_NUMERIC_CHARACTER,
                        "Invalid binary character: '" + c + "'");
            }
        }

        // Pad to 8-bit multiple
        final int remainder = bin.length() % 8;
        if (remainder != 0) {
            bin = "0".repeat(8 - remainder) + bin;
        }

        final byte[] data = new byte[bin.length() / 8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(bin.substring(i * 8, i * 8 + 8), 2);
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }

    private Sequence octalToBinary(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String octal = stripAndValidateOctal(args[0].getStringValue());
        if (octal.isEmpty()) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        final String binaryStr = octalToBinaryString(octal);
        return BinaryModuleHelper.createBinaryResult(context, this, binaryStringToBytes(binaryStr));
    }

    private String stripAndValidateOctal(final String input) throws XPathException {
        final String octal = input.replaceAll("[\\s_]", "");
        for (int i = 0; i < octal.length(); i++) {
            final char c = octal.charAt(i);
            if (c < '0' || c > '7') {
                throw new XPathException(this, BinaryModuleErrorCode.NON_NUMERIC_CHARACTER,
                        "Invalid octal character: '" + c + "'");
            }
        }
        return octal;
    }

    private static String octalToBinaryString(final String octal) {
        // Convert each octal digit to 3-bit binary
        final StringBuilder bits = new StringBuilder();
        for (int i = 0; i < octal.length(); i++) {
            final int digit = octal.charAt(i) - '0';
            bits.append(String.format("%3s", Integer.toBinaryString(digit)).replace(' ', '0'));
        }

        // Strip up to 2 leading zeros (octal digit = 3 bits, but only multiples of 8 matter)
        String binaryStr = bits.toString();
        int stripCount = 0;
        while (stripCount < 2 && !binaryStr.isEmpty() && binaryStr.charAt(0) == '0'
                && (binaryStr.length() - 1) % 8 != 7) {
            binaryStr = binaryStr.substring(1);
            stripCount++;
        }

        // Pad to 8-bit multiple
        final int remainder = binaryStr.length() % 8;
        if (remainder != 0) {
            binaryStr = "0".repeat(8 - remainder) + binaryStr;
        }
        return binaryStr;
    }

    private static byte[] binaryStringToBytes(final String binaryStr) {
        if (binaryStr.isEmpty()) {
            return new byte[0];
        }
        final byte[] data = new byte[binaryStr.length() / 8];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) Integer.parseInt(binaryStr.substring(i * 8, i * 8 + 8), 2);
        }
        return data;
    }

    private Sequence toOctets(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null || data.length == 0) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence(data.length);
        for (final byte b : data) {
            result.add(new IntegerValue(this, b & 0xFF));
        }
        return result;
    }

    private Sequence fromOctets(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return BinaryModuleHelper.createBinaryResult(context, this, new byte[0]);
        }

        final int len = args[0].getItemCount();
        final byte[] data = new byte[len];
        for (int i = 0; i < len; i++) {
            data[i] = (byte) ((IntegerValue) args[0].itemAt(i)).getInt();
        }
        return BinaryModuleHelper.createBinaryResult(context, this, data);
    }
}
