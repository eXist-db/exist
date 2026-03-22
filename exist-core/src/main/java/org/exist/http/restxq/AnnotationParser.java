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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.SequenceType;

import java.util.*;

/**
 * Parses RESTXQ annotations ({@code %rest:*}, {@code %output:*}) from
 * compiled XQuery function signatures and produces {@link Route} objects.
 *
 * <p>This replaces the EXQuery library's annotation processing with a
 * native implementation that works directly with eXist's type system.</p>
 */
public class AnnotationParser {

    private static final Logger LOG = LogManager.getLogger(AnnotationParser.class);

    private static final Set<String> HTTP_METHOD_ANNOTATIONS = Set.of(
            "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"
    );

    /**
     * Result of parsing a module: both path routes and error handlers.
     */
    public static class ParseResult {
        public final List<Route> routes;
        public final List<ErrorRoute> errorRoutes;

        public ParseResult(List<Route> routes, List<ErrorRoute> errorRoutes) {
            this.routes = routes;
            this.errorRoutes = errorRoutes;
        }
    }

    /**
     * Inspects all local functions in the given compiled XQuery and returns
     * Routes for any functions that have RESTXQ annotations.
     *
     * @param compiled the compiled XQuery
     * @param moduleUri the database URI of the XQuery module
     * @return list of routes found (may be empty)
     */
    public static List<Route> parseModule(final CompiledXQuery compiled, final String moduleUri)
            throws RestXqAnnotationException {
        return parseModuleFull(compiled, moduleUri).routes;
    }

    /**
     * Inspects all local functions and returns both path routes and error handlers.
     */
    public static ParseResult parseModuleFull(final CompiledXQuery compiled, final String moduleUri)
            throws RestXqAnnotationException {
        final List<Route> routes = new ArrayList<>();
        final List<ErrorRoute> errorRoutes = new ArrayList<>();
        final Iterator<UserDefinedFunction> functions = compiled.getContext().localFunctions();

        while (functions.hasNext()) {
            final UserDefinedFunction function = functions.next();
            final Route route = parseFunction(function, moduleUri);
            if (route != null) {
                routes.add(route);
                LOG.debug("Registered RESTXQ route: {}", route);
            }
            final ErrorRoute errorRoute = parseErrorFunction(function, moduleUri);
            if (errorRoute != null) {
                // Check for duplicate error handlers in the same module
                for (final ErrorRoute existing : errorRoutes) {
                    for (final ErrorRoute.ErrorCode newCode : errorRoute.getErrorCodes()) {
                        for (final ErrorRoute.ErrorCode existingCode : existing.getErrorCodes()) {
                            if (newCode.toString().equals(existingCode.toString())) {
                                throw new RestXqAnnotationException(
                                        "Duplicate error handler for " + newCode
                                                + " in module " + moduleUri);
                            }
                        }
                    }
                }
                errorRoutes.add(errorRoute);
                LOG.debug("Registered RESTXQ error handler: {}", errorRoute.getFunctionName());
            }
        }

        return new ParseResult(routes, errorRoutes);
    }

    private static final Set<String> KNOWN_OUTPUT_PARAMS = Set.of(
            "method", "media-type", "encoding", "indent", "omit-xml-declaration",
            "standalone", "version", "cdata-section-elements", "doctype-public",
            "doctype-system", "byte-order-mark", "escape-uri-attributes",
            "include-content-type", "normalization-form", "suppress-indentation",
            "undeclare-prefixes", "use-character-maps", "html-version",
            "item-separator", "json-node-output-method"
    );

    private static final Set<String> KNOWN_REST_ANNOTATIONS = Set.of(
            "path", "GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS",
            "method", "consumes", "produces",
            "query-param", "form-param", "header-param", "cookie-param",
            "error", "error-param", "single"
    );

