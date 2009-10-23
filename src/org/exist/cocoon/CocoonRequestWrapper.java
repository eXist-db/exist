/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.cocoon;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;

import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.servlet.multipart.Part;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;

/** A wrapper for requests processed by Cocoon.
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class CocoonRequestWrapper implements RequestWrapper {

	private Request cocoonRequest;
	private HttpServletRequest servletRequest = null;
	
	/**
	 * Constructs a wrapper for the given Cocoon request.
	 * @param cocoonRequest The request as viewed by Cocoon.
	 */
	public CocoonRequestWrapper(Request cocoonRequest) {
		this.cocoonRequest = cocoonRequest;
    }

	/** Constructs a wrapper for the given Cocoon request.
	 * @param cocoonRequest The request as viewed by Cocoon.
	 * @param servletRequest The request as viewed by Cocoon's servlet
	 */
	public CocoonRequestWrapper(Request cocoonRequest, HttpServletRequest servletRequest) {
		this.cocoonRequest = cocoonRequest;
		this.servletRequest = servletRequest;
	}

    public Object getAttribute(String name) {
        return this.cocoonRequest.getAttribute(name);
    }

    public Enumeration getAttributeNames() {
        return this.cocoonRequest.getAttributeNames();
    }

    public Cookie[] getCookies()
	{
		return servletRequest.getCookies();
	}
	
	/** 
	 * @see javax.servlet.http.HttpServletRequest#getInputStream()
	 */	
	public InputStream getInputStream() throws IOException {
		if(servletRequest == null)
			throw new IOException("Request input stream is only available " +
				"within a servlet environment");
		return servletRequest.getInputStream();
	}
	
	/**
	 * @see org.apache.cocoon.environment.Request#get(String)
	 */
	public Object get(String arg0) {
		return cocoonRequest.get(arg0);
	}

	/**
	 * @see org.apache.cocoon.environment.Request#getCharacterEncoding()
	 */
	public String getCharacterEncoding() {
		return cocoonRequest.getCharacterEncoding();
	}

	/**@see org.apache.cocoon.environment.Request#getContentLength()
	 */
	public int getContentLength() {
		return cocoonRequest.getContentLength();
	}

	/**@see org.apache.cocoon.environment.Request#getContentType()
	 */
	public String getContentType() {
		return cocoonRequest.getContentType();
	}

	/**@see org.apache.cocoon.environment.Request#getContextPath()
	 */
	public String getContextPath() {
		return cocoonRequest.getContextPath();
	}

	/**@see org.apache.cocoon.environment.Request#getCookieMap()
	 */
	public Map getCookieMap() {
		return cocoonRequest.getCookieMap();
	}

	/**@see org.apache.cocoon.environment.Request#getHeader(String)
	 */
	public String getHeader(String arg0) {
		return cocoonRequest.getHeader(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#getHeaderNames()
	 */
	public Enumeration getHeaderNames() {
		return cocoonRequest.getHeaderNames();
	}

	/**@see org.apache.cocoon.environment.Request#getHeaders(String)
	 */
	public Enumeration getHeaders(String arg0) {
		return cocoonRequest.getHeaders(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#getMethod()
	 */
	public String getMethod() {
		return cocoonRequest.getMethod();
	}

	/**@see org.apache.cocoon.environment.Request#getParameter(String)
	 */
	public String getParameter(String arg0) {
		return cocoonRequest.getParameter(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#getParameterNames()
	 */
	public Enumeration getParameterNames() {
		return cocoonRequest.getParameterNames();
	}

	/**@see org.apache.cocoon.environment.Request#getParameterValues(String)
	 */
	public String[] getParameterValues(String arg0) {
		return cocoonRequest.getParameterValues(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#getPathInfo()
	 */
	public String getPathInfo() {
		return cocoonRequest.getPathInfo();
	}

	/**@see org.apache.cocoon.environment.Request#getPathTranslated()
	 */
	public String getPathTranslated() {
		return cocoonRequest.getPathTranslated();
	}

	/**@see org.apache.cocoon.environment.Request#getProtocol()
	 */
	public String getProtocol() {
		return cocoonRequest.getProtocol();
	}

	/**@see org.apache.cocoon.environment.Request#getQueryString()
	 */
	public String getQueryString() {
		return cocoonRequest.getQueryString();
	}

	/**@see org.apache.cocoon.environment.Request#getRemoteAddr()
	 */
	public String getRemoteAddr() {
		return cocoonRequest.getRemoteAddr();
	}

	/**@see org.apache.cocoon.environment.Request#getRemoteHost()
	 */
	public String getRemoteHost() {
		return cocoonRequest.getRemoteHost();
	}

	/**
		As Cocoon does not implement the concept of remote port,
		give at least the server port. Beware, it could be wrong!!!!
	 */
	public int getRemotePort() {
		return cocoonRequest.getServerPort();
	}

	/**@see org.apache.cocoon.environment.Request#getRemoteUser()
	 */
	public String getRemoteUser() {
		return cocoonRequest.getRemoteUser();
	}

	/**@see org.apache.cocoon.environment.Request#getRequestedSessionId()
	 */
	public String getRequestedSessionId() {
		return cocoonRequest.getRequestedSessionId();
	}

	/**@see org.apache.cocoon.environment.Request#getRequestURI()
	 */
	public String getRequestURI() {
		return cocoonRequest.getRequestURI();
	}
	
	/**@see javax.servlet.http.HttpServletRequest#getRequestURL()
	 */
	public StringBuffer getRequestURL() {
		//TODO : check accuracy
		if (this.servletRequest == null) 
			return null;
		return this.servletRequest.getRequestURL();
	}	

	/**@see org.apache.cocoon.environment.Request#getScheme()
	 */
	public String getScheme() {
		return cocoonRequest.getScheme();
	}

	/**@see org.apache.cocoon.environment.Request#getServerName()
	 */
	public String getServerName() {
		return cocoonRequest.getServerName();
	}

	/**@see org.apache.cocoon.environment.Request#getServerPort()
	 */
	public int getServerPort() {
		return cocoonRequest.getServerPort();
	}

	/**@see org.apache.cocoon.environment.Request#getServletPath()()
	 */
	public String getServletPath() {
		return cocoonRequest.getServletPath();
	}

	/**@see org.apache.cocoon.environment.Request#getSession()
	 */
	public SessionWrapper getSession() {
		Session session = cocoonRequest.getSession();
		if(session == null)
			return null;
		else
			return new CocoonSessionWrapper(session);
	}

	/**@see org.apache.cocoon.environment.Request#getSession(boolean)
	 */
	public SessionWrapper getSession(boolean arg0) {
		Session session = cocoonRequest.getSession(arg0);
		if(session == null)
			return null;
		else
			return new CocoonSessionWrapper(session);
	}

	/**@see org.apache.cocoon.environment.Request#getSitemapURI()
	 */
	public String getSitemapURI() {
		return cocoonRequest.getSitemapURI();
	}

	/**@see org.apache.cocoon.environment.Request#getUserPrincipal()
	 */
	public Principal getUserPrincipal() {
		return cocoonRequest.getUserPrincipal();
	}

	/**@see org.apache.cocoon.environment.Request#isRequestedSessionIdFromCookie()
	 */
	public boolean isRequestedSessionIdFromCookie() {
		return cocoonRequest.isRequestedSessionIdFromCookie();
	}

	/**@see org.apache.cocoon.environment.Request#isRequestedSessionIdFromURL()
	 */
	public boolean isRequestedSessionIdFromURL() {
		return cocoonRequest.isRequestedSessionIdFromURL();
	}

	/**@see org.apache.cocoon.environment.Request#isRequestedSessionIdValid()
	 */
	public boolean isRequestedSessionIdValid() {
		return cocoonRequest.isRequestedSessionIdValid();
	}

	/**@see org.apache.cocoon.environment.Request#isSecure()
	 */
	public boolean isSecure() {
		return cocoonRequest.isSecure();
	}

	/**@see org.apache.cocoon.environment.Request#isUserInRole(String)
	 */
	public boolean isUserInRole(String arg0) {
		return cocoonRequest.isUserInRole(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#removeAttribute(String)
	 */
	public void removeAttribute(String arg0) {
		cocoonRequest.removeAttribute(arg0);
	}

	/**@see org.apache.cocoon.environment.Request#setAttribute(String, Object)
	 */
	public void setAttribute(String arg0, Object arg1) {
		cocoonRequest.setAttribute(arg0, arg1);
	}

	/**@see org.apache.cocoon.environment.Request#setCharacterEncoding(String)
	 */
	public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
		cocoonRequest.setCharacterEncoding(arg0);
	}

	/* 
	 * @see org.apache.cocoon.environment.Request#getFileUploadParam(String)
	 */
	public File getFileUploadParam(String parameter) {
		 Object param = cocoonRequest.get(parameter);
		 if(param == null)
		 	return null;
		 if(param instanceof Part) {
		 	Part part = (Part) param;
		 	try {
				File temp = File.createTempFile("existCRW", ".xml");
				temp.deleteOnExit();
				OutputStream os = new FileOutputStream(temp);
				InputStream is = part.getInputStream();
				byte[] data = new byte[1024];
				int read = 0;
				while((read = is.read(data)) > -1) {
					os.write(data, 0, read);
				}
				is.close();
				part.dispose();
				return temp;
			} catch (Exception e) {
				e.printStackTrace();
			}
		 }
		 return null;
	}

	/* 
	 * @see org.apache.cocoon.environment.Request#getUploadedFileName(String)
	 */
	public String getUploadedFileName(String parameter) {
		 Object param = cocoonRequest.get(parameter);
		 if(param == null)
		 	return null;
		 if(param instanceof Part) {
		 	Part part = (Part) param;
		 	return new File(part.getUploadName()).getName();
		 }
		 return null;
	}

}
