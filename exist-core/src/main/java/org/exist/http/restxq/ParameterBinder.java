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
                final Sequence bodyValue = readRequestBody(context, request, route);
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
                                            final HttpServletRequest request,
                                            final Route route)
            throws IOException, XPathException {
        final String contentType = request.getContentType();
        if (contentType == null) {
            return null;
        }

        final String baseType = contentType.contains(";")
                ? contentType.substring(0, contentType.indexOf(';')).trim()
                : contentType.trim();

        // Extract content-type parameters (e.g., "lax=false" from "application/json;lax=false")
        final java.util.Properties ctParams = parseContentTypeParams(contentType);

        try (final InputStream is = request.getInputStream()) {
            if ("application/xml".equals(baseType) || "text/xml".equals(baseType)
                    || baseType.endsWith("+xml")) {
                return parseXmlBody(context, is);
            } else if ("application/json".equals(baseType) || baseType.endsWith("+json")) {
                final String encoding = request.getCharacterEncoding() != null
                        ? request.getCharacterEncoding() : "UTF-8";
                final String jsonStr = new String(is.readAllBytes(), encoding);
                // Determine lax mode: %input:json annotation > content-type param > default (no)
                final boolean lax = resolveJsonLax(route, ctParams);
                return parseJsonToXml(context, jsonStr, lax);
            } else if ("text/csv".equals(baseType)) {
                final String encoding = request.getCharacterEncoding() != null
                        ? request.getCharacterEncoding() : "UTF-8";
                final String csvStr = new String(is.readAllBytes(), encoding);
                // Determine header mode: %input:csv annotation > content-type param > default (no)
                final boolean header = resolveCsvHeader(route, ctParams);
                return parseCsvToXml(context, csvStr, header);
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

    /**
     * Parses content-type parameters (everything after the semicolon).
     * E.g., "application/json;lax=false" → {"lax": "false"}
     */
    private static java.util.Properties parseContentTypeParams(final String contentType) {
        final java.util.Properties params = new java.util.Properties();
        if (contentType == null || !contentType.contains(";")) {
            return params;
        }
        final String paramPart = contentType.substring(contentType.indexOf(';') + 1);
        for (final String part : paramPart.split(";")) {
            final String trimmed = part.trim();
            final int eqIdx = trimmed.indexOf('=');
            if (eqIdx > 0) {
                params.setProperty(
                        trimmed.substring(0, eqIdx).trim().toLowerCase(java.util.Locale.ROOT),
                        trimmed.substring(eqIdx + 1).trim());
            }
        }
        return params;
    }

    /**
     * Resolves JSON lax mode from %input:json annotation, content-type params, or default.
     */
    private static boolean resolveJsonLax(final Route route, final java.util.Properties ctParams) {
        // 1. Check %input:json('lax=...') annotation
        final String annotationLax = route.getInputOptions().getProperty("input.json.lax");
        if (annotationLax != null) {
            return "yes".equalsIgnoreCase(annotationLax) || "true".equalsIgnoreCase(annotationLax);
        }
        // 2. Check content-type parameter (e.g., application/json;lax=yes)
        final String ctLax = ctParams.getProperty("lax");
        if (ctLax != null) {
            return "yes".equalsIgnoreCase(ctLax) || "true".equalsIgnoreCase(ctLax);
        }
        // 3. Default: lax=no (strict mode — underscores doubled)
        return false;
    }

    /**
     * Resolves CSV header mode from %input:csv annotation, content-type params, or default.
     */
    private static boolean resolveCsvHeader(final Route route, final java.util.Properties ctParams) {
        // 1. Check %input:csv('header=...') annotation
        final String annotationHeader = route.getInputOptions().getProperty("input.csv.header");
        if (annotationHeader != null) {
            return "yes".equalsIgnoreCase(annotationHeader) || "true".equalsIgnoreCase(annotationHeader);
        }
        // 2. Check content-type parameter (e.g., text/csv;header=yes)
        final String ctHeader = ctParams.getProperty("header");
        if (ctHeader != null) {
            return "yes".equalsIgnoreCase(ctHeader) || "true".equalsIgnoreCase(ctHeader);
        }
        // 3. Default: header=no
        return false;
    }

    /**
     * Parses JSON string to XML using the BaseX-compatible "direct" format.
     * JSON keys become element names. With lax=false (default), characters
     * invalid in XML names are escaped by doubling underscores.
     */
    private static Sequence parseJsonToXml(final XQueryContext context,
                                           final String json, final boolean lax)
            throws XPathException {
        try {
            context.pushDocumentContext();
            final org.exist.dom.memtree.MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();

            final com.fasterxml.jackson.core.JsonFactory factory = new com.fasterxml.jackson.core.JsonFactory();
            try (final com.fasterxml.jackson.core.JsonParser parser = factory.createParser(json)) {
                parser.nextToken(); // Move to first token
                jsonTokenToXml(builder, "json", parser, lax);
            }

            builder.endDocument();
            return builder.getDocument();
        } catch (final IOException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    "Failed to parse JSON body: " + e.getMessage());
        } finally {
            context.popDocumentContext();
        }
    }

    private static void jsonTokenToXml(
            final org.exist.dom.memtree.MemTreeBuilder builder,
            final String name,
            final com.fasterxml.jackson.core.JsonParser parser,
            final boolean lax) throws IOException, XPathException {
        final String xmlName = lax ? name : escapeJsonName(name);
        final org.exist.dom.QName qname = qname(xmlName);

        final com.fasterxml.jackson.core.JsonToken token = parser.currentToken();
        if (token == com.fasterxml.jackson.core.JsonToken.START_OBJECT) {
            builder.startElement(qname, null);
            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_OBJECT) {
                final String fieldName = parser.currentName();
                parser.nextToken();
                jsonTokenToXml(builder, fieldName, parser, lax);
            }
            builder.endElement();
        } else if (token == com.fasterxml.jackson.core.JsonToken.START_ARRAY) {
            builder.startElement(qname, null);
            while (parser.nextToken() != com.fasterxml.jackson.core.JsonToken.END_ARRAY) {
                jsonTokenToXml(builder, "_", parser, lax);
            }
            builder.endElement();
        } else if (token == com.fasterxml.jackson.core.JsonToken.VALUE_STRING) {
            builder.startElement(qname, null);
            final String text = parser.getText();
            if (!text.isEmpty()) {
                builder.characters(text);
            }
            builder.endElement();
        } else if (token == com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_INT
                || token == com.fasterxml.jackson.core.JsonToken.VALUE_NUMBER_FLOAT) {
            builder.startElement(qname, null);
            builder.characters(parser.getText());
            builder.endElement();
        } else if (token == com.fasterxml.jackson.core.JsonToken.VALUE_TRUE
                || token == com.fasterxml.jackson.core.JsonToken.VALUE_FALSE) {
            builder.startElement(qname, null);
            builder.characters(parser.getText());
            builder.endElement();
        } else {
            // null
            builder.startElement(qname, null);
            builder.endElement();
        }
    }

    /**
     * Escapes a JSON key for use as an XML element name (non-lax mode).
     * Underscores are doubled, other invalid chars replaced with underscore+hex.
     */
    private static String escapeJsonName(final String name) {
        final StringBuilder result = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);
            if (c == '_') {
                result.append("__");
            } else if (i == 0 && !Character.isLetter(c) && c != '_') {
                result.append('_').append(String.format("%04x", (int) c));
            } else if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.') {
                result.append('_').append(String.format("%04x", (int) c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    /**
     * Parses CSV string to XML using the BaseX-compatible format.
     * With header=true, first row values become element names.
     * With header=false, values are wrapped in generic &lt;entry&gt; elements.
     */
    private static Sequence parseCsvToXml(final XQueryContext context,
                                          final String csv, final boolean header)
            throws XPathException {
        context.pushDocumentContext();
        try {
            final org.exist.dom.memtree.MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startDocument();
            builder.startElement(qname("csv"), null);

            final String[] lines = csv.split("\r?\n");
            String[] headers = null;
            int startLine = 0;

            if (header && lines.length > 0) {
                headers = parseCsvLine(lines[0]);
                startLine = 1;
            }

            for (int i = startLine; i < lines.length; i++) {
                if (lines[i].trim().isEmpty()) {
                    continue;
                }
                builder.startElement(qname("record"), null);
                final String[] fields = parseCsvLine(lines[i]);
                for (int f = 0; f < fields.length; f++) {
                    final String elemName = (headers != null && f < headers.length)
                            ? headers[f] : "entry";
                    builder.startElement(qname(elemName), null);
                    builder.characters(fields[f]);
                    builder.endElement();
                }
                builder.endElement();
            }

            builder.endElement(); // csv
            builder.endDocument();
            return builder.getDocument();
        } finally {
            context.popDocumentContext();
        }
    }

    /**
     * Simple CSV line parser. Handles basic comma-separated values.
     */
    private static String[] parseCsvLine(final String line) {
        return line.split(",", -1);
    }

    /**
     * Creates a QName, wrapping the checked exception.
     */
    private static org.exist.dom.QName qname(final String localName) throws XPathException {
        try {
            return new org.exist.dom.QName(localName);
        } catch (final org.exist.dom.QName.IllegalQNameException e) {
            throw new XPathException((org.exist.xquery.Expression) null,
                    "Invalid element name: " + localName);
        }
    }
}
