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

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.xquery.Expression;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

/**
 * Helper utilities for the EXPath File Module.
 */
public class ExpathFileModuleHelper {

    private ExpathFileModuleHelper() {
        // no instances
    }

    /**
     * Check that the calling user has DBA role.
     *
     * @param context the XQuery context
     * @param expression the calling expression (for error reporting)
     * @throws XPathException if the user is not a DBA
     */
    public static void checkDbaRole(final XQueryContext context, final Expression expression) throws XPathException {
        if (!context.getSubject().hasDbaRole()) {
            throw new XPathException(expression,
                    "Permission denied, calling user '" + context.getSubject().getName() +
                    "' must be a DBA to call this function.");
        }
    }

    /**
     * Resolve a path string (file: URI or native path) to a {@link Path}.
     *
     * @param path the path string or file: URI
     * @param expression the calling expression (for error reporting)
     * @return the resolved Path
     * @throws XPathException if the path is invalid
     */
    public static Path getPath(final String path, final Expression expression) throws XPathException {
        try {
            if (path.startsWith("file:")) {
                return Paths.get(new URI(path));
            } else {
                return Paths.get(path);
            }
        } catch (final InvalidPathException e) {
            throw new XPathException(expression, ExpathFileErrorCode.INVALID_PATH,
                    "Invalid path: " + path + " - " + e.getMessage());
        } catch (final Exception e) {
            throw new XPathException(expression, ExpathFileErrorCode.INVALID_PATH,
                    path + " is not a valid path or URI: " + e.getMessage());
        }
    }

    /**
     * Validate and return a {@link Charset} for the given encoding name.
     *
     * @param encoding the encoding name
     * @param expression the calling expression (for error reporting)
     * @return the Charset
     * @throws XPathException if the encoding is not supported
     */
    public static Charset getCharset(final String encoding, final Expression expression) throws XPathException {
        try {
            return Charset.forName(encoding);
        } catch (final UnsupportedCharsetException e) {
            throw new XPathException(expression, ExpathFileErrorCode.UNKNOWN_ENCODING,
                    "Unknown encoding: " + encoding);
        }
    }
}
