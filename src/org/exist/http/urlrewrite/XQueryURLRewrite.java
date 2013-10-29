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

import org.apache.log4j.Logger;

import org.exist.security.internal.web.HttpAccount;
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
import org.exist.security.*;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.Serializer;
import org.exist.util.MimeType;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.Descriptor;

import org.apache.commons.io.output.ByteArrayOutputStream;

import org.w3c.dom.Node;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;
import org.xmldb.api.base.Database;
import org.xmldb.api.DatabaseManager;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.xml.transform.OutputKeys;

import java.net.URISyntaxException;
import java.util.*;
import java.util.Map.Entry;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * A servlet to redirect HTTP requests. Similar to the popular UrlRewriteFilter, but
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
public class XQueryURLRewrite extends HttpServlet {

    private static final Logger LOG = Logger.getLogger(XQueryURLRewrite.class);

    public final static String RQ_ATTR = "org.exist.forward";
    public final static String RQ_ATTR_REQUEST_URI = "org.exist.forward.request-uri";
    public final static String RQ_ATTR_SERVLET_PATH = "org.exist.forward.servlet-path";
    public final static String RQ_ATTR_RESULT = "org.exist.forward.result";
    public final static String RQ_ATTR_ERROR = "org.exist.forward.error";
    
    
    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private final static Pattern NAME_REGEX = Pattern.compile("^.*/([^/]+)$", 0);

    private ServletConfig config;

    private final Map<String, ModelAndView> urlCache =
            Collections.synchronizedMap(new TreeMap<String, ModelAndView>());

    protected Subject defaultUser = null;
    protected BrokerPool pool;

    // path to the query
    private String query = null;
    
    //private boolean checkModified = true;

    private boolean compiledCache = true;

    private RewriteConfig rewriteConfig;
    
    @Override
    public void init(ServletConfig filterConfig) throws ServletException {
        // save FilterConfig for later use
        this.config = filterConfig;

        query = filterConfig.getInitParameter("xquery");
        
//        String opt = filterConfig.getInitParameter("check-modified");
//        if (opt != null)
//            checkModified = opt != null && opt.equalsIgnoreCase("true");
        
        final String opt = filterConfig.getInitParameter("compiled-cache");
        if (opt != null)
        	{compiledCache = opt != null && opt.equalsIgnoreCase("true");}
        
    }

