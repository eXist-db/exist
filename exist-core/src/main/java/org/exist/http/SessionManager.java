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
package org.exist.http;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.value.Sequence;

import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class SessionManager {

    private static final Logger LOG = LogManager.getLogger(SessionManager.class);
    private static final long TIMEOUT = 120_000;  // ms (e.g. 2 minutes)
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Cache<Long, QueryResult> cache;

    private static class QueryResult {
        final int subjectId;
        final String query;
        final Sequence sequence;

        private QueryResult(final int subjectId, final String query, final Sequence sequence) {
            this.subjectId = subjectId;
            this.query = query;
            this.sequence = sequence;
        }
    }

    public SessionManager() {
        final Caffeine<Object, Object> cacheBuilder = Caffeine.newBuilder()
                .expireAfterAccess(TIMEOUT, TimeUnit.MILLISECONDS);
        if(LOG.isDebugEnabled()) {
            cacheBuilder.removalListener((key, value, cause) -> LOG.debug("Removing cached query result for session: {}", key));
        }
        cache = cacheBuilder.build();
    }

    /**
     * Cache a query result and return a cryptographically random,
     * non-negative session id.
     *
     * @param subjectId the id of the subject that owns this cache entry;
     *                  only callers with the same subject id can later
     *                  retrieve or release it
     * @param query     the original query text (used as a secondary
     *                  consistency check)
     * @param sequence  the result sequence
     * @return a non-negative session id; collisions with existing live
     *         entries are retried
     */
    public long add(final int subjectId, final String query, final Sequence sequence) {
        long sessionId;
        do {
            sessionId = RANDOM.nextLong() & Long.MAX_VALUE;
        } while (cache.getIfPresent(sessionId) != null);
        cache.put(sessionId, new QueryResult(subjectId, query, sequence));
        return sessionId;
    }

    /**
     * Look up a cached query result. Returns null when no entry exists,
     * the entry has expired, the query text differs, or the requesting
     * subject does not match the subject that created the entry.
     */
    public Sequence get(final int subjectId, final String query, final long sessionId) {
        if (sessionId < 0) {
            return null;
        }

        final QueryResult cached = cache.getIfPresent(sessionId);
        if (cached == null) {
            return null;
        }

        if (cached.subjectId != subjectId) {
            LOG.warn("Session {} requested by subject {} but owned by subject {}; refusing.",
                    sessionId, subjectId, cached.subjectId);
            return null;
        }

        if (cached.query.equals(query)) {
            return cached.sequence;
        } else {
            // wrong query
            return null;
        }
    }

    /**
     * Release a cached query result. Only the original subject may
     * release the entry.
     */
    public void release(final int subjectId, final long sessionId) {
        if (sessionId < 0) {
            return;
        }
        final QueryResult cached = cache.getIfPresent(sessionId);
        if (cached == null) {
            return;
        }
        if (cached.subjectId != subjectId) {
            LOG.warn("Session {} release requested by subject {} but owned by subject {}; refusing.",
                    sessionId, subjectId, cached.subjectId);
            return;
        }
        cache.invalidate(sessionId);
    }
}
