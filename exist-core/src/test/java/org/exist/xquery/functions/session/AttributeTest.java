/*
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This file was originally ported from FusionDB to eXist-db by
 * Evolved Binary, for the benefit of the eXist-db Open Source community.
 * Only the ported code as it appears in this file, at the time that
 * it was contributed to eXist-db, was re-licensed under The GNU
 * Lesser General Public License v2.1 only for use in eXist-db.
 *
 * This license grant applies only to a snapshot of the code as it
 * appeared when ported, it does not offer or infer any rights to either
 * updates of this source code or access to the original source code.
 *
 * The GNU Lesser General Public License v2.1 only license follows.
 *
 * ---------------------------------------------------------------------
 *
 * Copyright (C) 2014, Evolved Binary Ltd
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; version 2.1.
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
package org.exist.xquery.functions.session;

import org.exist.util.UUIDGenerator;
import org.junit.Test;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.AbstractHttpTest.assertRequestResponse;

public class AttributeTest extends AbstractSessionTest {

    @Test
    public void getSetAttributeExplicitSessionCreation() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final HttpRequest requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        assertRequestResponse(client, requestGetAttr, HTTP_OK, "");

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final HttpRequest requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        assertRequestResponse(client, requestSetAttr1, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the UUID
        assertRequestResponse(client, requestGetAttr, HTTP_OK, attr1Value);
    }

    @Test
    public void getSetAttributeImplicitSessionCreation() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final HttpRequest requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        assertRequestResponse(client, requestGetAttr, HTTP_OK, "");

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final HttpRequest requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        assertRequestResponse(client, requestSetAttr1, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the UUID
        assertRequestResponse(client, requestGetAttr, HTTP_OK, attr1Value);
    }

    @Test
    public void getAttributeOnInvalidatedSessionSeparateHttpCalls() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HTTP_OK, "");

        // invalidate the session
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate()");
        assertRequestResponse(client, requestInvalidateSession, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final HttpRequest requestGetAttr1 = xqueryRequest("session:get-attribute('attr1')");
        assertRequestResponse(client, requestGetAttr1, HTTP_OK, "");
    }

    @Test
    public void getAttributeOnInvalidatedSessionSameHttpCall() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HTTP_OK, "");

        // invalidate the session and call get-attribute
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate(), session:get-attribute('attr1')");
        assertRequestResponse(client, requestInvalidateSession, HTTP_OK, "");
    }

    @Test
    public void setAttributeOnInvalidatedSessionSeparateHttpCalls() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HTTP_OK, "");

        // invalidate the session
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate()");
        assertRequestResponse(client, requestInvalidateSession, HTTP_OK, "");

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final HttpRequest requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        assertRequestResponse(client, requestSetAttr1, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the UUID
        final HttpRequest requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        assertRequestResponse(client, requestGetAttr, HTTP_OK, attr1Value);
    }

    @Test
    public void setAttributeOnInvalidatedSessionSameHttpCall() throws IOException {
        final HttpClient client = newSessionHttpClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HTTP_OK, "");

        // invalidate the session and call set-attribute
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate(), session:set-attribute('attr1', '" + attr1Value + "')");
        assertRequestResponse(client, requestInvalidateSession, HTTP_OK, "");

        // get the value of the attribute named "attr1", and check its value is the UUID
        final HttpRequest requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        assertRequestResponse(client, requestGetAttr, HTTP_OK, attr1Value);
    }

    /**
     * Create an HTTP client that follows redirects and retains cookies across requests, so that the
     * HTTP session (JSESSIONID) established by one request is carried to subsequent requests within
     * the same test. The previous Apache HttpClient fluent API used a shared default cookie store for
     * this; the JDK {@link HttpClient} requires an explicit {@link CookieManager}.
     */
    private static HttpClient newSessionHttpClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();
    }

    public HttpRequest xqueryRequest(final String xquery) {
        final URI uri = URI.create(getCollectionRootUri() + "/?_query=" + URLEncoder.encode(xquery, UTF_8) + "&_wrap=no");
        return HttpRequest.newBuilder(uri).GET().build();
    }
}
