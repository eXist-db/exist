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
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.modules.httpclient.config.HttpClientOptions;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.http.HttpClient;
import java.util.stream.Collectors;

/**
 * JMX MBean implementation that exposes monitoring data for the {@link HttpClientFactory}
 * client cache.
 *
 * <p>Registered once as a singleton under the object name
 * {@value #OBJECT_NAME} on the platform {@link javax.management.MBeanServer}.</p>
 */
public class HttpClientCacheMonitor implements HttpClientCacheMXBean {

    public static final String OBJECT_NAME = "org.exist.management:type=HttpClientCache";

    private static final Logger LOG = LogManager.getLogger(HttpClientCacheMonitor.class);

    private final Cache<HttpClientOptions, HttpClient> cache;

    HttpClientCacheMonitor(final Cache<HttpClientOptions, HttpClient> cache) {
        this.cache = cache;
    }

    /**
     * Registers a singleton {@link HttpClientCacheMonitor} with the platform MBeanServer.
     * Safe to call multiple times — if the MBean is already registered the call is a no-op.
     *
     * @param cache the Caffeine cache to monitor
     */
    public static HttpClientCacheMonitor register(final Cache<HttpClientOptions, HttpClient> cache) {
        final HttpClientCacheMonitor monitor = new HttpClientCacheMonitor(cache);
        try {
            final ObjectName name = new ObjectName(OBJECT_NAME);
            final var server = ManagementFactory.getPlatformMBeanServer();
            if (!server.isRegistered(name)) {
                server.registerMBean(monitor, name);
            }
        } catch (final MalformedObjectNameException e) {
            // OBJECT_NAME is a compile-time constant — this cannot happen
            throw new IllegalStateException("Unexpected malformed JMX object name: " + OBJECT_NAME, e);
        } catch (final Exception e) {
            LOG.warn("Failed to register HttpClientCache JMX MBean: {}", e.getMessage(), e);
        }
        return monitor;
    }

    @Override
    public long getCacheSize() {
        cache.cleanUp();
        return cache.estimatedSize();
    }

    @Override
    public long getHitCount() {
        return cache.stats().hitCount();
    }

    @Override
    public long getMissCount() {
        return cache.stats().missCount();
    }

    @Override
    public long getEvictionCount() {
        return cache.stats().evictionCount();
    }

    @Override
    public double getHitRate() {
        return cache.stats().hitRate();
    }

    @Override
    public String getCachedClientsSummary() {
        return cache.asMap().keySet().stream()
                .map(opts -> "followRedirect=" + opts.followRedirect()
                        + ", timeout=" + opts.timeout() + "s"
                        + ", autoAcceptEncoding=" + opts.autoAcceptEncoding())
                .collect(Collectors.joining("; "));
    }

    @Override
    public void reset() {
        cache.asMap().values().forEach(HttpClient::close);
        cache.invalidateAll();
    }
}
