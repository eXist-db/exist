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
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Arrays;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — Text Encoding and Decoding (Section 6).
 *
 * <ul>
 *   <li>bin:decode-string</li>
 *   <li>bin:encode-string</li>
 * </ul>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#text-decoding-and-encoding">EXPath Binary Module 4.0 §6</a>
 */
public class BinaryTextFunctions extends BasicFunction {

    private static final QName QN_DECODE_STRING = new QName("decode-string", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);
    private static final QName QN_ENCODE_STRING = new QName("encode-string", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    static final FunctionSignature[] FS_DECODE_STRING = functionSignatures(
            QN_DECODE_STRING,
            "Decodes binary data to an xs:string using the specified encoding.",
            returnsOpt(Type.STRING),
            arities(
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data to decode")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data to decode"),
                            param("encoding", Type.STRING, "The character encoding (default: UTF-8)")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data to decode"),
                            param("encoding", Type.STRING, "The character encoding (default: UTF-8)"),
                            param("offset", Type.INTEGER, "The zero-based byte offset to start decoding")
                    ),
                    arity(
                            optParam("value", Type.BASE64_BINARY, "The binary data to decode"),
                            param("encoding", Type.STRING, "The character encoding (default: UTF-8)"),
                            param("offset", Type.INTEGER, "The zero-based byte offset to start decoding"),
                            param("size", Type.INTEGER, "The number of bytes to decode")
                    )
            )
    );

    static final FunctionSignature[] FS_ENCODE_STRING = functionSignatures(
            QN_ENCODE_STRING,
            "Encodes an xs:string to binary data using the specified encoding.",
            returnsOpt(Type.BASE64_BINARY),
            arities(
                    arity(
                            optParam("value", Type.STRING, "The string to encode")
                    ),
                    arity(
                            optParam("value", Type.STRING, "The string to encode"),
                            param("encoding", Type.STRING, "The character encoding (default: UTF-8)")
                    )
            )
    );

    public BinaryTextFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (isCalledAs("decode-string")) {
            return decodeString(args);
        } else {
            return encodeString(args);
        }
    }

    private Sequence decodeString(final Sequence[] args) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String encoding = (args.length > 1 && !args[1].isEmpty())
                ? args[1].getStringValue()
                : "UTF-8";

        final int offset = (args.length > 2 && !args[2].isEmpty())
                ? ((IntegerValue) args[2].itemAt(0)).getInt()
                : 0;

        final int size = (args.length > 3 && !args[3].isEmpty())
                ? ((IntegerValue) args[3].itemAt(0)).getInt()
                : data.length - offset;

        if (offset < 0 || offset > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " is out of range for binary data of length " + data.length);
        }

        if (size < 0) {
            throw new XPathException(this, BinaryModuleErrorCode.NEGATIVE_SIZE,
                    "Size must not be negative: " + size);
        }

        if (offset + size > data.length) {
            throw new XPathException(this, BinaryModuleErrorCode.INDEX_OUT_OF_RANGE,
                    "Offset " + offset + " + size " + size + " exceeds binary data length " + data.length);
        }

        final Charset charset = resolveCharset(encoding);

        try {
            final CharsetDecoder decoder = charset.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT);
            final CharBuffer result = decoder.decode(ByteBuffer.wrap(data, offset, size));
            return new StringValue(this, result.toString());
        } catch (final CharacterCodingException e) {
            throw new XPathException(this, BinaryModuleErrorCode.CONVERSION_ERROR,
                    "Failed to decode binary data using encoding '" + encoding + "': " + e.getMessage());
        }
    }

    private Sequence encodeString(final Sequence[] args) throws XPathException {
        if (args[0].isEmpty()) {
            return Sequence.EMPTY_SEQUENCE;
        }

        final String value = args[0].getStringValue();
        final String encoding = (args.length > 1 && !args[1].isEmpty())
                ? args[1].getStringValue()
                : "UTF-8";

        final Charset charset = resolveCharset(encoding);

        try {
            final ByteBuffer encoded = charset.newEncoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .encode(CharBuffer.wrap(value));
            final byte[] data = Arrays.copyOf(encoded.array(), encoded.limit());
            return BinaryModuleHelper.createBinaryResult(context, this, data);
        } catch (final CharacterCodingException e) {
            throw new XPathException(this, BinaryModuleErrorCode.CONVERSION_ERROR,
                    "Failed to encode string using encoding '" + encoding + "': " + e.getMessage());
        }
    }

    private Charset resolveCharset(final String encoding) throws XPathException {
        try {
            return Charset.forName(encoding);
        } catch (final UnsupportedCharsetException e) {
            throw new XPathException(this, BinaryModuleErrorCode.UNKNOWN_ENCODING,
                    "Unknown encoding: '" + encoding + "'");
        }
    }
}
