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

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.exist.util.UUIDGenerator;
import org.junit.Test;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;

public class AttributeTest extends AbstractSessionTest {

    @Test
    public void getSetAttributeExplicitSessionCreation() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final Request requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(getResponse1.getEntity()));

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final Request requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(setResponse1.getEntity()));

        // get the value of the attribute named "attr1", and check its value is the UUID
        final HttpResponse getResponse2 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse2.getStatusLine().getStatusCode());
        assertEquals(attr1Value, readEntityAsString(getResponse2.getEntity()));
    }

    @Test
    public void getSetAttributeImplicitSessionCreation() throws IOException {
        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final Request requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        final HttpResponse getResponse1 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(getResponse1.getEntity()));

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final Request requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(setResponse1.getEntity()));

        // get the value of the attribute named "attr1", and check its value is the UUID
        final HttpResponse getResponse2 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse2.getStatusLine().getStatusCode());
        assertEquals(attr1Value, readEntityAsString(getResponse2.getEntity()));
    }

    @Test
    public void getAttributeOnInvalidatedSessionSeparateHttpCalls() throws IOException {
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

        // get the value of the attribute named "attr1", and check its value is the empty sequence
        final Request requestGetAttr1 = xqueryRequest("session:get-attribute('attr1')");
        final HttpResponse getResponse1 = requestGetAttr1
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse1.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(getResponse1.getEntity()));
    }

    @Test
    public void getAttributeOnInvalidatedSessionSameHttpCall() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session and call get-attribute
        final Request requestInvalidateSession = xqueryRequest("session:invalidate(), session:get-attribute('attr1')");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(invalidateSessionResponse.getEntity()));
    }

    @Test
    public void setAttributeOnInvalidatedSessionSeparateHttpCalls() throws IOException {
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

        // set the value of the attribute named "attr1" to a random UUID
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final Request requestSetAttr1 = xqueryRequest("session:set-attribute('attr1', '" + attr1Value + "')");
        final HttpResponse setResponse1 = requestSetAttr1
                .execute()
                .returnResponse();
        final String responseBody = readEntityAsString(setResponse1.getEntity());
        assertEquals(HttpStatus.SC_OK, setResponse1.getStatusLine().getStatusCode());
        assertEquals("", responseBody);

        // get the value of the attribute named "attr1", and check its value is the UUID
        final Request requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        final HttpResponse getResponse2 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse2.getStatusLine().getStatusCode());
        assertEquals(attr1Value, readEntityAsString(getResponse2.getEntity()));
    }

    @Test
    public void setAttributeOnInvalidatedSessionSameHttpCall() throws IOException {
        // explicitly create a new session
        final Request requestCreateSession = xqueryRequest("session:create()");
        final HttpResponse createSessionResponse = requestCreateSession
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, createSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", readEntityAsString(createSessionResponse.getEntity()));

        // invalidate the session and call set-attribute
        final String attr1Value = UUIDGenerator.getUUIDversion4();
        final Request requestInvalidateSession = xqueryRequest("session:invalidate(), session:set-attribute('attr1', '" + attr1Value + "')");
        final HttpResponse invalidateSessionResponse = requestInvalidateSession
                .execute()
                .returnResponse();
        final String responseBody = readEntityAsString(invalidateSessionResponse.getEntity());
        assertEquals(HttpStatus.SC_OK, invalidateSessionResponse.getStatusLine().getStatusCode());
        assertEquals("", responseBody);

        // get the value of the attribute named "attr1", and check its value is the UUID
        final Request requestGetAttr = xqueryRequest("session:get-attribute('attr1')");
        final HttpResponse getResponse2 = requestGetAttr
                .execute()
                .returnResponse();
        assertEquals(HttpStatus.SC_OK, getResponse2.getStatusLine().getStatusCode());
        assertEquals(attr1Value, readEntityAsString(getResponse2.getEntity()));
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
