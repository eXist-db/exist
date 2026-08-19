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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.collections.Collection;
import org.exist.security.PermissionFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;

import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Regression test for <a href="https://github.com/eXist-db/exist/issues/6615">eXist-db/exist#6615</a>.
 * <p>
 * The Servlet API explicitly allows {@link HttpServletRequest#getPathTranslated()} and
 * {@link ServletContext#getRealPath(String)} to return {@code null} when the container cannot map
 * the request to a location on disk -- which is exactly what happens for a request that is forwarded
 * (e.g. via {@code <exist:forward>}) to a resource that only exists in the database. Before the fix,
 * {@link XQueryServlet#process(HttpServletRequest, HttpServletResponse)} passed that {@code null}
 * straight into {@link java.nio.file.Path#of(String, String...)}, which throws an unhandled
 * {@link NullPointerException} that surfaces to the client as a raw HTTP 500.
 */
public class XQueryServletNullPathTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/apps/xqueryservlet-null-path-test");
    private static final XmldbURI TEST_RESOURCE = TEST_COLLECTION.append("optimize.xql");
    private static final String TEST_QUERY = "xquery version \"3.1\"; <ok/>";

    /** Stores a resource ONLY in the database -- there is deliberately no file on disk for it. */
    @BeforeClass
    public static void storeDatabaseOnlyResource() throws Exception {
        final BrokerPool pool = existEmbeddedServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            try (final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION)) {
                collection.getPermissions().setMode("rwxr-xr-x");
                broker.saveCollection(transaction, collection);
                broker.storeDocument(transaction, TEST_RESOURCE.lastSegment(),
                        new StringInputSource(TEST_QUERY.getBytes(UTF_8)), MimeType.XQUERY_TYPE, collection);
            }
            PermissionFactory.chmod_str(broker, transaction, TEST_RESOURCE, Optional.of("rwxr-xr-x"), Optional.empty());

            transaction.commit();
        }
    }

    @Test
    public void nullPathTranslatedFallsBackToDatabaseResourceAndExecutesIt() throws Exception {
        final XQueryServlet servlet = newInitializedServlet();

        // Simulates a request forwarded to the database-only resource stored above: as in the
        // previous test, neither getPathTranslated() nor getRealPath() can resolve a disk path.
        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathTranslated()).andReturn(null).anyTimes();
        expect(request.getRequestURI()).andReturn("/exist" + TEST_RESOURCE).anyTimes();
        expect(request.getContextPath()).andReturn("/exist").anyTimes();
        expect(request.getMethod()).andReturn("GET").anyTimes();
        expect(request.getParameterMap()).andReturn(java.util.Collections.emptyMap()).anyTimes();
        replay(request);

        final RecordingResponse response = new RecordingResponse();

        servlet.process(request, response);

        assertEquals("body: " + response.getBodyAsString(), HttpServletResponse.SC_OK, response.getStatus());
        assertTrue("expected the database-resident query to have executed: " + response.getBodyAsString(),
                response.getBodyAsString().contains("<ok/>"));
    }

    @Test
    public void nullPathTranslatedReturnsCleanNotFoundInsteadOfNPE() throws Exception {
        final XQueryServlet servlet = newInitializedServlet();

        // Simulates a request forwarded to a virtual/database-only path: neither
        // getPathTranslated() nor (later) ServletContext#getRealPath(String) can resolve to a real
        // file on disk, so both return null -- as the Servlet API permits.
        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathTranslated()).andReturn(null).anyTimes();
        expect(request.getRequestURI()).andReturn("/exist/apps/does-not-exist.xql").anyTimes();
        expect(request.getContextPath()).andReturn("/exist").anyTimes();
        replay(request);

        final RecordingResponse response = new RecordingResponse();

        // Before the fix: throws NullPointerException from Path.of(null) at XQueryServlet.process().
        servlet.process(request, response);

        assertEquals(HttpServletResponse.SC_NOT_FOUND, response.getStatus());
    }

    private static XQueryServlet newInitializedServlet() throws ServletException {
        final ServletContext mockServletContext = createNiceMock(ServletContext.class);
        // Mirrors a deployment where the webapp is not exploded on disk (e.g. served from a
        // packed/embedded context): getRealPath() legitimately returns null for any input.
        expect(mockServletContext.getRealPath(org.easymock.EasyMock.anyString())).andReturn(null).anyTimes();
        replay(mockServletContext);

        final ServletConfig mockServletConfig = createNiceMock(ServletConfig.class);
        expect(mockServletConfig.getServletContext()).andReturn(mockServletContext).anyTimes();
        replay(mockServletConfig);

        final XQueryServlet servlet = new XQueryServlet();
        servlet.init(mockServletConfig);
        return servlet;
    }

    /**
     * A minimal {@link HttpServletResponse} that records the status code and captures the body,
     * delegating everything else to a nice mock.
     */
    private static class RecordingResponse extends HttpServletResponseWrapper {
        private final UnsynchronizedByteArrayOutputStream body = new UnsynchronizedByteArrayOutputStream();
        private int status = HttpServletResponse.SC_OK;

        RecordingResponse() {
            super(createNiceReplayedResponse());
        }

        private static HttpServletResponse createNiceReplayedResponse() {
            final HttpServletResponse mock = createNiceMock(HttpServletResponse.class);
            replay(mock);
            return mock;
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final WriteListener writeListener) {
                    // non-blocking I/O is not exercised by this test; nothing to wire up here
                }

                @Override
                public void write(final int b) {
                    body.write(b);
                }
            };
        }

        @Override
        public void setStatus(final int sc) {
            this.status = sc;
        }

        @Override
        public int getStatus() {
            return status;
        }

        String getBodyAsString() {
            return body.toString(UTF_8);
        }
    }
}
