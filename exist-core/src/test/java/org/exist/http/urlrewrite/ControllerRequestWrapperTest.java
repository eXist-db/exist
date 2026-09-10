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

import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

import java.util.Collections;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Direct unit tests for {@link ControllerRequestWrapper}, closing gaps left after
 * {@link XQueryURLRewriteRequestWrapperTest} (narrowly scoped to the #6615
 * {@code getPathTranslated()} regression) and {@link XQueryURLRewriteTest} (parameter-map copying
 * only). Fast, in-process coverage of the request-wrapping behavior that used to be exercisable
 * only indirectly through a full HTTP round trip.
 */
public class ControllerRequestWrapperTest {

    private static HttpServletRequest mockRequest() {
        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getParameterMap()).andReturn(Collections.emptyMap()).anyTimes();
        return request;
    }

    @Test
    public void getRequestURIUsesUnderlyingRequestWhenInContextPathNotSet() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getRequestURI()).andReturn("/exist/apps/foo").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("/exist/apps/foo", wrapper.getRequestURI());
    }

    @Test
    public void getRequestURIReflectsSetPaths() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getContextPath()).andReturn("/exist").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setPaths("/apps/bar", "/apps");

        assertEquals("/exist/apps/bar", wrapper.getRequestURI());
    }

    @Test
    public void getRequestURIStripsJsessionid() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getRequestURI()).andReturn("/exist/apps/foo;jsessionid=ABC123").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("/exist/apps/foo", wrapper.getRequestURI());
    }

    @Test
    public void getInContextPathFallsBackToComputingFromRequestURI() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getRequestURI()).andReturn("/exist/apps/foo").anyTimes();
        expect(underlying.getContextPath()).andReturn("/exist").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("/apps/foo", wrapper.getInContextPath());
    }

    @Test
    public void removePathPrefixStripsPrefixFromBothPaths() {
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setPaths("/fs/foo/baz", "/fs/foo");
        wrapper.removePathPrefix("/fs");

        assertEquals("/foo/baz", wrapper.getInContextPath());
        assertEquals("/foo", wrapper.getServletPath());
    }

    @Test
    public void getServletPathFallsBackToUnderlyingWhenNotSet() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getServletPath()).andReturn("").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("", wrapper.getServletPath());
    }

    @Test
    public void getPathInfoReturnsNullWhenPathEqualsServletPath() {
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setPaths("/apps/foo", "/apps/foo");

        assertNull(wrapper.getPathInfo());
    }

    @Test
    public void getPathInfoReturnsRemainderAfterServletPath() {
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setPaths("/apps/foo/bar.xql", "/apps/foo");

        assertEquals("/bar.xql", wrapper.getPathInfo());
    }

    @Test
    public void getPathInfoReturnsNullWhenServletPathLongerThanPath() {
        // Defensive branch: servletPath (set independently via setPaths) longer than the
        // in-context path itself -- an internal-inconsistency case that must not throw.
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setPaths("/a", "/apps/foo");

        assertNull(wrapper.getPathInfo());
    }

    @Test
    public void setDataMakesInputStreamAndContentLengthReflectTheBufferedBytes() throws Exception {
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setData("hello".getBytes());

        assertEquals(5, wrapper.getContentLength());
        assertArrayEquals("hello".getBytes(), wrapper.getInputStream().readAllBytes());
    }

    @Test
    public void getInputStreamFallsBackToUnderlyingWhenNoDataSet() throws Exception {
        final HttpServletRequest underlying = mockRequest();
        final jakarta.servlet.ServletInputStream underlyingStream = createNiceMock(jakarta.servlet.ServletInputStream.class);
        expect(underlying.getInputStream()).andReturn(underlyingStream).anyTimes();
        replay(underlying, underlyingStream);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals(underlyingStream, wrapper.getInputStream());
    }

    @Test
    public void getReaderReadsBackBufferedData() throws Exception {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getCharacterEncoding()).andReturn("UTF-8").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setData("hello".getBytes());

        assertEquals("hello", wrapper.getReader().readLine());
    }

    @Test
    public void getParameterReturnsFirstCopiedValue() {
        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap())
                .andReturn(java.util.Map.of("name", new String[]{"first", "second"}))
                .anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("first", wrapper.getParameter("name"));
        assertArrayEquals(new String[]{"first", "second"}, wrapper.getParameterValues("name"));
        assertNull(wrapper.getParameterValues("missing"));
    }

    @Test
    public void addParameterAppendsToExistingValues() {
        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap())
                .andReturn(java.util.Map.of("name", new String[]{"first"}))
                .anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.addParameter("name", "second");

        assertArrayEquals(new String[]{"first", "second"}, wrapper.getParameterValues("name"));
    }

    @Test
    public void getContentTypeFallsBackToUnderlyingWhenNotOverridden() {
        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap()).andReturn(Collections.emptyMap()).anyTimes();
        expect(underlying.getContentType()).andReturn("text/xml").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);

        assertEquals("text/xml", wrapper.getContentType());
    }

    @Test
    public void setContentTypeOverridesUnderlyingValue() {
        // Constructor captures the underlying Content-Type at construction time; this proves an
        // explicit setContentType() call afterward wins over it, as applyViews() relies on when
        // it sets a view request's Content-Type from the previous step's response.
        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap()).andReturn(Collections.emptyMap()).anyTimes();
        expect(underlying.getContentType()).andReturn("text/xml").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.setContentType("text/html");

        assertEquals("text/html", wrapper.getContentType());
    }

    @Test
    public void characterEncodingFallsBackToUnderlyingUntilOverridden() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getCharacterEncoding()).andReturn("ISO-8859-1").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        assertEquals("ISO-8859-1", wrapper.getCharacterEncoding());

        wrapper.setCharacterEncoding("UTF-8");
        assertEquals("UTF-8", wrapper.getCharacterEncoding());
    }

    @Test
    public void getMethodFallsBackToUnderlyingUntilOverridden() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getMethod()).andReturn("GET").anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        assertEquals("GET", wrapper.getMethod());

        wrapper.setMethod("POST");
        assertEquals("POST", wrapper.getMethod());
    }

    @Test
    public void getDateHeaderSuppressesIfModifiedSinceWhenCachingDisallowed() {
        // See #6603: allowCaching(false) must blank the *conditional-GET check's* view of
        // If-Modified-Since (read via getDateHeader()) without touching getHeader(), so a view
        // that changed the output isn't wrongly suppressed by a 304 based on the resource's own,
        // unrelated timestamp. IfModifiedSinceHandoverControllerTest covers the full pipeline
        // wiring for this (that allowCaching(false) actually gets engaged when a view applies,
        // and that request:get-header() still sees the raw value); this covers the wrapper's own
        // suppression logic directly.
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getDateHeader("If-Modified-Since")).andReturn(12345L).anyTimes();
        expect(underlying.getDateHeader("Last-Modified")).andReturn(67890L).anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        wrapper.allowCaching(false);

        assertEquals("If-Modified-Since must be suppressed for the conditional-GET check",
                -1L, wrapper.getDateHeader("If-Modified-Since"));
        assertEquals("Other date headers must be unaffected",
                67890L, wrapper.getDateHeader("Last-Modified"));
    }

    @Test
    public void getDateHeaderPassesThroughIfModifiedSinceWhenCachingAllowed() {
        final HttpServletRequest underlying = mockRequest();
        expect(underlying.getDateHeader("If-Modified-Since")).andReturn(12345L).anyTimes();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        // allowCaching defaults to true.

        assertEquals(12345L, wrapper.getDateHeader("If-Modified-Since"));
    }

    @Test
    public void basePathSetterAndGetterRoundTrip() {
        final HttpServletRequest underlying = mockRequest();
        replay(underlying);

        final ControllerRequestWrapper wrapper = new ControllerRequestWrapper(underlying);
        assertNull(wrapper.getBasePath());

        wrapper.setBasePath("/db/apps");
        assertEquals("/db/apps", wrapper.getBasePath());
    }
}
