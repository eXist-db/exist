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

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * Binds HTTP request data to XQuery function parameters based on
 * RESTXQ annotations. Handles path variables, query parameters,
 * form parameters, header parameters, cookie parameters, and request body.
 *
 * <p>Parameter values are automatically cast to the declared XQuery
 * function parameter types where possible.</p>
 */
public class ParameterBinder {

    private static final Logger LOG = LogManager.getLogger(ParameterBinder.class);

    /**
     * Binds all available request data to function arguments according to
     * the route's parameter annotations.
     *
     * @param context the XQuery context
     * @param route the matched route
     * @param request the HTTP request
     * @param requestPath the matched request path (after prefix stripping)
     * @param argTypes the function's declared parameter types
     * @return array of Sequence values to pass as function arguments
     */
    public static Sequence[] bind(final XQueryContext context,
                                  final Route route,
                                  final HttpServletRequest request,
                                  final String requestPath,
                                  final SequenceType[] argTypes) throws XPathException {

        if (argTypes == null || argTypes.length == 0) {
            return new Sequence[0];
        }

        // Build a map of variable name → value from all sources
        final Map<String, Sequence> bindings = new LinkedHashMap<>();

        // 1. Path template variables
        final Map<String, String> pathVars = route.getPathMatcher().extractVariables(requestPath);
        for (final Map.Entry<String, String> entry : pathVars.entrySet()) {
            bindings.put(entry.getKey(), new StringValue(entry.getValue()));
        }

        // 2. Query parameters
        bindParams(route.getQueryParams(), request.getParameterMap(), bindings);

        // 3. Form parameters (only for POST with form content type)
        if ("POST".equalsIgnoreCase(request.getMethod())
                && request.getContentType() != null
                && request.getContentType().startsWith("application/x-www-form-urlencoded")) {
            bindParams(route.getFormParams(), request.getParameterMap(), bindings);
        }

        // 4. Header parameters
        for (final Map.Entry<String, Route.ParamBinding> entry : route.getHeaderParams().entrySet()) {
            final String headerName = entry.getValue().getParamName();
            final String varName = entry.getValue().getVariableName();
            final String headerValue = request.getHeader(headerName);
            if (headerValue != null) {
                bindings.put(varName, new StringValue(headerValue));
            } else if (entry.getValue().getDefaultValue() != null) {
                bindings.put(varName, new StringValue(entry.getValue().getDefaultValue()));
            }
        }

        // 5. Cookie parameters
        for (final Map.Entry<String, Route.ParamBinding> entry : route.getCookieParams().entrySet()) {
            final String cookieName = entry.getValue().getParamName();
            final String varName = entry.getValue().getVariableName();
            String cookieValue = null;
            final Cookie[] cookies = request.getCookies();
            if (cookies != null) {
                for (final Cookie cookie : cookies) {
                    if (cookieName.equals(cookie.getName())) {
                        cookieValue = cookie.getValue();
                        break;
                    }
                }
            }
            if (cookieValue != null) {
                bindings.put(varName, new StringValue(cookieValue));
            } else if (entry.getValue().getDefaultValue() != null) {
                bindings.put(varName, new StringValue(entry.getValue().getDefaultValue()));
            }
        }

        // 6. Request body (for POST/PUT/PATCH with body variable binding)
        if (route.getBodyVariable() != null) {
            try {
                final Sequence bodyValue = readRequestBody(context, request);
                if (bodyValue != null) {
                    bindings.put(route.getBodyVariable(), bodyValue);
                }
            } catch (final IOException e) {
                throw new XPathException((org.exist.xquery.Expression) null,
                        "Failed to read request body: " + e.getMessage());
            }
        }

        // Map bindings to function argument positions
        final Sequence[] args = new Sequence[argTypes.length];
        for (int i = 0; i < argTypes.length; i++) {
            final FunctionParameterSequenceType paramType = (FunctionParameterSequenceType) argTypes[i];
            final String paramName = paramType.getAttributeName();

            final Sequence value = bindings.get(paramName);
            if (value != null) {
                args[i] = castValue(value, paramType.getPrimaryType());
            } else {
                args[i] = Sequence.EMPTY_SEQUENCE;
            }
        }

        return args;
    }

