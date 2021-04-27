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

import javax.servlet.RequestDispatcher;
import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Path;
import java.security.Principal;
import java.util.Enumeration;
import java.util.List;

public interface RequestWrapper {

    /**
     * Returns an Enumeration containing the names of the attributes available
     * to this request. This method returns an empty Enumeration if the
     * request has no attributes available to it.
     *
     * @see javax.servlet.ServletRequest#getAttributeNames()
     *
     * @return The attribute names.
     */
    Enumeration<String> getAttributeNames();

    /**
     * Returns the value of the named attribute as an Object, or null if no
     * attribute of the given name exists.
     *
     * @see javax.servlet.ServletRequest#getAttribute(String)
     *
     * @param name a String specifying the name of the attribute.
     *
     * @return The attribute value.
     */
    Object getAttribute(String name);

    /**
     * Stores an attribute in this request.
     * Attributes are reset between requests.
     *
     * @param name a String specifying the name of the attribute.
     * @param o the Object to be stored.
     *
     * @see javax.servlet.ServletRequest#setAttribute(String, Object)
     */
    void setAttribute(String name, Object o);

    /**
     * Removes an attribute from this request.
     * This method is not generally needed as attributes only persist as
     * long as the request is being handled.
     *
     * @param name a String specifying the name of the attribute.
     *
     * @see javax.servlet.ServletRequest#removeAttribute(String)
     */
    void removeAttribute(String name);

    /**
     * Returns the name of the character encoding used in the body of this
     * request.
     * This method returns null if the request does not specify a character
     * encoding.
     *
     * @see javax.servlet.ServletRequest#getCharacterEncoding()
     *
     * @return a String containing the name of the character encoding,
     *     or null if the request does not specify a character encoding.
     */
    String getCharacterEncoding();

    /**
     * Overrides the name of the character encoding used in the body of this
     * request.
     *
     * @see javax.servlet.ServletRequest#setCharacterEncoding(String)
     *
     * @param env String containing the name of the character encoding.
     *
     * @throws UnsupportedEncodingException if this ServletRequest is still
     * in a state where a character encoding may be set, but the specified
     * encoding is invalid
     */
    void setCharacterEncoding(String env) throws UnsupportedEncodingException;

    /**
     * Returns the length, in bytes, of the request body and made available
     * by the input stream, or -1 if the length is not known ir is greater
     * than Integer.MAX_VALUE
     *
     * @see javax.servlet.ServletRequest#getContentLength()
     *
     * @return an integer containing the length of the request body or -1
     * if the length is not known or is greater than Integer.MAX_VALUE.
     */
    long getContentLength();

    /**
     * Returns the length, in bytes, of the request body and made available
     * by the input stream, or -1 if the length is not known. For HTTP
     * servlets, same as the value of the CGI variable CONTENT_LENGTH.
     *
     * @see javax.servlet.ServletRequest#getContentLengthLong()
     *
     * @return a long containing the length of the request body or -1L
     *     if the length is not known.
     */
    long getContentLengthLong();

    /**
     * Retrieves the body of the request as binary data using a ServletInputStream.
     * Either this method or getReader may be called to read the body, not both.
     *
     * @see javax.servlet.ServletRequest#getInputStream()
     *
     * @return a ServletInputStream object containing the body of the request.
     *
     * @throws IllegalStateException if the getReader method has already been called
     *     for this request.
     * @throws java.io.IOException if an input or output exception occurred.
     */
    InputStream getInputStream() throws IOException;

    /**
     * Returns the MIME type of the body of the request, or null if the type
     * is not known. For HTTP servlets, same as the value of the CGI
     * variable CONTENT_TYPE.
     *
     * @see javax.servlet.ServletRequest#getContentType()
     *
     * @return a String containing the name of the MIME type of the
     *     request, or null if the type is not known.
     */
    String getContentType();

    /**
     * Returns an Enumeration of String objects containing the names of
     * the parameters contained in this request. If the request has no
     * parameters, the method returns an empty Enumeration.
     *
     * @see javax.servlet.ServletRequest#getParameterNames()
     *
     * @return an Enumeration of String objects, each String containing
     *     the name of a request parameter; or an empty Enumeration if
     *     the request has no parameters.
     */
    Enumeration<String> getParameterNames();

