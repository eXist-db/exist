/*
 * eXist Open Source Native XML Database Copyright (C) 2001-06 Wolfgang M.
 * Meier meier@ifs.tu-darmstadt.de http://exist.sourceforge.net
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation; either version 2 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 * $Id$
 */

package org.exist.http.servlets;

import javax.servlet.AsyncContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ReadListener;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.Part;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.security.Principal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.util.IterableEnumeration;
import org.exist.util.MimeType;
import org.exist.util.VirtualTempFile;
import org.exist.util.io.FastByteArrayInputStream;

/**
 * A wrapper for HttpServletRequest
 * - differentiates between POST parameters in the URL or Content Body
 * - keeps content Body of the POST request, making it available many times
 * through {@link #getContentBodyInputStream()} .
 * <p>
 * A method of differentiating between POST parameters in the URL or Content Body of the request was needed.
 * The standard javax.servlet.http.HTTPServletRequest does not differentiate between URL or content body parameters,
 * this class does, the type is indicated in RequestParameter.type.
 * <p>
 * To differentiate manually we need to read the URL (getQueryString()) and the Content body (getInputStream()),
 * this is problematic with the standard javax.servlet.http.HTTPServletRequest as parameter functions (getParameterMap(), getParameterNames(), getParameter(String), getParameterValues(String))
 * affect the  input stream functions (getInputStream(), getReader()) and vice versa.
 * <p>
 * This class solves this by reading the Request Parameters initially from both the URL and the Content Body of the Request
 * and storing them in the private variable params for later use.
 *
 * @author Adam Retter <adam.retter@devon.gov.uk>
 * @version 1.2
 * @serial 2018-04-03
 */

//TODO: check loops to make sure they only iterate as few times as needed
//TODO: do we need to do anything with encoding strings manually?


public class HttpServletRequestWrapper implements HttpServletRequest {
    private static final Logger LOG = LogManager.getLogger(HttpServletRequestWrapper.class);

    private final HttpServletRequest request;
    private final String formEncoding;
    private final Map<String, List<RequestParameter>> params = new HashMap<>();

    /**
     * the content Body of the POST request;
     * it must be stored because once the Servlet input stream has been
     * consumed, the content Body is no more readable.
     */
    private VirtualTempFile contentBody = null;


    /**
     * HttpServletRequestWrapper Constructor
     *
     * @param request      The HttpServletRequest to wrap
     * @param formEncoding The encoding to use
     */
    public HttpServletRequestWrapper(final HttpServletRequest request, final String formEncoding) throws UnsupportedEncodingException {
        this.request = request;
        if (request.getCharacterEncoding() != null) {
            this.formEncoding = getCharacterEncoding();

        } else {
            request.setCharacterEncoding(formEncoding);
            this.formEncoding = formEncoding;
        }

        initialiseWrapper();
    }

    //Initialises the wrapper, and parameter map
    private void initialiseWrapper() {
        //Parse out parameters from the URL
        parseURLParameters(this.request.getQueryString());

        //If POST request, Parse out parameters from the Content Body
        if ("POST".equals(request.getMethod().toUpperCase())) {
            //If there is some Content
            final int contentLength = request.getContentLength();
            if (contentLength > 0 || contentLength == -1) {
                // If a form POST , and not a document POST
                String contentType = request.getContentType().toLowerCase();
                final int semicolon = contentType.indexOf(';');
                if (semicolon > 0) {
                    contentType = contentType.substring(0, semicolon).trim();
                }
                if ("application/x-www-form-urlencoded".equals(contentType)
                        && request.getHeader("ContentType") == null) {
                    //Parse out parameters from the Content Body
                    parseContentBodyParameters();

                } else if (contentType.equals(MimeType.XML_TYPE.getName())) {
                    // if an XML-RPC
                    try {
                        recordContentBody();
                    } catch (final IOException e) {
                        LOG.error("Error Reading the Content Body into the buffer", e);
                    }
                }
            }
        }
    }

    //Stores parameters from the QueryString of the request
    private void parseURLParameters(final String querystring) {
        if (querystring != null) {
            //Parse any parameters from the URL
            parseParameters(querystring, RequestParameter.ParameterSource.URL);
        }
    }

    /**
     * Stores parameters from the Content Body of the Request
     */
    private void parseContentBodyParameters() {
        try {
            final String content = getContentBody();

            //Parse any parameters from the Content Body
            parseParameters(content, RequestParameter.ParameterSource.CONTENT);
        } catch (final IOException e) {
            LOG.error("Error Reading the Content Body into the buffer", e);
        }
    }

    private String getContentBody() throws IOException {
        recordContentBody();

        final StringBuilder result = new StringBuilder();
        appendContentBody(result);

        return result.toString();
    }

