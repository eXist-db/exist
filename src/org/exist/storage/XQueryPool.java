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

import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.xquery.*;

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
public class XQueryPool {

    private final static int MAX_POOL_SIZE = 128;
    private final static int MAX_STACK_SIZE = 5;
    private final static long TIMEOUT = 120000L;
    private final static long TIMEOUT_CHECK_INTERVAL = 30000L;

    private final static Logger LOG = LogManager.getLogger(XQueryPool.class);

    private ConcurrentMap<Source, Deque<CompiledXQuery>> pool = new ConcurrentHashMap<>();

    private long lastTimeOutCheck;
    private long lastTimeOfCleanup;

    @ConfigurationFieldAsAttribute("size")
    private final int maxPoolSize;

    @ConfigurationFieldAsAttribute("max-stack-size")
    private final int maxStackSize;

    @ConfigurationFieldAsAttribute("timeout")
    private final long timeout;

    @ConfigurationFieldAsAttribute("timeout-check-interval")
    private final long timeoutCheckInterval;

    public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
    public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
    public static final String POOL_SIZE_ATTRIBUTE = "size";
    public static final String TIMEOUT_ATTRIBUTE = "timeout";
    public static final String TIMEOUT_CHECK_INTERVAL_ATTRIBUTE = "timeout-check-interval";

    public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
    public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";
    public static final String PROPERTY_TIMEOUT = "db-connection.query-pool.timeout";
    public static final String PROPERTY_TIMEOUT_CHECK_INTERVAL = "db-connection.query-pool.timeout-check-interval";

    /**
     * @param conf The configuration
     */
    public XQueryPool(final Configuration conf) {

        lastTimeOutCheck = lastTimeOfCleanup = System.currentTimeMillis();

        final Integer maxStSz = (Integer) conf.getProperty(PROPERTY_MAX_STACK_SIZE);
        final Integer maxPoolSz = (Integer) conf.getProperty(PROPERTY_POOL_SIZE);
        final Long t = (Long) conf.getProperty(PROPERTY_TIMEOUT);
        final Long tci = (Long) conf.getProperty(PROPERTY_TIMEOUT_CHECK_INTERVAL);
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
        final long ts = source.getCacheTimestamp();
        if (ts == 0 || ts > lastTimeOfCleanup) {
            if (pool.size() >= maxPoolSize) {
                timeoutCheck();
            }

            if (pool.size() < maxPoolSize) {
                source.setCacheTimestamp(System.currentTimeMillis());

                Deque<CompiledXQuery> stack = pool.computeIfAbsent(source, (k) -> new ArrayDeque<>());

                if (stack.size() < maxStackSize) {
                    // check if the query is already in pool before adding,
                    // may happen for modules, don't add it a second time!
                    if(!stack.contains(xquery)) {
                        stack.push(xquery);
                    }
                }
            }
        }
    }

    private Object borrowObject(final Source source) {

        final Deque<CompiledXQuery> stack = pool.get(source);
        if (stack == null || stack.isEmpty()) {
            return null;
        }

        // now check if the compiled expression is valid
        // it might become invalid if an imported module has changed.
        final CompiledXQuery query = stack.pollFirst();
        if (query == null) {
            return null;
        }

        //final XQueryContext context = query.getContext();
        //context.setBroker(broker);

        // query.isValid() may open collections which in turn tries to acquire
        // org.exist.storage.lock.ReentrantReadWriteLock. In order to avoid
        // deadlocks with concurrent queries holding that lock while borrowing
        // we must not hold onto the XQueryPool while calling isValid().

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

        final CompiledXQuery query = (CompiledXQuery) borrowObject(source);
        if (query == null) {
            return null;
        }

        // now check if the compiled expression is valid
        // it might become invalid if an imported module has changed.
        //final XQueryContext context = query.getContext();
        //context.setBroker(broker);
        return query;
    }

    public void clear() {
        lastTimeOfCleanup = System.currentTimeMillis();
        pool.clear();
    }

    private void timeoutCheck() {
        if (timeoutCheckInterval < 0L) {
            return;
        }

        final long currentTime = System.currentTimeMillis();
        if (currentTime - lastTimeOutCheck < timeoutCheckInterval) {
            return;
        }

        for (final Iterator it = pool.entrySet().iterator(); it.hasNext(); ) {
            final Source next = (Source) it.next();
            if (currentTime - next.getCacheTimestamp() > timeout) {
                it.remove();
            }
        }

        lastTimeOutCheck = currentTime;
    }
}