    /**
     * Binds named parameters from the request parameter map using the
     * %rest:*-param annotation bindings.
     */
    private static void bindParams(final Map<String, Route.ParamBinding> paramBindings,
                                   final Map<String, String[]> requestParams,
                                   final Map<String, Sequence> bindings) {
        for (final Map.Entry<String, Route.ParamBinding> entry : paramBindings.entrySet()) {
            final Route.ParamBinding binding = entry.getValue();
            final String[] values = requestParams.get(binding.getParamName());
            if (values != null && values.length > 0) {
                if (values.length == 1) {
                    bindings.put(binding.getVariableName(), new UntypedAtomicValue(values[0]));
                } else {
                    final ValueSequence seq = new ValueSequence();
                    for (final String v : values) {
                        seq.add(new UntypedAtomicValue(v));
                    }
                    bindings.put(binding.getVariableName(), seq);
                }
            } else if (!binding.getDefaultValues().isEmpty()) {
                final java.util.List<String> defaults = binding.getDefaultValues();
                if (defaults.size() == 1) {
                    bindings.put(binding.getVariableName(), new UntypedAtomicValue(defaults.get(0)));
                } else {
                    final ValueSequence seq = new ValueSequence();
                    for (final String dv : defaults) {
                        seq.add(new UntypedAtomicValue(dv));
                    }
                    bindings.put(binding.getVariableName(), seq);
                }
            }
        }
    }

    /**
     * Reads the request body and returns an appropriate XQuery value
     * based on the Content-Type.
     */
    private static Sequence readRequestBody(final XQueryContext context,
                                            final HttpServletRequest request)
            throws IOException, XPathException {
        final String contentType = request.getContentType();
        if (contentType == null) {
            return null;
        }

        final String baseType = contentType.contains(";")
                ? contentType.substring(0, contentType.indexOf(';')).trim()
                : contentType.trim();

        try (final InputStream is = request.getInputStream()) {
            if ("application/xml".equals(baseType) || "text/xml".equals(baseType)
                    || baseType.endsWith("+xml")) {
                return parseXmlBody(context, is);
            } else if ("application/json".equals(baseType) || baseType.endsWith("+json")) {
                // Return JSON as string for now; full JSON parsing comes in Phase 2
                return new StringValue(new String(is.readAllBytes(), request.getCharacterEncoding() != null
                        ? request.getCharacterEncoding() : "UTF-8"));
            } else if (baseType.startsWith("text/")) {
                return new StringValue(new String(is.readAllBytes(), request.getCharacterEncoding() != null
                        ? request.getCharacterEncoding() : "UTF-8"));
            } else {
                // Binary body
                return BinaryValueFromInputStream.getInstance(context,
                        new Base64BinaryValueType(), is, null);
            }
        }
    }

    private static Sequence parseXmlBody(final XQueryContext context,
                                         final InputStream is) throws XPathException {
        try {
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            final SAXAdapter adapter = new SAXAdapter(context);
            reader.setContentHandler(adapter);
            reader.parse(new InputSource(is));
            return adapter.getDocument();
        } catch (final Exception e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    "Failed to parse XML request body: " + e.getMessage());
        }
    }

    /**
     * Casts a string value to the target XQuery type if needed.
     */
    private static Sequence castValue(final Sequence value, final int targetType) throws XPathException {
        if (targetType == Type.ITEM || targetType == Type.STRING || targetType == Type.ANY_TYPE) {
            return value;
        }

        // If it's already the right type, return as-is
        if (value.hasOne()) {
            final Item item = value.itemAt(0);
            if (item.getType() == targetType || Type.subTypeOf(item.getType(), targetType)) {
                return value;
            }
            // Try automatic casting
            if (item instanceof AtomicValue) {
                return ((AtomicValue) item).convertTo(targetType);
            }
        }

        return value;
    }
}
