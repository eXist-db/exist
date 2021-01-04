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

import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Locale;

import static java.nio.charset.StandardCharsets.ISO_8859_1;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class HttpResponseWrapper implements ResponseWrapper {
	
	private final HttpServletResponse response;

	/**
	 * Used the feature "Guess last modification time for an XQuery result"
	 */
	private Object2LongMap<String> dateHeaders = null;
	private static final long NO_SUCH_DATE_HEADER = -1;

	/**
	 * @param response the http response
	 */
	public HttpResponseWrapper(final HttpServletResponse response) {
		this.response = response;
	}
	
	@Override
	public void addCookie(final String name, final String value) {
		response.addCookie(new Cookie(name, encode(value)));
	}

	@Override
	public void addCookie(final String name, final String value, final int maxAge) {
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		response.addCookie(cookie);
	}
	
	@Override
	public void addCookie(final String name, final String value, final int maxAge, final boolean secure) {
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		cookie.setSecure(secure);
		response.addCookie(cookie);
	}
	
	@Override
	public void addCookie(final String name, final String value, final int maxAge, boolean secure, final String domain, final String path) {
		final Cookie cookie = new Cookie(name, encode(value));
		cookie.setMaxAge(maxAge);
		cookie.setSecure( secure );
		if (domain != null) {
			cookie.setDomain(domain);
		}
		if (path != null) {
			cookie.setPath(path);
		}
		response.addCookie(cookie);
	}
	
	@Override
	public void setContentType(final String type)
	{
		response.setContentType(type);
	}
	
	@Override
	public void addDateHeader(final String name, final long date) {
		response.addDateHeader(name, date);
	}
	
	@Override
	public void addHeader(final String name, final String value) {
		response.addHeader(name, encode(value));
	}
	
	@Override
	public void addIntHeader(final String name, final int value) {
		response.addIntHeader(name, value);
	}
	
	@Override
	public boolean containsHeader(final String name) {
		return response.containsHeader(name);
	}
	
	@Override
	public String encodeURL(final String s) {
		return response.encodeURL(s);
	}

	@Override
	public void flushBuffer() throws IOException {
		response.flushBuffer();
	}
	
	@Override
	public String getCharacterEncoding() {
		return response.getCharacterEncoding();
	}
	
	@Override
	public Locale getLocale() {
		return response.getLocale();
	}

	@Override
	public boolean isCommitted() {
		return response.isCommitted();
	}
	
	@Override
	public void sendRedirect(final String location) throws IOException {
		response.sendRedirect(location);
	}

	@Override
	public void setDateHeader(final String name, final long date) {
		if (dateHeaders == null) {
			dateHeaders = new Object2LongOpenHashMap<>();
			dateHeaders.defaultReturnValue(NO_SUCH_DATE_HEADER);
		}
		dateHeaders.put(name, date);
		response.setDateHeader(name, date);
	}

	@Override
	public long getDateHeader(final String name) {
		long ret = 0;
		final long val = dateHeaders != null ? dateHeaders.getLong(name) : NO_SUCH_DATE_HEADER;
		if (val != NO_SUCH_DATE_HEADER) {
			ret = val;
		}
		return ret;
	}
	
	@Override
	public void setHeader(final String name, final String value) {
		response.setHeader(name, encode(value));
	}

	@Override
	public void setIntHeader(final String name, final int value) {
		response.setIntHeader(name, value);
	}

	@Override
	public void sendError(final int sc) throws IOException {
		response.sendError(sc);
	}

	@Override
	public void sendError(final int sc, final String msg) throws IOException {
		response.sendError(sc, msg);
	}

	@Override
	public void setStatusCode(final int sc) {
		response.setStatus(sc);
	}
	
	@Override
	public void setLocale(final Locale locale) {
		response.setLocale(locale);
	}

	@Override
	public OutputStream getOutputStream() throws IOException {
		return response.getOutputStream();
	}
	
	// TODO: remove this hack after fixing HTTP 1.1 :)
	private String encode(final String value){
        return new String(value.getBytes(), ISO_8859_1);
	}
}