    /**
     * Parses annotations from a single function. Returns null if the
     * function has no RESTXQ annotations. Throws on invalid annotations.
     */
    static Route parseFunction(final UserDefinedFunction function, final String moduleUri)
            throws RestXqAnnotationException {
        final Annotation[] annotations = function.getSignature().getAnnotations();
        if (annotations == null || annotations.length == 0) {
            return null;
        }

        // Check if this function has any RESTXQ annotations at all
        boolean hasRestAnnotation = false;
        boolean hasOutputAnnotation = false;
        boolean hasInputAnnotation = false;
        for (final Annotation a : annotations) {
            final String ns = a.getName().getNamespaceURI();
            if (RestXqNamespaces.REST_NS.equals(ns)) {
                hasRestAnnotation = true;
            } else if (RestXqNamespaces.OUTPUT_NS.equals(ns)) {
                hasOutputAnnotation = true;
            } else if (RestXqNamespaces.INPUT_NS.equals(ns)) {
                hasInputAnnotation = true;
            }
        }
        if (!hasRestAnnotation && !hasOutputAnnotation && !hasInputAnnotation) {
            return null;
        }

        String pathTemplate = null;
        int pathAnnotationCount = 0;
        final Set<String> methods = new LinkedHashSet<>();
        final Set<String> rawMethodAnnotations = new LinkedHashSet<>();
        boolean hasMethodAnnotation = false;
        final Properties outputProperties = new Properties();
        final List<String> consumes = new ArrayList<>();
        final List<String> produces = new ArrayList<>();
        final Map<String, Route.ParamBinding> queryParams = new LinkedHashMap<>();
        final Map<String, Route.ParamBinding> formParams = new LinkedHashMap<>();
        final Map<String, Route.ParamBinding> headerParams = new LinkedHashMap<>();
        final Map<String, Route.ParamBinding> cookieParams = new LinkedHashMap<>();
        String bodyVariable = null;
        final Properties inputOptions = new Properties();
        final String funcName = function.getSignature().getName().getLocalPart();

        for (final Annotation annotation : annotations) {
            final QName name = annotation.getName();
            final String ns = name.getNamespaceURI();
            final String local = name.getLocalPart();
            final LiteralValue[] values = annotation.getValue();

            if (RestXqNamespaces.REST_NS.equals(ns)) {
                // Validate known annotation names
                if (!"error".equals(local) && !"error-param".equals(local)
                        && !KNOWN_REST_ANNOTATIONS.contains(local)) {
                    throw new RestXqAnnotationException(
                            "Unknown RESTXQ annotation %rest:" + local
                                    + " on function " + funcName);
                }

                if ("path".equals(local)) {
                    pathAnnotationCount++;
                    if (pathAnnotationCount > 1) {
                        throw new RestXqAnnotationException(
                                "Duplicate %rest:path annotation on function " + funcName);
                    }
                    if (values.length == 0) {
                        throw new RestXqAnnotationException(
                                "%rest:path requires a path argument on function " + funcName);
                    }
                    if (values.length > 1) {
                        throw new RestXqAnnotationException(
                                "%rest:path must have exactly one argument on function " + funcName);
                    }
                    pathTemplate = getLiteralString(values, 0);
                } else if (HTTP_METHOD_ANNOTATIONS.contains(local.toUpperCase(Locale.ROOT))) {
                    final String methodUpper = local.toUpperCase(Locale.ROOT);
                    if (!rawMethodAnnotations.add(local)) {
                        throw new RestXqAnnotationException(
                                "Duplicate %rest:" + local + " annotation on function " + funcName);
                    }
                    hasMethodAnnotation = true;
                    if (!methods.add(methodUpper)) {
                        throw new RestXqAnnotationException(
                                "Duplicate method " + methodUpper + " on function " + funcName);
                    }
                    if (values.length > 0) {
                        bodyVariable = extractVariableName(getLiteralString(values, 0));
                    }
                } else if ("method".equals(local)) {
                    hasMethodAnnotation = true;
                    if (values.length > 0) {
                        final String methodName = getLiteralString(values, 0).toUpperCase(Locale.ROOT);
                        // Check for duplicate method names (across all annotation types)
                        if (!methods.add(methodName)) {
                            throw new RestXqAnnotationException(
                                    "Duplicate method " + methodName + " on function " + funcName);
                        }
                    }
                    if (values.length > 1) {
                        bodyVariable = extractVariableName(getLiteralString(values, 1));
                    }
                } else if ("consumes".equals(local)) {
                    for (final LiteralValue v : values) {
                        consumes.add(literalToString(v));
                    }
                } else if ("produces".equals(local)) {
                    for (final LiteralValue v : values) {
                        produces.add(literalToString(v));
                    }
                } else if ("query-param".equals(local)) {
                    parseParamBinding(values, queryParams);
                } else if ("form-param".equals(local)) {
                    parseParamBinding(values, formParams);
                } else if ("header-param".equals(local)) {
                    parseParamBinding(values, headerParams);
                } else if ("cookie-param".equals(local)) {
                    parseParamBinding(values, cookieParams);
                }
            } else if (RestXqNamespaces.OUTPUT_NS.equals(ns)) {
                if (values.length == 0) {
                    throw new RestXqAnnotationException(
                            "%output:" + local + " requires a value on function " + funcName);
                }
                if (values.length > 1) {
                    throw new RestXqAnnotationException(
                            "%output:" + local + " must have exactly one value on function " + funcName);
                }
                // Validate known serialization parameter names
                if (!KNOWN_OUTPUT_PARAMS.contains(local)) {
                    throw new RestXqAnnotationException(
                            "Unknown serialization parameter %output:" + local + " on function " + funcName);
                }
                outputProperties.setProperty(local, getLiteralString(values, 0));
            } else if (RestXqNamespaces.INPUT_NS.equals(ns)) {
                // %input:json('lax=no'), %input:csv('header=yes'), %input:html('nons=true')
                // Parse key=value pairs from annotation values and store as input.type.key=value
                for (final LiteralValue v : values) {
                    final String optStr = literalToString(v);
                    if (optStr != null) {
                        parseInputOptions(local, optStr, inputOptions);
                    }
                }
            }
        }

        // If function has %rest:GET (or other method) but no %rest:path, it's an error
        if (hasRestAnnotation && pathTemplate == null) {
            // Only error if there's a method annotation without path — pure error handlers are OK
            if (hasMethodAnnotation) {
                throw new RestXqAnnotationException(
                        "Function " + funcName + " has HTTP method annotation but no %rest:path");
            }
            return null;
        }

        if (pathTemplate == null) {
            return null;
        }

        // Check for %rest:method conflicts with explicit method annotations
        for (final String m : methods) {
            if (rawMethodAnnotations.contains(m) || rawMethodAnnotations.contains(m.toLowerCase(Locale.ROOT))) {
                // Already handled above in the method parsing
            }
        }

        // If no explicit HTTP method annotation, default to GET
        if (methods.isEmpty()) {
            methods.add("GET");
        }

        // Validate: GET, HEAD, DELETE, OPTIONS should not have body variable
        if (bodyVariable != null) {
            final Set<String> noBodyMethods = Set.of("GET", "HEAD", "DELETE", "OPTIONS");
            for (final String m : methods) {
                if (noBodyMethods.contains(m)) {
                    throw new RestXqAnnotationException(
                            "HTTP method " + m + " must not have a body variable on function " + funcName);
                }
            }
        }

        // Parse and validate the path template
        final PathMatcher pathMatcher = PathMatcher.parse(pathTemplate);

        // Validate template variables against function parameters
        final SequenceType[] argTypes = function.getSignature().getArgumentTypes();
        final int arity = argTypes != null ? argTypes.length : 0;
        final List<String> templateVars = pathMatcher.getVarNames();

        // Collect all declared variable names from annotations
        final Set<String> annotationVars = new LinkedHashSet<>(templateVars);
        for (final Route.ParamBinding b : queryParams.values()) {
            annotationVars.add(b.getVariableName());
        }
        for (final Route.ParamBinding b : formParams.values()) {
            annotationVars.add(b.getVariableName());
        }
        for (final Route.ParamBinding b : headerParams.values()) {
            annotationVars.add(b.getVariableName());
        }
        for (final Route.ParamBinding b : cookieParams.values()) {
            annotationVars.add(b.getVariableName());
        }
        if (bodyVariable != null) {
            annotationVars.add(bodyVariable);
        }

        // Check that each template variable has a corresponding function parameter
        if (argTypes != null) {
            final Set<String> paramNames = new LinkedHashSet<>();
            for (final SequenceType st : argTypes) {
                if (st instanceof FunctionParameterSequenceType fpst) {
                    paramNames.add(fpst.getAttributeName());
                }
            }

            for (final String tv : templateVars) {
                if (!paramNames.contains(tv)) {
                    throw new RestXqAnnotationException(
                            "Path template variable {$" + tv + "} has no corresponding function parameter "
                                    + "on function " + funcName);
                }
            }

            // Check that every function parameter is bound by some annotation
            for (final String pn : paramNames) {
                if (!annotationVars.contains(pn)) {
                    throw new RestXqAnnotationException(
                            "Function parameter $" + pn + " is not bound by any annotation "
                                    + "on function " + funcName);
                }
            }

            // Check that every annotation variable has a corresponding function parameter
            for (final String av : annotationVars) {
                if (!paramNames.contains(av)) {
                    throw new RestXqAnnotationException(
                            "Annotation variable $" + av + " has no corresponding function parameter "
                                    + "on function " + funcName);
                }
            }
        } else if (!templateVars.isEmpty()) {
            throw new RestXqAnnotationException(
                    "Path template has variables but function " + funcName + " has no parameters");
        }

        return new Route(
                moduleUri,
                function.getSignature().getName(),
                arity,
                pathMatcher,
                Collections.unmodifiableSet(methods),
                outputProperties,
                Collections.unmodifiableList(consumes),
                Collections.unmodifiableList(produces),
                Collections.unmodifiableMap(queryParams),
                Collections.unmodifiableMap(formParams),
                Collections.unmodifiableMap(headerParams),
                Collections.unmodifiableMap(cookieParams),
                bodyVariable,
                inputOptions
        );
    }

