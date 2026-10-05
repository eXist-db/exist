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
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The analyzer of the index writer: it picks the analyzer for each field by the field's name.
 * <p>
 * The analyzers come from the index configurations of the collections, and the index (so the writer) is shared
 * by all of them, while a field name does not tell collections apart: every collection that indexes {@code p}
 * elements writes into the field of {@code p}. Looking the analyzer up in one shared map is therefore not enough,
 * because the writer analyzes a document when {@code addDocument} gets to it, which can be later than when the
 * analyzer was put into the map: {@code addDocument} may have to wait for a flush that another thread started,
 * and in the meantime a thread that indexes into another collection puts its own analyzer for the same field.
 * The analyzers a thread needs for the document it is adding are therefore given to it for the duration of that
 * call, see {@link #withAnalyzers(Map, AddAction)}, and take precedence over the shared map.
 */
public class FieldAnalyzerWrapper extends DelegatingAnalyzerWrapper {

    private final Analyzer defaultAnalyzer;
    private final Map<String, Analyzer> sharedAnalyzers = new ConcurrentHashMap<>();
    private final ThreadLocal<Map<String, Analyzer>> threadAnalyzers = new ThreadLocal<>();

    /** what to do while the analyzers are in effect, which is adding a document */
    @FunctionalInterface
    public interface AddAction {
        void run() throws IOException;
    }

    public FieldAnalyzerWrapper(@Nonnull final Analyzer defaultAnalyzer) {
        super(PER_FIELD_REUSE_STRATEGY);
        this.defaultAnalyzer = defaultAnalyzer;
    }

    /**
     * Registers an analyzer for a field for all threads, which is what is used when a thread has not given its own.
     *
     * @param fieldName the name of the field
     * @param analyzer the analyzer
     */
    public void addAnalyzer(@Nonnull final String fieldName, @Nonnull final Analyzer analyzer) {
        sharedAnalyzers.put(fieldName, analyzer);
    }

    /**
     * Runs the action with the given analyzers in effect for the calling thread only.
     *
     * @param analyzers the analyzer for each field of the document the action adds
     * @param action the adding of the document
     *
     * @throws IOException if the action does
     */
    public void withAnalyzers(@Nonnull final Map<String, Analyzer> analyzers, @Nonnull final AddAction action) throws IOException {
        final Map<String, Analyzer> previous = threadAnalyzers.get();
        threadAnalyzers.set(analyzers);
        try {
            action.run();
        } finally {
            if (previous == null) {
                threadAnalyzers.remove();
            } else {
                threadAnalyzers.set(previous);
            }
        }
    }

    @Override
    protected Analyzer getWrappedAnalyzer(@Nullable final String fieldName) {
        if (fieldName == null) {
            return defaultAnalyzer;
        }
        final Map<String, Analyzer> own = threadAnalyzers.get();
        if (own != null) {
            final Analyzer analyzer = own.get(fieldName);
            if (analyzer != null) {
                return analyzer;
            }
        }
        return sharedAnalyzers.getOrDefault(fieldName, defaultAnalyzer);
    }
}
