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
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.NodeValue;
import org.exist.Namespaces;
import org.exist.EXistException;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.security.*;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.serializers.Serializer;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.HttpSessionWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.http.BadRequestException;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XMLResource;
import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.File;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.HashMap;
import java.util.Enumeration;
import java.util.Vector;
import java.util.Iterator;
import java.util.Properties;

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
 *  &lt;exist:dispatch xmlns:exist="http://exist.sourceforge.net/NS/exist">
 *      &lt;!-- use exist:forward to forward the request to a different url -->
 *      &lt;exist:forward url="..."/>
 *      &lt;!-- or servlet: -->
 *      &lt;exist:forward servlet="..."/>
 *      &lt;!-- use exist:redirect to trigger a client redirect -->
 *      &lt;exist:redirect url="..."/>
 *      &lt;!-- pass additional parameters -->
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

    public final static String RQ_ATTR_REQUEST_URI = "org.exist.forward.request-uri";
    public final static String RQ_ATTR_SERVLET_PATH = "org.exist.forward.servlet-path";
    public final static String RQ_ATTR_RESULT = "org.exist.forward.result";
    
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private FilterConfig config;

    private Map urlCache = new HashMap();

    private User user;
    private BrokerPool pool;
    private XQueryContext queryContext;

    // path to the query
    private String query = null;

    public void init(FilterConfig filterConfig) throws ServletException {
        // save FilterConfig for later use
        this.config = filterConfig;

        query = filterConfig.getInitParameter("xquery");
        if (query == null)
            throw new ServletException(getClass().getName() + " requires a parameter 'xquery'.");
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

        DBBroker broker = null;
        try {
            configure();
            broker = pool.get(user);

            URLRewrite rewrite = (URLRewrite) urlCache.get(request.getRequestURI());
            if (rewrite == null) {
                // Execute the query
                Sequence result = runQuery(broker, request, response);

                RequestWrapper modifiedRequest = null;
                boolean useCache = false;
                // process the query result
                if (result.getItemCount() == 1) {
                    Item resource = result.itemAt(0);
                    if (!Type.subTypeOf(resource.getType(), Type.NODE))
                        throw new ServletException("XQueryURLRewrite: urlrewrite query should return an element!");
                    Node node = ((NodeValue)resource).getNode();
                    if (node.getNodeType() == Node.DOCUMENT_NODE)
                        node = ((Document) node).getDocumentElement();
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        throw new ServletException("Redirect XQuery should return an XML element!");
                    }
                    Element elem = (Element) node;
                    if (!(Namespaces.EXIST_NS.equals(elem.getNamespaceURI()))) {
                        throw new ServletException("Redirect XQuery should return an element in namespace " + Namespaces.EXIST_NS);
                    }

                    if ("dispatch".equals(elem.getLocalName())) {
                        node = elem.getFirstChild();
                        while (node != null) {
                            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                                Element action = (Element) node;
                                if ("forward".equals(action.getLocalName())) {
                                    rewrite = new PathForward(config, action, request.getRequestURI());
                                } else if ("redirect".equals(action.getLocalName())) {
                                    rewrite = new Redirect(action, request.getRequestURI());
                                } else if ("call".equals(action.getLocalName())) {
                                    rewrite = new ModuleCall(action, queryContext, request.getRequestURI());
                                }
                            }
                            node = node.getNextSibling();
                        }
                    } else if ("ignore".equals(elem.getLocalName())) {
                        rewrite = new PassThrough(elem, request.getRequestURI());
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
                request.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());
                request.setAttribute(RQ_ATTR_SERVLET_PATH, request.getServletPath());

                if (useCache) {
                    urlCache.put(request.getRequestURI(), rewrite);
                }
            }
            LOG.debug("REQUEST: " + request.getClass().getName());
            if (rewrite == null)
                throw new ServletException("No URL rewrite rule found! Giving up.");
            rewrite.doRewrite(request, response, filterChain);

            Sequence result;
            if ((result = (Sequence) request.getAttribute(RQ_ATTR_RESULT)) != null) {
                writeResults(response, broker, result);
            }
        } catch (EXistException e) {
            throw new ServletException("An error occurred while retrieving query results: " + e.getMessage(), e);
        } catch (XPathException e) {
            throw new ServletException("An error occurred while executing the urlrewrite query: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new ServletException("Error while serializing results: " + e.getMessage(), e);
        } finally {
            if (pool != null)
                pool.release(broker);
        }
    }

    private void configure() throws EXistException, ServletException {
        if (pool == null) {
            String username = config.getInitParameter("user");
            if(username == null)
                username = DEFAULT_USER;
            String password = config.getInitParameter("password");
            if(password == null)
                password = DEFAULT_PASS;
            pool = BrokerPool.getInstance();
            org.exist.security.SecurityManager secman = pool.getSecurityManager();
            user = secman.getUser(username);
            if (!user.validate(password)) {
                throw new ServletException("Invalid password specified for XQueryURLRewrite user");
            }
        }
    }

    private void writeResults(HttpServletResponse response, DBBroker broker, Sequence result) throws IOException, SAXException {
        if (result.getItemCount() > 0) {
            ServletOutputStream os = response.getOutputStream();
            Writer writer = new OutputStreamWriter(os, "UTF-8");
            Serializer serializer = broker.getSerializer();
            serializer.reset();
            SAXSerializer sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
            sax.setOutput(writer, new Properties());
            serializer.setSAXHandlers(sax, sax);

            serializer.toSAX(result, 1, result.getItemCount(), false);

            writer.close();
        }
    }

    public void destroy() {
        config = null;
        queryContext = null;
    }

    private Sequence runQuery(DBBroker broker, HttpServletRequest request, HttpServletResponse response) throws ServletException, XPathException {
        // Try to find the XQuery
        String qpath = config.getServletContext().getRealPath(query);
        File f = new File(qpath);
        if (!(f.canRead() && f.isFile()))
            throw new ServletException("Cannot read XQuery source from " + f.getAbsolutePath());
        FileSource source = new FileSource(f, "UTF-8", true);

        XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        if (compiled == null) {
			queryContext = xquery.newContext(AccessContext.REST);
		} else {
			queryContext = compiled.getContext();
		}
        // Find correct module load path
		queryContext.setModuleLoadPath(f.getParentFile().getAbsolutePath());
        declareVariables(queryContext, request, response);
        if (compiled == null) {
			try {
				compiled = xquery.compile(queryContext, source);
			} catch (IOException e) {
				throw new ServletException("Failed to read query from " + f.getAbsolutePath(), e);
			}
		}
        try {
			return xquery.execute(compiled, null);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}
    }

    private void declareVariables(XQueryContext context,
			HttpServletRequest request, HttpServletResponse response)
			throws XPathException {
		HttpRequestWrapper reqw = new HttpRequestWrapper(request, "UTF-8", "UTF-8", false);
		HttpResponseWrapper respw = new HttpResponseWrapper(response);
		// context.declareNamespace(RequestModule.PREFIX,
		// RequestModule.NAMESPACE_URI);
		context.declareVariable(RequestModule.PREFIX + ":request", reqw);
		context.declareVariable(ResponseModule.PREFIX + ":response", respw);
		context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession());
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
