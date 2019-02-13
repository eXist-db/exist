/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.http.urlrewrite;

import java.io.IOException;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;

public abstract class Forward extends URLRewrite {

    protected Forward(final Element config, final String uri) {
        super(config, uri);
    }

    protected Forward(final URLRewrite other) {
        super(other);
    }

    @Override
    public void doRewrite(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        final RequestDispatcher dispatcher = getRequestDispatcher(request);
        if (dispatcher == null) {
            throw new ServletException("Failed to initialize request dispatcher to forward request to " + uri);
        }
        setHeaders(new HttpResponseWrapper(response));
        dispatcher.forward(request, response);
    }

    protected abstract RequestDispatcher getRequestDispatcher(final HttpServletRequest request);
}
