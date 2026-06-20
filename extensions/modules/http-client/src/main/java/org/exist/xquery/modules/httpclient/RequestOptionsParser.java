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
package org.exist.xquery.modules.httpclient;

import java.net.http.HttpClient;

import org.exist.xquery.XPathException;
import org.exist.xquery.modules.httpclient.config.RequestOptions;
import org.exist.xquery.modules.httpclient.config.HttpClientOptions;
import org.exist.xquery.modules.httpclient.config.ResponseOptions;
import org.exist.xquery.modules.httpclient.config.UserCredentials;
import org.w3c.dom.Element;

/**
 * Parses an {@code <http:request>} element's attributes into an {@link RequestOptions} instance.
 *
 * <p>Reads the EXPath HTTP Client attributes ({@code follow-redirect}, {@code status-only},
 * {@code override-media-type}, {@code username}, {@code password}, {@code auth-method},
 * {@code send-authorization}, {@code timeout}, {@code http-version}) and produces an immutable
 * {@link RequestOptions} combining a {@link HttpClientOptions} and a {@link UserCredentials}.</p>
 */
public class RequestOptionsParser {

    private RequestOptionsParser() {
        // utility class
    }

    /**
     * Parses the attributes of the given {@code <http:request>} element into an {@link RequestOptions}.
     *
     * @param reqElem the {@code http:request} DOM element
     * @return a fully populated {@link RequestOptions}
     * @throws XPathException if the {@code timeout} attribute is present but not a valid integer
     */
    public static RequestOptions parse(final Element reqElem) throws XPathException {
        final HttpClientOptions defaults = HttpClientOptions.DEFAULTS;
        final ResponseOptions responseDefaults = ResponseOptions.DEFAULTS;
        final UserCredentials credDefaults = UserCredentials.DEFAULTS;

        final int timeout = parseTimeout(reqElem, defaults.timeout());
        final boolean followRedirect = parseBooleanAttr(reqElem, "follow-redirect", defaults.followRedirect());
        final boolean autoAcceptEncoding = parseBooleanAttr(reqElem, "auto-accept-encoding", defaults.autoAcceptEncoding());
        final HttpClient.Version httpVersion = parseHttpVersion(reqElem, defaults.httpVersion());
        final boolean statusOnly = parseBooleanAttr(reqElem, "status-only", responseDefaults.statusOnly());
        final String overrideMediaType = getAttr(reqElem, "override-media-type");
        final String username = getAttr(reqElem, "username");
        final String password = getAttr(reqElem, "password");
        final String authMethod = getAttr(reqElem, "auth-method");
        final boolean sendAuthorization = parseBooleanAttr(reqElem, "send-authorization", credDefaults.sendAuthorization());

        final HttpClientOptions requestOptions = new HttpClientOptions(followRedirect, timeout, httpVersion, autoAcceptEncoding);
        final ResponseOptions responseOptions = new ResponseOptions(statusOnly, overrideMediaType);
        final UserCredentials userCredentials = new UserCredentials(username, password, authMethod, sendAuthorization);
        return new RequestOptions(requestOptions, responseOptions, userCredentials);
    }

    private static HttpClient.Version parseHttpVersion(final Element reqElem, final HttpClient.Version defaultVersion) throws XPathException {
        final String value = getAttr(reqElem, "http-version");
        if (value == null) {
            return defaultVersion;
        }
        return switch (value) {
            case "1.1" -> HttpClient.Version.HTTP_1_1;
            case "2", "2.0" -> HttpClient.Version.HTTP_2;
            default -> throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "Invalid http-version value: " + value);
        };
    }

    private static int parseTimeout(final Element reqElem, final int defaultTimeout) throws XPathException {
        final String timeoutStr = getAttr(reqElem, "timeout");
        if (timeoutStr == null || timeoutStr.isEmpty()) {
            return defaultTimeout;
        }
        try {
            return Integer.parseInt(timeoutStr);
        } catch (final NumberFormatException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    HttpClientModule.HC005, "Invalid timeout value: " + timeoutStr);
        }
    }

    private static boolean parseBooleanAttr(final Element elem, final String name, final boolean defaultValue) {
        final String value = getAttr(elem, name);
        if (value == null) {
            return defaultValue;
        }
        return "true".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value);
    }

    private static String getAttr(final Element elem, final String name) {
        final String val = elem.getAttribute(name);
        return val != null && !val.isEmpty() ? val : null;
    }
}
