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
import org.exist.xquery.*;
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
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;

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
import java.util.regex.Pattern;
import java.util.regex.Matcher;

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
 * Please read the <a href="http://exist-db.org/urlrewrite.html">documentation</a> for further information. 
 */
public class XQueryURLRewrite implements Filter {

    private static final Logger LOG = Logger.getLogger(XQueryURLRewrite.class);

    public final static String DEFAULT_USER = "guest";
    public final static String DEFAULT_PASS = "guest";

    public final static String RQ_ATTR_REQUEST_URI = "org.exist.forward.request-uri";
    public final static String RQ_ATTR_SERVLET_PATH = "org.exist.forward.servlet-path";
    public final static String RQ_ATTR_RESULT = "org.exist.forward.result";
    
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final static Pattern NAME_REGEX = Pattern.compile("^.*/([^/]+)$", 0);

    private FilterConfig config;

    private final Map<String, ModelAndView> urlCache = new HashMap<String, ModelAndView>();

    protected User user;
    protected BrokerPool pool;

    // path to the query
    private String query = null;
    
    private final Map<Object, Source> sources = new HashMap<Object, Source>();

    private boolean checkModified = true;

    private RewriteConfig rewriteConfig;
    
    public void init(FilterConfig filterConfig) throws ServletException {
        // save FilterConfig for later use
        this.config = filterConfig;

        query = filterConfig.getInitParameter("xquery");
        
        String opt = filterConfig.getInitParameter("check-modified");
        if (opt != null)
            checkModified = opt != null && opt.equalsIgnoreCase("true");
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (rewriteConfig == null) {
            configure();
            rewriteConfig = new RewriteConfig(this);
        }
        
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
            checkCache();

            RequestWrapper modifiedRequest = new RequestWrapper(request);
            URLRewrite staticRewrite = rewriteConfig.lookup(modifiedRequest);
            if (staticRewrite != null && !staticRewrite.isControllerForward()) {
                modifiedRequest.setPaths(staticRewrite.resolve(modifiedRequest), staticRewrite.getPrefix());
                if (LOG.isTraceEnabled())
                    LOG.trace("Forwarding to target: " + staticRewrite.getTarget());
                staticRewrite.doRewrite(modifiedRequest, response, filterChain);
            } else {
                if (LOG.isTraceEnabled())
                    LOG.trace("Processing request URI: " + request.getRequestURI());

                if (staticRewrite != null) {
                    // fix the request URI
                    staticRewrite.updateRequest(modifiedRequest);
                }

                // check if the request URI is already in the url cache
                ModelAndView modelView;
                synchronized (urlCache) {
                    modelView = urlCache.get(modifiedRequest.getRequestURI());
                }
                // no: create a new model and view configuration
                if (modelView == null) {
                    modelView = new ModelAndView();
                    // Execute the query
                    Sequence result = Sequence.EMPTY_SEQUENCE;
                    DBBroker broker = null;
                    try {
                        broker = pool.get(user);
                        modifiedRequest.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());
                        result = runQuery(broker, modifiedRequest, response, staticRewrite);

                        logResult(broker, result);
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
                                        parseViews(modifiedRequest, action, modelView);
                                    } else if ("cache-control".equals(action.getLocalName())) {
                                        String option = action.getAttribute("cache");
                                        modelView.setUseCache("yes".equals(option));
                                    } else {
                                        URLRewrite urw = parseAction(modifiedRequest, action);
                                        if (urw != null)
                                            modelView.setModel(urw);
                                    }
                                }
                                node = node.getNextSibling();
                            }
                            if (modelView.getModel() == null)
                                modelView.setModel(new PassThrough(elem, modifiedRequest));
                        } else if ("ignore".equals(elem.getLocalName())) {
                            modelView.setModel(new PassThrough(elem, modifiedRequest));
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
                        synchronized (urlCache) {
                            urlCache.put(request.getRequestURI(), modelView);
                        }
                    }
                }
                if (LOG.isTraceEnabled())
                    LOG.trace("URLRewrite took " + (System.currentTimeMillis() - start) + "ms.");

                HttpServletResponse wrappedResponse = response;
                if (modelView.hasViews())
                    wrappedResponse = new CachingResponseWrapper(response, true);
                if (modelView.getModel() == null)
                    modelView.setModel(new PassThrough(modifiedRequest));

                if (staticRewrite != null) {
                    staticRewrite.rewriteRequest(modifiedRequest);
                }
                doRewrite(modelView.getModel(), modifiedRequest, wrappedResponse, filterChain);

                if (modelView.hasViews()) {
                    int status = ((CachingResponseWrapper) wrappedResponse).getStatus();
                    if (status == HttpServletResponse.SC_NOT_MODIFIED) {
                        response.flushBuffer();
                    } else if (status < 400) {
                        List views = modelView.views;
                        for (int i = 0; i < views.size(); i++) {
                            URLRewrite view = (URLRewrite) views.get(i);
                            RequestWrapper wrappedReq = new RequestWrapper(modifiedRequest);
                            wrappedReq.setMethod("POST");
                            wrappedReq.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
                            wrappedReq.setContentType(wrappedResponse.getContentType());
                            byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
                            if (data != null)
                                wrappedReq.setData(data);

                            if (i < views.size() - 1)
                                wrappedResponse = new CachingResponseWrapper(response, true);
                            else
                                wrappedResponse = new CachingResponseWrapper(response, false);
                            doRewrite(view, wrappedReq, wrappedResponse, null);
                            wrappedResponse.flushBuffer();

                            // catch errors in the view
                            status = ((CachingResponseWrapper) wrappedResponse).getStatus();
                            if (status >= 400) {
                                flushError(response, wrappedResponse);
                                break;
                            }
                        }
                    } else {
                        // HTTP response code indicates an error
                        flushError(response, wrappedResponse);
                    }
                }
