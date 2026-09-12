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
package org.exist.http.urlrewrite;

import jakarta.servlet.annotation.MultipartConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.http.Descriptor;
import org.exist.http.servlets.Authenticator;
import org.exist.http.servlets.BasicAuthenticator;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.web.HttpAccount;
import org.exist.source.DBSource;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.util.LockException;
import org.exist.util.MimeType;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Database;

import javax.annotation.Nullable;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import java.io.*;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * A servlet to redirect HTTP requests. Similar to the popular UrlRewriteFilter, but
 * based on XQuery.
 *
 * The request is passed to an XQuery whose return value determines where the request will be
 * redirected to. An empty return value means the request will be passed through the filter
 * untouched. Otherwise, the query should return a single XML element, which will instruct the filter
 * how to further process the request. Details about the format can be found in the main documentation.
 *
 * The request is forwarded via {@link jakarta.servlet.RequestDispatcher#forward(jakarta.servlet.ServletRequest, jakarta.servlet.ServletResponse)}.
 * Contrary to HTTP forwarding, there is no additional roundtrip to the client. It all happens on
 * the server. The client will not notice the redirect.
 *
 * Please read the <a href="http://exist-db.org/urlrewrite.html">documentation</a> for further information.
 */
@MultipartConfig
public class XQueryURLRewrite extends HttpServlet {

    private static final Logger LOG = LogManager.getLogger(XQueryURLRewrite.class);
    private static final String DRIVER = "org.exist.xmldb.DatabaseImpl";
    private static final Pattern NAME_REGEX = Pattern.compile("^.*/([^/]+)$", 0);

    public static final String XQUERY_CONTROLLER_FILENAME = "controller.xq";
    public static final String LEGACY_XQUERY_CONTROLLER_FILENAME = "controller.xql";
    public static final XmldbURI XQUERY_CONTROLLER_URI = XmldbURI.create(XQUERY_CONTROLLER_FILENAME);
    public static final XmldbURI LEGACY_XQUERY_CONTROLLER_URI = XmldbURI.create(LEGACY_XQUERY_CONTROLLER_FILENAME);

    public static final String RQ_ATTR = "org.exist.forward";
    public static final String RQ_ATTR_REQUEST_URI = "org.exist.forward.request-uri";
    public static final String RQ_ATTR_SERVLET_PATH = "org.exist.forward.servlet-path";
    public static final String RQ_ATTR_RESULT = "org.exist.forward.result";
    public static final String RQ_ATTR_ERROR = "org.exist.forward.error";

    private ServletConfig config;
    private final Map<String, ModelAndView> urlCache = Collections.synchronizedMap(new TreeMap<>());
    private Subject defaultUser = null;
    private BrokerPool pool;
    // path to the query
    private String query = null;
    private boolean compiledCache = true;
    private boolean sendChallenge = true;
    private RewriteConfig rewriteConfig;
    private Authenticator authenticator;

    @Override
    public void init(final ServletConfig filterConfig) {
        // save FilterConfig for later use
        this.config = filterConfig;

        query = filterConfig.getInitParameter("xquery");

        final String optCompiledCache = filterConfig.getInitParameter("compiled-cache");
        if (optCompiledCache != null) {
            compiledCache = "true".equalsIgnoreCase(optCompiledCache);
        }

        final String optSendChallenge = filterConfig.getInitParameter("send-challenge");
        if (optSendChallenge != null) {
            sendChallenge = "true".equalsIgnoreCase(optSendChallenge);
        }
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        if (rewriteConfig == null) {
            configure();
            rewriteConfig = new RewriteConfig(this);
        }

        final long start = System.currentTimeMillis();

        if (LOG.isTraceEnabled()) {
            LOG.trace(request.getRequestURI());
        }

        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null && descriptor.requestsFiltered()) {
            final String attr = (String) request.getAttribute("XQueryURLRewrite.forwarded");
            if (attr == null) {
                //logs the request if specified in the descriptor
                descriptor.doLogRequestInReplayLog(request);

                request.setAttribute("XQueryURLRewrite.forwarded", "true");
            }
        }

        Subject user = defaultUser;

        Subject requestUser = HttpAccount.getUserFromServletRequest(request);
        if (requestUser != null) {
            user = requestUser;
        } else {
            // Secondly try basic authentication
            final String auth = request.getHeader("Authorization");
            if (auth != null && auth.toLowerCase().startsWith("basic ")) {
                requestUser = authenticator.authenticate(request, response, sendChallenge);
                if (requestUser != null) {
                    user = requestUser;
                }
            }
        }

        try {
            configure();
            //checkCache(user);

            final ControllerRequestWrapper modifiedRequest = new ControllerRequestWrapper(request);
            final URLRewrite staticRewrite = rewriteConfig.lookup(modifiedRequest);
            if (staticRewrite != null && !staticRewrite.isControllerForward()) {
                modifiedRequest.setPaths(staticRewrite.resolve(modifiedRequest), staticRewrite.getPrefix());

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Forwarding to target: {}", staticRewrite.getTarget());
                }

                staticRewrite.doRewrite(modifiedRequest, response);

            } else {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Processing request URI: {}", request.getRequestURI());
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
                    LOG.debug("Checked cache for URI: {} original: {}", modifiedRequest.getRequestURI(), request.getRequestURI());
                }

                // no: create a new model and view configuration
                if (modelView == null) {
                    modelView = new ModelAndView();

                    // Execute the query
                    try (final DBBroker broker = pool.get(Optional.ofNullable(user))) {

                        modifiedRequest.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());

                        final Properties outputProperties = new Properties();

                        outputProperties.setProperty(OutputKeys.INDENT, "yes");
                        outputProperties.setProperty(OutputKeys.ENCODING, UTF_8.name());
                        outputProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());

                        final Sequence result = runQuery(broker, modifiedRequest, response, modelView, staticRewrite, outputProperties);

                        logResult(broker, result);

                        if (response.isCommitted()) {
                            return;
                        }

                        // process the query result
                        if (result.getItemCount() == 1) {
                            final Item resource = result.itemAt(0);
                            if (!Type.subTypeOf(resource.getType(), Type.NODE)) {
                                throw new ServletException("XQueryURLRewrite: urlrewrite query should return an element!");
                            }
                            Node node = ((NodeValue) resource).getNode();
                            if (node.getNodeType() == Node.DOCUMENT_NODE) {
                                node = ((Document) node).getDocumentElement();
                            }
                            if (node.getNodeType() != Node.ELEMENT_NODE) {
                                //throw new ServletException("Redirect XQuery should return an XML element!");
                                response(broker, response, outputProperties, result);
                                return;
                            }
                            Element elem = (Element) node;
                            final String ns = elem.getNamespaceURI();
                            if (!Namespaces.EXIST_NS.equals(ns)) {
                                response(broker, response, outputProperties, result);
                                return;
                            }

                            final String nsUri = elem.getNamespaceURI();
                            if (Namespaces.EXIST_NS.equals(nsUri) && "dispatch".equals(elem.getLocalName())) {
                                node = elem.getFirstChild();
                                while (node != null) {
                                    final String nodeNs = node.getNamespaceURI();
                                    if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(nodeNs)) {
                                        final Element action = (Element) node;
                                        switch (action.getLocalName()) {
                                            case "view" -> parseViews(modifiedRequest, action, modelView);
                                            case "error-handler" ->
                                                    parseErrorHandlers(modifiedRequest, action, modelView);
                                            case "cache-control" -> {
                                                final String option = action.getAttribute("cache");
                                                modelView.setUseCache("yes".equals(option));
                                            }
                                            case null, default -> {
                                                final URLRewrite urw = parseAction(modifiedRequest, action);
                                                if (urw != null) {
                                                    modelView.setModel(urw);
                                                }
                                            }
                                        }
                                    }
                                    node = node.getNextSibling();
                                }
                                if (modelView.getModel() == null) {
                                    modelView.setModel(new PassThrough(config, elem, modifiedRequest));
                                }
                            } else if (nsUri != null && Namespaces.EXIST_NS.equals(elem.getNamespaceURI()) && "ignore".equals(elem.getLocalName())) {
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
                            LOG.debug("Caching request to {}", request.getRequestURI());
                            urlCache.put(modifiedRequest.getHeader("Host") + request.getRequestURI(), modelView);
                        }
                    }

                    // store the original request URI to org.exist.forward.request-uri
                    modifiedRequest.setAttribute(RQ_ATTR_REQUEST_URI, request.getRequestURI());
                    modifiedRequest.setAttribute(RQ_ATTR_SERVLET_PATH, getServletPathSafely(request));

                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("URLRewrite took {}ms.", System.currentTimeMillis() - start);
                }
                final HttpServletResponse wrappedResponse =
                        new CachingResponseWrapper(response, modelView.hasViews() || modelView.hasErrorHandlers());
                if (modelView.getModel() == null) {
                    modelView.setModel(new PassThrough(config, modifiedRequest));
                }

                if (staticRewrite != null) {
                    if (modelView.getModel().doResolve()) {
                        staticRewrite.rewriteRequest(modifiedRequest);
                    } else {
                        modelView.getModel().setAbsolutePath(modifiedRequest);
                    }
                }
                modifiedRequest.allowCaching(!modelView.hasViews());
                doRewrite(modelView.getModel(), modifiedRequest, wrappedResponse);

                final int status = wrappedResponse.getStatus();
                if (status == HttpServletResponse.SC_NOT_MODIFIED) {
                    response.flushBuffer();
                } else if (status < HttpServletResponse.SC_BAD_REQUEST) {
                    if (modelView.hasViews()) {
                        applyViews(modelView, modelView.views, response, modifiedRequest, wrappedResponse);
                    } else {
                        ((CachingResponseWrapper) wrappedResponse).flush();
                    }
                } else {
                    // HTTP response code indicates an error
                    if (modelView.hasErrorHandlers()) {
                        final byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
                        if (data != null) {
                            modifiedRequest.setAttribute(RQ_ATTR_ERROR, new String(data, UTF_8));
                        }
                        applyViews(modelView, modelView.errorHandlers, response, modifiedRequest, wrappedResponse);
                    } else {
                        flushError(response, wrappedResponse);
                    }
                }
            }
        } catch (final Throwable e) {
            LOG.error("Error while processing {}: {}", request.getRequestURI(), e.getMessage(), e);
            throw new ServletException("An error occurred while processing request to " + request.getRequestURI() + ": "
                    + e.getMessage(), e);

        }
    }

    /**
     * Under Jetty 12's EE10 servlet module, {@link HttpServletRequest#getServletPath()}
     * throws {@code IllegalArgumentException} (surfaced to the client as a 400 response)
     * when the request URI contains an ambiguous path segment (e.g. a real '/' next to
     * an encoded '%2F'), even though {@link HttpServletRequest#getRequestURI()} on the
     * very same request remains safe to call. This servlet is always mapped with
     * {@code url-pattern} {@code /*} (see web.xml), a path-prefix mapping with an empty
     * prefix, so per the Jakarta Servlet spec {@code getServletPath()} is defined to
     * always return {@code ""} here regardless of the request URI -- Jetty's own
     * ambiguity check is a blanket refusal to answer, not a sign the answer differs.
     * Fall back to that known value instead of failing the whole request.
     *
     * @see <a href="https://github.com/jetty/jetty.project/issues/12346">jetty/jetty.project#12346</a>
     */
    static String getServletPathSafely(final HttpServletRequest request) {
        try {
            return request.getServletPath();
        } catch (final IllegalArgumentException e) {
            LOG.debug("Falling back to \"\" for servlet path of ambiguous request URI {}: {}", request.getRequestURI(), e.getMessage());
            return "";
        }
    }

    BrokerPool getBrokerPool() {
        return pool;
    }

    Subject getDefaultUser() {
        return defaultUser;
    }

    private void applyViews(final ModelAndView modelView, final List<URLRewrite> views, final HttpServletResponse response, final ControllerRequestWrapper modifiedRequest, final HttpServletResponse currentResponse) throws IOException, ServletException {
        HttpServletResponse wrappedResponse = currentResponse;
        for (int i = 0; i < views.size(); i++) {
            final URLRewrite view = views.get(i);

            // get data returned from last action
            byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
            // determine request method to use for calling view
            String method = view.getMethod();
            if (method == null) {
                method = "POST";    // default is POST
            }

            final ControllerRequestWrapper wrappedReq = new ControllerRequestWrapper(modifiedRequest);
            wrappedReq.allowCaching(false);
            wrappedReq.setMethod(method);
            wrappedReq.setBasePath(modifiedRequest.getBasePath());
            wrappedReq.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
            wrappedReq.setContentType(wrappedResponse.getContentType());

            if (data != null) {
                wrappedReq.setData(data);
            }

            wrappedResponse = new CachingResponseWrapper(response, true);
            doRewrite(view, wrappedReq, wrappedResponse);

            // catch errors in the view
            final int status = wrappedResponse.getStatus();
            if (status >= HttpServletResponse.SC_BAD_REQUEST) {
                if (modelView != null && modelView.hasErrorHandlers()) {
                    data = ((CachingResponseWrapper) wrappedResponse).getData();
                    final String msg = data == null ? "" : new String(data, UTF_8);
                    modifiedRequest.setAttribute(RQ_ATTR_ERROR, msg);
                    applyViews(null, modelView.errorHandlers, response, modifiedRequest, wrappedResponse);
                    break;
                } else {
                    flushError(response, wrappedResponse);
                }
                break;
            } else if (i == views.size() - 1) {
                ((CachingResponseWrapper) wrappedResponse).flush();
            }
        }
    }

    private void response(final DBBroker broker, final HttpServletResponse response, final Properties outputProperties, final Sequence resultSequence) throws IOException {
        final String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
        try (final OutputStream os = response.getOutputStream();
                final Writer writer = new OutputStreamWriter(os, encoding);
                final PrintWriter printWriter = new PrintWriter(writer)) {
            if (!response.containsHeader("Content-Type")) {
                String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                if (mimeType != null) {
                    final int semicolon = mimeType.indexOf(';');
                    if (semicolon != Constants.STRING_NOT_FOUND) {
                        mimeType = mimeType.substring(0, semicolon);
                    }
                    response.setContentType(mimeType + "; charset=" + encoding);
                }
            }

            try {
                final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, printWriter);
                serializer.serialize(resultSequence);
            } catch (final SAXException | XPathException e) {
                throw new IOException(e);
            }
            printWriter.flush();
        }
    }

    private void flushError(final HttpServletResponse response, final HttpServletResponse wrappedResponse) throws IOException {
        if (!response.isCommitted()) {
            response.setStatus(wrappedResponse.getStatus());
            final byte[] data = ((CachingResponseWrapper) wrappedResponse).getData();
            if (data != null) {
                response.setContentType(wrappedResponse.getContentType());
                response.setCharacterEncoding(wrappedResponse.getCharacterEncoding());
                response.getOutputStream().write(data);
                response.flushBuffer();
            }
        }
    }

    private ModelAndView getFromCache(final String url, final Subject user) throws EXistException, PermissionDeniedException {
        /* Make sure we have a broker *before* we synchronize on urlCache or we may run
         * into a deadlock situation (with method checkCache)
         */
        final ModelAndView model = urlCache.get(url);
        if (model == null) {
            return null;
        }

        try (@SuppressWarnings("PMD.UnusedLocalVariable") final DBBroker broker = pool.get(Optional.ofNullable(user))) {

            if (model.getSourceInfo().source instanceof DBSource) {
                ((DBSource) model.getSourceInfo().source).validate(Permission.EXECUTE);
            }

            if (model.getSourceInfo().source.isValid() != Source.Validity.VALID) {
                urlCache.remove(url);
                return null;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("Using cached entry for {}", url);
            }
            return model;
        }
    }

    void clearCaches() {
        urlCache.clear();
    }

    /**
     * Process a rewrite action. Method checks if the target path is mapped
     * to another action in controller-config.xml. If yes, replaces the current action
     * with the new action.
     *
     * @param action the URLRewrite action
     * @param request the http request
     * @param response the http response
     */
    private void doRewrite(final URLRewrite action, final ControllerRequestWrapper request, final HttpServletResponse response) throws IOException, ServletException {
        URLRewrite effectiveAction = action;
        ControllerRequestWrapper effectiveRequest = request;
        if (effectiveAction.getTarget() != null && !(effectiveAction instanceof Redirect)) {
            final String uri = effectiveAction.resolve(effectiveRequest);
            final URLRewrite staticRewrite = rewriteConfig.lookup(uri, effectiveRequest.getServerName(), true, effectiveAction);

            if (staticRewrite != null) {
                staticRewrite.copyFrom(effectiveAction);
                effectiveAction = staticRewrite;
                final ControllerRequestWrapper modifiedRequest = new ControllerRequestWrapper(effectiveRequest);
                modifiedRequest.setPaths(uri, effectiveAction.getPrefix());

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Forwarding to : {} url: {}", effectiveAction.toString(), effectiveAction.getURI());
                }
                effectiveRequest = modifiedRequest;
            }
        }
        effectiveAction.prepareRequest(effectiveRequest);
        effectiveAction.doRewrite(effectiveRequest, response);
    }

    protected ServletConfig getConfig() {
        return config;
    }

    private URLRewrite parseAction(final HttpServletRequest request, final Element action) throws ServletException {
        final URLRewrite rewrite;
        if ("forward".equals(action.getLocalName())) {
            rewrite = new PathForward(config, action, request.getRequestURI());
        } else if ("redirect".equals(action.getLocalName())) {
            rewrite = new Redirect(action, request.getRequestURI());
        } else {
            rewrite = null;
        }
        return rewrite;
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod") // called from switch expression in service()
    private void parseViews(final HttpServletRequest request, final Element view, final ModelAndView modelView) throws ServletException {
        Node node = view.getFirstChild();
        while (node != null) {
            final String ns = node.getNamespaceURI();
            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(ns)) {
                final URLRewrite urw = parseAction(request, (Element) node);
                if (urw != null) {
                    modelView.addView(urw);
                }
            }
            node = node.getNextSibling();
        }
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod") // called from switch expression in service()
    private void parseErrorHandlers(final HttpServletRequest request, final Element view, final ModelAndView modelView) throws ServletException {
        Node node = view.getFirstChild();
        while (node != null) {
            final String ns = node.getNamespaceURI();
            if (node.getNodeType() == Node.ELEMENT_NODE && Namespaces.EXIST_NS.equals(ns)) {
                final URLRewrite urw = parseAction(request, (Element) node);
                if (urw != null) {
                    modelView.addErrorHandler(urw);
                }
            }
            node = node.getNextSibling();
        }
    }

    private void configure() throws ServletException {
        if (pool != null) {
            return;
        }
        try {
            final Class<?> driver = Class.forName(DRIVER);
            final Database database = (Database) driver.getDeclaredConstructor().newInstance();
            database.setProperty("create-database", "true");
            DatabaseManager.registerDatabase(database);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Initialized database");
            }
        } catch (final Exception e) {
            final String errorMessage = "Failed to initialize database driver";
            LOG.error(errorMessage, e);
            throw new ServletException(errorMessage + ": " + e.getMessage(), e);
        }

        try {
            pool = BrokerPool.getInstance();
        } catch (final EXistException e) {
            throw new ServletException("Could not initialize db: " + e.getMessage(), e);
        }

        defaultUser = pool.getSecurityManager().getGuestSubject();

        final String username = config.getInitParameter("user");
        if (username != null) {
            final String password = config.getInitParameter("password");
            try {
                final Subject user = pool.getSecurityManager().authenticate(username, password);
                if (user != null && user.isAuthenticated()) {
                    defaultUser = user;
                }
            } catch (final AuthenticationException e) {
                LOG.error("User can not be authenticated ({}), using default user.", username);
            }
        }
        authenticator = new BasicAuthenticator(pool);
    }

    private void logResult(final DBBroker broker, final Sequence result) throws SAXException {
        if (LOG.isTraceEnabled() && result.getItemCount() > 0) {
            final Serializer serializer = broker.borrowSerializer();
            try {
                final Item item = result.itemAt(0);
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    LOG.trace(serializer.serialize((NodeValue) item));
                }
            } finally {
                broker.returnSerializer(serializer);
            }
        }
    }

    @Override
    public void destroy() {
        config = null;
    }

    private SourceInfo getSourceInfo(final DBBroker broker, final ControllerRequestWrapper request, final URLRewrite staticRewrite) throws ServletException {
        final String moduleLoadPath = config.getServletContext().getRealPath("/");
        final String basePath = staticRewrite == null ? "." : staticRewrite.getTarget();
        if (basePath == null) {
            return getSource(broker, moduleLoadPath);
        } else {
            return findSource(request, broker, basePath);
        }
    }

    private Sequence runQuery(final DBBroker broker, final ControllerRequestWrapper request, final HttpServletResponse response, final ModelAndView model, final URLRewrite staticRewrite, final Properties outputProperties) throws ServletException, XPathException, PermissionDeniedException {
        // Try to find the XQuery
        final SourceInfo sourceInfo = getSourceInfo(broker, request, staticRewrite);
        if (sourceInfo == null) {
            return Sequence.EMPTY_SEQUENCE; // no controller found
        }

        final String basePath = staticRewrite == null ? "." : staticRewrite.getTarget();

        final XQuery xquery = broker.getBrokerPool().getXQueryService();
        final XQueryPool xqyPool = broker.getBrokerPool().getXQueryPool();

        CompiledXQuery compiled = null;
        if (compiledCache) {
            compiled = xqyPool.borrowCompiledXQuery(broker, sourceInfo.source);
        }
        final XQueryContext queryContext;
        if (compiled == null) {
            queryContext = new XQueryContext(broker.getBrokerPool());
        } else {
            queryContext = compiled.getContext();
            queryContext.prepareForReuse();
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

        try {
            return xquery.execute(broker, compiled, null, outputProperties);
        } finally {
            queryContext.runCleanupTasks();
            xqyPool.returnCompiledXQuery(sourceInfo.source, compiled);
        }
    }

    String adjustPathForSourceLookup(final String basePath, final String path) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("request path={}", path);
        }

        String adjustedPath = path;
        if (basePath.startsWith(XmldbURI.EMBEDDED_SERVER_URI_PREFIX) && adjustedPath.startsWith(basePath.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, ""))) {
            adjustedPath = adjustedPath.replace(basePath.replace(XmldbURI.EMBEDDED_SERVER_URI_PREFIX, ""), "");

        } else if (adjustedPath.startsWith("/db/")) {
            adjustedPath = adjustedPath.substring(4);
        }

        if (adjustedPath.startsWith("/")) {
            adjustedPath = adjustedPath.substring(1);
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("adjusted request path={}", adjustedPath);
        }
        return adjustedPath;
    }

    @SuppressWarnings("PMD.UnusedPrivateMethod") // called indirectly from getSourceInfo()
    private SourceInfo findSource(final HttpServletRequest request, final DBBroker broker, final String basePath) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("basePath={}", basePath);
        }

        final String requestURI = request.getRequestURI();
        String path = requestURI.substring(request.getContextPath().length());
        path = adjustPathForSourceLookup(basePath, path);
        final String[] components = path.split("/");

        if (basePath.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Looking for " + XQUERY_CONTROLLER_FILENAME + " in the database, starting from: {}", basePath);
            }
            return findSourceFromDb(broker, basePath, path, components);
        } else {
            if (LOG.isTraceEnabled()) {
                LOG.trace("Looking for " + XQUERY_CONTROLLER_FILENAME + " in the filesystem, starting from: {}", basePath);
            }
            return findSourceFromFs(basePath, components);
        }
    }

    private @Nullable
    SourceInfo findSourceFromDb(final DBBroker broker, final String basePath, final String path, final String[] components) {
        LockedDocument lockedControllerDoc = null;
        try {
            final XmldbURI locationUri = XmldbURI.xmldbUriFor(basePath);
            XmldbURI resourceUri = locationUri;
            for(final String component : components) {
                resourceUri = resourceUri.append(component);
            }

            lockedControllerDoc = findDbControllerXql(broker, locationUri, resourceUri);

            if (lockedControllerDoc == null) {
                LOG.warn("XQueryURLRewrite controller could not be found for path: {}", path);
                return null;
            }

            final DocumentImpl controllerDoc = lockedControllerDoc.getDocument();

            if (LOG.isTraceEnabled()) {
                LOG.trace("Found controller file: {}", controllerDoc.getURI());
            }

            if (controllerDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                    !"application/xquery".equals(controllerDoc.getMimeType())) {
                LOG.warn("XQuery resource: {} is not an XQuery or declares a wrong mime-type", query);
                return null;
            }

            final String controllerPath = controllerDoc.getCollection().getURI().getRawCollectionPath();
            return new SourceInfo(new DBSource(broker.getBrokerPool(), (BinaryDocument) controllerDoc, true), "xmldb:exist://" + controllerPath, controllerPath.substring(locationUri.getCollectionPath().length()));

        } catch (final URISyntaxException e) {
            LOG.warn("Bad URI for base path: {}", e.getMessage(), e);
            return null;
        } finally {
            if (lockedControllerDoc != null) {
                lockedControllerDoc.close();
            }
        }
    }

    /**
     * Finds a `controller.xq` (or legacy `controller.xql`)
     * file within a Collection hierarchy.
     * Most specific collections are considered first.
     *
     * For example, given the collectionUri `/db/apps`
     * and the resourceUri /db/apps/myapp/data, the
     * order or search will be:
     *
     * /db/apps/myapp/data/controller.xq
     * /db/apps/myapp/controller.xq
     * /db/apps/controller.xq
     *
     * @param broker         The database broker
     * @param collectionUri  The root collection URI, below which we should not descend
     * @param resourceUri The path to the most specific document or collection for which we should find a controller
     * @return The most relevant controller.xq document (with a READ_LOCK), or null if it could not be found.
     */
    //@tailrec
    private @Nullable
    LockedDocument findDbControllerXql(final DBBroker broker, final XmldbURI collectionUri, final XmldbURI resourceUri) {
        if (collectionUri.compareTo(resourceUri) > 0) {
            return null;
        }

        try (final Collection collection = broker.openCollection(resourceUri, LockMode.READ_LOCK)) {
            if (collection != null) {
                LockedDocument lockedDoc = collection.getDocumentWithLock(broker, XQUERY_CONTROLLER_URI, LockMode.READ_LOCK);

                if (lockedDoc == null) {
                    lockedDoc = collection.getDocumentWithLock(broker, LEGACY_XQUERY_CONTROLLER_URI, LockMode.READ_LOCK);
                }

                if (lockedDoc != null) {
                    // NOTE: early release of Collection lock inline with Asymmetrical Locking scheme
                    // collection lock will be released by the try-with-resources before the locked document is returned by this function
                    return lockedDoc;
                }


            }
        } catch (final PermissionDeniedException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Permission denied while scanning for XQueryURLRewrite controllers: {}", e.getMessage(), e);
            }
            return null;
        } catch (final LockException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("LockException while scanning for XQueryURLRewrite controllers: {}", e.getMessage(), e);
            }
            return null;
        }

        if(resourceUri.numSegments() == 2) {
            return null;
        }
        final XmldbURI subResourceUri = resourceUri.removeLastSegment();

        return findDbControllerXql(broker, collectionUri, subResourceUri);
    }

    // package-private rather than private so it is directly testable without reflection
    SourceInfo findSourceFromFs(final String basePath, final String[] components) {
        final String realPath = config.getServletContext().getRealPath(basePath);
        // the Servlet API permits getRealPath() to return null when the container cannot map
        // basePath to a location on disk; there is no filesystem controller to find.
        if (realPath == null) {
            LOG.warn("Base path for XQueryURLRewrite does not point to a directory");
            return null;
        }
        final Path baseDir = Path.of(realPath);
        if (!Files.isDirectory(baseDir)) {
            LOG.warn("Base path for XQueryURLRewrite does not point to a directory");
            return null;
        }

        Path controllerFile = null;
        Path subDir = baseDir;
        for (final String component : components) {
            if (!component.isEmpty()) {
                subDir = subDir.resolve(component);
                if (Files.isDirectory(subDir)) {
                    Path cf = subDir.resolve(XQUERY_CONTROLLER_FILENAME);

                    if (!Files.isReadable(cf)) {
                        cf = subDir.resolve(LEGACY_XQUERY_CONTROLLER_FILENAME);
                    }

                    if (Files.isReadable(cf)) {
                        controllerFile = cf;
                    }
                } else {
                    break;
                }
            }
        }

        if (controllerFile == null) {
            Path cf = baseDir.resolve(XQUERY_CONTROLLER_FILENAME);

            if (!Files.isReadable(cf)) {
                cf = subDir.resolve(LEGACY_XQUERY_CONTROLLER_FILENAME);
            }

            if (Files.isReadable(cf)) {
                controllerFile = cf;
            }
        }

        if (controllerFile == null) {
            LOG.warn("XQueryURLRewrite controller could not be found");
            return null;
        }

        if (LOG.isTraceEnabled()) {
            LOG.trace("Found controller file: {}", controllerFile.toAbsolutePath());
        }

        final String parentPath = controllerFile.getParent().toAbsolutePath().toString();
        String controllerPath = parentPath.substring(baseDir.toAbsolutePath().toString().length());
        // replace windows path separators
        controllerPath = controllerPath.replace('\\', '/');
        return new SourceInfo(new FileSource(controllerFile, true), parentPath, controllerPath);
    }

    private SourceInfo getSource(final DBBroker broker, final String moduleLoadPath) throws ServletException {
        final SourceInfo sourceInfo;
        if (query.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
            // Is the module source stored in the database?
            try {
                final XmldbURI locationUri = XmldbURI.xmldbUriFor(query);

                try (final LockedDocument lockedSourceDoc = broker.getXMLResource(locationUri.toCollectionPathURI(), LockMode.READ_LOCK)) {
                    if (lockedSourceDoc == null) {
                        throw new ServletException("XQuery resource: " + query + " not found in database");
                    }

                    final DocumentImpl sourceDoc = lockedSourceDoc.getDocument();
                    if (sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !"application/xquery".equals(sourceDoc.getMimeType())) {
                        throw new ServletException("XQuery resource: " + query + " is not an XQuery or " +
                                "declares a wrong mime-type");
                    }
                    sourceInfo = new SourceInfo(new DBSource(broker.getBrokerPool(), (BinaryDocument) sourceDoc, true),
                            locationUri.toString());
                } catch (final PermissionDeniedException e) {
                    throw new ServletException("permission denied to read module source from " + query);
                }
            } catch (final URISyntaxException e) {
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

    private void declareVariables(final XQueryContext context, final SourceInfo sourceInfo, final URLRewrite staticRewrite, final String basePath, final ControllerRequestWrapper request, final HttpServletResponse response) throws XPathException {
        // parseMultipart=true so that multipart/form-data uploads (including file parts)
        // are exposed to controllers and RESTXQ resource functions for every HTTP method,
        // not only POST. See https://github.com/eXist-db/exist/issues/6580 and
        // https://github.com/eXist-db/exist/issues/6578
        final HttpRequestWrapper reqw = new HttpRequestWrapper(request, UTF_8.name(), UTF_8.name(), true);
        final HttpResponseWrapper respw = new HttpResponseWrapper(response);
        // context.declareNamespace(RequestModule.PREFIX,
        // RequestModule.NAMESPACE_URI);
        context.setHttpContext(new XQueryContext.HttpContext(reqw, respw));

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
        if (!sourceInfo.controllerPath.isEmpty() && !"/".equals(sourceInfo.controllerPath)) {
            path = request.getInContextPath().substring(sourceInfo.controllerPath.length());
        } else {
            path = request.getInContextPath();
        }
        final int p = path.lastIndexOf(';');
        if (p != Constants.STRING_NOT_FOUND) {
            path = path.substring(0, p);
        }
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
            LOG.debug("\nexist:path = {}\nexist:resource = {}\nexist:controller = {}", path, resource, sourceInfo.controllerPath);
        }
    }

    private static class ModelAndView {
        private URLRewrite rewrite = null;
        private final List<URLRewrite> views = new LinkedList<>();
        private List<URLRewrite> errorHandlers = null;
        private boolean useCache = false;
        private SourceInfo sourceInfo = null;

        public void setSourceInfo(final SourceInfo sourceInfo) {
            this.sourceInfo = sourceInfo;
        }

        public SourceInfo getSourceInfo() {
            return sourceInfo;
        }

        public void setModel(final URLRewrite model) {
            this.rewrite = model;
        }

        public URLRewrite getModel() {
            return rewrite;
        }

        public void addErrorHandler(final URLRewrite handler) {
            if (errorHandlers == null) {
                errorHandlers = new LinkedList<>();
            }
            errorHandlers.add(handler);
        }

        public void addView(URLRewrite view) {
            views.add(view);
        }

        public boolean hasViews() {
            return !views.isEmpty();
        }

        public boolean hasErrorHandlers() {
            return errorHandlers != null && !errorHandlers.isEmpty();
        }

        public boolean useCache() {
            return useCache;
        }

        public void setUseCache(final boolean useCache) {
            this.useCache = useCache;
        }
    }

    private static class SourceInfo {
        final Source source;
        final String moduleLoadPath;
        final String controllerPath;

        private SourceInfo(final Source source, final String moduleLoadPath) {
            this(source, moduleLoadPath, "");
        }

        private SourceInfo(final Source source, final String moduleLoadPath, final String controllerPath) {
            this.source = source;
            this.moduleLoadPath = moduleLoadPath;
            this.controllerPath = controllerPath;
        }
    }

}
