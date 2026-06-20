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
package org.exist.xquery.modules.httpclient;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.mizosoft.methanol.Methanol;
import org.exist.xquery.modules.httpclient.config.HttpClientOptions;
import org.exist.xquery.modules.httpclient.jmx.HttpClientCacheMonitor;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Factory for creating and caching configured {@link HttpClient} instances.
 *
 * <p>Methanol augments java.net.http.HttpClient: autoAcceptEncoding advertises Accept-Encoding
 * and transparently decodes gzip/deflate responses, and readTimeout gives a per-read (inactivity)
 * timeout that the bare JDK client lacks.</p>
 *
 * <p>Clients are cached by {@link HttpClientOptions} so that requests sharing the same options
 * reuse a single {@link HttpClient} instance (and its underlying connection pool). The cache
 * holds at most 25 entries and evicts entries that have not been accessed for one hour.
 * It is also cleared on JVM shutdown via a registered shutdown hook.</p>
 */
class HttpClientFactory {

    private static final Cache<HttpClientOptions, HttpClient> CLIENT_CACHE =
            Caffeine.newBuilder()
                    .maximumSize(25)
                    .expireAfterAccess(Duration.ofHours(1))
                    .recordStats()
                    .build();

    private static final HttpClientCacheMonitor MONITOR =
            HttpClientCacheMonitor.register(CLIENT_CACHE);

    static {
        Runtime.getRuntime().addShutdownHook(new Thread(HttpClientFactory::reset,
                "http-client-cache-shutdown"));
    }

    private HttpClientFactory() {
        // utility class
    }

    /**
     * Returns a cached {@link HttpClient} for the given {@link HttpClientOptions}, creating one
     * if no matching client exists in the cache yet.
     *
     * @param options the request options controlling redirect behaviour, timeouts, and HTTP version
     * @return a configured {@link HttpClient}
     */
    static HttpClient get(final HttpClientOptions options) {
        return CLIENT_CACHE.get(options, HttpClientFactory::newClient);
    }

    /**
     * Closes all cached {@link HttpClient} instances and removes them from the cache.
     */
    static void reset() {
        MONITOR.reset();
    }

    private static HttpClient newClient(final HttpClientOptions options) {
        final Methanol.Builder clientBuilder = Methanol.newBuilder()
                .autoAcceptEncoding(options.autoAcceptEncoding())
                .version(options.httpVersion())
                .followRedirects(options.followRedirect()
                        ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);
        if (options.timeout() > 0) {
            final Duration timeoutDuration = Duration.ofSeconds(options.timeout());
            clientBuilder.connectTimeout(timeoutDuration).readTimeout(timeoutDuration);
        }

        return clientBuilder.build();
    }
}
