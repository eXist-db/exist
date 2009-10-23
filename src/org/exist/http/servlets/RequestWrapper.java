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
package org.exist.http.servlets;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.Principal;
import java.util.Enumeration;

import javax.servlet.http.Cookie;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public interface RequestWrapper {

    public Object getAttribute(String name);

    public Enumeration getAttributeNames();
    
    public String getCharacterEncoding();
	
	public int getContentLength();
	
	public InputStream getInputStream() throws IOException;
	
	public Cookie[] getCookies();
	
	public String getContentType();
	
	public String getContextPath();
	
	public String getHeader(String arg0);
	
	public Enumeration getHeaderNames();
	
	public Enumeration getHeaders(String arg0);
	
	public String getMethod();
	
	public String getParameter(String arg0);
	
	public Enumeration getParameterNames();
	
	public String[] getParameterValues(String arg0);
	
	public File getFileUploadParam(String parameter);
	
	public String getUploadedFileName(String parameter);
	
	public String getPathInfo();
	
	public String getPathTranslated();
	
	public String getProtocol();
	
	public String getQueryString();
	
	public String getRemoteAddr();
	
	public String getRemoteHost();
	
	public int getRemotePort();
	
	public String getRemoteUser();
	
	public String getRequestedSessionId();
	
	public String getRequestURI();
	
	public StringBuffer getRequestURL();
	
	public String getScheme();
	
	public String getServerName();
	
	public int getServerPort();
	
	public String getServletPath();
	
	public SessionWrapper getSession();
	
	public SessionWrapper getSession(boolean arg0);
	
	public Principal getUserPrincipal();
	
	public boolean isRequestedSessionIdFromCookie();
	
	public boolean isRequestedSessionIdFromURL();
	
	public boolean isRequestedSessionIdValid();
	
	public boolean isSecure();
	
	public boolean isUserInRole(String arg0);
	
	public void removeAttribute(String arg0);
	
	public void setAttribute(String arg0, Object arg1);
	
	public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException;
}
