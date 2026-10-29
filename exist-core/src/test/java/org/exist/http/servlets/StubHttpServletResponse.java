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

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;

import javax.annotation.Nullable;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A minimal {@link HttpServletResponse} recording status, headers, content type and body, for
 * driving {@link XQueryServlet#process} without a servlet container. Every other method answers
 * with an empty default. Companion of {@link StubHttpServletRequest}.
 */
public class StubHttpServletResponse extends HttpServletResponseWrapper {

    private static final HttpServletResponse DEFAULTS = (HttpServletResponse) Proxy.newProxyInstance(
            StubHttpServletResponse.class.getClassLoader(),
            new Class<?>[]{HttpServletResponse.class},
            (proxy, method, args) -> method.getReturnType() == boolean.class ? Boolean.FALSE
                    : method.getReturnType() == int.class ? 0 : null);

    private final UnsynchronizedByteArrayOutputStream body = new UnsynchronizedByteArrayOutputStream();
    private final Map<String, List<String>> headers = new HashMap<>();
    private int status = SC_OK;
    @Nullable private String contentType;
    @Nullable private String characterEncoding;
    @Nullable private String errorMessage;

    public StubHttpServletResponse() {
        super(DEFAULTS);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(final WriteListener writeListener) {
                // non-blocking I/O is not exercised; nothing to wire up here
            }

            @Override
            public void write(final int b) {
                body.write(b);
            }

            @Override
            public void write(final byte[] b, final int off, final int len) {
                body.write(b, off, len);
            }
        };
    }

    @Override
    public void setStatus(final int sc) {
        this.status = sc;
    }

    @Override
    public int getStatus() {
        return status;
    }

    @Override
    public void sendError(final int sc, final String msg) {
        this.status = sc;
        this.errorMessage = msg;
    }

    @Override
    public void sendError(final int sc) {
        this.status = sc;
    }

    @Override
    public void addHeader(final String name, final String value) {
        headers.computeIfAbsent(name, k -> new ArrayList<>()).add(value);
    }

    @Override
    public void setHeader(final String name, final String value) {
        final List<String> values = new ArrayList<>();
        values.add(value);
        headers.put(name, values);
    }

    @Override
    @Nullable
    public String getHeader(final String name) {
        final List<String> values = headers.get(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    @Override
    public boolean containsHeader(final String name) {
        return headers.containsKey(name);
    }

    @Override
    public void setContentType(final String type) {
        this.contentType = type;
    }

    @Override
    @Nullable
    public String getContentType() {
        return contentType;
    }

    @Override
    public void setCharacterEncoding(final String charset) {
        this.characterEncoding = charset;
    }

    @Override
    @Nullable
    public String getCharacterEncoding() {
        return characterEncoding;
    }

    @Override
    public boolean isCommitted() {
        return false;
    }

    @Nullable
    public String getErrorMessage() {
        return errorMessage;
    }

    public int getBodySize() {
        return body.size();
    }

    public String getBodyAsString() {
        return body.toString(UTF_8);
    }
}
