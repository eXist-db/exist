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

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.exist.http.urlrewrite.XQueryURLRewrite.RequestWrapper;
import org.junit.Test;

import java.util.Collections;

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

/**
 * {@link RequestWrapper#getPathTranslated()} had dead code: when its own {@code getPathInfo()}
 * override returned {@code null} (the whole in-context path is consumed by the servlet path, i.e.
 * a full-match forward), it called {@code super.getPathTranslated()} but discarded the result, then
 * unconditionally returned {@code null} regardless of what the underlying request would have
 * reported. That silently threw away information the underlying container may have had, forcing
 * every full-match forward through this wrapper down the null-path branch downstream (see
 * {@code XQueryServlet}, fixed for eXist-db/exist#6615) even when the container could have resolved
 * a real path.
 */
public class XQueryURLRewriteRequestWrapperTest {

    @Test
    public void nullPathInfoFallsBackToUnderlyingRequestPathTranslated() {
        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap()).andReturn(Collections.emptyMap()).anyTimes();
        expect(underlying.getPathTranslated()).andReturn("/underlying/real/path").anyTimes();
        replay(underlying);

        final RequestWrapper wrapper = new RequestWrapper(underlying);
        // Same length in-context path and servlet path -- getPathInfo() computes to null.
        wrapper.setPaths("/apps/foo", "/apps/foo");

        assertEquals("/underlying/real/path", wrapper.getPathTranslated());
    }

    @Test
    public void nonNullPathInfoResolvesViaServletContextRealPath() {
        final ServletContext servletContext = createNiceMock(ServletContext.class);
        expect(servletContext.getRealPath("/bar.xql")).andReturn("/resolved/real/path").anyTimes();
        replay(servletContext);

        final HttpSession session = createNiceMock(HttpSession.class);
        expect(session.getServletContext()).andReturn(servletContext).anyTimes();
        replay(session);

        final HttpServletRequest underlying = createNiceMock(HttpServletRequest.class);
        expect(underlying.getParameterMap()).andReturn(Collections.emptyMap()).anyTimes();
        expect(underlying.getSession()).andReturn(session).anyTimes();
        replay(underlying);

        final RequestWrapper wrapper = new RequestWrapper(underlying);
        // Longer in-context path than servlet path -- getPathInfo() computes to "/bar.xql".
        wrapper.setPaths("/apps/foo/bar.xql", "/apps/foo");

        assertEquals("/resolved/real/path", wrapper.getPathTranslated());
    }
}
