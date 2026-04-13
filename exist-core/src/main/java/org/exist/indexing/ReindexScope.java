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
     * Reindex fulltext only. Skip vector computation; when {@code vector-store="db"},
     * read vectors from vector.dbx. When {@code vector-store="lucene"}, fulltext-only
     * is not supported and behaves as {@link #ALL} (clears vectors).
     */
    FULLTEXT,

    /**
     * Reindex vector fields only. Recompute vectors from source text; skip fulltext.
     */
    VECTOR;

    /**
     * Parse mode string from XQuery. Returns {@link #ALL} for null, empty, or invalid values.
     */
    public static ReindexScope fromString(final String mode) {
        if (mode == null || mode.isEmpty()) {
            return ALL;
        }
        switch (mode.toLowerCase()) {
            case "all":
                return ALL;
            case "fulltext":
                return FULLTEXT;
            case "vector":
                return VECTOR;
            default:
                return ALL;
        }
    }
}
