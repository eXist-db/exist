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

package org.exist.cocoon;

import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import org.apache.cocoon.environment.Cookie;
import org.apache.cocoon.environment.Response;
import org.apache.cocoon.environment.http.HttpCookie;
import org.apache.cocoon.environment.http.HttpResponse;
import org.exist.http.servlets.ResponseWrapper;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class CocoonResponseWrapper implements ResponseWrapper {

	private Response response;
	
	/**
	 * 
	 */
	public CocoonResponseWrapper(Response response) {
		this.response = response;
	}

	/**
	 * @param name Name of the Cookie
	 * @param value Value of the Cookie
	 */
	public void addCookie(String name, String value)
	{
		response.addCookie(new HttpCookie(name, value));
	}

	/**
	 * @param name Name of the Cookie
	 * @param value Value of the Cookie
	 * @param maxAge MaxAge of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge)
	{
	    HttpCookie cookie = new HttpCookie(name, value);
	    cookie.setMaxAge(maxAge);
	    response.addCookie(cookie);
	}
	
	/**
	 * @param name Name of the Cookie
	 * @param value Value of the Cookie
	 * @param maxAge MaxAge of the Cookie
	 * @param secure security of the Cookie
	 */
	public void addCookie(String name, String value, int maxAge, boolean secure)
	{
	    HttpCookie cookie = new HttpCookie(name, value);
	    cookie.setMaxAge(maxAge);
		cookie.setSecure( secure );
	    response.addCookie(cookie);
	}
	
	/**
	 * @param arg0
	 */
	public void addCookie(Cookie arg0) {
		response.addCookie(arg0);
	}

	/**
	 * @param contentType Content Type of the response
	 */
	public void setContentType(String contentType)
	{
		response.setHeader("Content-Type", contentType);
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
		response.addHeader(arg0, arg1);
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
	 */
	public boolean containsHeader(String arg0) {
		return response.containsHeader(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public Cookie createCookie(String arg0, String arg1) {
		return response.createCookie(arg0, arg1);
	}

	/**
	 * @param arg0
	 */
	public String encodeURL(String arg0) {
		return response.encodeURL(arg0);
	}

	/**
	 * Does nothing!
	 */
	public void flushBuffer() throws IOException
	{
	}
	
	/**
	 */
	public String getCharacterEncoding() {
		return response.getCharacterEncoding();
	}

	/**
	 */
	public Locale getLocale() {
		return response.getLocale();
	}

	/** Note: all this is pasted from class HttpResponseWrapper,
	 * but response is from a different class; no simple re-use of code possible.  :-( */
	private Map dateHeaders = new HashMap();
	/**
	 * @param name
	 * @param arg1
	 */
	public void setDateHeader(String name, long arg1) {
		dateHeaders.put(name, new Long(arg1) );
		response.setDateHeader(name, arg1);
	}
	/** @return the value of Date Header corresponding to given name,
	 * 0 if none has been set. */
	public long getDateHeader(String name) {
		long ret = 0;
		Long val = (Long)dateHeaders.get(name);
		if ( val != null )
			ret = val.longValue();
		return ret;
	}
	
	
	/**
	 * @return returns isCommitted
	 */
	public boolean isCommitted() {
		boolean committed = false;
		
		if( response instanceof HttpResponse ) {
			committed = ((HttpResponse)response).isCommitted();
		}
		
		return( committed );
	}
	
	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setHeader(String arg0, String arg1) {
		response.setHeader(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setIntHeader(String arg0, int arg1) {
		response.setIntHeader(arg0, arg1);
	}
    
    /**
     * @param arg0
     */
    public void setStatusCode(int arg0) {
        if( response instanceof HttpResponse ) {
            ((HttpResponse)response).setStatus(arg0);
        }
    }

	/**
	 * @param arg0
	 */
	public void setLocale(Locale arg0) {
		response.setLocale(arg0);
	}

	/* (non-Javadoc)
	 * @see org.exist.http.ResponseWrapper#sendRedirect(java.lang.String)
	 */
	public void sendRedirect(String arg0) throws IOException {
	}

    public OutputStream getOutputStream() throws IOException {
        return null;
    }
}
