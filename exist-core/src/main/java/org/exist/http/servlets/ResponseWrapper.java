/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2006 The eXist team
 *  http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */

package org.exist.http.servlets;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public interface ResponseWrapper {
	
	/**
	 * Add a cookie.
	 *
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 */
	public void addCookie(String name, String value);

	/**
	 * Add a cookie.
	 *
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 * @param maxAge maxAge of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge);
	
	/**
	 * Add a cookie.
	 *
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 * @param maxAge maxAge of the Cookie
	 * @param secure security of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge, boolean secure);
	
	/**
	 * Add a cookie.
	 *
	 * @param name Name of the Cookie
	 * @param value Value of the Cookie
	 * @param maxAge an <code>int</code> value
	 * @param secure security of the Cookie
	 * @param domain domain of the cookie
	 * @param path path scope of the cookie
	 */
	public void addCookie(String name, String value, int maxAge, boolean secure, String domain, String path);

	/**
	 * Add a date header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addDateHeader(String name, long value);

	/**
	 * Add a header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addHeader(String name, String value);

	/**
	 * Add a int header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addIntHeader(String name, int value);

	/**
	 * Returns true of the response contains the header.
	 *
	 * @param name the header name
	 * @return a boolean indicating whether the header is present
	 */
	public boolean containsHeader(String name);

	/**
	 * Encode a String as a URL.
	 *
	 * @param s the string to encode
	 * @return the encoded value
	 */
	public String encodeURL(String s);


	public void flushBuffer() throws IOException;

	/**
	 * Get the character encoding.
	 *
	 * @return returns the default character encoding
	 */
	public String getCharacterEncoding();

	/**
	 * @return returns the default locale
	 */
	public Locale getLocale();
	
	/**
	 * @return returns isCommitted
	 */
	public boolean isCommitted();
	
	/**
	 * @param contentType Content Type of the response
	 */
	public void setContentType(String contentType);

	/**
	 * Set a date header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setDateHeader(String name, long value);

	/**
	 * Set a header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setHeader(String name, String value);

	/**
	 * Set an int header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setIntHeader(String name, int value);

	void sendError(final int code) throws IOException;

	void sendError(final int code, final String msg) throws IOException;

	/**
	 * Set the HTP Status Code
	 *
	 * @param statusCode the status code.
	 */
    public void setStatusCode(int statusCode);

	/**
	 * Set the locale.
	 *
	 * @param locale the locale.
	 */
	public void setLocale(Locale locale);
	
	public void sendRedirect(String url) throws IOException;
	
	/**
	 * Get a date header.
	 *
	 * @param name the header name
	 *
	 * @return the value of Date Header corresponding to given name,0 if none has been set.
	 */
	public long getDateHeader(String name);
    
    public OutputStream getOutputStream() throws IOException;
}
