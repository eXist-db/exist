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
package org.exist.http.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.*;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.Enumeration;
import java.util.Locale;

public class DumpFilter implements Filter {

    private final static Logger LOG = LogManager.getLogger(DumpFilter.class);


    // ----------------------------------------------------- Instance Variables


    /**
     * The filter configuration object we are associated with.  If this value
     * is null, this filter instance is not currently configured.
     */
    private FilterConfig filterConfig = null;


    // --------------------------------------------------------- Public Methods


    /**
     * Take this filter out of service.
     */
    public void destroy() {

        this.filterConfig = null;

    }


    /**
     * Time the processing that is performed by all subsequent filters in the
     * current filter stack, including the ultimately invoked servlet.
     *
     * @param request  The servlet request we are processing
     * @param response The servlet response we are creating
     * @param chain    The filter chain we are processing
     * @throws IOException      if an input/output error occurs
     * @throws ServletException if a servlet error occurs
     */
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        if (!LOG.isInfoEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        LOG.info("== START ====================================");
        LOG.info("Request Received at {}", new Timestamp(System.currentTimeMillis()));
        LOG.info("=============================================");
        LOG.info(" characterEncoding={}", request.getCharacterEncoding());
        LOG.info("     contentLength={}", request.getContentLength());
        LOG.info("       contentType={}", request.getContentType());
        LOG.info("            locale={}", request.getLocale());
        StringBuilder buffer = new StringBuilder();
        buffer.append("           locales=");
        final Enumeration locales = request.getLocales();
        boolean first = true;
        while (locales.hasMoreElements()) {
            final Locale locale = (Locale) locales.nextElement();
            if (first)
                {first = false;}
            else
                {buffer.append(", ");}
            buffer.append(locale.toString());
        }
        LOG.info(buffer.toString());
        Enumeration names = request.getParameterNames();
        while (names.hasMoreElements()) {
            final String name = (String) names.nextElement();
            buffer = new StringBuilder();
            buffer.append("         parameter=").append(name).append("=");
            final String values[] = request.getParameterValues(name);
            for (int i = 0; i < values.length; i++) {
                if (i > 0)
                    {buffer.append(", ");}
                buffer.append(values[i]);
            }
            LOG.info(buffer.toString());
        }
        LOG.info("          protocol={}", request.getProtocol());
        LOG.info("        remoteAddr={}", request.getRemoteAddr());
        LOG.info("        remoteHost={}", request.getRemoteHost());
        LOG.info("            scheme={}", request.getScheme());
        LOG.info("        serverName={}", request.getServerName());
        LOG.info("        serverPort={}", request.getServerPort());
        LOG.info("          isSecure={}", request.isSecure());

        // Render the HTTP servlet request properties
        if (request instanceof HttpServletRequest hrequest) {
            LOG.info("---------------------------------------------");
            LOG.info("       contextPath={}", hrequest.getContextPath());
            Cookie cookies[] = hrequest.getCookies();
            if (cookies == null)
                {cookies = new Cookie[0];}
            for (Cookie cookie : cookies) {
                LOG.info("            cookie={}={}", cookie.getName(), cookie.getValue());
            }
            names = hrequest.getHeaderNames();
            while (names.hasMoreElements()) {
                final String name = (String) names.nextElement();
                final String value = hrequest.getHeader(name);
                LOG.info("            header={}={}", name, value);
            }
            LOG.info("            method={}", hrequest.getMethod());
            LOG.info("          pathInfo={}", hrequest.getPathInfo());
            LOG.info("       queryString={}", hrequest.getQueryString());
            LOG.info("        remoteUser={}", hrequest.getRemoteUser());
            LOG.info("requestedSessionId={}", hrequest.getRequestedSessionId());
            LOG.info("        requestURI={}", hrequest.getRequestURI());
            LOG.info("       servletPath={}", hrequest.getServletPath());
        }
        LOG.info("== END ======================================");

        // Pass control on to the next filter
        chain.doFilter(request, response);

    }


    /**
     * Place this filter into service.
     *
     * @param filterConfig The filter configuration object
     */
    public void init(FilterConfig filterConfig) throws ServletException {

        this.filterConfig = filterConfig;

    }


    /**
     * Return a String representation of this object.
     */
    public String toString() {

        if (filterConfig == null) {
            return ("RequestDumperFilter()");
        }

        return "RequestDumperFilter(" + filterConfig + ")";
    }


}