    @Override
    protected void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws IOException, ServletException {
        if (rewriteConfig == null) {
            configure();
            rewriteConfig = new RewriteConfig(this);
        }
        
        final long start = System.currentTimeMillis();
        final HttpServletRequest request = servletRequest;
        final HttpServletResponse response = servletResponse;

        if (LOG.isTraceEnabled()) {
            LOG.trace(request.getRequestURI());
        }
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if(descriptor != null && descriptor.requestsFiltered())
        {
            final String attr = (String) request.getAttribute("XQueryURLRewrite.forwarded");
            if (attr == null) {
//                request = new HttpServletRequestWrapper(request, /*formEncoding*/ "utf-8" );
                //logs the request if specified in the descriptor
                descriptor.doLogRequestInReplayLog(request);

                request.setAttribute("XQueryURLRewrite.forwarded", "true");
            }
        }

        Subject user = defaultUser;
    	
        Subject requestUser = HttpAccount.getUserFromServletRequest(request);
        if (requestUser != null)
        	{user = requestUser;}

        try {
            configure();
            //checkCache(user);

            final RequestWrapper modifiedRequest = new RequestWrapper(request);
            final URLRewrite staticRewrite = rewriteConfig.lookup(modifiedRequest);
            if (staticRewrite != null && !staticRewrite.isControllerForward()) {
                modifiedRequest.setPaths(staticRewrite.resolve(modifiedRequest), staticRewrite.getPrefix());
                
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Forwarding to target: " + staticRewrite.getTarget());
                }
                staticRewrite.doRewrite(modifiedRequest, response);
            } else {
                
            	if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing request URI: " + request.getRequestURI());
            	}
                if (staticRewrite != null) {
                    // fix the request URI
                    staticRewrite.updateRequest(modifiedRequest);
                }

                // check if the request URI is already in the url cache
                ModelAndView modelView = getFromCache(
                		request.getHeader("Host") + request.getRequestURI(),
                		user);
                
                if (LOG.isDebugEnabled()) {
                	LOG.debug(
            			"Checked cache for URI: " + modifiedRequest.getRequestURI() + 
            			" original: " + request.getRequestURI()
        			);
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
                        
                        final Properties outputProperties = new Properties();
                        
                		outputProperties.setProperty(OutputKeys.INDENT, "yes");
                		outputProperties.setProperty(OutputKeys.ENCODING, "UTF-8");
                		outputProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
                        
                        result = runQuery(broker, modifiedRequest, response, modelView, staticRewrite, outputProperties);

                        logResult(broker, result);
                        
                        if (response.isCommitted()) {
                        	return;
                        }

	                    // process the query result
	                    if (result.getItemCount() == 1) {
	                        final Item resource = result.itemAt(0);
	                        if (!Type.subTypeOf(resource.getType(), Type.NODE))
	                            {throw new ServletException("XQueryURLRewrite: urlrewrite query should return an element!");}
	                        Node node = ((NodeValue) resource).getNode();
	                        if (node.getNodeType() == Node.DOCUMENT_NODE)
	                            {node = ((Document) node).getDocumentElement();}
	                        if (node.getNodeType() != Node.ELEMENT_NODE) {
	                            //throw new ServletException("Redirect XQuery should return an XML element!");
	                        	response(broker, response, outputProperties, result);
	                        	return;
	                        }
	                        Element elem = (Element) node;
	                        if (!(Namespaces.EXIST_NS.equals(elem.getNamespaceURI()))) {
	                        	response(broker, response, outputProperties, result);
	                        	return;
	//                            throw new ServletException("Redirect XQuery should return an element in namespace " + Namespaces.EXIST_NS);
	                        }
	
	                        if (Namespaces.EXIST_NS.equals(elem.getNamespaceURI()) && "dispatch".equals(elem.getLocalName())) {
	                            node = elem.getFirstChild();
	                            while (node != null) {
	                                if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
	                                    final Element action = (Element) node;
	                                    if ("view".equals(action.getLocalName())) {
	                                        parseViews(modifiedRequest, action, modelView);
	                                    } else if ("error-handler".equals(action.getLocalName())) {
	                                    	parseErrorHandlers(modifiedRequest, action, modelView);
	                                    } else if ("cache-control".equals(action.getLocalName())) {
	                                        final String option = action.getAttribute("cache");
	                                        modelView.setUseCache("yes".equals(option));
	                                    } else {
	                                        final URLRewrite urw = parseAction(modifiedRequest, action);
	                                        if (urw != null)
	                                            {modelView.setModel(urw);}
	                                    }
	                                }
	                                node = node.getNextSibling();
	                            }
	                            if (modelView.getModel() == null)
	                                {modelView.setModel(new PassThrough(config, elem, modifiedRequest));}
	                        } else if (Namespaces.EXIST_NS.equals(elem.getNamespaceURI()) && "ignore".equals(elem.getLocalName())) {
	                            modelView.setModel(new PassThrough(config, elem, modifiedRequest));
	                            final NodeList nl = elem.getElementsByTagNameNS(Namespaces.EXIST_NS, "cache-control");
	                            if (nl.getLength() > 0) {
	                                elem = (Element) nl.item(0);
	                                final String option = elem.getAttribute("cache");
	                                modelView.setUseCache("yes".equals(option));
	                            }
	                        } else {
	                        	response(broker, response, outputProperties, result);
	                        	return;
	                        }
	                    } else if (result.getItemCount() > 1) {
                            response(broker, response, outputProperties, result);
                        	return;
	                    }
	                    
	                    if (modelView.useCache()) {
                            LOG.debug("Caching request to " + request.getRequestURI());
                            urlCache.put(modifiedRequest.getHeader("Host") + request.getRequestURI(), modelView);
                        }

                    } finally {
                        pool.release(broker);
                    }

                    // store the original request URI to org.exist.forward.request-uri
                    modifiedRequest.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());
                    modifiedRequest.setAttribute(RQ_ATTR_SERVLET_PATH, request.getServletPath());

                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URLRewrite took " + (System.currentTimeMillis() - start) + "ms.");
                }
            	final HttpServletResponse wrappedResponse = 
            		new CachingResponseWrapper(response, modelView.hasViews() || modelView.hasErrorHandlers());
                if (modelView.getModel() == null)
                    {modelView.setModel(new PassThrough(config, modifiedRequest));}

                if (staticRewrite != null) {
                	if (modelView.getModel().doResolve())
                		{staticRewrite.rewriteRequest(modifiedRequest);}
                	else
                		{modelView.getModel().setAbsolutePath(modifiedRequest);}
                }
                modifiedRequest.allowCaching(!modelView.hasViews());
                doRewrite(modelView.getModel(), modifiedRequest, wrappedResponse);

                int status = ((CachingResponseWrapper) wrappedResponse).getStatus();
                if (status == HttpServletResponse.SC_NOT_MODIFIED) {
                	response.flushBuffer();
                } else if (status < 400) {
                	if (modelView.hasViews())
                		{applyViews(modelView, modelView.views, response, modifiedRequest, wrappedResponse);}
                	else
                		{((CachingResponseWrapper) wrappedResponse).flush();}
                } else {
                	// HTTP response code indicates an error
                	if (modelView.hasErrorHandlers()) {
                        final byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
                        if (data != null)
                            {modifiedRequest.setAttribute(RQ_ATTR_ERROR, new String(data, "UTF-8"));}
                		applyViews(modelView, modelView.errorHandlers, response, modifiedRequest, wrappedResponse);
                	} else {
                		flushError(response, wrappedResponse);
                	}
                }
            }