    /**
     * Returns the value of a request parameter as a String, or null
     * if the parameter does not exist. Request parameters are extra
     * information sent with the request. For HTTP servlets, parameters
     * are contained in the query string or posted form data.
     *
     * @see javax.servlet.ServletRequest#getParameter(String)
     *
     * @param name a String specifying the name of the parameter.
     *
     * @return a String representing the single value of the parameter.
     */
    String getParameter(String name);

    /**
     * Returns an array of String objects containing all of the values the
     * given request parameter has, or null if the parameter does not exist.
     * If the parameter has a single value, the array has a length of 1.
     *
     * @see javax.servlet.ServletRequest#getParameterValues(String)
     *
     * @param name a String containing the name of the parameter whose
     *             value is requested.
     *
     * @return an array of String objects containing the parameter's values.
     */
    String[] getParameterValues(String name);

    /**
     * Returns the name and version of the protocol the request uses in the
     * form protocol/majorVersion.minorVersion, for example, HTTP/1.1.
     * For HTTP servlets, the value returned is the same as the value of
     * the CGI variable SERVER_PROTOCOL.
     *
     * @see javax.servlet.ServletRequest#getProtocol()
     *
     * @return a String containing the protocol name and version number.
     */
    String getProtocol();

    /**
     * Returns the name of the scheme used to make this request, for example,
     * http, https, or ftp. Different schemes have different rules for constructing
     * URLs, as noted in RFC 1738.
     *
     * @see javax.servlet.ServletRequest#getScheme()
     *
     * @return a String containing the name of the scheme used to make this
     *     request.
     */
    String getScheme();

    /**
     * Returns the host name of the server to which the request was sent.
     * It is the value of the part before ":" in the Host header value,
     * if any, or the resolved server name, or the server IP address.
     *
     * @see javax.servlet.ServletRequest#getServerName()
     *
     * @return a String containing the name of the server.
     */
    String getServerName();

    /**
     * Returns the port number to which the request was sent.
     * It is the value of the part after ":" in the Host header value,
     * if any, or the server port where the client connection was accepted on.
     *
     * @see javax.servlet.ServletRequest#getServerPort()
     *
     * @return an integer specifying the port number.
     */
    int getServerPort();

    /**
     * Returns the Internet Protocol (IP) address of the client or last proxy
     * that sent the request. For HTTP servlets, same as the value of the CGI
     * variable REMOTE_ADDR.
     *
     * @see javax.servlet.ServletRequest#getRemoteAddr()
     *
     * @return a String containing the IP address of the client that sent the
     *     request.
     */
    String getRemoteAddr();

    /**
     * Returns the fully qualified name of the client or the last proxy that
     * sent the request. If the engine cannot or chooses not to resolve the
     * hostname (to improve performance), this method returns the dotted-string
     * form of the IP address. For HTTP servlets, same as the value of the
     * CGI variable REMOTE_HOST.
     *
     * @see javax.servlet.ServletRequest#getRemoteHost()
     *
     * @return a String containing the fully qualified name of the client.
     */
    String getRemoteHost();

    /**
     * Returns the Internet Protocol (IP) source port of the client or last
     * proxy that sent the request.
     *
     * @see javax.servlet.ServletRequest#getRemotePort()
     *
     * @return an integer specifying the port number.
     */
    int getRemotePort();

    /**
     * Returns a boolean indicating whether this request was made using a
     * secure channel, such as HTTPS.
     *
     * @see javax.servlet.ServletRequest#isSecure()
     *
     * @return a boolean indicating if the request was made using a
     *     secure channel.
     */
    boolean isSecure();

    /**
     * Returns a RequestDispatcher object that acts as a wrapper for the
     * resource located at the given path.
     *
     * @see javax.servlet.ServletRequest#getRequestDispatcher(String)
     *
     * @param path a String specifying the pathname to the resource.
     *     If it is relative, it must be relative against the current servlet.
     *
     * @return a RequestDispatcher object that acts as a wrapper for the
     *     resource at the specified path, or null if the servlet container
     *     cannot return a RequestDispatcher.
     */
    RequestDispatcher getRequestDispatcher(final String path);

