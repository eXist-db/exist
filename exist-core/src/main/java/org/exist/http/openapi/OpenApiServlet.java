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
package org.exist.http.openapi;

import com.fasterxml.jackson.core.JsonGenerator;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.http.servlets.AbstractExistHttpServlet;
import org.exist.security.Subject;
import org.exist.source.StringSource;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Built-in OpenAPI routing servlet. When a collection contains a {@code controller.json}
 * that declares OpenAPI spec files, this servlet handles routing requests to XQuery
 * handler functions based on the OpenAPI spec's operationId mapping.
 * <p>
 * This replaces the Roaster XQuery library for apps that opt in via controller.json.
 * Apps without controller.json continue to use XQueryURLRewrite + controller.xql.
 * </p>
 * <p>
 * For a request to {@code /exist/apps/myapp/api/users/123}:
 * <ol>
 *   <li>Extract app name "myapp" and remaining path "/api/users/123"</li>
 *   <li>Look up matching route in OpenApiServiceRegistry</li>
 *   <li>If found: build request map, compile handler XQuery, execute, serialize result</li>
 *   <li>If not found: return false to let the filter chain continue (falls through to XQueryURLRewrite)</li>
 * </ol>
 * </p>
 */
public class OpenApiServlet extends AbstractExistHttpServlet {

