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
package org.exist.http.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.DefaultFileItem;
import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class HttpRequestWrapper implements RequestWrapper {
	
	private HttpServletRequest request;
	private String formEncoding = null;
	private String containerEncoding = null;
	
	private Hashtable params = null;
	
	/**
	 * 
	 */
	public HttpRequestWrapper(HttpServletRequest request, String formEncoding,
			String containerEncoding) {
		this.request = request;
		this.formEncoding = formEncoding;
		this.containerEncoding = containerEncoding;
		if(FileUpload.isMultipartContent(request)) {
			parseMultipartContent(request);
		}
	}

	
	/**
	 * @param request2
	 */
	private void parseMultipartContent(HttpServletRequest request) {
		DiskFileUpload upload = new DiskFileUpload();
		upload.setSizeThreshold(0);
		try {
			this.params = new Hashtable();
			List items = upload.parseRequest(request);
			for(Iterator i = items.iterator(); i.hasNext(); ) {
				FileItem next = (FileItem) i.next();
				this.params.put(next.getFieldName(), next);
			}
		} catch (FileUploadException e) {
			// TODO: handle this
			e.printStackTrace();
		}
	}


	/* (non-Javadoc)
	 * @see org.exist.http.servlets.RequestWrapper#getInputStream()
	 */
	public InputStream getInputStream() throws IOException {
		return request.getInputStream();
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
	public String getParameter(String name) {
		if(params == null) {
			String value = request.getParameter(name);
			if(formEncoding == null || value == null)
				return value;
			return decode(value);
		} else {
			FileItem item = (FileItem) params.get(name);
			if(item == null) {
				return null;
			}
			if(formEncoding == null)
				return item.getString();
			else
				try {
					return item.getString(formEncoding);
				} catch (UnsupportedEncodingException e) {
					return null;
				}
		}
	}
	
	public File getFileUploadParam(String name) {
		if(params == null)
			return null;
		FileItem item = (FileItem) params.get(name);
		if(item.isFormField())
			return null;
		return ((DefaultFileItem)item).getStoreLocation();
	}
	
	public String getUploadedFileName(String name) {
		if(params == null)
			return null;
		FileItem item = (FileItem) params.get(name);
		if(item.isFormField())
			return null;
		File f = new File(item.getName());
		return f.getName();
	}
	
	private String decode(String value) {
		if(containerEncoding == null)
			containerEncoding = "ISO-8859-1";
		try {
			byte[] bytes = value.getBytes(containerEncoding);
			return new String(bytes, formEncoding);
		} catch (UnsupportedEncodingException e) {
			return value;
		}
	}

	/**
	 * @return
	 */
	public Enumeration getParameterNames() {
		if(params == null)
			return request.getParameterNames();
		else {
			return params.keys();
		}
	}

	/**
	 * @param arg0
	 * @return
	 */
	public String[] getParameterValues(String key) {
		if(params == null) {
			String[] values = request.getParameterValues(key);
			if(formEncoding == null || values == null)
				return values;
			for(int i = 0; i < values.length; i++) {
				values[i] = decode(values[i]);
			}
			return values;
		} else {
			FileItem item = (FileItem)params.get(key);
			if(item == null)
				return null;
			String[] values = new String[1];
			try {
				values[0] = formEncoding == null ? item.getString() : item.getString(formEncoding);
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
			return values;
		}
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
		HttpSession session = request.getSession();
		if(session == null)
			return null;
		else
			return new HttpSessionWrapper(session);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public SessionWrapper getSession(boolean arg0) {
		HttpSession session = request.getSession(arg0);
		if(session == null)
			return null;
		else
			return new HttpSessionWrapper(session);
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

}