    /**
     * Parses Parameters into param objects and stores them in a vector in params
     */
    private void parseParameters(final String parameters, final RequestParameter.ParameterSource type) {
        //Split parameters into an array
        final String[] nameValuePairs = parameters.split("&");

        for (final String nameValuePair : nameValuePairs) {
            //Split parameter into name and value
            final String[] thePair = nameValuePair.split("=");

            try {
                //URL Decode the parameter name and value
                thePair[0] = URLDecoder.decode(thePair[0], formEncoding);
                if (thePair.length == 2) {
                    thePair[1] = URLDecoder.decode(thePair[1], formEncoding);
                }
            } catch (final UnsupportedEncodingException e) {
                LOG.error(e);
            }

            //Have we encountered a parameter with this name?
            if (params.containsKey(thePair[0])) {
                //key exists in hash map, add value and type to vector
                final List<RequestParameter> vecValues = params.get(thePair[0]);
                vecValues.add(new RequestParameter((thePair.length == 2 ? thePair[1] : ""), type));
                params.put(thePair[0], vecValues);
            } else {
                //not in hash map so add a vector with the initial value
                final List<RequestParameter> vecValues = new ArrayList<>();
                vecValues.add(new RequestParameter((thePair.length == 2 ? thePair[1] : ""), type));
                params.put(thePair[0], vecValues);
            }
        }
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (contentBodyRecorded()) {
            return new CachingServletInputStream(contentBody);
        } else {
            return request.getInputStream();
        }
    }

    /**
     * making the content Body of the POST request available many times, for processing by , e.g. Rpc processor .
     */
    private InputStream getContentBodyInputStream() throws IOException {
        recordContentBody();
        return contentBody.getByteStream();
    }

    private void recordContentBody() throws IOException {
        if (contentBody == null) {
            //Read the Content Body into the buffer
            final InputStream is = request.getInputStream();

            long clen = request.getContentLength();
            final String lenstr = request.getHeader("Content-Length");
            if (lenstr != null) {
                clen = Long.parseLong(lenstr);
            }
            contentBody = new VirtualTempFile();
            contentBody.write(is, clen);
            contentBody.close();
        }
    }

    private void appendContentBody(final Appendable buf)
            throws IOException {
        recordContentBody();

        final BufferedReader br = getReader();
        String line;
        while ((line = br.readLine()) != null) {
            buf.append(line);
        }
    }

    @Override
    public String getParameter(final String name) {
        //Does the parameter exist?
        if (params.containsKey(name)) {
            //Get the parameters vector of values
            final List<RequestParameter> vecParameterValues = params.get(name);

            //return the first value in the vector
            return vecParameterValues.get(0).getValue();
        } else {
            return null;
        }
    }

    @Override
    public Enumeration<String> getParameterNames() {
        return new IterableEnumeration<>(params.keySet());
    }

    @Override
    public String[] getParameterValues(final String name) {
        //Does the parameter exist?
        if (params.containsKey(name)) {
            //Get the parameters vector of values
            final List<RequestParameter> vecParameterValues = params.get(name);

            //Create a string array to hold the values
            final String[] values = new String[vecParameterValues.size()];

            //Copy each value into the string array
            for (int i = 0; i < vecParameterValues.size(); i++) {
                values[i] = vecParameterValues.get(i).getValue();
            }

            //return the string array of values
            return values;
        } else {
            return null;
        }
    }

    @Override
    public Map<String, String[]> getParameterMap() {
        //Map to hold the parameters
        final Map<String, String[]> mapParameters = new HashMap<>();

        final Set<Map.Entry<String, List<RequestParameter>>> setParams = params.entrySet();

        //iterate through the Request Parameters
        for (final Map.Entry<String, List<RequestParameter>> me : setParams) {
            //Get the parameters values
            final List<RequestParameter> vecParamValues = me.getValue();

            //Create a string array to hold the parameter values
            final String[] values = new String[vecParamValues.size()];

            //Copy the parameter values into a string array
            int i = 0;
            for (final Iterator<RequestParameter> itParamValues = vecParamValues.iterator(); itParamValues.hasNext(); i++) {
                values[i] = itParamValues.next().getValue();
            }
            mapParameters.put(me.getKey(), values); //Store the parameter in a map
        }
        return mapParameters; //return the Map of parameters
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (contentBodyRecorded()) {
            String encoding = request.getCharacterEncoding();
            if (encoding == null) {
                encoding = "UTF-8";
            }
            return new BufferedReader(new InputStreamReader(getContentBodyInputStream(), encoding));
        } else {
            return request.getReader();
        }
    }

