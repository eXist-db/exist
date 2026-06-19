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
package org.exist.xquery.modules.persistentlogin;

import org.exist.TestUtils;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.junit.Assert.assertEquals;

/**
 * Integration test: requires ExistWebServer (Jetty) with XML-RPC and REST.
 * Runs in Failsafe phase.
 */
public class LoginModuleIT {

    private static String XQUERY = "import module namespace login=\"http://exist-db.org/xquery/login\" " +
            "at \"resource:org/exist/xquery/modules/persistentlogin/login.xql\";" +
            "login:set-user('org.exist.login', (), false())," +
            "sm:id()/(descendant::sm:effective,descendant::sm:real)[1]/sm:username/string()";

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true);

    private final static String XQUERY_FILENAME = "test-login.xql";

    private static Collection root;
    private static HttpClient client;

    @BeforeClass
    public static void beforeClass() throws XMLDBException {
        final int port = existWebServer.getPort();
        final String uri = "xmldb:exist://localhost:" + port + "/xmlrpc" + XmldbURI.ROOT_COLLECTION;
        XMLDBException lastException = null;
        for (int i = 0; i < 20; i++) {
            try {
                root = DatabaseManager.getCollection(uri, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
                lastException = null;
                break;
            } catch (final XMLDBException e) {
                lastException = e;
                if (i < 19) {
                    try {
                        Thread.sleep(500);
                    } catch (final InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError("Interrupted while waiting for XML-RPC", ie);
                    }
                }
            }
        }
        if (root == null) {
            throw new AssertionError("Failed to connect to XML-RPC: "
                    + (lastException != null ? lastException.getMessage() : "unknown"));
        }
        final BinaryResource res = root.createResource(XQUERY_FILENAME, BinaryResource.class);
        ((EXistResource) res).setMimeType("application/xquery");
        res.setContent(XQUERY);
        root.storeResource(res);
        final UserManagementService ums = root.getService(UserManagementService.class);
        ums.chmod(res, 0777);

        // an in-memory cookie store keeps the login session across requests
        client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (root != null) {
            final org.xmldb.api.base.Resource res = root.getResource(XQUERY_FILENAME);
            if (res != null) {
                root.removeResource(res);
            }
        }
    }

    @Test
    public void loginAndLogout() throws IOException {
        // not logged in
        doGet(null, TestUtils.GUEST_DB_USER);

        // log in as admin
        doGet("user=" + TestUtils.ADMIN_DB_USER + "&password=" + TestUtils.ADMIN_DB_PWD + "&duration=P1D", TestUtils.ADMIN_DB_USER);

        // second request should stay logged in
        doGet(null, TestUtils.ADMIN_DB_USER);

        // log off returns to guest user
        doGet("logout=true", TestUtils.GUEST_DB_USER);
    }

    private void doGet(@Nullable String params, String expected) throws IOException {
        final String url = "http://localhost:" + existWebServer.getPort() + "/rest" + XmldbURI.ROOT_COLLECTION + '/' + XQUERY_FILENAME +
                (params == null ? "" : "?" + params);
        final HttpRequest request = HttpRequest.newBuilder(URI.create(url)).GET().build();
        final HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while awaiting HTTP response", e);
        }
        final String responseBody = response.body();
        assertEquals(responseBody, HTTP_OK, response.statusCode());
        assertEquals(expected, responseBody);
    }

}