//            Sequence result;
//            if ((result = (Sequence) request.getAttribute(RQ_ATTR_RESULT)) != null) {
//                writeResults(response, broker, result);
//            }
        } catch (final EXistException e) {
            LOG.error("Error while processing " + servletRequest.getRequestURI() + ": " + e.getMessage(), e);
            throw new ServletException("An error occurred while processing request to " + servletRequest.getRequestURI() + ": "
                    + e.getMessage(), e);

        } catch (final XPathException e) {
            LOG.error("Error while processing " + servletRequest.getRequestURI() + ": " + e.getMessage(), e);
            throw new ServletException("An error occurred while processing request to " + servletRequest.getRequestURI() + ": "
                    + e.getMessage(), e);

//        } catch (SAXException e) {
//            throw new ServletException("Error while serializing results: " + e.getMessage(), e);
            
        } catch (final Throwable e) {
            LOG.error("Error while processing " + servletRequest.getRequestURI() + ": " + e.getMessage(), e);
            throw new ServletException("An error occurred while processing request to " + servletRequest.getRequestURI() + ": "
                    + e.getMessage(), e);
        }
    }
    
	private void applyViews(ModelAndView modelView, List<URLRewrite> views, HttpServletResponse response, RequestWrapper modifiedRequest,
			HttpServletResponse currentResponse)
			throws UnsupportedEncodingException, IOException, ServletException {
		int status;
        HttpServletResponse wrappedResponse = currentResponse;
		for (int i = 0; i < views.size(); i++) {
			final URLRewrite view = (URLRewrite) views.get(i);
			
			// get data returned from last action
			byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
			// determine request method to use for calling view
			String method = view.getMethod();
			if (method == null) {
				method = "POST";	// default is POST
			}
			
			final RequestWrapper wrappedReq = new RequestWrapper(modifiedRequest);
			wrappedReq.allowCaching(false);
			wrappedReq.setMethod(method);
			wrappedReq.setBasePath(modifiedRequest.getBasePath());
			wrappedReq.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
			wrappedReq.setContentType(wrappedResponse.getContentType());

			if (data != null)
				{wrappedReq.setData(data);}
			
			wrappedResponse = new CachingResponseWrapper(response, true);
			doRewrite(view, wrappedReq, wrappedResponse);

			// catch errors in the view
			status = ((CachingResponseWrapper) wrappedResponse).getStatus();
            if (status >= 400) {
                if (modelView != null && modelView.hasErrorHandlers()) {
                    data = ((CachingResponseWrapper) wrappedResponse).getData();
                    final String msg = data == null ? "" : new String(data, "UTF-8");
                    modifiedRequest.setAttribute(RQ_ATTR_ERROR, msg);
                    applyViews(null, modelView.errorHandlers, response, modifiedRequest, wrappedResponse);
                    break;
                } else {
                    flushError(response, wrappedResponse);
                }
				break;
			} else if (i == views.size() - 1)
                {((CachingResponseWrapper)wrappedResponse).flush();}
		}
	}

    private void response(DBBroker broker, HttpServletResponse response, Properties outputProperties, Sequence resultSequence) throws IOException {

    	final String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        final ServletOutputStream sout = response.getOutputStream();
        final PrintWriter output = new PrintWriter(new OutputStreamWriter(sout, encoding));
		if (!response.containsHeader("Content-Type")){
			String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
			if (mimeType != null) {
				final int semicolon = mimeType.indexOf(';');
				if (semicolon != Constants.STRING_NOT_FOUND) {
					mimeType = mimeType.substring(0, semicolon);
				}
				response.setContentType(mimeType + "; charset=" + encoding);
			}
		}
        
//        response.addHeader( "pragma", "no-cache" );
//        response.addHeader( "Cache-Control", "no-cache" );

        final Serializer serializer = broker.getSerializer();
    	serializer.reset();
    
    	final SerializerPool serializerPool = SerializerPool.getInstance();

    	final SAXSerializer sax = (SAXSerializer) serializerPool.borrowObject(SAXSerializer.class);
    	try {
    		sax.setOutput(output, outputProperties);

	    	serializer.setProperties(outputProperties);
	    	serializer.setSAXHandlers(sax, sax);
        	serializer.toSAX(resultSequence, 1, resultSequence.getItemCount(), false, false);
        	
    	} catch (final SAXException e) {
    		throw new IOException(e);
    	} finally {
    		serializerPool.returnObject(sax);
    	}
    	output.flush();
    	output.close();
    }

	private void flushError(HttpServletResponse response, HttpServletResponse wrappedResponse) throws IOException {
        if (!response.isCommitted()) {
            final byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
            if (data != null) {
                response.setContentType(wrappedResponse.getContentType());
                response.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
                response.getOutputStream().write(data);
                response.flushBuffer();
            }
        }
    }

	private ModelAndView getFromCache(String url, Subject user) throws EXistException, ServletException, PermissionDeniedException {
		/* Make sure we have a broker *before* we synchronize on urlCache or we may run
		 * into a deadlock situation (with method checkCache)
		 */
		final ModelAndView model = urlCache.get(url);
		if (model == null)
			{return null;}

		DBBroker broker = null;
		try {
			broker = pool.get(user);

			model.getSourceInfo().source.validate(broker.getSubject(), Permission.EXECUTE);
			
			if (model.getSourceInfo().source.isValid(broker) != Source.VALID) {
                ModelAndView removed = urlCache.remove(url);
				return null;
			}
			
			if (LOG.isDebugEnabled()) {
				LOG.debug("Using cached entry for " + url);
			}
			return model;
		} finally {
			pool.release(broker);
		}
	}
	
