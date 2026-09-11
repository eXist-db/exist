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

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

/**
 * The request wrapper threaded through a controller.xql pipeline -- distinct from
 * {@link org.exist.http.servlets.RequestWrapper}, an unrelated class of the same simple name used
 * elsewhere in the HTTP layer. Lets {@link XQueryURLRewrite} rewrite the effective request URI,
 * servlet path, and parameters as a request moves through {@code <exist:forward>}/
 * {@code <exist:view>} steps, and lets a view step read the previous step's output as its own
 * request body (see {@link #setData}).
 */
public class ControllerRequestWrapper extends HttpServletRequestWrapper {

    private static final Logger LOG = LogManager.getLogger(ControllerRequestWrapper.class);

    private final Map<String, List<String>> addedParams = new HashMap<>();

    private ServletInputStream sis;
    private BufferedReader reader;

    private String contentType;
    private int contentLength;
    private String characterEncoding;
    private String method;
    private String inContextPath;
    private String servletPath;
    private String basePath;
    private boolean allowCaching = true;

    private void addNameValue(final String name, final String value, final Map<String, List<String>> map) {
        List<String> values = map.get(name);
        if (values == null) {
            values = new ArrayList<>();
        }
        values.add(value);
        map.put(name, values);
    }

    protected ControllerRequestWrapper(final HttpServletRequest request) {
        super(request);

        // copy parameters
        for (final Map.Entry<String, String[]> param : request.getParameterMap().entrySet()) {
            for (final String paramValue : param.getValue()) {
                addNameValue(param.getKey(), paramValue, addedParams);
            }
        }
        contentType = request.getContentType();
    }

    protected void allowCaching(final boolean cache) {
        this.allowCaching = cache;
    }

    @Override
    public String getRequestURI() {
        String uri = inContextPath == null ? super.getRequestURI() : getContextPath() + inContextPath;

        // Strip jsessionid from uris. New behavior of jetty
        // see jira.codehaus.org/browse/JETTY-1146
        final int pos = uri.indexOf(";jsessionid=");
        if (pos > 0) {
            uri = uri.substring(0, pos);
        }

        return uri;
    }

    public String getInContextPath() {
        return Objects.requireNonNullElseGet(inContextPath, () -> getRequestURI().substring(getContextPath().length()));
    }

    public void setInContextPath(final String path) {
        inContextPath = path;
    }

    @Override
    public String getMethod() {
        if (method == null) {
            return super.getMethod();
        }
        return method;
    }

    public void setMethod(final String method) {
        this.method = method;
    }

    /**
     * Change the requestURI and the servletPath
     *
     * @param requestURI  the URI of the request without the context path
     * @param servletPath the servlet path
     */
    public void setPaths(final String requestURI, final String servletPath) {
        this.inContextPath = requestURI;
        if (servletPath == null) {
            this.servletPath = requestURI;
        } else {
            this.servletPath = servletPath;
        }
    }

    public void setBasePath(final String base) {
        this.basePath = base;
    }

    public String getBasePath() {
        return basePath;
    }

    /**
     * Change the base path of the request, e.g. if the original request pointed
     * to /fs/foo/baz, but the request should be forwarded to /foo/baz.
     *
     * @param base the base path to remove
     */
    public void removePathPrefix(final String base) {
        setPaths(getInContextPath().substring(base.length()),
                servletPath != null ? servletPath.substring(base.length()) : null);
    }

    @Override
    public String getServletPath() {
        return servletPath == null ? super.getServletPath() : servletPath;
    }

    @Override
    public String getPathInfo() {
        final String path = getInContextPath();
        final String sp = getServletPath();
        if (sp == null) {
            return null;
        }
        if (path.length() < sp.length()) {
            LOG.error("Internal error: servletPath = {} is longer than path = {}", sp, path);
            return null;
        }
        return path.length() == sp.length() ? null : path.substring(sp.length());
    }

    @Override
    public String getPathTranslated() {
        final String pathInfo = getPathInfo();
        if (pathInfo == null) {
            return super.getPathTranslated();
        }
        return super.getSession().getServletContext().getRealPath(pathInfo);
    }

    protected void setData(@Nullable final byte[] data) {
        final byte[] effectiveData = data == null ? new byte[0] : data;
        contentLength = effectiveData.length;
        sis = new CachingServletInputStream(effectiveData);
    }

    public void addParameter(final String name, final String value) {
        addNameValue(name, value, addedParams);
    }

    @Override
    public String getParameter(final String name) {
        final List<String> paramValues = addedParams.get(name);
        if (paramValues != null && !paramValues.isEmpty()) {
            return paramValues.getFirst();
        }
        return null;
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        final Map<String, String[]> parameterMap = new HashMap<>();
        for (final Entry<String, List<String>> param : addedParams.entrySet()) {
            final List<String> values = param.getValue();
            if (values != null) {
                parameterMap.put(param.getKey(), values.toArray(new String[0]));
            } else {
                parameterMap.put(param.getKey(), new String[0]);
            }
        }
        return parameterMap;
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(addedParams.keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        final List<String> values = addedParams.get(name);

        if (values != null) {
            return values.toArray(new String[0]);
        } else {
            return null;
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (sis == null) {
            return super.getInputStream();
        }
        return sis;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (sis == null) {
            return super.getReader();
        }
        if (reader == null) {
            reader = new BufferedReader(new InputStreamReader(sis, getCharacterEncoding()));
        }
        return reader;
    }

    @Override
    public String getContentType() {
        if (contentType == null) {
            return super.getContentType();
        }
        return contentType;
    }

    protected void setContentType(final String contentType) {
        this.contentType = contentType;
    }

    @Override
    public int getContentLength() {
        if (sis == null) {
            return super.getContentLength();
        }
        return contentLength;
    }

    @Override
    public void setCharacterEncoding(final String encoding) {
        this.characterEncoding = encoding;
    }

    @Override
    public String getCharacterEncoding() {
        if (characterEncoding == null) {
            return super.getCharacterEncoding();
        }
        return characterEncoding;
    }

    @Override
    public long getDateHeader(final String s) {
        // When a view is applied, allowCaching is false and we hide If-Modified-Since from the
        // conditional-GET check (RESTServer reads it via getDateHeader): a view may have changed
        // even when the underlying resource has not, so answering with a 304 based on the
        // resource's timestamp would wrongly suppress the re-render. We suppress ONLY this
        // date-header form used by that check, and deliberately do NOT override getHeader(), so
        // application code can still read the raw If-Modified-Since value via request:get-header().
        // See https://github.com/eXist-db/exist/issues/6603
        if ("If-Modified-Since".equals(s) && !allowCaching) {
            return -1;
        }
        return super.getDateHeader(s);
    }
}
