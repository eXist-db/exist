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

import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.xml.sax.helpers.AttributesImpl;

import java.util.HashMap;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Arrays;
import java.io.StringWriter;
import java.io.PrintWriter;

public class PerformanceStats {

    public final static String XML_NAMESPACE = "http://exist-db.org/xquery/profiling";
    public final static String XML_PREFIX = "stats";

    public static String CONFIG_PROPERTY_TRACE = "xquery.profiling.trace";
    public static String CONFIG_ATTR_TRACE = "trace";

    private static class FunctionStats {

        private QName qname;
        private String source = null;
        private long executionTime = 0;
        private int callCount = 1;

        FunctionStats(QName name) {
            this.qname = name;
        }

        public void recordCall(long elapsed) {
            executionTime += elapsed;
            callCount++;
        }

        public int hashCode() {
            return qname.hashCode();
        }
    }

    private static class CompareByTime implements Comparator {

        public int compare(Object o1, Object o2) {
            long t1 = ((FunctionStats)o1).executionTime;
            long t2 = ((FunctionStats)o2).executionTime;
            return t1 == t2 ? 0 : (t1 > t2 ? 1 : -1);
        }
    }

    private HashMap<QName, FunctionStats> functions = new HashMap<QName, FunctionStats>();

    private boolean enabled = false;

    private BrokerPool pool;

    public PerformanceStats(BrokerPool pool) {
        this.pool = pool;
        if (pool != null) {
            String config = (String) pool.getConfiguration().getProperty(PerformanceStats.CONFIG_PROPERTY_TRACE);
            if (config != null)
                enabled = config.equals("functions") || config.equals("yes");
        }
    }

    public void setEnabled(boolean enable) {
        enabled = enable;
    }

    public boolean isEnabled() {
        return enabled ||
                (pool != null && pool.getPerformanceStats() != this &&
                        pool.getPerformanceStats().isEnabled());
    }

    public void recordFunctionCall(QName qname, String source, long elapsed) {
        FunctionStats stats = functions.get(qname);
        if (stats == null) {
            stats = new FunctionStats(qname);
            stats.executionTime = elapsed;
            stats.source = source;
            functions.put(qname, stats);
        } else {
            stats.recordCall(elapsed);
        }
    }

    public synchronized void merge(PerformanceStats otherStats) {
        for (Iterator<FunctionStats> i = otherStats.functions.values().iterator(); i.hasNext();) {
            FunctionStats other = i.next();
            FunctionStats mine = functions.get(other.qname);
            if (mine == null) {
                functions.put(other.qname, other);
            } else {
                mine.callCount += other.callCount;
                mine.executionTime += other.executionTime;
            }
        }
    }

    public boolean hasData() {
        return !functions.isEmpty();
    }
    
    public synchronized String toString() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        FunctionStats[] stats = sort();
        for (int i = 0; i < stats.length; i++) {
            pw.format("\n%30s %8.3f %8d", stats[i].qname, stats[i].executionTime / 1000.0, stats[i].callCount);
        }
        pw.flush();
        return sw.toString();
    }

    private FunctionStats[] sort() {
        FunctionStats stats[] = new FunctionStats[functions.size()];
        int j = 0;
        for (Iterator<FunctionStats> i = functions.values().iterator(); i.hasNext(); j++) {
            stats[j] = i.next();
        }
        Arrays.sort(stats, new CompareByTime());
        return stats;
    }

    public synchronized void toXML(MemTreeBuilder builder) {
        FunctionStats[] stats = sort();
        AttributesImpl attrs = new AttributesImpl();
        builder.startElement(new QName("calls", XML_NAMESPACE, XML_PREFIX), null);
        for (int i = 0; i < stats.length; i++) {
            attrs.clear();
            attrs.addAttribute("", "name", "name", "CDATA", stats[i].qname.toString());
            attrs.addAttribute("", "elapsed", "elapsed", "CDATA", Double.toString(stats[i].executionTime / 1000.0));
            attrs.addAttribute("", "calls", "calls", "CDATA", Integer.toString(stats[i].callCount));
            if (stats[i].source != null)
                attrs.addAttribute("", "source", "source", "CDATA", stats[i].source);
            builder.startElement(new QName("function", XML_NAMESPACE, XML_PREFIX), attrs);
            builder.endElement();
        }
        builder.endElement();
    }

    public synchronized void clear() {
        functions.clear();
    }

    public void reset() {
        functions.clear();
    }
}