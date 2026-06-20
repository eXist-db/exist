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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;

import static org.junit.Assert.*;

/**
 * Tests for {@link HttpClientCacheMonitor} JMX registration and attribute reporting.
 */
public class HttpClientCacheMonitorTest {

    private MBeanServer server;
    private ObjectName name;
    private Cache<HttpClientOptions, HttpClient> cache;

    @Before
    public void setUp() throws Exception {
        server = ManagementFactory.getPlatformMBeanServer();
        name = new ObjectName(HttpClientCacheMonitor.OBJECT_NAME);

        // Unregister any leftover MBean from a previous test run in the same JVM
        if (server.isRegistered(name)) {
            server.unregisterMBean(name);
        }

        cache = Caffeine.newBuilder().recordStats().build();
        HttpClientCacheMonitor.register(cache);
    }

    @After
    public void tearDown() throws Exception {
        if (server.isRegistered(name)) {
            server.unregisterMBean(name);
        }
    }

    @Test
    public void mBeanIsRegistered() {
        assertTrue("HttpClientCache MBean should be registered", server.isRegistered(name));
    }

    @Test
    public void registerAndGetIsIdempotent() {
        // Second call must not throw and must not register a duplicate
        HttpClientCacheMonitor.register(cache);
        assertTrue(server.isRegistered(name));
    }

    @Test
    public void cacheSizeReflectsEntries() throws Exception {
        assertEquals(0L, server.getAttribute(name, "CacheSize"));

        final HttpClientOptions opts = HttpClientOptions.DEFAULTS;
        cache.put(opts, HttpClient.newHttpClient());

        assertEquals(1L, server.getAttribute(name, "CacheSize"));
    }

    @Test
    public void hitAndMissCountsAreReported() throws Exception {
        final HttpClientOptions opts = HttpClientOptions.DEFAULTS;
        cache.put(opts, HttpClient.newHttpClient());

        // hit
        cache.getIfPresent(opts);
        // miss
        cache.getIfPresent(new HttpClientOptions(false, 30, HttpClient.Version.HTTP_1_1));

        assertEquals(1L, server.getAttribute(name, "HitCount"));
        assertEquals(1L, server.getAttribute(name, "MissCount"));
    }

    @Test
    public void hitRateIsZeroWhenNoRequests() throws Exception {
        assertEquals(0.0, (double) server.getAttribute(name, "HitRate"), 0.0001);
    }

    @Test
    public void cachedClientsSummaryListsConfigurations() throws Exception {
        cache.put(new HttpClientOptions(true, 0, HttpClient.Version.HTTP_1_1),
                HttpClient.newHttpClient());
        cache.put(new HttpClientOptions(false, 30, HttpClient.Version.HTTP_1_1),
                HttpClient.newHttpClient());

        final String summary = (String) server.getAttribute(name, "CachedClientsSummary");
        assertTrue("Summary should mention followRedirect=true", summary.contains("followRedirect=true"));
        assertTrue("Summary should mention followRedirect=false", summary.contains("followRedirect=false"));
        assertTrue("Summary should mention timeout=30s", summary.contains("timeout=30s"));
    }

    @Test
    public void cachedClientsSummaryIsEmptyWhenCacheIsEmpty() throws Exception {
        final String summary = (String) server.getAttribute(name, "CachedClientsSummary");
        assertEquals("", summary);
    }

    @Test
    public void resetClearsCache() throws Exception {
        cache.put(HttpClientOptions.DEFAULTS, HttpClient.newHttpClient());
        assertEquals(1L, server.getAttribute(name, "CacheSize"));

        server.invoke(name, "reset", new Object[0], new String[0]);

        assertEquals(0L, server.getAttribute(name, "CacheSize"));
    }
}