    /**
     * Returns an array containing all of the Cookie objects the client sent
     * with this request. This method returns null if no cookies were sent.
     *
     * @see javax.servlet.http.HttpServletRequest#getCookies()
     *
     * @return an array of all the Cookies included with this request,
     *     or null if the request has no cookies.
     */
    Cookie[] getCookies();

    /**
     * Returns the portion of the request URI that indicates the context of
     * the request.
     *
     * @see javax.servlet.http.HttpServletRequest#getContextPath()
     *
     * @return a String specifying the portion of the request URI that
     *     indicates the context of the request.
     */
    String getContextPath();

    /**
     * Returns an enumeration of all the header names this request contains.
     * If the request has no headers, this method returns an empty enumeration.
     *
     * @see javax.servlet.http.HttpServletRequest#getHeaderNames()
     *
     * @return an enumeration of all the header names sent with this request;
     *     if the request has no headers, an empty enumeration; if the servlet
     *     container does not allow servlets to use this method, null.
     */
    Enumeration<String> getHeaderNames();

    /**
     * Returns the value of the specified request header as a String.
     * If the request did not include a header of the specified name,
     * this method returns null
     *
     * @see javax.servlet.http.HttpServletRequest#getHeader(String)
     *
     * @param name a String specifying the header name.
     *
     * @return a String containing the value of the requested header,
     *     or null if the request does not have a header of that name.
     */
    String getHeader(String name);

    /**
     * Returns all the values of the specified request header as an
     * Enumeration of String objects.
     *
     * @see javax.servlet.http.HttpServletRequest#getHeaders(String)
     *
     * @param name a String specifying the header name.
     *
     * @return an Enumeration containing the values of the requested header.
     *     If the request does not have any headers of that name return an
     *     empty enumeration. If the container does not allow access to
     *     header information, return null.
     */
    Enumeration<String> getHeaders(String name);

    /**
     * Returns the name of the HTTP method with which this request was made,
     * for example, GET, POST, or PUT. Same as the value of the CGI variable
     * REQUEST_METHOD.
     *
     * @see javax.servlet.http.HttpServletRequest#getMethod()
     *
     * @return a String specifying the name of the method with which this
     *     request was made.
     */
    String getMethod();

    /**
     * Returns any extra path information associated with the URL the client
     * sent when it made this request.
     *
     * @see javax.servlet.http.HttpServletRequest#getPathInfo()
     *
     * @return a String, decoded by the web container, specifying extra path
     *     information that comes after the servlet path but before the query
     *     string in the request URL; or null if the URL does not have any
     *     extra path information.
     */
    String getPathInfo();

    /**
     * Returns any extra path information after the servlet name but before the
     * query string, and translates it to a real path.
     *
     * @see javax.servlet.http.HttpServletRequest#getPathTranslated()
     *
     * @return a String specifying the real path, or null if the URL does not
     *     have any extra path information.
     */
    String getPathTranslated();

    /**
     * Returns the query string that is contained in the request URL after the
     * path. This method returns null if the URL does not have a query string.
     * Same as the value of the CGI variable QUERY_STRING.
     *
     * @see javax.servlet.http.HttpServletRequest#getQueryString()
     *
     * @return a String containing the query string or null if the URL contains
     *     no query string. The value is not decoded by the container.
     */
    String getQueryString();

    /**
     * Returns the login of the user making this request, if the user has been
     * authenticated, or null if the user has not been authenticated. Whether
     * the user name is sent with each subsequent request depends on the
     * browser and type of authentication. Same as the value of the CGI
     * variable REMOTE_USER.
     *
     * @see javax.servlet.http.HttpServletRequest#getRemoteUser()
     *
     * @return a String specifying the login of the user making this request,
     *     or null if the user login is not known.
     */
    String getRemoteUser();

