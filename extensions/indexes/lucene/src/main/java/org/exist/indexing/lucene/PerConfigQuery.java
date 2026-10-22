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
package org.exist.indexing.lucene;

import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;

import java.util.Map;

/**
 * The query of documents of collections configured differently: one query per configuration, each restricted to its
 * own documents, combined. It is searched as the combination, and each hit's match keeps the query of its own
 * configuration, so that highlighting a hit only uses the terms analyzed for that hit's configuration.
 */
final class PerConfigQuery extends Query {

    private final Query combined;
    private final Map<Integer, Query> byDocument;

    /**
     * @param combined the combination searched
     * @param byDocument the query of each document's configuration, by document id
     */
    PerConfigQuery(final Query combined, final Map<Integer, Query> byDocument) {
        this.combined = combined;
        this.byDocument = byDocument;
    }

    /**
     * @param docId an eXist document id
     * @return the query of that document's configuration, or the combination for a document of none
     */
    Query forDocument(final int docId) {
        return byDocument.getOrDefault(docId, combined);
    }

    @Override
    public Query rewrite(final IndexSearcher searcher) {
        return combined;
    }

    @Override
    public void visit(final QueryVisitor visitor) {
        combined.visit(visitor);
    }

    @Override
    public String toString(final String field) {
        return combined.toString(field);
    }

    @Override
    public boolean equals(final Object other) {
        return sameClassAs(other) && combined.equals(((PerConfigQuery) other).combined);
    }

    @Override
    public int hashCode() {
        return classHash() ^ combined.hashCode();
    }
}
