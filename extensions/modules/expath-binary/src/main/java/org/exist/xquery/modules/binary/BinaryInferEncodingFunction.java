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
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;

import static org.exist.xquery.FunctionDSL.*;

/**
 * EXPath Binary Module 4.0 — bin:infer-encoding (Section 6.3).
 *
 * <p>Infers the actual encoding and byte offset of text data within binary data,
 * based on BOM detection and the declared encoding.</p>
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#infer-encoding">EXPath Binary Module 4.0 §6.3</a>
 */
public class BinaryInferEncodingFunction extends BasicFunction {

    private static final QName QN_INFER_ENCODING = new QName("infer-encoding", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX);

    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final byte[] UTF16_BE_BOM = {(byte) 0xFE, (byte) 0xFF};
    private static final byte[] UTF16_LE_BOM = {(byte) 0xFF, (byte) 0xFE};

    static final FunctionSignature[] FS_INFER_ENCODING = functionSignatures(
            QN_INFER_ENCODING,
            "Infers the actual encoding and data offset from binary data, detecting BOMs and resolving encoding families.",
            returns(Type.MAP_ITEM),
            arities(
                    arity(
                            param("data", Type.BASE64_BINARY, "The binary data to analyze")
                    ),
                    arity(
                            param("data", Type.BASE64_BINARY, "The binary data to analyze"),
                            optParam("encoding", Type.STRING, "The declared encoding (default: UTF-8)")
                    )
            )
    );

    public BinaryInferEncodingFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final byte[] data = BinaryModuleHelper.getBinaryData(args[0]);
        if (data == null) {
            throw new XPathException(this, org.exist.xquery.ErrorCodes.XPTY0004,
                    "Empty sequence is not allowed as the first argument of bin:infer-encoding");
        }

        final String declaredEncoding;
        if (args.length > 1 && !args[1].isEmpty()) {
            declaredEncoding = args[1].getStringValue();
        } else {
            declaredEncoding = "UTF-8";
        }

        validateEncoding(declaredEncoding);

        final String normalizedEncoding = normalizeEncodingFamily(declaredEncoding);
        String resultEncoding = declaredEncoding;
        int resultOffset = 0;

        if (isUtf8Family(normalizedEncoding)) {
            if (startsWith(data, UTF8_BOM)) {
                resultEncoding = "UTF-8";
                resultOffset = 3;
            }
        } else if (isUtf16Family(normalizedEncoding)) {
            if (startsWith(data, UTF16_BE_BOM)) {
                resultEncoding = "UTF-16BE";
                resultOffset = 2;
            } else if (startsWith(data, UTF16_LE_BOM)) {
                resultEncoding = "UTF-16LE";
                resultOffset = 2;
            } else if ("UTF-16".equalsIgnoreCase(normalizedEncoding)) {
                resultEncoding = "UTF-16BE";
                resultOffset = 0;
            }
        }

        final MapType result = new MapType(this, context);
        result.add(new StringValue(this, "encoding"), new StringValue(this, resultEncoding));
        result.add(new StringValue(this, "offset"), new IntegerValue(this, resultOffset));
        return result;
    }

    private void validateEncoding(final String encoding) throws XPathException {
        try {
            Charset.forName(encoding);
        } catch (final UnsupportedCharsetException e) {
            throw new XPathException(this, BinaryModuleErrorCode.UNKNOWN_ENCODING,
                    "Unknown encoding: '" + encoding + "'");
        }
    }

    private static String normalizeEncodingFamily(final String encoding) {
        return encoding.toUpperCase().replace("-", "").replace("_", "");
    }

    private static boolean isUtf8Family(final String normalized) {
        return "UTF8".equals(normalized);
    }

    private static boolean isUtf16Family(final String normalized) {
        return "UTF16".equals(normalized) || "UTF16BE".equals(normalized) || "UTF16LE".equals(normalized);
    }

    private static boolean startsWith(final byte[] data, final byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
