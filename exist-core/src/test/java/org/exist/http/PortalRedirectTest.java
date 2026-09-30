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

import org.exist.test.ExistWebServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import org.junit.jupiter.api.extension.RegisterExtension;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Distribution-mode portal at {@code /} — landing page and redirect target to {@code /exist}.
 */
public class PortalRedirectTest extends AbstractHttpTest {

    @RegisterExtension
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void portalRootServesLandingPageWithExistRedirect() throws IOException {
        final HttpRequest request = HttpRequest.newBuilder(URI.create(portalUri(existWebServer))).GET().build();
        final HttpResponseResult result = withHttpClient(client -> executeForStatusAndBody(client, request));

        assertEquals(HTTP_OK, result.statusCode());

        final String body = result.body();
        assertTrue(body.contains("Open Source Native XML Database"), "Expected portal title");
        assertTrue(body.contains("window.location.replace(\"/exist\")"), "Expected JS redirect to /exist");
        assertTrue(body.contains("href=\"/exist\""), "Expected noscript fallback link to /exist");
    }

    private static String portalUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort() + "/";
    }
}
