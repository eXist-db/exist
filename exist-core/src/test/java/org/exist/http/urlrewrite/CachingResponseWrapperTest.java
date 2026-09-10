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

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Direct unit tests for {@link CachingResponseWrapper} -- fast, in-process coverage of the
 * getWriter()/getOutputStream() state machine, header buffer+replay, and passthrough tunnelling
 * that every prior test of this class (URLRewriteXSLTViewPipelineTest,
 * URLRewriteContentLengthViewPipelineTest, URLRewriteResponseSetHeaderViewPipelineTest, etc.) had
 * to exercise indirectly through a full HTTP round trip against a live ExistWebServer, because
 * the class was a private nested type with no way to construct or call it directly. Now that it
 * is a top-level (if package-private) class, this is possible.
 */
public class CachingResponseWrapperTest {

    @Test
    public void getWriterThenGetOutputStreamThrowsIllegalStateException() throws Exception {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(new RecordingResponse(), true);
        wrapper.getWriter();

        try {
            wrapper.getOutputStream();
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException e) {
            // expected -- and specifically not IOException, see #6667
        }
    }

    @Test
    public void getOutputStreamThenGetWriterThrowsIllegalStateException() throws Exception {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(new RecordingResponse(), true);
        wrapper.getOutputStream();

        try {
            wrapper.getWriter();
            fail("Expected IllegalStateException");
        } catch (final IllegalStateException e) {
            // expected -- and specifically not IOException, see #6667
        }
    }

    @Test
    public void getWriterCalledTwiceIsIdempotent() throws Exception {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(new RecordingResponse(), true);
        final PrintWriter first = wrapper.getWriter();
        final PrintWriter second = wrapper.getWriter();

        assertSame("A repeat call to getWriter() must return the same writer, not throw",
                first, second);
    }

    @Test
    public void getOutputStreamCalledTwiceIsIdempotent() throws Exception {
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(new RecordingResponse(), true);
        final ServletOutputStream first = wrapper.getOutputStream();
        final ServletOutputStream second = wrapper.getOutputStream();

        assertSame("A repeat call to getOutputStream() must return the same stream, not throw",
                first, second);
    }

    @Test
    public void setHeaderBuffersWhileCaching() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, true);

        wrapper.setHeader("X-Foo", "bar");

        assertTrue("A buffered step's header must not reach the real response before flush()",
                real.calls.isEmpty());
    }

    @Test
    public void addHeaderBuffersWhileCaching() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, true);

        wrapper.addHeader("X-Foo", "bar");

        assertTrue("A buffered step's header must not reach the real response before flush()",
                real.calls.isEmpty());
    }

    @Test
    public void cacheFalseModeAppliesHeadersImmediately() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, false);

        wrapper.setHeader("X-Foo", "bar");

        assertEquals(List.of("setHeader(X-Foo, bar)"), real.calls);
    }

    @Test
    public void flushReplaysBufferedHeadersInOrder() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, true);

        wrapper.setHeader("X-First", "1");
        wrapper.addHeader("X-Second", "2");
        wrapper.getOutputStream().write("body".getBytes());
        wrapper.flush();

        assertEquals(List.of("setHeader(X-First, 1)", "addHeader(X-Second, 2)"),
                real.calls.stream().filter(c -> c.startsWith("setHeader") || c.startsWith("addHeader")).toList());
        assertArrayEquals("body".getBytes(), real.body.toByteArray());
    }

    @Test
    public void flushDerivesContentLengthFromActualBytesNotBufferedHeader() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, true);

        // A step self-reports the wrong Content-Length (matches #6669's actual failure shape:
        // a step's own claimed length doesn't match what's really about to be written).
        wrapper.setHeader("Content-Length", "999");
        wrapper.getOutputStream().write("four".getBytes());
        wrapper.flush();

        assertFalse("A buffered Content-Length header must never be replayed",
                real.calls.contains("setHeader(Content-Length, 999)"));
        assertTrue("flush() must set the real Content-Length from the actual buffered byte count",
                real.calls.contains("setContentLengthLong(4)"));
    }

    @Test
    public void contentTypeFirstWriteWins() throws Exception {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper wrapper = new CachingResponseWrapper(real, true);

        wrapper.setContentType("text/xml");
        wrapper.setContentType("text/html");
        wrapper.flush();

        assertEquals(List.of("setContentType(text/xml)"),
                real.calls.stream().filter(c -> c.startsWith("setContentType")).toList());
    }

    @Test
    public void setPassthroughHeaderTunnelsThroughNestedWrappers() {
        final RecordingResponse real = new RecordingResponse();
        final CachingResponseWrapper inner = new CachingResponseWrapper(real, false);
        final CachingResponseWrapper outer = new CachingResponseWrapper(inner, true);

        // Simulates a nested dispatch (e.g. a forward step targeting a *.xql extension re-enters
        // XQueryURLRewrite, wrapping a second CachingResponseWrapper around the first) --
        // setPassthroughHeader() must reach the real response regardless of how many layers deep.
        outer.setPassthroughHeader("Cache-Control", "no-cache");

        assertEquals(List.of("setHeader(Cache-Control, no-cache)"), real.calls);
    }

    /**
     * A minimal, real (not mocked) {@link HttpServletResponse} that records exactly the
     * interactions these tests care about, delegating everything else to a nice-mocked base
     * purely to satisfy {@link HttpServletResponseWrapper}'s non-null constructor requirement.
     */
    private static final class RecordingResponse extends HttpServletResponseWrapper {
        final List<String> calls = new ArrayList<>();
        final ByteArrayOutputStream body = new ByteArrayOutputStream();

        RecordingResponse() {
            super(createNiceMockResponse());
        }

        private static HttpServletResponse createNiceMockResponse() {
            final HttpServletResponse mock = createNiceMock(HttpServletResponse.class);
            replay(mock);
            return mock;
        }

        @Override
        public void setHeader(final String name, final String value) {
            calls.add("setHeader(" + name + ", " + value + ")");
        }

        @Override
        public void addHeader(final String name, final String value) {
            calls.add("addHeader(" + name + ", " + value + ")");
        }

        @Override
        public void setIntHeader(final String name, final int value) {
            calls.add("setIntHeader(" + name + ", " + value + ")");
        }

        @Override
        public void addIntHeader(final String name, final int value) {
            calls.add("addIntHeader(" + name + ", " + value + ")");
        }

        @Override
        public void setDateHeader(final String name, final long value) {
            calls.add("setDateHeader(" + name + ", " + value + ")");
        }

        @Override
        public void addDateHeader(final String name, final long value) {
            calls.add("addDateHeader(" + name + ", " + value + ")");
        }

        @Override
        public void setContentType(final String type) {
            calls.add("setContentType(" + type + ")");
        }

        @Override
        public void setContentLengthLong(final long len) {
            calls.add("setContentLengthLong(" + len + ")");
        }

        @Override
        public String getCharacterEncoding() {
            return "UTF-8";
        }

        @Override
        public ServletOutputStream getOutputStream() {
            return new ServletOutputStream() {
                @Override
                public void write(final int b) {
                    body.write(b);
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setWriteListener(final WriteListener writeListener) {
                    throw new UnsupportedOperationException();
                }
            };
        }
    }
}
