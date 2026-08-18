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
import org.junit.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertNull;

/**
 * {@code XQueryURLRewrite#findSourceFromFs(String, String[])} passed
 * {@code ServletContext#getRealPath(String)}'s result straight into {@code Path.of()} without a null
 * check. The Servlet API explicitly permits {@code getRealPath()} to return {@code null} when the
 * container cannot map a path to disk (e.g. a webapp not exploded on disk), which -- since this
 * method sits on the controller.xql lookup path exercised by every filesystem-routed request through
 * {@code XQueryURLRewrite} -- would throw an unhandled {@link NullPointerException} on effectively
 * every request. Same defect shape as eXist-db/exist#6615.
 */
public class XQueryURLRewriteFindSourceFromFsTest {

    @Test
    public void nullRealPathReturnsNullInsteadOfNPE() throws Exception {
        final ServletContext mockContext = createNiceMock(ServletContext.class);
        expect(mockContext.getRealPath(anyString())).andReturn(null).anyTimes();
        replay(mockContext);

        final ServletConfig mockConfig = createNiceMock(ServletConfig.class);
        expect(mockConfig.getServletContext()).andReturn(mockContext).anyTimes();
        replay(mockConfig);

        final XQueryURLRewrite rewrite = new XQueryURLRewrite();
        rewrite.init(mockConfig);

        final Method findSourceFromFs = XQueryURLRewrite.class.getDeclaredMethod(
                "findSourceFromFs", String.class, String[].class);
        findSourceFromFs.setAccessible(true);

        try {
            // Before the fix: Path.of(null) throws NullPointerException, wrapped by reflection in
            // InvocationTargetException.
            final Object sourceInfo = findSourceFromFs.invoke(rewrite, "/", new String[]{"apps", "optimize.xql"});
            assertNull("no filesystem controller can be found when the container has no real path", sourceInfo);
        } catch (final InvocationTargetException e) {
            throw (Exception) e.getCause();
        }
    }
}
