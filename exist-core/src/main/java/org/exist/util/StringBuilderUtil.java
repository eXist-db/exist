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
package org.exist.util;

import org.apache.xerces.util.XMLChar;

public class StringBuilderUtil {

    /**
     * Append a wide character to the String Builder.
     *
     * If the character code is greater than 0xffff,
     * then it is appended as a surrogate pair,
     * otherwise it is appended as a single character.
     *
     * @param builder the String Builder
     * @param ch the character code
     * @return the {@code builder}
     */
    public static StringBuilder appendWideChar(final StringBuilder builder, final int ch) {
        if (ch > 0xffff) {
            builder.append(XMLChar.highSurrogate(ch));
            builder.append(XMLChar.lowSurrogate(ch));
        } else {
            builder.append((char)ch);
        }
        return builder;
    }
}
