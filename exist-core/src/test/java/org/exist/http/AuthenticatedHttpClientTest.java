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

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;

/**
 * Regression test for preemptive HTTP Basic authentication against the eXist-db REST end-point.
 *
 * <p>Credentials must reliably attach to requests routed through Jetty's {@code /exist/...} context
 * path. {@link AbstractHttpTest#authenticatedRequest} sets a preemptive {@code Authorization}
 * request header; this test guards that an authenticated REST request still succeeds.</p>
 */
public class AuthenticatedHttpClientTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void authenticatedRestRequestSucceeds() throws IOException {
        final String url = getRestUri(existWebServer) + "/db/";
        final HttpClient client = newHttpClient();
        final HttpRequest request = authenticatedRequest(URI.create(url), TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD)
                .GET()
                .build();
        assertEquals(HTTP_OK, executeForStatus(client, request));
    }
}