//    private void checkCache(Subject user) throws EXistException {
//        if (checkModified) {
//            // check if any of the currently used sources has been updated
//        	DBBroker broker = null;
//            try {
//                broker = pool.get(user);
//
//                for (Entry<ModelAndView, Source> entry : sources.entrySet() )
//        			if (entry.getValue().isValid(broker) != Source.VALID)
//        				urlCache.remove(entry.getKey());
//
//            } finally {
//                pool.release(broker);
//            }
//        }
//    }

    protected void clearCaches() throws EXistException {
		urlCache.clear();
    }
    
    /**
     * Process a rewrite action. Method checks if the target path is mapped
     * to another action in controller-config.xml. If yes, replaces the current action
     * with the new action.
     *
     * @param action
     * @param request
     * @param response
     * @throws IOException
     * @throws ServletException
     */
    protected void doRewrite(URLRewrite action, RequestWrapper request, HttpServletResponse response) throws IOException, ServletException {
        if (action.getTarget() != null && !(action instanceof Redirect)) {
            final String uri = action.resolve(request);
            URLRewrite staticRewrite = rewriteConfig.lookup(uri, request.getServerName(), true);

            if (staticRewrite != null) {
                staticRewrite.copyFrom(action);
                action = staticRewrite;
                RequestWrapper modifiedRequest = new RequestWrapper(request);
                modifiedRequest.setPaths(uri, action.getPrefix());
                
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Forwarding to : " + action.toString() + " url: " + action.getURI());
                }
                request = modifiedRequest;
            }
        }
        action.prepareRequest(request);
        action.doRewrite(request, response);
    }

    protected ServletConfig getConfig() {
        return config;
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
                final URLRewrite urw = parseAction(request, (Element) node);
                if (urw != null)
                    {modelView.addView(urw);}
            }
            node = node.getNextSibling();
        }
    }

    private void parseErrorHandlers(HttpServletRequest request, Element view, ModelAndView modelView) throws ServletException {
        Node node = view.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(node.getNamespaceURI())) {
                final URLRewrite urw = parseAction(request, (Element) node);
                if (urw != null)
                    {modelView.addErrorHandler(urw);}
            }
            node = node.getNextSibling();
        }
    }
    
    private void configure() throws ServletException {
    	if (pool != null)
    		{return;}
        try {
            final Class<?> driver = Class.forName(DRIVER);
            final Database database = (Database) driver.newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            LOG.debug("Initialized database");
        } catch(final Exception e) {
            final String errorMessage="Failed to initialize database driver";
            LOG.error(errorMessage,e);
            throw new ServletException(errorMessage+": " + e.getMessage(), e);
        }

		try {
			pool = BrokerPool.getInstance();
		} catch (final EXistException e) {
            throw new ServletException("Could not intialize db: " + e.getMessage(), e);
		}
        
		defaultUser = pool.getSecurityManager().getGuestSubject();
		
		final String username = config.getInitParameter("user");
		if(username != null) {
			final String password = config.getInitParameter("password");
			try {
				Subject user = pool.getSecurityManager().authenticate(username, password);
	        	if (user != null && user.isAuthenticated())
	        		{defaultUser = user;}
			} catch (final AuthenticationException e) {
				LOG.error("User can not be authenticated ("+username+"), using default user.");
			}
		}
    }

    private void logResult(DBBroker broker, Sequence result) throws IOException, SAXException {
        if (LOG.isTraceEnabled() && result.getItemCount() > 0) {
            final Serializer serializer = broker.getSerializer();
            serializer.reset();

            final Item item = result.itemAt(0);
            if (Type.subTypeOf(item.getType(), Type.NODE)) {
                LOG.trace(serializer.serialize((NodeValue) item));
            }
        }
    }

    @Override
    public void destroy() {
        config = null;
    }
    
    private SourceInfo getSourceInfo(DBBroker broker, RequestWrapper request, URLRewrite staticRewrite) throws ServletException {
        final String moduleLoadPath = config.getServletContext().getRealPath(".");
        final String basePath = staticRewrite == null ? "." : staticRewrite.getTarget();
        if (basePath == null) {
            return getSource(broker, moduleLoadPath);
        } else {
            return findSource(request, broker, basePath);
        }
    }

    private Sequence runQuery(DBBroker broker, RequestWrapper request, HttpServletResponse response,
							  ModelAndView model,
                              URLRewrite staticRewrite, Properties outputProperties)
        throws ServletException, XPathException, PermissionDeniedException {
        // Try to find the XQuery
        final SourceInfo sourceInfo = getSourceInfo(broker, request, staticRewrite);

        if (sourceInfo == null) {
            return Sequence.EMPTY_SEQUENCE; // no controller found
        }
        final String basePath = staticRewrite == null ? "." : staticRewrite.getTarget();
        
        final XQuery xquery = broker.getXQueryService();
        final XQueryPool xqyPool = xquery.getXQueryPool();
		
        CompiledXQuery compiled = null;
        if (compiledCache) {
			compiled = xqyPool.borrowCompiledXQuery(broker, sourceInfo.source);
        }
        XQueryContext queryContext;
        if (compiled == null) {
			queryContext = xquery.newContext(AccessContext.REST);
		} else {
			queryContext = compiled.getContext();
		}
        // Find correct module load path
		queryContext.setModuleLoadPath(sourceInfo.moduleLoadPath);
        declareVariables(queryContext, sourceInfo, staticRewrite, basePath, request, response);
        if (compiled == null) {
			try {
				compiled = xquery.compile(queryContext, sourceInfo.source);
			} catch (final IOException e) {
				throw new ServletException("Failed to read query from " + query, e);
			}
		}
        model.setSourceInfo(sourceInfo);
        
//		This used by controller.xql only ?
//		String xdebug = request.getParameter("XDEBUG_SESSION_START");
//		if (xdebug != null)
//			compiled.getContext().setDebugMode(true);

//      outputProperties.put("base-uri", collectionURI.toString());

        try {
			return xquery.execute(compiled, null, outputProperties);
		} finally {
            queryContext.runCleanupTasks();
			xqyPool.returnCompiledXQuery(sourceInfo.source, compiled);
		}
    }

    protected String adjustPathForSourceLookup(String basePath, String path) {
    	if (LOG.isTraceEnabled()) {
    		LOG.trace("request path=" + path);
    	}
    	if(basePath.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX) && path.startsWith(basePath.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, ""))) {
            path = path.replace(basePath.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, ""), "");
        
    	} else if(path.startsWith("/db/")) {
            path = path.substring(4);
        }

        if(path.startsWith("/")) {
             path = path.substring(1);
        }

        if (LOG.isTraceEnabled()) {
        	LOG.trace("adjusted request path=" + path);
        }
        return path;
    }

    private SourceInfo findSource(HttpServletRequest request, DBBroker broker, String basePath) throws ServletException {
        final String requestURI = request.getRequestURI();
        String path = requestURI.substring(request.getContextPath().length());
        
        if (LOG.isTraceEnabled()) {
        	LOG.trace("basePath=" + basePath);
        }
        path = adjustPathForSourceLookup(basePath, path);

        final String[] components = path.split("/");
        SourceInfo sourceInfo = null;
        if (basePath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
        	if (LOG.isTraceEnabled()) {
        		LOG.trace("Looking for controller.xql in the database, starting from: " + basePath);
        	}
            try {
                final XmldbURI locationUri = XmldbURI.xmldbUriFor(basePath);
                final Collection collection = broker.openCollection(locationUri, Lock.READ_LOCK);
                if (collection == null) {
                    LOG.warn("Controller base collection not found: " + basePath);
                    return null;
                }

                Collection subColl = collection;
                DocumentImpl controllerDoc = null;
                for (int i = 0; i < components.length; i++) {
                    DocumentImpl doc = null;
                    try {
                        if (components[i].length() > 0 && subColl.hasChildCollection(broker, XmldbURI.createInternal(components[i]))) {
                            final XmldbURI newSubCollURI = subColl.getURI().append(components[i]);
                            if (LOG.isTraceEnabled()) {
                            	LOG.trace("Inspecting sub-collection: " + newSubCollURI);
                            }
                            subColl = broker.openCollection(newSubCollURI, Lock.READ_LOCK);
                            if (subColl != null) {
                                if (LOG.isTraceEnabled()) {
                                	LOG.trace("Looking for controller.xql in " + subColl.getURI());
                                }
                                final XmldbURI docUri = subColl.getURI().append("controller.xql");
                                doc = broker.getXMLResource(docUri, Lock.READ_LOCK);
                                if (doc != null) {
                                	if (controllerDoc != null) {
                                		controllerDoc.getUpdateLock().release(Lock.READ_LOCK);
                                	}
                                    controllerDoc = doc;
                                }
                            } else {
                                break;
                            }
                        } else {
                            break;
                        }
                    } catch (final PermissionDeniedException e) {
                        LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " +
                            e.getMessage(), e);
                        break;
                    } catch (final Exception e) {
                        LOG.debug("Bad collection URI: " + path);
                        break;
                    
                    } finally {
                        if (doc != null && controllerDoc == null) {
                            doc.getUpdateLock().release(Lock.READ_LOCK);
                        }
                        
                        if (subColl != null && subColl != collection) {
                            subColl.getLock().release(Lock.READ_LOCK);
                        }
                    }
                }
                collection.getLock().release(Lock.READ_LOCK);
                if (controllerDoc == null) {
                    try {
                        final XmldbURI docUri = collection.getURI().append("controller.xql");
                        controllerDoc = broker.getXMLResource(docUri, Lock.READ_LOCK);
                    } catch (final PermissionDeniedException e) {
                        LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " +
                            e.getMessage(), e);
                    }
                }
                if (controllerDoc == null) {
                    LOG.warn("XQueryURLRewrite controller could not be found for path: " + path);
                    return null;
                }

                if(LOG.isTraceEnabled()) {
                    LOG.trace("Found controller file: " + controllerDoc.getURI());
                }
                try {
                    if (controllerDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                                !"application/xquery".equals(controllerDoc.getMetadata().getMimeType())) {
                        LOG.warn("XQuery resource: " + query + " is not an XQuery or " +
                                "declares a wrong mime-type");
                        return null;
                    }
                    final String controllerPath = controllerDoc.getCollection().getURI().getRawCollectionPath();
                    
                    sourceInfo = new SourceInfo(new DBSource(broker, (BinaryDocument) controllerDoc, true), "xmldb:exist://" + controllerPath);
                    sourceInfo.controllerPath = controllerPath.substring(locationUri.getCollectionPath().length());

                    return sourceInfo;
                } finally {
                    if (controllerDoc != null) {
                        controllerDoc.getUpdateLock().release(Lock.READ_LOCK);
                    }
                }
            } catch (final URISyntaxException e) {
                LOG.warn("Bad URI for base path: " + e.getMessage(), e);
                return null;
            } catch (final PermissionDeniedException e) {
                LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: " + e.getMessage(), e);
              return null;
            }
        } else {
            if (LOG.isTraceEnabled()) {
            	LOG.trace("Looking for controller.xql in the filesystem, starting from: " + basePath);
            }
            final String realPath = config.getServletContext().getRealPath(basePath);
            final File baseDir = new File(realPath);
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
                            {controllerFile = cf;}
                    } else {
                        break;
                    }
                }
            }
            if (controllerFile == null) {
                File cf = new File(baseDir, "controller.xql");
                if (cf.canRead())
                    {controllerFile = cf;}
            }
            if (controllerFile == null) {
                LOG.warn("XQueryURLRewrite controller could not be found");
                return null;
            }
            if (LOG.isTraceEnabled()) {
                LOG.trace("Found controller file: " + controllerFile.getAbsolutePath());
            }
            final String parentPath = controllerFile.getParentFile().getAbsolutePath();
            
            sourceInfo = new SourceInfo(new FileSource(controllerFile, "UTF-8", true), parentPath);
            sourceInfo.controllerPath = parentPath.substring(baseDir.getAbsolutePath().length());
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
                final XmldbURI locationUri = XmldbURI.xmldbUriFor(query);
                DocumentImpl sourceDoc = null;
                try {
                    sourceDoc = broker.getXMLResource(locationUri.toCollectionPathURI(), Lock.READ_LOCK);
                    if (sourceDoc == null)
                        {throw new ServletException("XQuery resource: " + query + " not found in database");}
                    if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !"application/xquery".equals(sourceDoc.getMetadata().getMimeType()))
                        {throw new ServletException("XQuery resource: " + query + " is not an XQuery or " +
                                "declares a wrong mime-type");}
                    sourceInfo = new SourceInfo(new DBSource(broker, (BinaryDocument) sourceDoc, true),
                        locationUri.toString());
                } catch (final PermissionDeniedException e) {
                    throw new ServletException("permission denied to read module source from " + query);
                } finally {
                    if(sourceDoc != null)
                        {sourceDoc.getUpdateLock().release(Lock.READ_LOCK);}
                }
            } catch(final URISyntaxException e) {
                throw new ServletException(e.getMessage(), e);
            }
        } else {
            try {
                sourceInfo = new SourceInfo(SourceFactory.getSource(broker, moduleLoadPath, query, true), moduleLoadPath);
            } catch (final IOException e) {
                throw new ServletException("IO error while reading XQuery source: " + query);
            } catch (final PermissionDeniedException e) {
                throw new ServletException("Permission denied while reading XQuery source: " + query);
            }
        }
        return sourceInfo;
    }

    private void declareVariables(XQueryContext context, SourceInfo sourceInfo, URLRewrite staticRewrite, String basePath,
			RequestWrapper request, HttpServletResponse response)
			throws XPathException {
		final HttpRequestWrapper reqw = new HttpRequestWrapper(request, "UTF-8", "UTF-8", false);
		final HttpResponseWrapper respw = new HttpResponseWrapper(response);
		// context.declareNamespace(RequestModule.PREFIX,
		// RequestModule.NAMESPACE_URI);
		context.declareVariable(RequestModule.PREFIX + ":request", reqw);
		context.declareVariable(ResponseModule.PREFIX + ":response", respw);
		context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession( false ));

        context.declareVariable("exist:controller", sourceInfo.controllerPath);
        request.setAttribute("$exist:controller", sourceInfo.controllerPath);
        context.declareVariable("exist:root", basePath);
        request.setAttribute("$exist:root", basePath);
        context.declareVariable("exist:context", request.getContextPath());
        request.setAttribute("$exist:context", request.getContextPath());
        final String prefix = staticRewrite == null ? null : staticRewrite.getPrefix();
        context.declareVariable("exist:prefix", prefix == null ? "" : prefix);
        request.setAttribute("$exist:prefix", prefix == null ? "" : prefix);
        String path;
        if (sourceInfo.controllerPath.length() > 0 && !"/".equals(sourceInfo.controllerPath))
            {path = request.getInContextPath().substring(sourceInfo.controllerPath.length());}
        else
            {path = request.getInContextPath();}
        final int p = path.lastIndexOf(';');
        if(p != Constants.STRING_NOT_FOUND)
            {path = path.substring(0, p);}
        context.declareVariable("exist:path", path);
        request.setAttribute("$exist:path", path);

        String resource = "";
        final Matcher nameMatcher = NAME_REGEX.matcher(path);
        if (nameMatcher.matches()) {
            resource = nameMatcher.group(1);
        }
        context.declareVariable("exist:resource", resource);
        request.setAttribute("$exist:resource", resource);

        if (LOG.isDebugEnabled()) {
            LOG.debug("\nexist:path = " + path + "\nexist:resource = " + resource + "\nexist:controller = " + sourceInfo.controllerPath);
        }
	}

    private class ModelAndView {

        URLRewrite rewrite = null;
        List<URLRewrite> views = new LinkedList<URLRewrite>();
        List<URLRewrite> errorHandlers = null;
        boolean useCache = false;
        SourceInfo sourceInfo = null;

        private ModelAndView() {
        }

        public void setSourceInfo(SourceInfo sourceInfo) {
            this.sourceInfo = sourceInfo;
        }

        public SourceInfo getSourceInfo() {
            return sourceInfo;
        }

        public void setModel(URLRewrite model) {
            this.rewrite = model;
        }

        public URLRewrite getModel() {
            return rewrite;
        }

        public void addErrorHandler(URLRewrite handler) {
        	if (errorHandlers == null)
        		{errorHandlers = new LinkedList<URLRewrite>();}
        	errorHandlers.add(handler);
        }
        
        public void addView(URLRewrite view) {
            views.add(view);
        }

        public boolean hasViews() {
            return views.size() > 0;
        }

        public boolean hasErrorHandlers() {
        	return errorHandlers != null && errorHandlers.size() > 0;
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
        String moduleLoadPath;

        private SourceInfo(Source source, String moduleLoadPath) {
            this.source = source;
            this.moduleLoadPath = moduleLoadPath;
        }
    }

    public static class RequestWrapper extends javax.servlet.http.HttpServletRequestWrapper {

        Map<String, List<String>> addedParams = new HashMap<String, List<String>>();

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
        boolean allowCaching = true;

        private void addNameValue(String name, String value, Map<String, List<String>> map) {
            List<String> values = map.get(name);
            if(values == null) {
                values = new ArrayList<String>();
            }
            values.add(value);
            map.put(name, values);
        }

        protected RequestWrapper(HttpServletRequest request) {
            super(request);

            // copy parameters
            for(final Map.Entry<String, String[]> param : (Set<Map.Entry<String, String[]>>)request.getParameterMap().entrySet()) {
                for(final String paramValue : param.getValue()) {
                    addNameValue(param.getKey(), paramValue, addedParams);
                }
            }
            /*for(Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {

                String key = e.nextElement();
                String[] value = request.getParameterValues(key);
                addedParams.put(key, value);
            }*/

            contentType = request.getContentType();
        }

        protected void allowCaching(boolean cache) {
        	this.allowCaching = cache;
        }
        
        @Override
        public String getRequestURI() {
            String uri = inContextPath == null ? super.getRequestURI() : getContextPath() + inContextPath;
            
            // Strip jsessionid from uris. New behavior of jetty
            // see jira.codehaus.org/browse/JETTY-1146
            final int pos = uri.indexOf(";jsessionid=");
            if(pos>0)
                {uri=uri.substring(0, pos);}
            
            return uri;
        }

        public String getInContextPath() {
            if (inContextPath == null)
                {return getRequestURI().substring(getContextPath().length());}
            return inContextPath;
        }

        public void setInContextPath(String path) {
            inContextPath = path;
        }

        @Override
        public String getMethod() {
            if (method == null)
                {return super.getMethod();}
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
                {this.servletPath = requestURI;}
            else
                {this.servletPath = servletPath;}
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

        @Override
        public String getServletPath() {
            return servletPath == null ? super.getServletPath() : servletPath;
        }

        @Override
        public String getPathInfo() {
            final String path = getInContextPath();
            final String sp = getServletPath();
            if (sp == null)
                {return null;}
            if (path.length() < sp.length()) {
                LOG.error("Internal error: servletPath = " + sp + " is longer than path = " + path);
                return null;
            }
            return path.length() == sp.length() ? null : path.substring(sp.length());
       }

        @Override
        public String getPathTranslated() {
			final String pathInfo = getPathInfo();
			if (pathInfo == null) {
				super.getPathTranslated();
			}
			if( pathInfo == null ) {
				return( null );
			}
			 return super.getSession().getServletContext().getRealPath(pathInfo);
        }
        
        protected void setData(byte[] data) {
            if (data == null)
                {data = new byte[0];}
            contentLength = data.length;
            sis = new CachingServletInputStream(data);
        }

        public void addParameter(String name, String value) {
            addNameValue(name, value, addedParams);
        }

        @Override
        public String getParameter(String name) {
            final List<String> paramValues = addedParams.get(name);
            if (paramValues != null && paramValues.size() > 0)
                {return paramValues.get(0);}
            return null;
        }

        @Override
        public Map<String, String[]> getParameterMap() {
            final Map<String, String[]> parameterMap = new HashMap<String, String[]>();
            for(final Entry<String, List<String>> param : addedParams.entrySet()) {
                final List<String> values = param.getValue();
                if(values != null) {
                    parameterMap.put(param.getKey(), values.toArray(new String[values.size()]));
                } else {
                    parameterMap.put(param.getKey(), new String[]{});
                }
            }
            return parameterMap;
        }

        @Override
        public Enumeration<String> getParameterNames() {
            return Collections.enumeration(addedParams.keySet());
        }

        @Override
        public String[] getParameterValues(String name) {
            final List<String> values = addedParams.get(name);

            if(values != null) {
                return values.toArray(new String[values.size()]);
            } else {
                return null;
            }
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (sis == null)
                {return super.getInputStream();}
            return sis;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            if (sis == null)
                {return super.getReader();}
            if (reader == null)
                {reader = new BufferedReader(new InputStreamReader(sis, getCharacterEncoding()));}
            return reader;
        }

        @Override
        public String getContentType() {
            if (contentType == null)
                {return super.getContentType();}
            return contentType;
        }

        protected void setContentType(String contentType) {
            this.contentType = contentType;
        }

        @Override
        public int getContentLength() {
            if (sis == null)
                {return super.getContentLength();}
            return contentLength;
        }

        @Override
        public void setCharacterEncoding(String encoding) throws UnsupportedEncodingException {
            this.characterEncoding = encoding;
        }

        @Override
        public String getCharacterEncoding() {
            if (characterEncoding == null)
                {return super.getCharacterEncoding();}
            return characterEncoding;
        }

        @Override
        public String getHeader(String s) {
            if ("If-Modified-Since".equals(s) && !allowCaching)
                {return null;}
            return super.getHeader(s);
        }

        @Override
        public long getDateHeader(String s) {
            if ("If-Modified-Since".equals(s) && !allowCaching)
                {return -1;}
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

        @SuppressWarnings("unused")
		protected HttpServletResponse origResponse;
        protected CachingServletOutputStream sos = null;
        protected PrintWriter writer = null;
        protected int status = HttpServletResponse.SC_OK;
        protected String contentType = null;
        protected boolean cache;

        public CachingResponseWrapper(HttpServletResponse servletResponse, boolean cache) {
            super(servletResponse);
            this.cache = cache;
            this.origResponse = servletResponse;
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (!cache)
                {return super.getWriter();}
            if (sos != null)
                {throw new IOException("getWriter cannnot be called after getOutputStream");}
            sos = new CachingServletOutputStream();
            if (writer == null)
                {writer = new PrintWriter(new OutputStreamWriter(sos, getCharacterEncoding()));}
            return writer;
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (!cache)
                {return super.getOutputStream();}
            if (writer != null)
                {throw new IOException("getOutputStream cannnot be called after getWriter");}
            if (sos == null)
                {sos = new CachingServletOutputStream();}
            return sos;
        }

        public byte[] getData() {
            return sos != null ? sos.getData() : null;
        }

        @Override
		public void setContentType(String type) {
        	if (contentType != null)
        		{return;}
    		this.contentType = type;
    		if (!cache)
        		{super.setContentType(type);}
		}
        
		@Override
		public String getContentType() {
			return contentType != null ? contentType : super.getContentType();
		}

		@Override
		public void setHeader(String name, String value) {
			if ("Content-Type".equals(name))
				{setContentType(value);}
			else
				{super.setHeader(name, value);}
		}
		
		public int getStatus() {
            return status;
        }
        
        @Override
        public void setStatus(int i) {
            this.status = i;
            super.setStatus(i);
        }

        @Override
        public void setStatus(int i, String msg) {
            this.status = i;
            super.setStatus(i, msg);
        }

        @Override
        public void sendError(int i, String msg) throws IOException {
            this.status = i;
            super.sendError(i, msg);
        }

        @Override
        public void sendError(int i) throws IOException {
            this.status = i;
            super.sendError(i);
        }

        @Override
        public void setContentLength(int i) {
            if (!cache)
                {super.setContentLength(i);}
        }

		@Override
        public void flushBuffer() throws IOException {
            if (!cache)
                {super.flushBuffer();} 
        }
        
        public void flush() throws IOException {
        	if (cache) {
        		if (contentType != null)
        			{super.setContentType(contentType);}
        	}
        	if (sos != null) {
            	final ServletOutputStream out = super.getOutputStream();
            	out.write(sos.getData());
            	out.flush();
            }
        }
    }

    private class CachingServletOutputStream extends ServletOutputStream {

        protected ByteArrayOutputStream ostream = new ByteArrayOutputStream(512);

        protected byte[] getData() {
            return ostream.toByteArray();
        }

        @Override
        public void write(int b) throws IOException {
            ostream.write(b);
        }

        @Override
        public void write(byte b[]) throws IOException {
            ostream.write(b);
        }

        @Override
        public void write(byte b[], int off, int len) throws IOException {
            ostream.write(b, off, len);
        }
    }

    private static class CachingServletInputStream extends ServletInputStream {

        protected ByteArrayInputStream istream;

        public CachingServletInputStream(byte[] data) {
            if (data == null)
                {istream = new ByteArrayInputStream(new byte[0]);}
            else
                {istream = new ByteArrayInputStream(data);}
        }
        
        @Override
        public int read() throws IOException {
           return istream.read();
        }

        @Override
        public int read(byte b[]) throws IOException {
            return istream.read(b);
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            return istream.read(b, off, len);
        }

        @Override
        public int available() throws IOException {
            return istream.available(); 
        }
    }
}
