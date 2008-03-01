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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.DefaultFileItem;
import org.apache.commons.fileupload.DiskFileUpload;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUpload;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.log4j.Logger;
import org.exist.xquery.Constants;

/** A wrapper for requests processed by a servlet.
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class HttpRequestWrapper implements RequestWrapper {
    
    private HttpServletRequest servletRequest;
    private String formEncoding = null;
    private String containerEncoding = null;
    
    private Hashtable params = null;
    
    private static Logger LOG = Logger.getLogger(HttpRequestWrapper.class.getName());

    public HttpRequestWrapper(HttpServletRequest servletRequest, String formEncoding,
            String containerEncoding) {
        this(servletRequest, formEncoding, containerEncoding, true);
    }
    
    /**
	 * Constructs a wrapper for the given servlet request.
     * @param servletRequest The request as viewed by the servlet
     * @param formEncoding The encoding of the request's forms
     * @param containerEncoding The encoding of the servlet
     */
    public HttpRequestWrapper(HttpServletRequest servletRequest, String formEncoding,
            String containerEncoding, boolean parseMultipart) {
        this.servletRequest = servletRequest;
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        if(parseMultipart && FileUpload.isMultipartContent(servletRequest)) {
            parseMultipartContent();
        }
    }

    public Object getAttribute(String name) {
        return servletRequest.getAttribute(name);
    }

    /**
     * Returns an array of Cookies
     */
    public Cookie[] getCookies()
    {
        return servletRequest.getCookies();
    }
    
    /**
     * Parses multi-part requests in order to set the parameters. 
     */
    private void parseMultipartContent() {
        DiskFileUpload upload = new DiskFileUpload();
        upload.setSizeThreshold(0);
        try {
            this.params = new Hashtable();
            List items = upload.parseRequest(this.servletRequest);
            for(Iterator i = items.iterator(); i.hasNext(); ) {
                FileItem next = (FileItem) i.next();
                Object old = params.get(next.getFieldName());
                if(old != null) {
                    if(old instanceof List)
                        ((List)old).add(next);
                    else {
                        ArrayList list = new ArrayList();
                        list.add(old);
                        list.add(next);
                        params.put(next.getFieldName(), list);
                    }
                } else
                    params.put(next.getFieldName(), next);
            }
        } catch (FileUploadException e) {
            // TODO: handle this
            e.printStackTrace();
        }
    }
    
    /**
     * @param obj
     * @return
     */
    private FileItem getFileItem(Object obj) {
        if(obj instanceof List)
            return (FileItem)((List)obj).get(0);
        else
            return (FileItem)obj;
    }
    
    /**
     * @param value
     * @return
     */
    private String decode(String value) {
        if(containerEncoding == null)
        	//TODO : use file.encoding system property ?
            containerEncoding = "ISO-8859-1";
        if(containerEncoding.equals(formEncoding))
            return value;
        try {
            byte[] bytes = value.getBytes(containerEncoding);
            return new String(bytes, formEncoding);
        } catch (UnsupportedEncodingException e) {
        	LOG.warn(e);
            return value;
        }
    }    
    
    /** @see javax.servlet.http.HttpServletRequest#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return servletRequest.getInputStream();
    }
    
    /** @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     */
    public String getCharacterEncoding() {
        return servletRequest.getCharacterEncoding();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getContentLength()
     */
    public int getContentLength() {
        return servletRequest.getContentLength();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getContentType()
     */
    public String getContentType() {
        return servletRequest.getContentType();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getContextPath()
     */
    public String getContextPath() {
        return servletRequest.getContextPath();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getHeader(String)
     */
    public String getHeader(String arg0) {
        return servletRequest.getHeader(arg0);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     * @return An enumeration of header names
     */
    public Enumeration getHeaderNames() {
        return servletRequest.getHeaderNames();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getHeaders(String)
     */
    public Enumeration getHeaders(String arg0) {
        return servletRequest.getHeaders(arg0);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getMethod()
     */
    public String getMethod() {
        return servletRequest.getMethod();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public String getParameter(String name) {
        if(params == null) {
            String value = servletRequest.getParameter(name);
            if(formEncoding == null || value == null)
                return value;
            return decode(value);
        } else {
            Object o = params.get(name);
            if(o == null)
                return null;
            FileItem item;
            if(o instanceof List)
                item = (FileItem)((List)o).get(0);
            else
                item = (FileItem)o;
            if(formEncoding == null)
                return item.getString();
            else
                try {
                    return item.getString(formEncoding);
                } catch (UnsupportedEncodingException e) {
                	LOG.warn(e);
                    return null;
                }
        }
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public File getFileUploadParam(String name) {
        if(params == null)
            return null;
        Object o = params.get(name);
        if(o == null)
            return null;
        FileItem item = getFileItem(o);
        if(item.isFormField())
            return null;
        return ((DefaultFileItem)item).getStoreLocation();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public String getUploadedFileName(String name) {        
        if(params == null)
            return null;        
        Object o = params.get(name);
        if(o == null)
            return null;        
        FileItem item = getFileItem(o); 
        if(item.isFormField())
            return null;        
        // Get filename from FileItem
        String itemName = item.getName();
        if(itemName == null)
            return null;
        
        // Several browsers, e.g. MSIE send a full path of the LOCALLY stored
        // file instead of the filename alone.
        // Jakarta's Commons FileUpload package does not repair this
        // so we should remove all supplied path information.
        
        // If there are (back) slashes in the Filename, we have
        // a full path. Find the last (back) slash, take remaining text
        int lastFileSepPos = Math.max(itemName.lastIndexOf("/"), itemName.lastIndexOf("\\") );
        
        String documentName=itemName;
        if (lastFileSepPos != Constants.STRING_NOT_FOUND){
            documentName=itemName.substring(lastFileSepPos + 1);
        }
        
        return documentName;
    }
    
    
    
    /**@see javax.servlet.http.HttpServletRequest#getParameterNames()
     */
    public Enumeration getParameterNames() {
        if(params == null)
            return servletRequest.getParameterNames();
        else {
            return params.keys();
        }
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getParameterValues(String)
     */
    public String[] getParameterValues(String key) {
        if(params == null) {
            String[] values = servletRequest.getParameterValues(key);
            if(formEncoding == null || values == null)
                return values;
            for(int i = 0; i < values.length; i++) {
                values[i] = decode(values[i]);
            }
            return values;
        } else {
            Object obj = (Object)params.get(key);
            if(obj == null)
                return null;
            String[] values;
            if(obj instanceof List) {
                List list = (List)obj;
                values = new String[list.size()];
                int j = 0;
                for(Iterator i = list.iterator(); i.hasNext(); j++) {
                    FileItem item = (FileItem)i.next();
                    try {
                        values[j] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                    } catch (UnsupportedEncodingException e) {
                    	LOG.warn(e);
                        e.printStackTrace();
                    }
                }
            } else {
                FileItem item = (FileItem)obj;
                values = new String[1];
                try {
                    values[0] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                } catch (UnsupportedEncodingException e) {
                	LOG.warn(e);
                    e.printStackTrace();
                }
            }
            return values;
        }
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getPathInfo()
     */
    public String getPathInfo() {
        return servletRequest.getPathInfo();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getPathTranslated()
     */
    public String getPathTranslated() {
        return servletRequest.getPathTranslated();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getProtocol()
     */
    public String getProtocol() {
        return servletRequest.getProtocol();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getQueryString()
     */
    public String getQueryString() {
        return servletRequest.getQueryString();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRemoteAddr()
     */
    public String getRemoteAddr() {
        return servletRequest.getRemoteAddr();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRemoteHost()
     */
    public String getRemoteHost() {
        return servletRequest.getRemoteHost();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRemoteUser()
     */
    public String getRemoteUser() {
        return servletRequest.getRemoteUser();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    public String getRequestedSessionId() {
        return servletRequest.getRequestedSessionId();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRequestURI()
     */
    public String getRequestURI() {
        return servletRequest.getRequestURI();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getRequestURL()
     */
    public StringBuffer getRequestURL() {
        return servletRequest.getRequestURL();
    }    
    
    /**@see javax.servlet.http.HttpServletRequest#getScheme()
     */
    public String getScheme() {
        return servletRequest.getScheme();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getServerName()
     */
    public String getServerName() {
        return servletRequest.getServerName();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getServerPort()
     */
    public int getServerPort() {
        return servletRequest.getServerPort();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getServletPath()
     */
    public String getServletPath() {
        return servletRequest.getServletPath();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getSession()
     */
    public SessionWrapper getSession() {
        HttpSession session = servletRequest.getSession();
        if(session == null)
            return null;
        else
            return new HttpSessionWrapper(session);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getSession(boolean)
     */
    public SessionWrapper getSession(boolean arg0) {
        HttpSession session = servletRequest.getSession(arg0);
        if(session == null)
            return null;
        else
            return new HttpSessionWrapper(session);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    public Principal getUserPrincipal() {
        return servletRequest.getUserPrincipal();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    public boolean isRequestedSessionIdFromCookie() {
        return servletRequest.isRequestedSessionIdFromCookie();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    public boolean isRequestedSessionIdFromURL() {
        return servletRequest.isRequestedSessionIdFromURL();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    public boolean isRequestedSessionIdValid() {
        return servletRequest.isRequestedSessionIdValid();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#isSecure()
     */
    public boolean isSecure() {
        return servletRequest.isSecure();
    }
    
    /**@see javax.servlet.http.HttpServletRequest#isUserInRole(String)
     */
    public boolean isUserInRole(String arg0) {
        return servletRequest.isUserInRole(arg0);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#removeAttribute(String)
     */
    public void removeAttribute(String arg0) {
        servletRequest.removeAttribute(arg0);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#setAttribute(String, Object)
     */
    public void setAttribute(String arg0, Object arg1) {
        servletRequest.setAttribute(arg0, arg1);
    }
    
    /**@see javax.servlet.http.HttpServletRequest#setCharacterEncoding(String)
     */
    public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
        servletRequest.setCharacterEncoding(arg0);
    }
    
}