//            Sequence result;
//            if ((result = (Sequence) request.getAttribute(RQ_ATTR_RESULT)) != null) {
//                writeResults(response, broker, result);
//            }
            }
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

    private void flushError(HttpServletResponse response, HttpServletResponse wrappedResponse) throws IOException {
        byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
        if (data != null) {
            response.setContentType(wrappedResponse.getContentType());
            response.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
            response.getOutputStream().write(data);
            response.flushBuffer();
        }
    }

    private void checkCache() throws EXistException {
        if (checkModified) {
            // check if any of the currently used sources has been updated
            // if yes, clear the cache
            synchronized (urlCache) {
                for (Source source : sources.values()) {
                    if (source instanceof DBSource) {
                        // Check if the XQuery source changed. If yes, clear all caches.
                        DBBroker broker = null;
                        try {
                            broker = pool.get(user);
                            if (source.isValid(broker) != Source.VALID) {
                                clearCaches();
                                break;
                            }
                        } finally {
                            pool.release(broker);
                        }
                    } else {
                        if (source.isValid((DBBroker)null) != Source.VALID) {
                            clearCaches();
                            break;
                        }
                    }
                }
            }
        }
    }

    /**
     * Process a rewrite action. Method checks if the target path is mapped
     * to another action in controller-config.xml. If yes, replaces the current action
     * with the new action.
     *
     * @param action
     * @param request
     * @param response
     * @param filterChain
     * @throws IOException
     * @throws ServletException
     */
    protected void doRewrite(URLRewrite action, RequestWrapper request, HttpServletResponse response,
                             FilterChain filterChain) throws IOException, ServletException {
        if (action.getTarget() != null && !(action instanceof Redirect)) {
            String uri = action.resolve(request);
            URLRewrite staticRewrite = rewriteConfig.lookup(uri, true);
            if (staticRewrite != null) {
                staticRewrite.copyFrom(action);
                action = staticRewrite;
                RequestWrapper modifiedRequest = new RequestWrapper(request);
                modifiedRequest.setPaths(uri, action.getPrefix());
                if (LOG.isTraceEnabled())
                    LOG.trace("Forwarding to target: " + action.getTarget() + " url: " + action.getURI());
                request = modifiedRequest;
            }
        }
        action.prepareRequest(request);
        action.doRewrite(request, response, filterChain);
    }

    protected FilterConfig getConfig() {
        return config;
    }
    
    protected void clearCaches() {
        synchronized (urlCache) {
            urlCache.clear();
            sources.clear();
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
                if (urw != null)
                    modelView.addView(urw);
            }
            node = node.getNextSibling();
        }
    }

    private void configure() throws ServletException {
        try {
            Class driver = Class.forName(DRIVER);
            Database database = (Database) driver.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            LOG.debug("Initialized database");
        } catch(Exception e) {
            String errorMessage="Failed to initialize database driver";
            LOG.error(errorMessage,e);
            throw new ServletException(errorMessage+": " + e.getMessage(), e);
        }

        String username = config.getInitParameter("user");
        if(username == null)
            username = DEFAULT_USER;
        String password = config.getInitParameter("password");
        if(password == null)
            password = DEFAULT_PASS;
        try {
            pool = BrokerPool.getInstance();
            org.exist.security.SecurityManager secman = pool.getSecurityManager();
            user = secman.getUser(username);
            if (!user.validate(password)) {
                throw new ServletException("Invalid password specified for XQueryURLRewrite user");
            }
        } catch (EXistException e) {
            throw new ServletException("Could not intialize db: " + e.getMessage(), e);
        }
    }

    private void logResult(DBBroker broker, Sequence result) throws IOException, SAXException {
        if (LOG.isTraceEnabled() && result.getItemCount() > 0) {
            Serializer serializer = broker.getSerializer();
            serializer.reset();

            Item item = result.itemAt(0);
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                LOG.trace(serializer.serialize((NodeValue) item));
            }
        }
    }

    public void destroy() {
        config = null;
    }

    private Sequence runQuery(DBBroker broker, RequestWrapper request, HttpServletResponse response,
                              URLRewrite staticRewrite)
        throws ServletException, XPathException {
        // Try to find the XQuery
        SourceInfo sourceInfo;
        String moduleLoadPath = config.getServletContext().getRealPath(".");
        String basePath = staticRewrite == null ? "." : staticRewrite.getTarget();
        if (basePath == null)
            sourceInfo = getSource(broker, moduleLoadPath);
        else
            sourceInfo = findSource(request, broker, basePath);

        if (sourceInfo == null)
            return Sequence.EMPTY_SEQUENCE; // no controller found

        synchronized (urlCache) {
            sources.put(sourceInfo.source.getKey(), sourceInfo.source);
        }

        XQuery xquery = broker.getXQueryService();
		XQueryPool pool = xquery.getXQueryPool();
		CompiledXQuery compiled = pool.borrowCompiledXQuery(broker, sourceInfo.source);
        XQueryContext queryContext;
        if (compiled == null) {
			queryContext = xquery.newContext(AccessContext.REST);
		} else {
			queryContext = compiled.getContext();
		}
        // Find correct module load path
		queryContext.setModuleLoadPath(moduleLoadPath);
        declareVariables(queryContext, sourceInfo, staticRewrite.getPrefix(), basePath, request, response);
        if (compiled == null) {
			try {
				compiled = xquery.compile(queryContext, sourceInfo.source);
			} catch (IOException e) {
				throw new ServletException("Failed to read query from " + query, e);
			}
		}
        
//		This used by controller.xql only ?
//		String xdebug = request.getParameter("XDEBUG_SESSION_START");
//		if (xdebug != null)
//			compiled.getContext().setDebugMode(true);

        try {
			return xquery.execute(compiled, null);
		} finally {
			pool.returnCompiledXQuery(sourceInfo.source, compiled);
		}
    }

    private SourceInfo findSource(HttpServletRequest request, DBBroker broker, String basePath) throws ServletException {
        String requestURI = request.getRequestURI();
        String path = requestURI.substring(request.getContextPath().length());
        if (path.startsWith("/"))
            path = path.substring(1);
        String[] components = path.split("/");
        SourceInfo sourceInfo = null;
        if (basePath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor(basePath);
                Collection collection = broker.openCollection(locationUri, Lock.READ_LOCK);
                if (collection == null) {
                    LOG.warn("Controller base collection not found: " + basePath);
                    return null;
                }

                Collection subColl = collection;
                DocumentImpl controllerDoc = null;
                for (int i = 0; i < components.length; i++) {
                    if (components[i].length() > 0 && subColl.hasChildCollection(XmldbURI.createInternal(components[i]))) {
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
                    } else {
                        break;
                    }
                }
                collection.getLock().release(Lock.READ_LOCK);
                if (controllerDoc == null) {
                    try {
                        XmldbURI docUri = collection.getURI().append("controller.xql");
                        controllerDoc = broker.getXMLResource(docUri, Lock.READ_LOCK);
                    } catch (PermissionDeniedException e) {
                        LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " +
                            e.getMessage(), e);
                    }
                }
                if (controllerDoc == null) {
                    LOG.warn("XQueryURLRewrite controller could not be found");
                    return null;
                }
                if (controllerDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !controllerDoc.getMetadata().getMimeType().equals("application/xquery")) {
                    LOG.warn("XQuery resource: " + query + " is not an XQuery or " +
                            "declares a wrong mime-type");
                    return null;
                }
                sourceInfo = new SourceInfo(new DBSource(broker, (BinaryDocument) controllerDoc, true));
                String controllerPath = controllerDoc.getCollection().getURI().getRawCollectionPath();
                sourceInfo.controllerPath =
                    controllerPath.substring(locationUri.getCollectionPath().length());
                return sourceInfo;
            } catch (URISyntaxException e) {
                LOG.warn("Bad URI for base path: " + e.getMessage(), e);
                return null;
            }
        } else {
            String realPath = config.getServletContext().getRealPath(basePath);
            File baseDir = new File(realPath);
            if (!baseDir.isDirectory()) {
                LOG.warn("Base path for XQueryURLRewrite does not point to a directory");
                return null;
            }

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
            if (controllerFile == null) {
                LOG.warn("XQueryURLRewrite controller could not be found");
                return null;
            }
            if (LOG.isTraceEnabled())
                LOG.trace("Found controller file: " + controllerFile.getAbsolutePath());
            sourceInfo = new SourceInfo(new FileSource(controllerFile, "UTF-8", true));
            String parentPath = controllerFile.getParentFile().getAbsolutePath();
            sourceInfo.controllerPath =
                parentPath.substring(baseDir.getAbsolutePath().length());
            // replace windows path separators
            sourceInfo.controllerPath = sourceInfo.controllerPath.replace('\\', '/');
            return sourceInfo;
        }
    }
    
    private SourceInfo getSource(DBBroker broker, String moduleLoadPath) throws ServletException {
        SourceInfo sourceInfo;
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
                    sourceInfo = new SourceInfo(new DBSource(broker, (BinaryDocument) sourceDoc, true));
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
                sourceInfo = new SourceInfo(SourceFactory.getSource(broker, moduleLoadPath, query, true));
            } catch (IOException e) {
                throw new ServletException("IO error while reading XQuery source: " + query);
            } catch (PermissionDeniedException e) {
                throw new ServletException("Permission denied while reading XQuery source: " + query);
            }
        }
        return sourceInfo;
    }

    private void declareVariables(XQueryContext context, SourceInfo sourceInfo, String prefix, String basePath,
			RequestWrapper request, HttpServletResponse response)
			throws XPathException {
		HttpRequestWrapper reqw = new HttpRequestWrapper(request, "UTF-8", "UTF-8", false);
		HttpResponseWrapper respw = new HttpResponseWrapper(response);
		// context.declareNamespace(RequestModule.PREFIX,
		// RequestModule.NAMESPACE_URI);
		context.declareVariable(RequestModule.PREFIX + ":request", reqw);
		context.declareVariable(ResponseModule.PREFIX + ":response", respw);
		context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession( false ));

        context.declareVariable("exist:controller", sourceInfo.controllerPath);
        context.declareVariable("exist:root", basePath);
        context.declareVariable("exist:context", request.getContextPath());
        context.declareVariable("exist:prefix", prefix == null ? "" : prefix);
        String path;
        if (sourceInfo.controllerPath.length() > 0 && !sourceInfo.controllerPath.equals("/"))
            path = request.getInContextPath().substring(sourceInfo.controllerPath.length());
        else
            path = request.getInContextPath();
        int p = path.lastIndexOf(';');
        if(p != Constants.STRING_NOT_FOUND)
            path = path.substring(0, p);
        context.declareVariable("exist:path", path);

        String resource = "";
        Matcher nameMatcher = NAME_REGEX.matcher(path);
        if (nameMatcher.matches()) {
            resource = nameMatcher.group(1);
        }
        context.declareVariable("exist:resource", resource);

        if (LOG.isTraceEnabled())
            LOG.debug("\nexist:path = " + path + "\nexist:resource = " + resource + "\nexist:controller = " +
                sourceInfo.controllerPath);
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

    private static class SourceInfo {

        Source source;
        String controllerPath = "";

        private SourceInfo(Source source) {
            this.source = source;
        }
    }

    public static class RequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

        Map<String, String[]> addedParams = new HashMap<String, String[]>();

        Map attributes = new HashMap();
        
        ServletInputStream sis = null;
        BufferedReader reader = null;

        String contentType = null;
        int contentLength = 0;
        String characterEncoding = null;
        String method = null;
        String inContextPath = null;
        String servletPath;
        String basePath = null;

        private RequestWrapper(HttpServletRequest request) {
            super(request);
            // copy parameters
            for (Enumeration e = request.getParameterNames(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                String[] value = request.getParameterValues(key);
                addedParams.put(key, value);
            }

            contentType = request.getContentType();
        }

        public String getRequestURI() {
            return inContextPath == null ? super.getRequestURI() : getContextPath() + inContextPath;
        }

        public String getInContextPath() {
            if (inContextPath == null)
                return getRequestURI().substring(getContextPath().length());
            return inContextPath;
        }

        public void setInContextPath(String path) {
            inContextPath = path;
        }

        public String getMethod() {
            if (method == null)
                return super.getMethod();
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        /**
         * Change the requestURI and the servletPath
         *
         * @param requestURI the URI of the request without the context path
         * @param servletPath the servlet path
         */
        public void setPaths(String requestURI, String servletPath) {
            this.inContextPath = requestURI;
            if (servletPath == null)
                this.servletPath = requestURI;
            else
                this.servletPath = servletPath;
        }

        public void setBasePath(String base) {
            this.basePath = base;
        }

        public String getBasePath() {
            return basePath;
        }

        /**
         * Change the base path of the request, e.g. if the original request pointed
         * to /fs/foo/baz, but the request should be forwarded to /foo/baz.
         *
         * @param base the base path to remove
         */
        public void removePathPrefix(String base) {
            setPaths(getInContextPath().substring(base.length()),
                servletPath != null ? servletPath.substring(base.length()) : null);
        }

        public String getServletPath() {
            return servletPath == null ? super.getServletPath() : servletPath;
        }

        public String getPathInfo() {
            String path = getInContextPath();
            String sp = getServletPath();
            if (sp == null)
                return null;
            if (path.length() < sp.length()) {
                LOG.error("Internal error: servletPath = " + sp + " is longer than path = " + path);
                return null;
            }
            return path.length() == sp.length() ? null : path.substring(sp.length());
       }

        protected void setData(byte[] data) {
            if (data == null)
                data = new byte[0];
            contentLength = data.length;
            sis = new CachingServletInputStream(data);
        }

        public void addParameter(String name, String value) {
            addedParams.put(name, new String[] { value });
        }

        public String getParameter(String name) {
            String[] param = addedParams.get(name);
            if (param != null && param.length > 0)
                return param[0];
            return null;
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
            return addedParams.get(s);
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
        protected boolean cache;

        public CachingResponseWrapper(HttpServletResponse servletResponse, boolean cache) {
            super(servletResponse);
            this.cache = cache;
            this.origResponse = servletResponse;
        }

        public PrintWriter getWriter() throws IOException {
            if (!cache)
                return super.getWriter();
            if (sos != null)
                throw new IOException("getWriter cannnot be called after getOutputStream");
            sos = new CachingServletOutputStream();
            if (writer == null)
                writer = new PrintWriter(new OutputStreamWriter(sos, getCharacterEncoding()));
            return writer;
        }

        public ServletOutputStream getOutputStream() throws IOException {
            if (!cache)
                return super.getOutputStream();
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
            if (!cache)
                super.setContentLength(i);
        }

        public void flushBuffer() throws IOException {
            if (!cache)
                super.flushBuffer();
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