    /**
     * Parses a %rest:*-param annotation: ("paramName", "{$varName}", default?)
     */
    private static void parseParamBinding(final LiteralValue[] values,
                                          final Map<String, Route.ParamBinding> target)
            throws RestXqAnnotationException {
        if (values.length < 2) {
            return;
        }
        // First arg must be a string (the external parameter name)
        final String paramName = getLiteralString(values, 0);
        if (paramName == null) {
            throw new RestXqAnnotationException("Parameter name must be a string");
        }
        // Validate first arg is string type (not integer etc.)
        try {
            if (values[0].getValue().getType() != org.exist.xquery.value.Type.STRING) {
                throw new RestXqAnnotationException(
                        "Parameter name must be a string, got: " + values[0].getValue().getStringValue());
            }
        } catch (final XPathException e) {
            // ignore type check failures
        }

        // Second arg must use {$var} template syntax
        final String varTemplate = getLiteralString(values, 1);
        if (varTemplate == null || !varTemplate.contains("{") || !varTemplate.contains("$")) {
            throw new RestXqAnnotationException(
                    "Parameter variable must use {$var} template syntax, got: " + varTemplate);
        }
        final String varName = extractVariableName(varTemplate);
        if (varName == null) {
            throw new RestXqAnnotationException(
                    "Invalid variable template: " + varTemplate);
        }

        // Check for duplicate param bindings
        if (target.containsKey(paramName)) {
            throw new RestXqAnnotationException(
                    "Duplicate parameter binding for '" + paramName + "'");
        }

        final List<String> defaults = new ArrayList<>();
        for (int i = 2; i < values.length; i++) {
            final String dv = getLiteralString(values, i);
            if (dv != null) {
                defaults.add(dv);
            }
        }
        target.put(paramName, new Route.ParamBinding(paramName, varName, defaults));
    }

