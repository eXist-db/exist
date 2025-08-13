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
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.ErrorCodes;

class UtilErrorCodes extends ErrorCodes.ErrorCode {
    public static final ErrorCodes.ErrorCode UNRECOGNIZED_ENCODING = new UtilErrorCodes("UNRECOGNIZED_ENCODING",
            "The encoding is not recognized.");

    public static final ErrorCodes.ErrorCode IO_ERROR = new UtilErrorCodes("IO_ERROR",
            "There was an issue accessing system resources.");

    UtilErrorCodes(final String code, final String description) {
        super(new QName(code, UtilModule.NAMESPACE_URI, UtilModule.PREFIX), description);
    }
}
