/*
 * Copyright © 2001, Adam Retter
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in the
 *       documentation and/or other materials provided with the distribution.
 *     * Neither the name of the <organization> nor the
 *       names of its contributors may be used to endorse or promote products
 *       derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl.adapters;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exquery.http.HttpMethod;
import org.exquery.http.HttpRequest;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class HttpServletRequestAdapter implements HttpRequest {

    private final HttpServletRequest request;
    private final FilterInputStreamCacheConfiguration cacheConfiguration;
    private InputStream is = null;
    private Map<String, List<String>> formFields = null;

    public HttpServletRequestAdapter(final HttpServletRequest request, final FilterInputStreamCacheConfiguration cacheConfiguration) {
        this.request = request;
        this.cacheConfiguration = cacheConfiguration;
    }

    @Override
    public HttpMethod getMethod() {
        return HttpMethod.valueOf(request.getMethod());
    }

    @Override
    public String getScheme() {
        return request.getScheme();
    }

    @Override
    public String getHostname() {
        return request.getServerName();
    }

    @Override
    public int getPort() {
        return request.getServerPort();
    }

    @Override
    public String getQuery() {
        return request.getQueryString();
    }

    @Override
    public String getPath() {
        return request.getPathInfo();
    }

    @Override
    public String getURI() {
        return request.getRequestURI();
    }

    @Override
    public String getAddress() {
        return request.getLocalAddr();
    }

    @Override
    public String getRemoteHostname() {
        return request.getRemoteHost();
    }

    @Override
    public String getRemoteAddress() {
        return request.getRemoteAddr();
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }

    @Override
    public InputStream getInputStream() throws IOException {

        if (is == null) {
            final FilterInputStreamCache cache = FilterInputStreamCacheFactory.getCacheInstance(cacheConfiguration, request.getInputStream());
            is = new CachingFilterInputStream(cache);
            is.mark(Integer.MAX_VALUE);
        } else {
            is.reset();
        }

        return is;
    }

    @Override
    public String getContentType() {
        return request.getContentType();
    }

    @Override
    public int getContentLength() {
        return request.getContentLength();
    }

    @Override
    public List<String> getHeaderNames() {
        final List<String> names = new ArrayList<>();
        for (final Enumeration<String> enumNames = request.getHeaderNames(); enumNames.hasMoreElements();) {
            names.add(enumNames.nextElement());
        }
        return names;
    }

    @Override
    public String getHeader(final String httpHeaderName) {
        return request.getHeader(httpHeaderName);
    }

    @Override
    public String getCookieValue(final String cookieName) {
        final Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (final Cookie cookie : cookies) {
                if (cookie.getName().equals(cookieName)) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    @Override
    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    //TODO consider moving more of this code into EXQuery impl
    @Override
    public Object getFormParam(final String key) {
        if ("GET".equals(request.getMethod())) {
            return getGetParameters(key);
        }

        if ("POST".equals(request.getMethod()) && request.getContentType() != null && "application/x-www-form-urlencoded".equals(request.getContentType())) {
            if (formFields == null) {

                try {
                    final InputStream in = getInputStream();
                    formFields = extractFormFields(in);
                } catch (final IOException ioe) {
                    //TODO log or something?
                    ioe.printStackTrace();
                    return null;
                }
            }

            final List<String> formFieldValues = formFields.get(key);
            if (formFieldValues != null) {
                return formFieldValues;
            } else {
                //fallback to get parameters
                return getGetParameters(key);
            }
        }

        return null;
    }

    //TODO consider moving more of this code into EXQuery impl
    @Override
    public Object getQueryParam(final String key) {
        //if(request.getMethod().equals("GET")) {
        return getGetParameters(key);
        //}
        //return null;
    }

    private Object getGetParameters(final String key) {
        final String[] values = request.getParameterValues(key);
        if (values != null) {
            if (values.length == 1) {
                return values[0];
            } else {
                return Arrays.asList(values);
            }
        }
        return null;
    }

    @Override
    public List<String> getParameterNames() {
        final List<String> names = new ArrayList<>();
        for (final Enumeration<String> enumNames = request.getParameterNames(); enumNames.hasMoreElements();) {
            names.add(enumNames.nextElement());
        }
        return names;
    }

    private Map<String, List<String>> extractFormFields(final InputStream in) throws IOException {
        final Map<String, List<String>> fields = new Hashtable<>();

        final StringBuilder builder = new StringBuilder();
        try(final Reader reader = new InputStreamReader(in)) {
            int read = -1;
            final char[] cbuf = new char[1024];
            while ((read = reader.read(cbuf)) > -1) {
                builder.append(cbuf, 0, read);
            }
        }

        final StringTokenizer st = new StringTokenizer(builder.toString(), "&");

        String key;
        String val;

        while (st.hasMoreTokens()) {
            final String pair = st.nextToken();
            final int pos = pair.indexOf('=');
            if (pos == -1) {
                throw new IllegalArgumentException();
            }

            try {
                key = java.net.URLDecoder.decode(pair.substring(0, pos), UTF_8);
                val = java.net.URLDecoder.decode(pair.substring(pos + 1, pair.length()), UTF_8);
            } catch (final Exception e) {
                throw new IllegalArgumentException(e);
            }

            List<String> vals = fields.get(key);
            if (vals == null) {
                vals = new ArrayList<>();
            }
            vals.add(val);

            fields.put(key, vals);
        }

        return fields;
    }
}