    /**
     * Extracts a variable name from "{$varName}" syntax.
     * Returns the name without the $ prefix and curly braces.
     */
    static String extractVariableName(final String spec) {
        if (spec == null) {
            return null;
        }
        String s = spec.trim();
        if (s.startsWith("{") && s.endsWith("}")) {
            s = s.substring(1, s.length() - 1).trim();
        }
        if (s.startsWith("$")) {
            s = s.substring(1);
        }
        return s.isEmpty() ? null : s;
    }

    private static String getLiteralString(final LiteralValue[] values, final int index) {
        if (index >= values.length) {
            return null;
        }
        return literalToString(values[index]);
    }

    /**
     * Parses input option strings like "lax=no" or "header=yes" from
     * %input:json, %input:csv, %input:html annotations.
     * Stores as "input.{type}.{key}={value}" in the options Properties.
     */
    private static void parseInputOptions(final String type, final String optStr,
                                          final Properties options) {
        // Option format: "key=value" or "key=value,key2=value2"
        for (final String part : optStr.split(",")) {
            final String trimmed = part.trim();
            final int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                final String key = trimmed.substring(0, eqIdx).trim();
                final String value = trimmed.substring(eqIdx + 1).trim();
                options.setProperty("input." + type + "." + key, value);
            }
        }
    }

    private static String literalToString(final LiteralValue value) {
        try {
            return value.getValue().getStringValue();
        } catch (final XPathException e) {
            LOG.warn("Failed to get string value from annotation literal", e);
            return null;
        }
    }

    /**
     * Parses %rest:error annotations from a function.
     * Returns null if the function has no %rest:error annotation.
     */
    static ErrorRoute parseErrorFunction(final UserDefinedFunction function, final String moduleUri)
            throws RestXqAnnotationException {
        final Annotation[] annotations = function.getSignature().getAnnotations();
        if (annotations == null || annotations.length == 0) {
            return null;
        }

        final List<ErrorRoute.ErrorCode> errorCodes = new ArrayList<>();
        final Map<String, Route.ParamBinding> errorParams = new LinkedHashMap<>();

        for (final Annotation annotation : annotations) {
            final QName name = annotation.getName();
            if (!RestXqNamespaces.REST_NS.equals(name.getNamespaceURI())) {
                continue;
            }
            final String local = name.getLocalPart();
            final LiteralValue[] values = annotation.getValue();

            if ("error".equals(local)) {
                for (final LiteralValue value : values) {
                    final String codeStr = literalToString(value);
                    if (codeStr != null) {
                        final ErrorRoute.ErrorCode code = parseErrorCode(codeStr, function);
                        if (code == null) {
                            throw new RestXqAnnotationException(
                                    "Invalid error code: " + codeStr);
                        }
                        // Check for duplicate error codes
                        for (final ErrorRoute.ErrorCode existing : errorCodes) {
                            if (existing.toString().equals(code.toString())) {
                                throw new RestXqAnnotationException(
                                        "Duplicate error code: " + codeStr);
                            }
                        }
                        errorCodes.add(code);
                    }
                }
            } else if ("error-param".equals(local)) {
                parseParamBinding(values, errorParams);
            }
        }

        if (errorCodes.isEmpty()) {
            return null;
        }

        final SequenceType[] argTypes = function.getSignature().getArgumentTypes();
        final int arity = argTypes != null ? argTypes.length : 0;

        return new ErrorRoute(moduleUri, function.getSignature().getName(), arity,
                errorCodes, errorParams);
    }

    /**
     * Parses an error code pattern string into an ErrorCode.
     * Supports: "*", "prefix:*", "*:local", "prefix:local", "Q{uri}local", "Q{uri}*"
     */
    private static ErrorRoute.ErrorCode parseErrorCode(final String codeStr,
                                                        final UserDefinedFunction function)
            throws RestXqAnnotationException {
        if ("*".equals(codeStr)) {
            return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.CATCH_ALL, null, null);
        }

        // Q{uri}local or Q{uri}*
        if (codeStr.startsWith("Q{")) {
            final int closeBrace = codeStr.indexOf('}');
            if (closeBrace > 2) {
                final String uri = codeStr.substring(2, closeBrace);
                final String localPart = codeStr.substring(closeBrace + 1);
                if (localPart.isEmpty()) {
                    throw new RestXqAnnotationException(
                            "Invalid EQName in %rest:error — missing local part: " + codeStr);
                }
                if ("*".equals(localPart)) {
                    return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.NAMESPACE_WILD, uri, null);
                } else {
                    validateNCName(localPart, codeStr);
                    return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.EXACT, uri, localPart);
                }
            }
            throw new RestXqAnnotationException(
                    "Invalid EQName syntax in %rest:error: " + codeStr);
        }

        // prefix:* or *:local or prefix:local
        final int colonIdx = codeStr.indexOf(':');
        if (colonIdx > 0) {
            final String prefix = codeStr.substring(0, colonIdx);
            final String localPart = codeStr.substring(colonIdx + 1);

            if ("*".equals(prefix)) {
                // *:local
                validateNCName(localPart, codeStr);
                return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.LOCAL_WILD, null, localPart);
            } else if ("*".equals(localPart)) {
                // prefix:* — resolve prefix to namespace URI (use prefix as fallback)
                final String nsUri = resolvePrefix(prefix, function);
                return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.NAMESPACE_WILD,
                        (nsUri != null && !nsUri.isEmpty()) ? nsUri : prefix, null);
            } else {
                // prefix:local — resolve prefix to namespace URI
                validateNCName(localPart, codeStr);
                final String nsUri = resolvePrefix(prefix, function);
                return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.EXACT,
                        (nsUri != null && !nsUri.isEmpty()) ? nsUri : prefix, localPart);
            }
        }

        // Bare name — no namespace, validate as NCName
        validateNCName(codeStr, codeStr);
        return new ErrorRoute.ErrorCode(ErrorRoute.MatchType.EXACT, "", codeStr);
    }

    /**
     * Validates that a string is a valid XML NCName (no colons, no spaces,
     * starts with letter or underscore).
     */
    private static void validateNCName(final String name, final String context)
            throws RestXqAnnotationException {
        if (name == null || name.isEmpty()) {
            throw new RestXqAnnotationException(
                    "Empty name in %rest:error: " + context);
        }
        if (name.contains(" ")) {
            throw new RestXqAnnotationException(
                    "Invalid name (contains spaces) in %rest:error: " + context);
        }
        final char first = name.charAt(0);
        if (!Character.isLetter(first) && first != '_') {
            throw new RestXqAnnotationException(
                    "Invalid name (must start with letter or _) in %rest:error: " + context);
        }
    }

    /**
     * Resolves a namespace prefix using the function's XQuery context.
     */
    private static String resolvePrefix(final String prefix, final UserDefinedFunction function) {
        return function.getContext().getURIForPrefix(prefix);
    }
}