    /**
     * Similar to javax.servlet.http.HttpServletRequest.toString() ,
     * except it includes output of the Request parameters from the Request's Content Body
     *
     * @return String representation of HttpServletRequestWrapper
     */
    @Override
    public String toString() {
        // If POST request AND there is some content AND its not a file upload
        // 	AND content Body has not been recorded
        if ("POST".equals(request.getMethod().toUpperCase())
                && (request.getContentLength() > 0 || request.getContentLength() == -1)
                && !request.getContentType().toUpperCase().startsWith(
                "MULTIPART/")
                && !contentBodyRecorded()) {

            // Also return the content parameters, these are not part
            // of the standard HttpServletRequest.toString() output
            final StringBuilder buf = new StringBuilder(request.toString());

            final Set<Map.Entry<String, List<RequestParameter>>> setParams = params.entrySet();

            for (final Map.Entry<String, List<RequestParameter>> me : setParams) {
                final List<RequestParameter> vecParamValues = me.getValue();

                for (final RequestParameter p : vecParamValues) {

                    if (p.source == RequestParameter.ParameterSource.CONTENT) {
                        if (buf.charAt(buf.length() - 1) != '\n') {
                            buf.append("&");
                        }
                        buf.append(me.getKey());
                        buf.append("=");
                        buf.append(p.getValue());
                    }
                }
            }

            buf.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));