    private static final Logger LOG = LogManager.getLogger(OpenApiServlet.class);

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response)
            throws ServletException, IOException {

        final OpenApiServiceRegistry registry = OpenApiServiceRegistry.getInstance();
        if (!registry.hasRoutes()) {
            // No routes registered, pass through
            super.service(request, response);
            return;
        }

        // Extract the path relative to /exist/openapi/
        final String requestURI = request.getRequestURI();
        final String openApiPrefix = request.getContextPath() + "/openapi/";
        if (!requestURI.startsWith(openApiPrefix)) {
            super.service(request, response);
            return;
        }
        final String relativePath = requestURI.substring(openApiPrefix.length());
        final String method = request.getMethod();

        // Handle CORS preflight
        if ("OPTIONS".equalsIgnoreCase(method)) {
            handleOptions(request, response);
            return;
        }

        // Try to match a route
        final OpenApiServiceRegistry.RouteMatch match = registry.findRoute(method, relativePath);
        if (match == null) {
            // No matching route — fall through to default handler (XQueryURLRewrite)
            super.service(request, response);
            return;
        }

        // Authenticate
        final Subject user = authenticate(request, response);
        if (user == null) {
            return;
        }

        // Dispatch to XQuery handler
        try (final DBBroker broker = getPool().get(Optional.of(user))) {
            dispatchToHandler(broker, request, response, match);
        } catch (final EXistException e) {
            LOG.error("Database error dispatching OpenAPI request", e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final XPathException e) {
            LOG.error("XQuery error dispatching OpenAPI request", e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final org.exist.security.PermissionDeniedException e) {
            LOG.error("Permission denied dispatching OpenAPI request", e);
            writeJsonError(response, HttpServletResponse.SC_FORBIDDEN, e.getMessage());
        }
    }

    /**
     * Dispatch a matched route to its XQuery handler function.
     */
    private void dispatchToHandler(final DBBroker broker, final HttpServletRequest request,
                                    final HttpServletResponse response,
                                    final OpenApiServiceRegistry.RouteMatch match)
            throws XPathException, IOException, EXistException, org.exist.security.PermissionDeniedException {

        final OpenApiServiceRegistry.Route route = match.route();
        final String operationId = route.operationId();
        final String collectionPath = match.collectionPath();

        // Build the $request map (compatible with Roaster's format)
        final MapType requestMap = buildRequestMap(broker, request, match);

        // Build XQuery that resolves the operationId and calls the function
        // The operationId is expected to be in prefix:local-name format
        final String xquery = buildHandlerXQuery(operationId, collectionPath);

        // Compile and execute
        final XQuery xqueryService = broker.getBrokerPool().getXQueryService();
        final XQueryContext context = new XQueryContext(broker.getBrokerPool());
        context.setModuleLoadPath(XmldbURI.EMBEDDED_SERVER_URI_PREFIX + collectionPath + "/modules");

        // Declare the $request external variable
        context.declareVariable("request", requestMap);

        final StringSource source = new StringSource(xquery);
        final CompiledXQuery compiled;
        try {
            compiled = xqueryService.compile(context, source);
        } catch (final IOException e) {
            throw new XPathException((Expression) null, "Failed to compile handler for " + operationId + ": " + e.getMessage());
        }

        final Properties outputProperties = new Properties();
        final Sequence result;
        try {
            result = xqueryService.execute(broker, compiled, null, outputProperties);
        } finally {
            context.runCleanupTasks();
        }

        // Serialize the result
        serializeResult(broker, response, result, route, outputProperties);
    }

    /**
     * Build the XQuery source that resolves an operationId to a function and calls it.
     * The operationId format is "prefix:local-name" (e.g., "hello:greet").
     * The handler module is expected to be in the collection's modules/ directory.
     */
    private String buildHandlerXQuery(final String operationId, final String collectionPath) {
        // Parse prefix:local-name
        final int colonIdx = operationId.indexOf(':');
        if (colonIdx < 0) {
            // No prefix — treat as a local function name
            return "declare variable $request external;\n" +
                    "let $fn := function-lookup(xs:QName('" + operationId + "'), 1)\n" +
                    "return if (exists($fn)) then $fn($request) else error(xs:QName('err:HANDLER'), 'Handler not found: " + operationId + "')";
        }

        final String prefix = operationId.substring(0, colonIdx);
        final String localName = operationId.substring(colonIdx + 1);

        // Scan for XQuery modules in the collection to find the right namespace
        // For the PoC, we use a convention: the module file is named {prefix}.xqm
        // and stored in the modules/ subdirectory
        return "xquery version \"3.1\";\n" +
                "import module namespace " + prefix + " = \"http://exist-db.org/apps/" + prefix + "\" " +
                "at \"" + prefix + ".xqm\";\n" +
                "declare variable $request external;\n" +
                prefix + ":" + localName + "($request)";
    }

    /**
     * Build the request map passed to handler functions.
     * Shape is compatible with Roaster's $request map.
     */
    private MapType buildRequestMap(final DBBroker broker, final HttpServletRequest request,
                                     final OpenApiServiceRegistry.RouteMatch match)
            throws XPathException {

        final XQueryContext tempContext = new XQueryContext(broker.getBrokerPool());
        final MapType requestMap = new MapType(tempContext);

        // Method
        requestMap.add(new StringValue("method"), new StringValue(request.getMethod().toLowerCase()));

        // Path
        requestMap.add(new StringValue("path"), new StringValue(request.getRequestURI()));

        // Pattern
        requestMap.add(new StringValue("pattern"), new StringValue(match.route().pathPattern()));

        // Path parameters
        final MapType paramsMap = new MapType(tempContext);
        for (final Map.Entry<String, String> param : match.pathParams().entrySet()) {
            paramsMap.add(new StringValue(param.getKey()), new StringValue(param.getValue()));
        }
        // Also add query parameters
        for (final Map.Entry<String, String[]> param : request.getParameterMap().entrySet()) {
            if (param.getValue().length > 0) {
                paramsMap.add(new StringValue(param.getKey()), new StringValue(param.getValue()[0]));
            }
        }
        requestMap.add(new StringValue("parameters"), paramsMap);

        // Request body (for POST/PUT/PATCH)
        final String contentType = request.getContentType();
        if (contentType != null && request.getContentLength() > 0) {
            try {
                final String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                if (contentType.contains("json")) {
                    // Store raw JSON string — handler can parse with parse-json()
                    requestMap.add(new StringValue("body"), new StringValue(body));
                } else {
                    requestMap.add(new StringValue("body"), new StringValue(body));
                }
            } catch (final IOException e) {
                LOG.debug("Failed to read request body", e);
            }
        }

        return requestMap;
    }

    /**
     * Serialize the XQuery result to the HTTP response.
     */
    private void serializeResult(final DBBroker broker, final HttpServletResponse response,
                                  final Sequence result,
                                  final OpenApiServiceRegistry.Route route,
                                  final Properties outputProperties) throws IOException {

        if (result.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            return;
        }

        // Check if the result is a Roaster-style response map (with "code" and/or "body" keys)
        if (result.getItemCount() == 1 && result.itemAt(0).getType() == Type.MAP_ITEM) {
            try {
                final MapType responseMap = (MapType) result.itemAt(0);
                // Only treat as response map if it has "code" or "body" keys
                final Sequence codeSeq = responseMap.get(new StringValue("code"));
                final Sequence bodySeq = responseMap.get(new StringValue("body"));
                if ((codeSeq != null && !codeSeq.isEmpty()) || (bodySeq != null && !bodySeq.isEmpty())) {
                    serializeResponseMap(broker, response, responseMap, outputProperties);
                    return;
                }
            } catch (final XPathException e) {
                LOG.debug("Failed to interpret result as response map, falling back to default serialization", e);
            }
        }

        // Default: serialize as JSON using the adaptive/JSON serializer
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.setStatus(HttpServletResponse.SC_OK);

        outputProperties.setProperty("method", "json");
        outputProperties.setProperty("media-type", "application/json");

        try (final OutputStream os = response.getOutputStream();
             final OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
             final PrintWriter pw = new PrintWriter(writer)) {
            final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, pw);
            serializer.serialize(result);
            pw.flush();
        } catch (final Exception e) {
            LOG.error("Failed to serialize result", e);
            writeJsonError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Serialization error: " + e.getMessage());
        }
    }

    /**
     * Handle a response map returned by the handler.
     * The map may contain keys: "code" (integer), "type" (media-type string),
     * "body" (the response body), "headers" (map of header name → value).
     * This is compatible with Roaster's roaster:response() format.
     */
    private void serializeResponseMap(final DBBroker broker, final HttpServletResponse response,
                                       final MapType responseMap, final Properties outputProperties)
            throws XPathException, IOException {

        // Status code
        int statusCode = HttpServletResponse.SC_OK;
        final Sequence codeSeq = responseMap.get(new StringValue("code"));
        if (codeSeq != null && !codeSeq.isEmpty()) {
            statusCode = ((IntegerValue) codeSeq.itemAt(0)).getInt();
        }

        // Content type
        String contentType = "application/json";
        final Sequence typeSeq = responseMap.get(new StringValue("type"));
        if (typeSeq != null && !typeSeq.isEmpty()) {
            contentType = typeSeq.itemAt(0).getStringValue();
        }

        // Headers (simplified for PoC — skip custom headers from response map)

        // Body
        final Sequence bodySeq = responseMap.get(new StringValue("body"));

        response.setContentType(contentType);
        response.setCharacterEncoding("UTF-8");
        response.setStatus(statusCode);

        if (bodySeq != null && !bodySeq.isEmpty()) {
            try (final OutputStream os = response.getOutputStream();
                 final OutputStreamWriter writer = new OutputStreamWriter(os, StandardCharsets.UTF_8);
                 final PrintWriter pw = new PrintWriter(writer)) {
                if (contentType.contains("json")) {
                    outputProperties.setProperty("method", "json");
                    outputProperties.setProperty("media-type", "application/json");
                } else if (contentType.contains("xml")) {
                    outputProperties.setProperty("method", "xml");
                } else if (contentType.contains("html")) {
                    outputProperties.setProperty("method", "html5");
                }
                final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, pw);
                serializer.serialize(bodySeq);
            } catch (final Exception e) {
                LOG.error("Failed to serialize response body", e);
            }
        }
    }

    private void handleOptions(final HttpServletRequest request, final HttpServletResponse response) {
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, PATCH, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type, Authorization");
        response.setStatus(HttpServletResponse.SC_OK);
    }

    private void writeJsonError(final HttpServletResponse response, final int status, final String message)
            throws IOException {
        response.setContentType("application/json");
        response.setStatus(status);
        try (final OutputStream os = response.getOutputStream()) {
            final com.fasterxml.jackson.core.JsonFactory factory = new com.fasterxml.jackson.core.JsonFactory();
            try (final JsonGenerator gen = factory.createGenerator(os)) {
                gen.writeStartObject();
                gen.writeStringField("status", "error");
                gen.writeStringField("message", message);
                gen.writeEndObject();
            }
        }
    }
}
