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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;

import org.apache.log4j.Logger;

import org.exist.source.Source;
import org.exist.source.DBSource;
import org.exist.source.SourceFactory;
import org.exist.source.FileSource;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
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
import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.BinaryDocument;
import org.exist.xmldb.XmldbURI;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.security.*;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.Descriptor;

import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;

import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponseWrapper;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

/**
 * A filter to redirect HTTP requests. Similar to the popular UrlRewriteFilter, but
 * based on XQuery.
 *
 * The request is passed to an XQuery whose return value determines where the request will be
 * redirected to. An empty return value means the request will be passed through the filter
 * untouched. Otherwise, the query should return a single XML element, which will instruct the filter
 * how to further process the request. Details about the format can be found in the main documentation.
 *
 * The request is forwarded via {@link javax.servlet.RequestDispatcher#forward(javax.servlet.ServletRequest, javax.servlet.ServletResponse)}.
 * Contrary to HTTP forwarding, there is no additional roundtrip to the client. It all happens on
 * the server. The client will not notice the redirect.
 *
 * XQueryURLrewrite is configured in web.xml as follows:
 * <pre>
 *   &lt;filter>
 *       &lt;filter-name>XQueryURLRewrite&lt;/filter-name>
 *       &lt;filter-class>org.exist.http.urlrewrite.XQueryURLRewrite&lt;/filter-class>
 *	     &lt;init-param>
 *			&lt;param-name>base&lt;/param-name>
 *	        &lt;param-value>.&lt;/param-value>
 *			&lt;!--param-value>xmldb:exist:///db&lt;/param-value-->
 *		&lt;/init-param>
 *      &lt;!--init-param>
 *          &lt;param-name>xquery&lt;/param-name>
 *          &lt;param-value>controller.xql&lt;/param-value>
 *      &lt;/init-param-->
 *   &lt;/filter>
 * </pre>
 *
 * Parameter "xquery" directly points to the controller XQuery which should be used for
 * <b>all</b> requests. The query may reside on the file system or can be stored in the
 * database. If it is stored in the db, the parameter value for "xquery" should be a valid
 * XML:DB URI, containing the complete path to the resource in the db.
 *
 * Alternatively, XQueryURLRewrite can try to search for a controller
 * matching the current request path. As above, it can either search the database collection
 * hierarchy or the file system. The starting point for the search is determined by parameter
 * "base".
 *
 * If neither "xquery" nor "base" are present, XQueryURLRewrite sets base to point to the current
 * webapp root directory.
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

    private Map<String, ModelAndView> urlCache = new HashMap<String, ModelAndView>();

    private User user;
    private BrokerPool pool;

    // path to the query
    private String query = null;
    
    private List<Source> sources = new ArrayList<Source>();

    private String basePath = null;
    private boolean checkModified = true;

    public void init(FilterConfig filterConfig) throws ServletException {
        // save FilterConfig for later use
        this.config = filterConfig;

        query = filterConfig.getInitParameter("xquery");
        basePath = filterConfig.getInitParameter("base");
        if (query == null && basePath == null)
            basePath = ".";
        
        String opt = filterConfig.getInitParameter("check-modified");
        if (opt != null)
            checkModified = opt != null && opt.equalsIgnoreCase("true");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        long start = System.currentTimeMillis();
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;
        if (request.getCharacterEncoding() == null) {
            try {
                request.setCharacterEncoding("UTF-8");
            } catch (IllegalStateException e) {
            }
        }

        if (LOG.isTraceEnabled())
            LOG.trace(request.getRequestURI());
        
        Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if(descriptor != null && descriptor.requestsFiltered())
        {
            String attr = (String) request.getAttribute("XQueryURLRewrite.forwarded");
            if (attr == null) {
//                request = new HttpServletRequestWrapper(request, /*formEncoding*/ "utf-8" );
                //logs the request if specified in the descriptor
                descriptor.doLogRequestInReplayLog(request);

                request.setAttribute("XQueryURLRewrite.forwarded", "true");
            }
        }

        try {
            configure();

            DBBroker broker = null;
            if (checkModified) {
                // check if any of the currently used sources has been updated
                // if yes, clear the cache
                for (int i = 0; i < sources.size(); i++) {
                    Source source = sources.get(i);
                    if (source instanceof DBSource) {
                        // Check if the XQuery source changed. If yes, clear all caches.
                        try {
                            broker = pool.get(user);
                            if (source.isValid(broker) != Source.VALID)
                                urlCache.clear();
                        } finally {
                            pool.release(broker);
                        }
                    } else {
                        if (source.isValid((DBBroker)null) != Source.VALID)
                            urlCache.clear();
                    }
                }
            }
            
            if (LOG.isTraceEnabled())
                LOG.trace("Processing request URI: " + request.getRequestURI());
            RequestWrapper modifiedRequest = new RequestWrapper(request);
            // check if the request URI is already in the url cache
            ModelAndView modelView = urlCache.get(request.getRequestURI());
            // no: create a new model and view configuration
            if (modelView == null) {
                modelView = new ModelAndView();
                // Execute the query
                Sequence result = Sequence.EMPTY_SEQUENCE;
                try {
                    broker = pool.get(user);
                    result = runQuery(broker, request, response);
                } finally {
                    pool.release(broker);
                }

                // process the query result
                if (result.getItemCount() == 1) {
                    Item resource = result.itemAt(0);
                    if (!Type.subTypeOf(resource.getType(), Type.NODE))
                        throw new ServletException("XQueryURLRewrite: urlrewrite query should return an element!");
                    Node node = ((NodeValue) resource).getNode();
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
                                if ("view".equals(action.getLocalName())) {
                                    parseViews(request, action, modelView);
                                } else if ("cache-control".equals(action.getLocalName())) {
                                    String option = action.getAttribute("cache");
                                    modelView.setUseCache("yes".equals(option));
                                } else {
                                    URLRewrite urw = parseAction(request, action);
                                    if (urw != null)
                                        modelView.setModel(urw);
                                }
                            }
                            node = node.getNextSibling();
                        }
                        if (modelView.getModel() == null)
                            modelView.setModel(new PassThrough(elem, request.getRequestURI()));
                    } else if ("ignore".equals(elem.getLocalName())) {
                        modelView.setModel(new PassThrough(elem, request.getRequestURI()));
                        NodeList nl = elem.getElementsByTagNameNS(Namespaces.EXIST_NS, "cache-control");
                        if (nl.getLength() > 0) {
                            elem = (Element) nl.item(0);
                            String option = elem.getAttribute("cache");
                            modelView.setUseCache("yes".equals(option));
                        }
                    }
                }

                // store the original request URI to org.exist.forward.request-uri
                modifiedRequest.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());
                modifiedRequest.setAttribute(RQ_ATTR_SERVLET_PATH, request.getServletPath());

                if (modelView.useCache()) {
                    urlCache.put(request.getRequestURI(), modelView);
                }
            }
            if (LOG.isTraceEnabled())
                LOG.trace("URLRewrite took " + (System.currentTimeMillis() - start) + "ms.");

            HttpServletResponse wrappedResponse = response;
            if (modelView.hasViews())
                wrappedResponse = new CachingResponseWrapper(response);
			if (modelView.getModel() == null)
                modelView.setModel(new PassThrough(request.getRequestURI()));
            modelView.getModel().prepareRequest(modifiedRequest);
            modelView.getModel().doRewrite(modifiedRequest, wrappedResponse, filterChain);

            if (modelView.hasViews()) {
                int status = ((CachingResponseWrapper) wrappedResponse).getStatus();
                if (status == HttpServletResponse.SC_NOT_MODIFIED) {
                    response.flushBuffer();
                } else if (status < 400) {
                    List views = modelView.views;
                    for (int i = 0; i < views.size(); i++) {
                        URLRewrite view = (URLRewrite) views.get(i);
                        RequestWrapper wrappedReq = new RequestWrapper(request);
                        wrappedReq.setMethod("POST");
                        wrappedReq.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
                        wrappedReq.setContentType(wrappedResponse.getContentType());
                        byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
                        if (data != null)
                            wrappedReq.setData(data);

                        if (i < views.size() - 1)
                            wrappedResponse = new CachingResponseWrapper(response);
                        else
                            wrappedResponse = response;
                        view.prepareRequest(wrappedReq);
                        view.doRewrite(wrappedReq, wrappedResponse, null);
                        wrappedResponse.flushBuffer();
                    }
                } else {
                    // HTTP response code indicates an error
                    byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
                    if (data != null) {
                        response.setContentType(wrappedResponse.getContentType());
                        response.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
                        response.getOutputStream().write(data);
                        response.flushBuffer();
                    }
                }
            }
