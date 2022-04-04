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

import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.apache.commons.fileupload.servlet.ServletFileUpload;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;


/**
 * A wrapper for requests processed by a servlet. All parameters, submitted as part of
 * the URL and via the http POST body (application/x-www-form-urlencoded and
 * multipart/form-data encoded) are made available transparently.
 * 
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author <a href="mailto:dannes@exist-db.org">Dannes Wessels</a>
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class HttpRequestWrapper implements RequestWrapper {

    private static final Logger LOG = LogManager.getLogger(HttpRequestWrapper.class);
    
    private final HttpServletRequest servletRequest;
    private final String formEncoding;
    private String containerEncoding = null;

    private String pathInfo = null;
    private String servletPath = null;

    private final boolean isMultipartContent;

    // Use linkedhashmap to preserver order
    // Object can be a single object, or a List of objects
    private final Map<String, Object> params = new LinkedHashMap<>();

    // flag to administer wether multi-part formdata was processed
    private final boolean isFormDataParsed;

    /**
     * Constructs a wrapper for the given servlet request. multipart/form-data 
     * will be parsed when available upon indication.
     *
     * Defaults to UTF-8 encoding
     * 
     * @param servletRequest The request as viewed by the servlet
     */
    public HttpRequestWrapper(final HttpServletRequest servletRequest) {
        this(servletRequest, UTF_8.name(), UTF_8.name());
    }
    
    /**
     * Constructs a wrapper for the given servlet request. multipart/form-data 
     * will be parsed when available upon indication.
     *
     * @param servletRequest The request as viewed by the servlet
     * @param formEncoding The encoding of the request's forms
     * @param containerEncoding The encoding of the servlet
     */
    public HttpRequestWrapper(final HttpServletRequest servletRequest, final String formEncoding,
            final String containerEncoding) {
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
    public HttpRequestWrapper(final HttpServletRequest servletRequest, final String formEncoding,
            final String containerEncoding, final boolean parseMultipart) {
        this.servletRequest = servletRequest;
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.pathInfo = servletRequest.getPathInfo();
        this.servletPath = servletRequest.getServletPath();


        // Get url-encoded parameters (url-ecoded from http GET and POST)
        parseParameters();

        // Determine if request is a multipart
        isMultipartContent = ServletFileUpload.isMultipartContent(servletRequest);

        // Get multi-part formdata parameters when it is a mpfd request
        // and when instructed to do so
        if (parseMultipart && isMultipartContent) {

            // Formdata is actually parsed
            isFormDataParsed = true;

            // Get multi-part formdata
            parseMultipartContent();
        } else {
            isFormDataParsed = false;
        }

        LOG.debug("Retrieved {} parameters.", params.size());
    }

    @Override
    public Object getAttribute(final String name) {
        return servletRequest.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return servletRequest.getAttributeNames();
    }

    /**
     * Returns an array of Cookies
     */
    @Override
    public Cookie[] getCookies() {
        return servletRequest.getCookies();
    }

    private static void addParameter(final Map<String, Object> map, final String paramName, final Object value) {

        final Object original = map.get(paramName);

        if (original != null) {

            // Check if original value was already a List
            if (original instanceof List) {
                // Add value to existing List
                ((List) original).add(value);

            } else {
                // Single value already detected, convert to List and add both items
                final List<Object> list = new ArrayList<>();
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
    private List<FileItem> getFileItem(final Object obj) {

    	final List<FileItem> fileList = new LinkedList<>();
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
    private String decode(final String value) {

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
     * @see javax.servlet.http.HttpServletRequest#getContentLengthLong()
     */
    @Override
    public long getContentLengthLong() {
        long retval = servletRequest.getContentLengthLong();
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
    public String getHeader(final String name) {
        return servletRequest.getHeader(name);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getCharacterEncoding()
     * @return An enumeration of header names
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return servletRequest.getHeaderNames();
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getHeaders(String)
     */
    @Override
    public Enumeration<String> getHeaders(final String name) {
        return servletRequest.getHeaders(name);
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
    public String getParameter(final String name) {

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
    public List<Path> getFileUploadParam(final String name) {
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
    public List<String> getUploadedFileName(final String name) {
        if (!isFormDataParsed) {
            return null;
        }

        final Object o = params.get(name);
        if (o == null) {
            return null;
        }

        final List<FileItem> items = getFileItem(o);
        final List<String> files = new ArrayList<>(items.size());
        for (final FileItem item : items) {
        	files.add(FilenameUtils.normalize(item.getName()));
        }
        return files;
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterNames()
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(params.keySet());
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#getParameterValues(String)
     */
    @Override
    public String[] getParameterValues(final String name) {

        // params already retrieved
        final Object obj = params.get(name);

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
    public SessionWrapper getSession(final boolean create) {
        final HttpSession session = servletRequest.getSession(create);
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
    public boolean isUserInRole(final String role) {
        return servletRequest.isUserInRole(role);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#removeAttribute(String)
     */
    @Override
    public void removeAttribute(final String name) {
        servletRequest.removeAttribute(name);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setAttribute(String, Object)
     */
    @Override
    public void setAttribute(final String name, final Object o) {
        servletRequest.setAttribute(name, o);
    }

    /**
     * @see javax.servlet.http.HttpServletRequest#setCharacterEncoding(String)
     */
    @Override
    public void setCharacterEncoding(final String env) throws UnsupportedEncodingException {
        servletRequest.setCharacterEncoding(env);
    }


    public void setPathInfo(final String pathInfo) {
        this.pathInfo = pathInfo;
    }

    public void setServletPath(final String servletPath) {
        this.servletPath = servletPath;
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
