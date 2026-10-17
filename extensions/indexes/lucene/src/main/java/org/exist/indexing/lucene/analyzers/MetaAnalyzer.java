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

    public MetaAnalyzer(@Nonnull Analyzer defaultAnalyzer) {
        super(PER_FIELD_REUSE_STRATEGY);

        this.defaultAnalyzer = defaultAnalyzer;
        perFieldAnalyzers = new HashMap<>();
    }

    public void addAnalyzer(@Nonnull String fieldName, @Nonnull Analyzer analyzer) {
        perFieldAnalyzers.put(fieldName, analyzer);
    }

    @Override
    protected Analyzer getWrappedAnalyzer(@Nullable String fieldName) {
        if (fieldName == null) {
            return defaultAnalyzer;
        }
        return perFieldAnalyzers.getOrDefault(fieldName, defaultAnalyzer);
    }

    /**
     * The concrete analyzer this wrapper delegates to for the given field (or the default analyzer
     * when the field has no specific one, or {@code fieldName} is null). Public so configuration
     * introspection (e.g. ft:fields) can report the resolved analyzer class behind the wrapper.
     *
     * @param fieldName the field name, or null for the default analyzer
     * @return the concrete analyzer used for that field
     */
    public Analyzer getConfiguredAnalyzer(@Nullable final String fieldName) {
        return getWrappedAnalyzer(fieldName);
    }

    public String toString() {
        return "MetaAnalyzer(" + this.perFieldAnalyzers + ", default=" + this.defaultAnalyzer + ")";
    }
}