//            Sequence result;
//            if ((result = (Sequence) request.getAttribute(RQ_ATTR_RESULT)) != null) {
//                writeResults(response, broker, result);
//            }
        } catch (EXistException e) {
            LOG.error(e.getMessage(), e);
            throw new ServletException("An error occurred while retrieving query results: " 
                    + e.getMessage(), e);

        } catch (XPathException e) {
            LOG.error(e.getMessage(), e);
            throw new ServletException("An error occurred while executing the urlrewrite query: " 
                    + e.getMessage(), e);

//        } catch (SAXException e) {
//            throw new ServletException("Error while serializing results: " + e.getMessage(), e);
            
        } catch (Throwable e){
            LOG.error(e.getMessage(), e);
            throw new ServletException("An error occurred: "
                    + e.getMessage(), e);
        }
    }

    private URLRewrite parseAction(HttpServletRequest request, Element action) throws ServletException {
        URLRewrite rewrite = null;
        if ("forward".equals(action.getLocalName())) {
            rewrite = new PathForward(config, action, request.getRequestURI());
        } else if ("redirect".equals(action.getLocalName())) {
            rewrite = new Redirect(action, request.getRequestURI());
//        } else if ("call".equals(action.getLocalName())) {
//            rewrite = new ModuleCall(action, queryContext, request.getRequestURI());
        }
        return rewrite;
    }

    private void parseViews(HttpServletRequest request, Element view, ModelAndView modelView) throws ServletException {
        Node node = view.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                URLRewrite urw = parseAction(request, (Element) node);
                modelView.addView(urw);
            }
            node = node.getNextSibling();
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
    }

    private Sequence runQuery(DBBroker broker, HttpServletRequest request, HttpServletResponse response) throws ServletException, XPathException {
        // Try to find the XQuery
        Source source;
        String moduleLoadPath = config.getServletContext().getRealPath(".");
        if (basePath == null)
            source = getSource(broker, moduleLoadPath);
        else
            source = findSource(request, broker, moduleLoadPath);
        sources.add(source);
        XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, source);
        XQueryContext queryContext;
        if (compiled == null) {
			queryContext = xquery.newContext(AccessContext.REST);
		} else {
			queryContext = compiled.getContext();
		}
        // Find correct module load path
		queryContext.setModuleLoadPath(moduleLoadPath);
        declareVariables(queryContext, request, response);
        if (compiled == null) {
			try {
				compiled = xquery.compile(queryContext, source);
			} catch (IOException e) {
				throw new ServletException("Failed to read query from " + query, e);
			}
		}
        try {
			return xquery.execute(compiled, null);
		} finally {
			pool.returnCompiledXQuery(source, compiled);
		}
    }

    private Source findSource(HttpServletRequest request, DBBroker broker, String moduleLoadPath) throws ServletException {
        String requestURI = request.getRequestURI();
        String path = requestURI.substring(request.getContextPath().length());
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] components = path.split("/");

        if (basePath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor(basePath);
                Collection collection = broker.openCollection(locationUri, Lock.READ_LOCK);
                if (collection == null)
                    throw new ServletException("Base collection not found: " + basePath);

                Collection subColl = collection;
                DocumentImpl controllerDoc = null;
                for (int i = 0; i < components.length; i++) {
                    if (components[i].length() > 0) {
                        if (subColl.hasChildCollection(XmldbURI.createInternal(components[i]))) {
                            DocumentImpl doc = null;
                            try {
                                subColl = broker.openCollection(subColl.getURI().append(components[i]), Lock.READ_LOCK);
                                if (subColl != null) {
                                    XmldbURI docUri = subColl.getURI().append("controller.xql");
                                    doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
                                    if (doc != null)
                                        controllerDoc = doc;
                                } else
                                    break;
                            } catch (PermissionDeniedException e) {
                                LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " +
                                    e.getMessage(), e);
                            } finally {
                                if (doc != null)
                                    doc.getUpdateLock().release(Lock.READ_LOCK);
                                if (subColl != null)
                                    subColl.getLock().release(Lock.READ_LOCK);
                            }
                        } else
                            break;
                    }
                }
                if (controllerDoc == null) {
                    try {
                        XmldbURI docUri = collection.getURI().append("controller.xql");
                        controllerDoc = broker.getXMLResource(docUri, Lock.READ_LOCK);
                    } catch (PermissionDeniedException e) {
                        LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " +
                            e.getMessage(), e);
                    }
                }
                if (controllerDoc == null)
                    throw new ServletException("XQueryURLRewrite controller could not be found");
                if (controllerDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !controllerDoc.getMetadata().getMimeType().equals("application/xquery"))
                        throw new ServletException("XQuery resource: " + query + " is not an XQuery or " +
                                "declares a wrong mime-type");
                    return new DBSource(broker, (BinaryDocument) controllerDoc, true);
            } catch (URISyntaxException e) {
                throw new ServletException("Bad URI for base path: " + e.getMessage(), e);
            }
        } else {
            String realPath = config.getServletContext().getRealPath(basePath);
            File baseDir = new File(realPath);
            if (!baseDir.isDirectory())
                throw new ServletException("Base path for XQueryURLRewrite does not point to a directory");

            File controllerFile = null;
            File subDir = baseDir;
            for (int i = 0; i < components.length; i++) {
                if (components[i].length() > 0) {
                    subDir = new File(subDir, components[i]);
                    if (subDir.isDirectory()) {
                        File cf = new File(subDir, "controller.xql");
                        if (cf.canRead())
                            controllerFile = cf;
                    } else
                        break;
                }
            }
            if (controllerFile == null) {
                File cf = new File(baseDir, "controller.xql");
                if (cf.canRead())
                    controllerFile = cf;
            }
            if (controllerFile == null)
                throw new ServletException("XQueryURLRewrite controller could not be found");
            if (LOG.isTraceEnabled())
                LOG.trace("Found controller file: " + controllerFile.getAbsolutePath());
            return new FileSource(controllerFile, "UTF-8", true);
        }
    }
    
    private Source getSource(DBBroker broker, String moduleLoadPath) throws ServletException {
        Source source;
        if (query.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            // Is the module source stored in the database?
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor(query);
                DocumentImpl sourceDoc = null;
                try {
                    sourceDoc = broker.getXMLResource(locationUri.toCollectionPathURI(), Lock.READ_LOCK);
                    if (sourceDoc == null)
                        throw new ServletException("XQuery resource: " + query + " not found in database");
                    if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !sourceDoc.getMetadata().getMimeType().equals("application/xquery"))
                        throw new ServletException("XQuery resource: " + query + " is not an XQuery or " +
                                "declares a wrong mime-type");
                    source = new DBSource(broker, (BinaryDocument) sourceDoc, true);
                } catch (PermissionDeniedException e) {
                    throw new ServletException("permission denied to read module source from " + query);
                } finally {
                    if(sourceDoc != null)
                        sourceDoc.getUpdateLock().release(Lock.READ_LOCK);
                }
            } catch(URISyntaxException e) {
                throw new ServletException(e.getMessage(), e);
            }
        } else {
            try {
                source = SourceFactory.getSource(broker, moduleLoadPath, query, true);
            } catch (IOException e) {
                throw new ServletException("IO error while reading XQuery source: " + query);
            } catch (PermissionDeniedException e) {
                throw new ServletException("Permission denied while reading XQuery source: " + query);
            }
        }
        return source;
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
		context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession( false ));
	}

    private class ModelAndView {

        URLRewrite rewrite = null;
        List views = new LinkedList();
        boolean useCache = false;

        private ModelAndView() {
        }

        public void setModel(URLRewrite model) {
            this.rewrite = model;
        }

        public URLRewrite getModel() {
            return rewrite;
        }

        public void addView(URLRewrite view) {
            views.add(view);
        }

        public boolean hasViews() {
            return views.size() > 0;
        }

        public boolean useCache() {
            return useCache;
        }

        public void setUseCache(boolean useCache) {
            this.useCache = useCache;
        }
    }

    public static class RequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

        Map addedParams = new HashMap();

        Map attributes = new HashMap();
        
        ServletInputStream sis = null;
        BufferedReader reader = null;

        String contentType = null;
        int contentLength = 0;
        String characterEncoding = null;
        String method = null;

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

            contentType = request.getContentType();
        }

        public String getMethod() {
            if (method == null)
                return super.getMethod();
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }
        
        protected void setData(byte[] data) {
            if (data == null)
                data = new byte[0];
            contentLength = data.length;
            sis = new CachingServletInputStream(data);
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

        public ServletInputStream getInputStream() throws IOException {
            if (sis == null)
                return super.getInputStream();
            return sis;
        }

        public BufferedReader getReader() throws IOException {
            if (sis == null)
                return super.getReader();
            if (reader == null)
                reader = new BufferedReader(new InputStreamReader(sis, getCharacterEncoding()));
            return reader;
        }

        public String getContentType() {
            if (contentType == null)
                return super.getContentType();
            return contentType;
        }

        protected void setContentType(String contentType) {
            this.contentType = contentType;
        }

        public int getContentLength() {
            if (sis == null)
                return super.getContentLength();
            return contentLength;
        }

        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
            this.characterEncoding = encoding;
        }

        public String getCharacterEncoding() {
            if (characterEncoding == null)
                return super.getCharacterEncoding();
            return characterEncoding;
        }

        public String getHeader(String s) {
            if (s.equals("If-Modified-Since"))
                return null;
            return super.getHeader(s);
        }

        public long getDateHeader(String s) {
            if (s.equals("If-Modified-Since"))
                return -1;
            return super.getDateHeader(s);
        }

        //        public void setAttribute(String key, Object value) {
