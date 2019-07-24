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

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
 public interface RequestWrapper {

     Object getAttribute(String name);

     Enumeration<String> getAttributeNames();
    
     String getCharacterEncoding();
	
	 long getContentLength();
	
	 InputStream getInputStream() throws IOException;
	
	 Cookie[] getCookies();
	
	 String getContentType();
	
	 String getContextPath();
	
	 String getHeader(String arg0);
	
	 Enumeration getHeaderNames();
	
	 Enumeration getHeaders(String arg0);
	
	 String getMethod();
	
	 String getParameter(String arg0);
	
	 Enumeration<String> getParameterNames();
	
	 String[] getParameterValues(String arg0);
	
	 List<Path> getFileUploadParam(String parameter);
	
	 List<String> getUploadedFileName(String parameter);
	
	 String getPathInfo();
	
	 String getPathTranslated();
	
	 String getProtocol();
	
	 String getQueryString();
	
	 String getRemoteAddr();
	
	 String getRemoteHost();
	
	 int getRemotePort();
	
	 String getRemoteUser();
	
	 String getRequestedSessionId();
	
	 String getRequestURI();
	
	 StringBuffer getRequestURL();
	
	 String getScheme();
	
	 String getServerName();
	
	 int getServerPort();
	
	 String getServletPath();
	
	 SessionWrapper getSession();
	
	 SessionWrapper getSession(boolean arg0);
	
	 Principal getUserPrincipal();
	
	 boolean isRequestedSessionIdFromCookie();
	
	 boolean isRequestedSessionIdFromURL();
	
	 boolean isRequestedSessionIdValid();
	
	 boolean isSecure();
	
	 boolean isUserInRole(String arg0);
	
	 void removeAttribute(String arg0);
	
	 void setAttribute(String arg0, Object arg1);
	
	 void setCharacterEncoding(String arg0) throws UnsupportedEncodingException;

	 boolean isMultipartContent();

	 RequestDispatcher getRequestDispatcher(final String path);
}
