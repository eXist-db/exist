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

import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.DBBroker;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.*;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import java.util.Set;

/**
 * Serializes XQuery function results to HTTP responses, handling the
 * RESTXQ response protocol including {@code <rest:response>} elements,
 * {@code <rest:forward>}, and binary output.
 */
public class ResponseWriter {

    private static final Logger LOG = LogManager.getLogger(ResponseWriter.class);

    /**
     * Writes the XQuery result sequence to the HTTP response, applying
     * serialization properties from the route's %output:* annotations.
     *
     * <p>Handles these special cases:</p>
     * <ul>
     *   <li>{@code <rest:response>} — sets custom status code and headers</li>
     *   <li>{@code <rest:forward>} — server-side forward (not yet implemented)</li>
     *   <li>Binary results — written directly to output stream</li>
     *   <li>Node/atomic results — serialized via XQuerySerializer</li>
     * </ul>
     */
    public static void write(final DBBroker broker, final Route route,
                             final Sequence result,
                             final HttpServletResponse response) throws IOException {

        final Properties outputProperties = new Properties(route.getOutputProperties());

        // Check first item for rest:response or rest:forward
        Sequence bodySequence = result;
        boolean statusExplicitlySet = false;

        if (result.getItemCount() > 0) {
            try {
                final Item firstItem = result.itemAt(0);
                if (isRestResponseElement(firstItem)) {
                    statusExplicitlySet = processRestResponse((NodeValue) firstItem, response, outputProperties);
                    // Body is everything after the rest:response element
                    if (result.getItemCount() > 1) {
                        final ValueSequence remaining = new ValueSequence();
                        for (int i = 1; i < result.getItemCount(); i++) {
                            remaining.add(result.itemAt(i));
                        }
                        bodySequence = remaining;
                    } else {
                        bodySequence = Sequence.EMPTY_SEQUENCE;
                    }
                } else if (isRestForwardElement(firstItem)) {
                    // Return the forward path — the servlet handles internal dispatch
                    final String forwardPath = firstItem.getStringValue().trim();
                    throw new RestXqForwardException(forwardPath);
                }
            } catch (final XPathException e) {
                LOG.error("Error processing RESTXQ response", e);
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                return;
            }
        }

        if (bodySequence.isEmpty()) {
            if (!response.isCommitted() && !statusExplicitlySet) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            }
            return;
        }

        // Set Content-Type if not already set by rest:response
        if (response.getContentType() == null) {
            response.setContentType(route.getResponseContentType());
        }

