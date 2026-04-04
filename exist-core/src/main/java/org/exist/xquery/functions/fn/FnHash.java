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

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.CRC32;

import org.bouncycastle.crypto.digests.Blake3Digest;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.ErrorCodes;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromBinaryString;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.HexBinaryValueType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Implements fn:hash (XQuery 4.0).
 *
 * Returns the result of a hash/checksum function applied to the input.
 * Supports MD5, SHA-1, SHA-256, CRC-32.
 */
public class FnHash extends BasicFunction {

    public static final ErrorCodes.ErrorCode FOHA0001 = new ErrorCodes.ErrorCode("FOHA0001",
            "Unsupported hash algorithm");

    public static final FunctionSignature[] FN_HASH = {
            new FunctionSignature(
                    new QName("hash", Function.BUILTIN_FUNCTION_NS),
                    "Returns the hash of the input value using the default algorithm (MD5).",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_ONE, "The value to hash (string, hexBinary, or base64Binary)")
                    },
                    new FunctionReturnSequenceType(Type.HEX_BINARY, Cardinality.ZERO_OR_ONE, "the hash value")),
            new FunctionSignature(
                    new QName("hash", Function.BUILTIN_FUNCTION_NS),
                    "Returns the hash of the input value using the specified algorithm.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_ONE, "The value to hash (string, hexBinary, or base64Binary)"),
                            new FunctionParameterSequenceType("algorithm", Type.STRING, Cardinality.ZERO_OR_ONE, "The hash algorithm (MD5, SHA-1, SHA-256, CRC-32)")
                    },
                    new FunctionReturnSequenceType(Type.HEX_BINARY, Cardinality.ZERO_OR_ONE, "the hash value")),
            new FunctionSignature(
                    new QName("hash", Function.BUILTIN_FUNCTION_NS),
                    "Returns the hash of the input value using the specified algorithm and options.",
                    new SequenceType[] {
                            new FunctionParameterSequenceType("value", Type.ITEM, Cardinality.ZERO_OR_ONE, "The value to hash (string, hexBinary, or base64Binary)"),
                            new FunctionParameterSequenceType("algorithm", Type.STRING, Cardinality.ZERO_OR_ONE, "The hash algorithm (MD5, SHA-1, SHA-256, CRC-32)"),
                            new FunctionParameterSequenceType("options", Type.MAP_ITEM, Cardinality.ZERO_OR_ONE, "Options map (reserved for future use)")
                    },
                    new FunctionReturnSequenceType(Type.HEX_BINARY, Cardinality.ZERO_OR_ONE, "the hash value"))
    };

    public FnHash(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        // Get the input bytes
        final byte[] inputBytes = getInputBytes(args[0]);

        // Get the algorithm
        String algorithm = "MD5";
        if (args.length > 1 && !args[1].isEmpty()) {
            algorithm = args[1].getStringValue().trim().toUpperCase();
        }

        // Compute hash
        final byte[] hashBytes;
        if ("CRC-32".equals(algorithm) || "CRC32".equals(algorithm)) {
            final CRC32 crc32 = new CRC32();
            crc32.update(inputBytes);
            final long crcValue = crc32.getValue();
            // Return as 4-byte big-endian hexBinary
            hashBytes = ByteBuffer.allocate(4).putInt((int) crcValue).array();
        } else if ("BLAKE3".equals(algorithm)) {
            final Blake3Digest blake3 = new Blake3Digest(32);
            blake3.update(inputBytes, 0, inputBytes.length);
            hashBytes = new byte[32];
            blake3.doFinal(hashBytes, 0);
        } else {
            // Map algorithm names to Java MessageDigest names
            final String javaAlgorithm;
            switch (algorithm) {
                case "MD5":
                    javaAlgorithm = "MD5";
                    break;
                case "SHA-1":
                case "SHA1":
                    javaAlgorithm = "SHA-1";
                    break;
                case "SHA-256":
                case "SHA256":
                    javaAlgorithm = "SHA-256";
                    break;
                case "SHA-384":
                case "SHA384":
                    javaAlgorithm = "SHA-384";
                    break;
                case "SHA-512":
                case "SHA512":
                    javaAlgorithm = "SHA-512";
                    break;
                default:
                    throw new XPathException(this, FOHA0001,
                            "Unsupported hash algorithm: " + algorithm);
            }
            try {
                final MessageDigest digest = MessageDigest.getInstance(javaAlgorithm);
                hashBytes = digest.digest(inputBytes);
            } catch (final NoSuchAlgorithmException e) {
                throw new XPathException(this, FOHA0001,
                        "Hash algorithm not available: " + javaAlgorithm);
            }
        }

        // Return as hexBinary — use BinaryValueFromBinaryString to avoid
        // stream registration with the XQuery context (prevents deadlock
        // in concurrent test execution environments)
        final StringBuilder hex = new StringBuilder(hashBytes.length * 2);
        for (final byte b : hashBytes) {
            hex.append(String.format("%02X", b & 0xFF));
        }
        return new BinaryValueFromBinaryString(this, new HexBinaryValueType(), hex.toString());
    }

    private byte[] getInputBytes(final Sequence value) throws XPathException {
        final int type = value.itemAt(0).getType();
        if (Type.subTypeOf(type, Type.STRING) || Type.subTypeOf(type, Type.ANY_URI) || Type.subTypeOf(type, Type.UNTYPED_ATOMIC)) {
            return value.getStringValue().getBytes(StandardCharsets.UTF_8);
        } else if (Type.subTypeOf(type, Type.BASE64_BINARY) || Type.subTypeOf(type, Type.HEX_BINARY)) {
            final BinaryValue binaryValue = (BinaryValue) value.itemAt(0);
            return binaryValue.toJavaObject(byte[].class);
        } else {
            throw new XPathException(this, ErrorCodes.XPTY0004,
                    "fn:hash expects string, hexBinary, or base64Binary, got: " + Type.getTypeName(type));
        }
    }
}
