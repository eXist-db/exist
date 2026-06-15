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
package org.exist.xquery.functions.xmldb;

import org.exist.TestUtils;
import org.exist.http.AbstractHttpTest.HttpResponseResult;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.xmldb.UserManagementService;
import org.junit.Before;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.net.CookieManager;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.AbstractHttpTest.assertRequestResponse;
import static org.exist.http.AbstractHttpTest.executeForStatusAndBody;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class XMLDBAuthenticateTest extends AbstractXMLDBTest{

    private static final String USER1_UID = "user1";
    private static final String USER1_PWD = "user1";

    @Before
    public void beforeClass() throws XMLDBException {
        final Collection root = DatabaseManager.getCollection("xmldb:exist://localhost:" + existWebServer.getPort() + "/xmlrpc/db", TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
        final UserManagementService ums = root.getService(UserManagementService.class);

        final GroupAider group1 = new GroupAider(USER1_UID);
        ums.addGroup(group1);

        final UserAider user1 = new UserAider(USER1_UID, group1);
        user1.setPassword(USER1_PWD);
        ums.addAccount(user1);
    }

    @Test
    public void loginExplicitSessionCreation() throws IOException {
        final HttpClient client = newSessionClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HttpURLConnection.HTTP_OK, "");

        // login to the database
        final HttpRequest requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "')");
        assertRequestResponse(client, requestGetAttr, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>user1</sm:username>
                        <sm:groups>
                            <sm:group>user1</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginImplicitSessionCreateSessionFalse() throws IOException {
        final HttpClient client = newSessionClient();

        // login to the database
        final HttpRequest requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        assertRequestResponse(client, requestGetAttr, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>guest</sm:username>
                        <sm:groups>
                            <sm:group>guest</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginImplicitSessionCreateSessionTrue() throws IOException {
        final HttpClient client = newSessionClient();

        // login to the database
        final HttpRequest requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        assertRequestResponse(client, requestGetAttr, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>user1</sm:username>
                        <sm:groups>
                            <sm:group>user1</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionFalseSeparateHttpCalls() throws IOException {
        final HttpClient client = newSessionClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HttpURLConnection.HTTP_OK, "");

        // invalidate the session
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate()");
        assertRequestResponse(client, requestInvalidateSession, HttpURLConnection.HTTP_OK, "");

        // login to the database
        final HttpRequest requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        assertRequestResponse(client, requestGetAttr, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>guest</sm:username>
                        <sm:groups>
                            <sm:group>guest</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionTrueSeparateHttpCalls() throws IOException {
        final HttpClient client = newSessionClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HttpURLConnection.HTTP_OK, "");

        // invalidate the session
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate()");
        assertRequestResponse(client, requestInvalidateSession, HttpURLConnection.HTTP_OK, "");

        // login to the database
        final HttpRequest requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        assertRequestResponse(client, requestGetAttr, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>user1</sm:username>
                        <sm:groups>
                            <sm:group>user1</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionFalseSameHttpCall() throws IOException {
        final HttpClient client = newSessionClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HttpURLConnection.HTTP_OK, "");

        // invalidate the session and login to the database
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate(), xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        assertRequestResponse(client, requestInvalidateSession, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>guest</sm:username>
                        <sm:groups>
                            <sm:group>guest</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionTrueSameHttpCall() throws IOException {
        final HttpClient client = newSessionClient();

        // explicitly create a new session
        final HttpRequest requestCreateSession = xqueryRequest("session:create()");
        assertRequestResponse(client, requestCreateSession, HttpURLConnection.HTTP_OK, "");

        // invalidate the session and login to the database
        final HttpRequest requestInvalidateSession = xqueryRequest("session:invalidate(), xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        assertRequestResponse(client, requestInvalidateSession, HttpURLConnection.HTTP_OK, "true");

        // get the identity of the current user
        final HttpRequest requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponseResult result = executeForStatusAndBody(client, requestSetAttr1);
        assertEquals(HttpURLConnection.HTTP_OK, result.statusCode());

        final Source expected = Input.fromString(
                """
                <sm:id xmlns:sm="http://exist-db.org/xquery/securitymanager">
                    <sm:real>
                        <sm:username>user1</sm:username>
                        <sm:groups>
                            <sm:group>user1</sm:group>
                        </sm:groups>
                    </sm:real>
                </sm:id>""").build();
        final Source actual = Input.fromString(result.body()).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    /**
     * Create an HTTP client that retains cookies across requests, so the HTTP session
     * (JSESSIONID) is preserved across the sequential calls within a single test.
     *
     * @return a new session-aware {@link HttpClient}.
     */
    private static HttpClient newSessionClient() {
        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .cookieHandler(new CookieManager())
                .build();
    }

    public HttpRequest xqueryRequest(final String xquery) {
        return HttpRequest.newBuilder(URI.create(getCollectionRootUri() + "/?_query=" + URLEncoder.encode(xquery, UTF_8) + "&_wrap=no"))
                .GET()
                .build();
    }
}
