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
package org.exist.indexing;

import java.util.Locale;

/**
 * Scope of reindex operations for {@code xmldb:reindex($collection, $document?, $mode?)}.
 * Decouples fulltext reindex from vector recalculation.
 *
 * @see <a href="https://github.com/eXist-db/exist/blob/develop/plans/lucene10-semantic-vector-search-design.md">Lucene 10 semantic vector search design</a>
 */
public enum ReindexScope {

    /**
     * Reindex both fulltext and vector fields (default).
     */
    ALL,

    /**
     * Reindex, restoring computed vectors rather than computing them: when {@code vector-store="db"}, a node's
     * vector is read from vector.dbx, and computed only if it isn't there. When {@code vector-store="lucene"}, there
     * is nothing to restore from, and this behaves as {@link #ALL}.
     */
    FULLTEXT,

    /**
     * Reindex, computing vectors again from the source text even where vector.dbx has them, for example after the
     * model's files were replaced. The full-text fields are indexed too, as a node's Lucene document is written whole.
     */
    VECTOR;

    /**
     * Parse mode string from XQuery. Returns {@link #ALL} for null, empty, or invalid values.
     */
    public static ReindexScope fromString(final String mode) {
        if (mode == null || mode.isEmpty()) {
            return ALL;
        }
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "all" -> ALL;
            case "fulltext" -> FULLTEXT;
            case "vector" -> VECTOR;
            default -> ALL;
        };
    }
}
