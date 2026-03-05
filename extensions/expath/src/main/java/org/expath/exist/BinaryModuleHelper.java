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

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.Sequence;

import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Shared utility methods for the EXPath Binary Module functions.
 */
class BinaryModuleHelper {

    /**
     * Extracts a byte array from a binary sequence argument.
     *
     * @param arg the sequence argument (expected to contain a single binary value)
     * @return the byte array, or null if the argument is an empty sequence
     * @throws XPathException if the binary data cannot be read
     */
    @Nullable
    static byte[] getBinaryData(final Sequence arg) throws XPathException {
        if (arg.isEmpty()) {
            return null;
        }
        final BinaryValue binary = (BinaryValue) arg.itemAt(0);
        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            binary.streamBinaryTo(os);
            return os.toByteArray();
        } catch (final IOException e) {
            throw new XPathException((Expression) null, "Failed to read binary data: " + e.getMessage(), e);
        }
    }

    /**
     * Creates an xs:base64Binary value from a byte array.
     *
     * @param context the XQuery context
     * @param expr    the calling expression (for error reporting)
     * @param data    the byte array
     * @return the base64Binary value
     * @throws XPathException if the value cannot be created
     */
    static BinaryValue createBinaryResult(final XQueryContext context, final Expression expr, final byte[] data) throws XPathException {
        return BinaryValueFromInputStream.getInstance(
                context,
                new Base64BinaryValueType(),
                new UnsynchronizedByteArrayInputStream(data),
                expr
        );
    }

    /**
     * Validates the octet-order parameter string.
     *
     * @param order the order string
     * @return true if little-endian, false if big-endian
     * @throws XPathException if the value is not a valid octet order
     */
    static boolean isLittleEndian(final Expression expr, final String order) throws XPathException {
        switch (order) {
            case "most-significant-first":
            case "big-endian":
            case "BE":
                return false;
            case "least-significant-first":
            case "little-endian":
            case "LE":
                return true;
            default:
                throw new XPathException(expr,
                        org.exist.xquery.ErrorCodes.XPTY0004,
                        "Invalid octet order: '" + order + "'. Expected one of: most-significant-first, big-endian, BE, least-significant-first, little-endian, LE");
        }
    }

    /**
     * Reverses a byte array in place.
     */
    static void reverseBytes(final byte[] data) {
        for (int i = 0, j = data.length - 1; i < j; i++, j--) {
            final byte tmp = data[i];
            data[i] = data[j];
            data[j] = tmp;
        }
    }

    private BinaryModuleHelper() {
    }
}
