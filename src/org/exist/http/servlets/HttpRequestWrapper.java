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
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;

import org.apache.log4j.Logger;


/**
 * A wrapper for requests processed by a servlet.
 * 
 * @author Wolfgang Meier <wolfgang@exist-db.org>
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 * @author Dannes Wessels <dannes@exist-db.org>
 */
public class HttpRequestWrapper implements RequestWrapper {

    private static Logger LOG = Logger.getLogger(HttpRequestWrapper.class.getName());
    private HttpServletRequest servletRequest;
    private String formEncoding = null;
    private String containerEncoding = null;
    private String pathInfo = null;
    private String servletPath = null;

    // linkedhashmap to preserver order
    private Map<String, Object> params = new LinkedHashMap<String, Object>();

    // flag to administer wether multi-part formdata was processed
    private boolean isFormDataParsed = false;

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
        this.pathInfo = servletRequest.getPathInfo();
        this.servletPath = servletRequest.getServletPath();


        // Get multi-part formdata parameters when it is a mpfd request
        // and when instructed to do so
        if (parseMultipart && ServletFileUpload.isMultipartContent(servletRequest)) {

            // Formdata is actually parsed
            isFormDataParsed = true;

            // Get multi-part formdata
            parseMultipartContent();
        }

        // Get url-encoded parameters  (GET and POST)
        parseParameters();

    }

    public Object getAttribute(String name) {
        return servletRequest.getAttribute(name);
    }

    public Enumeration getAttributeNames() {
        return servletRequest.getAttributeNames();
    }

    /**
     * Returns an array of Cookies
     */
    public Cookie[] getCookies() {
        return servletRequest.getCookies();
    }

    private static void addParameter(Map<String, Object> map, String paramName, Object value) {

        Object original = map.get(paramName);

        if (original != null) {

            // Check if original value was already a List

            if (original instanceof List) {
                // Add value to existing List
                ((List) original).add(value);

            } else {
                // Single value already detected, convert to List and add both items
                ArrayList<Object> list = new ArrayList<Object>();
                list.add(original);
                list.add(value);
                map.put(paramName, list);
            }

        } else {
            // Parameter did not exist yet, add single value
            map.put(paramName, value);
        }
    }

    /**
     * Parses multi-part requests in order to set the parameters. 
     */
    private void parseMultipartContent() {
        // Create a factory for disk-based file items
        DiskFileItemFactory factory = new DiskFileItemFactory();

        // Dizzzz: Wonder why this should be zero
        factory.setSizeThreshold(0);

        // Create a new file upload handler
        ServletFileUpload upload = new ServletFileUpload(factory);

        try {

            List items = upload.parseRequest(servletRequest);

            // Iterate over all mult-part formdata items and
            // add all data (field and files) to parmeters
            for (Object i : items) {
                FileItem item = (FileItem) i;
                addParameter(params, item.getFieldName(), item);
            }

        } catch (FileUploadException e) {
            LOG.error(e);
        }

    }

    /**
     * Parses the url-encoded parameters
     */
    private void parseParameters() {
        Map map = servletRequest.getParameterMap();
        for (Object one : map.keySet()) {

            // Get key and corresponding values
            String key = (String) one;

            // Get values belonging to the key
            String[] values = (String[]) map.get(one);

            // Write keys and values
            for (String value : values) {
                addParameter(params, key, decode(value));
            }
        }
    }

    /**
     * Get file item
     * 
     * @param obj List or Fileitem
     * @return First Fileitem in list or Fileitem.
     */
    private FileItem getFileItem(Object obj) {
        if (obj instanceof List) {
            return (FileItem) ((List) obj).get(0);
        } else {
            return (FileItem) obj;
        }
    }

    /**
     * @param value
     * @return
     */
    private String decode(String value) {

        if (formEncoding == null || value == null) {
            return value;
        }

        if (containerEncoding == null) {
            //TODO : use file.encoding system property ?
            containerEncoding = "ISO-8859-1";
        }

        if (containerEncoding.equals(formEncoding)) {
            return value;
        }

        try {
            byte[] bytes = value.getBytes(containerEncoding);
            return new String(bytes, formEncoding);

        } catch (UnsupportedEncodingException e) {
            LOG.warn(e);
            return value;
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getInputStream()
     */
    public InputStream getInputStream() throws IOException {
        return servletRequest.getInputStream();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     */
    public String getCharacterEncoding() {
        return servletRequest.getCharacterEncoding();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContentLength()
     */
    public long getContentLength() {
        long retval = servletRequest.getContentLength();
        String lenstr = servletRequest.getHeader("Content-Length");
        if (lenstr != null) {
            retval = Long.parseLong(lenstr);
        }

        return retval;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContentType()
     */
    public String getContentType() {
        return servletRequest.getContentType();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContextPath()
     */
    public String getContextPath() {
        return servletRequest.getContextPath();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeader(String)
     */
    public String getHeader(String arg0) {
        return servletRequest.getHeader(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     * @return An enumeration of header names
     */
    public Enumeration getHeaderNames() {
        return servletRequest.getHeaderNames();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeaders(String)
     */
    public Enumeration getHeaders(String arg0) {
        return servletRequest.getHeaders(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getMethod()
     */
    public String getMethod() {
        return servletRequest.getMethod();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public String getParameter(String name) {

        // Parameters
        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        // If Parameter is a List, get first entry. The data is used later on
        if (o instanceof List) {
            List lst = ((List) o);
            o = lst.get(0);
        }

        // If parameter is file item, convert to string
        if (o instanceof FileItem) {

            FileItem fi = (FileItem) o;
            if (formEncoding == null) {
                return fi.getString();

            } else {
                try {
                    return fi.getString(formEncoding);

                } catch (UnsupportedEncodingException e) {
                    LOG.warn(e);
                    return null;
                }
            }

            // Return just a simple value
        } else if (o instanceof String) {
            return (String) o;
        }

        return null;

    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public File getFileUploadParam(String name) {
        if (!isFormDataParsed) {
            return null;
        }

        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        FileItem item = getFileItem(o);
        if (item.isFormField()) {
            return null;
        }

        return ((DiskFileItem) item).getStoreLocation();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    public String getUploadedFileName(String name) {
        if (!isFormDataParsed) {
            return null;
        }

        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        FileItem item = getFileItem(o);
        if (item.isFormField()) {
            return null;
        }

        // Get filename from FileItem
        String itemName = item.getName();
        if (itemName == null) {
            return null;
        }

        // return Filename only
        return FilenameUtils.getName(itemName);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterNames()
     */
    public Enumeration getParameterNames() {
//        if (!isFormDataParsed) {
//            return servletRequest.getParameterNames();
//        } else {
            return Collections.enumeration(params.keySet());
//        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterValues(String)
     */
    public String[] getParameterValues(String key) {

        // params already retrieved
        Object obj = params.get(key);

        // Fast return
        if (obj == null) {
            return null;
        }

        // Allocate return values
        String[] values;

        // If object is a List, retrieve data from list
        if (obj instanceof List) {

            // Cast to List
            List list = (List) obj;

            // Reserve the right aboumt of elements
            values = new String[list.size()];

            // position in array
            int position = 0;

            // Iterate over list
            for (Object object : list) {

                // Item is a FileItem
                if (object instanceof FileItem) {

                    // Cast
                    FileItem item = (FileItem) object;

                    // Get string representation of FileItem
                    try {
                        values[position] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                    } catch (UnsupportedEncodingException e) {
                        LOG.warn(e);
                        e.printStackTrace();
                    }

                } else {
                    // Normal formfield
                    values[position] = (String) object;
                }
                position++;
            }

        } else {
            // No list retrieve one element only

            // Allocate space
            values = new String[1];

            // Item is a FileItem
            if (obj instanceof FileItem) {
                FileItem item = (FileItem) obj;
                try {
                    values[0] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                } catch (UnsupportedEncodingException e) {
                    LOG.warn(e);
                    e.printStackTrace();
                }

            } else {
                // Normal formfield
                values[0] = (String) obj;
            }

        }

        return values;

    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getPathInfo()
     */
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
     */
    public String getPathTranslated() {
        return servletRequest.getPathTranslated();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getProtocol()
     */
    public String getProtocol() {
        return servletRequest.getProtocol();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getQueryString()
     */
    public String getQueryString() {
        return servletRequest.getQueryString();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteAddr()
     */
    public String getRemoteAddr() {
        return servletRequest.getRemoteAddr();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteHost()
     */
    public String getRemoteHost() {
        return servletRequest.getRemoteHost();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemotePort()
     */
    public int getRemotePort() {
        return servletRequest.getRemotePort();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
     */
    public String getRemoteUser() {
        return servletRequest.getRemoteUser();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    public String getRequestedSessionId() {
        return servletRequest.getRequestedSessionId();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURI()
     */
    public String getRequestURI() {
        return servletRequest.getRequestURI();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURL()
     */
    public StringBuffer getRequestURL() {
        return servletRequest.getRequestURL();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getScheme()
     */
    public String getScheme() {
        return servletRequest.getScheme();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServerName()
     */
    public String getServerName() {
        return servletRequest.getServerName();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServerPort()
     */
    public int getServerPort() {
        return servletRequest.getServerPort();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServletPath()
     */
    public String getServletPath() {
        return servletPath;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession()
     */
    public SessionWrapper getSession() {
        HttpSession session = servletRequest.getSession();
        if (session == null) {
            return null;
        } else {
            return new HttpSessionWrapper(session);
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
     */
    public SessionWrapper getSession(boolean arg0) {
        HttpSession session = servletRequest.getSession(arg0);
        if (session == null) {
            return null;
        } else {
            return new HttpSessionWrapper(session);
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    public Principal getUserPrincipal() {
        return servletRequest.getUserPrincipal();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    public boolean isRequestedSessionIdFromCookie() {
        return servletRequest.isRequestedSessionIdFromCookie();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    public boolean isRequestedSessionIdFromURL() {
        return servletRequest.isRequestedSessionIdFromURL();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    public boolean isRequestedSessionIdValid() {
        return servletRequest.isRequestedSessionIdValid();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isSecure()
     */
    public boolean isSecure() {
        return servletRequest.isSecure();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isUserInRole(String)
     */
    public boolean isUserInRole(String arg0) {
        return servletRequest.isUserInRole(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#removeAttribute(String)
     */
    public void removeAttribute(String arg0) {
        servletRequest.removeAttribute(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setAttribute(String, Object)
     */
    public void setAttribute(String arg0, Object arg1) {
        servletRequest.setAttribute(arg0, arg1);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setCharacterEncoding(String)
     */
    public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
        servletRequest.setCharacterEncoding(arg0);
    }

    public void setPathInfo(String arg0) {
        pathInfo = arg0;
    }

    public void setServletPath(String arg0) {
        servletPath = arg0;
    }
}
