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
package org.expath.exist.file;

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes.ErrorCode;

/**
 * Error codes defined by the EXPath File Module 4.0.
 *
 * @see <a href="https://qt4cg.org/specifications/expath-file-40/Overview.html">EXPath File Module 4.0</a>
 */
public class ExpathFileErrorCode {

    public static final ErrorCode NOT_FOUND = new ErrorCode(
            new QName("not-found", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path does not exist.");

    public static final ErrorCode INVALID_PATH = new ErrorCode(
            new QName("invalid-path", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path is invalid.");

    public static final ErrorCode EXISTS = new ErrorCode(
            new QName("exists", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path already exists.");

    public static final ErrorCode NO_DIR = new ErrorCode(
            new QName("no-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path does not point to a directory.");

    public static final ErrorCode IS_DIR = new ErrorCode(
            new QName("is-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path points to a directory.");

    public static final ErrorCode IS_RELATIVE = new ErrorCode(
            new QName("is-relative", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified path is relative.");

    public static final ErrorCode UNKNOWN_ENCODING = new ErrorCode(
            new QName("unknown-encoding", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified encoding is not supported.");

    public static final ErrorCode OUT_OF_RANGE = new ErrorCode(
            new QName("out-of-range", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "The specified offset or length is out of range.");

    public static final ErrorCode IO_ERROR = new ErrorCode(
            new QName("io-error", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
            "A generic file system error occurred.");

    private ExpathFileErrorCode() {
        // no instances
    }
}
