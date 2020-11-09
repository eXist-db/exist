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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

public interface ResponseWrapper {

    /**
     * @see javax.servlet.ServletResponse#flushBuffer()
     */
    void flushBuffer() throws IOException;

    /**
     * @see javax.servlet.ServletResponse#getCharacterEncoding()
     */
    String getCharacterEncoding();

    /**
     * @see javax.servlet.ServletResponse#getLocale()
     */
    Locale getLocale();

    /**
     * @see javax.servlet.ServletResponse#setLocale(Locale)
     */
    void setLocale(Locale loc);

    /**
     * @see javax.servlet.ServletResponse#setContentType(String)
     */
    void setContentType(String type);

    /**
     * @see javax.servlet.ServletResponse#getOutputStream()
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * @see javax.servlet.ServletResponse#isCommitted()
     */
    boolean isCommitted();
	
    /**
     * Add a cookie.
     *
     * @param name  Name of the Cookie
     * @param value Value of the Cookie
     */
    void addCookie(String name, String value);

    /**
     * Add a cookie.
     *
     * @param name   Name of the Cookie
     * @param value  Value of the Cookie
     * @param maxAge maxAge of the Cookie
     */
    void addCookie(String name, String value, int maxAge);

    /**
     * Add a cookie.
     *
     * @param name   Name of the Cookie
     * @param value  Value of the Cookie
     * @param maxAge maxAge of the Cookie
     * @param secure security of the Cookie
     */
    void addCookie(String name, String value, int maxAge, boolean secure);

    /**
     * Add a cookie.
     *
     * @param name   Name of the Cookie
     * @param value  Value of the Cookie
     * @param maxAge an <code>int</code> value
     * @param secure security of the Cookie
     * @param domain domain of the cookie
     * @param path   path scope of the cookie
     */
    void addCookie(String name, String value, int maxAge, boolean secure, String domain, String path);

    /**
     * @see javax.servlet.http.HttpServletResponse#setDateHeader(String, long)
     */
    void setDateHeader(String name, long date);

    /**
     * @see javax.servlet.http.HttpServletResponse#addDateHeader(String, long)
     */
    void addDateHeader(String name, long date);

    /**
     * @see javax.servlet.http.HttpServletResponse#setIntHeader(String, int)
     */
    void setIntHeader(String name, int value);

    /**
     * @see javax.servlet.http.HttpServletResponse#addIntHeader(String, int)
     */
    void addIntHeader(String name, int value);

    /**
     * @see javax.servlet.http.HttpServletResponse#addDateHeader(String, long)
     */
    void addHeader(String name, String value);

    /**
     * @see javax.servlet.http.HttpServletResponse#containsHeader(String)
     */
    boolean containsHeader(String name);

    /**
     * @see javax.servlet.http.HttpServletResponse#setHeader(String, String)
     */
    void setHeader(String name, String value);

    /**
     * @see javax.servlet.http.HttpServletResponse#encodeURL(String)
     */
    String encodeURL(String s);

    /**
     * @see javax.servlet.http.HttpServletResponse#sendRedirect(String)
     */
    void sendRedirect(String location) throws IOException;

    /**
     * @see javax.servlet.http.HttpServletResponse#sendError(int)
     */
    void sendError(final int sc) throws IOException;

    /**
     * @see javax.servlet.http.HttpServletResponse#sendError(int, String)
     */
    void sendError(final int sc, final String msg) throws IOException;

    /**
     * Get a date header.
     *
     * @param name the header name
     * @return the value of Date Header corresponding to given name,0 if none has been set.
     */
    long getDateHeader(String name);

    /**
     * Set the HTP Status Code
     *
     * @param statusCode the status code.
     */
    void setStatusCode(int statusCode);
}
