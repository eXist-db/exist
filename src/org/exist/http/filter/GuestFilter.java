package org.exist.http.filter;

import org.apache.log4j.Logger;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.Enumeration;

/**
 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Aug 24, 2010
 * Time: 2:06:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class GuestFilter implements Filter {

    private final static Logger LOG = Logger.getLogger(GuestFilter.class);

    private FilterConfig filterConfig;

    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.info("Starting GuestFilter");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest;
        HttpServletResponse httpServletResponse;

        if (servletRequest instanceof HttpServletRequest) {
            httpServletRequest = (HttpServletRequest)servletRequest;
            httpServletResponse = (HttpServletResponse)servletResponse;
            LOG.info("HTTP Servlet Request confirmed");
        }
        else {
            LOG.info("Servlet Request confirmed");
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        String username = httpServletRequest.getRemoteUser();
        String requestURI = httpServletRequest.getRequestURI();
        String sessionID = httpServletRequest.getRequestedSessionId();
        LOG.info("username [" + username + "]");
        LOG.info("requestURI [" + requestURI + "]");
        LOG.info("sessionID [" + sessionID + "]");

        HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            LOG.info("session: " + session.toString());
            Enumeration enumeration = session.getAttributeNames();

            while (enumeration.hasMoreElements()) {
                String key = (String) enumeration.nextElement();
                Object value = session.getAttribute(key);
                LOG.info("session attribute [" + key + "][" + value.toString() + "]");
            }
        } else {
            LOG.info("No valid session");
        }

        if (requestURI.startsWith("/webdav") || requestURI.startsWith("/xmlrpc")) {
            if (username != null && username.equalsIgnoreCase("guest")) {
                httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
                return;
            } else if (!httpServletRequest.isSecure()) {
                String serverName = httpServletRequest.getServerName();
                String path = httpServletRequest.getRequestURI();
                String newpath = "https://" + serverName + path;
                LOG.info("Redirecting to SSL: " + newpath);
                httpServletResponse.sendRedirect(newpath);
                return;
            }
        }
        filterChain.doFilter(servletRequest, servletResponse);

    }

    public void destroy() {
        LOG.info("Ending GuestFilter");
    }

    public FilterConfig getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(FilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }
}