//            attributes.put(key, value);
//        }
//
//        public Object getAttribute(String key) {
//            Object value = attributes.get(key);
//            if (value == null)
//                value = super.getAttribute(key);
//            return value;
//        }
//
//        public Enumeration getAttributeNames() {
//            Vector v = new Vector();
//            for (Enumeration e = super.getAttributeNames(); e.hasMoreElements();) {
//                v.add(e.nextElement());
//            }
//            for (Iterator i = attributes.keySet().iterator(); i.hasNext();) {
//                v.add(i.next());
//            }
//            return v.elements();
//        }
    }

    private class CachingResponseWrapper extends HttpServletResponseWrapper {

        protected HttpServletResponse origResponse;
        protected CachingServletOutputStream sos = null;
        protected PrintWriter writer = null;
        protected int status = HttpServletResponse.SC_OK;

        public CachingResponseWrapper(HttpServletResponse servletResponse) {
            super(servletResponse);
            origResponse = servletResponse;
        }

        public PrintWriter getWriter() throws IOException {
            if (sos != null)
                throw new IOException("getWriter cannnot be called after getOutputStream");
            sos = new CachingServletOutputStream();
            if (writer == null)
                writer = new PrintWriter(new OutputStreamWriter(sos, getCharacterEncoding()));
            return writer;
        }

        public ServletOutputStream getOutputStream() throws IOException {
            if (writer != null)
                throw new IOException("getOutputStream cannnot be called after getWriter");
            if (sos == null)
                sos = new CachingServletOutputStream();
            return sos;
        }

        public byte[] getData() {
            return sos != null ? sos.getData() : null;
        }

        public int getStatus() {
            return status;
        }
        
        public void setStatus(int i) {
            this.status = i;
            super.setStatus(i);
        }

        public void setStatus(int i, String msg) {
            this.status = i;
            super.setStatus(i, msg);
        }

        public void sendError(int i, String msg) throws IOException {
            this.status = i;
            super.sendError(i, msg);
        }

        public void sendError(int i) throws IOException {
            this.status = i;
            super.sendError(i);
        }

        public void setContentLength(int i) {
        }

        public void flushBuffer() throws IOException {
        }
    }

    private class CachingServletOutputStream extends ServletOutputStream {

        protected ByteArrayOutputStream ostream = new ByteArrayOutputStream(512);

        protected byte[] getData() {
            return ostream.toByteArray();
        }

        public void write(int b) throws IOException {
            ostream.write(b);
        }

        public void write(byte b[]) throws IOException {
            ostream.write(b);
        }

        public void write(byte b[], int off, int len) throws IOException {
            ostream.write(b, off, len);
        }
    }

    private static class CachingServletInputStream extends ServletInputStream {

        protected ByteArrayInputStream istream;

        public CachingServletInputStream(byte[] data) {
            if (data == null)
                istream = new ByteArrayInputStream(new byte[0]);
            else
                istream = new ByteArrayInputStream(data);
        }
        
        public int read() throws IOException {
           return istream.read();
        }

        public int read(byte b[]) throws IOException {
            return istream.read(b);
        }

        public int read(byte b[], int off, int len) throws IOException {
            return istream.read(b, off, len);
        }

        public int available() throws IOException {
            return istream.available(); 
        }
    }
}
