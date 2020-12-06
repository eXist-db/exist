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

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Enumeration;
import java.util.List;

public interface RequestWrapper {

    /**
     * @see javax.servlet.ServletRequest#getAttributeNames()
     */
    Enumeration<String> getAttributeNames();

    /**
     * @see javax.servlet.ServletRequest#getAttribute(String)
     */
    Object getAttribute(String name);

    /**
     * @see javax.servlet.ServletRequest#setAttribute(String, Object)
     */
    void setAttribute(String name, Object o);

    /**
     * @see javax.servlet.ServletRequest#removeAttribute(String)
     */
    void removeAttribute(String name);

    /**
     * @see javax.servlet.ServletRequest#getCharacterEncoding()
     */
    String getCharacterEncoding();

    /**
     * @see javax.servlet.ServletRequest#setCharacterEncoding(String)
     */
    void setCharacterEncoding(String env) throws UnsupportedEncodingException;

    /**
     * @see javax.servlet.ServletRequest#getContentLength()
     */
    long getContentLength();

    /**
     * @see javax.servlet.ServletRequest#getContentLengthLong()
     */
    long getContentLengthLong();

    /**
     * @see javax.servlet.ServletRequest#getInputStream()
     */
    InputStream getInputStream() throws IOException;

    /**
     * @see javax.servlet.ServletRequest#getContentType()
     */
    String getContentType();

    /**
     * @see javax.servlet.ServletRequest#getParameterNames()
     */
    Enumeration<String> getParameterNames();

    /**
     * @see javax.servlet.ServletRequest#getParameter(String)
     */
    String getParameter(String name);

    /**
     * @see javax.servlet.ServletRequest#getParameterValues(String)
     */
    String[] getParameterValues(String name);

    /**
     * @see javax.servlet.ServletRequest#getProtocol()
     */
    String getProtocol();

    /**
     * @see javax.servlet.ServletRequest#getScheme()
     */
    String getScheme();

    /**
     * @see javax.servlet.ServletRequest#getServerName()
     */
    String getServerName();

    /**
     * @see javax.servlet.ServletRequest#getServerPort()
     */
    int getServerPort();

    /**
     * @see javax.servlet.ServletRequest#getRemoteAddr()
     */
    String getRemoteAddr();

    /**
     * @see javax.servlet.ServletRequest#getRemoteHost()
     */
    String getRemoteHost();

    /**
     * @see javax.servlet.ServletRequest#getRemotePort()
     */
    int getRemotePort();

    /**
     * @see javax.servlet.ServletRequest#isSecure()
     */
    boolean isSecure();

    /**
     * @see javax.servlet.ServletRequest#getRequestDispatcher(String)
     */
    RequestDispatcher getRequestDispatcher(final String path);

    /**
     * @see javax.servlet.http.HttpServletRequest#getCookies()
     */
    Cookie[] getCookies();

    /**
     * @see javax.servlet.http.HttpServletRequest#getContextPath()
     */
    String getContextPath();

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeaderNames()
     */
    Enumeration<String> getHeaderNames();

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeader(String)
     */
    String getHeader(String name);

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeaders(String)
     */
    Enumeration<String> getHeaders(String name);

    /**
     * @see javax.servlet.http.HttpServletRequest#getMethod()
     */
    String getMethod();

    /**
     * @see javax.servlet.http.HttpServletRequest#getPathInfo()
     */
    String getPathInfo();

    /**
     * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
     */
    String getPathTranslated();

    /**
     * @see javax.servlet.http.HttpServletRequest#getQueryString()
     */
    String getQueryString();

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
     */
    String getRemoteUser();

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    String getRequestedSessionId();

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    boolean isRequestedSessionIdFromCookie();

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    boolean isRequestedSessionIdFromURL();

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    boolean isRequestedSessionIdValid();

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURI()
     */
    String getRequestURI();

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURL()
     */
    StringBuffer getRequestURL();

    /**
     * @see javax.servlet.http.HttpServletRequest#getServletPath()
     */
    String getServletPath();

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession()
     */
    SessionWrapper getSession();

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession()
     */
    SessionWrapper getSession(boolean create);

    /**
     * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    Principal getUserPrincipal();

    /**
     * @see javax.servlet.http.HttpServletRequest#isUserInRole(String)
     */
    boolean isUserInRole(String role);

    boolean isMultipartContent();

    List<Path> getFileUploadParam(String parameter);

    List<String> getUploadedFileName(String parameter);
}
