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
package org.exist.indexing.lucene.analyzers;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.DelegatingAnalyzerWrapper;

import javax.annotation.Nullable;

/**
 * An analyzer with one chain for indexing and another for queries, such as a word delimiter filter that needs
 * {@code flattenGraph} when indexing but not in a query. It analyzes as its index chain; a query uses
 * {@link #forQuery(Analyzer)}.
 */
public class IndexQueryAnalyzer extends DelegatingAnalyzerWrapper {

    private final Analyzer index;
    private final Analyzer query;

    /**
     * @param index the analyzer to index with
     * @param query the analyzer to query with
     */
    public IndexQueryAnalyzer(final Analyzer index, final Analyzer query) {
        super(GLOBAL_REUSE_STRATEGY);
        this.index = index;
        this.query = query;
    }

    @Override
    protected Analyzer getWrappedAnalyzer(@Nullable final String fieldName) {
        return index;
    }

    /**
     * @return the analyzer to index with
     */
    public Analyzer getIndexAnalyzer() {
        return index;
    }

    /**
     * @return the analyzer to query with
     */
    public Analyzer getQueryAnalyzer() {
        return query;
    }

    /**
     * The analyzer a query uses in place of the given one: its query chain, for an analyzer that has one, or for each
     * field of a {@link MetaAnalyzer} that has one.
     *
     * @param analyzer the analyzer the index uses
     * @return the analyzer to query with
     */
    public static @Nullable Analyzer forQuery(@Nullable final Analyzer analyzer) {
        if (analyzer instanceof IndexQueryAnalyzer indexQuery) {
            return indexQuery.query;
        }
        if (analyzer instanceof MetaAnalyzer meta) {
            return meta.forQuery();
        }
        return analyzer;
    }

    @Override
    public String toString() {
        return "IndexQueryAnalyzer(index=" + index + ", query=" + query + ")";
    }
}
