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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.Part;
import org.apache.commons.io.FilenameUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.exist.util.FileUtils;
import org.exist.util.io.InputStreamUtil;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.Principal;
import java.util.*;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
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

    private static final Path TMP_DIR;
    static {
        try {
            TMP_DIR = Files.createTempDirectory("");
        } catch (final IOException e) {
            throw new RuntimeException("Unable to create temporary directory for HttpRequestWrapper", e);
        }
    }

    private final HttpServletRequest servletRequest;
    @Nullable private final String formEncoding;
    @Nullable private String containerEncoding;

    @Nullable private String pathInfo;
    @Nullable private String servletPath;

    private final boolean isMultipartContent;

    // Use LinkedHashMap to preserver order
    // Object can be a single object, or a List of objects
    private final Map<String, Object> params = new LinkedHashMap<>();

    // flag to indicate whether multipart form data was processed
    private final boolean isFormDataParsed;

    @Nullable private Map<Part, Path> temporaryUploadedFilesPathCache = null;

    private boolean parsedQueryString = false;
    @Nullable private Map<String, Object> queryStringParameters = null;

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
    public HttpRequestWrapper(final HttpServletRequest servletRequest,
            @Nullable final String formEncoding, @Nullable final String containerEncoding) {
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
    public HttpRequestWrapper(final HttpServletRequest servletRequest,
            @Nullable final String formEncoding,
            @Nullable final String containerEncoding,
            final boolean parseMultipart) {
        this.servletRequest = servletRequest;
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.pathInfo = servletRequest.getPathInfo();
        this.servletPath = servletRequest.getServletPath();

        // Determine if request is a multipart

        @Nullable final String contentType = servletRequest.getContentType();
        isMultipartContent = "POST".equalsIgnoreCase(servletRequest.getMethod()) && contentType != null && contentType.toLowerCase(Locale.ENGLISH).startsWith("multipart/");

        // Get multi-part formdata parameters when it is a mpfd request
        // and when instructed to do so
        if (parseMultipart && isMultipartContent) {

            // Formdata is actually parsed
            isFormDataParsed = true;

            // get only parameters from url query
            parseQueryParameters();

            // Get multipart form data
            parseMultipartContent();
        } else {
            isFormDataParsed = false;

            // Get url query and form parameters from http GET and POST
            parseAllParameters();
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

    private static void addParameter(final Map<String, Object> map, final String paramName, @Nullable final Object value) {

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
        try {
            final Collection<Part> parts = servletRequest.getParts();

            // Iterate over all multipart form data items and
            // add all data (field and files) to parameters
            for (final Part part : parts) {
                addParameter(params, part.getName(), part);
            }

        } catch (final IOException | ServletException e) {
            LOG.error(e);
        }
    }

    /**
     * Parses the URL query parameters.
     */
    private void parseQueryParameters() {
       parseParameters(true);
    }

    /**
     * Parses the URL query, and form parameters.
     */
    private void parseAllParameters() {
        parseParameters(false);
    }

    /**
     * Parses the URL query, and form parameters.
     *
     * @param queryParametersOnly true if only the URL query parameters
     *     should be parsed, false to include both query and form parameters.
     */
    private void parseParameters(final boolean queryParametersOnly) {
        final Map<String, String[]> map = servletRequest.getParameterMap();
        for (final Map.Entry<String, String[]> parameter : map.entrySet()) {

            final String parameterName = parameter.getKey();

            // Add keys and values
            for (final String parameterValue : parameter.getValue()) {
                if (!queryParametersOnly || isQueryParameter(parameterName, parameterValue)) {
                    addParameter(params, parameterName, decode(parameterValue));
                }
            }
        }
    }

    private boolean isQueryParameter(final String parameterName, @Nullable final String parameterValue) {
        if (!parsedQueryString) {
            // lazy initialisation
            this.queryStringParameters = parseQueryString(servletRequest.getQueryString(), formEncoding);
            parsedQueryString = true;
        }

        if (queryStringParameters == null) {
            return false;
        }

        @Nullable final Object queryStringParameterValue = queryStringParameters.get(parameterName);
        if (queryStringParameterValue == null) {
            return parameterValue == null;
        }

        if (queryStringParameterValue instanceof List) {
            // Add value to existing List
            return ((List) queryStringParameterValue).contains(parameterValue);
        } else {
            if (parameterValue == null) {
                return false;
            }
            return parameterValue.equals(queryStringParameterValue);
        }
    }

    private static @Nullable Map<String, Object> parseQueryString(@Nullable final String queryString, @Nullable String encoding) {
        if (queryString == null) {
            return null;
        }

        final String[] pairs = queryString.split("&");
        if (pairs == null || pairs.length == 0) {
            return null;
        }

        Map<String, Object> queryParameters = null;
        for (final String pair : pairs) {
            final String[] keyValue = pair.split("=");
            if (keyValue == null || keyValue.length == 0) {
                continue;
            }

            if (queryParameters == null) {
                queryParameters = new LinkedHashMap<>();  // uses a LinkedHashMap to preserve order
            }

            if (encoding == null) {
                encoding = ISO_8859_1.displayName();
            }

            try {
                final String key = URLDecoder.decode(keyValue[0], encoding);
                @Nullable final String value;
                if (keyValue.length > 1) {
                    value = URLDecoder.decode(keyValue[1], encoding);
                } else {
                    value = null;
                }
                addParameter(queryParameters, key, value);
            } catch (final UnsupportedEncodingException e) {
                LOG.warn("Unable to parse URL query keyValue pair: " + Arrays.toString(keyValue), e);
            }
        }

        return queryParameters;
    }

    /**
     * Convert object to FileItem, get FirstItem from list, or null
     * if object or object in list is not a FileItem
     * 
     * @param obj List or Part
     * @return First Part in list or Part.
     */
    private List<Part> getFileItem(final Object obj) {

    	final List<Part> partList = new LinkedList<>();
        if (obj instanceof List list) {
            // Cast
            // Return first Part object if present
            for(final Object listObject : list) {
                if (listObject instanceof Part && !isFormField(((Part) listObject))) {
                    partList.add((Part) listObject);
                }
            }           

        } else if(obj instanceof Part && !isFormField(((Part) obj))) {
            // Cast and return
             partList.add((Part) obj);
        }

        // object did not represent a List of Part's or Part.
        return partList;
    }

    private static boolean isFormField(final Part part) {
        return part.getSubmittedFileName() == null;
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
     * @see jakarta.servlet.http.HttpServletRequest#getInputStream()
     */
    @Override
    public InputStream getInputStream() throws IOException {
        return servletRequest.getInputStream();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getCharacterEncoding()
     */
    @Override
    public String getCharacterEncoding() {
        return servletRequest.getCharacterEncoding();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getContentLength()
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
     * @see jakarta.servlet.http.HttpServletRequest#getContentLengthLong()
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
     * @see jakarta.servlet.http.HttpServletRequest#getContentType()
     */
    @Override
    public String getContentType() {
        return servletRequest.getContentType();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getContextPath()
     */
    @Override
    public String getContextPath() {
        return servletRequest.getContextPath();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getHeader(String)
     */
    @Override
    public String getHeader(final String name) {
        return servletRequest.getHeader(name);
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getCharacterEncoding()
     * @return An enumeration of header names
     */
    @Override
    public Enumeration<String> getHeaderNames() {
        return servletRequest.getHeaderNames();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getHeaders(String)
     */
    @Override
    public Enumeration<String> getHeaders(final String name) {
        return servletRequest.getHeaders(name);
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getMethod()
     */
    @Override
    public String getMethod() {
        return servletRequest.getMethod();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getParameter(String)
     */
    @Override
    public String getParameter(final String name) {

        // Parameters
        Object o = params.get(name);
        if (o == null) {
            return null;
        }

        // If Parameter is a List, get first entry. The data is used later on
        if (o instanceof List lst) {
            o = lst.getFirst();
        }

        // If parameter is file item, convert to string
        if (o instanceof Part fi) {

            try {
                return getPartContentAsString(fi, formEncoding);
            } catch (final IOException e) {
                LOG.warn(e);
                return null;
            }

            // Return just a simple value
        } else if (o instanceof String) {
            return (String) o;
        }

        return null;

    }

    private static String getPartContentAsString(final Part part, @Nullable final String encoding) throws IOException {
        try (final InputStream is = part.getInputStream()) {
            final byte[] data = InputStreamUtil.readAll(is);
            if (encoding != null) {
                return new String(data, encoding);
            } else {
                return new String(data);
            }
        }
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getParameter(String)
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

        final List<Part> parts = getFileItem(o);
        final List<Path> files = new ArrayList<>(parts.size());
        for (final Part part : parts) {
            Path temporaryUploadedFilePath = null;
            if (temporaryUploadedFilesPathCache != null) {
                temporaryUploadedFilePath = temporaryUploadedFilesPathCache.get(part);
            }

            if (temporaryUploadedFilePath == null) {
                try {
                    final String tmpFilename = UUID.randomUUID().toString() + ".tmp";

                    temporaryUploadedFilePath = TMP_DIR.resolve(tmpFilename);
                    part.write(temporaryUploadedFilePath.toAbsolutePath().toString());
                } catch (final IOException e) {
                    LOG.warn(e);
                    continue;
                }

                if (temporaryUploadedFilesPathCache == null) {
                    temporaryUploadedFilesPathCache = new HashMap<>();
                }

                temporaryUploadedFilesPathCache.put(part, temporaryUploadedFilePath);
            }

        	files.add(temporaryUploadedFilePath);
        }
        return files;
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getParameter(String)
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

        final List<Part> parts = getFileItem(o);
        final List<String> files = new ArrayList<>(parts.size());
        for (final Part part : parts) {
        	files.add(FilenameUtils.normalize(part.getSubmittedFileName()));
        }
        return files;
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getParameterNames()
     */
    @Override
    public Enumeration<String> getParameterNames() {
        return Collections.enumeration(params.keySet());
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getParameterValues(String)
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
        if (obj instanceof List list) {

            // Cast to List

            // Reserve the right aboumt of elements
            values = new String[list.size()];

            // position in array
            int position = 0;

            // Iterate over list
            for (final Object object : list) {

                // Item is a FileItem
                if (object instanceof Part part) {

                    // Cast

                    // Get string representation of FileItem
                    try {
                        values[position] = getPartContentAsString(part, formEncoding);
                    } catch (final IOException e) {
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
            if (obj instanceof Part part) {
                try {
                    values[0] = getPartContentAsString(part, formEncoding);
                } catch (final IOException e) {
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
     * @see jakarta.servlet.http.HttpServletRequest#getPathInfo()
     */
    @Override
    public String getPathInfo() {
        return pathInfo;
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getPathTranslated()
     */
    @Override
    public String getPathTranslated() {
        return servletRequest.getPathTranslated();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getProtocol()
     */
    @Override
    public String getProtocol() {
        return servletRequest.getProtocol();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getQueryString()
     */
    @Override
    public String getQueryString() {
        return servletRequest.getQueryString();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRemoteAddr()
     */
    @Override
    public String getRemoteAddr() {
        return servletRequest.getRemoteAddr();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRemoteHost()
     */
    @Override
    public String getRemoteHost() {
        return servletRequest.getRemoteHost();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRemotePort()
     */
    @Override
    public int getRemotePort() {
        return servletRequest.getRemotePort();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRemoteUser()
     */
    @Override
    public String getRemoteUser() {
        return servletRequest.getRemoteUser();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRequestedSessionId()
     */
    @Override
    public String getRequestedSessionId() {
        return servletRequest.getRequestedSessionId();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRequestURI()
     */
    @Override
    public String getRequestURI() {
        return servletRequest.getRequestURI();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getRequestURL()
     */
    @Override
    public StringBuffer getRequestURL() {
        return servletRequest.getRequestURL();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getScheme()
     */
    @Override
    public String getScheme() {
        return servletRequest.getScheme();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getServerName()
     */
    @Override
    public String getServerName() {
        return servletRequest.getServerName();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getServerPort()
     */
    @Override
    public int getServerPort() {
        return servletRequest.getServerPort();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getServletPath()
     */
    @Override
    public String getServletPath() {
        return servletPath;
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#getSession()
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
     * @see jakarta.servlet.http.HttpServletRequest#getSession(boolean)
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
     * @see jakarta.servlet.http.HttpServletRequest#getUserPrincipal()
     */
    @Override
    public Principal getUserPrincipal() {
        return servletRequest.getUserPrincipal();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     */
    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return servletRequest.isRequestedSessionIdFromCookie();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     */
    @Override
    public boolean isRequestedSessionIdFromURL() {
        return servletRequest.isRequestedSessionIdFromURL();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     */
    @Override
    public boolean isRequestedSessionIdValid() {
        return servletRequest.isRequestedSessionIdValid();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#isSecure()
     */
    @Override
    public boolean isSecure() {
        return servletRequest.isSecure();
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#isUserInRole(String)
     */
    @Override
    public boolean isUserInRole(final String role) {
        return servletRequest.isUserInRole(role);
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#removeAttribute(String)
     */
    @Override
    public void removeAttribute(final String name) {
        servletRequest.removeAttribute(name);
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#setAttribute(String, Object)
     */
    @Override
    public void setAttribute(final String name, final Object o) {
        servletRequest.setAttribute(name, o);
    }

    /**
     * @see jakarta.servlet.http.HttpServletRequest#setCharacterEncoding(String)
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

    @Override
    protected void finalize() {
        if (temporaryUploadedFilesPathCache == null) {
            return;
        }

        for (final Map.Entry<Part, Path> temporaryUploadedFilePathCache : temporaryUploadedFilesPathCache.entrySet()) {
            final Part part = temporaryUploadedFilePathCache.getKey();
            try {
                part.delete();
            } catch (final IOException e) {
                LOG.error("Unable to delete: {}", part.getSubmittedFileName(), e);
            }

            final Path temporaryFile = temporaryUploadedFilePathCache.getValue();
            FileUtils.deleteQuietly(temporaryFile);
        }
    }
}
