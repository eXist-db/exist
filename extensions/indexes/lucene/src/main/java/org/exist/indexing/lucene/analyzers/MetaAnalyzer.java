/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
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

    public String toString() {
        return "MetaAnalyzer(" + this.perFieldAnalyzers + ", default=" + this.defaultAnalyzer + ")";
    }
}
