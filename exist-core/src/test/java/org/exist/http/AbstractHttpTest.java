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

import com.evolvedbinary.j8fu.function.FunctionE;
import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.client.fluent.Executor;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;

import java.io.IOException;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public abstract class AbstractHttpTest {

    /**
     * Get the Server URI.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the Server URI.
     */
    protected static String getServerUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort() + "/exist";
    }

    /**
     * Get the URI of the Server's REST end-point.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the URI of the Server's REST end-point.
     */
    protected static String getRestUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/rest";
    }

    /**
     * Get the URI of the Server's Apps end-point.
     *
     * @param existWebServer the eXist-db Web Server.
     *
     * @return the URI of the Server's Apps end-point.
     */
    protected static String getAppsUri(final ExistWebServer existWebServer) {
        return getServerUri(existWebServer) + "/apps";
    }

    /**
     * Execute a function with a HTTP Client.
     *
     * @param <T> the return type of the <code>fn</code> function.
     * @param fn the function which accepts the HTTP Client.
     *
     * @return the result of the <code>fn</code> function.
     *
     * @throws IOException if an I/O error occurs
     */
    protected static <T> T withHttpClient(final FunctionE<HttpClient, T, IOException> fn) throws IOException {
        try (final CloseableHttpClient client = HttpClientBuilder
                .create()
                .disableAutomaticRetries()
                .build()) {
            return fn.apply(client);
        }
    }

    /**
     * Execute a function with a HTTP Executor.
     *
     * @param <T> the return type of the <code>fn</code> function.
     * @param existWebServer the eXist-db Web Server.
     * @param fn the function which accepts the HTTP Executor.
     *
     * @return the result of the <code>fn</code> function.
     *
     * @throws IOException if an I/O error occurs
     */
    protected static <T> T withHttpExecutor(final ExistWebServer existWebServer, final FunctionE<Executor, T, IOException> fn) throws IOException {
        return withHttpClient(client -> {
            final Executor executor = Executor
                    .newInstance(client)
                    .auth(TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                    .authPreemptive(new HttpHost("localhost", existWebServer.getPort()));
            return fn.apply(executor);
        });
    }
}
