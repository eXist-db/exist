package org.exist.http.servlets;

/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
 *  http://exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.Namespaces;
import org.exist.source.FileSource;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.EXistXQueryService;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import javax.servlet.RequestDispatcher;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Servlet to redirect HTTP requests. The request is passed to an XQuery whose return value
 * determines where the request will be redirected to. The query should return a single XML element:
 *
 * <pre>
 *  &lt;exist:dispatch xmlns:exist="http://exist.sourceforge.net/NS/exist"
 *      path="/preview.xql" servlet-name="MyServlet" redirect="path">
 *       &lt;exist:add-parameter name="new-param" value="new-param-value"/>
 *   &lt;/exist:dispatch>
 * </pre>
 *
 * The element should have one of three attributes: <em>path</em>, <em>servlet-name</em> or
 * <em>redirect</em>.
 *
 * If the servlet-name attribute is present, the request will be forwarded to the named servlet
 * (name as specified in web.xml). Alternatively, path can point to an arbitrary resource. It can be either absolute or relative.
 * Relative paths are resolved relative to the original request.
 *
 * The request is forwarded via {@link javax.servlet.RequestDispatcher#forward(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}.
 * Contrary to HTTP forwarding, there is no additional roundtrip to the client. It all happens on
 * the server. The client will not notice the redirect.
 *
 * When forwarding to other servlets, the fields in {@link javax.servlet.http.HttpServletRequest} will be
 * updated to point to the new, redirected URI. However, the original request URI is stored in the
 * request attribute org.exist.forward.request-uri.
 *
 * If present, the "redirect" attribute causes the server to send a redirect request to the client, which will usually respond
 * with a new request to the redirected location. Note that this is quite different from a forwarding via RequestDispatcher,
 * which is completely transparent to the client.
 *
 * RedirectorServlet takes a single parameter in web.xml: "xquery". This parameter should point to an
 * XQuery script. It should be relative to the current web context.
 *
 * <pre>
 * &lt;servlet>
 *       &lt;servlet-name>RedirectorServlet</servlet-name>
 *       &lt;servlet-class>org.exist.http.servlets.RedirectorServlet</servlet-class>
 *
 *       &lt;init-param>
 *           &lt;param-name>xquery</param-name>
 *           &lt;param-value>dispatcher.xql</param-value>
 *       &lt;/init-param>
 *   &lt;/servlet>
 *
 * &lt;servlet-mapping>
 *       &lt;servlet-name>RedirectorServlet</servlet-name>
 *       &lt;url-pattern>/wiki/*</url-pattern>
 *   &lt;/servlet-mapping>
 * </pre>
 */
@Deprecated
public class RedirectorServlet extends HttpServlet {

	private static final long serialVersionUID = 853971301553787943L;

	private static final Logger LOG = LogManager.getLogger(RedirectorServlet.class);

    public final static String DEFAULT_USER = "guest";
    public final static String DEFAULT_PASS = "guest";
    public final static XmldbURI DEFAULT_URI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";
    
    private String user = null;
    private String password = null;
    private XmldbURI collectionURI = null;
    private String query = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        query = config.getInitParameter("xquery");
        if (query == null)
            {throw new ServletException("RedirectorServlet requires a parameter 'xquery'.");}
        user = config.getInitParameter("user");
        if(user == null)
            {user = DEFAULT_USER;}
        password = config.getInitParameter("password");
        if(password == null)
            {password = DEFAULT_PASS;}
        final String confCollectionURI = config.getInitParameter("uri");
        if(confCollectionURI == null) {
            collectionURI = DEFAULT_URI;
        } else {
            try {
                collectionURI = XmldbURI.xmldbUriFor(confCollectionURI);
            } catch (final URISyntaxException e) {
                throw new ServletException("Invalid XmldbURI for parameter 'uri': "+e.getMessage(),e);
            }
        }

        try {
            final Class<?> driver = Class.forName(DRIVER);
            final Database database = (Database)driver.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch(final Exception e) {
            final String errorMessage="Failed to initialize database driver";
            LOG.error(errorMessage,e);
            throw new ServletException(errorMessage+": " + e.getMessage(), e);
        }
    }

    @Override
    protected void service(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        if (request.getCharacterEncoding() == null)
            try {
                request.setCharacterEncoding("UTF-8");
            } catch (final IllegalStateException e) {
            }
        // Try to find the XQuery
        final String qpath = getServletContext().getRealPath(query);
        final Path p = Paths.get(qpath);
        if(!(Files.isReadable(p) && Files.isRegularFile(p))) {
            throw new ServletException("Cannot read XQuery source from " + p.toAbsolutePath());
        }
        final FileSource source = new FileSource(p, true);

        try {
            // Prepare and execute the XQuery
            final Collection collection = DatabaseManager.getCollection(collectionURI.toString(), user, password);
            final EXistXQueryService service = (EXistXQueryService) collection.getService("XQueryService", "1.0");
            if(!((EXistCollection)collection).isRemoteCollection()) {
                service.declareVariable(RequestModule.PREFIX + ":request", new HttpRequestWrapper(request, "UTF-8", "UTF-8"));
                service.declareVariable(ResponseModule.PREFIX + ":response", new HttpResponseWrapper(response));
                service.declareVariable(SessionModule.PREFIX + ":session", new HttpSessionWrapper(request.getSession( false )));
            }
            final ResourceSet result = service.execute(source);

            String redirectTo = null;
            String servletName = null;
            String path = null;
            RequestWrapper modifiedRequest = null;
            // parse the query result element
            if (result.getSize() == 1) {
                final XMLResource resource = (XMLResource) result.getResource(0);
                Node node = resource.getContentAsDOM();
                if (node.getNodeType() == Node.DOCUMENT_NODE)
                    {node = ((Document) node).getDocumentElement();}
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Redirect XQuery should return an XML element. Received: " + resource.getContent());
                    return;
                }
                Element elem = (Element) node;
                final String ns = elem.getNamespaceURI();
                if (ns == null || ((!Namespaces.EXIST_NS.equals(ns)) && "dispatch".equals(elem.getLocalName())))
                {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Redirect XQuery should return an element <exist:dispatch>. Received: " + resource.getContent());
                    return;
                }
                if (elem.hasAttribute("path"))
                    {path = elem.getAttribute("path");}
                else if (elem.hasAttribute("servlet-name"))
                    {servletName = elem.getAttribute("servlet-name");}
                else if (elem.hasAttribute("redirect"))
                    {redirectTo = elem.getAttribute("redirect");}
                else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                        "Element <exist:dispatch> should either provide an attribute 'path' or 'servlet-name'. Received: " +
                                resource.getContent());
                    return;
                }

                // Check for add-parameter elements etc.
                if (elem.hasChildNodes()) {
                    node = elem.getFirstChild();
                    while (node != null) {
                        final String nsUri = node.getNamespaceURI();
                        if (node.getNodeType() == Node.ELEMENT_NODE && nsUri != null && Namespaces.EXIST_NS.equals(nsUri)) {
                            elem = (Element) node;
                            if ("add-parameter".equals(elem.getLocalName())) {
                                if (modifiedRequest == null)
                                    {modifiedRequest = new RequestWrapper(request);}
                                modifiedRequest.addParameter(elem.getAttribute("name"), elem.getAttribute("value"));
                            }
                        }
                        node = node.getNextSibling();
                    }
                }
            }

            if (redirectTo != null) {
                // directly redirect to the specified URI
                response.sendRedirect(redirectTo);
                return;
            }

            // Get a RequestDispatcher, either from the servlet context or the request
            RequestDispatcher dispatcher;
            if (servletName != null && servletName.length() > 0)
                {dispatcher = getServletContext().getNamedDispatcher(servletName);}
            else {
                LOG.debug("Dispatching to " + path);
                dispatcher = getServletContext().getRequestDispatcher(path);
                if (dispatcher == null)
                    {dispatcher = request.getRequestDispatcher(path);}
            }
            if (dispatcher == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not create a request dispatcher. Giving up.");
                return;
            }

            if (modifiedRequest != null)
                {request = modifiedRequest;}
            // store the original request URI to org.exist.forward.request-uri
            request.setAttribute("org.exist.forward.request-uri", request.getRequestURI());
            request.setAttribute("org.exist.forward.servlet-path", request.getServletPath());

            // finally, execute the forward
            dispatcher.forward(request, response);
        } catch (final XMLDBException e) {
            throw new ServletException("An error occurred while initializing RedirectorServlet: " + e.getMessage(), e);
        }
    }

    private static class RequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

        Map<String, String[]> addedParams = new HashMap<>();

        private RequestWrapper(HttpServletRequest request) {
            super(request);
            // copy parameters
            for (final Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                final String key = (String) e.nextElement();
                final String[] value = request.getParameterValues(key);
                addedParams.put(key, value);
            }
        }

        public void addParameter(String name, String value) {
            addedParams.put(name, new String[] { value });
        }

        //XXX: something wrong here, the value can be String[], see line 278
        @Override
        public String getParameter(final String name) {
            final String[] value = addedParams.get(name);
            if (value != null && value.length > 0) {
                return value[0];
            }
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            return addedParams;
        }

        @Override
        public Enumeration getParameterNames() {
            final Vector<String> v = new Vector<>();
            for (final String key : addedParams.keySet()) {
                v.addElement(key);
            }
            return v.elements();
        }

        @Override
        public String[] getParameterValues(final String s) {
            final String[] value = addedParams.get(s);
            return value;
        }
    }
}