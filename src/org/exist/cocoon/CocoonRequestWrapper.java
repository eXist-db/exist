/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
import java.util.Locale;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.apache.cocoon.environment.Request;
import org.apache.cocoon.environment.Session;
import org.apache.cocoon.servlet.multipart.Part;
import org.exist.http.servlets.RequestWrapper;
import org.exist.http.servlets.SessionWrapper;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class CocoonRequestWrapper implements RequestWrapper {

	private Request request;
	private HttpServletRequest httpRequest = null;
	
	/**
	 * 
	 */
	public CocoonRequestWrapper(Request request) {
		this.request = request;
	}

	public CocoonRequestWrapper(Request request, HttpServletRequest httpRequest) {
		this.request = request;
		this.httpRequest = httpRequest;
	}
	
	/* (non-Javadoc)
	 * @see org.exist.http.servlets.RequestWrapper#getInputStream()
	 */
	public InputStream getInputStream() throws IOException {
		if(httpRequest == null)
			throw new IOException("Request input stream is only available " +
				"within a servlet environment");
		return httpRequest.getInputStream();
	}
	
	/**
	 * @param arg0
	 * @return
	 */
	public Object get(String arg0) {
		return request.get(arg0);
	}

	/**
	 * @return
	 */
	public String getCharacterEncoding() {
		return request.getCharacterEncoding();
	}

	/**
	 * @return
	 */
	public int getContentLength() {
		return request.getContentLength();
	}

	/**
	 * @return
	 */
	public String getContentType() {
		return request.getContentType();
	}

	/**
	 * @return
	 */
	public String getContextPath() {
		return request.getContextPath();
	}

	/**
	 * @return
	 */
	public Map getCookieMap() {
		return request.getCookieMap();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public long getDateHeader(String arg0) {
		return request.getDateHeader(arg0);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public String getHeader(String arg0) {
		return request.getHeader(arg0);
	}

	/**
	 * @return
	 */
	public Enumeration getHeaderNames() {
		return request.getHeaderNames();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public Enumeration getHeaders(String arg0) {
		return request.getHeaders(arg0);
	}

	/**
	 * @return
	 */
	public Locale getLocale() {
		return request.getLocale();
	}

	/**
	 * @return
	 */
	public Enumeration getLocales() {
		return request.getLocales();
	}

	/**
	 * @return
	 */
	public String getMethod() {
		return request.getMethod();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public String getParameter(String arg0) {
		return request.getParameter(arg0);
	}

	/**
	 * @return
	 */
	public Enumeration getParameterNames() {
		return request.getParameterNames();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public String[] getParameterValues(String arg0) {
		return request.getParameterValues(arg0);
	}

	/**
	 * @return
	 */
	public String getPathInfo() {
		return request.getPathInfo();
	}

	/**
	 * @return
	 */
	public String getPathTranslated() {
		return request.getPathTranslated();
	}

	/**
	 * @return
	 */
	public String getProtocol() {
		return request.getProtocol();
	}

	/**
	 * @return
	 */
	public String getQueryString() {
		return request.getQueryString();
	}

	/**
	 * @return
	 */
	public String getRemoteAddr() {
		return request.getRemoteAddr();
	}

	/**
	 * @return
	 */
	public String getRemoteHost() {
		return request.getRemoteHost();
	}

	/**
	 * @return
	 */
	public String getRemoteUser() {
		return request.getRemoteUser();
	}

	/**
	 * @return
	 */
	public String getRequestedSessionId() {
		return request.getRequestedSessionId();
	}

	/**
	 * @return
	 */
	public String getRequestURI() {
		return request.getRequestURI();
	}

	/**
	 * @return
	 */
	public String getScheme() {
		return request.getScheme();
	}

	/**
	 * @return
	 */
	public String getServerName() {
		return request.getServerName();
	}

	/**
	 * @return
	 */
	public int getServerPort() {
		return request.getServerPort();
	}

	/**
	 * @return
	 */
	public String getServletPath() {
		return request.getServletPath();
	}

	/**
	 * @return
	 */
	public SessionWrapper getSession() {
		Session session = request.getSession();
		if(session == null)
			return null;
		else
			return new CocoonSessionWrapper(session);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public SessionWrapper getSession(boolean arg0) {
		Session session = request.getSession(arg0);
		if(session == null)
			return null;
		else
			return new CocoonSessionWrapper(session);
	}

	/**
	 * @return
	 */
	public String getSitemapURI() {
		return request.getSitemapURI();
	}

	/**
	 * @return
	 */
	public Principal getUserPrincipal() {
		return request.getUserPrincipal();
	}

	/**
	 * @return
	 */
	public boolean isRequestedSessionIdFromCookie() {
		return request.isRequestedSessionIdFromCookie();
	}

	/**
	 * @return
	 */
	public boolean isRequestedSessionIdFromURL() {
		return request.isRequestedSessionIdFromURL();
	}

	/**
	 * @return
	 */
	public boolean isRequestedSessionIdValid() {
		return request.isRequestedSessionIdValid();
	}

	/**
	 * @return
	 */
	public boolean isSecure() {
		return request.isSecure();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public boolean isUserInRole(String arg0) {
		return request.isUserInRole(arg0);
	}

	/**
	 * @param arg0
	 */
	public void removeAttribute(String arg0) {
		request.removeAttribute(arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 */
	public void setAttribute(String arg0, Object arg1) {
		request.setAttribute(arg0, arg1);
	}

	/**
	 * @param arg0
	 * @throws java.io.UnsupportedEncodingException
	 */
	public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
		request.setCharacterEncoding(arg0);
	}

	/* (non-Javadoc)
	 * @see org.exist.http.servlets.RequestWrapper#getFileUploadParam(java.lang.String)
	 */
	public File getFileUploadParam(String parameter) {
		 Object param = request.get(parameter);
		 if(param == null)
		 	return null;
		 if(param instanceof Part) {
		 	Part part = (Part) param;
		 	try {
				File temp = File.createTempFile("exist", ".xml");
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

	/* (non-Javadoc)
	 * @see org.exist.http.servlets.RequestWrapper#getUploadedFileName(java.lang.String)
	 */
	public String getUploadedFileName(String parameter) {
		 Object param = request.get(parameter);
		 if(param == null)
		 	return null;
		 if(param instanceof Part) {
		 	Part part = (Part) param;
		 	return new File(part.getUploadName()).getName();
		 }
		 return null;
	}

}
