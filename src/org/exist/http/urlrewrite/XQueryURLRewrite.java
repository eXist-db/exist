/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id$
 */
package org.exist.http.urlrewrite;

import org.apache.log4j.Logger;
import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.XQueryService;
import org.exist.xmldb.CollectionImpl;
import org.exist.source.FileSource;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.Constants;
import org.exist.Namespaces;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.HttpSessionWrapper;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XMLResource;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Iterator;

/**
 * A filter to redirect HTTP requests. Similar to the popular UrlRewriteFilter, but
 * based on XQuery.
 *
 * The request is passed to an XQuery whose return value determines where the request will be
 * redirected to. An empty return value means the request will be passed through the filter
 * untouched. Otherwise, the query should return a single XML element, either
 * &lt;exist:dispatch&gt; or &lt;exist:ignore&gt;:
 *
 * <pre>
 *  &lt;exist:dispatch xmlns:exist="http://exist.sourceforge.net/NS/exist"
 *      path="/preview.xql" servlet-name="MyServlet" redirect="path">
 *       &lt;exist:add-parameter name="new-param" value="new-param-value"/>
 *       &lt;exist:cache-control cache="yes|no"/>
 *   &lt;/exist:dispatch>
 *
 *  &lt;exist:ignore xmlns:exist="http://exist.sourceforge.net/NS/exist"/&gt;
 * </pre>
 *
 * &lt;exist:ignore&gt; has the same effect as returning the empty sequence from the query: the request
 * will not be touched and is passed on to the next filter or servlet.
 *
 * &lt;exist:dispatch&gt; should have one of three attributes: <em>path</em>, <em>servlet-name</em> or
 * <em>redirect</em>.
 *
 * If the <em>servlet-name</em> attribute is present, the request will be forwarded to the named servlet
 * (name as specified in web.xml). Alternatively, <em>path</em> can point to an arbitrary resource. It can be either absolute or relative.
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
 * As an alternative to the server-side forward, the <em>redirect</em> attribute causes the server to send a redirect request to the client, which will usually respond
 * with a new request to the redirected location. Note that this is quite different from a forwarding via RequestDispatcher,
 * which is completely transparent to the client.
 *
 * One or more &lt;exist:add-parameter&gt; can be used to pass additional query parameters in the URL. The parameters will
 * be added to the already existing parameters of the original request.
 *
 * The &lt;cache-control&lt; element controls if the rewritten URL will be cached or not. For a cached URL, the XQuery will not
 * be executed again. Instead, the forward or redirect is executed directly. By default caching is disabled. Please note that
 * request parameters are not taken into account when caching the URL. 
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
public class XQueryURLRewrite implements Filter {

    private static final Logger LOG = Logger.getLogger(XQueryURLRewrite.class);

    public final static String DEFAULT_USER = "guest";
    public final static String DEFAULT_PASS = "guest";
    public final static XmldbURI DEFAULT_URI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private FilterConfig config;

    private Map urlCache = new HashMap();
    
    private String user = null;
    private String password = null;
    private XmldbURI collectionURI = null;
    // path to the query
    private String query = null;

    public void init(FilterConfig filterConfig) throws ServletException {
        // save FilterConfig for later use
        this.config = filterConfig;
        
        query = filterConfig.getInitParameter("xquery");
        if (query == null)
            throw new ServletException(getClass().getName() + " requires a parameter 'xquery'.");
        user = filterConfig.getInitParameter("user");
        if(user == null)
            user = DEFAULT_USER;
        password = filterConfig.getInitParameter("password");
        if(password == null)
            password = DEFAULT_PASS;
        String confCollectionURI = filterConfig.getInitParameter("uri");
        if(confCollectionURI == null) {
            collectionURI = DEFAULT_URI;
        } else {
            try {
                collectionURI = XmldbURI.xmldbUriFor(confCollectionURI);
            } catch (URISyntaxException e) {
                throw new ServletException("Invalid XmldbURI for parameter 'uri': "+e.getMessage(),e);
            }
        }
        try {
            Class driver = Class.forName(DRIVER);
            Database database = (Database)driver.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
        } catch(Exception e) {
            String errorMessage="Failed to initialize database driver";
            LOG.error(errorMessage,e);
            throw new ServletException(errorMessage+": " + e.getMessage(), e);
        }
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (request.getCharacterEncoding() == null) {
            try {
                request.setCharacterEncoding("UTF-8");
            } catch (IllegalStateException e) {
            }
        }

        try {
            URLRewrite rewrite = (URLRewrite) urlCache.get(request.getRequestURI());
            if (rewrite == null) {
                // Execute the query
                ResourceSet result = runQuery(request, response);

                RequestWrapper modifiedRequest = null;
                boolean useCache = false;
                // process the query result
                if (result.getSize() == 1) {
                    XMLResource resource = (XMLResource) result.getResource(0);
                    if (LOG.isTraceEnabled())
                        LOG.trace(resource.getContent());
                    Node node = resource.getContentAsDOM();
                    if (node.getNodeType() == Node.DOCUMENT_NODE)
                        node = ((Document) node).getDocumentElement();
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        throw new ServletException("Redirect XQuery should return an XML element. Received: " +
                                resource.getContent());
                    }
                    Element elem = (Element) node;
                    if (!(Namespaces.EXIST_NS.equals(elem.getNamespaceURI())))
                    {
                        throw new ServletException("Redirect XQuery should return an element in namespace " + Namespaces.EXIST_NS +
                            ". Received: " + resource.getContent());
                    }

                    if ("dispatch".equals(elem.getLocalName())) {
                        if (elem.hasAttribute("path")) {
                            String path = elem.getAttribute("path");
                            if (path.length() == 0)
                                    throw new ServletException("attribute path of <exist:dispatch> should not be empty.");
                            rewrite = new PathForward(request.getRequestURI(), path);
                        } else if (elem.hasAttribute("servlet-name")) {
                            String servletName = elem.getAttribute("servlet-name");
                            if (servletName.length() == 0)
                                throw new ServletException("attribute servlet-name of <exist:dispatch> should not be empty.");
                            rewrite = new ServletForward(config, request.getRequestURI(), servletName);
                        } else if (elem.hasAttribute("redirect")) {
                            String redirectTo = elem.getAttribute("redirect");
                            if (redirectTo.length() == 0)
                                throw new ServletException("attribute redirect of <exist:dispatch> should not be empty.");
                            rewrite = new Redirect(request.getRequestURI(), redirectTo);
                        } else {
                            throw new ServletException("Element <exist:dispatch> should either provide " +
                                    "an attribute 'path' or 'servlet-name'. Received: " + resource.getContent());
                        }
                    } else if ("ignore".equals(elem.getLocalName())) {
                        rewrite = new PassThrough(request.getRequestURI());
                    }

                    // Check for add-parameter elements etc.
                    if (elem.hasChildNodes()) {
                        node = elem.getFirstChild();
                        while (node != null) {
                            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                                elem = (Element) node;
                                if ("add-parameter".equals(elem.getLocalName())) {
                                    if (modifiedRequest == null)
                                        modifiedRequest = new RequestWrapper(request);
                                    modifiedRequest.addParameter(elem.getAttribute("name"), elem.getAttribute("value"));
                                } else if ("cache-control".equals(elem.getLocalName())) {
                                    String option = elem.getAttribute("cache");
                                    useCache = "yes".equals(option);
                                }
                            }
                            node = node.getNextSibling();
                        }
                    }
                    if (modifiedRequest != null)
                        request = modifiedRequest;
                } else {
                    // empty result: pass through the filter chain
                    rewrite = new PassThrough(request.getRequestURI());
                }

                // store the original request URI to org.exist.forward.request-uri
                request.setAttribute("org.exist.forward.request-uri", request.getRequestURI());
                request.setAttribute("org.exist.forward.servlet-path", request.getServletPath());

                if (useCache) {
                    urlCache.put(request.getRequestURI(), rewrite);
                }
            }

            rewrite.doRewrite(request, response, filterChain);
        } catch (XMLDBException e) {
            throw new ServletException("An error occurred while retrieving query results: " + e.getMessage(), e);
        }
    }

    public void destroy() {
        collectionURI = null;
        config = null;
    }

    private ResourceSet runQuery(HttpServletRequest request, HttpServletResponse response) throws ServletException {
        // Try to find the XQuery
        String qpath = config.getServletContext().getRealPath(query);
        File f = new File(qpath);
        if (!(f.canRead() && f.isFile()))
            throw new ServletException("Cannot read XQuery source from " + f.getAbsolutePath());
        FileSource source = new FileSource(f, "UTF-8", true);

        // Find correct module load path
        String moduleLoadPath = f.getParentFile().getAbsolutePath();
        try {
            // Prepare and execute the XQuery
            Collection collection = DatabaseManager.getCollection(collectionURI.toString(), user, password);
            XQueryService service = (XQueryService) collection.getService("XQueryService", "1.0");
            if(!((CollectionImpl)collection).isRemoteCollection()) {
                service.setModuleLoadPath(moduleLoadPath);
                service.declareVariable(RequestModule.PREFIX + ":request", new HttpRequestWrapper(request, "UTF-8", "UTF-8", false));
                service.declareVariable(ResponseModule.PREFIX + ":response", new HttpResponseWrapper(response));
                service.declareVariable(SessionModule.PREFIX + ":session", new HttpSessionWrapper(request.getSession()));
            }
            return service.execute(source);
        } catch (XMLDBException e) {
            throw new ServletException("An error occurred while executing the redirect XQuery: " +
                    e.getMessage(), e);
        }
    }

    private class RequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

        Map addedParams = new HashMap();

        private RequestWrapper(HttpServletRequest request) {
            super(request);
            // copy parameters
            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                String[] value = request.getParameterValues(key);
                if (value.length == 1)
                    addedParams.put(key, value[0]);
                else
                    addedParams.put(key, value);
            }
        }

        public void addParameter(String name, String value) {
            addedParams.put(name, value);
        }

        public String getParameter(String name) {
            return (String) addedParams.get(name);
        }

        public Map getParameterMap() {
            return addedParams;
        }

        public Enumeration getParameterNames() {
            Vector v = new Vector();
            for (Iterator i = addedParams.keySet().iterator(); i.hasNext(); ) {
                String key = (String) i.next();
                v.addElement(key);
            }
            return v.elements();
        }

        public String[] getParameterValues(String s) {
            Object value = addedParams.get(s);
            if (value != null) {
                if (value instanceof String[])
                    return (String[]) value;
                else
                    return new String[] { value.toString() };
            }
            return null;
        }
    }
}
