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

import org.exist.http.servlets.HttpResponseWrapper;
import org.w3c.dom.Element;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import javax.annotation.Nullable;
import java.io.IOException;

public class Redirect extends URLRewrite {

    private @Nullable RedirectType redirectType;

    public Redirect(final Element config, final String uri) throws ServletException {
        super(config, uri);
        this.redirectType = parseRedirectType(config.getAttribute("type"));
        final String redirectTo = config.getAttribute("url");
        if (redirectTo.isEmpty()) {
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
        this.redirectType = other.redirectType;
    }

    @Override
    public void doRewrite(final HttpServletRequest request, final HttpServletResponse response) throws IOException {
        if (redirectType == null) {
            redirectType =  "GET".equals(request.getMethod()) ? RedirectType.Found : RedirectType.SeeOther;
        }

        final HttpResponseWrapper responseWrapper = new HttpResponseWrapper(response);
        setHeaders(responseWrapper);
        responseWrapper.setStatusCode(redirectType.httpStatusCode);
        responseWrapper.setHeader("Location", target);

        // commit the response
        responseWrapper.flushBuffer();
    }

    @Override
    protected URLRewrite copy() {
        return new Redirect(this);
    }

    private static @Nullable RedirectType parseRedirectType(@Nullable final String strRedirectType) throws ServletException {
        // first, if no value use the default
        if (strRedirectType == null || strRedirectType.isEmpty()) {
            return null;
        }

        // second, try to parse by number
        try {
            final int intRedirectType = Integer.valueOf(strRedirectType);
            for (final RedirectType redirectType : RedirectType.values()) {
                if (redirectType.httpStatusCode == intRedirectType) {
                    return redirectType;
                }
            }
        } catch (final NumberFormatException e) {
            // ignore - no op
        }

        // third, try to parse by name
        try {
            return RedirectType.valueOf(strRedirectType);
        } catch (final IllegalArgumentException e) {
            throw new ServletException("<exist:redirect type=\"" + strRedirectType + "\" is unsupported.");
        }
    }

    private enum RedirectType {
        MovedPermanently(301),
        Found(302),
        SeeOther(303),
        TemporaryRedirect(307),
        PermanentRedirect(308);

        public final int httpStatusCode;

        RedirectType(final int httpStatusCode) {
            this.httpStatusCode = httpStatusCode;
        }
    }
}
