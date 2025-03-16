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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.security.PermissionDeniedException;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Servlet to redirect HTTP requests. The request is passed to an XQuery whose return value
 * determines where the request will be redirected to. The query should return a single XML element:
 *
 * <pre>
 *  &lt;exist:dispatch xmlns:exist="http://exist.sourceforge.net/NS/exist"
 *      path="/preview.xql" servlet-name="MyServlet" redirect="path"&gt;
 *       &lt;exist:add-parameter name="new-param" value="new-param-value"/&gt;
 *   &lt;/exist:dispatch&gt;
 * </pre>
 *
 * The element should have one of three attributes: <em>path</em>, <em>servlet-name</em> or
 * <em>redirect</em>.
 *
 * If the servlet-name attribute is present, the request will be forwarded to the named servlet
 * (name as specified in web.xml). Alternatively, path can point to an arbitrary resource. It can be either absolute or relative.
 * Relative paths are resolved relative to the original request.
 *
 * The request is forwarded via {@link jakarta.servlet.RequestDispatcher#forward(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse)}.
 * Contrary to HTTP forwarding, there is no additional roundtrip to the client. It all happens on
 * the server. The client will not notice the redirect.
 *
 * When forwarding to other servlets, the fields in {@link jakarta.servlet.http.HttpServletRequest} will be
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
 * &lt;servlet&gt;
 *       &lt;servlet-name&gt;RedirectorServlet&lt;/servlet-name&gt;
 *       &lt;servlet-class&gt;org.exist.http.servlets.RedirectorServlet&lt;/servlet-class&gt;
 *
 *       &lt;init-param&gt;
 *           &lt;param-name&gt;xquery&lt;/param-name&gt;
 *           &lt;param-value&gt;dispatcher.xql&lt;/param-value&gt;
 *       &lt;/init-param&gt;
 *   &lt;/servlet&gt;
 *
 * &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;RedirectorServlet&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/wiki/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 */
@Deprecated
public class RedirectorServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = 853971301553787943L;

    private static final Logger LOG = LogManager.getLogger(RedirectorServlet.class);

    public final static String DEFAULT_USER = "guest";
    public final static String DEFAULT_PASS = "guest";
    public final static XmldbURI DEFAULT_URI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);

    private String user = null;
    private String password = null;
    private XmldbURI collectionURI = null;
    private String query = null;

    @Override
    public void init(final ServletConfig config) throws ServletException {
        query = config.getInitParameter("xquery");
        if (query == null) {
            throw new ServletException("RedirectorServlet requires a parameter 'xquery'.");
        }
        user = config.getInitParameter("user");
        if (user == null) {
            user = DEFAULT_USER;
        }
        password = config.getInitParameter("password");
        if (password == null) {
            password = DEFAULT_PASS;
        }
        final String confCollectionURI = config.getInitParameter("uri");
        if (confCollectionURI == null) {
            collectionURI = DEFAULT_URI;
        } else {
            try {
                collectionURI = XmldbURI.xmldbUriFor(confCollectionURI);
            } catch (final URISyntaxException e) {
                throw new ServletException("Invalid XmldbURI for parameter 'uri': " + e.getMessage(), e);
            }
        }

        super.init(config);
    }

    @Override
    protected void service(final HttpServletRequest req, final HttpServletResponse res) throws ServletException, IOException {
        final RequestWrapper request = new HttpRequestWrapper(req);
        final ResponseWrapper response = new HttpResponseWrapper(res);
        if (request.getCharacterEncoding() == null)
            try {
                request.setCharacterEncoding(UTF_8.name());
            } catch (final IllegalStateException e) {
            }
        // Try to find the XQuery
        final String qpath = getServletContext().getRealPath(query);
        final Path p = Paths.get(qpath);
        if (!(Files.isReadable(p) && Files.isRegularFile(p))) {
            throw new ServletException("Cannot read XQuery source from " + p.toAbsolutePath());
        }
        final FileSource source = new FileSource(p, true);

        try {
            // Prepare and execute the XQuery
            final Sequence result = executeQuery(source, request, response);

            String redirectTo = null;
            String servletName = null;
            String path = null;
            ModifiableRequestWrapper modifiedRequest = null;
            // parse the query result element
            if (result != null && result.getItemCount() == 1) {
                Node node = (Node)result.itemAt(0);
                if (node.getNodeType() == Node.DOCUMENT_NODE) {
                    node = ((Document) node).getDocumentElement();
                }
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Redirect XQuery should return an XML element. Received: " + node);
                    return;
                }
                Element elem = (Element) node;
                final String ns = elem.getNamespaceURI();
                if (ns == null || ((!Namespaces.EXIST_NS.equals(ns)) && "dispatch".equals(elem.getLocalName()))) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Redirect XQuery should return an element <exist:dispatch>. Received: " + node);
                    return;
                }
                if (elem.hasAttribute("path")) {
                    path = elem.getAttribute("path");
                } else if (elem.hasAttribute("servlet-name")) {
                    servletName = elem.getAttribute("servlet-name");
                } else if (elem.hasAttribute("redirect")) {
                    redirectTo = elem.getAttribute("redirect");
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST,
                            "Element <exist:dispatch> should either provide an attribute 'path' or 'servlet-name'. Received: " +
                                    node);
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
                                if (modifiedRequest == null) {
                                    modifiedRequest = new ModifiableRequestWrapper(req);
                                }
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
            if (servletName != null && !servletName.isEmpty()) {
                dispatcher = getServletContext().getNamedDispatcher(servletName);
            } else {
                LOG.debug("Dispatching to {}", path);
                dispatcher = getServletContext().getRequestDispatcher(path);
                if (dispatcher == null) {
                    dispatcher = request.getRequestDispatcher(path);
                }
            }
            if (dispatcher == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Could not create a request dispatcher. Giving up.");
                return;
            }

            if (modifiedRequest != null) {
                // store the original request URI to org.exist.forward.request-uri
                modifiedRequest.setAttribute("org.exist.forward.request-uri", modifiedRequest.getRequestURI());
                modifiedRequest.setAttribute("org.exist.forward.servlet-path", modifiedRequest.getServletPath());

                // finally, execute the forward
                dispatcher.forward(modifiedRequest, res);
            } else {
                // store the original request URI to org.exist.forward.request-uri
                request.setAttribute("org.exist.forward.request-uri", request.getRequestURI());
                request.setAttribute("org.exist.forward.servlet-path", request.getServletPath());

                // finally, execute the forward
                dispatcher.forward(req, res);
            }
        } catch (final XPathException | EXistException | PermissionDeniedException | IOException e) {
            throw new ServletException("An error occurred while executing the RedirectorServlet XQuery: " + e.getMessage(), e);
        }
    }

    private Sequence executeQuery(final Source source, final RequestWrapper request, final ResponseWrapper response) throws EXistException, XPathException, PermissionDeniedException, IOException {
        final XQuery xquery = getPool().getXQueryService();
        final XQueryPool pool = getPool().getXQueryPool();

        try (final DBBroker broker = getPool().getBroker()) {

            final XQueryContext context;
            CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
            if (compiled == null) {
                // special header to indicate that the query is not returned from
                // cache
                response.setHeader("X-XQuery-Cached", "false");
                context = new XQueryContext(getPool());
                context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI.toString());
                compiled = xquery.compile(context, source);
            } else {
                response.setHeader("X-XQuery-Cached", "true");
                context = compiled.getContext();
                context.prepareForReuse();
            }

            try {
                return xquery.execute(broker, compiled, null, new Properties());
            } finally {
                context.runCleanupTasks();
                pool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    @Override
    public Logger getLog() {
        return LOG;
    }

    private static class ModifiableRequestWrapper extends jakarta.servlet.http.HttpServletRequestWrapper {

        private final Map<String, String[]> addedParams = new HashMap<>();

        private ModifiableRequestWrapper(final HttpServletRequest request) {
            super(request);
            // copy parameters
            for (final Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                final String key = (String) e.nextElement();
                final String[] value = request.getParameterValues(key);
                addedParams.put(key, value);
            }
        }

        public void addParameter(final String name, final String value) {
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
