/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
package org.exist.xmlrpc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Used by {@link XmldbRequestProcessorFactory} to cache query results. Each query result
 * is identified by a unique integer id.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class QueryResultCache {

    private static final Logger LOG = LogManager.getLogger(QueryResultCache.class);
    private static final int TIMEOUT = 180_000;  // ms (e.g. 2 minutes)

    private final AtomicInteger cacheIdCounter = new AtomicInteger();
    private final Cache<Integer, AbstractCachedResult> cache;

    public QueryResultCache() {
        this.cache = Caffeine.newBuilder()
                .expireAfterAccess(TIMEOUT, TimeUnit.MILLISECONDS)
                .removalListener((key, value, cause) -> {
                    final AbstractCachedResult qr = (AbstractCachedResult)value;
                    qr.free();  // must free associated resources
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Removing cached result set: " + new Date(qr.getTimestamp()).toString());
                    }
                }).build();
    }

    public int add(final AbstractCachedResult qr) {
        final int cacheId = cacheIdCounter.getAndIncrement();
        cache.put(cacheId, qr);
        return cacheId;
    }

    public AbstractCachedResult get(final int cacheId) {
        if (cacheId < 0 || cacheId >= cacheIdCounter.get()) {
            return null;
        }
        return cache.getIfPresent(cacheId);
    }

    public QueryResult getResult(final int cacheId) {
        final AbstractCachedResult acr = get(cacheId);
        return (acr != null && acr instanceof QueryResult) ? (QueryResult) acr : null;
    }

    public SerializedResult getSerializedResult(final int cacheId) {
        final AbstractCachedResult acr = get(cacheId);
        return (acr != null && acr instanceof SerializedResult) ? (SerializedResult) acr : null;
    }

    public void remove(final int cacheId) {
        if (cacheId < 0 || cacheId >= cacheIdCounter.get()) {
            return; // out of scope
        }

        cache.invalidate(cacheId);
    }

    public void remove(final int cacheId, final int hash) {
        if (cacheId < 0 || cacheId >= cacheIdCounter.get()) {
            return; // out of scope
        }

        final AbstractCachedResult qr = cache.getIfPresent(cacheId);
        if(qr != null && qr.hashCode() == hash) {
            cache.invalidate(cacheId);
        }
    }
}
