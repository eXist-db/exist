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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.util.Holder;
import org.exist.xquery.*;

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

    /**
     * Source -> Deque of compiled Queries
     */
    private Cache<Source, Deque<CompiledXQuery>> cache;

    @Override
    public void configure(final Configuration configuration) {
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

        LOG.info("QueryPool: " + "size = " + nf.format(maxPoolSize) + "; "
                + "maxQueryStackSize = " + nf.format(maxQueryStackSize) + "; "
                + "timeout = " + nf.format(timeout) + "; ");
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
            final Deque<CompiledXQuery> deque;
            if (value != null) {
                deque = value;
            } else {
                deque = new ArrayDeque<>(maxQueryStackSize);
            }

            deque.offerFirst(compiledXQuery);

            return deque;
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
        if (broker == null || source == null) {
            return null;
        }

        // this will be set to non-null if we can borrow a query... allows us to escape the lamba, see https://github.com/ben-manes/caffeine/issues/192#issuecomment-337365618
        final Holder<CompiledXQuery> borrowedCompiledQuery = new Holder<>();

        // get (compute by checking validity) the stack of compiled XQuerys for the source
        final Deque<CompiledXQuery> deque = cache.asMap().computeIfPresent(source, (key, value) -> {
            final CompiledXQuery firstCompiledXQuery = value.pollFirst();
            if (firstCompiledXQuery == null) {
                // deque is empty, returning null will remove the entry from the cache
                return null;
            }

            if (!isCompiledQueryValid(broker, source, firstCompiledXQuery)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug(source.getKey() + " is invalid, removing from XQuery Pool...");
                }

                // query is invalid, returning null will remove the entry from the cache
                return null;
            }

            // escape the result from the lambda
            borrowedCompiledQuery.value = firstCompiledXQuery;

            // query is ok, preserve the tail of the deque
            return value;
        });

        if (deque == null) {
            return null;
        }

        //check execution permission
        source.validate(broker.getCurrentSubject(), Permission.EXECUTE);

        return borrowedCompiledQuery.value;
    }

    /**
     * Determines if a compiled XQuery is still valid.
     *
     * @param broker the database broker
     * @param source the source of the query
     * @param compiledXQuery the compiled query
     *
     * @return true if the compiled query is still valid, false otherwise.
     */
    private static boolean isCompiledQueryValid(final DBBroker broker, final Source source,
            final CompiledXQuery compiledXQuery) {
        final Source cachedSource = compiledXQuery.getSource();
        Source.Validity validity = cachedSource.isValid(broker);
        if (validity == Source.Validity.UNKNOWN) {
            validity = cachedSource.isValid(source);
        }

        if (validity == Source.Validity.INVALID || validity == Source.Validity.UNKNOWN) {
            return false;    // returning null will remove the entry from the cache
        }

        // the compiled query is no longer valid if one of the imported
        // modules may have changed
        if (!compiledXQuery.isValid()) {
            return false;
        }

        return true;
    }

    /**
     * Removes all entries from the XQuery Pool.
     */
    public void clear() {
        cache.invalidateAll();
    }
}
