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

package org.exist.http.urlrewrite;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.urlrewrite.XQueryURLRewrite.LEGACY_XQUERY_CONTROLLER_FILENAME;
import static org.exist.http.urlrewrite.XQueryURLRewrite.XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class ControllerTest extends AbstractHttpTest {

    private static final String CONTROLLER_XQUERY = "<controller>xq</controller>";
    private static final String LEGACY_CONTROLLER_XQUERY = "<controller>xql</controller>";
    private static final String TEST_DOCUMENT_NAME = "test.xml";

    @Rule
    public final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void findsLegacyController() throws IOException {
        final String testCollectionName = "test-finds-legacy-controller";

        // store the legacy controller
        store(testCollectionName, "application/xquery", LEGACY_XQUERY_CONTROLLER_FILENAME, LEGACY_CONTROLLER_XQUERY);

        // make a request and see if the legacy controller responds
        final Tuple2<Integer, String> responseCodeAndBody = get(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpStatus.SC_OK, (int)responseCodeAndBody._1);
        assertEquals(LEGACY_CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    @Test
    public void findsController() throws IOException {
        final String testCollectionName = "test-finds-controller";

        // store the controller
        store(testCollectionName, "application/xquery", XQUERY_CONTROLLER_FILENAME, CONTROLLER_XQUERY);

        // make a request and see if the controller responds
        final Tuple2<Integer, String> responseCodeAndBody = get(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpStatus.SC_OK, (int)responseCodeAndBody._1);
        assertEquals(CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    @Test
    public void prefersNonLegacyController() throws IOException {
        final String testCollectionName = "test-prefers-non-legacy-controller";

        // store the controller and the legacy controller
        store(testCollectionName, "application/xquery", XQUERY_CONTROLLER_FILENAME, CONTROLLER_XQUERY);
        store(testCollectionName, "application/xquery", LEGACY_XQUERY_CONTROLLER_FILENAME, LEGACY_CONTROLLER_XQUERY);

        // make a request and see if the (non-legacy) controller responds
        final Tuple2<Integer, String> responseCodeAndBody = get(testCollectionName, TEST_DOCUMENT_NAME);
        assertEquals(HttpStatus.SC_OK, (int)responseCodeAndBody._1);
        assertEquals(CONTROLLER_XQUERY, responseCodeAndBody._2);
    }

    private void store(final String testCollectionName, final String documentMediaType, final String documentName, final String documentContent) throws IOException {
        final Request request = Request
                .Put(getRestUri(existWebServer) + "/db/apps/" + testCollectionName + "/" + documentName)
                .bodyString(documentContent, ContentType.create(documentMediaType));
        int statusCode = withHttpExecutor(existWebServer, executor ->
                executor.execute(request).returnResponse().getStatusLine().getStatusCode()
        );
        assertEquals(HttpStatus.SC_CREATED, statusCode);
    }

    private Tuple2<Integer, String> get(final String testCollectionName, final String documentName) throws IOException {
        final Request request = Request
                .Get(getAppsUri(existWebServer) + "/" + testCollectionName + "/" + documentName);
        final Tuple2<Integer, String> responseCodeAndBody = withHttpExecutor(existWebServer, executor -> {
            final HttpResponse response = executor.execute(request).returnResponse();
            final int sc = response.getStatusLine().getStatusCode();
            try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
                response.getEntity().writeTo(baos);
                return Tuple(sc, baos.toString(UTF_8));
            }
        });
        return responseCodeAndBody;
    }
}
