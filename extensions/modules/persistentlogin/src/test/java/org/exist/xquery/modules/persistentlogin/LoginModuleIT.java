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

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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

import static org.apache.hc.core5.http.HttpStatus.SC_OK;
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
    private static CloseableHttpClient client;
    private static BasicCookieStore cookieStore;
    private static HttpClientContext httpContext;

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

        cookieStore = new BasicCookieStore();
        httpContext = HttpClientContext.create();
        httpContext.setCookieStore(cookieStore);
        client = HttpClients.custom().build();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        if (client != null) {
            client.close();
        }
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
        final HttpGet httpGet = new HttpGet("http://localhost:" + existWebServer.getPort() + "/rest" + XmldbURI.ROOT_COLLECTION + '/' + XQUERY_FILENAME +
                (params == null ? "" : "?" + params));
        final ClassicHttpResponse response = client.executeOpen(null, httpGet, httpContext);
        final HttpEntity entity = response.getEntity();
        final String responseBody;
        try {
            responseBody = EntityUtils.toString(entity);
        } catch (final ParseException e) {
            throw new IOException(e);
        }
        assertEquals(responseBody, SC_OK, response.getCode());
        assertEquals(expected, responseBody);
    }

}
