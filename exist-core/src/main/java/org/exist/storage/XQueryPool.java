/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.storage;

import java.text.NumberFormat;
import java.util.Queue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.xquery.*;
import org.jctools.queues.atomic.MpmcAtomicArrayQueue;

/**
 * Global pool for compiled XQuery expressions.
 *
 * Expressions are stored and retrieved from the pool by comparing the
 * {@link org.exist.source.Source} objects from which they were created.
 *
 * For each XQuery, a maximum of {@link #DEFAULT_MAX_QUERY_STACK_SIZE} compiled
 * expressions are kept in the pool.
 * An XQuery expression will be removed from the pool if it has not been
 * used for a pre-defined timeout (default is {@link #DEFAULT_TIMEOUT}); these
 * settings can be configured in conf.xml.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class XQueryPool implements BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(XQueryPool.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
    public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
    public static final String POOL_SIZE_ATTTRIBUTE = "size";
    public static final String TIMEOUT_ATTRIBUTE = "timeout";

    public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
    public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";
    public static final String PROPERTY_TIMEOUT = "db-connection.query-pool.timeout";

    private static final int DEFAULT_MAX_POOL_SIZE = 128;
    private static final int DEFAULT_MAX_QUERY_STACK_SIZE = 64;
    private static final long DEFAULT_TIMEOUT = 120_000L;   // ms (i.e. 2 mins)

    private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    private int maxQueryStackSize = DEFAULT_MAX_QUERY_STACK_SIZE;
    private long timeout = DEFAULT_TIMEOUT;

    private final AtomicBoolean configured = new AtomicBoolean();
    private Cache<Source, Queue<CompiledXQuery>> cache;

    @Override
    public void configure(final Configuration configuration) {
        if(configured.compareAndSet(false, true)) {

            final Integer maxStSz = (Integer) configuration.getProperty(PROPERTY_MAX_STACK_SIZE);
            final Integer maxPoolSz = (Integer) configuration.getProperty(PROPERTY_POOL_SIZE);
            final Long t = (Long) configuration.getProperty(PROPERTY_TIMEOUT);
            final NumberFormat nf = NumberFormat.getNumberInstance();

            if (maxPoolSz != null) {
                this.maxPoolSize = maxPoolSz;
            } else {
                this.maxPoolSize = DEFAULT_MAX_POOL_SIZE;
            }

            if (maxStSz != null) {
                this.maxQueryStackSize = maxStSz;
            } else {
                this.maxQueryStackSize = DEFAULT_MAX_QUERY_STACK_SIZE;
            }

            if (t != null) {
                this.timeout = t;
            } else {
                this.timeout = DEFAULT_TIMEOUT;
            }

            this.cache = Caffeine.newBuilder()
                    .maximumSize(maxPoolSize)
                    .expireAfterAccess(timeout, TimeUnit.MILLISECONDS)
                    .build();

            LOG.info("QueryPool: " +
            "size = " + nf.format(maxPoolSize) + "; " +
            "maxQueryStackSize = " + nf.format(maxQueryStackSize) + "; " +
            "timeout = " + nf.format(timeout) + "; ");
        } else {
            throw new IllegalStateException("XQuery Pool has already been configured");
        }
    }

    /**
     * Returns a compiled XQuery to the XQuery pool.
     *
     * @param source The source of the compiled XQuery.
     * @param compiledXQuery The compiled XQuery to add to the XQuery pool.
     */
    public void returnCompiledXQuery(final Source source, final CompiledXQuery compiledXQuery) {
        if (compiledXQuery == null) {
            return;
        }

        cache.asMap().compute(source, (key, value) -> {
            final Queue<CompiledXQuery> queue;
            if (value != null) {
                queue = value;
            } else {
                queue = new MpmcAtomicArrayQueue<>(maxQueryStackSize);
            }

            queue.offer(compiledXQuery);

            return queue;
        });
    }

    /**
     * Borrows a compiled XQuery from the XQuery pool.
     *
     * @param broker A database broker.
     * @param source The source identifying the XQuery to borrow.
     *
     * @return The compiled XQuery identified by the source, or null if
     *     there is no valid compiled representation in the XQuery pool.
     *
     * @throws PermissionDeniedException if the caller does not have execute
     *     permission for the compiled XQuery.
     */
    public CompiledXQuery borrowCompiledXQuery(final DBBroker broker, final Source source)
            throws PermissionDeniedException {
        if(broker == null || source == null) {
            return null;
        }

        // get (compute by checking validity) the stack of compiled XQuerys for the source
        final Queue<CompiledXQuery> queue = cache.asMap().computeIfPresent(source, (key, value) -> {
            if(!value.isEmpty()) {

                // remove any stack of compiled queries which are now invalid
                final CompiledXQuery firstCompiledXQuery = value.peek();
                final Source cachedSource = firstCompiledXQuery.getSource();
                Source.Validity validity = cachedSource.isValid(broker);
                if (validity == Source.Validity.UNKNOWN) {
                    validity = cachedSource.isValid(source);
                }

                if (validity == Source.Validity.INVALID || validity == Source.Validity.UNKNOWN) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug(source.getKey() + " is invalid, removing from XQuery Pool...");
                    }
                    return null;    // returning null will remove the entry from the cache
                }

                // the compiled query is no longer valid: one of the imported
                // modules may have changed
                if(!firstCompiledXQuery.isValid()) {
                    return null;    // returning null will remove the entry from the cache
                }
            }

            return value;
        });

        if(queue == null) {
            return null;
        }

        final CompiledXQuery query = queue.poll();
        if(query == null) {
            return null;
        }

        //check execution permission
        source.validate(broker.getCurrentSubject(), Permission.EXECUTE);

        return query;
    }

    /**
     * Removes all entries from the XQuery Pool.
     */
    public void clear() {
        cache.invalidateAll();
    }
}
