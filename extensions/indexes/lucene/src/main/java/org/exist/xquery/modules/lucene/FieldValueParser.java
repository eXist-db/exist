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
package org.exist.xquery.modules.lucene;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.BooleanValue;

/**
 * Parses string representations to XQuery atomic values for Lucene field retrieval.
 * Supports xs:boolean lexical forms: "true", "false", "1", "0" (per XQuery 3.1).
 */
final class FieldValueParser {

    private FieldValueParser() {}

    /**
     * Parse a string to xs:boolean per XQuery casting rules.
     * Accepts: "true", "false" (case-insensitive), "1", "0".
     *
     * @throws XPathException FORG0001 if the value is not a valid boolean lexical form
     */
    static BooleanValue parseBoolean(final String s) throws XPathException {
        final String trimmed = (s != null ? s : "").trim();
        if ("0".equals(trimmed) || "false".equalsIgnoreCase(trimmed)) {
            return BooleanValue.FALSE;
        }
        if ("1".equals(trimmed) || "true".equalsIgnoreCase(trimmed)) {
            return BooleanValue.TRUE;
        }
        throw new XPathException(ErrorCodes.FORG0001,
            "cannot convert string '" + (s != null ? s : "") + "' to boolean");
    }
}
