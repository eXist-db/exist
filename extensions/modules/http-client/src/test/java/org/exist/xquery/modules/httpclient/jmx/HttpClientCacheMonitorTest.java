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
package org.exist.xquery.modules.httpclient.jmx;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.exist.xquery.modules.httpclient.config.HttpClientOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.management.MBeanServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;

/**
 * Tests for {@link HttpClientCacheMonitor} JMX registration and attribute reporting.
 */
class HttpClientCacheMonitorTest {

    private MBeanServer server;
    private ObjectName name;
    private Cache<HttpClientOptions, HttpClient> cache;

    @BeforeEach
    void setUp() throws Exception {
        server = ManagementFactory.getPlatformMBeanServer();
        name = new ObjectName(HttpClientCacheMonitor.OBJECT_NAME);

        // Unregister any leftover MBean from a previous test run in the same JVM
        if (server.isRegistered(name)) {
            server.unregisterMBean(name);
        }

        cache = Caffeine.newBuilder().recordStats().build();
        HttpClientCacheMonitor.register(cache);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (server.isRegistered(name)) {
            server.unregisterMBean(name);
        }
    }

    @Test
    void mBeanIsRegistered() {
        assertTrue(server.isRegistered(name), "HttpClientCache MBean should be registered");
    }

    @Test
    void registerAndGetIsIdempotent() {
        // Second call must not throw and must not register a duplicate
        HttpClientCacheMonitor.register(cache);
        assertTrue(server.isRegistered(name));
    }

    @Test
    void cacheSizeReflectsEntries() throws Exception {
        assertEquals(0L, server.getAttribute(name, "CacheSize"));

        final HttpClientOptions opts = HttpClientOptions.DEFAULTS;
        cache.put(opts, HttpClient.newHttpClient());

        assertEquals(1L, server.getAttribute(name, "CacheSize"));
    }

    @Test
    void hitAndMissCountsAreReported() throws Exception {
        final HttpClientOptions opts = HttpClientOptions.DEFAULTS;
        cache.put(opts, HttpClient.newHttpClient());

        // hit
        cache.getIfPresent(opts);
        // miss
        cache.getIfPresent(new HttpClientOptions(false, 30, HttpClient.Version.HTTP_1_1, true));

        assertEquals(1L, server.getAttribute(name, "HitCount"));
        assertEquals(1L, server.getAttribute(name, "MissCount"));
    }

    @Test
    void hitRateIsZeroWhenNoRequests() throws Exception {
        assertEquals(0.0, (double) server.getAttribute(name, "HitRate"), 0.0001);
    }

    @Test
    void cachedClientsSummaryListsConfigurations() throws Exception {
        cache.put(new HttpClientOptions(true, 0, HttpClient.Version.HTTP_1_1, true),
                HttpClient.newHttpClient());
        cache.put(new HttpClientOptions(false, 30, HttpClient.Version.HTTP_1_1, true),
                HttpClient.newHttpClient());

        final String summary = (String) server.getAttribute(name, "CachedClientsSummary");
        assertTrue(summary.contains("followRedirect=true"), "Summary should mention followRedirect=true");
        assertTrue(summary.contains("followRedirect=false"), "Summary should mention followRedirect=false");
        assertTrue(summary.contains("timeout=30"), "Summary should mention timeout=30");
        assertTrue(summary.contains("autoAcceptEncoding=true"), "Summary should mention autoAcceptEncoding=true");
    }

    @Test
    void cachedClientsSummaryIsEmptyWhenCacheIsEmpty() throws Exception {
        final String summary = (String) server.getAttribute(name, "CachedClientsSummary");
        assertEquals("", summary);
    }

    @Test
    void resetClearsCache() throws Exception {
        cache.put(HttpClientOptions.DEFAULTS, HttpClient.newHttpClient());
        assertEquals(1L, server.getAttribute(name, "CacheSize"));

        server.invoke(name, "reset", new Object[0], new String[0]);

        assertEquals(0L, server.getAttribute(name, "CacheSize"));
    }
}
