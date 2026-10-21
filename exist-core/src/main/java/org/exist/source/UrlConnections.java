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
package org.exist.source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;
import org.exist.xquery.XQueryContext;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 * Opens connections to URLs with bounded connect and read timeouts, so that a slow or silent
 * remote host cannot block the calling thread, which usually holds a broker, indefinitely.
 * See <a href="https://github.com/eXist-db/exist/issues/6898">#6898</a> and
 * <a href="https://github.com/eXist-db/exist/issues/6902">#6902</a>.
 */
public final class UrlConnections {

    private static final Logger LOG = LogManager.getLogger(UrlConnections.class);

    /**
     * Default connect timeout in milliseconds.
     */
    public static final int DEFAULT_CONNECT_TIMEOUT = 10_000;
    /**
     * Default read timeout in milliseconds.
     */
    public static final int DEFAULT_READ_TIMEOUT = 30_000;

    private UrlConnections() {
    }

    /**
     * Connect and read timeouts in milliseconds, 0 means no timeout. The read timeout is the
     * longest wait for data between two reads, not a limit on the whole transfer.
     *
     * @param connect the connect timeout
     * @param read the read timeout
     */
    public record Timeouts(int connect, int read) {

        public static final Timeouts DEFAULT = new Timeouts(DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT);

        /**
         * The timeouts configured by the {@code url-connect-timeout} and {@code url-read-timeout}
         * attributes of {@code <xquery>} in conf.xml.
         *
         * @param config the database configuration, or null for the defaults
         *
         * @return the configured timeouts
         */
        public static Timeouts from(@Nullable final Configuration config) {
            if (config == null) {
                return DEFAULT;
            }
            return new Timeouts(
                    config.getProperty(XQueryContext.PROPERTY_URL_CONNECT_TIMEOUT, DEFAULT_CONNECT_TIMEOUT),
                    config.getProperty(XQueryContext.PROPERTY_URL_READ_TIMEOUT, DEFAULT_READ_TIMEOUT));
        }

        /**
         * The timeouts of the running database, for code that has no broker or configuration at hand.
         *
         * @return the configured timeouts, or the defaults when no database is running
         */
        public static Timeouts current() {
            if (BrokerPool.isConfigured()) {
                try {
                    return from(BrokerPool.getInstance().getConfiguration());
                } catch (final EXistException e) {
                    LOG.debug("No database instance available, using the default URL timeouts: {}", e.getMessage());
                }
            }
            return DEFAULT;
        }
    }

    /**
     * Open a connection to the URL with the timeouts applied.
     *
     * @param url the URL
     * @param timeouts the timeouts to apply
     *
     * @return the configured, not yet connected, connection
     *
     * @throws IOException if the connection cannot be opened
     */
    public static URLConnection open(final URL url, final Timeouts timeouts) throws IOException {
        final URLConnection connection = url.openConnection();
        connection.setConnectTimeout(timeouts.connect());
        connection.setReadTimeout(timeouts.read());
        return connection;
    }

    /**
     * Open a stream to the URL with the timeouts applied, in place of {@link URL#openStream()}.
     *
     * @param url the URL
     * @param timeouts the timeouts to apply
     *
     * @return the stream
     *
     * @throws IOException if the URL cannot be read
     */
    public static InputStream openStream(final URL url, final Timeouts timeouts) throws IOException {
        return open(url, timeouts).getInputStream();
    }
}
