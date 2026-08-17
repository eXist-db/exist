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
import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 * Error codes for the EXPath Binary Module 4.0.
 *
 * @see <a href="https://qt4cg.org/specifications/expath-binary-40/Overview.html#errors">EXPath Binary Module 4.0 - Errors</a>
 */
public class BinaryModuleErrorCode {

    public static final ErrorCode NON_NUMERIC_CHARACTER = new ErrorCode(
            new QName("non-numeric-character", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "The argument to bin:hex(), bin:bin(), or bin:octal() contains a character that is not valid for the specified notation.");

    public static final ErrorCode INDEX_OUT_OF_RANGE = new ErrorCode(
            new QName("index-out-of-range", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "Offset and/or size is out of range for the given binary data.");

    public static final ErrorCode NEGATIVE_SIZE = new ErrorCode(
            new QName("negative-size", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "Size, count, or padding is negative.");

    public static final ErrorCode UNKNOWN_ENCODING = new ErrorCode(
            new QName("unknown-encoding", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "The specified encoding is not supported.");

    public static final ErrorCode CONVERSION_ERROR = new ErrorCode(
            new QName("conversion-error", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "An error occurred during encoding or decoding of a string.");

    public static final ErrorCode DIFFERING_LENGTH_ARGUMENTS = new ErrorCode(
            new QName("differing-length-arguments", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "The arguments to a bitwise operation are of differing length.");

    public static final ErrorCode INVALID_ENCODING = new ErrorCode(
            new QName("invalid-encoding", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "The encoding is invalid for the given data.");

    public static final ErrorCode INTEGER_TOO_LARGE = new ErrorCode(
            new QName("integer-too-large", BinaryModule.NAMESPACE_URI, BinaryModule.PREFIX),
            "Integer value exceeds the implementation-defined maximum.");

    private BinaryModuleErrorCode() {
    }
}