        // Serialize the body
        try {
            serializeSequence(broker, bodySequence, outputProperties, response);
        } catch (final XPathException | SAXException e) {
            LOG.error("Error serializing RESTXQ response", e);
            if (!response.isCommitted()) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Serialization error: " + e.getMessage());
            }
        }
    }

    private static boolean isRestResponseElement(final Item item) throws XPathException {
        if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
            final NodeValue node = (NodeValue) item;
            final Node n = node.getNode();
            return "response".equals(n.getLocalName())
                    && RestXqNamespaces.REST_NS.equals(n.getNamespaceURI());
        }
        return false;
    }

    private static boolean isRestForwardElement(final Item item) throws XPathException {
        if (Type.subTypeOf(item.getType(), Type.ELEMENT)) {
            final NodeValue node = (NodeValue) item;
            final Node n = node.getNode();
            return "forward".equals(n.getLocalName())
                    && RestXqNamespaces.REST_NS.equals(n.getNamespaceURI());
        }
        return false;
    }

    /**
     * Validates and processes a {@code <rest:response>} element, extracting
     * HTTP status, headers, and serialization parameters.
     *
     * @return true if an HTTP status was explicitly set
     * @throws IOException if the rest:response element has invalid structure
     */
    private static boolean processRestResponse(final NodeValue responseNode,
                                               final HttpServletResponse response,
                                               final Properties outputProperties)
            throws IOException {
        final Node node = responseNode.getNode();

        // Validate: no unknown attributes on rest:response
        final org.w3c.dom.NamedNodeMap attrs = node.getAttributes();
        if (attrs != null) {
            for (int a = 0; a < attrs.getLength(); a++) {
                final Node attr = attrs.item(a);
                final String attrNs = attr.getNamespaceURI();
                // Allow xmlns declarations, reject anything else
                if (attrNs == null || !attrNs.equals("http://www.w3.org/2000/xmlns/")) {
                    final String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                    if (!"xmlns".equals(attrName) && !attrName.startsWith("xmlns:")) {
                        throw new IOException(
                                "Invalid attribute '" + attrName + "' on rest:response element");
                    }
                }
            }
        }

        final NodeList children = node.getChildNodes();
        boolean statusSet = false;

        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);

            // Validate: no text content in rest:response
            if (child.getNodeType() == Node.TEXT_NODE) {
                final String text = child.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    throw new IOException(
                            "rest:response must not contain text content");
                }
                continue;
            }

            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }

            final String childNs = child.getNamespaceURI();
            final String childLocal = child.getLocalName();

            if (RestXqNamespaces.HTTP_NS.equals(childNs) && "response".equals(childLocal)) {
                statusSet = processHttpResponse((Element) child, response);
            } else if (RestXqNamespaces.OUTPUT_NS.equals(childNs)
                    && "serialization-parameters".equals(childLocal)) {
                processSerializationParams((Element) child, outputProperties);
            } else {
                // Validate: only http:response and output:serialization-parameters allowed
                throw new IOException(
                        "Invalid child element in rest:response: "
                                + (childNs != null ? "{" + childNs + "}" : "") + childLocal);
            }
        }
        return statusSet;
    }

    /**
     * @return true if status was explicitly set
     */
    private static final Set<String> VALID_HTTP_RESPONSE_ATTRS = Set.of(
            "status", "reason", "message"
    );

    private static boolean processHttpResponse(final Element httpResponse,
                                               final HttpServletResponse response)
            throws IOException {
        // Validate attributes: only status and reason/message allowed
        final org.w3c.dom.NamedNodeMap attrs = httpResponse.getAttributes();
        if (attrs != null) {
            for (int a = 0; a < attrs.getLength(); a++) {
                final Node attr = attrs.item(a);
                final String attrNs = attr.getNamespaceURI();
                final String attrName = attr.getLocalName() != null ? attr.getLocalName() : attr.getNodeName();
                if (attrNs != null && attrNs.equals("http://www.w3.org/2000/xmlns/")) {
                    continue;
                }
                if ("xmlns".equals(attrName) || attrName.startsWith("xmlns:")) {
                    continue;
                }
                if (!VALID_HTTP_RESPONSE_ATTRS.contains(attrName)) {
                    throw new IOException(
                            "Invalid attribute '" + attrName + "' on http:response element");
                }
            }
        }

        boolean statusSet = false;
        final String status = httpResponse.getAttribute("status");
        if (status != null && !status.isEmpty()) {
            try {
                response.setStatus(Integer.parseInt(status));
                statusSet = true;
            } catch (final NumberFormatException e) {
                LOG.warn("Invalid HTTP status in rest:response: {}", status);
            }
        }

        // Process children: only http:header elements allowed, no text content
        final NodeList children = httpResponse.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);

            if (child.getNodeType() == Node.TEXT_NODE) {
                final String text = child.getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    throw new IOException(
                            "http:response must not contain text content");
                }
                continue;
            }

            if (child.getNodeType() == Node.ELEMENT_NODE
                    && "header".equals(child.getLocalName())
                    && RestXqNamespaces.HTTP_NS.equals(child.getNamespaceURI())) {
                final Element headerElem = (Element) child;
                final String name = headerElem.getAttribute("name");
                final String value = headerElem.getAttribute("value");
                if (name != null && !name.isEmpty()) {
                    response.addHeader(name, value);
                    if ("Content-Type".equalsIgnoreCase(name)) {
                        response.setContentType(value);
                    }
                }
            }
        }
        return statusSet;
    }

    private static void processSerializationParams(final Element params,
                                                   final Properties outputProperties) {
        final NodeList children = params.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && RestXqNamespaces.OUTPUT_NS.equals(child.getNamespaceURI())) {
                final Element param = (Element) child;
                final String value = param.getAttribute("value");
                if (value != null && !value.isEmpty()) {
                    outputProperties.setProperty(param.getLocalName(), value);
                }
            }
        }
    }

    private static void handleForward(final NodeValue forwardNode,
                                      final HttpServletResponse response) throws IOException {
        final String path = forwardNode.getNode().getTextContent();
        if (path != null && !path.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_NO_CONTENT);
            response.sendRedirect(path);
        }
    }

    private static void serializeSequence(final DBBroker broker,
                                          final Sequence sequence,
                                          final Properties outputProperties,
                                          final HttpServletResponse response)
            throws IOException, XPathException, SAXException {

        // Handle binary results
        if (sequence.hasOne()) {
            final Item item = sequence.itemAt(0);
            if (item instanceof BinaryValue) {
                writeBinaryResult((BinaryValue) item, response);
                return;
            }
        }

        // Serialize using XQuerySerializer
        final OutputStream os = response.getOutputStream();
        try (final Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8)) {
            final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, writer);
            serializer.serialize(sequence);
            writer.flush();
        }
    }

    private static void writeBinaryResult(final BinaryValue binary,
                                          final HttpServletResponse response) throws IOException {
        try (final OutputStream os = response.getOutputStream()) {
            binary.streamBinaryTo(os);
            os.flush();
        }
    }
}
