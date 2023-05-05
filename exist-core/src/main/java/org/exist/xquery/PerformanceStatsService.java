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

import net.jcip.annotations.GuardedBy;
import net.jcip.annotations.ThreadSafe;
import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPoolService;
import org.exist.storage.BrokerPoolServiceException;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.ManagedLock;
import org.exist.util.Configuration;

import javax.annotation.Nullable;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static org.exist.storage.lock.ManagedLock.acquire;

/**
 * Implementation of a PerformanceStats that is designed
 * to be used my multiple-threads as a Service from the BrokerPool.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class PerformanceStatsService implements BrokerPoolService, PerformanceStats {

    private @Nullable ReadWriteLock performanceStatsLock = null;  // access is guarded by volatile `performanceStats` field below

    @GuardedBy("performanceStatsLock")
    private volatile @Nullable PerformanceStats performanceStats = null;  // volatile access as it is lazy-initialised (or not) in {@link BrokerPoolService#configure()} by the system thread

    @Override
    public void configure(final Configuration configuration) throws BrokerPoolServiceException {
        final String xqueryProfilingTraceEnabled = (String) configuration.getProperty(PerformanceStatsImpl.CONFIG_PROPERTY_TRACE);
        if ("yes".equals(xqueryProfilingTraceEnabled) || "functions".equals(xqueryProfilingTraceEnabled)) {
            init();
        }
    }

    private void init() {
        this.performanceStatsLock = new ReentrantReadWriteLock();
        this.performanceStats = new PerformanceStatsImpl(true);
    }

    @Override
    public boolean isEnabled() {
        if (performanceStats == null) {
            // not initialized or disabled
            return false;
        }

        try (final ManagedLock<ReadWriteLock> readLock = acquire(performanceStatsLock, Lock.LockMode.READ_LOCK)) {
            return performanceStats.isEnabled();
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (performanceStats == null) {
            // not initialized or disabled
            if (enabled == true) {
                init();
            }
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.setEnabled(enabled);
        }
    }

    @Override
    public void recordQuery(final String source, final long elapsed) {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.recordQuery(source, elapsed);
        }
    }

    @Override
    public void recordFunctionCall(final QName qname, final String source, final long elapsed) {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.recordFunctionCall(qname, source, elapsed);
        }
    }

    @Override
    public void recordIndexUse(final Expression expression, final String indexName, final String source, final IndexOptimizationLevel indexOptimizationLevel, final long elapsed) {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.recordIndexUse(expression, indexName, source, indexOptimizationLevel, elapsed);
        }
    }

    @Override
    public void recordOptimization(final Expression expression, final PerformanceStatsImpl.OptimizationType type, final String source) {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.recordOptimization(expression, type, source);
        }
    }

    @Override
    public void recordAll(final PerformanceStats otherPerformanceStats) {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.recordAll(otherPerformanceStats);
        }
    }

    @Override
    public void serialize(final MemTreeBuilder builder) {
        if (performanceStats == null) {
            // not initialized or disabled
            builder.startElement(new QName(XML_ELEMENT_CALLS, XML_NAMESPACE, XML_PREFIX), null);
            builder.endElement();
            return;
        }

        try (final ManagedLock<ReadWriteLock> readLock = acquire(performanceStatsLock, Lock.LockMode.READ_LOCK)) {
            performanceStats.serialize(builder);
        }
    }

    @Override
    public void reset() {
        if (performanceStats == null) {
            // not initialized or disabled
            return;
        }

        try (final ManagedLock<ReadWriteLock> writeLock = acquire(performanceStatsLock, Lock.LockMode.WRITE_LOCK)) {
            performanceStats.reset();
        }
    }

    @Override
    public String toString() {
        if (performanceStats == null) {
            // not initialized or disabled
            return "";
        }

        try (final ManagedLock<ReadWriteLock> readLock = acquire(performanceStatsLock, Lock.LockMode.READ_LOCK)) {
            return performanceStats.toString();
        }
    }
}
