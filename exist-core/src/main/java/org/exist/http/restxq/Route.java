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

import org.exist.dom.QName;

import javax.xml.transform.OutputKeys;
import java.util.*;

/**
 * Represents a single RESTXQ route: the combination of a path pattern,
 * HTTP method(s), a function reference, and serialization options parsed
 * from the function's annotations.
 *
 * <p>A Route is built by {@link AnnotationParser} from a compiled XQuery
 * function's annotations, and consumed by {@link NativeRestXqServlet} for
 * dispatch.</p>
 */
public class Route implements Comparable<Route> {

    /** The XQuery source URI (database path) containing this function. */
    private final String moduleUri;

    /** The QName of the annotated XQuery function. */
    private final QName functionName;

    /** The arity of the function. */
    private final int arity;

    /** The compiled path matcher for %rest:path. */
    private final PathMatcher pathMatcher;

    /** The HTTP methods this route handles (GET, POST, etc.). */
    private final Set<String> methods;

    /** Serialization properties from %output:* annotations. */
    private final Properties outputProperties;

    /** %rest:consumes media types. */
    private final List<String> consumes;

    /** %rest:produces media types. */
    private final List<String> produces;

    /** %rest:query-param bindings: param name → variable name. */
    private final Map<String, ParamBinding> queryParams;

    /** %rest:form-param bindings. */
    private final Map<String, ParamBinding> formParams;

    /** %rest:header-param bindings. */
    private final Map<String, ParamBinding> headerParams;

    /** %rest:cookie-param bindings. */
    private final Map<String, ParamBinding> cookieParams;

    /** The variable name for POST/PUT body binding, or null. */
    private final String bodyVariable;

    Route(final String moduleUri, final QName functionName, final int arity,
          final PathMatcher pathMatcher, final Set<String> methods,
          final Properties outputProperties,
          final List<String> consumes, final List<String> produces,
          final Map<String, ParamBinding> queryParams,
          final Map<String, ParamBinding> formParams,
          final Map<String, ParamBinding> headerParams,
          final Map<String, ParamBinding> cookieParams,
          final String bodyVariable) {
        this.moduleUri = moduleUri;
        this.functionName = functionName;
        this.arity = arity;
        this.pathMatcher = pathMatcher;
        this.methods = methods;
        this.outputProperties = outputProperties;
        this.consumes = consumes;
        this.produces = produces;
        this.queryParams = queryParams;
        this.formParams = formParams;
        this.headerParams = headerParams;
        this.cookieParams = cookieParams;
        this.bodyVariable = bodyVariable;
    }

    public String getModuleUri() {
        return moduleUri;
    }

    public QName getFunctionName() {
        return functionName;
    }

    public int getArity() {
        return arity;
    }

    public PathMatcher getPathMatcher() {
        return pathMatcher;
    }

    public Set<String> getMethods() {
        return methods;
    }

    public Properties getOutputProperties() {
        return outputProperties;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public List<String> getProduces() {
        return produces;
    }

    public Map<String, ParamBinding> getQueryParams() {
        return queryParams;
    }

    public Map<String, ParamBinding> getFormParams() {
        return formParams;
    }

    public Map<String, ParamBinding> getHeaderParams() {
        return headerParams;
    }

    public Map<String, ParamBinding> getCookieParams() {
        return cookieParams;
    }

    public String getBodyVariable() {
        return bodyVariable;
    }

    /**
     * Tests whether this route matches the given HTTP method and request path.
     */
    public boolean matches(final String method, final String requestPath) {
        return methods.contains(method.toUpperCase(Locale.ROOT))
                && pathMatcher.matches(requestPath);
    }

    /**
     * Tests whether this route's consumes constraint is satisfied by the
     * given Content-Type (or if no constraint is declared).
     */
    public boolean matchesConsumes(final String contentType) {
        if (consumes.isEmpty()) {
            return true;
        }
        if (contentType == null || contentType.isEmpty()) {
            // No content type in request — only match if consumes includes wildcard
            for (final String consume : consumes) {
                if ("*/*".equals(consume.trim())) {
                    return true;
                }
            }
            return false;
        }
        // Strip parameters (e.g., charset) for matching
        final String baseType = contentType.contains(";")
                ? contentType.substring(0, contentType.indexOf(';')).trim()
                : contentType.trim();
        for (final String consume : consumes) {
            if (mediaTypeMatches(baseType, consume)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether this route's produces constraint is satisfied by the
     * given Accept header (or if no constraint is declared).
     */
    public boolean matchesProduces(final String acceptHeader) {
        if (produces.isEmpty()) {
            return true;
        }
        if (acceptHeader == null || acceptHeader.isEmpty()) {
            return true;
        }
        for (final String produce : produces) {
            // Strip qs parameter for matching
            final String baseProduces = produce.contains(";")
                    ? produce.substring(0, produce.indexOf(';')).trim()
                    : produce.trim();
            for (final String accept : acceptHeader.split(",")) {
                final String baseAccept = accept.contains(";")
                        ? accept.substring(0, accept.indexOf(';')).trim()
                        : accept.trim();
                if (mediaTypeMatches(baseAccept, baseProduces)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean mediaTypeMatches(final String actual, final String pattern) {
        if ("*/*".equals(pattern) || "*/*".equals(actual)) {
            return true;
        }
        if (actual.equalsIgnoreCase(pattern)) {
            return true;
        }
        // Check type/* wildcard
        final int slashActual = actual.indexOf('/');
        final int slashPattern = pattern.indexOf('/');
        if (slashActual > 0 && slashPattern > 0) {
            final String typeActual = actual.substring(0, slashActual);
            final String typePattern = pattern.substring(0, slashPattern);
            final String subtypePattern = pattern.substring(slashPattern + 1);
            if (typeActual.equalsIgnoreCase(typePattern) && "*".equals(subtypePattern)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns the Content-Type to set on the response, derived from
     * %output:media-type or %output:method annotations.
     */
    public String getResponseContentType() {
        final String mediaType = outputProperties.getProperty("media-type");
        if (mediaType != null) {
            return mediaType;
        }
        // Derive from method
        final String method = outputProperties.getProperty(OutputKeys.METHOD, "xml");
        return switch (method) {
            case "json" -> "application/json";
            case "html" -> "text/html";
            case "text" -> "text/plain";
            case "adaptive" -> "text/plain";
            default -> "application/xml";
        };
    }

    /**
     * Sort by path specificity (most specific first).
     */
    @Override
    public int compareTo(final Route other) {
        return this.pathMatcher.compareTo(other.pathMatcher);
    }

    @Override
    public String toString() {
        return methods + " " + pathMatcher.getTemplate() + " → " +
                functionName.getLocalPart() + "#" + arity +
                " [" + moduleUri + "]";
    }

    /**
     * A parameter binding from a %rest:*-param annotation.
     */
    public static class ParamBinding {
        private final String paramName;
        private final String variableName;
        private final List<String> defaultValues;

        public ParamBinding(final String paramName, final String variableName, final List<String> defaultValues) {
            this.paramName = paramName;
            this.variableName = variableName;
            this.defaultValues = defaultValues;
        }

        public String getParamName() {
            return paramName;
        }

        public String getVariableName() {
            return variableName;
        }

        public String getDefaultValue() {
            return defaultValues.isEmpty() ? null : defaultValues.get(0);
        }

        public List<String> getDefaultValues() {
            return defaultValues;
        }
    }
}
