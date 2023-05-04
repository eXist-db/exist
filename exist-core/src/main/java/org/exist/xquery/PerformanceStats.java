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

import org.exist.Database;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPoolService;
import org.xml.sax.helpers.AttributesImpl;

import javax.annotation.Nullable;
import java.io.StringWriter;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PerformanceStats implements BrokerPoolService {

    public static final String RANGE_IDX_TYPE = "range";

    public static final String XML_NAMESPACE = "http://exist-db.org/xquery/profiling";
    public static final String XML_PREFIX = "stats";

    public static final String CONFIG_PROPERTY_TRACE = "xquery.profiling.trace";
    public static final String CONFIG_ATTR_TRACE = "trace";

    public static final int NO_INDEX = 0;
    public static final int BASIC_INDEX = 1;
    public static final int OPTIMIZED_INDEX = 2;

    private static class IndexStats {

        final String source;
        final String indexType;
        final int line;
        final int column;
        final int mode;
        int usageCount = 0;
        long executionTime = 0;

        private IndexStats(final String indexType, final String source, final int line, final int column, final int mode) {
            this.indexType = indexType;
            this.source = source;
            this.line = line;
            this.column = column;
            this.mode = mode;
        }

        public void recordUsage(final long elapsed) {
            executionTime += elapsed;
            usageCount++;
        }

        @Override
        public int hashCode() {
            return indexType.hashCode() + source.hashCode() + line + column + mode;
        }

        @Override
        public boolean equals(final Object obj) {
            if (obj instanceof IndexStats other) {
                return other.indexType.equals(indexType) && other.source.equals(source) &&
                        other.line == line && other.column == column && other.mode == mode;
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

    private static class FunctionStats extends QueryStats {
        final QName qname;

        FunctionStats(final String source, final QName name) {
            super(source);
            this.qname = name;
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

    public enum OptimizationType {
        PositionalPredicate
    }

    private static class CompareByTime implements Comparator<FunctionStats> {

        @Override
        public int compare(final FunctionStats o1, final FunctionStats o2) {
            return Long.compare(o1.executionTime, o2.executionTime);
        }
    }

    private final Map<String, QueryStats> queries = new HashMap<>();
    private final Map<FunctionStats, FunctionStats> functions = new HashMap<>();
    private final Map<IndexStats, IndexStats> indexStats = new HashMap<>();
    private final Set<OptimizationStats> optimizations = new HashSet<>();

    private boolean enabled = false;

    private final Database db;

    public PerformanceStats(final Database db) {
        this.db = db;
        if (db != null) {
            final String config = (String) db.getConfiguration().getProperty(PerformanceStats.CONFIG_PROPERTY_TRACE);
            if (config != null) {
                enabled = config.equals("functions") || "yes".equals(config);
            }
        }
    }

    public void setEnabled(final boolean enable) {
        enabled = enable;
    }

    public boolean isEnabled() {
        return enabled ||
                (db != null
                        && db.getPerformanceStats() != this
                        && db.getPerformanceStats().isEnabled());
    }

    public void recordQuery(@Nullable final String source, final long elapsed) {
        if (source == null) {
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

    public void recordFunctionCall(final QName qname, final String source, final long elapsed) {
        final FunctionStats newStats = new FunctionStats(source, qname);
        final FunctionStats stats = functions.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            functions.put(newStats, newStats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    public void recordIndexUse(final Expression expression, final String indexName, final String source, final int mode, final long elapsed) {
        final IndexStats newStats = new IndexStats(indexName, source, expression.getLine(), expression.getColumn(), mode);
        final IndexStats stats = indexStats.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            indexStats.put(newStats, newStats);
        } else {
            stats.recordUsage(elapsed);
        }
    }

    public void recordOptimization(final Expression expression, final OptimizationType type, final String source) {
        final OptimizationStats newStats = new OptimizationStats(source, type, expression.getLine(), expression.getColumn());
        optimizations.add(newStats);
    }

    public synchronized void merge(final PerformanceStats otherStats) {
        for (final QueryStats other : otherStats.queries.values()) {
            final QueryStats mine = queries.get(other.source);
            if (mine == null) {
                queries.put(other.source, other);
            } else {
                mine.callCount += other.callCount;
                mine.executionTime += other.executionTime;
            }
        }
        for (final FunctionStats other : otherStats.functions.values()) {
            final FunctionStats mine = functions.get(other);
            if (mine == null) {
                functions.put(other, other);
            } else {
                mine.callCount += other.callCount;
                mine.executionTime += other.executionTime;
            }
        }
        for (final IndexStats other : otherStats.indexStats.values()) {
            final IndexStats mine = indexStats.get(other);
            if (mine == null) {
                indexStats.put(other, other);
            } else {
                mine.usageCount += other.usageCount;
                mine.executionTime += other.executionTime;
            }
        }
        optimizations.addAll(otherStats.optimizations);
    }

    @SuppressWarnings("unused")
    private String createKey(final QName qname, final String source) {
        return qname.getNamespaceURI() + ":" + qname.getLocalPart() + ":" + source;
    }

    public boolean hasData() {
        return !(functions.isEmpty() && queries.isEmpty());
    }

    @Override
    public synchronized String toString() {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        final FunctionStats[] stats = sort();
        for (final FunctionStats stat : stats) {
            pw.format("\n%30s %8.3f %8d", stat.qname, stat.executionTime / 1000.0, stat.callCount);
        }
        pw.flush();
        return sw.toString();
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

    public synchronized void toXML(final MemTreeBuilder builder) {
        final AttributesImpl attrs = new AttributesImpl();
        builder.startElement(new QName("calls", XML_NAMESPACE, XML_PREFIX), null);
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
            attrs.addAttribute("", "optimization", "optimization", "CDATA",
                    Integer.toString(stats.mode));
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
        builder.endElement();
    }

    public synchronized void clear() {
        queries.clear();
        functions.clear();
        indexStats.clear();
        optimizations.clear();
    }

    public void reset() {
        queries.clear();
        functions.clear();
        indexStats.clear();
        optimizations.clear();
    }
}
