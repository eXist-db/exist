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

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;

/**
 * Regression test for HTTP Basic authentication with Apache HttpClient 5.
 *
 * <p>HC5's fluent {@link org.apache.hc.client5.http.fluent.Executor} {@code authPreemptive} and
 * {@code auth} helpers do not reliably attach credentials to requests routed through Jetty's
 * {@code /exist/...} context path. {@link AbstractHttpTest} therefore installs a preemptive
 * {@code Authorization} request interceptor on the underlying client; this test guards that
 * {@link AbstractHttpTest#createAuthenticatedExecutor} still succeeds for authenticated REST access.</p>
 */
public class AuthenticatedHttpClientTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void authenticatedRestRequestSucceeds() throws IOException {
        final String url = getRestUri(existWebServer) + "/db/";
        try (ClassicHttpResponse response = (ClassicHttpResponse) createAuthenticatedExecutor(
                existWebServer, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .execute(Request.get(url))
                .returnResponse()) {
            assertEquals(HttpStatus.SC_OK, response.getCode());
        }
    }
}
