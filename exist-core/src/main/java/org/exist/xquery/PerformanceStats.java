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

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;


public interface PerformanceStats {

    String CONFIG_PROPERTY_TRACE = "xquery.profiling.trace";
    String CONFIG_ATTR_TRACE = "trace";

    String XML_NAMESPACE = "http://exist-db.org/xquery/profiling";
    String XML_PREFIX = "stats";
    String XML_ELEMENT_CALLS = "calls";

    String RANGE_IDX_TYPE = "range";

    enum OptimizationType {
        POSITIONAL_PREDICATE
    }

    enum IndexOptimizationLevel {
        NONE,
        BASIC,
        OPTIMIZED;
    }

    /**
     * Enable of disable recording of Performance Stats.
     *
     * @param enabled true to enable performance stats, false to disable performance stats.
     */
    void setEnabled(final boolean enabled);

    /**
     * Returns true if performance stats are enabled.
     *
     * @return true if performance stats are enabled, false otherwise.
     */
    boolean isEnabled();

    /**
     * Record the time taken for execution of a Query.
     *
     * @param source the source of the Query.
     * @param elapsed the time taken by the Query.
     */
    void recordQuery(final String source, final long elapsed);

    /**
     * Record the time taken for execution of a Function Call.
     *
     * @param qname the name of the Function Call.
     * @param source the source of the Query.
     * @param elapsed the time taken by the Function Call.
     */
    void recordFunctionCall(final QName qname, final String source, final long elapsed);

    /**
     * Record the time taken for an Index Lookup.
     *
     * @param expression the expression that was completed by the Index.
     * @param indexName the name of the Index.
     * @param source the source of the Query.
     * @param indexOptimizationLevel the level of optimisation offered by the Index.
     * @param elapsed the time taken by the Index Call.
     */
    void recordIndexUse(final Expression expression, final String indexName, final String source, final IndexOptimizationLevel indexOptimizationLevel, final long elapsed);

    /**
     * Record that an Optimization was applied.
     *
     * @param expression the optimized expression.
     * @param type the type of Optimization that was applied.
     * @param source the source of the Query.
     */
    void recordOptimization(final Expression expression, final PerformanceStatsImpl.OptimizationType type, final String source);

    /**
     * Record the performance stats from the provided other performance stats.
     *
     * @param otherPerformanceStats another performance stats object.
     */
    void recordAll(final PerformanceStats otherPerformanceStats);

    /**
     * Serialized the performance stats as an XML fragment.
     *
     * @param builder the in-memory DOM builder to receive the serialized XML events.
     */
    void serialize(final MemTreeBuilder builder);

    /**
     * Reset all Performance Stats.
     */
    void reset();
}
