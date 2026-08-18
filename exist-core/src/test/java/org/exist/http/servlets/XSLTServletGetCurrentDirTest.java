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
import jakarta.servlet.http.HttpServletRequest;
import org.junit.Test;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertNull;

/**
 * {@code XSLTServlet#getCurrentDir(HttpServletRequest)} had the same defect shape as
 * eXist-db/exist#6615: when {@code HttpServletRequest#getPathTranslated()} is {@code null}, it falls
 * back to {@code ServletContext#getRealPath(String)} and passes the result straight into
 * {@code Path.of()} -- but the Servlet API permits {@code getRealPath()} to return {@code null} too
 * (e.g. a webapp not exploded on disk), which threw an unhandled {@link NullPointerException} instead
 * of letting the caller report a clean "not found".
 */
public class XSLTServletGetCurrentDirTest {

    @Test
    public void nullPathTranslatedAndNullRealPathReturnsNullInsteadOfNPE() throws ServletException {
        final ServletContext mockContext = createNiceMock(ServletContext.class);
        expect(mockContext.getRealPath(anyString())).andReturn(null).anyTimes();
        replay(mockContext);

        final ServletConfig mockConfig = createNiceMock(ServletConfig.class);
        expect(mockConfig.getServletContext()).andReturn(mockContext).anyTimes();
        replay(mockConfig);

        final XSLTServlet servlet = new XSLTServlet();
        servlet.init(mockConfig);

        final HttpServletRequest request = createNiceMock(HttpServletRequest.class);
        expect(request.getPathTranslated()).andReturn(null).anyTimes();
        expect(request.getRequestURI()).andReturn("/exist/db/apps/style.xsl").anyTimes();
        expect(request.getContextPath()).andReturn("/exist").anyTimes();
        replay(request);

        // Before the fix: Path.of(null) throws NullPointerException.
        final Object currentDir = servlet.getCurrentDir(request);
        assertNull("no directory can be resolved when the container has no real path", currentDir);
    }
}
