/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.storage;

import java.text.NumberFormat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Objects;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.util.Holder;
import org.exist.xquery.*;
import org.exist.xquery.Module;

/**
 * Global pool for compiled XQuery expressions.
 *
 * Expressions are stored and retrieved from the pool by comparing the
 * {@link org.exist.source.Source} objects from which they were created.
 *
 * For each XQuery, a maximum of {@link #DEFAULT_MAX_QUERY_STACK_SIZE} compiled
 * expressions are kept in the pool.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class XQueryPool implements BrokerPoolService {

    private static final Logger LOG = LogManager.getLogger(XQueryPool.class);

    public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
    public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
    public static final String POOL_SIZE_ATTTRIBUTE = "size";

    public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
    public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";

    private static final int DEFAULT_MAX_POOL_SIZE = 128;
    private static final int DEFAULT_MAX_QUERY_STACK_SIZE = 64;

    private int maxPoolSize = DEFAULT_MAX_POOL_SIZE;
    private int maxQueryStackSize = DEFAULT_MAX_QUERY_STACK_SIZE;

    /**
     * Source -> Deque of compiled Queries
     */
    private Cache<Source, Deque<CompiledXQuery>> cache;

    @Override
    public void configure(final Configuration configuration) {
        final Integer maxStSz = (Integer) configuration.getProperty(PROPERTY_MAX_STACK_SIZE);
        final Integer maxPoolSz = (Integer) configuration.getProperty(PROPERTY_POOL_SIZE);
        final NumberFormat nf = NumberFormat.getNumberInstance();

        this.maxPoolSize = Objects.requireNonNullElse(maxPoolSz, DEFAULT_MAX_POOL_SIZE);

        this.maxQueryStackSize = Objects.requireNonNullElse(maxStSz, DEFAULT_MAX_QUERY_STACK_SIZE);

        this.cache = Caffeine.newBuilder()
                .maximumSize(maxPoolSize)
                .build();

        LOG.info("QueryPool: size = {}; maxQueryStackSize = {}", nf.format(maxPoolSize), nf.format(maxQueryStackSize));
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
            final Deque<CompiledXQuery> deque = Objects.requireNonNullElseGet(value, () -> new ArrayDeque<>(maxQueryStackSize));
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
     * @return The compiled XQuery identified by the source, or null if there is no valid compiled
     *     representation in the XQuery pool, or the caller may not read a module it imports — in
     *     which case compiling the query itself would be denied at the import gate too.
     *
     * @throws PermissionDeniedException if the caller does not have execute
     *     permission for the compiled XQuery.
     */
    public CompiledXQuery borrowCompiledXQuery(final DBBroker broker, final Source source)
            throws PermissionDeniedException {
        if (broker == null || source == null) {
            return null;
        }

        // check execution permission BEFORE the cache is touched: a caller which may not execute the
        // query must not be able to perturb the pool which is shared with every other user
        if (source instanceof DBSource bSource) {
            bSource.validate(Permission.EXECUTE);
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

            if (!isCompiledQueryValid(firstCompiledXQuery)) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("{} is invalid, removing from XQuery Pool...", source.pathOrShortIdentifier());
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

        final CompiledXQuery compiledXQuery = borrowedCompiledQuery.value;

        if (!callerMayUseModulesOf(broker, compiledXQuery)) {
            // a cache hit must never authorize what a cold compile would refuse. The caller cannot read
            // one of the imported modules, so compiling the query itself would be denied at the import
            // gate (SourceFactory#getSource). Hand the entry back untouched — it is perfectly good for
            // the users which may read the module — and let this caller take the cold path, where it
            // fails exactly as it would with an empty pool
            returnCompiledXQuery(source, compiledXQuery);
            return null;
        }

        return compiledXQuery;
    }

    /**
     * Determines whether the caller could have resolved every module imported by a pooled compiled
     * XQuery, had it compiled the query itself.
     *
     * A cold compile resolves each imported module through
     * {@link org.exist.source.SourceFactory#getSource(DBBroker, String, String, boolean)}, which
     * requires READ on the module. The same gate is applied here, so that borrowing from the pool
     * grants no access which compiling would not.
     *
     * @param broker the database broker
     * @param compiledXQuery the compiled query to check the imported modules of
     *
     * @return true if the caller may read every imported module, false otherwise.
     */
    private static boolean callerMayUseModulesOf(final DBBroker broker, final CompiledXQuery compiledXQuery) {
        final XQueryContext context = compiledXQuery.getContext();
        if (context == null) {
            return true;
        }

        final Subject subject = broker.getCurrentSubject();
        for (final Iterator<Module> it = context.getAllModules(); it.hasNext(); ) {
            final Module module = it.next();
            if (module == null || module.isInternalModule()) {
                continue;
            }

            if (((ExternalModule) module).getSource() instanceof DBSource moduleSource) {
                try {
                    moduleSource.validate(subject, Permission.READ);
                } catch (final PermissionDeniedException e) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Not serving the pooled query, the caller may not read the imported module {}",
                                moduleSource.pathOrShortIdentifier());
                    }
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Determines if a compiled XQuery is still valid.
     *
     * @param compiledXQuery the compiled query
     *
     * @return true if the compiled query is still valid, false otherwise.
     */
    private static boolean isCompiledQueryValid(final CompiledXQuery compiledXQuery) {
        final Source cachedSource = compiledXQuery.getSource();
        final Source.Validity validity = cachedSource.isValid();

        if (validity == Source.Validity.INVALID) {
            return false;    // returning false will remove the entry from the cache
        }

        // the compiled query is no longer valid if one of the imported
        // modules may have changed
        return compiledXQuery.isValid();
    }

    /**
     * Removes all entries from the XQuery Pool.
     */
    public void clear() {
        cache.invalidateAll();
    }
}
