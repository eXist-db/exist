package org.exist.http.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    private final static Logger LOG = LogManager.getLogger(GuestFilter.class);

    private String sslPort = null;

    private FilterConfig filterConfig = null;

    public void init(FilterConfig filterConfig) throws ServletException {
        LOG.info("Starting GuestFilter");
        setFilterConfig(filterConfig);
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
        final String requestURI = httpServletRequest.getRequestURI().trim();

        final HttpSession session = httpServletRequest.getSession(false);
        if (session != null) {
            LOG.info("session: " + session.toString());
            final Enumeration enumeration = session.getAttributeNames();

            while (enumeration.hasMoreElements()) {
                final String key = (String) enumeration.nextElement();
                final Object value = session.getAttribute(key);
                LOG.info("session attribute [" + key + "][" + value.toString() + "]");
                if (key.equalsIgnoreCase("_eXist_xmldb_user")) {
                    username = ((org.exist.security.internal.SubjectImpl)value).getUsername();
                    LOG.info("username [" + username + "]");
                }
            }
        } else {
            LOG.info("No valid session");
        }

        LOG.info("username [" + username + "]");
        LOG.info("requestURI [" + requestURI + "]");

        if (requestURI.indexOf("/webdav/") >= 0) {
            if (username != null && username.equalsIgnoreCase("guest")) {
                LOG.info("Permission denied to : " + requestURI);
                httpServletResponse.sendError(HttpServletResponse.SC_FORBIDDEN);
            } else if (!httpServletRequest.isSecure()) {
                final String serverName = httpServletRequest.getServerName();
                final String path = httpServletRequest.getRequestURI();
                final String newpath = "https://" + serverName + ":" + sslPort + path;
                LOG.info("Redirecting to SSL: " + newpath);
                httpServletResponse.sendRedirect(newpath);
            } else if (httpServletRequest.isSecure()) {
                LOG.info("Request is appropriate");
                filterChain.doFilter(servletRequest, servletResponse);
            }
            return;
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
        final Enumeration initParams = filterConfig.getInitParameterNames();

        // no initial parameters, so invoke the next filter in the chain
        if (initParams != null) {
            sslPort = "443";
            while (initParams.hasMoreElements()) {
                final String name = (String) initParams.nextElement();
                String value = filterConfig.getInitParameter(name);

                LOG.info("Parameter [" + name + "][" + value + "]");

                if (name.equalsIgnoreCase("sslport")) {
                    sslPort = value;
                }
            }
        }
    }
}
