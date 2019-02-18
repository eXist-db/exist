/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */
package org.exist.http.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.HashSet;

/**
 *
 * HTTP GET /rest/
 * HTTP GET ?_query=
 * HTTP POST XUpdate
 * HTTP POST Query Document
 * HTTP DELETE
 * HTTP PUT

 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Aug 18, 2010
 * Time: 2:03:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class PathFilter implements Filter {

    private final static Logger LOG = LogManager.getLogger(PathFilter.class);

    private FilterConfig filterConfig;

    private static final String TEST_REST = "HTTP GET /rest/";
    private static final String TEST_GET_QUERY = "HTTP GET ?_query=";
    private static final String TEST_POST_XUPDATE = "HTTP POST XUpdate";
    private static final String TEST_POST_QUERY = "HTTP POST Query Document";
    private static final String TEST_DELETE = "HTTP DELETE";
    private static final String TEST_PUT = "HTTP PUT";

    private boolean allowFirst = false;
    private HashSet<String> allows = new HashSet<String>();
    private HashSet<String> denys = new HashSet<String>();
    private HashSet<String> filterNames = new HashSet<String>();

    public void init(FilterConfig filterConfig) throws ServletException {
        setFilterConfig(filterConfig);
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        if (filterConfig == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        HttpServletRequest httpServletRequest;
        HttpServletResponse httpServletResponse;

        if (servletRequest instanceof HttpServletRequest) {
            httpServletRequest = (HttpServletRequest)servletRequest;
            httpServletResponse = (HttpServletResponse)servletResponse;
        }
        else {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        boolean conditionMet = false;

        final String queryString = httpServletRequest.getQueryString();
        final String requestURI = httpServletRequest.getRequestURI();

        LOG.info("requestURI = [" + requestURI + "]");
        LOG.info("queryString = [" + queryString + "]");
        LOG.info("method = [" + httpServletRequest.getMethod() + "]");

        if ((queryString != null && queryString.indexOf("_query=") >= 0) && filterNames.contains(TEST_GET_QUERY)) {
            LOG.info(TEST_GET_QUERY + " met");
            conditionMet = true;
        } else if (requestURI != null && requestURI.indexOf("/rest/") >= 0 && filterNames.contains(TEST_REST)) {
            conditionMet = true;
            LOG.info(TEST_REST + " met");
        } else if (httpServletRequest.getMethod().equalsIgnoreCase("PUT") && filterNames.contains(TEST_PUT)) {
            conditionMet = true;
            LOG.info(TEST_PUT + " met");
        } else if (httpServletRequest.getMethod().equalsIgnoreCase("DELETE") && filterNames.contains(TEST_DELETE)) {
            conditionMet = true;
            LOG.info(TEST_DELETE + " met");
        } else if (httpServletRequest.getMethod().equalsIgnoreCase("POST")) {

        }

        if (!conditionMet) {
            filterChain.doFilter(httpServletRequest, httpServletResponse);
            return;
        }

        if (allowFirst) {
            if (allowMatch(httpServletRequest)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
            }
        } else {
            if (denyMatch(httpServletRequest)) {
                httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            } else if (allowMatch(httpServletRequest)) {
                filterChain.doFilter(httpServletRequest, httpServletResponse);
            }
        }

        httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
    }

    private String validName(ServletRequest servletRequest) {
        String name = servletRequest.getRemoteHost();
        final String address = servletRequest.getRemoteAddr();

        if (name.equalsIgnoreCase(address)) {
            try {
                name = InetAddress.getByName(address).getCanonicalHostName();
            } catch (final UnknownHostException e) {
                name = null;
            }
        }
        return name;
    }

    private boolean denyMatch(ServletRequest servletRequest) {

        final String name = validName(servletRequest);
        final String address = servletRequest.getRemoteAddr();
        return denys.contains(address) || denys.contains(name);
    }

    private boolean allowMatch(ServletRequest servletRequest) {

        final String name = validName(servletRequest);
        final String address = servletRequest.getRemoteAddr();

        return allows.contains(address) || allows.contains(name);
    }

    public void destroy() {
        allows = null;
        denys = null;
        filterNames = null;
        filterConfig = null;
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
        final Enumeration initParams = filterConfig.getInitParameterNames();

        // no initial parameters, so invoke the next filter in the chain
        if (initParams != null) {
            allows.clear();
            denys.clear();
            filterNames.clear();
            while (initParams.hasMoreElements()) {
                final String name = (String) initParams.nextElement();
                final String value = filterConfig.getInitParameter(name);

                LOG.info("Parameter [" + name + "][" + value + "]");

                if (name.startsWith("exclude")) {
                    denys.add(value);
                } else if (name.startsWith("include")) {
                    allows.add(value);
                } else if (name.startsWith("type")) {
                    filterNames.add(value);
                } else if (name.equalsIgnoreCase("order")) {
                    allowFirst = value.equalsIgnoreCase("allow,deny");
                }
            }
        }
    }
}
