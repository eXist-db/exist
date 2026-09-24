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

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.exist.Namespaces;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertThrows;

/**
 * A forward whose servlet is not registered is a configuration error, unless the forward is
 * marked optional, in which case its route answers 404.
 */
public class PathForwardOptionalTest {

    private static Element forward(final boolean optional) throws Exception {
        final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        final Document doc = factory.newDocumentBuilder().newDocument();
        final Element forward = doc.createElementNS(Namespaces.EXIST_NS, "forward");
        forward.setAttribute("pattern", "/missing");
        forward.setAttribute("servlet", "MissingServlet");
        if (optional) {
            forward.setAttribute("optional", "true");
        }
        doc.appendChild(forward);
        return forward;
    }

    private static ServletConfig configWithoutTheServlet() {
        final ServletContext servletContext = createMock(ServletContext.class);
        expect(servletContext.getNamedDispatcher("MissingServlet")).andReturn(null);
        final ServletConfig servletConfig = createMock(ServletConfig.class);
        expect(servletConfig.getServletContext()).andReturn(servletContext);
        replay(servletContext, servletConfig);
        return servletConfig;
    }

    @Test
    public void aMissingServletIsAnErrorByDefault() throws Exception {
        final PathForward forward = new PathForward(configWithoutTheServlet(), forward(false), "/missing");
        final HttpServletRequest request = createMock(HttpServletRequest.class);
        final HttpServletResponse response = createMock(HttpServletResponse.class);
        replay(request, response);

        assertThrows(ServletException.class, () -> forward.doRewrite(request, response));
    }

    @Test
    public void aMissingOptionalServletAnswers404() throws Exception {
        final PathForward forward = new PathForward(configWithoutTheServlet(), forward(true), "/missing");
        final HttpServletRequest request = createMock(HttpServletRequest.class);
        final HttpServletResponse response = createMock(HttpServletResponse.class);
        response.sendError(eq(HttpServletResponse.SC_NOT_FOUND), anyString());
        expectLastCall();
        replay(request, response);

        forward.doRewrite(request, response);

        verify(response);
    }
}
