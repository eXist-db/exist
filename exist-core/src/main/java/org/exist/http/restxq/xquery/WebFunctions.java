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
package org.exist.http.restxq.xquery;

import org.exist.dom.QName;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.http.restxq.RestXqNamespaces;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.xml.sax.helpers.AttributesImpl;

/**
 * Implements web:redirect(), web:forward(), and web:error() for RESTXQ.
 *
 * <ul>
 *   <li>{@code web:redirect($url)} — returns a {@code <rest:response>} with HTTP 302</li>
 *   <li>{@code web:forward($url)} — returns a {@code <rest:forward>} element</li>
 *   <li>{@code web:error($code, $message)} — throws an XPathException with HTTP status code</li>
 * </ul>
 */
public class WebFunctions extends BasicFunction {

    private static final QName QN_REDIRECT = new QName("redirect", WebModule.NAMESPACE_URI, WebModule.PREFIX);
    private static final QName QN_FORWARD = new QName("forward", WebModule.NAMESPACE_URI, WebModule.PREFIX);
    private static final QName QN_ERROR = new QName("error", WebModule.NAMESPACE_URI, WebModule.PREFIX);

    public static final FunctionSignature FNS_REDIRECT = new FunctionSignature(
            QN_REDIRECT,
            "Returns a rest:response element that redirects the client to the given URL (HTTP 302).",
            new SequenceType[]{
                    new FunctionParameterSequenceType("url", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The URL to redirect to")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    "A rest:response element with HTTP 302 redirect")
    );

    public static final FunctionSignature FNS_FORWARD = new FunctionSignature(
            QN_FORWARD,
            "Returns a rest:forward element for server-side forwarding to the given path.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The path to forward to")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                    "A rest:forward element")
    );

    public static final FunctionSignature FNS_ERROR_2 = new FunctionSignature(
            QN_ERROR,
            "Aborts query evaluation and returns an HTTP error response with the given status code and message.",
            new SequenceType[]{
                    new FunctionParameterSequenceType("code", Type.INTEGER, Cardinality.EXACTLY_ONE,
                            "The HTTP status code"),
                    new FunctionParameterSequenceType("message", Type.STRING, Cardinality.EXACTLY_ONE,
                            "The error message body")
            },
            new FunctionReturnSequenceType(Type.ITEM, Cardinality.EMPTY_SEQUENCE,
                    "Never returns — throws an exception")
    );

    public WebFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String funcName = getSignature().getName().getLocalPart();
        return switch (funcName) {
            case "redirect" -> doRedirect(args[0].getStringValue());
            case "forward" -> doForward(args[0].getStringValue());
            case "error" -> doError(
                    ((IntegerValue) args[0].itemAt(0)).getInt(),
                    args[1].getStringValue());
            default -> throw new XPathException(this, "Unknown web function: " + funcName);
        };
    }

    private Sequence doRedirect(final String url) {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();

            // <rest:response>
            builder.startElement(
                    new QName("response", RestXqNamespaces.REST_NS, RestXqNamespaces.REST_PREFIX), null);

            // <http:response status="302">
            final AttributesImpl httpAttrs = new AttributesImpl();
            httpAttrs.addAttribute("", "status", "status", "CDATA", "302");
            builder.startElement(
                    new QName("response", RestXqNamespaces.HTTP_NS, RestXqNamespaces.HTTP_PREFIX), httpAttrs);

            // <http:header name="Location" value="url"/>
            final AttributesImpl headerAttrs = new AttributesImpl();
            headerAttrs.addAttribute("", "name", "name", "CDATA", "Location");
            headerAttrs.addAttribute("", "value", "value", "CDATA", url);
            builder.startElement(
                    new QName("header", RestXqNamespaces.HTTP_NS, RestXqNamespaces.HTTP_PREFIX), headerAttrs);
            builder.endElement();

            builder.endElement(); // http:response
            builder.endElement(); // rest:response

            return builder.getDocument().getNode(1);
        } finally {
            context.popDocumentContext();
        }
    }

    private Sequence doForward(final String path) {
        context.pushDocumentContext();
        try {
            final MemTreeBuilder builder = context.getDocumentBuilder();
            builder.startElement(
                    new QName("forward", RestXqNamespaces.REST_NS, RestXqNamespaces.REST_PREFIX), null);
            builder.characters(path);
            builder.endElement();
            return builder.getDocument().getNode(1);
        } finally {
            context.popDocumentContext();
        }
    }

    private Sequence doError(final int statusCode, final String message) throws XPathException {
        // Throw a special exception that the servlet can catch and convert to an HTTP error response
        throw new WebErrorException(this, statusCode, message);
    }

    /**
     * Special exception for web:error() that carries an HTTP status code.
     */
    public static class WebErrorException extends XPathException {
        private final int httpStatusCode;

        public WebErrorException(final Expression expr, final int statusCode, final String message) {
            super(expr, ErrorCodes.ERROR, message);
            this.httpStatusCode = statusCode;
        }

        public int getHttpStatusCode() {
            return httpStatusCode;
        }
    }
}
