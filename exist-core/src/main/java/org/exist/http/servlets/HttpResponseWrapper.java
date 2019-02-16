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
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class HttpResponseWrapper implements ResponseWrapper {
	
	private HttpServletResponse response;
	
	/**
	 * 
	 */
	public HttpResponseWrapper(HttpServletResponse response) {
		this.response = response;
	}
	
	/**
	 * @param name Name of the Cookie
	 * @param value Value of the Cookie
	 */
	public void addCookie(String name, String value)
	{
		response.addCookie(new Cookie(name, encode(value)));
	}
	
	/**
     * The method <code>addCookie</code>
     *
     * @param name Name of the Cookie
     * @param value Value of the Cookie
     * @param maxAge an <code>int</code> value
     */
	public void addCookie(final String name, final String value, final int maxAge)
	{
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		response.addCookie(cookie);
	}
	
	/**
     * The method <code>addCookie</code>
     *
     * @param name Name of the Cookie
     * @param value Value of the Cookie
     * @param maxAge an <code>int</code> value
	 * @param secure security of the Cookie
     */
	public void addCookie(final String name, final String value, final int maxAge, boolean secure)
	{
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		cookie.setSecure( secure );
		response.addCookie(cookie);
	}
	
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
	public void addCookie(final String name, final String value, final int maxAge, boolean secure, final String domain, final String path)
	{
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		cookie.setSecure( secure );
		if(domain!=null)
			{cookie.setDomain(domain);}
		if(path!=null)
			{cookie.setPath(path);}
		response.addCookie(cookie);
	}
	
	/**
	 * @param contentType Content Type of the response
	 */
	public void setContentType(String contentType)
	{
		response.setContentType(contentType);
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void addDateHeader(String arg0, long arg1) {
		response.addDateHeader(arg0, arg1);
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void addHeader(String arg0, String arg1) {
		response.addHeader(arg0, encode(arg1));
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void addIntHeader(String arg0, int arg1) {
		response.addIntHeader(arg0, arg1);
	}
	
	/**
	 * @param arg0
	 * @return a boolean indicating whether the header is present
	 */
	public boolean containsHeader(String arg0) {
		return response.containsHeader(arg0);
	}
	
	/**
	 * @param arg0
	 * @return the encoded value
	 */
	public String encodeURL(String arg0) {
		return response.encodeURL(arg0);
	}
	
	public void flushBuffer() throws IOException
	{
		response.flushBuffer();
	}
	
	/**
	 * @return returns the default character encoding
	 */
	public String getCharacterEncoding() {
		return response.getCharacterEncoding();
	}
	
	/**
	 * @return returns the locale
	 */
	public Locale getLocale() {
		return response.getLocale();
	}

	/**
	 * @return returns isCommitted
	 */
	public boolean isCommitted() {
		return response.isCommitted();
	}
	
	/**
	 * @param arg0
	 * @throws java.io.IOException
	 */
	public void sendRedirect(String arg0) throws IOException {
		response.sendRedirect(arg0);
	}
	
	/** used the feature "Guess last modification time for an XQuery result" */
	private Map<String, Long> dateHeaders = new HashMap<String, Long>();
	/**
	 * @param name
	 * @param arg1
	 */
	public void setDateHeader(String name, long arg1) {
		dateHeaders.put(name, Long.valueOf(arg1) );
		response.setDateHeader(name, arg1);
	}
	/** @return the value of Date Header corresponding to given name,
	 * 0 if none has been set. */
	public long getDateHeader(String name) {
		long ret = 0;
		final Long val = dateHeaders.get(name);
		if ( val != null )
			{ret = val.longValue();}
		return ret;
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setHeader(String arg0, String arg1) {
		response.setHeader(arg0, encode(arg1));
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setIntHeader(String arg0, int arg1) {
		response.setIntHeader(arg0, arg1);
	}

	@Override
	public void sendError(final int code) throws IOException {
		response.sendError(code);
	}

	@Override
	public void sendError(final int code, final String msg) throws IOException {
		response.sendError(code, msg);
	}

	/**
     * @param arg0
     */
	public void setStatusCode(int arg0) {
		response.setStatus(arg0);
	}
	
	/**
	 * @param arg0
	 */
	public void setLocale(Locale arg0) {
		response.setLocale(arg0);
	}
	
	public OutputStream getOutputStream() throws IOException {
		return response.getOutputStream();
	}
	
	// TODO: remove this hack after fixing HTTP 1.1 :)
	private String encode(String value){
        return new String(value.getBytes(), ISO_8859_1);
	}
}
