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
package org.exist.http.restxq;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.http.servlets.AbstractExistHttpServlet;
import org.exist.security.EffectiveSubject;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.source.DBSource;
import org.exist.storage.DBBroker;
import org.exist.storage.ProcessMonitor;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.xmldb.XmldbURI;
import org.exist.http.restxq.xquery.WebFunctions;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.util.*;



/**
 * Native RESTXQ servlet that dispatches HTTP requests to XQuery functions
 * annotated with {@code %rest:*} annotations.
 *
 * <p>This servlet replaces the old {@code RestXqServlet} from the EXQuery
 * extension, eliminating the 10-JAR EXQuery library dependency and the
 * adapter layer between EXQuery and eXist types.</p>
 *
 * <h3>Servlet Configuration</h3>
 * <p>Add to web.xml:</p>
 * <pre>{@code
 * <servlet>
 *     <servlet-name>NativeRestXqServlet</servlet-name>
 *     <servlet-class>org.exist.http.restxq.NativeRestXqServlet</servlet-class>
 *     <init-param>
 *         <param-name>scan-root</param-name>
 *         <param-value>/db/apps</param-value>
 *     </init-param>
 * </servlet>
 * }</pre>
 */
public class NativeRestXqServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = 1L;
    private static final Logger LOG = LogManager.getLogger(NativeRestXqServlet.class);

    /** Default database path to scan for RESTXQ modules. */
    private static final String DEFAULT_SCAN_ROOT = "/db/apps";

    /** Init parameter for the scan root collection path. */
    private static final String PARAM_SCAN_ROOT = "scan-root";

    /** Init parameter to scan at startup (default true). */
    private static final String PARAM_SCAN_ON_STARTUP = "scan-on-startup";

    private RouteRegistry registry;

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);

        final String scanRoot = Optional.ofNullable(config.getInitParameter(PARAM_SCAN_ROOT))
                .orElse(DEFAULT_SCAN_ROOT);

        registry = new RouteRegistry(getPool(), scanRoot);

        final boolean scanOnStartup = !"false".equalsIgnoreCase(
                config.getInitParameter(PARAM_SCAN_ON_STARTUP));

        if (scanOnStartup) {
            try (final DBBroker broker = getPool().get(Optional.empty())) {
                LOG.info("NativeRestXqServlet: pre-scanning RESTXQ modules at startup");
                registry.fullScan(broker);
            } catch (final EXistException e) {
                LOG.warn("Failed to pre-scan RESTXQ modules at startup: {}", e.getMessage());
            }
        }

        LOG.info("NativeRestXqServlet initialized; scan-root={}, scan-on-startup={}",
                scanRoot, scanOnStartup);
    }

    @Override
    protected void service(final HttpServletRequest request,
                           final HttpServletResponse response)
            throws ServletException, IOException {

        // Authenticate
        final Subject user = authenticate(request, response);
        if (user == null) {
            return; // Authentication challenge sent
        }

        // Wrap request to cache body for potential forward dispatch
        final HttpServletRequest wrappedRequest =
                hasBody(request) ? new CachingHttpServletRequest(request) : request;

        // Handle /.init — cache invalidation endpoint
        final String pathInfo = getRestXqPath(wrappedRequest);
        if ("/.init".equals(pathInfo)) {
            registry.invalidate();
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        final long startTime = System.nanoTime();

        try (final DBBroker broker = getPool().get(Optional.of(user))) {

            // Ensure the route registry is initialized
            registry.ensureInitialized(broker);

            // Find matching route
            final String method = wrappedRequest.getMethod().toUpperCase(Locale.ROOT);
            Route route = registry.findRoute(
                    method, pathInfo,
                    wrappedRequest.getContentType(),
                    wrappedRequest.getHeader("Accept"));

            boolean headFromGet = false;

            // Auto-handle HEAD: if no explicit HEAD route, try GET
            if (route == null && "HEAD".equals(method)) {
                route = registry.findRoute("GET", pathInfo,
                        wrappedRequest.getContentType(),
                        wrappedRequest.getHeader("Accept"));
                if (route != null) {
                    headFromGet = true;
                }
            }

            // Auto-handle OPTIONS: if no explicit OPTIONS route, return Allow header
            if (route == null && "OPTIONS".equals(method)) {
                final Set<String> allowed = registry.allowedMethods(pathInfo);
                if (!allowed.isEmpty()) {
                    allowed.add("OPTIONS");
                    allowed.add("HEAD");
                    response.setHeader("Allow", String.join(", ", allowed));
                    response.setStatus(HttpServletResponse.SC_OK);
                    return;
                }
            }

            if (route == null) {
                // If modules failed and there are no working routes at all,
                // return 500 (the user likely just uploaded a broken module)
                final Map<String, String> failures = registry.getFailedModules();
                if (!failures.isEmpty() && registry.getRouteCount() == 0
                        && registry.getModuleCount() == 0) {
                    final String firstError = failures.values().iterator().next();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "RESTXQ module error: " + firstError);
                    return;
                }
                // Check if path matches with different method → 405
                final Set<String> allowed = registry.allowedMethods(pathInfo,
                        wrappedRequest.getContentType(), wrappedRequest.getHeader("Accept"));
                if (!allowed.isEmpty()) {
                    response.setHeader("Allow", String.join(", ", allowed));
                    response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND);
                }
                return;
            }

            if (headFromGet) {
                executeRoute(broker, route, wrappedRequest, response, true);
            } else {
                executeRoute(broker, route, wrappedRequest, response, false);
            }

            // Add Server-Timing header
            final long durationMs = (System.nanoTime() - startTime) / 1_000_000;
            response.addHeader("Server-Timing", "total;dur=" + durationMs);

        } catch (final EXistException e) {
            LOG.error("Database error processing RESTXQ request", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Database error: " + e.getMessage());
        }
    }

    /**
     * Executes the matched route's XQuery function and writes the result
     * to the HTTP response.
     */
    private void executeRoute(final DBBroker broker, final Route route,
                              final HttpServletRequest request,
                              final HttpServletResponse response,
                              final boolean headOnly) throws IOException {

        CompiledXQuery xquery = null;
        ProcessMonitor processMonitor = null;

        try {
            // Compile or retrieve the XQuery module
            final XmldbURI moduleUri = XmldbURI.create(route.getModuleUri());
            final BinaryDocument binDoc = (BinaryDocument) broker.getResource(moduleUri,
                    Permission.READ | Permission.EXECUTE);
            if (binDoc == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "RESTXQ module not found: " + route.getModuleUri());
                return;
            }

            final DBSource source = new DBSource(getPool(), binDoc, true);
            final XQuery xqueryService = getPool().getXQueryService();
            final XQueryContext context = new XQueryContext(getPool());

            context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX
                    + moduleUri.removeLastSegment().toString());

            xquery = xqueryService.compile(context, source);

            // Check eXist security annotations (%auth:*) before execution
            final String authDenial = SecurityAnnotationHandler.checkAccess(
                    broker.getCurrentSubject(), route, xquery);
            if (authDenial != null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, authDenial);
                return;
            }

            // Resolve the function
            final UserDefinedFunction fn = context.resolveFunction(
                    route.getFunctionName(), route.getArity());
            if (fn == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "RESTXQ function not found: " + route.getFunctionName()
                                + "#" + route.getArity());
                return;
            }

            // Evaluate global variable declarations (workaround for context.reset())
            final Expression rootExpr = context.getRootExpression();
            for (int i = 0; i < rootExpr.getSubExpressionCount(); i++) {
                final Expression subExpr = rootExpr.getSubExpression(i);
                if (subExpr instanceof VariableDeclaration) {
                    subExpr.eval(null, null);
                }
            }

            // Set up process monitoring
            processMonitor = broker.getBrokerPool().getProcessMonitor();
            context.getProfiler().traceQueryStart();
            processMonitor.queryStarted(context.getWatchDog());

            // Bind parameters
            final String restxqPath = getRestXqPath(request);
            final SequenceType[] argTypes = fn.getSignature().getArgumentTypes();
            final Sequence[] args = ParameterBinder.bind(
                    context, route, request, restxqPath, argTypes);

            // Execute the function
            try (final FunctionReference fnRef = new FunctionReference(
                    new FunctionCall(context, fn))) {

                fnRef.analyze(new AnalyzeContextInfo());

                // Handle setUid/setGid
                final Optional<EffectiveSubject> effectiveSubject = getEffectiveSubject(xquery);
                try {
                    effectiveSubject.ifPresent(broker::pushSubject);

                    final Sequence result = fnRef.evalFunction(null, null, args);

                    // Check if this is an explicit HEAD route (not auto-from-GET)
                    final boolean isExplicitHead = route.getMethods().contains("HEAD")
                            && "HEAD".equals(request.getMethod().toUpperCase(Locale.ROOT));

                    if (isExplicitHead) {
                        // Explicit HEAD handler: must return rest:response element
                        if (result.isEmpty()) {
                            throw new XPathException((Expression) null,
                                    "HEAD handler must return a rest:response element, got empty sequence");
                        }
                        final Item firstItem = result.itemAt(0);
                        if (!Type.subTypeOf(firstItem.getType(), Type.ELEMENT)) {
                            throw new XPathException((Expression) null,
                                    "HEAD handler must return a rest:response element");
                        }
                        final org.w3c.dom.Node node = ((NodeValue) firstItem).getNode();
                        if (!"response".equals(node.getLocalName())
                                || !RestXqNamespaces.REST_NS.equals(node.getNamespaceURI())) {
                            throw new XPathException((Expression) null,
                                    "HEAD handler must return a rest:response element, got: "
                                            + node.getLocalName());
                        }
                        // HEAD handler: 200 OK, no body
                        response.setStatus(HttpServletResponse.SC_OK);
                    } else if (!headOnly) {
                        // Normal route execution
                        if (!response.isCommitted()) {
                            response.setStatus(HttpServletResponse.SC_OK);
                        }
                        ResponseWriter.write(broker, route, result, response);
                    } else {
                        // Auto-HEAD from GET: set status and headers but skip body
                        response.setStatus(HttpServletResponse.SC_OK);
                        if (response.getContentType() == null) {
                            response.setContentType(route.getResponseContentType());
                        }
                    }

                } finally {
                    effectiveSubject.ifPresent(es -> broker.popSubject());
                }
            }

        } catch (final WebFunctions.WebErrorException e) {
            // web:error() — return clean HTTP error, no stack trace
            if (!response.isCommitted()) {
                response.sendError(e.getHttpStatusCode(), e.getDetailMessage());
            }
        } catch (final XPathException e) {
            // Try to find a matching error handler
            final org.exist.dom.QName errorQName = e.getErrorCode() != null
                    ? e.getErrorCode().getErrorQName()
                    : new org.exist.dom.QName("FOER0000", "http://www.w3.org/2005/xqt-errors", "err");
            final ErrorRoute errorHandler = registry.findErrorHandler(errorQName);
            if (errorHandler != null && !response.isCommitted()) {
                try {
                    executeErrorHandler(broker, errorHandler, e, response);
                    return;
                } catch (final Exception ex) {
                    LOG.error("Error executing RESTXQ error handler", ex);
                }
            }
            LOG.error("XQuery error executing RESTXQ function {}: {}",
                    route.getFunctionName(), e.getMessage());
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "XQuery error: " + e.getMessage());
            }
        } catch (final RestXqForwardException e) {
            // Server-side forward: dispatch to the target route
            final String forwardPath = "/" + e.getForwardPath();
            final Route forwardRoute = registry.findRoute(
                    request.getMethod().toUpperCase(Locale.ROOT), forwardPath,
                    request.getContentType(), request.getHeader("Accept"));
            if (forwardRoute != null && !response.isCommitted()) {
                executeRoute(broker, forwardRoute, request, response, headOnly);
            } else if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        "Forward target not found: " + forwardPath);
            }
        } catch (final PermissionDeniedException e) {
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
        } catch (final Exception e) {
            LOG.error("Unexpected error executing RESTXQ function", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        e.getMessage());
            }
        } finally {
            if (processMonitor != null && xquery != null) {
                xquery.getContext().getProfiler().traceQueryEnd(xquery.getContext());
                processMonitor.queryCompleted(xquery.getContext().getWatchDog());
            }
        }
    }

    /**
     * Executes a RESTXQ error handler function, binding error parameters.
     */
    private void executeErrorHandler(final DBBroker broker, final ErrorRoute errorHandler,
                                     final XPathException error,
                                     final HttpServletResponse response) throws Exception {

        final XmldbURI moduleUri = XmldbURI.create(errorHandler.getModuleUri());
        final BinaryDocument binDoc = (BinaryDocument) broker.getResource(moduleUri,
                Permission.READ | Permission.EXECUTE);
        if (binDoc == null) {
            throw new IOException("Error handler module not found: " + errorHandler.getModuleUri());
        }

        final DBSource source = new DBSource(getPool(), binDoc, true);
        final XQuery xqueryService = getPool().getXQueryService();
        final XQueryContext context = new XQueryContext(getPool());
        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX
                + moduleUri.removeLastSegment().toString());

        final CompiledXQuery xquery = xqueryService.compile(context, source);
        final UserDefinedFunction fn = context.resolveFunction(
                errorHandler.getFunctionName(), errorHandler.getArity());

        // Evaluate global variables
        final Expression rootExpr = context.getRootExpression();
        for (int i = 0; i < rootExpr.getSubExpressionCount(); i++) {
            final Expression subExpr = rootExpr.getSubExpression(i);
            if (subExpr instanceof VariableDeclaration) {
                subExpr.eval(null, null);
            }
        }

        // Bind error parameters
        final SequenceType[] argTypes = fn.getSignature().getArgumentTypes();
        final Sequence[] args = new Sequence[argTypes != null ? argTypes.length : 0];

        // Build error param bindings
        final Map<String, Sequence> errorBindings = new LinkedHashMap<>();
        final org.exist.dom.QName errorQName = error.getErrorCode() != null
                ? error.getErrorCode().getErrorQName()
                : new org.exist.dom.QName("FOER0000", "http://www.w3.org/2005/xqt-errors", "err");
        errorBindings.put("code", new StringValue("#" +
                (errorQName.getPrefix() != null && !errorQName.getPrefix().isEmpty()
                        ? errorQName.getPrefix() + ":" : "")
                + errorQName.getLocalPart()));
        errorBindings.put("description", new StringValue(
                error.getDetailMessage() != null ? error.getDetailMessage() : ""));
        errorBindings.put("module", new StringValue(
                error.getSource() != null ? error.getSource().path() : ""));
        errorBindings.put("line-number", new IntegerValue(error.getLine()));
        errorBindings.put("column-number", new IntegerValue(error.getColumn()));
        if (error.getErrorVal() != null) {
            errorBindings.put("value", error.getErrorVal());
        }

        for (final Map.Entry<String, Route.ParamBinding> entry : errorHandler.getErrorParams().entrySet()) {
            final String varName = entry.getValue().getVariableName();
            final String paramName = entry.getValue().getParamName();
            final Sequence val = errorBindings.get(paramName);
            if (val != null) {
                errorBindings.put(varName, val);
            }
        }

        // Map to function args
        if (argTypes != null) {
            for (int i = 0; i < argTypes.length; i++) {
                final FunctionParameterSequenceType paramType = (FunctionParameterSequenceType) argTypes[i];
                final Sequence val = errorBindings.get(paramType.getAttributeName());
                args[i] = val != null ? val : Sequence.EMPTY_SEQUENCE;
            }
        }

        try (final FunctionReference fnRef = new FunctionReference(new FunctionCall(context, fn))) {
            fnRef.analyze(new AnalyzeContextInfo());
            final Sequence result = fnRef.evalFunction(null, null, args);

            response.setStatus(HttpServletResponse.SC_OK);
            // Use a minimal route for serialization
            final Route dummyRoute = new Route(errorHandler.getModuleUri(),
                    errorHandler.getFunctionName(), errorHandler.getArity(),
                    PathMatcher.parse("/"), Set.of("GET"), new java.util.Properties(),
                    List.of(), List.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(), null);
            ResponseWriter.write(broker, dummyRoute, result, response);
        }
    }

    /**
     * Extracts the RESTXQ-relevant path from the request.
     * Strips the servlet context path and any prefix like "/apps".
     */
    private String getRestXqPath(final HttpServletRequest request) {
        String path = request.getPathInfo();
        if (path == null) {
            path = request.getServletPath();
        }
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        return path;
    }

    /**
     * If the compiled XQuery is setUid and/or setGid, returns the
     * EffectiveSubject to use for execution.
     */
    private Optional<EffectiveSubject> getEffectiveSubject(final CompiledXQuery xquery) {
        final org.exist.source.Source src = xquery.getContext().getSource();
        if (src instanceof DBSource dbSrc) {
            final Permission perm = dbSrc.getPermissions();
            if (perm.isSetUid()) {
                if (perm.isSetGid()) {
                    return Optional.of(new EffectiveSubject(perm.getOwner(), perm.getGroup()));
                } else {
                    return Optional.of(new EffectiveSubject(perm.getOwner()));
                }
            } else if (perm.isSetGid()) {
                return Optional.of(new EffectiveSubject(
                        xquery.getContext().getBroker().getCurrentSubject(), perm.getGroup()));
            }
        }
        return Optional.empty();
    }

    /**
     * Returns the route registry, for use by XQuery modules like
     * rest:resource-functions() and rest:init().
     */
    public RouteRegistry getRouteRegistry() {
        return registry;
    }

    /**
     * Returns true if the request method typically carries a body.
     */
    private static boolean hasBody(final HttpServletRequest request) {
        final String method = request.getMethod().toUpperCase(Locale.ROOT);
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }
}
