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

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.exist.TestUtils;
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
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
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
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // login to the database
        final Request requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "')");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("true", readEntityAsString(getResponse1.getEntity()));

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                "    <sm:real>\n" +
                "        <sm:username>user1</sm:username>\n" +
                "        <sm:groups>\n" +
                "            <sm:group>user1</sm:group>\n" +
                "        </sm:groups>\n" +
                "    </sm:real>\n" +
                "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginImplicitSessionCreateSessionFalse() throws IOException {
        // login to the database
        final Request requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("true", readEntityAsString(getResponse1.getEntity()));

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>guest</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>guest</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginImplicitSessionCreateSessionTrue() throws IOException {
        // login to the database
        final Request requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("true", readEntityAsString(getResponse1.getEntity()));

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>user1</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>user1</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionFalseSeparateHttpCalls() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session
        final Request requestInvalidateSession = xqueryRequest("session:invalidate()");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(invalidateSessionResponse.getEntity()));

        // login to the database
        final Request requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("true", readEntityAsString(getResponse1.getEntity()));

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>guest</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>guest</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionTrueSeparateHttpCalls() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session
        final Request requestInvalidateSession = xqueryRequest("session:invalidate()");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(invalidateSessionResponse.getEntity()));

        // login to the database
        final Request requestGetAttr = xqueryRequest("xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("true", readEntityAsString(getResponse1.getEntity()));

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>user1</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>user1</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionFalseSameHttpCall() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session and login to the database
        final Request requestInvalidateSession = xqueryRequest("session:invalidate(), xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', false())");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        final String responseBody = readEntityAsString(invalidateSessionResponse.getEntity());
        assertEquals(responseBody, HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("true", responseBody);

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>guest</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>guest</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    @Test
    public void loginOnInvalidatedSessionCreateSessionTrueSameHttpCall() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session and login to the database
        final Request requestInvalidateSession = xqueryRequest("session:invalidate(), xmldb:login('/db', '" + USER1_UID + "', '" + USER1_PWD + "', true())");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        final String responseBody = readEntityAsString(invalidateSessionResponse.getEntity());
        assertEquals(responseBody, HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("true", responseBody);

        // get the identity of the current user
        final Request requestSetAttr1 = xqueryRequest("sm:id()");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());

        final Source expected = Input.fromString(
                "<sm:id xmlns:sm=\"http://exist-db.org/xquery/securitymanager\">\n" +
                        "    <sm:real>\n" +
                        "        <sm:username>user1</sm:username>\n" +
                        "        <sm:groups>\n" +
                        "            <sm:group>user1</sm:group>\n" +
                        "        </sm:groups>\n" +
                        "    </sm:real>\n" +
                        "</sm:id>").build();
        final Source actual = Input.fromString(readEntityAsString(setResponse1.getEntity())).build();
        final Diff diff = DiffBuilder.compare(expected)
                .withTest(actual)
                .checkForSimilar()
                .build();
        assertFalse(diff.toString(), diff.hasDifferences());
    }

    public Request xqueryRequest(final String xquery) throws UnsupportedEncodingException {
        return Request.Get(getCollectionRootUri() + "/?_query=" + URLEncoder.encode(xquery, UTF_8) + "&_wrap=no");
    }

    private static String readEntityAsString(final HttpEntity entity) throws IOException {
        return new String(readEntity(entity), UTF_8);
    }

    private static byte[] readEntity(final HttpEntity entity) throws IOException {
        try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream()) {
            entity.writeTo(os);
            return os.toByteArray();
        }
    }
}
