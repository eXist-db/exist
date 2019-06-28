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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * A wrapper for requests processed by a servlet. All parameters, submitted as part of
 * the URL and via the http POST body (application/x-www-form-urlencoded and
 * multipart/form-data encoded) are made available transparently.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 */
public class HttpRequestWrapper implements RequestWrapper {

    private static Logger LOG = LogManager.getLogger(HttpRequestWrapper.class.getName());
    
    private HttpServletRequest servletRequest;
    private String formEncoding = null;
    private String containerEncoding = null;

    private String pathInfo = null;
    private String servletPath = null;

    private boolean isMultipartContent=false;

    // Use linkedhashmap to preserver order
    // Object can be a single object, or a List of objects
    private Map<String, Object> params = new LinkedHashMap<String, Object>();

    // flag to administer wether multi-part formdata was processed
    private boolean isFormDataParsed = false;

    /**
     * Constructs a wrapper for the given servlet request. multipart/form-data 
     * will be parsed when available upon indication.
     *
     * Defaults to UTF-8 encoding
     * 
     * @param servletRequest The request as viewed by the servlet
     */
    public HttpRequestWrapper(HttpServletRequest servletRequest) {
        this(servletRequest, "UTF-8", "UTF-8");
    }
    
    /**
     * Constructs a wrapper for the given servlet request. multipart/form-data 
     * will be parsed when available upon indication.
     *
     * @param servletRequest The request as viewed by the servlet
     * @param formEncoding The encoding of the request's forms
     * @param containerEncoding The encoding of the servlet
     */
    public HttpRequestWrapper(HttpServletRequest servletRequest, String formEncoding,
            String containerEncoding) {
        this(servletRequest, formEncoding, containerEncoding, true);
    }

    /**
     * Constructs a wrapper for the given servlet request.
     *
     * @param servletRequest The request as viewed by the servlet
     * @param formEncoding The encoding of the request's forms
     * @param containerEncoding The encoding of the servlet
     * @param parseMultipart Set to TRUE to enable parse multipart/form-data when available.
     */
    public HttpRequestWrapper(HttpServletRequest servletRequest, String formEncoding,
            String containerEncoding, boolean parseMultipart) {
        this.servletRequest = servletRequest;
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.pathInfo = servletRequest.getPathInfo();
        this.servletPath = servletRequest.getServletPath();


        // Get url-encoded parameters (url-ecoded from http GET and POST)
        parseParameters();

        // Determine if request is a multipart
        isMultipartContent=ServletFileUpload.isMultipartContent(servletRequest);

        // Get multi-part formdata parameters when it is a mpfd request
        // and when instructed to do so
        if (parseMultipart && isMultipartContent) {

            // Formdata is actually parsed
            isFormDataParsed = true;

            // Get multi-part formdata
            parseMultipartContent();
        }

        LOG.debug("Retrieved "+params.size() + " parameters.");

    }

    @Override
    public Object getAttribute(String name) {
        return servletRequest.getAttribute(name);
    }

    @Override
    public Enumeration getAttributeNames() {
        return servletRequest.getAttributeNames();
    }

    /**
     * Returns an array of Cookies
     */
    @Override
    public Cookie[] getCookies() {
        return servletRequest.getCookies();
    }

