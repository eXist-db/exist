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
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpStatus;
import org.exist.http.AbstractHttpTest;
import org.exist.test.ExistWebServer;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.io.IOException;

import static com.evolvedbinary.j8fu.tuple.Tuple.Tuple;
import static org.exist.http.urlrewrite.XQueryURLRewrite.XQUERY_CONTROLLER_FILENAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class URLRewritingTest extends AbstractHttpTest {

    private static final XmldbURI TEST_COLLECTION_NAME = XmldbURI.create("controller-test");
    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/apps").append(TEST_COLLECTION_NAME);

    private static final String TEST_CONTROLLER = "xquery version \"3.1\";\n<controller>{fn:current-dateTime()}</controller>";

    @ClassRule
    public static final ExistWebServer existWebServer = new ExistWebServer(true, false, true, true, false);

    @Test
    public void findsParentController() throws IOException {
        final XmldbURI nestedCollectionName = XmldbURI.create("nested");
        final XmldbURI docName = XmldbURI.create("test.xml");
        final String testDocument = "<hello>world</hello>";

        final String storeDocUri = getRestUri(existWebServer) + TEST_COLLECTION.append(nestedCollectionName).append(docName);
        final Request storeRequest = Request
                .put(storeDocUri)
                .bodyString(testDocument, ContentType.APPLICATION_XML);
        final int storeResponseStatusCode = withHttpExecutor(existWebServer, executor -> executeForStatus(executor, storeRequest));
        assertEquals(HttpStatus.SC_CREATED, storeResponseStatusCode);

        final String retrieveDocUri = getAppsUri(existWebServer) + "/" + TEST_COLLECTION_NAME.append(nestedCollectionName).append(docName);
        final Request retrieveRequest = Request
                .get(retrieveDocUri);
        final Tuple2<Integer, String> retrieveResponseStatusCodeAndBody = withHttpExecutor(existWebServer, executor -> {
            final HttpResponseResult r = executeForStatusAndBody(executor, retrieveRequest);
            return Tuple(r.statusCode(), r.body());
        });
        assertEquals(HttpStatus.SC_OK, retrieveResponseStatusCodeAndBody._1.intValue());
        assertTrue(retrieveResponseStatusCodeAndBody._2.matches("<controller>.+</controller>"));
    }

    @BeforeClass
    public static void setup() throws IOException {
        final Request request = Request
                .put(getRestUri(existWebServer) + TEST_COLLECTION + "/" + XQUERY_CONTROLLER_FILENAME)
                .bodyString(TEST_CONTROLLER, ContentType.create("application/xquery"));

        final int statusCode = withHttpExecutor(existWebServer, executor -> executeForStatus(executor, request));

        assertEquals(HttpStatus.SC_CREATED, statusCode);
    }

    @AfterClass
    public static void cleanup() throws IOException {
        final Request request = Request
                .delete(getRestUri(existWebServer) + TEST_COLLECTION);

        final int statusCode = withHttpExecutor(existWebServer, executor -> executeForStatus(executor, request));

        assertEquals(HttpStatus.SC_OK, statusCode);
    }
}