    /**
     * Returns the session ID specified by the client. This may not be the same
     * as the ID of the current valid session for this request. If the client
     * did not specify a session ID, this method returns null.
     *
     * @see javax.servlet.http.HttpServletRequest#getRequestedSessionId()
     *
     * @return a String specifying the session ID, or null if the request did not
     *     specify a session ID.
     */
    String getRequestedSessionId();

    /**
     * Checks whether the requested session ID came in as a cookie.
     *
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromCookie()
     *
     * @return true if the session ID came in as a cookie; otherwise, false.
     */
    boolean isRequestedSessionIdFromCookie();

    /**
     * Checks whether the requested session ID came in as part of the request URL.
     *
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdFromURL()
     *
     * @return true if the session ID came in as part of a URL;
     *     otherwise, false.
     */
    boolean isRequestedSessionIdFromURL();

    /**
     * Checks whether the requested session ID is still valid.
     * If the client did not specify any session ID, this method returns false.
     *
     * @see javax.servlet.http.HttpServletRequest#isRequestedSessionIdValid()
     *
     * @return true if this request has an id for a valid session in the current
     *     session context; false otherwise.
     */
    boolean isRequestedSessionIdValid();

    /**
     * Returns the part of this request's URL from the protocol name up to the
     * query string in the first line of the HTTP request.
     *
     * @see javax.servlet.http.HttpServletRequest#getRequestURI()
     *
     * @return a String containing the part of the URL from the protocol name
     *     up to the query string.
     */
    String getRequestURI();

    /**
     * Reconstructs the URL the client used to make the request.
     * The returned URL contains a protocol, server name, port number,
     * and server path, but it does not include query string parameters.
     *
     * @see javax.servlet.http.HttpServletRequest#getRequestURL()
     *
     * @return a StringBuffer object containing the reconstructed URL.
     */
    StringBuffer getRequestURL();

    /**
     * Returns the part of this request's URL that calls the servlet.
     *
     * @see javax.servlet.http.HttpServletRequest#getServletPath()
     *
     * @return a String containing the name or path of the servlet being
     *     called, as specified in the request URL, decoded, or an empty
     *     string if the servlet used to process the request is matched
     *     using the "/*" pattern.
     */
    String getServletPath();

    /**
     * Returns the current session associated with this request, or if
     * the request does not have a session, creates one.
     *
     * @see javax.servlet.http.HttpServletRequest#getSession()
     *
     * @return the HttpSession associated with this request.
     */
    SessionWrapper getSession();

    /**
     * Returns the current session associated with this request,
     * or if the request does not have a session, creates one.
     *
     * @see javax.servlet.http.HttpServletRequest#getSession(boolean)
     *
     * @param create true to create a new session for this request if
     *     necessary; false to return null if there's no current session.
     *
     * @return the HttpSession associated with this request or null if create
     *     is false and the request has no valid session.
     */
    SessionWrapper getSession(boolean create);

    /**
     * Returns a java.security.Principal object containing the name of the
     * current authenticated user. If the user has not been authenticated,
     * the method returns null.
     *
     * @see javax.servlet.http.HttpServletRequest#getUserPrincipal()
     *
     * @return a java.security.Principal containing the name of the user
     *     making this request; null if the user has not been authenticated.
     */
    Principal getUserPrincipal();

    /**
     * Returns a boolean indicating whether the authenticated user is included
     * in the specified logical "role".
     *
     * @see javax.servlet.http.HttpServletRequest#isUserInRole(String)
     *
     * @param role a String specifying the name of the role.
     *
     * @return a boolean indicating whether the user making this request
     *     belongs to a given role; false if the user has not been
     *     authenticated.
     */
    boolean isUserInRole(String role);

    /**
     * Determines if the request has multi-part content.
     *
     * @return true if the request has multipart content.
     */
    boolean isMultipartContent();

    /**
     * Get a file-upload parameter value.
     *
     * @param name the parameter name
     *
     * @return the list of file paths.
     */
    List<Path> getFileUploadParam(String name);

    /**
     * Get the name of an uploaded file.
     *
     * @param name the parameter name
     *
     * @return the list of file names.
     */
    List<String> getUploadedFileName(String name);
}
