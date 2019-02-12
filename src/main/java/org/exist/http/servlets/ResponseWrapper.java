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
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface ResponseWrapper {
	
	/**
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 */
	public void addCookie(String name, String value);

	/**
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 * @param maxAge maxAge of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge);
	
	/**
	 * @param name	Name of the Cookie
	 * @param value	Value of the Cookie
	 * @param maxAge maxAge of the Cookie
	 * @param secure security of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge, boolean secure);
	
	/**
	 * The method <code>addCookie</code>
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
	 * @param arg0
	 * @param arg1
	 */
	public void addDateHeader(String arg0, long arg1);
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void addHeader(String arg0, String arg1);
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void addIntHeader(String arg0, int arg1);
	/**
	 * @param arg0 The name of the header.
	 * @return A boolean value indicating whether it contains the header name.
	 */
	public boolean containsHeader(String arg0);
	/**
	 * @param arg0
	 * @return The encoded value
	 */
	public String encodeURL(String arg0);
	/***/
	public void flushBuffer() throws IOException;
	/**
	 * @return Returns the default character encoding
	 */
	public String getCharacterEncoding();
	/**
	 * @return Returns the default locale
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
	 * @param arg0
	 * @param arg1
	 */
	public void setDateHeader(String arg0, long arg1);
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setHeader(String arg0, String arg1);
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setIntHeader(String arg0, int arg1);

	void sendError(final int code) throws IOException;

	void sendError(final int code, final String msg) throws IOException;

    /**
     * @param arg0
     */
    public void setStatusCode(int arg0);
	/**
	 * @param arg0
	 */
	public void setLocale(Locale arg0);
	
	public void sendRedirect(String arg0) throws IOException;
	
	/** @return the value of Date Header corresponding to given name,
	 * 0 if none has been set. */
	public long getDateHeader(String name);
    
    public OutputStream getOutputStream() throws IOException;
}
