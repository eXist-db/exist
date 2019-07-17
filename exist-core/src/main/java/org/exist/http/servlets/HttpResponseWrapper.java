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
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class HttpResponseWrapper implements ResponseWrapper {
	
	private HttpServletResponse response;
	
	/**
	 * @param response the http response
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
	 * Add a date header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addDateHeader(String name, long value) {
		response.addDateHeader(name, value);
	}
	
	/**
	 * Add a header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addHeader(String name, String value) {
		response.addHeader(name, encode(value));
	}
	
	/**
	 * Add a int header.
	 *
	 * @param name the header name
	 * @param value the value of the header
	 */
	public void addIntHeader(String name, int value) {
		response.addIntHeader(name, value);
	}
	
	/**
	 * Returns true of the response contains the header.
	 *
	 * @param name the header name
	 * @return a boolean indicating whether the header is present
	 */
	public boolean containsHeader(String name) {
		return response.containsHeader(name);
	}
	
	/**
	 * Encode a String as a URL.
	 *
	 * @param s the string to encode
	 * @return the encoded value
	 */
	public String encodeURL(String s) {
		return response.encodeURL(s);
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
	 * Send a HTTP Reedirect.
	 *
	 * @param url the URL to redirect to
	 * @throws IOException if an I/O error occurs
	 */
	public void sendRedirect(String url) throws IOException {
		response.sendRedirect(url);
	}
	
	/** used the feature "Guess last modification time for an XQuery result" */
	private Map<String, Long> dateHeaders = new HashMap<String, Long>();

	/**
	 * Set a date header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setDateHeader(String name, long value) {
		dateHeaders.put(name, Long.valueOf(value) );
		response.setDateHeader(name, value);
	}

	/**
	 * Get a date header.
	 *
	 * @param name the header name
	 *
	 * @return the value of Date Header corresponding to given name, 0 if none has been set.
	 */
	public long getDateHeader(String name) {
		long ret = 0;
		final Long val = dateHeaders.get(name);
		if ( val != null )
			{ret = val.longValue();}
		return ret;
	}
	
	/**
	 * Set a header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setHeader(String name, String value) {
		response.setHeader(name, encode(value));
	}

	/**
	 * Set an int header.
	 *
	 * @param name the header name
	 * @param value the header value
	 */
	public void setIntHeader(String name, int value) {
		response.setIntHeader(name, value);
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
	 * Set the HTP Status Code
	 *
     * @param statusCode the status code.
     */
	public void setStatusCode(int statusCode) {
		response.setStatus(statusCode);
	}
	
	/**
	 * Set the locale.
	 *
	 * @param locale the locale.
	 */
	public void setLocale(Locale locale) {
		response.setLocale(locale);
	}
	
	public OutputStream getOutputStream() throws IOException {
		return response.getOutputStream();
	}
	
	// TODO: remove this hack after fixing HTTP 1.1 :)
	private String encode(String value){
        return new String(value.getBytes(), ISO_8859_1);
	}
}
