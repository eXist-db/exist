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
     * Forces any content in the buffer to be written to the client.
     *
     * @see javax.servlet.ServletResponse#flushBuffer()
     *
     * @throws IOException if an I/O error occurs.
     */
    void flushBuffer() throws IOException;

    /**
     * Returns the name of the character encoding (MIME charset) used for the
     * body sent in this response.
     *
     * @see javax.servlet.ServletResponse#getCharacterEncoding()
     *
     * @return a String specifying the name of the character encoding,
     *     for example, UTF-8.
     */
    String getCharacterEncoding();

    /**
     * Returns the locale specified for this response using the
     * {@link #setLocale(Locale)} method.
     *
     * @see javax.servlet.ServletResponse#getLocale()
     *
     * @return the locale.
     */
    Locale getLocale();

    /**
     * Sets the locale of the response, if the response has not been committed yet.
     *
     * @see javax.servlet.ServletResponse#setLocale(Locale)
     *
     * @param loc the locale of the response.
     */
    void setLocale(Locale loc);

    /**
     * Sets the content type of the response being sent to the client, if the
     * response has not been committed yet.
     *
     * @see javax.servlet.ServletResponse#setContentType(String)
     *
     * @param type a String specifying the MIME type of the content.
     */
    void setContentType(String type);

    /**
     * Returns a ServletOutputStream suitable for writing binary data in the
     * response. The servlet container does not encode the binary data.
     *
     * @see javax.servlet.ServletResponse#getOutputStream()
     *
     * @return a ServletOutputStream for writing binary data.
     *
     * @throws IOException if an input or output exception occurred.
     */
    OutputStream getOutputStream() throws IOException;

    /**
     * Returns a boolean indicating if the response has been committed.
     *
     * @see javax.servlet.ServletResponse#isCommitted()
     *
     * @return a boolean indicating if the response has been committed.
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
     * Sets a response header with the given name and date-value.
     *
     * @see javax.servlet.http.HttpServletResponse#setDateHeader(String, long)
     *
     * @param name the name of the header to set.
     * @param date the assigned date value.
     */
    void setDateHeader(String name, long date);

    /**
     * Adds a response header with the given name and date-value.
     *
     * @see javax.servlet.http.HttpServletResponse#addDateHeader(String, long)
     *
     * @param name the name of the header to set.
     * @param date the additional date value.
     */
    void addDateHeader(String name, long date);

    /**
     * Sets a response header with the given name and integer value.
     *
     * @see javax.servlet.http.HttpServletResponse#setIntHeader(String, int)
     *
     * @param name the name of the header.
     * @param value the assigned integer value.
     */
    void setIntHeader(String name, int value);

    /**
     * Adds a response header with the given name and integer value.
     *
     * @see javax.servlet.http.HttpServletResponse#addIntHeader(String, int)
     *
     * @param name the name of the header.
     * @param value the assigned integer value.
     */
    void addIntHeader(String name, int value);

    /**
     * Adds a response header with the given name and value.
     *
     * @param name the name of the header.
     * @param value the assigned value.
     */
    void addHeader(String name, String value);

    /**
     * Returns a boolean indicating whether the named response header
     * has already been set.
     *
     * @see javax.servlet.http.HttpServletResponse#containsHeader(String)
     *
     * @param name the header name.
     *
     * @return true if the named response header has already been set;
     *     false otherwise.
     */
    boolean containsHeader(String name);

    /**
     * Sets a response header with the given name and value.
     *
     * @see javax.servlet.http.HttpServletResponse#setHeader(String, String)
     *
     * @param name the name of the header
     * @param value the header value If it contains octet string,
     *     it should be encoded according to RFC 2047 (http://www.ietf.org/rfc/rfc2047.txt).
     */
    void setHeader(String name, String value);

    /**
     * Encodes the specified URL by including the session ID, or,
     * if encoding is not needed, returns the URL unchanged.
     *
     * @see javax.servlet.http.HttpServletResponse#encodeURL(String)
     *
     * @param url the url to be encoded.
     *
     * @return the encoded URL if encoding is needed; the unchanged URL
     *     otherwise.
     */
    String encodeURL(String url);

    /**
     * Sends a temporary redirect response to the client using the specified
     * redirect location URL and clears the buffer.
     *
     * @see javax.servlet.http.HttpServletResponse#sendRedirect(String)
     *
     * @param location the redirect location URL.
     *
     * @throws IOException If an input or output exception occurs.
     */
    void sendRedirect(String location) throws IOException;

    /**
     * Sends an error response to the client using the specified status
     * code and clears the buffer.
     *
     * @see javax.servlet.http.HttpServletResponse#sendError(int)
     *
     * @param sc the error status code.
     *
     * @throws IOException If an input or output exception occurs.
     */
    void sendError(final int sc) throws IOException;

    /**
     * Sends an error response to the client using the specified status code
     * and clears the buffer.
     *
     * @see javax.servlet.http.HttpServletResponse#sendError(int, String)
     *
     * @param sc the error status code.
     * @param msg the descriptive message.
     *
     * @throws IOException If an input or output exception occurs.
     */
    void sendError(final int sc, final String msg) throws IOException;

    /**
     * Get a date header.
     *
     * @param name the header name.
     *
     * @return the value of Date Header corresponding to given name,
     *     0 if none has been set.
     */
    long getDateHeader(String name);

    /**
     * Set the HTP Status Code
     *
     * @param statusCode the status code.
     */
    void setStatusCode(int statusCode);
}
