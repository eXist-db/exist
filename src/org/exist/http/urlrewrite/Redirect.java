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

import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;

public class Redirect extends URLRewrite {

    public Redirect(final Element config, final String uri) throws ServletException {
        super(config, uri);
        final String redirectTo = config.getAttribute("url");
        if (redirectTo.length() == 0) {
            throw new ServletException("<exist:redirect> needs an attribute 'url'.");
        }
        if (redirectTo.matches("^\\w+://.*")) {
            setTarget(redirectTo);
        } // do not touch URIs pointing to other server
        else {
            setTarget(URLRewrite.normalizePath(redirectTo));
        }
    }

    public Redirect(final Redirect other) {
        super(other);
    }

    @Override
    public void doRewrite(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        setHeaders(new HttpResponseWrapper(response));
        response.sendRedirect(target);
    }

    @Override
    protected URLRewrite copy() {
        return new Redirect(this);
    }
}
