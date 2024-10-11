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

package org.exist.xquery;

import net.jcip.annotations.NotThreadSafe;
import net.jcip.annotations.ThreadSafe;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.xml.sax.helpers.AttributesImpl;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of a PerformanceStats that is designed
 * to be used from a single XQuery via its {@link XQueryContext}
 * and therefore does not need to be thread-safe.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@NotThreadSafe
public class PerformanceStatsImpl implements PerformanceStats {

    private static class IndexStats {

        final String source;
        final String indexType;
        final int line;
        final int column;
        final IndexOptimizationLevel indexOptimizationLevel;
        int usageCount = 1;
        long executionTime = 0;

        private IndexStats(final String indexType, final String source, final int line, final int column, final IndexOptimizationLevel indexOptimizationLevel) {
            this.indexType = indexType;
            this.source = source;
            this.line = line;
            this.column = column;
            this.indexOptimizationLevel = indexOptimizationLevel;
        }

        public static IndexStats copy(final IndexStats other) {
           final IndexStats copy = new IndexStats(other.indexType, other.source, other.line, other.column, other.indexOptimizationLevel);
           copy.usageCount = other.usageCount;
           copy.executionTime = other.executionTime;
           return copy;
        }

        public void recordUsage(final long elapsed) {
            executionTime += elapsed;
            usageCount++;
        }