    private static void addParameter(Map<String, Object> map, String paramName, Object value) {

        final Object original = map.get(paramName);

        if (original != null) {

            // Check if original value was already a List
            if (original instanceof List) {
                // Add value to existing List
                ((List) original).add(value);

            } else {
                // Single value already detected, convert to List and add both items
                final ArrayList<Object> list = new ArrayList<Object>();
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
        final DiskFileItemFactory factory = new DiskFileItemFactory();

        // Dizzzz: Wonder why this should be zero
        factory.setSizeThreshold(0);

        // Create a new file upload handler
        final ServletFileUpload upload = new ServletFileUpload(factory);

        try {

            final List<FileItem> items = upload.parseRequest(servletRequest);

            // Iterate over all mult-part formdata items and
            // add all data (field and files) to parmeters
            for (final FileItem item : items) {
                addParameter(params, item.getFieldName(), item);
            }

        } catch (final FileUploadException e) {
            LOG.error(e);
        }

    }

    /**
     * Parses the url-encoded parameters
     */
    private void parseParameters() {
        final Map<String, String[]> map = servletRequest.getParameterMap();
        for (final Map.Entry<String, String[]> param : map.entrySet()) {

            // Write keys and values
            for (final String value : param.getValue()) {
                addParameter(params, param.getKey(), decode(value));
            }
        }
    }

    /**
     *  Convert object to FileItem, get FirstItem from list, or null
     * if object or object in list is not a FileItem
     * 
     * @param obj List or Fileitem
     * @return First Fileitem in list or Fileitem.
     */
    private List<FileItem> getFileItem(Object obj) {

    	final List<FileItem> fileList = new LinkedList<FileItem>();
        if (obj instanceof List) {
            // Cast
            final List list = (List) obj;
            // Return first FileItem object if present
            for(final Object listObject : list) {
                if(listObject instanceof FileItem && !((FileItem) listObject).isFormField()){
                    fileList.add((FileItem) listObject);
                }
            }           

        } else if(obj instanceof FileItem && !((FileItem) obj).isFormField()) {
            // Cast and return
             fileList.add((FileItem) obj);
        }

        // object did not represent a List of FileItem's or FileItem.
        return fileList;

    }

    /**
     * @param value
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
            final byte[] bytes = value.getBytes(containerEncoding);
            return new String(bytes, formEncoding);

        } catch (final UnsupportedEncodingException e) {
            LOG.warn(e);
            return value;
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getInputStream()
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return servletRequest.getInputStream();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     */
    @Override
    public String getCharacterEncoding() {
        return servletRequest.getCharacterEncoding();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContentLength()
     */
    @Override
    public long getContentLength() {
        long retval = servletRequest.getContentLength();
        final String lenstr = servletRequest.getHeader("Content-Length");
        if (lenstr != null) {
            retval = Long.parseLong(lenstr);
        }

        return retval;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContentType()
     */
    @Override
    public String getContentType() {
        return servletRequest.getContentType();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getContextPath()
     */
    @Override
    public String getContextPath() {
        return servletRequest.getContextPath();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeader(String)
     */
    @Override
    public String getHeader(String arg0) {
        return servletRequest.getHeader(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     * @return An enumeration of header names
     */
    @Override
    public Enumeration getHeaderNames() {
        return servletRequest.getHeaderNames();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeaders(String)
     */
    @Override
    public Enumeration getHeaders(String arg0) {
        return servletRequest.getHeaders(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getMethod()
     */
    @Override
    public String getMethod() {
        return servletRequest.getMethod();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    @Override
    public String getParameter(String name) {

        // Parameters
        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        // If Parameter is a List, get first entry. The data is used later on
        if (o instanceof List) {
            final List lst = ((List) o);
            o = lst.get(0);
        }

        // If parameter is file item, convert to string
        if (o instanceof FileItem) {

            final FileItem fi = (FileItem) o;
            if (formEncoding == null) {
                return fi.getString();

            } else {
                try {
                    return fi.getString(formEncoding);

                } catch (final UnsupportedEncodingException e) {
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
    @Override
    public List<Path> getFileUploadParam(String name) {
        if (!isFormDataParsed) {
            return null;
        }

        final Object o = params.get(name);
        if (o == null) {
            return null;
        }

        final List<FileItem> items = getFileItem(o);
        final List<Path> files = new ArrayList<>(items.size());
        for (final FileItem item : items) {
        	files.add(((DiskFileItem) item).getStoreLocation().toPath());
        }
        return files;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameter(String)
     */
    @Override
    public List<String> getUploadedFileName(String name) {
        if (!isFormDataParsed) {
            return null;
        }

        final Object o = params.get(name);
        if (o == null) {
            return null;
        }

        final List<FileItem> items = getFileItem(o);
        final List<String> files = new ArrayList<String>(items.size());
        for (final FileItem item : items) {
        	files.add(FilenameUtils.normalize(item.getName()));
        }
        return files;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterNames()
     */
    @Override
    public Enumeration getParameterNames() {
        return Collections.enumeration(params.keySet());
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterValues(String)
     */
    @Override
    public String[] getParameterValues(String key) {

        // params already retrieved
        final Object obj = params.get(key);

        // Fast return
        if (obj == null) {
            return null;
        }

        // Allocate return values
        String[] values;

        // If object is a List, retrieve data from list
        if (obj instanceof List) {

            // Cast to List
            final List list = (List) obj;

            // Reserve the right aboumt of elements
            values = new String[list.size()];

            // position in array
            int position = 0;

            // Iterate over list
            for (final Object object : list) {

                // Item is a FileItem
                if (object instanceof FileItem) {

                    // Cast
                    final FileItem item = (FileItem) object;

                    // Get string representation of FileItem
                    try {
                        values[position] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                    } catch (final UnsupportedEncodingException e) {
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
                final FileItem item = (FileItem) obj;
                try {
                    values[0] = formEncoding == null ? item.getString() : item.getString(formEncoding);
                } catch (final UnsupportedEncodingException e) {
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
    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
     */
    @Override
    public String getPathTranslated() {
        return servletRequest.getPathTranslated();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getProtocol()
     */
    @Override
    public String getProtocol() {
        return servletRequest.getProtocol();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getQueryString()
     */
    @Override
    public String getQueryString() {
        return servletRequest.getQueryString();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteAddr()
     */
    @Override
    public String getRemoteAddr() {
        return servletRequest.getRemoteAddr();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteHost()
     */
    @Override
    public String getRemoteHost() {
        return servletRequest.getRemoteHost();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemotePort()
     */
    @Override
    public int getRemotePort() {
        return servletRequest.getRemotePort();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
     */
    @Override
    public String getRemoteUser() {
        return servletRequest.getRemoteUser();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    @Override
    public String getRequestedSessionId() {
        return servletRequest.getRequestedSessionId();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURI()
     */
    @Override
    public String getRequestURI() {
        return servletRequest.getRequestURI();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getRequestURL()
     */
    @Override
    public StringBuffer getRequestURL() {
        return servletRequest.getRequestURL();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getScheme()
     */
    @Override
    public String getScheme() {
        return servletRequest.getScheme();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServerName()
     */
    @Override
    public String getServerName() {
        return servletRequest.getServerName();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServerPort()
     */
    @Override
    public int getServerPort() {
        return servletRequest.getServerPort();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getServletPath()
     */
    @Override
    public String getServletPath() {
        return servletPath;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession()
     */
    @Override
    public SessionWrapper getSession() {
        final HttpSession session = servletRequest.getSession();
        if (session == null) {
            return null;
        } else {
            return new HttpSessionWrapper(session);
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
     */
    @Override
    public SessionWrapper getSession(boolean arg0) {
        final HttpSession session = servletRequest.getSession(arg0);
        if (session == null) {
            return null;
        } else {
            return new HttpSessionWrapper(session);
        }
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    @Override
    public Principal getUserPrincipal() {
        return servletRequest.getUserPrincipal();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return servletRequest.isRequestedSessionIdFromCookie();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return servletRequest.isRequestedSessionIdFromURL();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return servletRequest.isRequestedSessionIdValid();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isSecure()
     */
    @Override
    public boolean isSecure() {
        return servletRequest.isSecure();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#isUserInRole(String)
     */
    @Override
    public boolean isUserInRole(String arg0) {
        return servletRequest.isUserInRole(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#removeAttribute(String)
     */
    @Override
    public void removeAttribute(String arg0) {
        servletRequest.removeAttribute(arg0);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setAttribute(String, Object)
     */
    @Override
    public void setAttribute(String arg0, Object arg1) {
        servletRequest.setAttribute(arg0, arg1);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setCharacterEncoding(String)
     */
    @Override
    public void setCharacterEncoding(String arg0) throws UnsupportedEncodingException {
        servletRequest.setCharacterEncoding(arg0);
    }


    public void setPathInfo(String arg0) {
        pathInfo = arg0;
    }

    public void setServletPath(String arg0) {
        servletPath = arg0;
    }

    /**
     *  Indicate if a form is processed.
     *
     * @return TRUE if a form is processed else FALSE.
     */
    public boolean isFormDataParsed() {
        return isFormDataParsed;
    }

    /**
     *  Indicate if the request is a multi-part formdata request
     *
     * @return TRUE if request is multi-part/formdata request, else FALSE.
     */
    @Override
    public boolean isMultipartContent() {
        return isMultipartContent;
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String path) {
        return servletRequest.getRequestDispatcher(path);
    }
}
