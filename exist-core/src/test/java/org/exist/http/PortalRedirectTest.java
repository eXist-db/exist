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

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Distribution-mode portal at {@code /} — landing page and redirect target to {@code /exist}.
 */
public class PortalRedirectTest extends AbstractHttpTest {

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void portalRootServesLandingPageWithExistRedirect() throws IOException {
        final Request request = Request.get(portalUri(existWebServer));
        final ClassicHttpResponse response = withHttpExecutor(existWebServer,
                executor -> (ClassicHttpResponse) executor.execute(request).returnResponse());

        assertEquals(HttpStatus.SC_OK, response.getCode());

        final String body = readResponseBody(response);
        assertTrue("Expected portal title", body.contains("Open Source Native XML Database"));
        assertTrue("Expected JS redirect to /exist", body.contains("window.location.replace(\"/exist\")"));
        assertTrue("Expected noscript fallback link to /exist", body.contains("href=\"/exist\""));
    }

    private static String portalUri(final ExistWebServer existWebServer) {
        return "http://localhost:" + existWebServer.getPort() + "/";
    }
}