        @Override
        public int hashCode() {
            return indexType.hashCode() + source.hashCode() + line + column + indexOptimizationLevel.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IndexStats other) {
                return other.indexType.equals(indexType) && other.source.equals(source) &&
                        other.line == line && other.column == column && other.indexOptimizationLevel == indexOptimizationLevel;
            }
            return false;
        }
    }

    private static class QueryStats {

        final String source;
        long executionTime = 0;
        int callCount = 1;

        QueryStats(final String source) {
            this.source = (source != null ? source : "");
        }

        public static QueryStats copy(final QueryStats other) {
            final QueryStats copy = new QueryStats(other.source);
            copy.executionTime = other.executionTime;
            copy.callCount = other.callCount;
            return copy;
        }

        public void recordCall(final long elapsed) {
            executionTime += elapsed;
            callCount++;
        }

        @Override
        public int hashCode() {
            return source.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof QueryStats other) {
                return other.source.equals(source);
            }
            return false;
        }
    }

    @ThreadSafe
    private static class FunctionStats extends QueryStats {
        final QName qname;

        FunctionStats(final String source, final QName name) {
            super(source);
            this.qname = name;
        }

        public static FunctionStats copy(final FunctionStats other) {
            final FunctionStats copy = new FunctionStats(other.source, other.qname);
            copy.executionTime = other.executionTime;
            copy.callCount = other.callCount;
            return copy;
        }

        @Override
        public int hashCode() {
            return 31 * qname.hashCode() + source.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof FunctionStats other) {
                return qname.equals(other.qname) &&
                        source.equals(other.source);
            }
            return false;
        }
    }

    @ThreadSafe
    private static class OptimizationStats {
        final String source;
        final OptimizationType type;
        final int line;
        final int column;

        OptimizationStats(final String source, final OptimizationType type, final int line, final int column) {
            this.source = source != null ? source : "";
            this.type = type;
            this.line = line;
            this.column = column;
        }

        @Override
        public int hashCode() {
            return 32 * type.hashCode() + source.hashCode() + line + column;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof OptimizationStats other) {
                return source.equals(other.source) && type == other.type &&
                        line == other.line && column == other.column;
            }
            return false;
        }
    }

    private static class CompareByTime implements Comparator<FunctionStats> {

        @Override
        public int compare(final FunctionStats o1, final FunctionStats o2) {
            return Long.compare(o1.executionTime, o2.executionTime);
        }
    }

    @FunctionalInterface
    public interface Enabler {
        boolean enabled(final boolean enabled);
    }

    private final Map<String, QueryStats> queries = new HashMap<>();
    private final Map<FunctionStats, FunctionStats> functions = new HashMap<>();
    private final Map<IndexStats, IndexStats> indexStats = new HashMap<>();
    private final Set<OptimizationStats> optimizations = new HashSet<>();

    private final Enabler enabler;

    private boolean enabled = false;

    public PerformanceStatsImpl(final boolean enabled) {
        this(enabled, x -> x);
    }

    public PerformanceStatsImpl(final boolean enabled, final Enabler enabler) {
        this.enabled = enabled;
        this.enabler = enabler;
    }

    @Override
    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabler.enabled(enabled);
    }

    @Override
    public void recordQuery(final String source, final long elapsed) {
        if (!isEnabled()) {
            return;
        }

        QueryStats stats = queries.get(source);
        if (stats == null) {
            stats = new QueryStats(source);
            stats.executionTime = elapsed;
            queries.put(source, stats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    @Override
    public void recordFunctionCall(final QName qname, final String source, final long elapsed) {
        if (!isEnabled()) {
            return;
        }

        final FunctionStats newStats = new FunctionStats(source, qname);
        final FunctionStats stats = functions.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            functions.put(newStats, newStats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    @Override
    public void recordIndexUse(final Expression expression, final String indexName, final String source, final IndexOptimizationLevel indexOptimizationLevel, final long elapsed) {
        if (!isEnabled()) {
            return;
        }

        final IndexStats newStats = new IndexStats(indexName, source, expression.getLine(), expression.getColumn(), indexOptimizationLevel);
        final IndexStats stats = indexStats.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            indexStats.put(newStats, newStats);
        } else {
            stats.recordUsage(elapsed);
        }
    }

    @Override
    public void recordOptimization(final Expression expression, final OptimizationType type, final String source) {
        if (!isEnabled()) {
            return;
        }

        final OptimizationStats newStats = new OptimizationStats(source, type, expression.getLine(), expression.getColumn());
        optimizations.add(newStats);
    }

    @Override
    public void recordAll(final PerformanceStats otherPerformanceStats) {
        if (!isEnabled()) {
            return;
        }

        if (!(otherPerformanceStats instanceof PerformanceStatsImpl)) {
            throw new IllegalArgumentException("Argument must be of type: " + getClass().getName());
        }

        final PerformanceStatsImpl other = (PerformanceStatsImpl) otherPerformanceStats;

        for (final QueryStats otherQueryStats : other.queries.values()) {
            final QueryStats copy = QueryStats.copy(otherQueryStats);
            @Nullable final QueryStats mine = queries.get(copy.source);
            if (mine == null) {
                queries.put(copy.source, copy);
            } else {
                mine.callCount += copy.callCount;
                mine.executionTime += copy.executionTime;
            }
        }

        for (final FunctionStats otherFunctionStats : other.functions.values()) {
            final FunctionStats copy = FunctionStats.copy(otherFunctionStats);
            final FunctionStats mine = functions.get(copy);
            if (mine == null) {
                functions.put(copy, copy);
            } else {
                mine.callCount += copy.callCount;
                mine.executionTime += copy.executionTime;
            }
        }

        for (final IndexStats otherIndexStats : other.indexStats.values()) {
            final IndexStats copy = IndexStats.copy(otherIndexStats);
            final IndexStats mine = indexStats.get(copy);
            if (mine == null) {
                indexStats.put(copy, copy);
            } else {
                mine.usageCount += copy.usageCount;
                mine.executionTime += copy.executionTime;
            }
        }
        optimizations.addAll(other.optimizations);
    }

    @Override
    public String toString() {
        try (final StringWriter sw = new StringWriter();
            final PrintWriter pw = new PrintWriter(sw)) {
            final FunctionStats[] stats = sort();
            for (final FunctionStats stat : stats) {
                pw.format("\n%30s %8.3f %8d", stat.qname, stat.executionTime / 1000.0, stat.callCount);
            }
            pw.flush();
            return sw.toString();
        } catch (final IOException e) {
            // no-op
            return "";
        }
    }

    private FunctionStats[] sort() {
        final FunctionStats[] stats = new FunctionStats[functions.size()];
        int j = 0;
        for (final FunctionStats next : functions.values()) {
            stats[j] = next;
            j++;
        }
        Arrays.sort(stats, new CompareByTime());
        return stats;
    }

    @Override
    public void serialize(final MemTreeBuilder builder) {
        builder.startElement(new QName(XML_ELEMENT_CALLS, XML_NAMESPACE, XML_PREFIX), null);
        if (isEnabled()) {
            final AttributesImpl attrs = new AttributesImpl();
            for (final QueryStats stats : queries.values()) {
                attrs.clear();
                attrs.addAttribute("", "source", "source", "CDATA", stats.source);
                attrs.addAttribute("", "elapsed", "elapsed", "CDATA", Double.toString(stats.executionTime / 1000.0));
                attrs.addAttribute("", "calls", "calls", "CDATA", Integer.toString(stats.callCount));
                builder.startElement(new QName("query", XML_NAMESPACE, XML_PREFIX), attrs);
                builder.endElement();
            }
            for (final FunctionStats stats : functions.values()) {
                attrs.clear();
                attrs.addAttribute("", "name", "name", "CDATA", stats.qname.getStringValue());
                attrs.addAttribute("", "elapsed", "elapsed", "CDATA", Double.toString(stats.executionTime / 1000.0));
                attrs.addAttribute("", "calls", "calls", "CDATA", Integer.toString(stats.callCount));
                if (stats.source != null) {
                    attrs.addAttribute("", "source", "source", "CDATA", stats.source);
                }
                builder.startElement(new QName("function", XML_NAMESPACE, XML_PREFIX), attrs);
                builder.endElement();
            }
            for (final IndexStats stats : indexStats.values()) {
                attrs.clear();
                attrs.addAttribute("", "type", "type", "CDATA", stats.indexType);
                attrs.addAttribute("", "source", "source", "CDATA", stats.source + " [" + stats.line + ":" +
                        stats.column + "]");
                attrs.addAttribute("", "elapsed", "elapsed", "CDATA", Double.toString(stats.executionTime / 1000.0));
                attrs.addAttribute("", "calls", "calls", "CDATA", Integer.toString(stats.usageCount));
                attrs.addAttribute("", "optimization-level", "optimization", "CDATA", stats.indexOptimizationLevel.name());
                builder.startElement(new QName("index", XML_NAMESPACE, XML_PREFIX), attrs);
                builder.endElement();
            }
            for (final OptimizationStats stats : optimizations) {
                attrs.clear();
                attrs.addAttribute("", "type", "type", "CDATA", stats.type.toString());
                if (stats.source != null) {
                    attrs.addAttribute("", "source", "source", "CDATA", stats.source + " [" + stats.line + ":" + stats.column + "]");
                }
                builder.startElement(new QName("optimization", XML_NAMESPACE, XML_PREFIX), attrs);
                builder.endElement();
            }
        }
        builder.endElement();
    }

    @Override
    public void reset() {
        queries.clear();
        functions.clear();
        indexStats.clear();
        optimizations.clear();
    }
}