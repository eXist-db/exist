/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

package org.exist.xquery;

import org.exist.Database;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPoolService;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Arrays;
import java.io.StringWriter;
import java.io.PrintWriter;

public class PerformanceStats implements BrokerPoolService {

    public final static String RANGE_IDX_TYPE = "range";

    public final static String XML_NAMESPACE = "http://exist-db.org/xquery/profiling";
    public final static String XML_PREFIX = "stats";

    public static final String CONFIG_PROPERTY_TRACE = "xquery.profiling.trace";
    public static final String CONFIG_ATTR_TRACE = "trace";

    public final static int NO_INDEX = 0;
    public final static int BASIC_INDEX = 1;
    public final static int OPTIMIZED_INDEX = 2;

    private static class IndexStats {

        String source;
        String indexType;
        int line;
        int column;
        int mode = 0;
        int usageCount = 1;
        long executionTime = 0;

        private IndexStats(String indexType, String source, int line, int column, int mode) {
            this.indexType = indexType;
            this.source = source;
            this.line = line;
            this.column = column;
            this.mode = mode;
        }

        public void recordUsage(long elapsed) {
            executionTime += elapsed;
            usageCount++;
        }

        public int hashCode() {
            return indexType.hashCode() + source.hashCode() + line + column + mode;
        }

        public boolean equals(Object obj) {
        	if (obj != null && obj instanceof IndexStats) {
                final IndexStats other = (IndexStats) obj;
                return other.indexType.equals(indexType) && other.source.equals(source) &&
                    other.line == line && other.column == column && other.mode == mode;
			}
        	return false;
        }
    }

    private static class QueryStats {

        String source;
        long executionTime = 0;
        int callCount = 1;

        QueryStats(String source) {
            this.source = source;
            if (this.source == null)
                {this.source = "";}
        }

        public void recordCall(long elapsed) {
            executionTime += elapsed;
            callCount++;
        }

        public int hashCode() {
            return source.hashCode();
        }

        public boolean equals(Object obj) {
        	if (obj != null && obj instanceof QueryStats) {
                return ((QueryStats)obj).source.equals(source);
			}
        	return false;
        }
    }

    private static class FunctionStats extends QueryStats {

        QName qname;

        FunctionStats(String source, QName name) {
            super(source);
            this.qname = name;
        }

        public int hashCode() {
            return 31 * qname.hashCode() + source.hashCode();
        }

        public boolean equals(Object obj) {
        	if (obj != null && obj instanceof FunctionStats) {
                final FunctionStats ostats = (FunctionStats) obj;
                return qname.equals(ostats.qname) &&
                        source.equals(ostats.source);
			}
        	return false;
        }
    }

    private static class CompareByTime implements Comparator<FunctionStats> {

        public int compare(FunctionStats o1, FunctionStats o2) {
            final long t1 = o1.executionTime;
            final long t2 = o2.executionTime;
            return t1 == t2 ? 0 : (t1 > t2 ? 1 : -1);
        }
    }

    private HashMap<String, QueryStats> queries = new HashMap<String, QueryStats>();
    private HashMap<FunctionStats, FunctionStats> functions = new HashMap<FunctionStats, FunctionStats>();
    private HashMap<IndexStats, IndexStats> indexStats = new HashMap<IndexStats, IndexStats>();
    
    private boolean enabled = false;

    private Database db;

    public PerformanceStats(Database db) {
        this.db = db;
        if (db != null) {
            final String config = (String) db.getConfiguration().getProperty(PerformanceStats.CONFIG_PROPERTY_TRACE);
            if (config != null)
                enabled = config.equals("functions") || "yes".equals(config);
        }
    }

    public void setEnabled(boolean enable) {
        enabled = enable;
    }

    public boolean isEnabled() {
        return enabled ||
                (db != null 
            		&& db.getPerformanceStats() != this 
            		&& db.getPerformanceStats().isEnabled());
    }

    public void recordQuery(String source, long elapsed) {
        if (source == null)
            {return;}
        QueryStats stats = queries.get(source);
        if (stats == null) {
            stats = new QueryStats(source);
            stats.executionTime = elapsed;
            queries.put(source, stats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    public void recordFunctionCall(QName qname, String source, long elapsed) {
        final FunctionStats newStats = new FunctionStats(source, qname);
        final FunctionStats stats = functions.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            functions.put(newStats, newStats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    public void recordIndexUse(Expression expression, String indexName, String source, int mode, long elapsed) {
        final IndexStats newStats = new IndexStats(indexName, source, expression.getLine(), expression.getColumn(), mode);
        final IndexStats stats = indexStats.get(newStats);
        if (stats == null) {
            newStats.executionTime = elapsed;
            indexStats.put(newStats, newStats);
        } else {
            stats.recordUsage(elapsed);
        }
    }
    
    public synchronized void merge(PerformanceStats otherStats) {
        for (final QueryStats other: otherStats.queries.values()) {
            final QueryStats mine = queries.get(other.source);
            if (mine == null) {
                queries.put(other.source, other);
            } else {
                mine.callCount += other.callCount;
                mine.executionTime += other.executionTime;
            }
        }
        for (final FunctionStats other: otherStats.functions.values()) {
            final FunctionStats mine = functions.get(other);
            if (mine == null) {
                functions.put(other, other);
            } else {
                mine.callCount += other.callCount;
                mine.executionTime += other.executionTime;
            }
        }
        for (final IndexStats other: otherStats.indexStats.values()) {
           final IndexStats mine = indexStats.get(other);
           if (mine == null) {
               indexStats.put(other, other);
           } else {
               mine.usageCount += other.usageCount;
               mine.executionTime += other.executionTime;
           }
       }
    }

    @SuppressWarnings("unused")
	private String createKey(QName qname, String source) {
        return qname.getNamespaceURI() + ":" + qname.getLocalPart() + ":" + source;
    }

    public boolean hasData() {
        return !(functions.isEmpty() && queries.isEmpty());
    }
    
    public synchronized String toString() {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw);
        final FunctionStats[] stats = sort();
        for (int i = 0; i < stats.length; i++) {
            pw.format("\n%30s %8.3f %8d", stats[i].qname, stats[i].executionTime / 1000.0, stats[i].callCount);
        }
        pw.flush();
        return sw.toString();
    }

    private FunctionStats[] sort() {
        final FunctionStats stats[] = new FunctionStats[functions.size()];
        int j = 0;
        for (FunctionStats next: functions.values() ) {
            stats[j] = next;
            j++;
        }
        Arrays.sort(stats, new CompareByTime());
        return stats;
    }

    public synchronized void toXML(MemTreeBuilder builder) {
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
        for (final FunctionStats stats: functions.values()) {
            attrs.clear();
            attrs.addAttribute("", "name", "name", "CDATA", stats.qname.getStringValue());
            attrs.addAttribute("", "elapsed", "elapsed", "CDATA", Double.toString(stats.executionTime / 1000.0));
            attrs.addAttribute("", "calls", "calls", "CDATA", Integer.toString(stats.callCount));
            if (stats.source != null)
                {attrs.addAttribute("", "source", "source", "CDATA", stats.source);}
            builder.startElement(new QName("function", XML_NAMESPACE, XML_PREFIX), attrs);
            builder.endElement();
        }
        for (final IndexStats stats: indexStats.values()) {
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
        builder.endElement();
    }

    public synchronized void clear() {
        queries.clear();
        functions.clear();
        indexStats.clear();
    }

    public void reset() {
        queries.clear();
        functions.clear();
        indexStats.clear();
    }
}