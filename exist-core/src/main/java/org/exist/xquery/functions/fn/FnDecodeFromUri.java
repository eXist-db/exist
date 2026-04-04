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

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * Implements XQuery 4.0 fn:decode-from-uri.
 *
 * Decodes a URI-encoded string. Replaces '+' with space.
 * Invalid/incomplete percent sequences are replaced with U+FFFD.
 * Resulting octets are decoded as UTF-8; invalid UTF-8 is replaced with U+FFFD.
 * XML-invalid codepoints are replaced with U+FFFD.
 */
public class FnDecodeFromUri extends BasicFunction {

    private static final char REPLACEMENT = '\uFFFD';

    public static final FunctionSignature FN_DECODE_FROM_URI = new FunctionSignature(
            new QName("decode-from-uri", Function.BUILTIN_FUNCTION_NS),
            "Decodes a URI-encoded string.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("value", Type.STRING, Cardinality.ZERO_OR_ONE, "The URI-encoded string to decode")
            },
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the decoded string"));

    public FnDecodeFromUri(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (args[0].isEmpty()) {
            return new StringValue(this, "");
        }

        final String input = args[0].getStringValue();

        // Phase 1: decode percent-encoding and '+' to bytes, collecting raw bytes
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream(input.length());
        final StringBuilder result = new StringBuilder(input.length());

        int i = 0;
        while (i < input.length()) {
            final char c = input.charAt(i);
            if (c == '+') {
                // Flush any accumulated bytes first
                flushBytes(bytes, result);
                result.append(' ');
                i++;
            } else if (c == '%') {
                // Try to read percent-encoded byte
                if (i + 2 < input.length() && isAscii(input.charAt(i + 1))) {
                    // Two chars follow and first is ASCII — treat as percent triplet
                    final int hi = hexDigit(input.charAt(i + 1));
                    final int lo = hexDigit(input.charAt(i + 2));
                    if (hi >= 0 && lo >= 0) {
                        bytes.write((hi << 4) | lo);
                        i += 3;
                    } else {
                        // Invalid hex pair: consume all 3 chars, produce one replacement
                        flushBytes(bytes, result);
                        result.append(REPLACEMENT);
                        i += 3;
                    }
                } else if (i + 1 < input.length()) {
                    // First char after % is non-ASCII, or only 1 char follows
                    // Consume % + next char, produce replacement
                    flushBytes(bytes, result);
                    result.append(REPLACEMENT);
                    i += 2;
                } else {
                    // % at end of string
                    flushBytes(bytes, result);
                    result.append(REPLACEMENT);
                    i++;
                }
            } else {
                flushBytes(bytes, result);
                result.append(c);
                i++;
            }
        }
        flushBytes(bytes, result);

        // Phase 2: replace XML-invalid codepoints (handle surrogate pairs for supplementary chars)
        final StringBuilder cleaned = new StringBuilder(result.length());
        for (int j = 0; j < result.length(); j++) {
            final char ch = result.charAt(j);
            if (Character.isHighSurrogate(ch) && j + 1 < result.length()
                    && Character.isLowSurrogate(result.charAt(j + 1))) {
                // Valid surrogate pair = supplementary character (valid in XML 1.0 4th+ edition)
                cleaned.append(ch);
                cleaned.append(result.charAt(++j));
            } else if (isXmlValid(ch)) {
                cleaned.append(ch);
            } else {
                cleaned.append(REPLACEMENT);
            }
        }

        return new StringValue(this, cleaned.toString());
    }

    /**
     * Flush accumulated bytes as UTF-8, replacing invalid sequences with U+FFFD.
     */
    private void flushBytes(final ByteArrayOutputStream bytes, final StringBuilder result) {
        if (bytes.size() == 0) {
            return;
        }
        final byte[] data = bytes.toByteArray();
        bytes.reset();

        final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPLACE)
                .onUnmappableCharacter(CodingErrorAction.REPLACE)
                .replaceWith("\uFFFD");

        final ByteBuffer bb = ByteBuffer.wrap(data);
        final CharBuffer cb = CharBuffer.allocate(data.length * 2);
        decoder.decode(bb, cb, true);
        decoder.flush(cb);
        cb.flip();
        result.append(cb);
    }

    private static int hexDigit(final char c) {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    }

    private static boolean isAscii(final char c) {
        return c <= 0x7F;
    }

    private static boolean isXmlValid(final char c) {
        return c == 0x9 || c == 0xA || c == 0xD ||
                (c >= 0x20 && c <= 0xD7FF) ||
                (c >= 0xE000 && c <= 0xFFFD);
    }
}