            return buf.toString();

        } else if (contentBodyRecorded()) {

            // XML-RPC request or plain XML REST POST
            final StringBuilder buf = new StringBuilder(request.toString());
            try {
                appendContentBody(buf);
            } catch (final IOException ioe) {
                LOG.error("Error Reading the Content Body into the buffer: " + ioe);
            }

            buf.append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));
            return buf.toString();

        } else {
            //Return standard HttpServletRequest.toString() output
            return request.toString();
        }
    }

    private boolean contentBodyRecorded() {
        return contentBody != null
                && contentBody.length() > 0;
    }

    @Override
    public String getAuthType() {
        return request.getAuthType();
    }

    @Override
    public Cookie[] getCookies() {
        return request.getCookies();
    }

    @Override
    public long getDateHeader(final String name) {
        return request.getDateHeader(name);
    }

    @Override
    public String getHeader(final String name) {
        return request.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(final String name) {
        return request.getHeaders(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        return request.getHeaderNames();
    }

    @Override
    public int getIntHeader(final String name) {
        return request.getIntHeader(name);
    }

    @Override
    public String getMethod() {
        return request.getMethod();
    }

    @Override
    public String getPathInfo() {
        return request.getPathInfo();
    }

    @Override
    public String getPathTranslated() {
        return request.getPathInfo();
    }

    @Override
    public String getContextPath() {
        return request.getContextPath();
    }

    @Override
    public String getQueryString() {
        return request.getQueryString();
    }

    @Override
    public String getRemoteUser() {
        return request.getRemoteUser();
    }

    @Override
    public boolean isUserInRole(final String name) {
        return request.isUserInRole(name);
    }

    @Override
    public Principal getUserPrincipal() {
        return request.getUserPrincipal();
    }

    @Override
    public String getRequestedSessionId() {
        return request.getRequestedSessionId();
    }

    @Override
    public String getRequestURI() {
        return request.getRequestURI();
    }

    @Override
    public StringBuffer getRequestURL() {
        return request.getRequestURL();
    }

    @Override
    public String getServletPath() {
        return request.getServletPath();
    }

    @Override
    public HttpSession getSession(final boolean create) {
        return request.getSession(create);
    }

    @Override
    public HttpSession getSession() {
        return request.getSession();
    }

    @Override
    public String changeSessionId() {
        return request.changeSessionId();
    }

    @Override
    public boolean isRequestedSessionIdValid() {
        return request.isRequestedSessionIdValid();
    }

    @Override
    public boolean isRequestedSessionIdFromCookie() {
        return request.isRequestedSessionIdFromCookie();
    }

    @Override
    public boolean isRequestedSessionIdFromURL() {
        return request.isRequestedSessionIdFromURL();
    }

    @Override
    @Deprecated
    public boolean isRequestedSessionIdFromUrl() {
        return request.isRequestedSessionIdFromUrl();
    }

    @Override
    public boolean authenticate(final HttpServletResponse httpServletResponse) throws IOException, ServletException {
        return request.authenticate(httpServletResponse);
    }

    @Override
    public void login(final String s, final String s1) throws ServletException {
        request.login(s, s1);
    }

    @Override
    public void logout() throws ServletException {
        request.logout();
    }

    @Override
    public Collection<Part> getParts() throws IOException, ServletException {
        return request.getParts();
    }

    @Override
    public Part getPart(final String s) throws IOException, ServletException {
        return request.getPart(s);
    }

    @Override
    public <T extends HttpUpgradeHandler> T upgrade(final Class<T> clazz) throws IOException, ServletException {
        return request.upgrade(clazz);
    }

    @Override
    public Object getAttribute(final String name) {
        return request.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        return request.getAttributeNames();
    }

    @Override
    public String getCharacterEncoding() {
        return request.getCharacterEncoding();
    }

    @Override
    public void setCharacterEncoding(final String enc) throws UnsupportedEncodingException {
        request.setCharacterEncoding(enc);
    }

    @Override
    public int getContentLength() {
        return request.getContentLength();
    }

    @Override
    public long getContentLengthLong() {
        return request.getContentLengthLong();
    }

    @Override
    public String getContentType() {
        return request.getContentType();
    }

    @Override
    public String getProtocol() {
        return request.getProtocol();
    }

    @Override
    public String getScheme() {
        return request.getScheme();
    }

    @Override
    public String getServerName() {
        return request.getServerName();
    }

    @Override
    public int getServerPort() {
        return request.getServerPort();
    }

    @Override
    public String getRemoteAddr() {
        return request.getRemoteAddr();
    }

    @Override
    public String getRemoteHost() {
        return request.getRemoteHost();
    }

    @Override
    public void setAttribute(final String name, final Object o) {
        request.setAttribute(name, o);
    }

    @Override
    public void removeAttribute(final String name) {
        request.removeAttribute(name);
    }

    @Override
    public Locale getLocale() {
        return request.getLocale();
    }

    @Override
    public Enumeration<Locale> getLocales() {
        return request.getLocales();
    }

    @Override
    public boolean isSecure() {
        return request.isSecure();
    }

    @Override
    public RequestDispatcher getRequestDispatcher(final String name) {
        return request.getRequestDispatcher(name);
    }

    @Override
    @Deprecated
    public String getRealPath(final String path) {
        return request.getSession().getServletContext().getRealPath(path);
    }

    @Override
    public int getRemotePort() {
        return request.getRemotePort();
    }

    @Override
    public String getLocalName() {
        return request.getLocalName();
    }

    @Override
    public String getLocalAddr() {
        return request.getLocalAddr();
    }

    @Override
    public int getLocalPort() {
        return request.getLocalPort();
    }

    @Override
    public ServletContext getServletContext() {
        return request.getServletContext();
    }

    @Override
    public AsyncContext startAsync() throws IllegalStateException {
        return request.startAsync();
    }

    @Override
    public AsyncContext startAsync(ServletRequest servletRequest, ServletResponse servletResponse) throws IllegalStateException {
        return request.startAsync(servletRequest, servletResponse);
    }

    @Override
    public boolean isAsyncStarted() {
        return request.isAsyncStarted();
    }

    @Override
    public boolean isAsyncSupported() {
        return request.isAsyncSupported();
    }

    @Override
    public AsyncContext getAsyncContext() {
        return request.getAsyncContext();
    }

    @Override
    public DispatcherType getDispatcherType() {
        return request.getDispatcherType();
    }


    private static class CachingServletInputStream extends ServletInputStream {
        private final InputStream istream;

        public CachingServletInputStream(final VirtualTempFile contentBody)
                throws IOException {
            if (contentBody == null) {
                this.istream = new FastByteArrayInputStream(new byte[0]);
            } else {
                this.istream = contentBody.getByteStream();
            }
        }

        @Override
        public int read() throws IOException {
            return istream.read();
        }

        @Override
        public int read(final byte b[]) throws IOException {
            return istream.read(b);
        }

        @Override
        public int read(final byte b[], final int off, final int len) throws IOException {
            return istream.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return istream.available();
        }

        @Override
        public boolean isFinished() {
            try {
                return istream.available() == 0;
            } catch (final IOException ioe) {
                LOG.error(ioe);
                return true;
            }
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(final ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Simple class to hold the value and type of a request parameter
     */
    private static class RequestParameter {
        enum ParameterSource {
            URL,        //parameter from the URL of the request
            CONTENT     //parameter from the Content of the request
        }

        private final String value;
        private final ParameterSource source;

        /**
         * RequestParameter Constructor
         *
         * @param value  Value of the Request Parameter
         * @param source Source of the Request Parameter, URL (1) or Content (2)
         */
        RequestParameter(final String value, final ParameterSource source) {
            this.value = value;
            this.source = source;
        }

        /**
         * Request parameter value accessor
         *
         * @return Value of Request parameter
         */
        public String getValue() {
            return (value);
        }

        /**
         * Request parameter source accessor
         *
         * @return Source of Request parameter
         */
        public ParameterSource getSource() {
            return (source);
        }
    }
}
