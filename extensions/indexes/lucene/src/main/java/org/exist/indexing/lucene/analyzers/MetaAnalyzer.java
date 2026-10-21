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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Delegates to different analyzers configured by field.
 *
 * @author Wolfgang Meier
 */
public class MetaAnalyzer extends DelegatingAnalyzerWrapper {

    private final Analyzer defaultAnalyzer;
    private final Map<String, Analyzer> perFieldAnalyzers;
    private volatile @Nullable Analyzer queryAnalyzer;

    public MetaAnalyzer(@Nonnull Analyzer defaultAnalyzer) {
        super(PER_FIELD_REUSE_STRATEGY);

        this.defaultAnalyzer = defaultAnalyzer;
        perFieldAnalyzers = new HashMap<>();
    }

    public void addAnalyzer(@Nonnull String fieldName, @Nonnull Analyzer analyzer) {
        perFieldAnalyzers.put(fieldName, analyzer);
        queryAnalyzer = null;
    }

    @Override
    protected Analyzer getWrappedAnalyzer(@Nullable String fieldName) {
        return analyzerFor(fieldName);
    }

    /**
     * The analyzer of a field.
     *
     * @param fieldName the field, or null for the default
     * @return the field's analyzer, or the default analyzer if it has none of its own
     */
    public Analyzer analyzerFor(@Nullable final String fieldName) {
        if (fieldName == null) {
            return defaultAnalyzer;
        }
        return perFieldAnalyzers.getOrDefault(fieldName, defaultAnalyzer);
    }

    /**
     * The analyzer a query uses: the same fields, each with its query chain where it has one of its own.
     *
     * @return this analyzer if no field has a query chain, or else one with the query chains
     */
    public Analyzer forQuery() {
        Analyzer query = queryAnalyzer;
        if (query == null) {
            query = buildQueryAnalyzer();
            queryAnalyzer = query;
        }
        return query;
    }

    private Analyzer buildQueryAnalyzer() {
        final Analyzer queryDefault = IndexQueryAnalyzer.forQuery(defaultAnalyzer);
        boolean changed = queryDefault != defaultAnalyzer;
        final MetaAnalyzer query = new MetaAnalyzer(queryDefault);
        for (final Map.Entry<String, Analyzer> entry : perFieldAnalyzers.entrySet()) {
            final Analyzer fieldQuery = IndexQueryAnalyzer.forQuery(entry.getValue());
            changed |= fieldQuery != entry.getValue();
            query.perFieldAnalyzers.put(entry.getKey(), fieldQuery);
        }
        if (!changed) {
            return this;
        }
        query.queryAnalyzer = query;
        return query;
    }

    public String toString() {
        return "MetaAnalyzer(" + this.perFieldAnalyzers + ", default=" + this.defaultAnalyzer + ")";
    }
}
