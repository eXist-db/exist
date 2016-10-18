/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2016 The eXist Project
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
 */
package org.exist.storage;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.xquery.CompiledXQuery;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Global pool for pre-compiled XQuery expressions. Expressions are stored and
 * retrieved from the pool by comparing the {@link org.exist.source.Source}
 * objects from which they were created. For each XQuery, a maximum of
 * {@link #MAX_STACK_SIZE} compiled expressions are kept in the pool. An XQuery
 * expression will be removed from the pool if it has not been used for a
 * pre-defined timeout. These settings can be configured in conf.xml.
 *
 * @author wolf
 */
@ConfigurationClass("query-pool")
@ThreadSafe
public class XQueryPool implements BrokerPoolService {

    private final static int MAX_POOL_SIZE = 128;
    private final static int MAX_STACK_SIZE = 5;
    private final static long TIMEOUT = 120000L;
    private final static long TIMEOUT_CHECK_INTERVAL = 30000L;

    private final static Logger LOG = LogManager.getLogger(XQueryPool.class);

    private ConcurrentMap<Source, NavigableSet<Entry>> pool = new ConcurrentHashMap<>();

    private AtomicLong lastTimeOutCheck;

    @ConfigurationFieldAsAttribute("size")
    private int maxPoolSize;

    @ConfigurationFieldAsAttribute("max-stack-size")
    private int maxStackSize;

    @ConfigurationFieldAsAttribute("timeout")
    private long timeout;

    @ConfigurationFieldAsAttribute("timeout-check-interval")
    private long timeoutCheckInterval;

    public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
    public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
    public static final String POOL_SIZE_ATTTRIBUTE = "size";
    public static final String TIMEOUT_ATTRIBUTE = "timeout";
    public static final String TIMEOUT_CHECK_INTERVAL_ATTRIBUTE = "timeout-check-interval";

    public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
    public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";
    public static final String PROPERTY_TIMEOUT = "db-connection.query-pool.timeout";
    public static final String PROPERTY_TIMEOUT_CHECK_INTERVAL = "db-connection.query-pool.timeout-check-interval";

    public XQueryPool() {
        lastTimeOutCheck = new AtomicLong(System.currentTimeMillis());
    }

    @Override
    public void configure(final Configuration configuration) {
        final Integer maxStSz = (Integer) configuration.getProperty(PROPERTY_MAX_STACK_SIZE);
        final Integer maxPoolSz = (Integer) configuration.getProperty(PROPERTY_POOL_SIZE);
        final Long t = (Long) configuration.getProperty(PROPERTY_TIMEOUT);
        final Long tci = (Long) configuration.getProperty(PROPERTY_TIMEOUT_CHECK_INTERVAL);
        final NumberFormat nf = NumberFormat.getNumberInstance();

        if (maxPoolSz != null) {
            maxPoolSize = maxPoolSz;
        } else {
            maxPoolSize = MAX_POOL_SIZE;
        }

        if (maxStSz != null) {
            maxStackSize = maxStSz;
        } else {
            maxStackSize = MAX_STACK_SIZE;
        }

        if (t != null) {
            timeout = t;
        } else {
            timeout = TIMEOUT;
        }

        if (tci != null) {
            timeoutCheckInterval = tci;
        } else {
            timeoutCheckInterval = TIMEOUT_CHECK_INTERVAL;
        }

        LOG.info("QueryPool: " +
                "size = " + nf.format(maxPoolSize) + "; " +
                "maxStackSize = " + nf.format(maxStackSize) + "; " +
                "timeout = " + nf.format(timeout) + "; " +
                "timeoutCheckInterval = " + nf.format(timeoutCheckInterval));
    }

    public void returnCompiledXQuery(final Source source, final CompiledXQuery xquery) {
        returnObject(source, xquery);
    }

    private void returnObject(final Source source, final CompiledXQuery xquery) {
        if (pool.size() >= maxPoolSize) {
            timeoutCheck();
        }

        if (pool.size() < maxPoolSize) {

            final Set<Entry> stack = pool.computeIfAbsent(source, (k) -> new ConcurrentSkipListSet<>());

            if (stack.size() < maxStackSize) {
                stack.add(new Entry(xquery, System.currentTimeMillis()));
            }
        }
    }

    private CompiledXQuery borrowObject(final Source source) {

        final NavigableSet<Entry> stack = pool.get(source);
        if (stack == null) {
            return null;
        }

        // now check if the compiled expression is valid
        // it might become invalid if an imported module has changed.
        final Entry entry = stack.pollFirst();
        if (entry == null) {
            return null;
        }

        final CompiledXQuery query = entry.script;

        if (!query.isValid()) {
            // the compiled query is no longer valid: one of the imported
            // modules may have changed
            pool.remove(source);
            return null;
        }

        return query;
    }

    public CompiledXQuery borrowCompiledXQuery(final DBBroker broker, final Source source) throws PermissionDeniedException {
        //check execution permission
        source.validate(broker.getCurrentSubject(), Permission.EXECUTE);

        return borrowObject(source);
    }

    public void clear() {
        pool.clear();
    }

    private void timeoutCheck() {
        if (timeoutCheckInterval < 0L) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        if (currentTime - lastTimeOutCheck.get() < timeoutCheckInterval) {
            return;
        }

        lastTimeOutCheck.set(currentTime);

        long until = currentTime + timeout;

        for (final Iterator<Map.Entry<Source, NavigableSet<Entry>>> it = pool.entrySet().iterator(); it.hasNext(); ) {
            final NavigableSet<Entry> next = it.next().getValue();

            Entry entry = next.pollLast();

            if (until < entry.ts) {
                it.remove();
            }
        }
    }

    static class Entry implements Comparable<Entry> {

        CompiledXQuery script;
        long ts;

        Entry(CompiledXQuery script, long ts) {
            this.script = script;
            this.ts = ts;
        }

        @Override
        public int compareTo(Entry o) {
            return Long.compare(ts, o.ts);
        }
    }
}
