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

import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.NodeSet;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.vector.VectorOperationMetrics;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.PerformanceStats;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * Shared profiler and metrics handling for vector KNN query functions.
 */
final class VectorSearchSupport {

    private VectorSearchSupport() {
    }

    @FunctionalInterface
    interface VectorSearch {
        Sequence search() throws IOException, XPathException;
    }

    static Sequence execute(final BasicFunction fn, final XQueryContext context,
            @Nullable final LuceneIndexWorker index,
            final PerformanceStats.IndexOptimizationLevel optimizationLevel,
            final VectorSearch search) throws XPathException {
        if (optimizationLevel == PerformanceStats.IndexOptimizationLevel.NONE) {
            final long start = System.nanoTime();
            if (context.getProfiler().traceFunctions()) {
                context.getProfiler().traceIndexUsage(context, "lucene-vector", fn, optimizationLevel,
                        (System.nanoTime() - start) / 1_000_000L);
            }
            return Sequence.EMPTY_SEQUENCE;
        }

        final long start = System.nanoTime();
        try {
            final Sequence result = search.search();
            final long durationNanos = System.nanoTime() - start;
            VectorOperationMetrics.recordKnn(durationNanos);
            if (context.getProfiler().traceFunctions()) {
                context.getProfiler().traceIndexUsage(context, "lucene-vector", fn,
                        optimizationLevel, durationNanos / 1_000_000L);
            }
            return result;
        } catch (IOException e) {
            throw new XPathException(fn, "Vector search failed: " + e.getMessage(), e);
        }
    }

    static PerformanceStats.IndexOptimizationLevel optimizationLevelForField(@Nullable final LuceneIndexWorker index,
            final DocumentSet docs, final String field) {
        return index != null && index.hasVectorIndexForField(docs, field)
                ? PerformanceStats.IndexOptimizationLevel.OPTIMIZED
                : PerformanceStats.IndexOptimizationLevel.NONE;
    }

    static PerformanceStats.IndexOptimizationLevel optimizationLevelForQNames(final BasicFunction fn,
            @Nullable final LuceneIndexWorker index, final DocumentSet docs,
            @Nullable final java.util.List<org.exist.dom.QName> qnames) throws XPathException {
        if (index == null) {
            return PerformanceStats.IndexOptimizationLevel.NONE;
        }
        try {
            return index.hasVectorIndexForQNames(docs, qnames)
                    ? PerformanceStats.IndexOptimizationLevel.OPTIMIZED
                    : PerformanceStats.IndexOptimizationLevel.NONE;
        } catch (IOException e) {
            throw new XPathException(fn, "Failed to check vector index config: " + e.getMessage(), e);
        }
    }
}
