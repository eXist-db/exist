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
package org.exist.http.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.exist.collections.Collection;
import org.exist.security.PermissionFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for {@link XQueryServlet#process}: pins the observable behaviour of each
 * branch (source resolution, request-attribute options, error reporting, content type, result
 * delivery) so that the method can be restructured without changing what clients see.
 */
class XQueryServletProcessTest {

    @RegisterExtension
    static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final String CONTEXT = "/exist";
    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/apps/xqueryservlet-process-test");
    private static final XmldbURI STORED_QUERY = TEST_COLLECTION.append("stored.xql");

    private static final String SERIALIZATION_NS =
            "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";\n";

    @BeforeAll
    static void storeQuery() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
                broker.storeDocument(transaction, STORED_QUERY.lastSegment(),
                        new StringInputSource("<stored/>".getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
            }
            PermissionFactory.chmod_str(broker, transaction, STORED_QUERY, Optional.of("rwxr-xr-x"), Optional.empty());
            transaction.commit();
        }
    }

    @Test
    void sourceAttributeStringIsExecuted() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>"));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
        assertEquals("no-cache", response.getHeader("pragma"));
        assertEquals("no-cache", response.getHeader("Cache-Control"));
    }

    @Test
    void sourceAttributeItemIsExecuted() throws Exception {
        final StubHttpServletRequest request = request()
                .attribute(XQueryServlet.ATTR_XQUERY_SOURCE, new StringValue("<item/>"));

        final StubHttpServletResponse response = process(request);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getBodyAsString().contains("<item/>"), response.getBodyAsString());
    }

    @Test
    void urlAttributeExecutesStoredQuery() throws Exception {
        final StubHttpServletRequest request = request()
                .attribute(XQueryServlet.ATTR_XQUERY_URL, "xmldb:exist://" + STORED_QUERY);

        final StubHttpServletResponse response = process(request);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<stored/>"), response.getBodyAsString());
    }

    @Test
    void urlAttributeToMissingQueryFails() throws Exception {
        final StubHttpServletRequest request = request()
                .attribute(XQueryServlet.ATTR_XQUERY_URL, "xmldb:exist://" + TEST_COLLECTION.append("missing.xql"));

        final StubHttpServletResponse response = process(request);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("Source not found"), response.getBodyAsString());
    }

    @Test
    void missingSourceIsNotFound() throws Exception {
        final StubHttpServletResponse response = process(
                new StubHttpServletRequest(CONTEXT, CONTEXT + "/apps/does-not-exist.xql"));

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
        assertTrue(response.getBodyAsString().contains("Cannot read source file"), response.getBodyAsString());
    }

    @Test
    void storedQueryFoundThroughDatabaseFallback() throws Exception {
        final StubHttpServletResponse response = process(new StubHttpServletRequest(CONTEXT, CONTEXT + STORED_QUERY));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<stored/>"), response.getBodyAsString());
    }

    @Test
    void badTimeoutOptionIsReported() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>")
                .attribute(XQueryServlet.ATTR_TIMEOUT, "abc"));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getBodyAsString().contains("Bad timeout option: abc"), response.getBodyAsString());
    }

    @Test
    void badMaxNodesOptionIsReported() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>")
                .attribute(XQueryServlet.ATTR_MAX_NODES, "abc"));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getBodyAsString().contains("Bad max-nodes option: abc"), response.getBodyAsString());
    }

    @Test
    void validWatchdogOptionsAreAccepted() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>")
                .attribute(XQueryServlet.ATTR_TIMEOUT, "60000")
                .attribute(XQueryServlet.ATTR_MAX_NODES, "1000"));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
    }

    @Test
    void runtimeErrorRendersErrorPage() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("error(xs:QName('local:boom'), 'kaboom')"));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getBodyAsString().contains("Error found"), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("kaboom"), response.getBodyAsString());
    }

    @Test
    void reportErrorsWritesErrorElement() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("error(xs:QName('local:boom'), 'kaboom')")
                .attribute(XQueryServlet.ATTR_XQUERY_REPORT_ERRORS, "yes"));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertTrue(response.getBodyAsString().startsWith("<error>"), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("kaboom"), response.getBodyAsString());
    }

    @Test
    void compileErrorIsReported() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("1 +"));

        assertEquals(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, response.getStatus());
        assertTrue(response.getBodyAsString().contains("Cannot compile xquery"), response.getBodyAsString());
    }

    @Test
    void resultIsStoredInRequestAttribute() throws Exception {
        final StubHttpServletRequest request = sourceRequest("<ok/>")
                .attribute(XQueryServlet.ATTR_XQUERY_ATTRIBUTE, "result");

        final StubHttpServletResponse response = process(request);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        assertInstanceOf(Sequence.class, request.getAttribute("result"));
        assertFalse(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
    }

    @Test
    void defaultContentTypeIsHtmlWithCharset() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>"));

        assertEquals("text/html; charset=UTF-8", response.getContentType());
    }

    @Test
    void textMediaTypeGetsCharset() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest(SERIALIZATION_NS
                + "declare option output:media-type \"text/xml\";\n<ok/>"));

        assertEquals("text/xml; charset=UTF-8", response.getContentType());
        assertEquals("UTF-8", response.getCharacterEncoding());
    }

    @Test
    void binaryMediaTypeHasNoCharset() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest(SERIALIZATION_NS
                + "declare option output:method \"text\";\n"
                + "declare option output:media-type \"application/octet-stream\";\n'bytes'"));

        assertEquals("application/octet-stream", response.getContentType());
        assertNull(response.getCharacterEncoding());
    }

    @Test
    void sourceViewNotAllowedByDescriptorIsForbidden() throws Exception {
        // Descriptor.getDescriptorSingleton() is never null; without a descriptor.xml no path is allow-listed
        final StubHttpServletResponse response = process(sourceRequest("<ok/>").parameter("_source", "yes"));

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getErrorMessage().contains("Must be explicitly defined in descriptor.xml"),
                response.getErrorMessage());
        assertFalse(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
    }

    @Test
    void sourceParameterOtherThanYesExecutesQuery() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>").parameter("_source", "no"));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
    }

    @Test
    void sourceParameterIgnoredForPost() throws Exception {
        final StubHttpServletResponse response = process(sourceRequest("<ok/>").method("POST").parameter("_source", "yes"));

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<ok/>"), response.getBodyAsString());
    }

    @Test
    void postIsExecuted() throws Exception {
        final StubHttpServletResponse response = new StubHttpServletResponse();
        newInitializedServlet().doPost(sourceRequest("<posted/>").method("POST"), response);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<posted/>"), response.getBodyAsString());
    }

    @Test
    void putIsExecuted() throws Exception {
        final StubHttpServletResponse response = new StubHttpServletResponse();
        newInitializedServlet().doPut(sourceRequest("<put/>").method("PUT"), response);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus(), response.getBodyAsString());
        assertTrue(response.getBodyAsString().contains("<put/>"), response.getBodyAsString());
    }

    private static StubHttpServletRequest request() {
        return new StubHttpServletRequest(CONTEXT, CONTEXT + "/apps/test/inline.xql");
    }

    private static StubHttpServletRequest sourceRequest(final String query) {
        return request().attribute(XQueryServlet.ATTR_XQUERY_SOURCE, query);
    }

    private static StubHttpServletResponse process(final StubHttpServletRequest request)
            throws ServletException, IOException {
        final StubHttpServletResponse response = new StubHttpServletResponse();
        newInitializedServlet().process(request, response);
        return response;
    }

    private static XQueryServlet newInitializedServlet() throws ServletException {
        final XQueryServlet servlet = new XQueryServlet();
        servlet.init(StubServletConfig.create());
        return servlet;
    }
}
