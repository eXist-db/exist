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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

public abstract class Forward extends URLRewrite {

    private static final Logger LOG = LogManager.getLogger(Forward.class);

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
            if (!isOptional()) {
                throw new ServletException("Failed to initialize request dispatcher to forward request to " + uri);
            }
            // an optional target whose module is not deployed: that route simply does not exist here
            LOG.warn("No servlet registered for optional forward target '{}'; is the module that provides it on the classpath?", uri);
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Servlet not available: " + uri);
            return;
        }
        setHeaders(new HttpResponseWrapper(response));
        dispatcher.forward(request, response);
    }

    protected abstract RequestDispatcher getRequestDispatcher(final HttpServletRequest request);

    /**
     * Whether a missing target is expected, because the module providing it may not be deployed.
     * A missing target of any other forward is a configuration error and fails loudly.
     *
     * @return true if a missing target should answer 404 rather than fail
     */
    protected boolean isOptional() {
        return false;
    }
}
